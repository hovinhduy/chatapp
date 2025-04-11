package com.chatapp.controller;

import com.chatapp.dto.request.UserDto;
import com.chatapp.dto.response.ApiResponse;
import com.chatapp.service.FileStorageService;
import com.chatapp.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "User Management", description = "Các API để quản lý hồ sơ và cài đặt người dùng")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;
    private final FileStorageService fileStorageService;

    public UserController(UserService userService, FileStorageService fileStorageService) {
        this.userService = userService;
        this.fileStorageService = fileStorageService;
    }

    @Operation(summary = "Lấy thông tin người dùng hiện tại", description = "Lấy thông tin hồ sơ của người dùng đang đăng nhập")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy thông tin hồ sơ thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa xác thực - Người dùng chưa đăng nhập")
    })
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> getCurrentUser(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        UserDto user = userService.getUserByPhone(userDetails.getUsername());
        ApiResponse<UserDto> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setMessage("Lấy thông tin người dùng thành công");
        response.setPayload(user);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Lấy người dùng theo ID", description = "Lấy thông tin hồ sơ người dùng theo ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy thông tin hồ sơ thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDto>> getUserById(
            @Parameter(description = "ID người dùng", required = true) @PathVariable Long id) {
        UserDto user = userService.getUserById(id);
        ApiResponse<UserDto> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setMessage("Lấy thông tin người dùng thành công");
        response.setPayload(user);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Lấy tất cả người dùng", description = "Lấy danh sách tất cả người dùng trong hệ thống")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy danh sách người dùng thành công")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserDto>>> getAllUsers() {
        List<UserDto> users = userService.getAllUsers();
        ApiResponse<List<UserDto>> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setMessage("Lấy danh sách người dùng thành công");
        response.setPayload(users);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Cập nhật thông tin người dùng", description = "Cập nhật thông tin hồ sơ của người dùng")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cập nhật hồ sơ thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Bị cấm - Người dùng chỉ có thể cập nhật hồ sơ của chính mình"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDto>> updateUser(
            @Parameter(description = "ID người dùng", required = true) @PathVariable Long id,
            @Parameter(description = "Thông tin người dùng cập nhật", required = true) @RequestBody UserDto userDto,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        UserDto currentUser = userService.getUserByPhone(userDetails.getUsername());
        if (!currentUser.getUserId().equals(id)) {
            ApiResponse<UserDto> response = new ApiResponse<>();
            response.setSuccess(false);
            response.setMessage("Không có quyền cập nhật thông tin người dùng khác");
            response.setError("Forbidden");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        UserDto updatedUser = userService.updateUser(id, userDto);
        ApiResponse<UserDto> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setMessage("Cập nhật thông tin người dùng thành công");
        response.setPayload(updatedUser);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Tải lên ảnh đại diện", description = "Tải lên ảnh đại diện mới cho người dùng")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tải lên ảnh đại diện thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Bị cấm - Người dùng chỉ có thể cập nhật ảnh đại diện của chính mình"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Định dạng hoặc kích thước tệp không hợp lệ")
    })
    @PostMapping("/{id}/avatar")
    public ResponseEntity<ApiResponse<UserDto>> uploadAvatar(
            @Parameter(description = "ID người dùng", required = true) @PathVariable Long id,
            @Parameter(description = "Tệp ảnh đại diện", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) throws IOException {

        UserDto currentUser = userService.getUserByPhone(userDetails.getUsername());
        if (!currentUser.getUserId().equals(id)) {
            ApiResponse<UserDto> response = new ApiResponse<>();
            response.setSuccess(false);
            response.setMessage("Không có quyền cập nhật avatar của người dùng khác");
            response.setError("Forbidden");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        String fileUrl = fileStorageService.uploadFile(file);

        UserDto userDto = new UserDto();
        userDto.setDisplayName(currentUser.getDisplayName());
        userDto.setAvatarUrl(fileUrl);

        UserDto updatedUser = userService.updateUser(id, userDto);
        ApiResponse<UserDto> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setMessage("Cập nhật ảnh đại diện thành công");
        response.setPayload(updatedUser);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Đổi mật khẩu", description = "Đổi mật khẩu cho người dùng hiện tại")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đổi mật khẩu thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Bị cấm - Người dùng chỉ có thể đổi mật khẩu của chính mình"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Mật khẩu cũ không đúng")
    })
    @PostMapping("/{id}/change-password")
    public ResponseEntity<ApiResponse<Object>> changePassword(
            @Parameter(description = "ID người dùng", required = true) @PathVariable Long id,
            @Parameter(description = "Mật khẩu hiện tại", required = true) @RequestParam String oldPassword,
            @Parameter(description = "Mật khẩu mới", required = true) @RequestParam String newPassword,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        UserDto currentUser = userService.getUserByPhone(userDetails.getUsername());
        if (!currentUser.getUserId().equals(id)) {
            ApiResponse<Object> response = new ApiResponse<>();
            response.setSuccess(false);
            response.setMessage("Không có quyền đổi mật khẩu của người dùng khác");
            response.setError("Forbidden");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        boolean success = userService.changePassword(id, oldPassword, newPassword);
        ApiResponse<Object> response = new ApiResponse<>();

        if (success) {
            response.setSuccess(true);
            response.setMessage("Đổi mật khẩu thành công");
            return ResponseEntity.ok(response);
        } else {
            response.setSuccess(false);
            response.setMessage("Mật khẩu cũ không chính xác");
            response.setError("Invalid old password");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @Operation(summary = "Tìm người dùng theo số điện thoại", description = "Lấy thông tin hồ sơ người dùng theo số điện thoại chính xác")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy thông tin hồ sơ thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng với số điện thoại đã cho")
    })
    @GetMapping("/by-phone")
    public ResponseEntity<ApiResponse<UserDto>> getUserByPhone(
            @Parameter(description = "Số điện thoại", required = true) @RequestParam String phone) {
        UserDto user = userService.getUserByPhone(phone);
        ApiResponse<UserDto> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setMessage("Lấy thông tin người dùng thành công");
        response.setPayload(user);
        return ResponseEntity.ok(response);
    }
}