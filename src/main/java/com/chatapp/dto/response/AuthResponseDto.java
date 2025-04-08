package com.chatapp.dto.response;

import com.chatapp.dto.request.UserDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponseDto {
    private String accessToken;
    private String tokenType = "Bearer";
    private UserDto user;

    public AuthResponseDto(String accessToken, UserDto user) {
        this.accessToken = accessToken;
        this.user = user;
    }
}