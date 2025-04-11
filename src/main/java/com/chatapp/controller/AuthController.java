package com.chatapp.controller;

import com.chatapp.config.FirebaseConfig;
import com.chatapp.dto.request.ForgotPasswordRequest;
import com.chatapp.dto.request.LoginDto;
import com.chatapp.dto.request.LogoutRequest;
import com.chatapp.dto.request.RefreshTokenRequest;
import com.chatapp.dto.request.RegisterRequest;
import com.chatapp.dto.request.TokenVerificationRequest;
import com.chatapp.dto.response.ApiResponse;
import com.chatapp.exception.TokenRefreshException;
import com.chatapp.service.AuthService;
import com.chatapp.service.UserService;
import com.google.firebase.auth.FirebaseToken;

import io.swagger.v3.oas.annotations.Operation;
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
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Xác minh token thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Token không hợp lệ")
    })
    @PostMapping("/verify-token")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyFirebaseToken(
            @RequestBody TokenVerificationRequest request) {
        try {
            // Xác minh token từ client
            FirebaseToken decodedToken = firebaseConfig.verifyIdToken(request.getIdToken());

            // Trích xuất thông tin từ token
            String uid = decodedToken.getUid();
            String phone = decodedToken.getClaims().get("phone_number").toString();

            // Trả về kết quả xác thực
            Map<String, Object> data = new HashMap<>();
            data.put("verified", true);
            data.put("uid", uid);
            data.put("phoneNumber", phone);

            ApiResponse<Map<String, Object>> response = new ApiResponse<>();
            response.setSuccess(true);
            response.setMessage("Xác minh token thành công");
            response.setPayload(data);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiResponse<Map<String, Object>> response = new ApiResponse<>();
            response.setSuccess(false);
            response.setMessage("Xác minh token thất bại");
            response.setError(e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * API đăng ký người dùng mới
     * Yêu cầu token đã được xác thực từ Firebase
     */
    @Operation(summary = "Đăng ký người dùng", description = "Đăng ký người dùng mới")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đăng ký người dùng thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Yêu cầu không hợp lệ")
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
                ApiResponse<Object> response = new ApiResponse<>();
                response.setSuccess(false);
                response.setMessage("Số điện thoại không khớp với token xác thực");

                return ResponseEntity.badRequest().body(response);
            }

            // Đăng ký người dùng mới
            ApiResponse<Object> response = new ApiResponse<>();
            response.setSuccess(true);
            response.setMessage("Đăng ký người dùng thành công");
            response.setPayload(authService.register(registerRequest));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            ApiResponse<Object> response = new ApiResponse<>();
            response.setSuccess(false);
            response.setMessage("Lỗi xác thực: " + e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * API đăng nhập người dùng
     */
    @Operation(summary = "Đăng nhập người dùng", description = "Đăng nhập người dùng vào hệ thống")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đăng nhập thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Yêu cầu không hợp lệ")
    })
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@Valid @RequestBody LoginDto loginRequest) {
        try {
            ApiResponse<Object> response = new ApiResponse<>();
            response.setSuccess(true);
            response.setMessage("Đăng nhập thành công");
            response.setPayload(authService.login(loginRequest));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiResponse<Object> response = new ApiResponse<>();
            response.setSuccess(false);
            response.setMessage("Lỗi đăng nhập: " + e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * API làm mới token
     */
    @Operation(summary = "Làm mới token", description = "Làm mới access token bằng refresh token")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Làm mới token thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Refresh token không hợp lệ")
    })
    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            ApiResponse<Object> response = new ApiResponse<>();
            response.setSuccess(true);
            response.setMessage("Làm mới token thành công");
            response.setPayload(authService.refreshToken(request));

            return ResponseEntity.ok(response);
        } catch (TokenRefreshException e) {
            ApiResponse<Object> response = new ApiResponse<>();
            response.setSuccess(false);
            response.setMessage(e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * API đăng xuất người dùng
     */
    @Operation(summary = "Đăng xuất người dùng", description = "Đăng xuất người dùng và vô hiệu hóa token")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đăng xuất thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Yêu cầu không hợp lệ")
    })
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Object>> logoutUser(@Valid @RequestBody LogoutRequest request) {
        try {
            boolean result = authService.logout(request);
            ApiResponse<Object> response = new ApiResponse<>();

            if (result) {
                response.setSuccess(true);
                response.setMessage("Đăng xuất thành công");
                return ResponseEntity.ok(response);
            } else {
                response.setSuccess(false);
                response.setMessage("Đăng xuất thất bại");
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            ApiResponse<Object> response = new ApiResponse<>();
            response.setSuccess(false);
            response.setMessage("Lỗi đăng xuất: " + e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(summary = "Quên mật khẩu", description = "Quên mật khẩu")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy lại mật khẩu thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Yêu cầu không hợp lệ")
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Object>> forgotPassword(@RequestBody ForgotPasswordRequest request) {
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
                ApiResponse<Object> response = new ApiResponse<>();
                response.setSuccess(false);
                response.setMessage("Số điện thoại không khớp với token xác thực");

                return ResponseEntity.badRequest().body(response);
            }

            boolean result = authService.forgotPassword(request);
            ApiResponse<Object> response = new ApiResponse<>();
            response.setSuccess(result);
            response.setMessage("Lấy lại mật khẩu thành công");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            ApiResponse<Object> response = new ApiResponse<>();
            response.setSuccess(false);
            response.setMessage("Lỗi quên mật khẩu: " + e.getMessage());

            return ResponseEntity.badRequest().body(response);
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