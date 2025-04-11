package com.chatapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Token xác thực không được để trống")
    private String idToken;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải có định dạng 0xxxxxxxxx")
    private String phone;

    @NotBlank(message = "Tên hiển thị không được để trống")
    @Size(min = 2, max = 40, message = "Tên hiển thị phải từ 2 đến 40 ký tự")
    @Pattern(regexp = "^[A-Za-zÀ-ỹ\\s]+$", message = "Tên hiển thị không được chứa số và ký tự đặc biệt")
    private String displayName;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, max = 20, message = "Mật khẩu phải từ 6 đến 20 ký tự")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[^A-Za-z0-9]).+$", message = "Mật khẩu phải có chữ và kí tự đặt biệt")
    private String password;

    @NotBlank(message = "Giới tính không được để trống")
    private String gender;

    @NotNull(message = "Ngày sinh không được để trống")
    @Past(message = "Ngày sinh phải là ngày trong quá khứ")
    private LocalDate dateOfBirth;
}