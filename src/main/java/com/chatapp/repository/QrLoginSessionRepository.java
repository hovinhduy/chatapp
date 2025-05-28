package com.chatapp.repository;

import com.chatapp.enums.QrLoginStatus;
import com.chatapp.model.QrLoginSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface QrLoginSessionRepository extends JpaRepository<QrLoginSession, Long> {

    Optional<QrLoginSession> findBySessionToken(String sessionToken);

    Optional<QrLoginSession> findBySessionTokenAndStatus(String sessionToken, QrLoginStatus status);

    @Modifying
    @Query("UPDATE QrLoginSession q SET q.status = :status WHERE q.expiresAt < :now AND q.status = :currentStatus")
    void markExpiredSessions(@Param("status") QrLoginStatus status,
            @Param("now") LocalDateTime now,
            @Param("currentStatus") QrLoginStatus currentStatus);

    @Modifying
    @Query("DELETE FROM QrLoginSession q WHERE q.expiresAt < :cutoffTime")
    void deleteExpiredSessions(@Param("cutoffTime") LocalDateTime cutoffTime);
}