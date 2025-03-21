package com.chatapp.repository;

import com.chatapp.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("SELECT c FROM Conversation c JOIN c.conversationUsers cu WHERE cu.user.userId = :userId")
    List<Conversation> findByParticipantId(@Param("userId") Long userId);

    @Query("SELECT c FROM Conversation c " +
            "JOIN c.conversationUsers cu1 " +
            "JOIN c.conversationUsers cu2 " +
            "WHERE c.type = 'ONE_TO_ONE' " +
            "AND cu1.user.userId = :userId1 " +
            "AND cu2.user.userId = :userId2")
    Optional<Conversation> findOneToOneConversation(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
}