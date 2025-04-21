package com.chatapp.controller;

import com.chatapp.dto.request.GroupCreateDto;
import com.chatapp.dto.request.GroupDto;
import com.chatapp.dto.response.ApiResponse;
import com.chatapp.service.FileStorageService;
import com.chatapp.service.GroupService;
import com.chatapp.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@Tag(name = "Group Management", description = "Các API quản lý nhóm chat")
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

        @Operation(summary = "Tạo nhóm", description = "Tạo một nhóm chat mới với bạn bè làm thành viên")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tạo nhóm thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu nhóm không hợp lệ")
        })
        @PostMapping
        public ResponseEntity<ApiResponse<GroupDto>> createGroup(
                        @Parameter(description = "Thông tin chi tiết nhóm", required = true) @RequestBody GroupCreateDto groupCreateDto,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                GroupDto createdGroup = groupService.createGroup(groupCreateDto, userId);

                ApiResponse<GroupDto> response = ApiResponse.<GroupDto>builder()
                                .success(true)
                                .message("Nhóm đã được tạo thành công")
                                .payload(createdGroup)
                                .id(createdGroup.getGroupId())
                                .build();

                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Lấy nhóm theo ID", description = "Lấy thông tin chi tiết của nhóm theo ID")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy thông tin nhóm thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy nhóm")
        })
        @GetMapping("/{id}")
        public ResponseEntity<ApiResponse<GroupDto>> getGroupById(
                        @Parameter(description = "ID của nhóm", required = true) @PathVariable Long id) {
                GroupDto group = groupService.getGroupById(id);

                ApiResponse<GroupDto> response = ApiResponse.<GroupDto>builder()
                                .success(true)
                                .message("Lấy thông tin nhóm thành công")
                                .payload(group)
                                .id(id)
                                .build();

                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Lấy danh sách nhóm của người dùng", description = "Lấy tất cả các nhóm mà người dùng hiện tại là thành viên")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy danh sách nhóm của người dùng thành công")
        })
        @GetMapping("/user")
        public ResponseEntity<ApiResponse<List<GroupDto>>> getGroupsByUser(
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                List<GroupDto> groups = groupService.getGroupsByUserId(userId);

                ApiResponse<List<GroupDto>> response = ApiResponse.<List<GroupDto>>builder()
                                .success(true)
                                .message("Lấy danh sách nhóm thành công")
                                .payload(groups)
                                .data("count", groups.size())
                                .build();

                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Tìm kiếm nhóm theo tên", description = "Tìm kiếm các nhóm theo tên mà người dùng hiện tại là thành viên")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tìm kiếm nhóm thành công")
        })
        @GetMapping("/search")
        public ResponseEntity<ApiResponse<List<GroupDto>>> searchGroupsByName(
                        @Parameter(description = "Tên nhóm cần tìm kiếm", required = true) @RequestParam String name,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                List<GroupDto> groups = groupService.searchGroupsByName(userId, name);

                ApiResponse<List<GroupDto>> response = ApiResponse.<List<GroupDto>>builder()
                                .success(true)
                                .message("Tìm kiếm nhóm thành công")
                                .payload(groups)
                                .data("count", groups.size())
                                .data("searchTerm", name)
                                .build();

                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Cập nhật nhóm", description = "Cập nhật thông tin của nhóm")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cập nhật nhóm thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền cập nhật nhóm này"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy nhóm")
        })
        @PutMapping("/{id}")
        public ResponseEntity<ApiResponse<GroupDto>> updateGroup(
                        @Parameter(description = "ID của nhóm", required = true) @PathVariable Long id,
                        @Parameter(description = "Thông tin nhóm cần cập nhật", required = true) @RequestBody GroupDto groupDto,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                GroupDto updatedGroup = groupService.updateGroup(id, groupDto, userId);

                ApiResponse<GroupDto> response = ApiResponse.<GroupDto>builder()
                                .success(true)
                                .message("Cập nhật nhóm thành công")
                                .payload(updatedGroup)
                                .id(id)
                                .build();

                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Tải lên ảnh đại diện nhóm", description = "Tải lên ảnh đại diện mới cho nhóm")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tải lên ảnh đại diện thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền cập nhật nhóm này"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy nhóm"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Định dạng hoặc kích thước tệp không hợp lệ")
        })
        @PostMapping("/{id}/avatar")
        public ResponseEntity<ApiResponse<GroupDto>> uploadGroupAvatar(
                        @Parameter(description = "ID của nhóm", required = true) @PathVariable Long id,
                        @Parameter(description = "Tệp ảnh đại diện", required = true) @RequestParam("file") MultipartFile file,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) throws IOException {

                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                String fileUrl = fileStorageService.uploadFile(file);

                GroupDto groupDto = new GroupDto();
                groupDto.setAvatarUrl(fileUrl);

                GroupDto updatedGroup = groupService.updateGroup(id, groupDto, userId);

                ApiResponse<GroupDto> response = ApiResponse.<GroupDto>builder()
                                .success(true)
                                .message("Tải lên ảnh đại diện nhóm thành công")
                                .payload(updatedGroup)
                                .id(id)
                                .data("avatarUrl", fileUrl)
                                .build();

                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Thêm thành viên", description = "Thêm một thành viên mới vào nhóm")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Thêm thành viên thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền thêm thành viên"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy nhóm hoặc người dùng"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Người dùng đã là thành viên của nhóm")
        })
        @PostMapping("/{id}/members/{userId}")
        public ResponseEntity<ApiResponse<Void>> addMember(
                        @Parameter(description = "ID của nhóm", required = true) @PathVariable Long id,
                        @Parameter(description = "ID của người dùng cần thêm", required = true) @PathVariable Long userId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long adminId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                groupService.addMember(id, userId, adminId);

                ApiResponse<Void> response = ApiResponse.<Void>builder()
                                .success(true)
                                .message("Thêm thành viên vào nhóm thành công")
                                .id(id)
                                .data("userId", userId)
                                .build();

                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Xóa thành viên", description = "Xóa một thành viên khỏi nhóm")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Xóa thành viên thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền xóa thành viên"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy nhóm hoặc người dùng")
        })
        @DeleteMapping("/{id}/members/{userId}")
        public ResponseEntity<ApiResponse<Void>> removeMember(
                        @Parameter(description = "ID của nhóm", required = true) @PathVariable Long id,
                        @Parameter(description = "ID của người dùng cần xóa", required = true) @PathVariable Long userId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long adminId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                groupService.removeMember(id, userId, adminId);

                ApiResponse<Void> response = ApiResponse.<Void>builder()
                                .success(true)
                                .message("Xóa thành viên khỏi nhóm thành công")
                                .id(id)
                                .data("userId", userId)
                                .build();

                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Thăng cấp thành quản trị viên", description = "Thăng cấp một thành viên nhóm thành quản trị viên")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Thăng cấp thành quản trị viên thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền thăng cấp thành viên"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy nhóm hoặc người dùng")
        })
        @PostMapping("/{id}/admins/{userId}")
        public ResponseEntity<ApiResponse<Void>> makeAdmin(
                        @Parameter(description = "ID của nhóm", required = true) @PathVariable Long id,
                        @Parameter(description = "ID của người dùng cần thăng cấp", required = true) @PathVariable Long userId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long adminId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                groupService.makeAdmin(id, userId, adminId);

                ApiResponse<Void> response = ApiResponse.<Void>builder()
                                .success(true)
                                .message("Đã thăng cấp thành viên thành quản trị viên")
                                .id(id)
                                .data("userId", userId)
                                .build();

                return ResponseEntity.ok(response);
        }
}