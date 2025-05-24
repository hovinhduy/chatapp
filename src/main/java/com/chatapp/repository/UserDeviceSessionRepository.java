package com.chatapp.repository;

import com.chatapp.enums.Platform;
import com.chatapp.model.User;
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
public interface UserDeviceSessionRepository extends JpaRepository<UserDeviceSession, String> {

    /**
     * Tìm session đang hoạt động (chưa logout) của user trên một platform
     */
    @Query("SELECT s FROM UserDeviceSession s WHERE s.user.userId = :userId AND s.platform = :platform AND s.logoutTime IS NULL")
    List<UserDeviceSession> findActiveSessionsByUserAndPlatform(@Param("userId") Long userId,
            @Param("platform") Platform platform);

    /**
     * Tìm tất cả session đang hoạt động của user
     */
    @Query("SELECT s FROM UserDeviceSession s WHERE s.user.userId = :userId AND s.logoutTime IS NULL")
    List<UserDeviceSession> findActiveSessionsByUser(@Param("userId") Long userId);

    /**
     * Tìm tất cả session của user (bao gồm cả đã đăng xuất)
     */
    @Query("SELECT s FROM UserDeviceSession s WHERE s.user.userId = :userId ORDER BY s.loginTime DESC")
    List<UserDeviceSession> findAllByUser_UserId(@Param("userId") Long userId);

    /**
     * Tìm các session đã đăng xuất của user
     */
    @Query("SELECT s FROM UserDeviceSession s WHERE s.user.userId = :userId AND s.logoutTime IS NOT NULL ORDER BY s.logoutTime DESC")
    List<UserDeviceSession> findLoggedOutSessionsByUser(@Param("userId") Long userId);

    /**
     * Tìm session theo device ID và user ID
     */
    Optional<UserDeviceSession> findByDeviceIdAndUser(String deviceId, User user);

    /**
     * Cập nhật logout time cho session
     */
    @Modifying
    @Query("UPDATE UserDeviceSession s SET s.logoutTime = :logoutTime WHERE s.id = :sessionId")
    void updateLogoutTime(@Param("sessionId") String sessionId, @Param("logoutTime") LocalDateTime logoutTime);

    /**
     * Cập nhật last active time cho session
     */
    @Modifying
    @Query("UPDATE UserDeviceSession s SET s.lastActiveTime = :lastActiveTime WHERE s.id = :sessionId")
    void updateLastActiveTime(@Param("sessionId") String sessionId,
            @Param("lastActiveTime") LocalDateTime lastActiveTime);

    /**
     * Xóa các session cũ (đã logout) sau một khoảng thời gian
     */
    @Modifying
    @Query("DELETE FROM UserDeviceSession s WHERE s.logoutTime IS NOT NULL AND s.logoutTime < :cutoffDate")
    void deleteOldLoggedOutSessions(@Param("cutoffDate") LocalDateTime cutoffDate);
}