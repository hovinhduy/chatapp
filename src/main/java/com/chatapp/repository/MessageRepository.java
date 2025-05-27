package com.chatapp.repository;

import com.chatapp.model.Conversation;
import com.chatapp.model.Group;
import com.chatapp.model.Message;
import com.chatapp.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByGroup(Group group);

    @Query("SELECT m FROM Message m WHERE " +
            "(m.sender = :user1 AND m.receiver = :user2) OR " +
            "(m.sender = :user2 AND m.receiver = :user1) " +
            "ORDER BY m.createdAt ASC")
    List<Message> findMessagesBetweenUsers(@Param("user1") User user1, @Param("user2") User user2);

    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    // Thêm phương thức có phân trang, sắp xếp từ mới đến cũ
    Page<Message> findByConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);

    void deleteByConversation(Conversation conversation);

    void deleteAllByConversation(Conversation conversation);

    // Tìm kiếm tin nhắn theo nội dung trong cuộc trò chuyện
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId " +
            "AND LOWER(m.content) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "ORDER BY m.createdAt DESC")
    Page<Message> searchMessagesByContent(@Param("conversationId") Long conversationId,
            @Param("searchTerm") String searchTerm,
            Pageable pageable);

    // Tìm kiếm tin nhắn theo người gửi trong cuộc trò chuyện
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId " +
            "AND m.sender.userId = :senderId " +
            "ORDER BY m.createdAt DESC")
    Page<Message> searchMessagesBySender(@Param("conversationId") Long conversationId,
            @Param("senderId") Long senderId,
            Pageable pageable);

    // Tìm kiếm tin nhắn theo khoảng thời gian trong cuộc trò chuyện
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId " +
            "AND m.createdAt BETWEEN :startDate AND :endDate " +
            "ORDER BY m.createdAt DESC")
    Page<Message> searchMessagesByDateRange(@Param("conversationId") Long conversationId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    // Tìm kiếm tin nhắn với tất cả các bộ lọc
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId " +
            "AND (:searchTerm IS NULL OR LOWER(m.content) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "AND (:senderId IS NULL OR m.sender.userId = :senderId) " +
            "AND (:startDate IS NULL OR m.createdAt >= :startDate) " +
            "AND (:endDate IS NULL OR m.createdAt <= :endDate) " +
            "ORDER BY m.createdAt DESC")
    Page<Message> searchMessages(@Param("conversationId") Long conversationId,
            @Param("searchTerm") String searchTerm,
            @Param("senderId") Long senderId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    // Tìm kiếm tin nhắn trong tất cả cuộc trò chuyện của người dùng
    @Query("SELECT m FROM Message m JOIN m.conversation.conversationUsers cu " +
            "WHERE cu.user.userId = :userId " +
            "AND (:searchTerm IS NULL OR LOWER(m.content) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "AND (:senderId IS NULL OR m.sender.userId = :senderId) " +
            "AND (:startDate IS NULL OR m.createdAt >= :startDate) " +
            "AND (:endDate IS NULL OR m.createdAt <= :endDate) " +
            "ORDER BY m.createdAt DESC")
    Page<Message> searchMessagesGlobal(@Param("userId") Long userId,
            @Param("searchTerm") String searchTerm,
            @Param("senderId") Long senderId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);
}