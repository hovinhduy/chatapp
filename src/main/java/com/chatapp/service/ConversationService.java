package com.chatapp.service;

import com.chatapp.dto.request.ConversationDto;
import com.chatapp.dto.request.MessageDto;
import com.chatapp.dto.request.UserDto;
import com.chatapp.dto.response.AttachmentDto;
import com.chatapp.dto.response.PageResponse;
import com.chatapp.dto.response.BlockedUserResponse;
import com.chatapp.enums.ConversationType;
import com.chatapp.enums.MessageType;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;

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

        @Autowired
        private SimpMessagingTemplate messagingTemplate;

        public List<ConversationDto> getConversationsByUserId(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy người dùng với id: " + userId));

                List<Conversation> conversations = conversationRepository.findByParticipantId(userId);
                return conversations.stream()
                                .map(conversation -> mapToDto(conversation, userId))
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

                return mapToDto(savedConversation, creator.getUserId());
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
                        return mapToDto(existingConversation.get(), user1.getUserId());
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

                return mapToDto(savedConversation, user1.getUserId());
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

        public PageResponse<MessageDto> getPagedMessagesByConversationId(Long conversationId, Long userId,
                        Pageable pageable) {
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
                Page<MessageDto> messageDtosPage = messagesPage.map(message -> {
                        if (finalDeletedMessageIds.contains(message.getMessageId())) {
                                // Nếu tin nhắn đã bị xóa bởi người dùng này, trả về null
                                return null;
                        }
                        return mapToMessageDto(message);
                }).map(dto -> dto); // Chỉ giữ lại các tin nhắn không null

                return PageResponse.of(messageDtosPage);
        }

        private ConversationDto mapToDto(Conversation conversation, Long userId) {
                ConversationDto dto = new ConversationDto();
                dto.setId(conversation.getId());
                dto.setType(conversation.getType());
                dto.setCreatedAt(conversation.getCreatedAt());

                // Thêm thông tin trạng thái chặn
                dto.setIsBlocked(isConversationBlocked(conversation.getId()));
                dto.setIsBlockedByMe(isBlockedByUser(conversation.getId(), userId));

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
                dto.setContent(message.getContent());
                dto.setCreatedAt(message.getCreatedAt());
                dto.setType(message.getType().name());

                // Xử lý trường hợp tin nhắn hệ thống không có sender
                if (message.getSender() != null) {
                        dto.setSenderId(message.getSender().getUserId());
                        dto.setSenderName(message.getSender().getDisplayName());
                } else {
                        // Đối với tin nhắn hệ thống
                        dto.setSenderId(null);
                        dto.setSenderName("Hệ thống");
                }

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
         * Tìm kiếm cuộc trò chuyện theo ID và trả về ConversationDto
         *
         * @param conversationId ID của cuộc trò chuyện cần tìm
         * @param userId         ID của người dùng yêu cầu (để kiểm tra quyền truy cập
         *                       và thông tin chặn)
         * @return ConversationDto Thông tin cuộc trò chuyện
         * @throws ResourceNotFoundException Nếu không tìm thấy cuộc trò chuyện
         * @throws AccessDeniedException     Nếu người dùng không có quyền truy cập cuộc
         *                                   trò chuyện
         */
        public ConversationDto findConversationDtoById(Long conversationId, Long userId) {
                // Kiểm tra cuộc trò chuyện có tồn tại không
                Conversation conversation = conversationRepository.findById(conversationId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy cuộc trò chuyện với id: " + conversationId));

                // Kiểm tra người dùng có quyền truy cập cuộc trò chuyện không
                boolean isParticipant = conversationUserRepository.existsByConversationIdAndUserId(conversationId,
                                userId);
                if (!isParticipant) {
                        throw new AccessDeniedException(
                                        "Bạn không có quyền truy cập cuộc trò chuyện này");
                }

                // Trả về ConversationDto với thông tin đầy đủ
                return mapToDto(conversation, userId);
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

        /**
         * Gửi tin nhắn hệ thống thông báo kết bạn thành công
         *
         * @param conversationId ID của cuộc trò chuyện
         * @param user1Name      Tên người dùng thứ nhất
         * @param user2Name      Tên người dùng thứ hai
         * @return MessageDto Thông tin tin nhắn đã được tạo
         */
        @Transactional
        public MessageDto sendFriendshipNotification(Long conversationId, String user1Name, String user2Name) {
                Conversation conversation = getConversationById(conversationId);

                // Tạo tin nhắn thông báo hệ thống
                Message systemMessage = new Message();
                systemMessage.setConversation(conversation);
                systemMessage.setContent(String.format("🎉 %s và %s đã trở thành bạn bè!",
                                user1Name, user2Name));
                systemMessage.setType(MessageType.SYSTEM_NOTIFICATION);
                // Không set sender cho tin nhắn hệ thống

                Message savedMessage = messageRepository.save(systemMessage);
                return mapToMessageDto(savedMessage);
        }

        /**
         * Tìm kiếm tin nhắn trong cuộc trò chuyện
         *
         * @param conversationId ID của cuộc trò chuyện
         * @param userId         ID của người dùng thực hiện tìm kiếm
         * @param searchTerm     Từ khóa tìm kiếm (có thể null)
         * @param senderId       ID của người gửi (có thể null)
         * @param startDate      Ngày bắt đầu (có thể null)
         * @param endDate        Ngày kết thúc (có thể null)
         * @param pageable       Thông tin phân trang
         * @return PageResponse<MessageDto> Danh sách tin nhắn tìm được
         */
        public PageResponse<MessageDto> searchMessages(Long conversationId, Long userId, String searchTerm,
                        Long senderId, LocalDateTime startDate, LocalDateTime endDate,
                        Pageable pageable) {
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

                final List<Long> finalDeletedMessageIds = deletedMessageIds;

                // Thực hiện tìm kiếm
                Page<Message> messagesPage = messageRepository.searchMessages(conversationId, searchTerm,
                                senderId, startDate, endDate, pageable);

                // Map sang DTO và bỏ qua các tin nhắn đã xóa
                Page<MessageDto> messageDtosPage = messagesPage.map(message -> {
                        if (finalDeletedMessageIds.contains(message.getMessageId())) {
                                return null;
                        }
                        return mapToMessageDto(message);
                }).map(dto -> dto);

                return PageResponse.of(messageDtosPage);
        }

        /**
         * Tìm kiếm tin nhắn trong tất cả cuộc trò chuyện của người dùng
         *
         * @param userId     ID của người dùng thực hiện tìm kiếm
         * @param searchTerm Từ khóa tìm kiếm (có thể null)
         * @param senderId   ID của người gửi (có thể null)
         * @param startDate  Ngày bắt đầu (có thể null)
         * @param endDate    Ngày kết thúc (có thể null)
         * @param pageable   Thông tin phân trang
         * @return PageResponse<MessageDto> Danh sách tin nhắn tìm được
         */
        public PageResponse<MessageDto> searchMessagesGlobal(Long userId, String searchTerm, Long senderId,
                        LocalDateTime startDate, LocalDateTime endDate,
                        Pageable pageable) {
                // Kiểm tra người dùng có tồn tại
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy người dùng với id: " + userId));

                // Lấy danh sách tin nhắn đã xóa của người dùng
                List<Long> deletedMessageIds = deletedMessageRepository.findByUser(user)
                                .stream().map(dm -> dm.getMessage().getMessageId()).toList();

                final List<Long> finalDeletedMessageIds = deletedMessageIds;

                // Thực hiện tìm kiếm
                Page<Message> messagesPage = messageRepository.searchMessagesGlobal(userId, searchTerm,
                                senderId, startDate, endDate, pageable);

                // Map sang DTO và bỏ qua các tin nhắn đã xóa
                Page<MessageDto> messageDtosPage = messagesPage.map(message -> {
                        if (finalDeletedMessageIds.contains(message.getMessageId())) {
                                return null;
                        }
                        return mapToMessageDto(message);
                }).map(dto -> dto);

                return PageResponse.of(messageDtosPage);
        }

        /**
         * Gửi tin nhắn hệ thống thông báo thêm thành viên mới vào nhóm
         *
         * @param conversationId ID của cuộc trò chuyện nhóm
         * @param memberName     Tên của thành viên mới được thêm
         * @param adderName      Tên của người thêm thành viên
         * @return MessageDto Thông tin tin nhắn đã được tạo
         */
        @Transactional
        public MessageDto sendGroupMemberAddedNotification(Long conversationId, String memberName, String adderName) {
                Conversation conversation = getConversationById(conversationId);

                // Tạo tin nhắn thông báo hệ thống
                Message systemMessage = new Message();
                systemMessage.setConversation(conversation);
                systemMessage.setContent(String.format(" %s đã được %s thêm vào nhóm",
                                memberName, adderName));
                systemMessage.setType(MessageType.SYSTEM_NOTIFICATION);
                systemMessage.setCreatedAt(LocalDateTime.now());
                // Không set sender cho tin nhắn hệ thống

                Message savedMessage = messageRepository.save(systemMessage);
                MessageDto messageDto = mapToMessageDto(savedMessage);

                // Gửi thông báo realtime qua WebSocket
                messagingTemplate.convertAndSend("/queue/conversation/" + conversationId, messageDto);

                return messageDto;
        }

        /**
         * Gửi tin nhắn hệ thống thông báo xóa thành viên khỏi nhóm
         *
         * @param conversationId ID của cuộc trò chuyện nhóm
         * @param memberName     Tên của thành viên bị xóa
         * @param removerName    Tên của người xóa thành viên
         * @return MessageDto Thông tin tin nhắn đã được tạo
         */
        @Transactional
        public MessageDto sendGroupMemberRemovedNotification(Long conversationId, String memberName,
                        String removerName) {
                Conversation conversation = getConversationById(conversationId);

                Message systemMessage = new Message();
                systemMessage.setConversation(conversation);
                systemMessage.setContent(String.format(" %s đã bị %s xóa khỏi nhóm",
                                memberName, removerName));
                systemMessage.setType(MessageType.SYSTEM_NOTIFICATION);
                systemMessage.setCreatedAt(LocalDateTime.now());

                Message savedMessage = messageRepository.save(systemMessage);
                MessageDto messageDto = mapToMessageDto(savedMessage);

                messagingTemplate.convertAndSend("/queue/conversation/" + conversationId, messageDto);

                return messageDto;
        }

        /**
         * Gửi tin nhắn hệ thống thông báo thành viên rời khỏi nhóm
         *
         * @param conversationId ID của cuộc trò chuyện nhóm
         * @param memberName     Tên của thành viên rời khỏi nhóm
         * @return MessageDto Thông tin tin nhắn đã được tạo
         */
        @Transactional
        public MessageDto sendGroupMemberLeftNotification(Long conversationId, String memberName) {
                Conversation conversation = getConversationById(conversationId);

                Message systemMessage = new Message();
                systemMessage.setConversation(conversation);
                systemMessage.setContent(String.format(" %s đã rời khỏi nhóm", memberName));
                systemMessage.setType(MessageType.SYSTEM_NOTIFICATION);
                systemMessage.setCreatedAt(LocalDateTime.now());

                Message savedMessage = messageRepository.save(systemMessage);
                MessageDto messageDto = mapToMessageDto(savedMessage);

                messagingTemplate.convertAndSend("/queue/conversation/" + conversationId, messageDto);

                return messageDto;
        }

        /**
         * Gửi tin nhắn hệ thống thông báo thăng cấp thành viên lên phó nhóm
         *
         * @param conversationId ID của cuộc trò chuyện nhóm
         * @param memberName     Tên của thành viên được thăng cấp
         * @param promoterName   Tên của người thăng cấp
         * @return MessageDto Thông tin tin nhắn đã được tạo
         */
        @Transactional
        public MessageDto sendGroupMemberPromotedNotification(Long conversationId, String memberName,
                        String promoterName) {
                Conversation conversation = getConversationById(conversationId);

                Message systemMessage = new Message();
                systemMessage.setConversation(conversation);
                systemMessage.setContent(String.format(" %s đã được %s thăng cấp thành phó nhóm",
                                memberName, promoterName));
                systemMessage.setType(MessageType.SYSTEM_NOTIFICATION);
                systemMessage.setCreatedAt(LocalDateTime.now());

                Message savedMessage = messageRepository.save(systemMessage);
                MessageDto messageDto = mapToMessageDto(savedMessage);

                messagingTemplate.convertAndSend("/queue/conversation/" + conversationId, messageDto);

                return messageDto;
        }

        /**
         * Gửi tin nhắn hệ thống thông báo hạ cấp thành viên xuống thành viên thường
         *
         * @param conversationId ID của cuộc trò chuyện nhóm
         * @param memberName     Tên của thành viên bị hạ cấp
         * @param demoterName    Tên của người hạ cấp
         * @return MessageDto Thông tin tin nhắn đã được tạo
         */
        @Transactional
        public MessageDto sendGroupMemberDemotedNotification(Long conversationId, String memberName,
                        String demoterName) {
                Conversation conversation = getConversationById(conversationId);

                Message systemMessage = new Message();
                systemMessage.setConversation(conversation);
                systemMessage.setContent(String.format(" %s đã được %s hạ cấp xuống thành viên thường",
                                memberName, demoterName));
                systemMessage.setType(MessageType.SYSTEM_NOTIFICATION);
                systemMessage.setCreatedAt(LocalDateTime.now());

                Message savedMessage = messageRepository.save(systemMessage);
                MessageDto messageDto = mapToMessageDto(savedMessage);

                messagingTemplate.convertAndSend("/queue/conversation/" + conversationId, messageDto);

                return messageDto;
        }

        /**
         * Gửi tin nhắn hệ thống thông báo chuyển quyền trưởng nhóm
         *
         * @param conversationId ID của cuộc trò chuyện nhóm
         * @param newLeaderName  Tên của trưởng nhóm mới
         * @param oldLeaderName  Tên của trưởng nhóm cũ
         * @return MessageDto Thông tin tin nhắn đã được tạo
         */
        @Transactional
        public MessageDto sendGroupLeadershipTransferredNotification(Long conversationId, String newLeaderName,
                        String oldLeaderName) {
                Conversation conversation = getConversationById(conversationId);

                Message systemMessage = new Message();
                systemMessage.setConversation(conversation);
                systemMessage.setContent(String.format(" Quyền trưởng nhóm đã được chuyển từ %s sang %s",
                                oldLeaderName, newLeaderName));
                systemMessage.setType(MessageType.SYSTEM_NOTIFICATION);
                systemMessage.setCreatedAt(LocalDateTime.now());

                Message savedMessage = messageRepository.save(systemMessage);
                MessageDto messageDto = mapToMessageDto(savedMessage);

                messagingTemplate.convertAndSend("/queue/conversation/" + conversationId, messageDto);

                return messageDto;
        }

        /**
         * Gửi tin nhắn hệ thống thông báo cập nhật thông tin nhóm
         *
         * @param conversationId ID của cuộc trò chuyện nhóm
         * @param updaterName    Tên của người cập nhật
         * @param updateType     Loại cập nhật (tên nhóm, ảnh đại diện, v.v.)
         * @param newValue       Giá trị mới (nếu cần)
         * @return MessageDto Thông tin tin nhắn đã được tạo
         */
        @Transactional
        public MessageDto sendGroupUpdatedNotification(Long conversationId, String updaterName, String updateType,
                        String newValue) {
                Conversation conversation = getConversationById(conversationId);

                String content;
                switch (updateType) {
                        case "name":
                                content = String.format(" %s đã thay đổi tên nhóm thành \"%s\"", updaterName,
                                                newValue);
                                break;
                        case "avatar":
                                content = String.format(" %s đã thay đổi ảnh đại diện nhóm", updaterName);
                                break;
                        default:
                                content = String.format(" %s đã cập nhật thông tin nhóm", updaterName);
                                break;
                }

                Message systemMessage = new Message();
                systemMessage.setConversation(conversation);
                systemMessage.setContent(content);
                systemMessage.setType(MessageType.SYSTEM_NOTIFICATION);
                systemMessage.setCreatedAt(LocalDateTime.now());

                Message savedMessage = messageRepository.save(systemMessage);
                MessageDto messageDto = mapToMessageDto(savedMessage);

                messagingTemplate.convertAndSend("/queue/conversation/" + conversationId, messageDto);

                return messageDto;
        }

        /**
         * Gửi tin nhắn hệ thống thông báo tạo nhóm mới
         *
         * @param conversationId ID của cuộc trò chuyện nhóm
         * @param creatorName    Tên của người tạo nhóm
         * @param groupName      Tên nhóm
         * @return MessageDto Thông tin tin nhắn đã được tạo
         */
        @Transactional
        public MessageDto sendGroupCreatedNotification(Long conversationId, String creatorName, String groupName) {
                Conversation conversation = getConversationById(conversationId);

                Message systemMessage = new Message();
                systemMessage.setConversation(conversation);
                systemMessage.setContent(String.format(" Nhóm \"%s\" đã được tạo bởi %s",
                                groupName, creatorName));
                systemMessage.setType(MessageType.SYSTEM_NOTIFICATION);
                systemMessage.setCreatedAt(LocalDateTime.now());

                Message savedMessage = messageRepository.save(systemMessage);
                MessageDto messageDto = mapToMessageDto(savedMessage);

                messagingTemplate.convertAndSend("/queue/conversation/" + conversationId, messageDto);

                return messageDto;
        }

        /**
         * Lấy danh sách tất cả user đang bị chặn của người dùng trong tất cả cuộc trò
         * chuyện
         *
         * @param userId ID của người dùng muốn lấy danh sách user bị chặn
         * @return List<BlockedUserResponse> Danh sách các user bị chặn kèm thông tin
         *         cuộc trò chuyện
         */
        public List<BlockedUserResponse> getBlockedUsers(Long userId) {
                // Kiểm tra người dùng có tồn tại
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy người dùng với id: " + userId));

                // Lấy tất cả conversation block của user này
                List<ConversationBlock> conversationBlocks = conversationBlockRepository.findByUser_UserId(userId);

                // Map sang BlockedUserResponse
                return conversationBlocks.stream()
                                .map(block -> {
                                        Conversation conversation = block.getConversation();

                                        // Chỉ xử lý cuộc trò chuyện 1-1
                                        if (conversation.getType() == ConversationType.ONE_TO_ONE) {
                                                // Tìm user khác trong cuộc trò chuyện (người bị chặn)
                                                List<ConversationUser> conversationUsers = conversationUserRepository
                                                                .findByConversationId(conversation.getId());

                                                User blockedUser = conversationUsers.stream()
                                                                .map(ConversationUser::getUser)
                                                                .filter(u -> !u.getUserId().equals(userId))
                                                                .findFirst()
                                                                .orElse(null);

                                                if (blockedUser != null) {
                                                        UserDto blockedUserDto = mapUserToDto(blockedUser);
                                                        return BlockedUserResponse.builder()
                                                                        .conversationId(conversation.getId())
                                                                        .blockedUser(blockedUserDto)
                                                                        .blockedAt(block.getCreatedAt())
                                                                        .build();
                                                }
                                        }
                                        return null;
                                })
                                .filter(response -> response != null)
                                .collect(Collectors.toList());
        }

        /**
         * Chuyển tiếp nhiều tin nhắn sang cuộc trò chuyện khác
         *
         * @param messageIds           Danh sách ID tin nhắn cần chuyển tiếp
         * @param targetConversationId ID cuộc trò chuyện đích
         * @param senderId             ID người gửi
         * @param additionalMessage    Tin nhắn kèm theo (tùy chọn)
         * @return List<MessageDto> Danh sách tin nhắn đã được chuyển tiếp
         * @throws ResourceNotFoundException Nếu không tìm thấy tin nhắn hoặc cuộc trò
         *                                   chuyện
         * @throws AccessDeniedException     Nếu không có quyền chuyển tiếp
         */
        @Transactional
        public List<MessageDto> forwardMultipleMessages(List<Long> messageIds, Long targetConversationId,
                        Long senderId) {
                // Kiểm tra quyền truy cập cuộc trò chuyện đích
                if (!isUserInConversation(targetConversationId, senderId)) {
                        throw new AccessDeniedException(
                                        "Bạn không có quyền chuyển tiếp tin nhắn đến cuộc trò chuyện này");
                }

                // Kiểm tra cuộc trò chuyện đích có bị chặn không
                if (isConversationBlocked(targetConversationId)) {
                        throw new AccessDeniedException("Tin nhắn đã bị chặn trong cuộc trò chuyện này");
                }

                User sender = userRepository.findById(senderId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy người gửi với id: " + senderId));

                Conversation targetConversation = conversationRepository.findById(targetConversationId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy cuộc trò chuyện đích với id: " + targetConversationId));

                List<MessageDto> forwardedMessages = new ArrayList<>();

                // Chuyển tiếp từng tin nhắn theo thứ tự
                for (Long messageId : messageIds) {
                        Message originalMessage = messageRepository.findById(messageId)
                                        .orElse(null);

                        if (originalMessage == null) {
                                // Bỏ qua tin nhắn không tồn tại và tiếp tục xử lý các tin nhắn khác
                                continue;
                        }

                        // Kiểm tra quyền truy cập tin nhắn gốc
                        if (originalMessage.getConversation() != null &&
                                        !isUserInConversation(originalMessage.getConversation().getId(), senderId)) {
                                // Bỏ qua tin nhắn không có quyền truy cập
                                continue;
                        }

                        // Tạo tin nhắn mới
                        Message forwardedMessage = new Message();
                        forwardedMessage.setSender(sender);
                        forwardedMessage.setConversation(targetConversation);

                        // Thêm prefix để chỉ ra đây là tin nhắn được chuyển tiếp
                        String forwardedContent = originalMessage.getContent();
                        forwardedMessage.setContent(forwardedContent);
                        forwardedMessage.setType(originalMessage.getType());
                        forwardedMessage.setCreatedAt(LocalDateTime.now());

                        // Xử lý attachments nếu có
                        if (!originalMessage.getAttachments().isEmpty()) {
                                // Copy attachments từ tin nhắn gốc
                                originalMessage.getAttachments().forEach(attachment -> {
                                        // Tạo attachment mới với cùng thông tin
                                        // Lưu ý: Trong thực tế có thể cần copy file vật lý
                                        forwardedMessage.getAttachments().add(attachment);
                                });
                        }

                        Message savedForwardedMessage = messageRepository.save(forwardedMessage);
                        MessageDto forwardedMessageDto = mapToMessageDto(savedForwardedMessage);
                        forwardedMessages.add(forwardedMessageDto);

                        // Gửi thông báo realtime
                        messagingTemplate.convertAndSend("/queue/conversation/" + targetConversationId,
                                        forwardedMessageDto);
                }

                return forwardedMessages;
        }

        /**
         * Xóa nhiều tin nhắn cùng lúc cho người dùng cụ thể (chỉ ẩn tin nhắn khỏi người
         * dùng đó)
         *
         * @param messageIds Danh sách ID tin nhắn cần xóa
         * @param userId     ID người dùng thực hiện xóa
         * @return int Số lượng tin nhắn đã xóa thành công
         * @throws IllegalArgumentException  Nếu danh sách tin nhắn không hợp lệ
         * @throws ResourceNotFoundException Nếu không tìm thấy người dùng
         */
        @Transactional
        public int deleteMultipleMessagesForUser(List<Long> messageIds, Long userId) {
                // Kiểm tra danh sách tin nhắn không được rỗng
                if (messageIds == null || messageIds.isEmpty()) {
                        throw new IllegalArgumentException("Danh sách tin nhắn không được rỗng");
                }

                // Giới hạn số lượng tin nhắn có thể xóa cùng lúc
                if (messageIds.size() > 50) {
                        throw new IllegalArgumentException("Không thể xóa quá 50 tin nhắn cùng lúc");
                }

                // Kiểm tra người dùng có tồn tại
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Không tìm thấy người dùng với id: " + userId));

                int deletedCount = 0;

                // Xử lý từng tin nhắn
                for (Long messageId : messageIds) {
                        try {
                                Message message = messageRepository.findById(messageId).orElse(null);

                                if (message == null) {
                                        // Bỏ qua tin nhắn không tồn tại
                                        continue;
                                }

                                // Kiểm tra quyền truy cập tin nhắn
                                // Người dùng phải là thành viên của cuộc trò chuyện chứa tin nhắn này
                                if (message.getConversation() != null &&
                                                !isUserInConversation(message.getConversation().getId(), userId)) {
                                        // Bỏ qua tin nhắn không có quyền truy cập
                                        continue;
                                }

                                // Kiểm tra xem tin nhắn đã được xóa bởi người dùng này chưa
                                Optional<DeletedMessage> existingDeletedMessage = deletedMessageRepository
                                                .findByUserAndMessage(user, message);

                                if (existingDeletedMessage.isEmpty()) {
                                        // Tạo bản ghi xóa tin nhắn mới
                                        DeletedMessage deletedMessage = new DeletedMessage();
                                        deletedMessage.setUser(user);
                                        deletedMessage.setMessage(message);
                                        deletedMessageRepository.save(deletedMessage);

                                        deletedCount++;
                                }

                        } catch (Exception e) {
                                // Log lỗi và tiếp tục xử lý các tin nhắn khác
                                System.err.println("Lỗi khi xóa tin nhắn ID " + messageId + ": " + e.getMessage());
                        }
                }

                return deletedCount;
        }

}