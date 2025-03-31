package com.chatapp.service;

import com.chatapp.dto.FriendDto;
import com.chatapp.dto.UserDto;
import com.chatapp.exception.BadRequestException;
import com.chatapp.exception.ResourceNotFoundException;
import com.chatapp.model.Friend;
import com.chatapp.model.User;
import com.chatapp.repository.FriendRepository;
import com.chatapp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service xử lý các thao tác liên quan đến kết bạn và quản lý bạn bè
 */
@Service
public class FriendService {

    private final FriendRepository friendRepository;
    private final UserRepository userRepository;

    /**
     * Constructor để dependency injection
     * 
     * @param friendRepository Repository xử lý thao tác với database của Friend
     * @param userRepository   Repository xử lý thao tác với database của User
     */
    public FriendService(FriendRepository friendRepository, UserRepository userRepository) {
        this.friendRepository = friendRepository;
        this.userRepository = userRepository;
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
            throw new BadRequestException("Cannot send friend request to yourself");
        }

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found with id: " + senderId));

        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new ResourceNotFoundException("Receiver not found with id: " + receiverId));

        // Check if friendship already exists
        if (friendRepository.findByUsers(sender, receiver).isPresent()) {
            throw new BadRequestException("Friendship already exists");
        }

        Friend friend = new Friend();
        friend.setUser1(sender);
        friend.setUser2(receiver);
        friend.setStatus(Friend.FriendStatus.PENDING);

        Friend savedFriend = friendRepository.save(friend);
        return mapToDto(savedFriend);
    }

    /**
     * Chấp nhận lời mời kết bạn
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
                .orElseThrow(() -> new ResourceNotFoundException("Friendship not found with id: " + friendshipId));

        // Check if user is the receiver of the request
        if (!friend.getUser2().getUserId().equals(userId)) {
            throw new BadRequestException("Only the receiver can accept the friend request");
        }

        // Check if status is PENDING
        if (friend.getStatus() != Friend.FriendStatus.PENDING) {
            throw new BadRequestException("Friend request is not pending");
        }

        friend.setStatus(Friend.FriendStatus.ACCEPTED);
        Friend updatedFriend = friendRepository.save(friend);
        return mapToDto(updatedFriend);
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
                .orElseThrow(() -> new ResourceNotFoundException("Friendship not found with id: " + friendshipId));

        // Check if user is the receiver of the request
        if (!friend.getUser2().getUserId().equals(userId)) {
            throw new BadRequestException("Only the receiver can reject the friend request");
        }

        // Check if status is PENDING
        if (friend.getStatus() != Friend.FriendStatus.PENDING) {
            throw new BadRequestException("Friend request is not pending");
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
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        User friendToBlock = userRepository.findById(friendId)
                .orElseThrow(() -> new ResourceNotFoundException("Friend not found with id: " + friendId));

        Friend friendship = friendRepository.findByUsers(user, friendToBlock)
                .orElseThrow(() -> new ResourceNotFoundException("Friendship not found"));

        // Set the user who initiated the block as user1
        if (friendship.getUser1().getUserId().equals(friendId)) {
            // Swap users to ensure blocker is user1
            User temp = friendship.getUser1();
            friendship.setUser1(friendship.getUser2());
            friendship.setUser2(temp);
        }

        friendship.setStatus(Friend.FriendStatus.BLOCKED);
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
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        User friendToUnblock = userRepository.findById(friendId)
                .orElseThrow(() -> new ResourceNotFoundException("Friend not found with id: " + friendId));

        Friend friendship = friendRepository.findByUsers(user, friendToUnblock)
                .orElseThrow(() -> new ResourceNotFoundException("Friendship not found"));

        // Check if the user is the one who blocked
        if (!friendship.getUser1().getUserId().equals(userId) ||
                friendship.getStatus() != Friend.FriendStatus.BLOCKED) {
            throw new BadRequestException("You cannot unblock this user");
        }

        friendship.setStatus(Friend.FriendStatus.ACCEPTED);
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
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

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
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

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
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

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
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        return friendRepository.findSentFriendRequests(user).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
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

        return dto;
    }
}