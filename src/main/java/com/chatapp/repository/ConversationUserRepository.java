package com.chatapp.repository;

import com.chatapp.model.ConversationUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationUserRepository extends JpaRepository<ConversationUser, Long> {

    List<ConversationUser> findByConversationId(Long conversationId);

    @Query("SELECT CASE WHEN COUNT(cu) > 0 THEN true ELSE false END FROM ConversationUser cu " +
            "WHERE cu.conversation.id = :conversationId AND cu.user.userId = :userId")
    boolean existsByConversationIdAndUserId(@Param("conversationId") Long conversationId, @Param("userId") Long userId);
}