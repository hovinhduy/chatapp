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
import com.chatapp.repository.GroupRepository;
import com.chatapp.model.Group;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.ArrayList;

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

        @Autowired
        private GroupRepository groupRepository;

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

        public Page<MessageDto> getPagedMessagesByConversationId(Long conversationId, Long userId, Pageable pageable) {
                // Kiểm tra xem người dùng có trong cuộc trò chuyện không
                boolean isParticipant = conversationUserRepository.existsByConversationIdAndUserId(conversationId,
                                userId);
                if (!isParticipant) {
                        throw new ResourceNotFoundException(
                                        "Không tìm thấy cuộc trò chuyện hoặc người dùng không phải là thành viên");
                }

                // Lấy danh sách tin nhắn đã xóa của người dùng
                User user = userRepository.findById(userId).orElse(null);
                List<Long> deletedMessageIds = new ArrayList<>();
                if (user != null) {
                        deletedMessageIds = deletedMessageRepository.findByUser(user)
                                        .stream().map(dm -> dm.getMessage().getMessageId()).toList();
                }

                // Lọc tin nhắn đã bị xóa trên server trước khi trả về client
                final List<Long> finalDeletedMessageIds = deletedMessageIds;

                // Lấy tin nhắn theo trang, sắp xếp từ mới đến cũ
                Page<Message> messagesPage = messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId,
                                pageable);

                // Map sang DTO và bỏ qua các tin nhắn đã xóa
                return messagesPage.map(message -> {
                        if (finalDeletedMessageIds.contains(message.getMessageId())) {
                                // Nếu tin nhắn đã bị xóa bởi người dùng này, trả về null
                                return null;
                        }
                        return mapToMessageDto(message);
                }).map(dto -> dto); // Chỉ giữ lại các tin nhắn không null
        }

        private ConversationDto mapToDto(Conversation conversation) {
                ConversationDto dto = new ConversationDto();
                dto.setId(conversation.getId());
                dto.setType(conversation.getType());
                dto.setCreatedAt(conversation.getCreatedAt());

                // Xử lý khác nhau tùy theo loại cuộc trò chuyện
                if (conversation.getType() == ConversationType.ONE_TO_ONE) {
                        // Đối với cuộc trò chuyện 1-1, lấy danh sách người tham gia như cũ
                        List<ConversationUser> conversationUsers = conversationUserRepository
                                        .findByConversationId(conversation.getId());
                        List<UserDto> participants = conversationUsers.stream()
                                        .map(cu -> mapUserToDto(cu.getUser()))
                                        .collect(Collectors.toList());
                        dto.setParticipants(participants);
                } else if (conversation.getType() == ConversationType.GROUP) {
                        // Đối với cuộc trò chuyện nhóm, lấy thông tin nhóm
                        Optional<Group> groupOpt = groupRepository.findByConversationId(conversation.getId());
                        if (groupOpt.isPresent()) {
                                Group group = groupOpt.get();
                                // Thiết lập thông tin nhóm cho DTO
                                dto.setGroupId(group.getGroupId());
                                dto.setGroupName(group.getName());
                                dto.setGroupAvatarUrl(group.getAvatarUrl());
                        }

                        // Vẫn lấy danh sách người tham gia cho cả GROUP
                        List<ConversationUser> conversationUsers = conversationUserRepository
                                        .findByConversationId(conversation.getId());
                        List<UserDto> participants = conversationUsers.stream()
                                        .map(cu -> mapUserToDto(cu.getUser()))
                                        .collect(Collectors.toList());
                        dto.setParticipants(participants);
                }

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

        /**
         * Lấy đối tượng Conversation từ id
         *
         * @param conversationId ID của cuộc hội thoại cần lấy
         * @return Đối tượng Conversation
         * @throws ResourceNotFoundException Nếu không tìm thấy cuộc hội thoại
         */
        public Conversation getConversationById(Long conversationId) {
                return conversationRepository.findById(conversationId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy cuộc trò chuyện với id: " + conversationId));
        }

        /**
         * Thêm người dùng vào cuộc trò chuyện
         *
         * @param conversationId ID của cuộc trò chuyện
         * @param userId         ID của người dùng cần thêm
         * @throws ResourceNotFoundException Nếu không tìm thấy cuộc trò chuyện hoặc
         *                                   người dùng
         */
        @Transactional
        public void addUserToConversation(Long conversationId, Long userId) {
                Conversation conversation = getConversationById(conversationId);
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy người dùng với id: " + userId));

                // Kiểm tra xem người dùng đã trong cuộc trò chuyện chưa
                boolean isExist = conversationUserRepository.existsByConversationIdAndUserId(conversationId, userId);
                if (isExist) {
                        return; // Nếu đã tồn tại, không làm gì
                }

                // Thêm người dùng vào cuộc trò chuyện
                ConversationUser conversationUser = new ConversationUser();
                conversationUser.setConversation(conversation);
                conversationUser.setUser(user);
                conversationUserRepository.save(conversationUser);
        }

        /**
         * Xóa người dùng khỏi cuộc trò chuyện
         *
         * @param conversationId ID của cuộc trò chuyện
         * @param userId         ID của người dùng cần xóa
         * @throws ResourceNotFoundException Nếu không tìm thấy cuộc trò chuyện hoặc
         *                                   người dùng
         */
        @Transactional
        public void removeUserFromConversation(Long conversationId, Long userId) {
                // Kiểm tra xem người dùng có trong cuộc trò chuyện không
                ConversationUser conversationUser = conversationUserRepository
                                .findByConversationIdAndUserId(conversationId, userId)
                                .orElse(null);

                if (conversationUser != null) {
                        conversationUserRepository.delete(conversationUser);
                }
        }

        /**
         * Xóa một cuộc trò chuyện và tất cả dữ liệu liên quan
         *
         * @param conversationId ID của cuộc trò chuyện cần xóa
         * @throws ResourceNotFoundException Nếu không tìm thấy cuộc trò chuyện
         */
        @Transactional
        public void deleteConversation(Long conversationId) {
                Conversation conversation = conversationRepository.findById(conversationId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy cuộc trò chuyện với id: " + conversationId));

                // Xóa tất cả các tin nhắn đã bị xóa liên quan đến tin nhắn trong cuộc trò
                // chuyện
                List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
                for (Message message : messages) {
                        deletedMessageRepository.deleteAllByMessage(message);
                }

                // Xóa tất cả tin nhắn trong cuộc trò chuyện
                messageRepository.deleteAllByConversation(conversation);

                // Xóa tất cả các block cuộc trò chuyện
                conversationBlockRepository.deleteAllByConversation(conversation);

                // Xóa tất cả liên kết người dùng trong cuộc trò chuyện
                conversationUserRepository.deleteAllByConversation(conversation);

                // Cuối cùng xóa cuộc trò chuyện
                conversationRepository.delete(conversation);
        }

}