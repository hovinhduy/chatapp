package com.chatapp.model;

import com.chatapp.enums.QrLoginStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "qr_login_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QrLoginSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sessionToken;

    @Column(nullable = false)
    private String deviceId;

    @Column(nullable = false)
    private String deviceInfo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QrLoginStatus status;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

    private LocalDateTime confirmedAt;

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = QrLoginStatus.PENDING;
        }
        // QR code có hiệu lực trong 5 phút
        expiresAt = LocalDateTime.now().plusMinutes(5);
    }
}