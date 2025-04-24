package com.chatapp.repository;

import com.chatapp.model.Conversation;
import com.chatapp.model.ConversationBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationBlockRepository extends JpaRepository<ConversationBlock, Long> {

    boolean existsByConversation_Id(Long conversationId);

    boolean existsByConversation_IdAndUser_UserId(Long conversationId, Long userId);

    void deleteByConversation_IdAndUser_UserId(Long conversationId, Long userId);

    void deleteByConversation(Conversation conversation);

    void deleteAllByConversation(Conversation conversation);
}