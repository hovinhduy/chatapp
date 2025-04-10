package com.chatapp.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.chatapp.enums.Gender;
import com.chatapp.enums.UserStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long userId;
    private String displayName;
    private String phone;
    private String password;
    private String email;
    private Gender gender;
    private LocalDate dateOfBirth;
    private String avatarUrl;
    private LocalDateTime lastLogin;
    private UserStatus status;
    private LocalDateTime createdAt;
}