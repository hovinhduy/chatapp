package com.chatapp.service;

import com.chatapp.dto.request.FriendDto;
import com.chatapp.dto.request.UserDto;
import com.chatapp.dto.request.ConversationDto;
import com.chatapp.dto.response.FriendAcceptanceResponse;
import com.chatapp.enums.FriendStatus;
import com.chatapp.exception.BadRequestException;
import com.chatapp.exception.ResourceNotFoundException;
import com.chatapp.model.Friend;
import com.chatapp.model.User;
import com.chatapp.repository.FriendRepository;
import com.chatapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service xử lý các thao tác liên quan đến kết bạn và quản lý bạn bè
 */
@Service
public class FriendService {

    private static final Logger logger = LoggerFactory.getLogger(FriendService.class);

    private final FriendRepository friendRepository;
    private final UserRepository userRepository;
    private final ConversationService conversationService;

    /**
     * Constructor để dependency injection
     * 
     * @param friendRepository    Repository xử lý thao tác với database của Friend
     * @param userRepository      Repository xử lý thao tác với database của User
     * @param conversationService Service xử lý các thao tác liên quan đến cuộc trò
     *                            chuyện
     */
    public FriendService(FriendRepository friendRepository, UserRepository userRepository,
            ConversationService conversationService) {
        this.friendRepository = friendRepository;
        this.userRepository = userRepository;
        this.conversationService = conversationService;
    }

    /**
     * Gửi lời mời kết bạn
     * 
     * @param senderId   ID của người gửi lời mời
     * @param receiverId ID của người nhận lời mời
     * @return FriendDto Thông tin về mối quan hệ bạn bè được tạo
     * @throws BadRequestException       Nếu người dùng gửi lời mời cho chính mình
     *                                   hoặc đã tồn tại quan hệ bạn bè
     * @throws ResourceNotFoundException Nếu không tìm thấy người gửi hoặc người
     *                                   nhận
     */
    @Transactional
    public FriendDto sendFriendRequest(Long senderId, Long receiverId) {
        if (senderId.equals(receiverId)) {
            throw new BadRequestException("Không thể gửi lời mời kết bạn cho chính mình");
        }

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người gửi với id: " + senderId));

        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người nhận với id: " + receiverId));

        // Kiểm tra xem mối quan hệ bạn bè đã tồn tại chưa
        if (friendRepository.findByUsers(sender, receiver).isPresent()) {
            throw new BadRequestException("Mối quan hệ bạn bè đã tồn tại");
        }

        Friend friend = new Friend();
        friend.setUser1(sender);
        friend.setUser2(receiver);
        friend.setStatus(FriendStatus.PENDING);

        Friend savedFriend = friendRepository.save(friend);
        return mapToDto(savedFriend);
    }

