package com.chatapp.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

import com.chatapp.enums.OtpStatus;

@Data
@Entity
@Table(name = "otps")
public class Otp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private LocalDateTime expiryTime;

    @Enumerated(EnumType.STRING)
    private OtpStatus status;
}