package com.chatapp.controller;

import com.chatapp.config.FirebaseConfig;
import com.chatapp.dto.request.ForgotPasswordRequest;
import com.chatapp.dto.request.LoginDto;
import com.chatapp.dto.request.LogoutRequest;
import com.chatapp.dto.request.RefreshTokenRequest;
import com.chatapp.dto.request.RegisterRequest;
import com.chatapp.dto.request.TokenVerificationRequest;
import com.chatapp.exception.TokenRefreshException;
import com.chatapp.service.AuthService;
import com.chatapp.service.UserService;
import com.google.firebase.auth.FirebaseToken;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Các API dùng cho việc xác thực người dùng")
public class AuthController {

    @Autowired
    private FirebaseConfig firebaseConfig;

    @Autowired
    private UserService userService;

    @Autowired
    private AuthService authService;

    /**
     * API xác minh token từ Firebase
     * Được gọi sau khi client đã xác thực thành công với Firebase
     */
    @Operation(summary = "Xác minh token Firebase", description = "Xác minh token từ Firebase")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Xác minh token thành công"),
            @ApiResponse(responseCode = "400", description = "Token không hợp lệ")
    })
    @PostMapping("/verify-token")
    public ResponseEntity<?> verifyFirebaseToken(@RequestBody TokenVerificationRequest request) {
        try {
            // Xác minh token từ client
            FirebaseToken decodedToken = firebaseConfig.verifyIdToken(request.getIdToken());

            // Trích xuất thông tin từ token
            String uid = decodedToken.getUid();
            String phone = decodedToken.getClaims().get("phone_number").toString();

            // Trả về kết quả xác thực
            Map<String, Object> response = new HashMap<>();
            response.put("verified", true);
            response.put("uid", uid);
            response.put("phoneNumber", phone);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("verified", false);
            response.put("error", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * API đăng ký người dùng mới
     * Yêu cầu token đã được xác thực từ Firebase
     */
    @Operation(summary = "Đăng ký người dùng", description = "Đăng ký người dùng mới")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Đăng ký người dùng thành công"),
            @ApiResponse(responseCode = "400", description = "Yêu cầu không hợp lệ")
    })
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        try {
            // Xác minh token (bắt buộc phải có)
            FirebaseToken decodedToken = firebaseConfig.verifyIdToken(registerRequest.getIdToken());

            // Kiểm tra số điện thoại từ token có khớp với số trong request
            String phoneFromToken = decodedToken.getClaims().get("phone_number").toString();
            String phoneFromRequest = registerRequest.getPhone();

            // Format số điện thoại để so sánh (đảm bảo định dạng nhất quán)
            String formattedPhoneFromToken = formatPhoneNumber(phoneFromToken);
            String formattedPhoneFromRequest = formatPhoneNumber(phoneFromRequest);

            if (!formattedPhoneFromToken.equals(formattedPhoneFromRequest)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Số điện thoại không khớp với token xác thực"));
            }

            // Đăng ký người dùng mới
            return userService.registerUser(registerRequest);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Lỗi xác thực: " + e.getMessage()));
        }
    }

    /**
     * API đăng nhập người dùng
     */
    @Operation(summary = "Đăng nhập người dùng", description = "Đăng nhập người dùng vào hệ thống")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Đăng nhập thành công"),
            @ApiResponse(responseCode = "400", description = "Yêu cầu không hợp lệ")
    })
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@Valid @RequestBody LoginDto loginRequest) {
        try {
            return ResponseEntity.ok(authService.login(loginRequest));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Lỗi đăng nhập: " + e.getMessage()));
        }
    }

    /**
     * API làm mới token
     */
    @Operation(summary = "Làm mới token", description = "Làm mới access token bằng refresh token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Làm mới token thành công"),
            @ApiResponse(responseCode = "403", description = "Refresh token không hợp lệ")
    })
    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            return ResponseEntity.ok(authService.refreshToken(request));
        } catch (TokenRefreshException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
        }
    }

    /**
     * API đăng xuất người dùng
     */
    @Operation(summary = "Đăng xuất người dùng", description = "Đăng xuất người dùng và vô hiệu hóa token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Đăng xuất thành công"),
            @ApiResponse(responseCode = "400", description = "Yêu cầu không hợp lệ")
    })
    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser(@Valid @RequestBody LogoutRequest request) {
        try {
            boolean result = authService.logout(request);
            if (result) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Đăng xuất thành công"));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Đăng xuất thất bại"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Lỗi đăng xuất: " + e.getMessage()));
        }
    }

    @Operation(summary = "Quên mật khẩu", description = "Quên mật khẩu")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lấy lại mật khẩu thành công"),
            @ApiResponse(responseCode = "400", description = "Yêu cầu không hợp lệ")
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        try {
            // Xác minh token (bắt buộc phải có)
            FirebaseToken decodedToken = firebaseConfig.verifyIdToken(request.getIdToken());

            // Kiểm tra số điện thoại từ token có khớp với số trong request
            String phoneFromToken = decodedToken.getClaims().get("phone_number").toString();
            String phoneFromRequest = request.getPhone();

            // Format số điện thoại để so sánh (đảm bảo định dạng nhất quán)
            String formattedPhoneFromToken = formatPhoneNumber(phoneFromToken);
            String formattedPhoneFromRequest = formatPhoneNumber(phoneFromRequest);

            if (!formattedPhoneFromToken.equals(formattedPhoneFromRequest)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Số điện thoại không khớp với token xác thực"));
            }
            return ResponseEntity.ok(Map.of(
                    "success", authService.forgotPassword(request),
                    "message", "Lấy lại mật khẩu thành công"));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Lỗi quên mật khẩu: " + e.getMessage()));
        }

    }

    /**
     * Format số điện thoại để đảm bảo nhất quán khi so sánh
     * Ví dụ: chuyển "+84912345678" thành "0912345678"
     */
    private String formatPhoneNumber(String phone) {
        if (phone == null)
            return "";

        if (phone.startsWith("+84")) {
            return "0" + phone.substring(3);
        }
        return phone;
    }
}