package com.chatapp.service;

import com.chatapp.dto.ConversationDto;
import com.chatapp.dto.MessageDto;
import com.chatapp.dto.UserDto;
import com.chatapp.exception.ResourceNotFoundException;
import com.chatapp.model.Conversation;
import com.chatapp.model.ConversationUser;
import com.chatapp.model.Message;
import com.chatapp.model.User;
import com.chatapp.repository.ConversationRepository;
import com.chatapp.repository.ConversationUserRepository;
import com.chatapp.repository.MessageRepository;
import com.chatapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ConversationService {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationUserRepository conversationUserRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserService userService;

    public List<ConversationDto> getConversationsByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        List<Conversation> conversations = conversationRepository.findByParticipantId(userId);
        return conversations.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ConversationDto createConversation(Long creatorId, List<Long> participantIds) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + creatorId));

        Conversation conversation = new Conversation();
        conversation.setType(participantIds.size() == 1
                ? Conversation.ConversationType.ONE_TO_ONE
                : Conversation.ConversationType.GROUP);
        conversation.setCreatedAt(LocalDateTime.now());

        Conversation savedConversation = conversationRepository.save(conversation);

        // Thêm người tạo vào cuộc trò chuyện
        ConversationUser creatorConversationUser = new ConversationUser();
        creatorConversationUser.setConversation(savedConversation);
        creatorConversationUser.setUser(creator);
        conversationUserRepository.save(creatorConversationUser);

        // Thêm các người tham gia khác
        for (Long participantId : participantIds) {
            User participant = userRepository.findById(participantId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + participantId));

            ConversationUser conversationUser = new ConversationUser();
            conversationUser.setConversation(savedConversation);
            conversationUser.setUser(participant);
            conversationUserRepository.save(conversationUser);
        }

        return mapToDto(savedConversation);
    }

    @Transactional
    public ConversationDto getOrCreateOneToOneConversation(Long userId1, Long userId2) {
        // Tìm người dùng
        User user1 = userRepository.findById(userId1)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId1));

        User user2 = userRepository.findById(userId2)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId2));

        // Tìm cuộc trò chuyện 1-1 giữa hai người dùng
        Optional<Conversation> existingConversation = conversationRepository.findOneToOneConversation(userId1, userId2);

        if (existingConversation.isPresent()) {
            return mapToDto(existingConversation.get());
        }

        // Tạo cuộc trò chuyện mới
        Conversation conversation = new Conversation();
        conversation.setType(Conversation.ConversationType.ONE_TO_ONE);
        conversation.setCreatedAt(LocalDateTime.now());

        Conversation savedConversation = conversationRepository.save(conversation);

        // Thêm người dùng vào cuộc trò chuyện
        ConversationUser conversationUser1 = new ConversationUser();
        conversationUser1.setConversation(savedConversation);
        conversationUser1.setUser(user1);
        conversationUserRepository.save(conversationUser1);

        ConversationUser conversationUser2 = new ConversationUser();
        conversationUser2.setConversation(savedConversation);
        conversationUser2.setUser(user2);
        conversationUserRepository.save(conversationUser2);

        return mapToDto(savedConversation);
    }

    public List<MessageDto> getMessagesByConversationId(Long conversationId, Long userId) {
        // Kiểm tra xem người dùng có trong cuộc trò chuyện không
        boolean isParticipant = conversationUserRepository.existsByConversationIdAndUserId(conversationId, userId);
        if (!isParticipant) {
            throw new ResourceNotFoundException("Conversation not found or user is not a participant");
        }

        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        return messages.stream()
                .map(this::mapToMessageDto)
                .collect(Collectors.toList());
    }

    private ConversationDto mapToDto(Conversation conversation) {
        ConversationDto dto = new ConversationDto();
        dto.setId(conversation.getId());
        dto.setType(conversation.getType());
        dto.setCreatedAt(conversation.getCreatedAt());

        // Lấy danh sách người tham gia
        List<ConversationUser> conversationUsers = conversationUserRepository
                .findByConversationId(conversation.getId());
        List<UserDto> participants = conversationUsers.stream()
                .map(cu -> mapUserToDto(cu.getUser()))
                .collect(Collectors.toList());

        dto.setParticipants(participants);
        return dto;
    }

    public MessageDto mapToMessageDto(Message message) {
        MessageDto dto = new MessageDto();
        dto.setId(message.getMessageId());
        dto.setConversationId(message.getConversation().getId());
        dto.setSenderId(message.getSender().getUserId());
        dto.setContent(message.getContent());
        dto.setCreatedAt(message.getCreatedAt());
        return dto;
    }

    private UserDto mapUserToDto(User user) {
        UserDto dto = new UserDto();
        dto.setUserId(user.getUserId());
        dto.setPhone(user.getPhone());
        dto.setDisplayName(user.getDisplayName());
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
    }

    public boolean isUserInConversation(Long conversationId, Long userId) {
        return conversationUserRepository.existsByConversationIdAndUserId(conversationId, userId);
    }
}