package com.chatapp.dto.request;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.chatapp.enums.Gender;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterDto {
    private String displayName;
    private String phone;
    private String password;
    private String email;
    private Gender gender;
    private LocalDate dateOfBirth;
}