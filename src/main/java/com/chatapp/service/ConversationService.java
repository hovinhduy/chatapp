package com.chatapp.service;

import com.chatapp.dto.request.ConversationDto;
import com.chatapp.dto.request.MessageDto;
import com.chatapp.dto.request.UserDto;
import com.chatapp.dto.response.AttachmentDto;
import com.chatapp.enums.ConversationType;
import com.chatapp.exception.ResourceNotFoundException;
import com.chatapp.model.Conversation;
import com.chatapp.model.ConversationUser;
import com.chatapp.model.Message;
import com.chatapp.model.User;
import com.chatapp.model.DeletedMessage;
import com.chatapp.repository.ConversationRepository;
import com.chatapp.repository.ConversationUserRepository;
import com.chatapp.repository.MessageRepository;
import com.chatapp.repository.UserRepository;
import com.chatapp.repository.DeletedMessageRepository;
import com.chatapp.repository.ConversationBlockRepository;
import com.chatapp.model.ConversationBlock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Set;

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

        @Autowired
        private DeletedMessageRepository deletedMessageRepository;

        @Autowired
        private ConversationBlockRepository conversationBlockRepository;

        public List<ConversationDto> getConversationsByUserId(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy người dùng với id: " + userId));

                List<Conversation> conversations = conversationRepository.findByParticipantId(userId);
                return conversations.stream()
                                .map(this::mapToDto)
                                .collect(Collectors.toList());
        }

        @Transactional
        public ConversationDto createConversation(Long creatorId, List<Long> participantIds) {
                User creator = userRepository.findById(creatorId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy người dùng với id: " + creatorId));

                // Loại bỏ userId trùng lặp và loại bỏ cả creatorId nếu có trong participantIds
                List<Long> uniqueParticipantIds = participantIds.stream()
                                .filter(id -> !id.equals(creatorId))
                                .distinct()
                                .toList();

                Conversation conversation = new Conversation();
                conversation.setType(uniqueParticipantIds.size() == 1
                                ? ConversationType.ONE_TO_ONE
                                : ConversationType.GROUP);
                conversation.setCreatedAt(LocalDateTime.now());

                Conversation savedConversation = conversationRepository.save(conversation);

                // Thêm người tạo vào cuộc trò chuyện
                ConversationUser creatorConversationUser = new ConversationUser();
                creatorConversationUser.setConversation(savedConversation);
                creatorConversationUser.setUser(creator);
                conversationUserRepository.save(creatorConversationUser);

                // Thêm các người tham gia khác (không trùng creator và không trùng nhau)
                for (Long participantId : uniqueParticipantIds) {
                        User participant = userRepository.findById(participantId)
                                        .orElseThrow(
                                                        () -> new ResourceNotFoundException(
                                                                        "Không tìm thấy người dùng với id: "
                                                                                        + participantId));

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
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy người dùng với id: " + userId1));

                User user2 = userRepository.findById(userId2)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy người dùng với id: " + userId2));

                // Tìm cuộc trò chuyện 1-1 giữa hai người dùng
                Optional<Conversation> existingConversation = conversationRepository.findOneToOneConversation(userId1,
                                userId2);

                if (existingConversation.isPresent()) {
                        return mapToDto(existingConversation.get());
                }

                // Tạo cuộc trò chuyện mới
                Conversation conversation = new Conversation();
                conversation.setType(ConversationType.ONE_TO_ONE);
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
                boolean isParticipant = conversationUserRepository.existsByConversationIdAndUserId(conversationId,
                                userId);
                if (!isParticipant) {
                        throw new ResourceNotFoundException(
                                        "Không tìm thấy cuộc trò chuyện hoặc người dùng không phải là thành viên");
                }

                List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                        List<Long> deletedMessageIds = deletedMessageRepository.findByUser(user)
                                        .stream().map(dm -> dm.getMessage().getMessageId()).toList();
                        messages = messages.stream()
                                        .filter(m -> !deletedMessageIds.contains(m.getMessageId()))
                                        .collect(Collectors.toList());
                }
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
                dto.setType(message.getType().name());
                dto.setSenderName(message.getSender().getDisplayName());

                Set<AttachmentDto> attachmentDtos = message.getAttachments().stream()
                                .map(attachment -> new AttachmentDto(
                                                attachment.getId(),
                                                attachment.getName(),
                                                attachment.getType(),
                                                attachment.getUrl(),
                                                attachment.getSize(),
                                                attachment.getCreatedAt(),
                                                attachment.getUpdatedAt()))
                                .collect(Collectors.toSet());
                dto.setFiles(attachmentDtos);

                return dto;
        }

        private UserDto mapUserToDto(User user) {
                UserDto dto = new UserDto();
                dto.setUserId(user.getUserId());
                dto.setPhone(user.getPhone());
                dto.setDisplayName(user.getDisplayName());
                dto.setCreatedAt(user.getCreatedAt());
                dto.setAvatarUrl(user.getAvatarUrl());
                return dto;
        }

        public boolean isUserInConversation(Long conversationId, Long userId) {
                return conversationUserRepository.existsByConversationIdAndUserId(conversationId, userId);
        }

        @Transactional
        public void blockConversation(Long conversationId, Long userId) {
                Conversation conversation = conversationRepository.findById(conversationId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy cuộc trò chuyện với id: " + conversationId));
                if (conversation.getType() != ConversationType.ONE_TO_ONE) {
                        throw new IllegalArgumentException("Chỉ hỗ trợ chặn cho cuộc trò chuyện 1-1");
                }
                if (!isUserInConversation(conversationId, userId)) {
                        throw new AccessDeniedException("Bạn không phải thành viên của cuộc trò chuyện này");
                }
                if (!conversationBlockRepository.existsByConversation_IdAndUser_UserId(conversationId, userId)) {
                        ConversationBlock block = new ConversationBlock();
                        block.setConversation(conversation);
                        block.setUser(userRepository.findById(userId)
                                        .orElseThrow(() -> new ResourceNotFoundException(
                                                        "Không tìm thấy người dùng với id: " + userId)));
                        conversationBlockRepository.save(block);
                }
        }

        @Transactional
        public void unblockConversation(Long conversationId, Long userId) {
                conversationBlockRepository.deleteByConversation_IdAndUser_UserId(conversationId, userId);
        }

        public boolean isConversationBlocked(Long conversationId) {
                return conversationBlockRepository.existsByConversation_Id(conversationId);
        }

        public boolean isBlockedByUser(Long conversationId, Long userId) {
                return conversationBlockRepository.existsByConversation_IdAndUser_UserId(conversationId, userId);
        }
}