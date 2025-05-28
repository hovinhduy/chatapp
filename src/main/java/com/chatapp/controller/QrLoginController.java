package com.chatapp.controller;

import com.chatapp.dto.request.DeviceInfoDto;
import com.chatapp.dto.request.QrLoginConfirmRequest;
import com.chatapp.dto.request.QrLoginScanRequest;
import com.chatapp.dto.response.ApiResponse;
import com.chatapp.dto.response.QrLoginResponse;
import com.chatapp.service.DeviceSessionService;
import com.chatapp.service.QrLoginService;
import com.chatapp.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/qr-login")
@Tag(name = "QR Login", description = "Các API dùng cho việc đăng nhập bằng QR code")
public class QrLoginController {

    @Autowired
    private QrLoginService qrLoginService;

    @Autowired
    private DeviceSessionService deviceSessionService;

    @Autowired
    private UserService userService;

    /**
     * Tạo QR code để đăng nhập
     */
    @Operation(summary = "Tạo QR code", description = "Tạo QR code để quét và đăng nhập từ thiết bị khác")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tạo QR code thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Yêu cầu không hợp lệ")
    })
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<QrLoginResponse>> generateQRCode(HttpServletRequest request) {
        try {
            // Lấy thông tin thiết bị từ header
            DeviceInfoDto deviceInfo = deviceSessionService.extractDeviceInfo(request);

            QrLoginResponse qrResponse = qrLoginService.generateQRCode(deviceInfo);

            ApiResponse<QrLoginResponse> response = new ApiResponse<>();
            response.setSuccess(true);
            response.setMessage("QR code đã được tạo thành công");
            response.setPayload(qrResponse);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            ApiResponse<QrLoginResponse> response = new ApiResponse<>();
            response.setSuccess(false);
            response.setMessage("Lỗi khi tạo QR code: " + e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Quét QR code và lấy thông tin session
     */
    @Operation(summary = "Quét QR code", description = "Quét QR code và lấy thông tin phiên đăng nhập")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Quét QR code thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "QR code không hợp lệ hoặc đã hết hạn"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Không có quyền truy cập")
    })
    @PostMapping("/scan")
    public ResponseEntity<ApiResponse<Map<String, Object>>> scanQRCode(
            @Valid @RequestBody QrLoginScanRequest request,
            HttpServletRequest httpRequest) {
        try {
            Map<String, Object> scanResult = qrLoginService.scanQRCode(request);

            ApiResponse<Map<String, Object>> response = new ApiResponse<>();
            response.setSuccess(true);
            response.setMessage("QR code đã được quét thành công");
            response.setPayload(scanResult);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            ApiResponse<Map<String, Object>> response = new ApiResponse<>();
            response.setSuccess(false);
            response.setMessage("Lỗi khi quét QR code: " + e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Xác nhận hoặc từ chối đăng nhập QR
     */
    @Operation(summary = "Xác nhận đăng nhập QR", description = "Xác nhận hoặc từ chối đăng nhập từ QR code")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Xác nhận thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Yêu cầu không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Không có quyền truy cập")
    })
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<Map<String, Object>>> confirmQRLogin(
            @Valid @RequestBody QrLoginConfirmRequest request,
            HttpServletRequest httpRequest) {
        try {
            Map<String, Object> confirmResult = qrLoginService.confirmQRLogin(request, getCurrentUserId());

            ApiResponse<Map<String, Object>> response = new ApiResponse<>();
            response.setSuccess(true);
            response.setMessage("Xử lý yêu cầu thành công");
            response.setPayload(confirmResult);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            ApiResponse<Map<String, Object>> response = new ApiResponse<>();
            response.setSuccess(false);
            response.setMessage("Lỗi khi xử lý yêu cầu: " + e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Kiểm tra trạng thái QR login
     */
    @Operation(summary = "Kiểm tra trạng thái QR login", description = "Kiểm tra trạng thái phiên đăng nhập QR và nhận token nếu đã được xác nhận")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Kiểm tra thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Session không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy session")
    })
    @GetMapping("/status/{sessionToken}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkQRLoginStatus(
            @PathVariable String sessionToken,
            HttpServletRequest request) {
        try {
            // Lấy thông tin thiết bị từ header
            DeviceInfoDto deviceInfo = deviceSessionService.extractDeviceInfo(request);

            Map<String, Object> statusResult = qrLoginService.checkQRLoginStatus(sessionToken, deviceInfo);

            ApiResponse<Map<String, Object>> response = new ApiResponse<>();
            response.setSuccess(true);
            response.setMessage("Kiểm tra trạng thái thành công");
            response.setPayload(statusResult);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            ApiResponse<Map<String, Object>> response = new ApiResponse<>();
            response.setSuccess(false);
            response.setMessage("Lỗi khi kiểm tra trạng thái: " + e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) principal;
            return userService.getUserByPhone(userDetails.getUsername()).getUserId();
        } else {
            throw new RuntimeException("User not found");
        }
    }
}