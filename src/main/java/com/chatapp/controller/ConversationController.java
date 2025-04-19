package com.chatapp.controller;

import com.chatapp.dto.request.ConversationDto;
import com.chatapp.dto.request.MessageDto;
import com.chatapp.enums.MessageType;
import com.chatapp.model.Message;
import com.chatapp.model.User;
import com.chatapp.service.ConversationService;
import com.chatapp.service.UserService;
import com.chatapp.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.chatapp.repository.UserRepository;
import com.chatapp.repository.ConversationRepository;
import com.chatapp.repository.MessageRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.security.Principal;

import com.chatapp.dto.request.UserDto;
import com.chatapp.model.DeletedMessage;
import com.chatapp.repository.DeletedMessageRepository;
import com.chatapp.service.MessageService;
import com.chatapp.model.Attachments;
import com.chatapp.service.AttachmentsService;
import java.util.ArrayList;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@RestController
@RequestMapping("/api/conversations")
@Tag(name = "Conversation Management", description = "Các API để quản lý cuộc trò chuyện chat")
@SecurityRequirement(name = "bearerAuth")
public class ConversationController {

        @Autowired
        private ConversationService conversationService;

        @Autowired
        private UserService userService;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private ConversationRepository conversationRepository;

        @Autowired
        private MessageRepository messageRepository;

        @Autowired
        private SimpMessagingTemplate messagingTemplate;

        @Autowired
        private DeletedMessageRepository deletedMessageRepository;

        @Autowired
        private MessageService messageService;

        @Autowired
        private AttachmentsService attachmentsService;

