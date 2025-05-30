package com.chatapp.service;

import com.chatapp.dto.request.ConversationDto;
import com.chatapp.dto.request.MessageDto;
import com.chatapp.dto.request.UserDto;
import com.chatapp.enums.ConversationType;
import com.chatapp.enums.MessageType;
import com.chatapp.enums.UserStatus;
import com.chatapp.exception.ResourceNotFoundException;
import com.chatapp.model.Conversation;
import com.chatapp.model.ConversationUser;
import com.chatapp.model.Message;
import com.chatapp.model.User;
import com.chatapp.model.Attachments;
import com.chatapp.repository.ConversationRepository;
import com.chatapp.repository.ConversationUserRepository;
import com.chatapp.repository.MessageRepository;
import com.chatapp.repository.UserRepository;
import com.chatapp.repository.AttachmentsRepository;
import com.google.genai.types.GenerateContentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Service xử lý các cuộc trò chuyện AI tự động trả lời
 */
@Service
public class AiConversationService {

    private static final Logger logger = LoggerFactory.getLogger(AiConversationService.class);

    private static final String AI_BOT_PHONE = "ai_bot_system";
    private static final String AI_BOT_DISPLAY_NAME = "AI Assistant";
    private static final String AI_BOT_AVATAR_URL = "https://mys3iuh.s3.ap-southeast-1.amazonaws.com/449521094_1563710030877271_6471969711590990679_n.jpg";

    // Pattern để detect từ khóa tạo ảnh
    private static final Pattern IMAGE_GENERATION_PATTERN = Pattern.compile(
            ".*(?:tạo\\s*ảnh|vẽ\\s*(?:cho|giúp|tôi)?|generate\\s*image|create\\s*image|draw|painting).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationUserRepository conversationUserRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private TextGenerationService textGenerationService;

    @Autowired
    private ImageGenerationService imageGenerationService;

    @Autowired
    private AttachmentsService attachmentsService;

    @Autowired
    private AttachmentsRepository attachmentsRepository;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private UserService userService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Tạo hoặc lấy AI bot user
     */
    @Transactional
    public User getOrCreateAiBot() {
        Optional<User> existingBot = userRepository.findByPhone(AI_BOT_PHONE);

        if (existingBot.isPresent()) {
            return existingBot.get();
        }

        // Tạo AI bot user mới
        User aiBot = new User();
        aiBot.setDisplayName(AI_BOT_DISPLAY_NAME);
        aiBot.setPhone(AI_BOT_PHONE);
        aiBot.setPassword(""); // AI bot không cần password
        aiBot.setEmail("ai@chatapp.com");
        aiBot.setDateOfBirth(LocalDate.of(2024, 1, 1));
        aiBot.setAvatarUrl(AI_BOT_AVATAR_URL);
        aiBot.setStatus(UserStatus.ONLINE);
        aiBot.setCreatedAt(LocalDateTime.now());

        return userRepository.save(aiBot);
    }

    /**
     * Tạo cuộc trò chuyện AI giữa user và AI bot
     */
    @Transactional
    public ConversationDto createAiConversation(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + userId));

        User aiBot = getOrCreateAiBot();

        // Kiểm tra xem đã có cuộc trò chuyện AI với user này chưa
        Optional<Conversation> existingConversation = conversationRepository.findOneToOneConversation(userId,
                aiBot.getUserId());

        if (existingConversation.isPresent() && existingConversation.get().getIsAiConversation()) {
            return conversationService.mapToConversationDto(existingConversation.get(), userId);
        }

