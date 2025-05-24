package com.chatapp.repository;

import com.chatapp.model.Token;
import com.chatapp.model.UserDeviceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TokenRepository extends JpaRepository<Token, Long> {

    /**
     * Tìm token theo token string
     */
    Optional<Token> findByToken(String token);

    /**
     * Tìm token theo refresh token
     */
    Optional<Token> findByRefreshToken(String refreshToken);

    /**
     * Tìm tất cả token của một device session
     */
    List<Token> findByDeviceSession(UserDeviceSession deviceSession);

    /**
     * Tìm token đang hoạt động (không bị revoked và chưa hết hạn) của device
     * session
     */
    @Query("SELECT t FROM Token t WHERE t.deviceSession = :deviceSession AND t.revoked = false AND t.expirationDate > :now")
    List<Token> findActiveTokensByDeviceSession(@Param("deviceSession") UserDeviceSession deviceSession,
            @Param("now") LocalDateTime now);

    /**
     * Vô hiệu hóa tất cả token của một device session
     */
    @Modifying
    @Query("UPDATE Token t SET t.revoked = true WHERE t.deviceSession.id = :sessionId")
    void revokeAllTokensBySessionId(@Param("sessionId") String sessionId);

    /**
     * Vô hiệu hóa token cụ thể
     */
    @Modifying
    @Query("UPDATE Token t SET t.revoked = true WHERE t.token = :token")
    void revokeToken(@Param("token") String token);

    /**
     * Xóa các token đã hết hạn
     */
    @Modifying
    @Query("DELETE FROM Token t WHERE t.expirationDate < :cutoffDate OR (t.refreshExpirationDate IS NOT NULL AND t.refreshExpirationDate < :cutoffDate)")
    void deleteExpiredTokens(@Param("cutoffDate") LocalDateTime cutoffDate);
}