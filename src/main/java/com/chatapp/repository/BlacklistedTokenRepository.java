package com.chatapp.repository;

import com.chatapp.model.BlacklistedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, Long> {

    /**
     * Kiểm tra xem token có trong blacklist không
     */
    boolean existsByToken(String token);

    /**
     * Tìm blacklisted token theo token
     */
    Optional<BlacklistedToken> findByToken(String token);

    /**
     * Tìm tất cả blacklisted token của một device session
     */
    List<BlacklistedToken> findByDeviceSessionId(String deviceSessionId);

    /**
     * Tìm tất cả blacklisted token của một user
     */
    List<BlacklistedToken> findByUserId(Long userId);

    /**
     * Tìm blacklisted token theo lý do
     */
    List<BlacklistedToken> findByReason(String reason);

    /**
     * Xóa các token cũ (tùy chọn, có thể giữ để audit)
     */
    @Modifying
    @Query("DELETE FROM BlacklistedToken t WHERE t.blacklistedAt < :cutoffDate")
    void deleteOldBlacklistedTokens(@Param("cutoffDate") LocalDateTime cutoffDate);
}