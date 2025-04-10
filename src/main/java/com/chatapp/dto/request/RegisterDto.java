package com.chatapp.dto.request;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.chatapp.enums.Gender;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterDto {
    @NotBlank(message = "Display name không được để trống")
    @Size(min = 2, max = 40, message = "Tên hiển thị phải có từ 2 đến 40 ký tự")
    @Pattern(regexp = "^[A-Za-zÀ-ỹ\\s]+$", message = "Tên hiển thị không chứa số và kí tự đặt biệt")
    private String displayName;
    @NotBlank(message = "Số điện thoại không được để trống")
    @Size(min = 10, max = 10, message = "Số điện thoại chỉ có 10 số")
    @Pattern(regexp = "^[0-9]+$", message = "Số điện thoại chỉ chứa số")
    private String phone;
    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, max = 20, message = "Mật khẩu phải có từ 6 đến 20 ký tự")
    private String password;
    // @NotBlank(message = "Email không được để trống")
    // @Email(message = "Email không hợp lệ")
    // private String email;
    @NotNull(message = "Giới tính không được để trống")
    private Gender gender;
    @NotNull(message = "Ngày sinh không được để trống")
    @Past(message = "Ngày sinh phải là ngày trong quá khứ")
    private LocalDate dateOfBirth;
}