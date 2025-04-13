package com.chatapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "device_sessions")
public class DeviceSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "userId")
    private User user;

    @Column(nullable = false)
    private String deviceId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DeviceType deviceType;

    @Column(nullable = false)
    private String deviceInfo;

    @Column(nullable = false)
    private Instant loginTime;

    @Column
    private Instant logoutTime;

    @Column(nullable = false)
    private boolean active;

    public enum DeviceType {
        WEB,
        MOBILE
    }
}