        // Tạo cuộc trò chuyện AI mới
        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.ONE_TO_ONE);
        conversation.setIsAiConversation(true);
        conversation.setCreatedAt(LocalDateTime.now());

        Conversation savedConversation = conversationRepository.save(conversation);

        // Thêm user vào cuộc trò chuyện
        ConversationUser userConversation = new ConversationUser();
        userConversation.setConversation(savedConversation);
        userConversation.setUser(user);
        conversationUserRepository.save(userConversation);

        // Thêm AI bot vào cuộc trò chuyện
        ConversationUser botConversation = new ConversationUser();
        botConversation.setConversation(savedConversation);
        botConversation.setUser(aiBot);
        conversationUserRepository.save(botConversation);

        // Gửi tin nhắn chào mừng
        sendWelcomeMessage(savedConversation.getId(), aiBot);

        return conversationService.mapToConversationDto(savedConversation, user.getUserId());
    }

    /**
     * Gửi tin nhắn chào mừng từ AI bot
     */
    private void sendWelcomeMessage(Long conversationId, User aiBot) {
        String welcomeMessage = "Xin chào! Tôi là AI Assistant. Tôi có thể giúp bạn trả lời câu hỏi, tạo nội dung, và nhiều việc khác. Hãy hỏi tôi bất cứ điều gì bạn muốn!";

        Message message = new Message();
        message.setSender(aiBot);
        message.setConversation(conversationRepository.findById(conversationId).orElse(null));
        message.setContent(welcomeMessage);
        message.setType(MessageType.TEXT);
        message.setCreatedAt(LocalDateTime.now());

        Message savedMessage = messageRepository.save(message);
        MessageDto messageDto = conversationService.mapToMessageDto(savedMessage);

        // Gửi qua WebSocket
        messagingTemplate.convertAndSend("/queue/conversation/" + conversationId, messageDto);
    }

    /**
     * Xử lý tin nhắn tự động cho AI conversation
     */
    @Transactional
    public void handleAutoReply(Long conversationId, String userMessage, Long senderId) {
        try {
            Conversation conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cuộc trò chuyện"));

            // Kiểm tra xem có phải là AI conversation không
            if (!conversation.getIsAiConversation()) {
                return;
            }

            User aiBot = getOrCreateAiBot();

            // Kiểm tra xem người gửi có phải là AI bot không (tránh loop)
            if (senderId.equals(aiBot.getUserId())) {
                return;
            }

            // Kiểm tra xem tin nhắn có yêu cầu tạo ảnh không
            if (shouldGenerateImage(userMessage)) {
                handleImageGeneration(conversation, aiBot, userMessage);
            } else {
                handleTextGeneration(conversation, aiBot, userMessage);
            }

        } catch (Exception e) {
            logger.error("Lỗi khi tạo phản hồi AI cho conversation {}: {}", conversationId, e.getMessage(), e);
            // Gửi tin nhắn lỗi fallback
            sendErrorMessage(conversationId, getOrCreateAiBot());
        }
    }

    /**
     * Kiểm tra xem tin nhắn có yêu cầu tạo ảnh không
     */
    private boolean shouldGenerateImage(String userMessage) {
        return IMAGE_GENERATION_PATTERN.matcher(userMessage).matches();
    }

    /**
     * Xử lý tạo ảnh và gửi tin nhắn
     */
    private void handleImageGeneration(Conversation conversation, User aiBot, String userMessage) {
        try {
            logger.info("Detecting image generation request: {}", userMessage);

            // Tạo prompt tối ưu cho tạo ảnh
            String imagePrompt = extractImagePrompt(userMessage);

            // Gửi tin nhắn thông báo đang tạo ảnh
            sendTypingMessage(conversation.getId(), aiBot, "Tôi đang tạo ảnh cho bạn, vui lòng chờ một chút...");

            // Delay để hiển thị tin nhắn thông báo
            Thread.sleep(1500);

            // Tạo ảnh
            List<String> imageUrls = imageGenerationService.generateImages(imagePrompt, null);

            if (!imageUrls.isEmpty()) {
                // Gửi tin nhắn với ảnh đã tạo
                sendImageMessage(conversation, aiBot, imageUrls.get(0),
                        "Đây là ảnh tôi đã tạo cho bạn dựa trên yêu cầu: " + imagePrompt);
            } else {
                sendErrorMessage(conversation.getId(), aiBot,
                        "Xin lỗi, tôi không thể tạo ảnh lúc này. Vui lòng thử lại sau.");
            }

        } catch (Exception e) {
            logger.error("Lỗi khi tạo ảnh: {}", e.getMessage(), e);
            sendErrorMessage(conversation.getId(), aiBot,
                    "Xin lỗi, tôi gặp sự cố khi tạo ảnh. Vui lòng thử lại với mô tả khác.");
        }
    }

    /**
     * Xử lý tạo văn bản thông thường
     */
    private void handleTextGeneration(Conversation conversation, User aiBot, String userMessage) {
        try {
            // Tạo AI response
            String aiResponse = generateAiResponse(userMessage);

            // Tạo và lưu tin nhắn phản hồi từ AI
            Message aiMessage = new Message();
            aiMessage.setSender(aiBot);
            aiMessage.setConversation(conversation);
            aiMessage.setContent(aiResponse);
            aiMessage.setType(MessageType.TEXT);
            aiMessage.setCreatedAt(LocalDateTime.now());

            Message savedMessage = messageRepository.save(aiMessage);
            MessageDto messageDto = conversationService.mapToMessageDto(savedMessage);

            // Gửi qua WebSocket với độ trễ nhỏ để tạo cảm giác tự nhiên
            Thread.sleep(1000); // Delay 1 giây
            messagingTemplate.convertAndSend("/queue/conversation/" + conversation.getId(), messageDto);

            logger.info("AI đã phản hồi văn bản trong conversation {}: {}", conversation.getId(), aiResponse);

        } catch (Exception e) {
            logger.error("Lỗi khi tạo phản hồi văn bản: {}", e.getMessage(), e);
            sendErrorMessage(conversation.getId(), aiBot);
        }
    }

    /**
     * Trích xuất prompt tạo ảnh từ tin nhắn người dùng
     */
    private String extractImagePrompt(String userMessage) {
        // Loại bỏ các từ khóa trigger và làm sạch prompt
        String prompt = userMessage
                .replaceAll("(?i)(tạo\\s*ảnh|vẽ\\s*(?:cho|giúp|tôi)?|generate\\s*image|create\\s*image)", "")
                .replaceAll("(?i)(về|của|cho\\s*(?:tôi|mình))", "")
                .trim();

        // Nếu prompt trống sau khi làm sạch, dùng prompt mặc định
        if (prompt.isEmpty()) {
            prompt = "một bức tranh đẹp và sáng tạo";
        }

        // Thêm context cho prompt tạo ảnh tốt hơn
        return "High quality, detailed, beautiful: " + prompt;
    }

    /**
     * Gửi tin nhắn thông báo đang typing/processing
     */
    private void sendTypingMessage(Long conversationId, User aiBot, String content) {
        try {
            Message typingMessage = new Message();
            typingMessage.setSender(aiBot);
            typingMessage.setConversation(conversationRepository.findById(conversationId).orElse(null));
            typingMessage.setContent(content);
            typingMessage.setType(MessageType.TEXT);
            typingMessage.setCreatedAt(LocalDateTime.now());

            Message savedMessage = messageRepository.save(typingMessage);
            MessageDto messageDto = conversationService.mapToMessageDto(savedMessage);

            messagingTemplate.convertAndSend("/queue/conversation/" + conversationId, messageDto);
        } catch (Exception e) {
            logger.error("Lỗi khi gửi typing message: {}", e.getMessage());
        }
    }

    /**
     * Gửi tin nhắn với ảnh
     */
    private void sendImageMessage(Conversation conversation, User aiBot, String imageUrl, String content) {
        try {
            // Tạo attachment cho ảnh
            Attachments attachment = new Attachments();
            attachment.setName("ai_generated_image.png");
            attachment.setType("image/png");
            attachment.setUrl(imageUrl);
            attachment.setSize(0); // Size không biết trước
            // createdAt và updatedAt sẽ được tự động set bởi @CreationTimestamp và
            // @UpdateTimestamp

            // Tạo tin nhắn
            Message imageMessage = new Message();
            imageMessage.setSender(aiBot);
            imageMessage.setConversation(conversation);
            imageMessage.setContent(attachment.getName());
            imageMessage.setType(MessageType.IMAGE);
            imageMessage.setCreatedAt(LocalDateTime.now());

            // Lưu tin nhắn trước
            Message savedMessage = messageRepository.save(imageMessage);

            // Set relationship và save attachment
            attachment.setMessage(savedMessage);
            attachmentsRepository.save(attachment);

            // Thêm attachment vào message
            savedMessage.getAttachments().add(attachment);

            MessageDto messageDto = conversationService.mapToMessageDto(savedMessage);

            messagingTemplate.convertAndSend("/queue/conversation/" + conversation.getId(), messageDto);

            logger.info("AI đã gửi ảnh trong conversation {}: {}", conversation.getId(), imageUrl);

        } catch (Exception e) {
            logger.error("Lỗi khi gửi tin nhắn ảnh: {}", e.getMessage(), e);
            sendErrorMessage(conversation.getId(), aiBot, "Đã tạo ảnh nhưng gặp lỗi khi gửi. Vui lòng thử lại.");
        }
    }

    /**
     * Gửi tin nhắn lỗi
     */
    private void sendErrorMessage(Long conversationId, User aiBot) {
        sendErrorMessage(conversationId, aiBot, "Xin lỗi, tôi gặp sự cố kỹ thuật. Vui lòng thử lại sau.");
    }

    /**
     * Gửi tin nhắn lỗi với nội dung tùy chỉnh
     */
    private void sendErrorMessage(Long conversationId, User aiBot, String errorMessage) {
        try {
            Message message = new Message();
            message.setSender(aiBot);
            message.setConversation(conversationRepository.findById(conversationId).orElse(null));
            message.setContent(errorMessage);
            message.setType(MessageType.TEXT);
            message.setCreatedAt(LocalDateTime.now());

            Message savedMessage = messageRepository.save(message);
            MessageDto messageDto = conversationService.mapToMessageDto(savedMessage);

            messagingTemplate.convertAndSend("/queue/conversation/" + conversationId, messageDto);
        } catch (Exception e) {
            logger.error("Lỗi khi gửi error message: {}", e.getMessage());
        }
    }

    /**
     * Tạo phản hồi AI dựa trên tin nhắn của user
     */
    private String generateAiResponse(String userMessage) {
        try {
            // Tạo system instruction cho AI
            String systemInstruction = "Bạn là một AI Assistant thông minh và hữu ích trong ứng dụng chat. " +
                    "Hãy trả lời một cách tự nhiên, thân thiện và hữu ích. " +
                    "Trả lời bằng tiếng Việt trừ khi được yêu cầu sử dụng ngôn ngữ khác. " +
                    "Giữ câu trả lời ngắn gọn và dễ hiểu. " +
                    "Nếu được hỏi về thông tin cá nhân, hãy lịch sự từ chối và chuyển hướng cuộc trò chuyện.";

            GenerateContentConfig config = textGenerationService.createSimpleConfig(500, 0.7);

            return textGenerationService.generateTextWithAdvancedConfig(
                    userMessage,
                    systemInstruction,
                    500,
                    0.7,
                    textGenerationService.createDefaultSafetySettings());

        } catch (Exception e) {
            logger.error("Lỗi khi tạo phản hồi AI: {}", e.getMessage(), e);
            return "Xin lỗi, tôi gặp sự cố kỹ thuật. Vui lòng thử lại sau.";
        }
    }

    /**
     * Kiểm tra xem cuộc trò chuyện có phải là AI conversation không
     */
    public boolean isAiConversation(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .map(conversation -> conversation.getIsAiConversation() != null && conversation.getIsAiConversation())
                .orElse(false);
    }

    /**
     * Lấy danh sách tất cả AI conversations của user
     */
    public List<ConversationDto> getAiConversations(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + userId));

        List<Conversation> conversations = conversationRepository.findByParticipantId(userId);

        return conversations.stream()
                .filter(conversation -> conversation.getIsAiConversation() != null
                        && conversation.getIsAiConversation())
                .map(conversation -> conversationService.mapToConversationDto(conversation, userId))
                .toList();
    }
}