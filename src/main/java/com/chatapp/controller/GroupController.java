package com.chatapp.controller;

import com.chatapp.dto.request.GroupDto;
import com.chatapp.service.FileStorageService;
import com.chatapp.service.GroupService;
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
@RequestMapping("/api/groups")
@Tag(name = "Group Management", description = "APIs for managing chat groups")
@SecurityRequirement(name = "bearerAuth")
public class GroupController {

        private final GroupService groupService;
        private final UserService userService;
        private final FileStorageService fileStorageService;

        public GroupController(GroupService groupService, UserService userService,
                        FileStorageService fileStorageService) {
                this.groupService = groupService;
                this.userService = userService;
                this.fileStorageService = fileStorageService;
        }

        @Operation(summary = "Create group", description = "Creates a new chat group")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Group created successfully"),
                        @ApiResponse(responseCode = "400", description = "Invalid group data")
        })
        @PostMapping
        public ResponseEntity<GroupDto> createGroup(
                        @Parameter(description = "Group details", required = true) @RequestBody GroupDto groupDto,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                return ResponseEntity.ok(groupService.createGroup(groupDto, userId));
        }

        @Operation(summary = "Get group by ID", description = "Retrieves a group's details by its ID")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Successfully retrieved group details"),
                        @ApiResponse(responseCode = "404", description = "Group not found")
        })
        @GetMapping("/{id}")
        public ResponseEntity<GroupDto> getGroupById(
                        @Parameter(description = "Group ID", required = true) @PathVariable Long id) {
                return ResponseEntity.ok(groupService.getGroupById(id));
        }

        @Operation(summary = "Get user's groups", description = "Retrieves all groups that the current user is a member of")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Successfully retrieved user's groups")
        })
        @GetMapping("/user")
        public ResponseEntity<List<GroupDto>> getGroupsByUser(
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                return ResponseEntity.ok(groupService.getGroupsByUserId(userId));
        }

        @Operation(summary = "Search user's groups by name", description = "Searches for groups by name that the current user is a member of")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Successfully retrieved matching groups")
        })
        @GetMapping("/search")
        public ResponseEntity<List<GroupDto>> searchGroupsByName(
                        @Parameter(description = "Group name to search for", required = true) @RequestParam String name,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                return ResponseEntity.ok(groupService.searchGroupsByName(userId, name));
        }

        @Operation(summary = "Update group", description = "Updates a group's information")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Group updated successfully"),
                        @ApiResponse(responseCode = "403", description = "Not authorized to update this group"),
                        @ApiResponse(responseCode = "404", description = "Group not found")
        })
        @PutMapping("/{id}")
        public ResponseEntity<GroupDto> updateGroup(
                        @Parameter(description = "Group ID", required = true) @PathVariable Long id,
                        @Parameter(description = "Updated group information", required = true) @RequestBody GroupDto groupDto,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                return ResponseEntity.ok(groupService.updateGroup(id, groupDto, userId));
        }

        @Operation(summary = "Upload group avatar", description = "Uploads a new avatar image for the group")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Avatar uploaded successfully"),
                        @ApiResponse(responseCode = "403", description = "Not authorized to update this group"),
                        @ApiResponse(responseCode = "404", description = "Group not found"),
                        @ApiResponse(responseCode = "400", description = "Invalid file format or size")
        })
        @PostMapping("/{id}/avatar")
        public ResponseEntity<GroupDto> uploadGroupAvatar(
                        @Parameter(description = "Group ID", required = true) @PathVariable Long id,
                        @Parameter(description = "Avatar image file", required = true) @RequestParam("file") MultipartFile file,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) throws IOException {

                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                String fileUrl = fileStorageService.uploadFile(file);

                GroupDto groupDto = new GroupDto();
                groupDto.setAvatarUrl(fileUrl);

                return ResponseEntity.ok(groupService.updateGroup(id, groupDto, userId));
        }

        @Operation(summary = "Add member", description = "Adds a new member to the group")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Member added successfully"),
                        @ApiResponse(responseCode = "403", description = "Not authorized to add members"),
                        @ApiResponse(responseCode = "404", description = "Group or user not found"),
                        @ApiResponse(responseCode = "409", description = "User is already a member")
        })
        @PostMapping("/{id}/members/{userId}")
        public ResponseEntity<?> addMember(
                        @Parameter(description = "Group ID", required = true) @PathVariable Long id,
                        @Parameter(description = "User ID to add", required = true) @PathVariable Long userId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long adminId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                groupService.addMember(id, userId, adminId);
                return ResponseEntity.ok().build();
        }

        @Operation(summary = "Remove member", description = "Removes a member from the group")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Member removed successfully"),
                        @ApiResponse(responseCode = "403", description = "Not authorized to remove members"),
                        @ApiResponse(responseCode = "404", description = "Group or user not found")
        })
        @DeleteMapping("/{id}/members/{userId}")
        public ResponseEntity<?> removeMember(
                        @Parameter(description = "Group ID", required = true) @PathVariable Long id,
                        @Parameter(description = "User ID to remove", required = true) @PathVariable Long userId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long adminId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                groupService.removeMember(id, userId, adminId);
                return ResponseEntity.ok().build();
        }

        @Operation(summary = "Make admin", description = "Promotes a group member to admin status")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "User promoted to admin successfully"),
                        @ApiResponse(responseCode = "403", description = "Not authorized to promote members"),
                        @ApiResponse(responseCode = "404", description = "Group or user not found")
        })
        @PostMapping("/{id}/admins/{userId}")
        public ResponseEntity<?> makeAdmin(
                        @Parameter(description = "Group ID", required = true) @PathVariable Long id,
                        @Parameter(description = "User ID to promote", required = true) @PathVariable Long userId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long adminId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                groupService.makeAdmin(id, userId, adminId);
                return ResponseEntity.ok().build();
        }
}