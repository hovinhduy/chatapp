package com.chatapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QrLoginConfirmRequest {
    @NotBlank(message = "Session token không được để trống")
    private String sessionToken;

    @NotBlank(message = "Action không được để trống")
    private String action; // "confirm" hoặc "reject"
}