        @Operation(summary = "Lấy danh sách cuộc trò chuyện", description = "Lấy tất cả các cuộc trò chuyện của người dùng hiện tại")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy danh sách cuộc trò chuyện thành công")
        })
        @GetMapping
        public ResponseEntity<ApiResponse<List<ConversationDto>>> getConversations(
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                List<ConversationDto> conversations = conversationService.getConversationsByUserId(userId);
                return ResponseEntity.ok(ApiResponse.<List<ConversationDto>>builder()
                                .success(true)
                                .message("Lấy danh sách cuộc trò chuyện thành công")
                                .payload(conversations)
                                .build());
        }

        @Operation(summary = "Tạo cuộc trò chuyện", description = "Tạo mới một cuộc trò chuyện với nhiều thành viên")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tạo cuộc trò chuyện thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu thành viên không hợp lệ"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy một hoặc nhiều thành viên")
        })
        @PostMapping
        public ResponseEntity<ApiResponse<ConversationDto>> createConversation(
                        @Parameter(description = "Conversation request with participant IDs", required = true) @RequestBody ConversationRequest request,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                ConversationDto conversation = conversationService.createConversation(userId,
                                request.getParticipantIds());
                return ResponseEntity.ok(ApiResponse.<ConversationDto>builder()
                                .success(true)
                                .message("Tạo cuộc trò chuyện thành công")
                                .payload(conversation)
                                .build());
        }

        @Operation(summary = "Lấy hoặc tạo cuộc trò chuyện 1-1", description = "Lấy cuộc trò chuyện 1-1 đã tồn tại hoặc tạo mới nếu chưa có")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy hoặc tạo cuộc trò chuyện thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng")
        })
        @PostMapping("/user/{userId}")
        public ResponseEntity<ApiResponse<ConversationDto>> getOrCreateOneToOneConversation(
                        @Parameter(description = "ID người dùng để tạo/lấy cuộc trò chuyện với", required = true) @PathVariable Long userId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
                Long currentUserId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                ConversationDto conversation = conversationService.getOrCreateOneToOneConversation(currentUserId,
                                userId);
                return ResponseEntity.ok(ApiResponse.<ConversationDto>builder()
                                .success(true)
                                .message("Lấy/tạo cuộc trò chuyện thành công")
                                .payload(conversation)
                                .build());
        }

        @Operation(summary = "Lấy tin nhắn cuộc trò chuyện", description = "Lấy tất cả tin nhắn trong một cuộc trò chuyện")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy tin nhắn thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền truy cập cuộc trò chuyện này"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy cuộc trò chuyện")
        })
        @GetMapping("/{conversationId}/messages")
        public ResponseEntity<ApiResponse<List<MessageDto>>> getMessages(
                        @Parameter(description = "Conversation ID", required = true) @PathVariable Long conversationId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                List<MessageDto> messages = conversationService.getMessagesByConversationId(conversationId, userId);

                // Kiểm tra xem cuộc trò chuyện có bị chặn hay không
                boolean isBlocked = conversationService.isConversationBlocked(conversationId);
                boolean isBlockedByMe = conversationService.isBlockedByUser(conversationId, userId);

                return ResponseEntity.ok(ApiResponse.<List<MessageDto>>builder()
                                .success(true)
                                .message("Lấy tin nhắn thành công")
                                .payload(messages)
                                .data("isBlocked", isBlocked)
                                .data("isBlockedByMe", isBlockedByMe)
                                .build());
        }

        @Operation(summary = "Lấy tin nhắn cuộc trò chuyện có phân trang", description = "Lấy tin nhắn trong một cuộc trò chuyện có phân trang")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy tin nhắn thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền truy cập cuộc trò chuyện này"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy cuộc trò chuyện")
        })
        @GetMapping("/{conversationId}/messages/paged")
        public ResponseEntity<ApiResponse<Page<MessageDto>>> getPagedMessages(
                        @Parameter(description = "Conversation ID", required = true) @PathVariable Long conversationId,
                        @Parameter(description = "Số trang (bắt đầu từ 0)", required = false) @RequestParam(defaultValue = "0") int page,
                        @Parameter(description = "Kích thước trang", required = false) @RequestParam(defaultValue = "20") int size,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();

                // Tạo đối tượng Pageable từ tham số page và size
                Pageable pageable = PageRequest.of(page, size);

                // Lấy tin nhắn có phân trang
                Page<MessageDto> messages = conversationService.getPagedMessagesByConversationId(conversationId, userId,
                                pageable);

                // Kiểm tra xem cuộc trò chuyện có bị chặn hay không
                boolean isBlocked = conversationService.isConversationBlocked(conversationId);
                boolean isBlockedByMe = conversationService.isBlockedByUser(conversationId, userId);

                return ResponseEntity.ok(ApiResponse.<Page<MessageDto>>builder()
                                .success(true)
                                .message("Lấy tin nhắn thành công")
                                .payload(messages)
                                .data("isBlocked", isBlocked)
                                .data("isBlockedByMe", isBlockedByMe)
                                .build());
        }

        @Operation(summary = "Gửi tin nhắn", description = "Gửi một tin nhắn mới trong cuộc trò chuyện")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Gửi tin nhắn thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền gửi tin nhắn trong cuộc trò chuyện này"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy cuộc trò chuyện")
        })
        @PostMapping("/{conversationId}/messages")
        public ResponseEntity<ApiResponse<MessageDto>> sendMessage(
                        @Parameter(description = "Conversation ID", required = true) @PathVariable Long conversationId,
                        @Parameter(description = "Message details", required = true) @RequestBody MessageDto messageDto,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
                UserDto userDto = userService.getUserByPhone(userDetails.getUsername());
                Long senderId = userDto.getUserId();

                messageDto.setConversationId(conversationId);

                if (!conversationService.isUserInConversation(conversationId, senderId)) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .body(ApiResponse.<MessageDto>builder()
                                                        .success(false)
                                                        .message("Bạn không có quyền gửi tin nhắn trong cuộc trò chuyện này")
                                                        .build());
                }

                // Kiểm tra nếu cuộc trò chuyện đã bị chặn
                if (conversationService.isConversationBlocked(conversationId)) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .body(ApiResponse.<MessageDto>builder()
                                                        .success(false)
                                                        .message("Tin nhắn đã bị chặn trong cuộc trò chuyện này")
                                                        .build());
                }

                Message message = new Message();
                message.setSender(userRepository.findById(senderId).orElseThrow());
                message.setConversation(conversationRepository.findById(conversationId).orElseThrow());
                message.setContent(messageDto.getContent());
                message.setType(MessageType.TEXT);
                message.setCreatedAt(LocalDateTime.now());

                Message savedMessage = messageRepository.save(message);
                MessageDto savedMessageDto = conversationService.mapToMessageDto(savedMessage);
                messagingTemplate.convertAndSend("/queue/conversation/" + conversationId, savedMessageDto);

                return ResponseEntity.ok(ApiResponse.<MessageDto>builder()
                                .success(true)
                                .message("Gửi tin nhắn thành công")
                                .payload(savedMessageDto)
                                .build());
        }

        @Operation(summary = "Xử lý tin nhắn WebSocket", description = "Xử lý tin nhắn WebSocket gửi đến trong một cuộc trò chuyện")
        @MessageMapping("/conversation/{conversationId}")
        @SendTo("/queue/conversation/{conversationId}")
        public MessageDto handleMessage(
                        @Parameter(description = "Conversation ID", required = true) @DestinationVariable Long conversationId,
                        @Parameter(description = "Message details", required = true) MessageDto messageDto,
                        @Parameter(hidden = true) Principal principal) {
                UserDto userDto = userService.getUserByPhone(principal.getName());
                User sender = userRepository.findById(userDto.getUserId()).orElseThrow();

                messageDto.setConversationId(conversationId);

                if (!conversationService.isUserInConversation(conversationId, sender.getUserId())) {
                        throw new AccessDeniedException("Bạn không phải là thành viên của cuộc trò chuyện này");
                }

                // Kiểm tra nếu cuộc trò chuyện đã bị chặn
                if (conversationService.isConversationBlocked(conversationId)) {
                        throw new AccessDeniedException("Tin nhắn đã bị chặn trong cuộc trò chuyện này");
                }

                Message message = new Message();
                message.setSender(sender);
                message.setConversation(conversationRepository.findById(conversationId).orElseThrow());
                message.setContent(messageDto.getContent());
                message.setType(MessageType.TEXT);
                message.setCreatedAt(LocalDateTime.now());

                Message savedMessage = messageRepository.save(message);

                return conversationService.mapToMessageDto(savedMessage);
        }

        @Operation(summary = "Thu hồi tin nhắn", description = "Thu hồi tin nhắn trong vòng 1 ngày, chỉ người gửi mới được thu hồi")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Thu hồi tin nhắn thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền thu hồi hoặc quá thời gian cho phép"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy tin nhắn")
        })
        @PutMapping("/messages/{messageId}/recall")
        public ResponseEntity<ApiResponse<MessageDto>> recallMessage(
                        @Parameter(description = "ID tin nhắn", required = true) @PathVariable Long messageId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
                UserDto userDto = userService.getUserByPhone(userDetails.getUsername());
                Long userId = userDto.getUserId();

                Message message = messageRepository.findById(messageId).orElse(null);
                if (message == null) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .body(ApiResponse.<MessageDto>builder()
                                                        .success(false)
                                                        .message("Không tìm thấy tin nhắn")
                                                        .build());
                }

                if (!message.getSender().getUserId().equals(userId)) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .body(ApiResponse.<MessageDto>builder()
                                                        .success(false)
                                                        .message("Bạn không có quyền thu hồi tin nhắn này")
                                                        .build());
                }

                if (message.getCreatedAt().isBefore(LocalDateTime.now().minusDays(1))) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .body(ApiResponse.<MessageDto>builder()
                                                        .success(false)
                                                        .message("Tin nhắn đã quá thời gian cho phép thu hồi")
                                                        .build());
                }

                message.setContent("tin nhắn đã thu hồi");
                Message savedMessage = messageRepository.save(message);
                MessageDto savedMessageDto = conversationService.mapToMessageDto(savedMessage);

                if (message.getConversation() != null) {
                        messagingTemplate.convertAndSend("/queue/conversation/" + message.getConversation().getId(),
                                        savedMessageDto);
                }

                return ResponseEntity.ok(ApiResponse.<MessageDto>builder()
                                .success(true)
                                .message("Thu hồi tin nhắn thành công")
                                .payload(savedMessageDto)
                                .build());
        }

        // WebSocket thu hồi tin nhắn realtime
        @MessageMapping("/conversation/{conversationId}/recall")
        @SendTo("/queue/conversation/{conversationId}")
        public MessageDto recallMessageWebSocket(
                        @DestinationVariable Long conversationId,
                        MessageDto messageDto,
                        Principal principal) {
                UserDto userDto = userService.getUserByPhone(principal.getName());
                Long userId = userDto.getUserId();
                Message message = messageRepository.findById(messageDto.getId()).orElseThrow();
                if (!message.getSender().getUserId().equals(userId)) {
                        throw new AccessDeniedException("Bạn không có quyền thu hồi tin nhắn này");
                }
                if (message.getCreatedAt().isBefore(LocalDateTime.now().minusDays(1))) {
                        throw new AccessDeniedException("Tin nhắn đã quá thời gian cho phép thu hồi");
                }
                message.setContent("tin nhắn đã thu hồi");
                Message savedMessage = messageRepository.save(message);
                return conversationService.mapToMessageDto(savedMessage);
        }

        @Operation(summary = "Xoá tin nhắn phía tôi", description = "Chỉ user hiện tại không nhìn thấy tin nhắn nữa")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Xoá tin nhắn thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy tin nhắn")
        })
        @DeleteMapping("/messages/{messageId}")
        public ResponseEntity<ApiResponse<Void>> deleteMessageForMe(
                        @Parameter(description = "ID tin nhắn", required = true) @PathVariable Long messageId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
                UserDto userDto = userService.getUserByPhone(userDetails.getUsername());
                Long userId = userDto.getUserId();
                User user = userRepository.findById(userId).orElse(null);
                Message message = messageRepository.findById(messageId).orElse(null);

                if (user == null || message == null) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .body(ApiResponse.<Void>builder()
                                                        .success(false)
                                                        .message("Không tìm thấy tin nhắn hoặc người dùng")
                                                        .build());
                }

                if (deletedMessageRepository.findByUserAndMessage(user, message).isEmpty()) {
                        DeletedMessage deletedMessage = new DeletedMessage();
                        deletedMessage.setUser(user);
                        deletedMessage.setMessage(message);
                        deletedMessageRepository.save(deletedMessage);
                }

                return ResponseEntity.ok(ApiResponse.<Void>builder()
                                .success(true)
                                .message("Xóa tin nhắn thành công")
                                .build());
        }

        @Operation(summary = "Chuyển tiếp tin nhắn", description = "Chuyển tiếp một tin nhắn sang cuộc trò chuyện khác")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chuyển tiếp tin nhắn thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy tin nhắn hoặc cuộc trò chuyện")
        })
        @PostMapping("/messages/{messageId}/forward/{conversationId}")
        public ResponseEntity<ApiResponse<MessageDto>> forwardMessage(
                        @Parameter(description = "ID tin nhắn gốc", required = true) @PathVariable Long messageId,
                        @Parameter(description = "ID cuộc trò chuyện đích", required = true) @PathVariable Long conversationId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
                UserDto userDto = userService.getUserByPhone(userDetails.getUsername());
                Long senderId = userDto.getUserId();

                Message originalMessage = messageRepository.findById(messageId).orElse(null);
                if (originalMessage == null) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .body(ApiResponse.<MessageDto>builder()
                                                        .success(false)
                                                        .message("Không tìm thấy tin nhắn gốc")
                                                        .build());
                }

                if (!conversationService.isUserInConversation(conversationId, senderId)) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .body(ApiResponse.<MessageDto>builder()
                                                        .success(false)
                                                        .message("Bạn không có quyền chuyển tiếp tin nhắn đến cuộc trò chuyện này")
                                                        .build());
                }

                Message newMessage = new Message();
                newMessage.setSender(userRepository.findById(senderId).orElseThrow());
                newMessage.setConversation(conversationRepository.findById(conversationId).orElseThrow());
                newMessage.setContent(originalMessage.getContent());
                newMessage.setType(originalMessage.getType());
                newMessage.setCreatedAt(LocalDateTime.now());

                Message savedMessage = messageRepository.save(newMessage);
                MessageDto savedMessageDto = conversationService.mapToMessageDto(savedMessage);
                messagingTemplate.convertAndSend("/queue/conversation/" + conversationId, savedMessageDto);

                return ResponseEntity.ok(ApiResponse.<MessageDto>builder()
                                .success(true)
                                .message("Chuyển tiếp tin nhắn thành công")
                                .payload(savedMessageDto)
                                .build());
        }

        @Operation(summary = "Upload file trong cuộc trò chuyện", description = "Upload file và gửi như một tin nhắn trong cuộc trò chuyện")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Upload file thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền gửi file trong cuộc trò chuyện này"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy cuộc trò chuyện")
        })
        @PostMapping(value = "/{conversationId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<ApiResponse<List<MessageDto>>> uploadFile(
                        @Parameter(description = "Conversation ID", required = true) @PathVariable Long conversationId,
                        @Parameter(description = "Các file cần upload", required = true, content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)) @RequestPart("files") MultipartFile[] files,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
                try {
                        UserDto userDto = userService.getUserByPhone(userDetails.getUsername());
                        Long senderId = userDto.getUserId();

                        if (!conversationService.isUserInConversation(conversationId, senderId)) {
                                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                                .body(ApiResponse.<List<MessageDto>>builder()
                                                                .success(false)
                                                                .message("Bạn không có quyền gửi file trong cuộc trò chuyện này")
                                                                .build());
                        }

                        // Kiểm tra nếu cuộc trò chuyện đã bị chặn
                        if (conversationService.isConversationBlocked(conversationId)) {
                                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                                .body(ApiResponse.<List<MessageDto>>builder()
                                                                .success(false)
                                                                .message("Tin nhắn đã bị chặn trong cuộc trò chuyện này")
                                                                .build());
                        }

                        List<MessageDto> savedMessages = new ArrayList<>();

                        for (MultipartFile file : files) {
                                // Upload file và lưu thông tin attachment
                                Attachments attachment = attachmentsService.uploadFile(file);

                                // Xác định loại tin nhắn dựa trên loại file
                                MessageType messageType = determineMessageType(file.getContentType());

                                // Tạo tin nhắn mới với file đính kèm
                                Message message = new Message();
                                message.setSender(userRepository.findById(senderId).orElseThrow());
                                message.setConversation(conversationRepository.findById(conversationId).orElseThrow());
                                message.setContent(attachment.getName());
                                message.setType(messageType);
                                message.setCreatedAt(LocalDateTime.now());

                                // Thiết lập mối quan hệ hai chiều
                                message.getAttachments().add(attachment);
                                attachment.setMessage(message);

                                Message savedMessage = messageRepository.save(message);
                                MessageDto savedMessageDto = conversationService.mapToMessageDto(savedMessage);
                                savedMessages.add(savedMessageDto);

                                // Gửi thông báo realtime qua WebSocket
                                messagingTemplate.convertAndSend("/queue/conversation/" + conversationId,
                                                savedMessageDto);
                        }

                        return ResponseEntity.ok(ApiResponse.<List<MessageDto>>builder()
                                        .success(true)
                                        .message("Upload " + files.length + " file thành công")
                                        .payload(savedMessages)
                                        .build());

                } catch (IOException e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ApiResponse.<List<MessageDto>>builder()
                                                        .success(false)
                                                        .message("Lỗi khi upload file: " + e.getMessage())
                                                        .build());
                }
        }

        // WebSocket upload file realtime
        @MessageMapping("/conversation/{conversationId}/upload")
        @SendTo("/queue/conversation/{conversationId}")
        public List<MessageDto> handleFileUpload(
                        @DestinationVariable Long conversationId,
                        @RequestParam("files") MultipartFile[] files,
                        Principal principal) throws IOException {
                UserDto userDto = userService.getUserByPhone(principal.getName());
                Long senderId = userDto.getUserId();

                if (!conversationService.isUserInConversation(conversationId, senderId)) {
                        throw new AccessDeniedException("Bạn không có quyền gửi file trong cuộc trò chuyện này");
                }

                // Kiểm tra nếu cuộc trò chuyện đã bị chặn
                if (conversationService.isConversationBlocked(conversationId)) {
                        throw new AccessDeniedException("Tin nhắn đã bị chặn trong cuộc trò chuyện này");
                }

                List<MessageDto> messages = new ArrayList<>();

                for (MultipartFile file : files) {
                        Attachments attachment = attachmentsService.uploadFile(file);

                        // Xác định loại tin nhắn dựa trên loại file
                        MessageType messageType = determineMessageType(file.getContentType());

                        Message message = new Message();
                        message.setSender(userRepository.findById(senderId).orElseThrow());
                        message.setConversation(conversationRepository.findById(conversationId).orElseThrow());
                        message.setContent(attachment.getName());
                        message.setType(messageType);
                        message.setCreatedAt(LocalDateTime.now());

                        // Thiết lập mối quan hệ hai chiều
                        message.getAttachments().add(attachment);
                        attachment.setMessage(message);

                        Message savedMessage = messageRepository.save(message);
                        messages.add(conversationService.mapToMessageDto(savedMessage));
                }

                return messages;
        }

        private MessageType determineMessageType(String contentType) {
                if (contentType == null) {
                        return MessageType.DOCUMENT;
                }

                if (contentType.startsWith("image/")) {
                        return MessageType.IMAGE;
                } else if (contentType.startsWith("video/")) {
                        return MessageType.VIDEO;
                } else if (contentType.startsWith("audio/")) {
                        return MessageType.AUDIO;
                } else {
                        return MessageType.DOCUMENT;
                }
        }

        // Thêm endpoint chặn tin nhắn
        @Operation(summary = "Chặn tin nhắn", description = "Chặn tin nhắn trong cuộc trò chuyện 1-1")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chặn tin nhắn thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền chặn tin nhắn"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy cuộc trò chuyện")
        })
        @PostMapping("/{conversationId}/block")
        public ResponseEntity<ApiResponse<Void>> blockConversation(
                        @PathVariable Long conversationId,
                        @AuthenticationPrincipal UserDetails userDetails) {
                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                conversationService.blockConversation(conversationId, userId);
                return ResponseEntity.ok(ApiResponse.<Void>builder()
                                .success(true)
                                .message("Chặn tin nhắn thành công")
                                .build());
        }

        @Operation(summary = "Bỏ chặn tin nhắn", description = "Bỏ chặn tin nhắn trong cuộc trò chuyện 1-1")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Bỏ chặn tin nhắn thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền bỏ chặn tin nhắn"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy cuộc trò chuyện")
        })
        @DeleteMapping("/{conversationId}/block")
        public ResponseEntity<ApiResponse<Void>> unblockConversation(
                        @PathVariable Long conversationId,
                        @AuthenticationPrincipal UserDetails userDetails) {
                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                conversationService.unblockConversation(conversationId, userId);
                return ResponseEntity.ok(ApiResponse.<Void>builder()
                                .success(true)
                                .message("Bỏ chặn tin nhắn thành công")
                                .build());
        }

        public static class ConversationRequest {
                private List<Long> participantIds;

                public List<Long> getParticipantIds() {
                        return participantIds;
                }

                public void setParticipantIds(List<Long> participantIds) {
                        this.participantIds = participantIds;
                }
        }
}