    /**
     * Chấp nhận lời mời kết bạn và tự động tạo cuộc trò chuyện 1-1
     * 
     * @param friendshipId ID của mối quan hệ bạn bè
     * @param userId       ID của người dùng đang thực hiện hành động
     * @return FriendDto Thông tin về mối quan hệ bạn bè sau khi cập nhật
     * @throws ResourceNotFoundException Nếu không tìm thấy mối quan hệ bạn bè
     * @throws BadRequestException       Nếu người dùng không phải là người nhận lời
     *                                   mời hoặc lời mời không ở trạng thái chờ
     */
    @Transactional
    public FriendDto acceptFriendRequest(Long friendshipId, Long userId) {
        Friend friend = friendRepository.findById(friendshipId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy mối quan hệ bạn bè với id: " + friendshipId));

        // Kiểm tra người dùng có phải là người nhận lời mời không
        if (!friend.getUser2().getUserId().equals(userId)) {
            throw new BadRequestException("Chỉ người nhận mới có thể chấp nhận lời mời kết bạn");
        }

        // Kiểm tra trạng thái có phải là CHỜ không
        if (friend.getStatus() != FriendStatus.PENDING) {
            throw new BadRequestException("Lời mời kết bạn không ở trạng thái chờ xử lý");
        }

        friend.setStatus(FriendStatus.ACCEPTED);
        Friend updatedFriend = friendRepository.save(friend);

        // Tự động tạo cuộc trò chuyện 1-1 giữa hai người bạn
        try {
            ConversationDto conversation = conversationService.getOrCreateOneToOneConversation(
                    friend.getUser1().getUserId(),
                    friend.getUser2().getUserId());
            logger.info("Đã tạo cuộc trò chuyện cho hai người bạn: {} và {}",
                    friend.getUser1().getUserId(), friend.getUser2().getUserId());

            // Gửi tin nhắn thông báo kết bạn thành công
            if (conversation != null) {
                try {
                    conversationService.sendFriendshipNotification(
                            conversation.getId(),
                            friend.getUser1().getDisplayName(),
                            friend.getUser2().getDisplayName());
                    logger.info("Đã gửi tin nhắn thông báo kết bạn thành công cho conversation: {}",
                            conversation.getId());
                } catch (Exception notifyException) {
                    logger.error("Lỗi khi gửi tin nhắn thông báo kết bạn: {}", notifyException.getMessage(),
                            notifyException);
                }
            }
        } catch (Exception e) {
            // Log lỗi nhưng không làm fail transaction chính
            logger.error("Lỗi khi tạo cuộc trò chuyện cho hai người bạn {} và {}: {}",
                    friend.getUser1().getUserId(), friend.getUser2().getUserId(), e.getMessage(), e);
        }

        return mapToDto(updatedFriend);
    }

    /**
     * Chấp nhận lời mời kết bạn và trả về thông tin cuộc trò chuyện được tạo
     * 
     * @param friendshipId ID của mối quan hệ bạn bè
     * @param userId       ID của người dùng đang thực hiện hành động
     * @return FriendAcceptanceResponse Thông tin về mối quan hệ bạn bè và cuộc trò
     *         chuyện được tạo
     * @throws ResourceNotFoundException Nếu không tìm thấy mối quan hệ bạn bè
     * @throws BadRequestException       Nếu người dùng không phải là người nhận lời
     *                                   mời hoặc lời mời không ở trạng thái chờ
     */
    @Transactional
    public FriendAcceptanceResponse acceptFriendRequestWithConversation(Long friendshipId, Long userId) {
        Friend friend = friendRepository.findById(friendshipId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy mối quan hệ bạn bè với id: " + friendshipId));

        // Kiểm tra người dùng có phải là người nhận lời mời không
        if (!friend.getUser2().getUserId().equals(userId)) {
            throw new BadRequestException("Chỉ người nhận mới có thể chấp nhận lời mời kết bạn");
        }

        // Kiểm tra trạng thái có phải là CHỜ không
        if (friend.getStatus() != FriendStatus.PENDING) {
            throw new BadRequestException("Lời mời kết bạn không ở trạng thái chờ xử lý");
        }

        friend.setStatus(FriendStatus.ACCEPTED);
        Friend updatedFriend = friendRepository.save(friend);

        // Tự động tạo cuộc trò chuyện 1-1 giữa hai người bạn
        ConversationDto conversation = null;
        try {
            conversation = conversationService.getOrCreateOneToOneConversation(
                    friend.getUser1().getUserId(),
                    friend.getUser2().getUserId());
            logger.info("Đã tạo cuộc trò chuyện cho hai người bạn: {} và {}",
                    friend.getUser1().getUserId(), friend.getUser2().getUserId());

            // Gửi tin nhắn thông báo kết bạn thành công
            if (conversation != null) {
                try {
                    conversationService.sendFriendshipNotification(
                            conversation.getId(),
                            friend.getUser1().getDisplayName(),
                            friend.getUser2().getDisplayName());
                    logger.info("Đã gửi tin nhắn thông báo kết bạn thành công cho conversation: {}",
                            conversation.getId());
                } catch (Exception notifyException) {
                    logger.error("Lỗi khi gửi tin nhắn thông báo kết bạn: {}", notifyException.getMessage(),
                            notifyException);
                }
            }
        } catch (Exception e) {
            // Log lỗi nhưng không làm fail transaction chính
            logger.error("Lỗi khi tạo cuộc trò chuyện cho hai người bạn {} và {}: {}",
                    friend.getUser1().getUserId(), friend.getUser2().getUserId(), e.getMessage(), e);
        }

        FriendDto friendDto = mapToDto(updatedFriend);
        return new FriendAcceptanceResponse(friendDto, conversation);
    }

    /**
     * Từ chối lời mời kết bạn
     * 
     * @param friendshipId ID của mối quan hệ bạn bè
     * @param userId       ID của người dùng đang thực hiện hành động
     * @return FriendDto Thông tin về mối quan hệ bạn bè bị từ chối
     * @throws ResourceNotFoundException Nếu không tìm thấy mối quan hệ bạn bè
     * @throws BadRequestException       Nếu người dùng không phải là người nhận lời
     *                                   mời hoặc lời mời không ở trạng thái chờ
     */
    @Transactional
    public FriendDto rejectFriendRequest(Long friendshipId, Long userId) {
        Friend friend = friendRepository.findById(friendshipId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy mối quan hệ bạn bè với id: " + friendshipId));

        // Kiểm tra người dùng có phải là người nhận lời mời không
        if (!friend.getUser2().getUserId().equals(userId)) {
            throw new BadRequestException("Chỉ người nhận mới có thể từ chối lời mời kết bạn");
        }

        // Kiểm tra trạng thái có phải là CHỜ không
        if (friend.getStatus() != FriendStatus.PENDING) {
            throw new BadRequestException("Lời mời kết bạn không ở trạng thái chờ xử lý");
        }

        friendRepository.delete(friend);
        return mapToDto(friend);
    }

    /**
     * Chặn một người bạn
     * 
     * @param userId   ID của người dùng thực hiện chặn
     * @param friendId ID của người bạn bị chặn
     * @return FriendDto Thông tin về mối quan hệ bạn bè sau khi cập nhật
     * @throws ResourceNotFoundException Nếu không tìm thấy người dùng, người bạn
     *                                   hoặc mối quan hệ bạn bè
     */
    @Transactional
    public FriendDto blockFriend(Long userId, Long friendId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + userId));

        User friendToBlock = userRepository.findById(friendId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bạn với id: " + friendId));

        Friend friendship = friendRepository.findByUsers(user, friendToBlock)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mối quan hệ bạn bè"));

        // Đặt người thực hiện chặn là user1
        if (friendship.getUser1().getUserId().equals(friendId)) {
            // Đảo vị trí để đảm bảo người chặn là user1
            User temp = friendship.getUser1();
            friendship.setUser1(friendship.getUser2());
            friendship.setUser2(temp);
        }

        friendship.setStatus(FriendStatus.BLOCKED);
        Friend updatedFriend = friendRepository.save(friendship);
        return mapToDto(updatedFriend);
    }

    /**
     * Bỏ chặn một người bạn
     * 
     * @param userId   ID của người dùng thực hiện bỏ chặn
     * @param friendId ID của người bạn bị chặn
     * @return FriendDto Thông tin về mối quan hệ bạn bè sau khi cập nhật
     * @throws ResourceNotFoundException Nếu không tìm thấy người dùng, người bạn
     *                                   hoặc mối quan hệ bạn bè
     * @throws BadRequestException       Nếu người dùng không phải là người đã chặn
     *                                   hoặc mối quan hệ không ở trạng thái bị chặn
     */
    @Transactional
    public FriendDto unblockFriend(Long userId, Long friendId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + userId));

        User friendToUnblock = userRepository.findById(friendId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bạn với id: " + friendId));

        Friend friendship = friendRepository.findByUsers(user, friendToUnblock)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mối quan hệ bạn bè"));

        // Kiểm tra người dùng có phải là người đã chặn không
        if (!friendship.getUser1().getUserId().equals(userId) ||
                friendship.getStatus() != FriendStatus.BLOCKED) {
            throw new BadRequestException("Bạn không thể bỏ chặn người dùng này");
        }

        friendship.setStatus(FriendStatus.ACCEPTED);
        Friend updatedFriend = friendRepository.save(friendship);
        return mapToDto(updatedFriend);
    }

    /**
     * Lấy danh sách bạn bè của người dùng
     * 
     * @param userId ID của người dùng
     * @return List<FriendDto> Danh sách bạn bè
     * @throws ResourceNotFoundException Nếu không tìm thấy người dùng
     */
    public List<FriendDto> getFriends(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + userId));

