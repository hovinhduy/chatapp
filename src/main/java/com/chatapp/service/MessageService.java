package com.chatapp.service;

import com.chatapp.dto.request.MessageDto;
import com.chatapp.enums.MessageType;
import com.chatapp.exception.ResourceNotFoundException;
import com.chatapp.exception.UnauthorizedException;
import com.chatapp.model.Group;
import com.chatapp.model.Message;
import com.chatapp.model.User;
import com.chatapp.repository.GroupMemberRepository;
import com.chatapp.repository.GroupRepository;
import com.chatapp.repository.MessageRepository;
import com.chatapp.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service xử lý các thao tác liên quan đến tin nhắn trong ứng dụng chat
 */
@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final EncryptionService encryptionService;

    /**
     * Constructor để dependency injection
     * 
     * @param messageRepository     Repository xử lý thao tác với database của
     *                              Message
     * @param userRepository        Repository xử lý thao tác với database của User
     * @param groupRepository       Repository xử lý thao tác với database của Group
     * @param groupMemberRepository Repository xử lý thao tác với database của
     *                              GroupMember
     * @param messagingTemplate     Template để gửi tin nhắn WebSocket
     * @param encryptionService     Service để mã hóa và giải mã tin nhắn
     */
    public MessageService(
            MessageRepository messageRepository,
            UserRepository userRepository,
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            SimpMessagingTemplate messagingTemplate,
            EncryptionService encryptionService) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.messagingTemplate = messagingTemplate;
        this.encryptionService = encryptionService;
    }

    /**
     * Tạo tin nhắn trực tiếp giữa hai người dùng
     * 
     * @param senderId   ID của người gửi
     * @param messageDto Đối tượng chứa thông tin tin nhắn
     * @return MessageDto Thông tin tin nhắn đã được tạo
     * @throws ResourceNotFoundException Nếu không tìm thấy người gửi hoặc người
     *                                   nhận
     */
    @Transactional
    public MessageDto createDirectMessage(Long senderId, MessageDto messageDto) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người gửi với id: " + senderId));

        User receiver = userRepository.findById(messageDto.getReceiverId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy người nhận với id: " + messageDto.getReceiverId()));

        Message message = new Message();
        message.setSender(sender);
        message.setReceiver(receiver);

        // Mã hóa nội dung tin nhắn trước khi lưu vào database
        String encryptedContent = encryptionService.encrypt(messageDto.getContent());
        message.setContent(encryptedContent);

        message.setType(MessageType.TEXT);

        if (messageDto.getType() != null) {
            message.setType(MessageType.valueOf(messageDto.getType()));
        }

        Message savedMessage = messageRepository.save(message);
        return mapToDto(savedMessage);
    }

    /**
     * Tạo tin nhắn trong nhóm
     * 
     * @param senderId   ID của người gửi
     * @param messageDto Đối tượng chứa thông tin tin nhắn
     * @return MessageDto Thông tin tin nhắn đã được tạo
     * @throws ResourceNotFoundException Nếu không tìm thấy người gửi hoặc nhóm
     * @throws UnauthorizedException     Nếu người gửi không phải là thành viên của
     *                                   nhóm
     */
    @Transactional
    public MessageDto createGroupMessage(Long senderId, MessageDto messageDto) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người gửi với id: " + senderId));

        Group group = groupRepository.findById(messageDto.getGroupId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Không tìm thấy nhóm với id: " + messageDto.getGroupId()));

        if (!groupMemberRepository.existsByGroupAndUser(group, sender)) {
            throw new UnauthorizedException("Bạn không phải là thành viên của nhóm này");
        }

        Message message = new Message();
        message.setSender(sender);
        message.setGroup(group);

        // Mã hóa nội dung tin nhắn trước khi lưu vào database
        String encryptedContent = encryptionService.encrypt(messageDto.getContent());
        message.setContent(encryptedContent);

        message.setType(MessageType.TEXT);

        if (messageDto.getType() != null) {
            message.setType(MessageType.valueOf(messageDto.getType()));
        }

        Message savedMessage = messageRepository.save(message);
        return mapToDto(savedMessage);
    }

    /**
     * Lấy danh sách tin nhắn trực tiếp giữa hai người dùng
     * 
     * @param user1Id ID của người dùng thứ nhất
     * @param user2Id ID của người dùng thứ hai
     * @return List<MessageDto> Danh sách tin nhắn giữa hai người dùng
     * @throws ResourceNotFoundException Nếu không tìm thấy một trong hai người dùng
     */
    public List<MessageDto> getDirectMessages(Long user1Id, Long user2Id) {
        User user1 = userRepository.findById(user1Id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + user1Id));

        User user2 = userRepository.findById(user2Id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + user2Id));

        return messageRepository.findMessagesBetweenUsers(user1, user2).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Lấy danh sách tin nhắn trong nhóm
     * 
     * @param groupId ID của nhóm
     * @param userId  ID của người dùng đang yêu cầu
     * @return List<MessageDto> Danh sách tin nhắn trong nhóm
     * @throws ResourceNotFoundException Nếu không tìm thấy nhóm hoặc người dùng
     * @throws UnauthorizedException     Nếu người dùng không phải là thành viên của
     *                                   nhóm
     */
    public List<MessageDto> getGroupMessages(Long groupId, Long userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm với id: " + groupId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + userId));

        if (!groupMemberRepository.existsByGroupAndUser(group, user)) {
            throw new UnauthorizedException("Bạn không phải là thành viên của nhóm này");
        }

        return messageRepository.findByGroup(group).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Cập nhật nội dung tin nhắn
     * 
     * @param messageId  ID của tin nhắn cần cập nhật
     * @param newContent Nội dung mới
     * @param userId     ID của người dùng đang thực hiện cập nhật
     * @return MessageDto Thông tin tin nhắn sau khi cập nhật
     * @throws ResourceNotFoundException Nếu không tìm thấy tin nhắn
     * @throws UnauthorizedException     Nếu người dùng không phải là người gửi tin
     *                                   nhắn
     */
    @Transactional
    public MessageDto updateMessage(Long messageId, String newContent, Long userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tin nhắn với id: " + messageId));

        if (!message.getSender().getUserId().equals(userId)) {
            throw new UnauthorizedException("Bạn chỉ có thể chỉnh sửa tin nhắn của chính mình");
        }

        // Mã hóa nội dung tin nhắn mới trước khi lưu vào database
        String encryptedContent = encryptionService.encrypt(newContent);
        message.setContent(encryptedContent);

        Message updatedMessage = messageRepository.save(message);

        MessageDto updatedMessageDto = mapToDto(updatedMessage);

        if (message.getGroup() != null) {
            messagingTemplate.convertAndSend(
                    "/topic/group/" + message.getGroup().getGroupId() + "/update",
                    updatedMessageDto);
        } else if (message.getReceiver() != null) {
            messagingTemplate.convertAndSendToUser(
                    message.getReceiver().getPhone(),
                    "/queue/messages/update",
                    updatedMessageDto);
        }

        return updatedMessageDto;
    }

    /**
     * Xóa tin nhắn
     * 
     * @param messageId ID của tin nhắn cần xóa
     * @param userId    ID của người dùng đang thực hiện xóa
     * @throws ResourceNotFoundException Nếu không tìm thấy tin nhắn
     * @throws UnauthorizedException     Nếu người dùng không phải là người gửi tin
     *                                   nhắn
     */
    @Transactional
    public void deleteMessage(Long messageId, Long userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tin nhắn với id: " + messageId));

        if (!message.getSender().getUserId().equals(userId)) {
            throw new UnauthorizedException("Bạn chỉ có thể xóa tin nhắn của chính mình");
        }

        messageRepository.delete(message);

        if (message.getGroup() != null) {
            messagingTemplate.convertAndSend(
                    "/topic/group/" + message.getGroup().getGroupId() + "/delete",
                    messageId);
        } else if (message.getReceiver() != null) {
            messagingTemplate.convertAndSendToUser(
                    message.getReceiver().getPhone(),
                    "/queue/messages/delete",
                    messageId);
        }
    }

    /**
     * Gửi tin nhắn trực tiếp
     * 
     * @param messageDto Đối tượng chứa thông tin tin nhắn
     * @param senderId   ID của người gửi
     * @return MessageDto Thông tin tin nhắn đã được gửi
     */
    public MessageDto sendDirectMessage(MessageDto messageDto, Long senderId) {
        return createDirectMessage(senderId, messageDto);
    }

    /**
     * Gửi tin nhắn nhóm
     * 
     * @param messageDto Đối tượng chứa thông tin tin nhắn
     * @param senderId   ID của người gửi
     * @return MessageDto Thông tin tin nhắn đã được gửi
     */
    public MessageDto sendGroupMessage(MessageDto messageDto, Long senderId) {
        return createGroupMessage(senderId, messageDto);
    }

    /**
     * Chuyển đổi đối tượng Message thành MessageDto
     * 
     * @param message Đối tượng Message cần chuyển đổi
     * @return MessageDto Đối tượng DTO tương ứng
     */
    private MessageDto mapToDto(Message message) {
        MessageDto dto = new MessageDto();
        dto.setId(message.getMessageId());
        dto.setSenderId(message.getSender().getUserId());

        if (message.getReceiver() != null) {
            dto.setReceiverId(message.getReceiver().getUserId());
        }

        if (message.getGroup() != null) {
            dto.setGroupId(message.getGroup().getGroupId());
        }

        if (message.getConversation() != null) {
            dto.setConversationId(message.getConversation().getId());
        }

        // Giải mã nội dung tin nhắn trước khi trả về cho client
        String decryptedContent = encryptionService.decrypt(message.getContent());
        dto.setContent(decryptedContent);

        dto.setCreatedAt(message.getCreatedAt());

        dto.setType(message.getType().name());

        return dto;
    }
}