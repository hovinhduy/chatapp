package com.chatapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QrLoginResponse {
    private String sessionToken;
    private String qrCodeImage; // Base64 encoded QR code image
    private String status;
    private LocalDateTime expiresAt;
    private String message;
}