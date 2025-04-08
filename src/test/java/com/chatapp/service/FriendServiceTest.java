package com.chatapp.service;

import com.chatapp.dto.request.FriendDto;
import com.chatapp.exception.BadRequestException;
import com.chatapp.exception.ResourceNotFoundException;
import com.chatapp.model.Friend;
import com.chatapp.model.User;
import com.chatapp.repository.FriendRepository;
import com.chatapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FriendServiceTest {

    @Mock
    private FriendRepository friendRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FriendService friendService;

    private User user1;
    private User user2;
    private Friend friendship;

    @BeforeEach
    void setUp() {
        user1 = new User();
        user1.setUserId(1L);
        user1.setDisplayName("User One");
        user1.setPhone("1234567890");
        user1.setPassword("password");
        user1.setCreatedAt(LocalDateTime.now());

        user2 = new User();
        user2.setUserId(2L);
        user2.setDisplayName("User Two");
        user2.setPhone("0987654321");
        user2.setPassword("password");
        user2.setCreatedAt(LocalDateTime.now());

        friendship = new Friend();
        friendship.setId(1L);
        friendship.setUser1(user1);
        friendship.setUser2(user2);
        friendship.setStatus(Friend.FriendStatus.PENDING);
        friendship.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void sendFriendRequest_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user2));
        when(friendRepository.findByUsers(user1, user2)).thenReturn(Optional.empty());
        when(friendRepository.save(any(Friend.class))).thenReturn(friendship);

        FriendDto result = friendService.sendFriendRequest(1L, 2L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(Friend.FriendStatus.PENDING, result.getStatus());
        verify(friendRepository, times(1)).save(any(Friend.class));
    }

    @Test
    void sendFriendRequest_ToSelf_ThrowsException() {
        assertThrows(BadRequestException.class, () -> {
            friendService.sendFriendRequest(1L, 1L);
        });

        verify(friendRepository, never()).save(any(Friend.class));
    }

    @Test
    void sendFriendRequest_UserNotFound_ThrowsException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            friendService.sendFriendRequest(1L, 2L);
        });

        verify(friendRepository, never()).save(any(Friend.class));
    }

    @Test
    void sendFriendRequest_AlreadyFriends_ThrowsException() {
        friendship.setStatus(Friend.FriendStatus.ACCEPTED);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user2));
        when(friendRepository.findByUsers(user1, user2)).thenReturn(Optional.of(friendship));

        assertThrows(BadRequestException.class, () -> {
            friendService.sendFriendRequest(1L, 2L);
        });

        verify(friendRepository, never()).save(any(Friend.class));
    }

    @Test
    void acceptFriendRequest_Success() {
        when(friendRepository.findById(1L)).thenReturn(Optional.of(friendship));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user2));
        when(friendRepository.save(any(Friend.class))).thenReturn(friendship);

        FriendDto result = friendService.acceptFriendRequest(1L, 2L);

        assertNotNull(result);
        assertEquals(Friend.FriendStatus.ACCEPTED, result.getStatus());
        verify(friendRepository, times(1)).save(any(Friend.class));
    }

    @Test
    void acceptFriendRequest_NotFound_ThrowsException() {
        when(friendRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            friendService.acceptFriendRequest(1L, 2L);
        });

        verify(friendRepository, never()).save(any(Friend.class));
    }

    @Test
    void acceptFriendRequest_NotReceiver_ThrowsException() {
        when(friendRepository.findById(1L)).thenReturn(Optional.of(friendship));
        when(userRepository.findById(3L)).thenReturn(Optional.of(new User()));

        assertThrows(BadRequestException.class, () -> {
            friendService.acceptFriendRequest(1L, 3L);
        });

        verify(friendRepository, never()).save(any(Friend.class));
    }

    @Test
    void getFriends_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(friendRepository.findAcceptedFriendships(user1)).thenReturn(Arrays.asList(friendship));

        List<FriendDto> result = friendService.getFriends(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    void getPendingFriendRequests_Success() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user2));
        when(friendRepository.findPendingFriendRequests(user2)).thenReturn(Arrays.asList(friendship));

        List<FriendDto> result = friendService.getPendingFriendRequests(2L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals(Friend.FriendStatus.PENDING, result.get(0).getStatus());
    }
}