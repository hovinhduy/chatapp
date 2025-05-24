package com.chatapp.controller;

import com.chatapp.dto.response.ApiResponse;
import com.chatapp.dto.response.DeviceSessionDto;
import com.chatapp.service.DeviceSessionService;
import com.chatapp.service.UserService;
import com.chatapp.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/devices")
@Tag(name = "Device Management", description = "Các API quản lý thiết bị và phiên đăng nhập")
@SecurityRequirement(name = "bearerAuth")
public class DeviceController {

    @Autowired
    private DeviceSessionService deviceSessionService;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /**
     * Lấy sessionId từ JWT token trong Authorization header
     */
    private String getCurrentSessionId(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            String jwt = bearerToken.substring(7);
            try {
                return jwtTokenProvider.getSessionIdFromToken(jwt);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Lấy danh sách thiết bị đang đăng nhập của user
     */
    @Operation(summary = "Lấy danh sách thiết bị", description = "Lấy danh sách tất cả thiết bị đang đăng nhập của người dùng")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy danh sách thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa xác thực")
    })
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<DeviceSessionDto>>> getActiveSessions(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request) {
        try {
            Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
            String currentSessionId = getCurrentSessionId(request);
            List<DeviceSessionDto> sessions = deviceSessionService.getActiveSessionsByUser(userId, currentSessionId);

            ApiResponse<List<DeviceSessionDto>> response = new ApiResponse<>();
            response.setSuccess(true);
            response.setMessage("Lấy danh sách thiết bị thành công");
            response.setPayload(sessions);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiResponse<List<DeviceSessionDto>> response = new ApiResponse<>();
            response.setSuccess(false);
            response.setMessage("Lỗi khi lấy danh sách thiết bị: " + e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Lấy danh sách thiết bị với filter theo trạng thái
     */
    @Operation(summary = "Lấy danh sách thiết bị với filter", description = "Lấy danh sách thiết bị theo trạng thái: active (đang hoạt động), logged_out (đã đăng xuất), all (tất cả)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy danh sách thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Tham số không hợp lệ")
    })
    @GetMapping("/sessions/filter")
    public ResponseEntity<ApiResponse<List<DeviceSessionDto>>> getSessionsWithFilter(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Trạng thái session: active, logged_out, all") @RequestParam(value = "status", defaultValue = "active") String status,
            HttpServletRequest request) {
        try {
            Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
            String currentSessionId = getCurrentSessionId(request);
            List<DeviceSessionDto> sessions;

            switch (status.toLowerCase()) {
                case "active":
                    sessions = deviceSessionService.getActiveSessionsByUser(userId, currentSessionId);
                    break;
                case "logged_out":
                    sessions = deviceSessionService.getLoggedOutSessionsByUser(userId, currentSessionId);
                    break;
                case "all":
                    sessions = deviceSessionService.getAllSessionsByUser(userId, currentSessionId);
                    break;
                default:
                    ApiResponse<List<DeviceSessionDto>> errorResponse = new ApiResponse<>();
                    errorResponse.setSuccess(false);
                    errorResponse.setMessage("Trạng thái không hợp lệ. Sử dụng: active, logged_out, hoặc all");
                    return ResponseEntity.badRequest().body(errorResponse);
            }

            ApiResponse<List<DeviceSessionDto>> response = new ApiResponse<>();
            response.setSuccess(true);
            response.setMessage("Lấy danh sách thiết bị thành công");
            response.setPayload(sessions);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiResponse<List<DeviceSessionDto>> response = new ApiResponse<>();
            response.setSuccess(false);
            response.setMessage("Lỗi khi lấy danh sách thiết bị: " + e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Lấy tất cả thiết bị của user (bao gồm cả đã đăng xuất)
     */
    @Operation(summary = "Lấy tất cả thiết bị", description = "Lấy danh sách tất cả thiết bị của người dùng bao gồm cả thiết bị đã đăng xuất")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy danh sách thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa xác thực")
    })
    @GetMapping("/sessions/all")
    public ResponseEntity<ApiResponse<List<DeviceSessionDto>>> getAllSessions(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request) {
        try {
            Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
            String currentSessionId = getCurrentSessionId(request);
            List<DeviceSessionDto> sessions = deviceSessionService.getAllSessionsByUser(userId, currentSessionId);

            ApiResponse<List<DeviceSessionDto>> response = new ApiResponse<>();
            response.setSuccess(true);
            response.setMessage("Lấy danh sách tất cả thiết bị thành công");
            response.setPayload(sessions);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiResponse<List<DeviceSessionDto>> response = new ApiResponse<>();
            response.setSuccess(false);
            response.setMessage("Lỗi khi lấy danh sách thiết bị: " + e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Đăng xuất một thiết bị cụ thể
     */
    @Operation(summary = "Đăng xuất thiết bị", description = "Đăng xuất và vô hiệu hóa một thiết bị cụ thể")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đăng xuất thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy session")
    })
    @PostMapping("/sessions/{sessionId}/logout")
    public ResponseEntity<ApiResponse<String>> logoutDevice(
            @Parameter(description = "ID của session cần đăng xuất") @PathVariable String sessionId,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            deviceSessionService.logoutDeviceSession(sessionId, "manual_logout");

            ApiResponse<String> response = new ApiResponse<>();
            response.setSuccess(true);
            response.setMessage("Đăng xuất thiết bị thành công");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiResponse<String> response = new ApiResponse<>();
            response.setSuccess(false);
            response.setMessage("Lỗi khi đăng xuất thiết bị: " + e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Đăng xuất tất cả thiết bị khác (trừ thiết bị hiện tại)
     */
    @Operation(summary = "Đăng xuất tất cả thiết bị khác", description = "Đăng xuất tất cả thiết bị khác ngoại trừ thiết bị hiện tại")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đăng xuất thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa xác thực")
    })
    @PostMapping("/sessions/logout-all-others")
    public ResponseEntity<ApiResponse<String>> logoutAllOtherDevices(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request) {
        try {
            Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
            String currentSessionId = getCurrentSessionId(request);
            List<DeviceSessionDto> sessions = deviceSessionService.getActiveSessionsByUser(userId, currentSessionId);

            int loggedOutCount = 0;
            for (DeviceSessionDto session : sessions) {
                if (!session.isCurrentSession()) {
                    deviceSessionService.logoutDeviceSession(session.getSessionId(), "logout_all_others");
                    loggedOutCount++;
                }
            }

            ApiResponse<String> response = new ApiResponse<>();
            response.setSuccess(true);
            response.setMessage("Đã đăng xuất " + loggedOutCount + " thiết bị khác");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiResponse<String> response = new ApiResponse<>();
            response.setSuccess(false);
            response.setMessage("Lỗi khi đăng xuất thiết bị: " + e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }
}