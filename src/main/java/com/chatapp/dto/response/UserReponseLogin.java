package com.chatapp.dto.response;

import java.time.LocalDateTime;

import com.chatapp.enums.UserStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserReponseLogin {
    private Long userId;
    private String displayName;
    private String phone;
    private String email;
    private String gender;
    private String dateOfBirth;
    private String avatar;
    private UserStatus status;
    private LocalDateTime lastLogin;
}
