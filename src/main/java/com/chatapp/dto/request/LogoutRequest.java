package com.chatapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LogoutRequest {

    @NotBlank(message = "Access token không được để trống")
    private String accessToken;

    @NotBlank(message = "Refresh token không được để trống")
    private String refreshToken;
}