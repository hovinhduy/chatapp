package com.chatapp.repository;

import com.chatapp.model.Friend;
import com.chatapp.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
public class FriendRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private FriendRepository friendRepository;

    private User user1;
    private User user2;
    private User user3;
    private Friend friendship1;
    private Friend friendship2;

    @BeforeEach
    void setUp() {
        user1 = new User();
        user1.setDisplayName("User One");
        user1.setPhone("1234567890");
        user1.setPassword("password");
        user1.setCreatedAt(LocalDateTime.now());
        entityManager.persist(user1);

        user2 = new User();
        user2.setDisplayName("User Two");
        user2.setPhone("0987654321");
        user2.setPassword("password");
        user2.setCreatedAt(LocalDateTime.now());
        entityManager.persist(user2);

        user3 = new User();
        user3.setDisplayName("User Three");
        user3.setPhone("1122334455");
        user3.setPassword("password");
        user3.setCreatedAt(LocalDateTime.now());
        entityManager.persist(user3);

        friendship1 = new Friend();
        friendship1.setUser1(user1);
        friendship1.setUser2(user2);
        friendship1.setStatus(Friend.FriendStatus.ACCEPTED);
        friendship1.setCreatedAt(LocalDateTime.now());
        entityManager.persist(friendship1);

        friendship2 = new Friend();
        friendship2.setUser1(user3);
        friendship2.setUser2(user1);
        friendship2.setStatus(Friend.FriendStatus.PENDING);
        friendship2.setCreatedAt(LocalDateTime.now());
        entityManager.persist(friendship2);

        entityManager.flush();
    }

    @Test
    void findByUsers_Success() {
        Optional<Friend> result = friendRepository.findByUsers(user1, user2);

        assertTrue(result.isPresent());
        assertEquals(friendship1.getId(), result.get().getId());
    }

    @Test
    void findByUsers_Reverse_Success() {
        Optional<Friend> result = friendRepository.findByUsers(user2, user1);

        assertTrue(result.isPresent());
        assertEquals(friendship1.getId(), result.get().getId());
    }

    @Test
    void findByUsers_NotFound() {
        Optional<Friend> result = friendRepository.findByUsers(user2, user3);

        assertFalse(result.isPresent());
    }

    @Test
    void findAcceptedFriendships_Success() {
        List<Friend> result = friendRepository.findAcceptedFriendships(user1);

        assertEquals(1, result.size());
        assertEquals(friendship1.getId(), result.get(0).getId());
    }

    @Test
    void findPendingFriendRequests_Success() {
        List<Friend> result = friendRepository.findPendingFriendRequests(user1);

        assertEquals(1, result.size());
        assertEquals(friendship2.getId(), result.get(0).getId());
    }
}