        return friendRepository.findAcceptedFriendships(user).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Tìm kiếm bạn bè theo tên
     * 
     * @param userId     ID của người dùng
     * @param searchTerm Chuỗi tìm kiếm tên
     * @return List<FriendDto> Danh sách bạn bè phù hợp với tên tìm kiếm
     * @throws ResourceNotFoundException Nếu không tìm thấy người dùng
     */
    public List<FriendDto> searchFriendsByName(Long userId, String searchTerm) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + userId));

        return friendRepository.searchFriendsByName(user, searchTerm).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Lấy danh sách lời mời kết bạn đang chờ xử lý
     * 
     * @param userId ID của người dùng
     * @return List<FriendDto> Danh sách lời mời kết bạn
     * @throws ResourceNotFoundException Nếu không tìm thấy người dùng
     */
    public List<FriendDto> getPendingFriendRequests(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + userId));

        return friendRepository.findPendingFriendRequests(user).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Lấy danh sách lời mời kết bạn đã gửi
     * 
     * @param userId ID của người dùng
     * @return List<FriendDto> Danh sách lời mời kết bạn đã gửi
     * @throws ResourceNotFoundException Nếu không tìm thấy người dùng
     */
    public List<FriendDto> getSentFriendRequests(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + userId));

        return friendRepository.findSentFriendRequests(user).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Thu hồi lời mời kết bạn đã gửi
     * 
     * @param friendshipId ID của mối quan hệ bạn bè
     * @param userId       ID của người dùng đang thực hiện hành động
     * @return FriendDto Thông tin về lời mời kết bạn đã thu hồi
     * @throws ResourceNotFoundException Nếu không tìm thấy mối quan hệ bạn bè
     * @throws BadRequestException       Nếu người dùng không phải là người gửi lời
     *                                   mời hoặc lời mời không ở trạng thái chờ
     */
    @Transactional
    public FriendDto withdrawFriendRequest(Long friendshipId, Long userId) {
        Friend friend = friendRepository.findById(friendshipId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy mối quan hệ bạn bè với id: " + friendshipId));

        // Kiểm tra người dùng có phải là người gửi lời mời không
        if (!friend.getUser1().getUserId().equals(userId)) {
            throw new BadRequestException("Chỉ người gửi mới có thể thu hồi lời mời kết bạn");
        }

        // Kiểm tra trạng thái có phải là CHỜ không
        if (friend.getStatus() != FriendStatus.PENDING) {
            throw new BadRequestException("Lời mời kết bạn không ở trạng thái chờ xử lý");
        }

        friendRepository.delete(friend);
        return mapToDto(friend);
    }

    /**
     * Xóa kết bạn với một người dùng
     * 
     * @param friendshipId ID của mối quan hệ bạn bè
     * @param userId       ID của người dùng đang thực hiện hành động
     * @return FriendDto Thông tin về mối quan hệ bạn bè đã xóa
     * @throws ResourceNotFoundException Nếu không tìm thấy mối quan hệ bạn bè
     * @throws BadRequestException       Nếu người dùng không phải là một trong hai
     *                                   người trong mối quan hệ bạn bè
     *                                   hoặc mối quan hệ không ở trạng thái đã chấp
     *                                   nhận
     */
    @Transactional
    public FriendDto deleteFriend(Long friendshipId, Long userId) {
        Friend friend = friendRepository.findById(friendshipId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy mối quan hệ bạn bè với id: " + friendshipId));

        // Kiểm tra người dùng có phải là một trong hai người bạn không
        if (!friend.getUser1().getUserId().equals(userId) && !friend.getUser2().getUserId().equals(userId)) {
            throw new BadRequestException("Bạn chỉ có thể xóa mối quan hệ bạn bè của chính mình");
        }

        // Kiểm tra trạng thái đã chấp nhận
        if (friend.getStatus() != FriendStatus.ACCEPTED) {
            throw new BadRequestException("Chỉ có thể xóa mối quan hệ bạn bè đã được chấp nhận");
        }

        friendRepository.delete(friend);
        return mapToDto(friend);
    }

    /**
     * Chuyển đổi đối tượng Friend thành FriendDto
     * 
     * @param friend Đối tượng Friend cần chuyển đổi
     * @return FriendDto Đối tượng DTO tương ứng
     */
    private FriendDto mapToDto(Friend friend) {
        FriendDto dto = new FriendDto();
        dto.setId(friend.getId());

        UserDto user1Dto = new UserDto();
        user1Dto.setUserId(friend.getUser1().getUserId());
        user1Dto.setDisplayName(friend.getUser1().getDisplayName());
        user1Dto.setPhone(friend.getUser1().getPhone());
        user1Dto.setAvatarUrl(friend.getUser1().getAvatarUrl());
        dto.setUser1(user1Dto);

        UserDto user2Dto = new UserDto();
        user2Dto.setUserId(friend.getUser2().getUserId());
        user2Dto.setDisplayName(friend.getUser2().getDisplayName());
        user2Dto.setPhone(friend.getUser2().getPhone());
        user2Dto.setAvatarUrl(friend.getUser2().getAvatarUrl());
        dto.setUser2(user2Dto);

        dto.setStatus(friend.getStatus());
        dto.setCreatedAt(friend.getCreatedAt());

        // Thiết lập senderId và receiverId cho WebSocket notification
        dto.setSenderId(friend.getUser1().getUserId());
        dto.setReceiverId(friend.getUser2().getUserId());

        return dto;
    }
}