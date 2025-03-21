package com.chatapp.controller;

import com.chatapp.dto.UserDto;
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
@Tag(name = "User Management", description = "APIs for managing user profiles and settings")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;
    private final FileStorageService fileStorageService;

    public UserController(UserService userService, FileStorageService fileStorageService) {
        this.userService = userService;
        this.fileStorageService = fileStorageService;
    }

    @Operation(summary = "Get current user", description = "Retrieves the profile of the currently authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved user profile"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - User not authenticated")
    })
    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        UserDto user = userService.getUserByPhone(userDetails.getUsername());
        return ResponseEntity.ok(user);
    }

    @Operation(summary = "Get user by ID", description = "Retrieves a user profile by their ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved user profile"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(
            @Parameter(description = "User ID", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @Operation(summary = "Get all users", description = "Retrieves a list of all users in the system")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved user list")
    })
    @GetMapping
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @Operation(summary = "Update user", description = "Updates the profile information of a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully updated user profile"),
            @ApiResponse(responseCode = "403", description = "Forbidden - User can only update their own profile"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(
            @Parameter(description = "User ID", required = true) @PathVariable Long id,
            @Parameter(description = "Updated user information", required = true) @RequestBody UserDto userDto,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        UserDto currentUser = userService.getUserByPhone(userDetails.getUsername());
        if (!currentUser.getUserId().equals(id)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(userService.updateUser(id, userDto));
    }

    @Operation(summary = "Upload avatar", description = "Uploads a new avatar image for the user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully uploaded avatar"),
            @ApiResponse(responseCode = "403", description = "Forbidden - User can only update their own avatar"),
            @ApiResponse(responseCode = "400", description = "Invalid file format or size")
    })
    @PostMapping("/{id}/avatar")
    public ResponseEntity<UserDto> uploadAvatar(
            @Parameter(description = "User ID", required = true) @PathVariable Long id,
            @Parameter(description = "Avatar image file", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) throws IOException {

        UserDto currentUser = userService.getUserByPhone(userDetails.getUsername());
        if (!currentUser.getUserId().equals(id)) {
            return ResponseEntity.status(403).build();
        }

        String fileUrl = fileStorageService.uploadFile(file);

        UserDto userDto = new UserDto();
        userDto.setAvatarUrl(fileUrl);

        return ResponseEntity.ok(userService.updateUser(id, userDto));
    }

    @Operation(summary = "Change password", description = "Changes the password for the current user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully changed password"),
            @ApiResponse(responseCode = "403", description = "Forbidden - User can only change their own password"),
            @ApiResponse(responseCode = "400", description = "Invalid old password")
    })
    @PostMapping("/{id}/change-password")
    public ResponseEntity<?> changePassword(
            @Parameter(description = "User ID", required = true) @PathVariable Long id,
            @Parameter(description = "Current password", required = true) @RequestParam String oldPassword,
            @Parameter(description = "New password", required = true) @RequestParam String newPassword,
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
}