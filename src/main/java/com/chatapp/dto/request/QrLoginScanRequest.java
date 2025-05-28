package com.chatapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QrLoginScanRequest {
    @NotBlank(message = "Session token không được để trống")
    private String sessionToken;
}