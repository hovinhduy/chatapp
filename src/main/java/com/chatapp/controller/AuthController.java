package com.chatapp.controller;

import com.chatapp.config.FirebaseConfig;
import com.chatapp.dto.request.LoginDto;
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
@Tag(name = "Authentication", description = "APIs for authentication")
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
    @Operation(summary = "Verify Firebase token", description = "Verifies the Firebase token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token verified successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid token")
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
    @Operation(summary = "Register user", description = "Registers a new user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
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
    @Operation(summary = "Login user", description = "Logs in a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User logged in successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
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
    @Operation(summary = "Refresh token", description = "Refreshes the access token using a refresh token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
            @ApiResponse(responseCode = "403", description = "Invalid refresh token")
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