package com.chatapp.repository;

import com.chatapp.model.Friend;
import com.chatapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendRepository extends JpaRepository<Friend, Long> {
    @Query("SELECT f FROM Friend f WHERE (f.user1 = :user1 AND f.user2 = :user2) OR (f.user1 = :user2 AND f.user2 = :user1)")
    Optional<Friend> findByUsers(@Param("user1") User user1, @Param("user2") User user2);

    @Query("SELECT f FROM Friend f WHERE (f.user1 = :user OR f.user2 = :user) AND f.status = 'ACCEPTED'")
    List<Friend> findAcceptedFriendships(@Param("user") User user);

    @Query("SELECT f FROM Friend f WHERE f.user2 = :user AND f.status = 'PENDING'")
    List<Friend> findPendingFriendRequests(@Param("user") User user);
}