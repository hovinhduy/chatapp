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

        @Operation(summary = "Tạo nhóm", description = "Tạo một nhóm chat mới với bạn bè làm thành viên và tự động tạo cuộc trò chuyện nhóm tương ứng")
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
                                .message("Nhóm và cuộc trò chuyện đã được tạo thành công")
                                .payload(createdGroup)
                                .build();

                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Tạo nhóm với ảnh đại diện", description = "Tạo một nhóm chat mới với ảnh đại diện (tùy chọn), bạn bè làm thành viên và tự động tạo cuộc trò chuyện nhóm tương ứng")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tạo nhóm thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu nhóm không hợp lệ hoặc lỗi khi tải ảnh")
        })
        @PostMapping(value = "/with-avatar", consumes = { "multipart/form-data" })
        public ResponseEntity<ApiResponse<GroupDto>> createGroupWithAvatar(
                        @Parameter(description = "Tên nhóm", required = true) @RequestParam("name") String name,
                        @Parameter(description = "Danh sách ID thành viên, cách nhau bởi dấu phẩy", required = true) @RequestParam("memberIds") String memberIdsStr,
                        @Parameter(description = "Tệp ảnh đại diện nhóm (tùy chọn)") @RequestParam(value = "file", required = false) MultipartFile file,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) throws IOException {

                // Khởi tạo fileUrl là null
                String fileUrl = null;

                // Chỉ tải lên ảnh đại diện nếu file không phải null
                if (file != null && !file.isEmpty()) {
                        fileUrl = fileStorageService.uploadFile(file);
                }

                // Chuyển đổi chuỗi memberIds thành List<Long>
                List<Long> memberIds = new java.util.ArrayList<>();
                for (String id : memberIdsStr.split(",")) {
                        if (!id.trim().isEmpty()) {
                                memberIds.add(Long.parseLong(id.trim()));
                        }
                }

                // Tạo đối tượng GroupCreateDto
                GroupCreateDto groupCreateDto = new GroupCreateDto();
                groupCreateDto.setName(name);
                groupCreateDto.setAvatarUrl(fileUrl); // Có thể là null
                groupCreateDto.setMemberIds(memberIds);

                // Lấy userId của người đang đăng nhập
                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();

                // Gọi service để tạo nhóm
                GroupDto createdGroup = groupService.createGroup(groupCreateDto, userId);

                ApiResponse<GroupDto> response = ApiResponse.<GroupDto>builder()
                                .success(true)
                                .message("Nhóm đã được tạo thành công" + (fileUrl != null ? " với ảnh đại diện" : ""))
                                .payload(createdGroup)
                                .data("avatarUrl", fileUrl)
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

        @Operation(summary = "Thăng cấp thành phó nhóm", description = "Thăng cấp một thành viên nhóm thành phó nhóm")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Thăng cấp thành phó nhóm thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền thăng cấp thành viên"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy nhóm hoặc người dùng")
        })
        @PostMapping("/{id}/deputies/{userId}")
        public ResponseEntity<ApiResponse<Void>> makeDeputy(
                        @Parameter(description = "ID của nhóm", required = true) @PathVariable Long id,
                        @Parameter(description = "ID của người dùng cần thăng cấp", required = true) @PathVariable Long userId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long leaderId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                groupService.makeDeputy(id, userId, leaderId);

                ApiResponse<Void> response = ApiResponse.<Void>builder()
                                .success(true)
                                .message("Đã thăng cấp thành viên thành phó nhóm")
                                .id(id)
                                .data("userId", userId)
                                .build();

                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Hạ cấp xuống thành viên thường", description = "Hạ cấp một phó nhóm xuống thành viên thường")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Hạ cấp thành viên thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền hạ cấp thành viên"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy nhóm hoặc người dùng")
        })
        @PostMapping("/{id}/members/{userId}/demote")
        public ResponseEntity<ApiResponse<Void>> demoteToMember(
                        @Parameter(description = "ID của nhóm", required = true) @PathVariable Long id,
                        @Parameter(description = "ID của người dùng cần hạ cấp", required = true) @PathVariable Long userId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long leaderId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                groupService.demoteToMember(id, userId, leaderId);

                ApiResponse<Void> response = ApiResponse.<Void>builder()
                                .success(true)
                                .message("Đã hạ cấp xuống thành viên thường")
                                .id(id)
                                .data("userId", userId)
                                .build();

                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Chuyển quyền trưởng nhóm", description = "Chuyển quyền trưởng nhóm cho một thành viên khác")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chuyển quyền trưởng nhóm thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền chuyển quyền trưởng nhóm"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy nhóm hoặc người dùng")
        })
        @PostMapping("/{id}/transfer-leadership/{newLeaderId}")
        public ResponseEntity<ApiResponse<Void>> transferLeadership(
                        @Parameter(description = "ID của nhóm", required = true) @PathVariable Long id,
                        @Parameter(description = "ID của người dùng được chọn làm trưởng nhóm mới", required = true) @PathVariable Long newLeaderId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long currentLeaderId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                groupService.transferLeadership(id, newLeaderId, currentLeaderId);

                ApiResponse<Void> response = ApiResponse.<Void>builder()
                                .success(true)
                                .message("Đã chuyển quyền trưởng nhóm thành công")
                                .id(id)
                                .data("newLeaderId", newLeaderId)
                                .build();

                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Giải tán nhóm", description = "Giải tán nhóm và xóa tất cả dữ liệu liên quan đến nhóm (chỉ trưởng nhóm mới có quyền thực hiện)")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Giải tán nhóm thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền giải tán nhóm"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy nhóm")
        })
        @DeleteMapping("/{id}/dissolve")
        public ResponseEntity<ApiResponse<Void>> dissolveGroup(
                        @Parameter(description = "ID của nhóm", required = true) @PathVariable Long id,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long leaderId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                groupService.dissolveGroup(id, leaderId);

                ApiResponse<Void> response = ApiResponse.<Void>builder()
                                .success(true)
                                .message("Nhóm đã được giải tán thành công")
                                .id(id)
                                .build();

                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Rời khỏi nhóm", description = "Cho phép thành viên rời khỏi nhóm (trưởng nhóm không thể rời khỏi, phải chuyển quyền trước)")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Rời khỏi nhóm thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Không thể rời khỏi nhóm (trưởng nhóm hoặc không phải thành viên)"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy nhóm")
        })
        @PostMapping("/{id}/leave")
        public ResponseEntity<ApiResponse<Void>> leaveGroup(
                        @Parameter(description = "ID của nhóm", required = true) @PathVariable Long id,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                groupService.leaveGroup(id, userId);

                ApiResponse<Void> response = ApiResponse.<Void>builder()
                                .success(true)
                                .message("Bạn đã rời khỏi nhóm thành công")
                                .id(id)
                                .build();

                return ResponseEntity.ok(response);
        }
}