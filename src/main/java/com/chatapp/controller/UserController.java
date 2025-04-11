package com.chatapp.controller;

import com.chatapp.dto.request.UserDto;
import com.chatapp.service.FileStorageService;
import com.chatapp.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
            @ApiResponse(responseCode = "200", description = "Lấy thông tin hồ sơ thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực - Người dùng chưa đăng nhập")
    })
    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        UserDto user = userService.getUserByPhone(userDetails.getUsername());
        return ResponseEntity.ok(user);
    }

    @Operation(summary = "Lấy người dùng theo ID", description = "Lấy thông tin hồ sơ người dùng theo ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lấy thông tin hồ sơ thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng")
    })
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(
            @Parameter(description = "ID người dùng", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @Operation(summary = "Lấy tất cả người dùng", description = "Lấy danh sách tất cả người dùng trong hệ thống")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lấy danh sách người dùng thành công")
    })
    @GetMapping
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @Operation(summary = "Cập nhật thông tin người dùng", description = "Cập nhật thông tin hồ sơ của người dùng")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cập nhật hồ sơ thành công"),
            @ApiResponse(responseCode = "403", description = "Bị cấm - Người dùng chỉ có thể cập nhật hồ sơ của chính mình"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng")
    })
    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(
            @Parameter(description = "ID người dùng", required = true) @PathVariable Long id,
            @Parameter(description = "Thông tin người dùng cập nhật", required = true) @RequestBody UserDto userDto,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        UserDto currentUser = userService.getUserByPhone(userDetails.getUsername());
        if (!currentUser.getUserId().equals(id)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(userService.updateUser(id, userDto));
    }

    @Operation(summary = "Tải lên ảnh đại diện", description = "Tải lên ảnh đại diện mới cho người dùng")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tải lên ảnh đại diện thành công"),
            @ApiResponse(responseCode = "403", description = "Bị cấm - Người dùng chỉ có thể cập nhật ảnh đại diện của chính mình"),
            @ApiResponse(responseCode = "400", description = "Định dạng hoặc kích thước tệp không hợp lệ")
    })
    @PostMapping("/{id}/avatar")
    public ResponseEntity<UserDto> uploadAvatar(
            @Parameter(description = "ID người dùng", required = true) @PathVariable Long id,
            @Parameter(description = "Tệp ảnh đại diện", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) throws IOException {

        UserDto currentUser = userService.getUserByPhone(userDetails.getUsername());
        if (!currentUser.getUserId().equals(id)) {
            return ResponseEntity.status(403).build();
        }

        String fileUrl = fileStorageService.uploadFile(file);

        UserDto userDto = new UserDto();
        userDto.setDisplayName(currentUser.getDisplayName());
        userDto.setAvatarUrl(fileUrl);

        return ResponseEntity.ok(userService.updateUser(id, userDto));
    }

    @Operation(summary = "Đổi mật khẩu", description = "Đổi mật khẩu cho người dùng hiện tại")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Đổi mật khẩu thành công"),
            @ApiResponse(responseCode = "403", description = "Bị cấm - Người dùng chỉ có thể đổi mật khẩu của chính mình"),
            @ApiResponse(responseCode = "400", description = "Mật khẩu cũ không đúng")
    })
    @PostMapping("/{id}/change-password")
    public ResponseEntity<?> changePassword(
            @Parameter(description = "ID người dùng", required = true) @PathVariable Long id,
            @Parameter(description = "Mật khẩu hiện tại", required = true) @RequestParam String oldPassword,
            @Parameter(description = "Mật khẩu mới", required = true) @RequestParam String newPassword,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        UserDto currentUser = userService.getUserByPhone(userDetails.getUsername());
        if (!currentUser.getUserId().equals(id)) {
            return ResponseEntity.status(403).build();
        }

        boolean success = userService.changePassword(id, oldPassword, newPassword);
        if (success) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.badRequest().body("Invalid old password");
        }
    }

    @Operation(summary = "Tìm người dùng theo số điện thoại", description = "Lấy thông tin hồ sơ người dùng theo số điện thoại chính xác")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lấy thông tin hồ sơ thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng với số điện thoại đã cho")
    })
    @GetMapping("/by-phone")
    public ResponseEntity<UserDto> getUserByPhone(
            @Parameter(description = "Số điện thoại", required = true) @RequestParam String phone) {
        return ResponseEntity.ok(userService.getUserByPhone(phone));
    }
}