package com.chatapp.model;

import com.chatapp.enums.Platform;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_device_sessions")
public class UserDeviceSession {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "userId")
    private User user;

    @Column(name = "device_id", length = 128)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Platform platform;

    @Column(name = "device_name", length = 128)
    private String deviceName;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(length = 128)
    private String location;

    @Column(name = "login_time")
    private LocalDateTime loginTime;

    @Column(name = "last_active_time")
    private LocalDateTime lastActiveTime;

    @Column(name = "logout_time")
    private LocalDateTime logoutTime;

    @PrePersist
    protected void onCreate() {
        if (loginTime == null) {
            loginTime = LocalDateTime.now();
        }
        lastActiveTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastActiveTime = LocalDateTime.now();
    }
}