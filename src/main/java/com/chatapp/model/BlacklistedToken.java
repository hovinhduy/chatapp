package com.chatapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blacklist_tokens")
public class BlacklistedToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 512)
    private String token;

    @Column(name = "device_session_id", length = 36)
    private String deviceSessionId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "blacklisted_at")
    private LocalDateTime blacklistedAt;

    @Column(length = 255)
    private String reason; // manual_logout, session_kick, token_refresh, etc

    @PrePersist
    protected void onCreate() {
        if (blacklistedAt == null) {
            blacklistedAt = LocalDateTime.now();
        }
    }
}