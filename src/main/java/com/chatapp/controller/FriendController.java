package com.chatapp.controller;

import com.chatapp.dto.request.FriendDto;
import com.chatapp.dto.response.ApiResponse;
import com.chatapp.service.FriendService;
import com.chatapp.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/friends")
@Tag(name = "Friend Management", description = "Các API quản lý quan hệ bạn bè")
@SecurityRequirement(name = "bearerAuth")
public class FriendController {

        private final FriendService friendService;
        private final UserService userService;
        private final SimpMessagingTemplate messagingTemplate;

        public FriendController(FriendService friendService, UserService userService,
                        SimpMessagingTemplate messagingTemplate) {
                this.friendService = friendService;
                this.userService = userService;
                this.messagingTemplate = messagingTemplate;
        }

        @Operation(summary = "Gửi lời mời kết bạn", description = "Gửi lời mời kết bạn tới người dùng khác")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Gửi lời mời kết bạn thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Đã tồn tại lời mời kết bạn")
        })
        @PostMapping("/request/{userId}")
        public ResponseEntity<ApiResponse<FriendDto>> sendFriendRequest(
                        @Parameter(description = "User ID to send friend request to", required = true) @PathVariable Long userId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long senderId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                FriendDto result = friendService.sendFriendRequest(senderId, userId);

                // Gửi thông báo realtime đến người nhận lời mời kết bạn
                messagingTemplate.convertAndSend("/queue/user/" + userId + "/friend-requests", result);

                ApiResponse<FriendDto> response = ApiResponse.<FriendDto>builder()
                                .success(true)
                                .message("Gửi lời mời kết bạn thành công")
                                .payload(result)
                                .build();
                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Chấp nhận lời mời kết bạn", description = "Chấp nhận lời mời kết bạn đang chờ xử lý")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chấp nhận lời mời kết bạn thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền chấp nhận lời mời này"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy lời mời kết bạn")
        })
        @PostMapping("/accept/{friendshipId}")
        public ResponseEntity<ApiResponse<FriendDto>> acceptFriendRequest(
                        @Parameter(description = "Friendship ID", required = true) @PathVariable Long friendshipId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                FriendDto result = friendService.acceptFriendRequest(friendshipId, userId);

                // Gửi thông báo realtime đến người gửi lời mời kết bạn
                messagingTemplate.convertAndSend("/queue/user/" + result.getSenderId() + "/friend-updates", result);

                ApiResponse<FriendDto> response = ApiResponse.<FriendDto>builder()
                                .success(true)
                                .message("Chấp nhận lời mời kết bạn thành công")
                                .payload(result)
                                .build();
                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Từ chối lời mời kết bạn", description = "Từ chối lời mời kết bạn đang chờ xử lý")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Từ chối lời mời kết bạn thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền từ chối lời mời này"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy lời mời kết bạn")
        })
        @PostMapping("/reject/{friendshipId}")
        public ResponseEntity<ApiResponse<FriendDto>> rejectFriendRequest(
                        @Parameter(description = "Friendship ID", required = true) @PathVariable Long friendshipId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                FriendDto result = friendService.rejectFriendRequest(friendshipId, userId);

                // Gửi thông báo realtime đến người gửi lời mời kết bạn
                messagingTemplate.convertAndSend("/queue/user/" + result.getSenderId() + "/friend-updates", result);

                ApiResponse<FriendDto> response = ApiResponse.<FriendDto>builder()
                                .success(true)
                                .message("Từ chối lời mời kết bạn thành công")
                                .payload(result)
                                .build();
                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Chặn người dùng", description = "Chặn người dùng không cho gửi lời mời kết bạn")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chặn người dùng thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng")
        })
        @PostMapping("/block/{userId}")
        public ResponseEntity<ApiResponse<FriendDto>> blockFriend(
                        @Parameter(description = "User ID to block", required = true) @PathVariable Long userId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long currentUserId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                FriendDto result = friendService.blockFriend(currentUserId, userId);

                // Gửi thông báo realtime đến người bị chặn
                messagingTemplate.convertAndSend("/queue/user/" + userId + "/friend-updates", result);

                ApiResponse<FriendDto> response = ApiResponse.<FriendDto>builder()
                                .success(true)
                                .message("Chặn người dùng thành công")
                                .payload(result)
                                .build();
                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Bỏ chặn người dùng", description = "Bỏ chặn người dùng đã bị chặn trước đó")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Bỏ chặn người dùng thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng")
        })
        @PostMapping("/unblock/{userId}")
        public ResponseEntity<ApiResponse<FriendDto>> unblockFriend(
                        @Parameter(description = "User ID to unblock", required = true) @PathVariable Long userId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long currentUserId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                FriendDto result = friendService.unblockFriend(currentUserId, userId);

                // Gửi thông báo realtime đến người được bỏ chặn
                messagingTemplate.convertAndSend("/queue/user/" + userId + "/friend-updates", result);

                ApiResponse<FriendDto> response = ApiResponse.<FriendDto>builder()
                                .success(true)
                                .message("Bỏ chặn người dùng thành công")
                                .payload(result)
                                .build();
                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Lấy danh sách bạn bè", description = "Lấy toàn bộ danh sách bạn bè")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy danh sách bạn bè thành công")
        })
        @GetMapping
        public ResponseEntity<ApiResponse<List<FriendDto>>> getFriends(
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                List<FriendDto> result = friendService.getFriends(userId);
                ApiResponse<List<FriendDto>> response = ApiResponse.<List<FriendDto>>builder()
                                .success(true)
                                .message("Lấy danh sách bạn bè thành công")
                                .payload(result)
                                .build();
                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Lấy danh sách lời mời kết bạn chờ xử lý", description = "Lấy danh sách lời mời kết bạn đang chờ xử lý")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy danh sách lời mời kết bạn chờ xử lý thành công")
        })
        @GetMapping("/pending")
        public ResponseEntity<ApiResponse<List<FriendDto>>> getPendingFriendRequests(
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                List<FriendDto> result = friendService.getPendingFriendRequests(userId);
                ApiResponse<List<FriendDto>> response = ApiResponse.<List<FriendDto>>builder()
                                .success(true)
                                .message("Lấy danh sách lời mời kết bạn đang chờ thành công")
                                .payload(result)
                                .build();
                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Lấy danh sách lời mời kết bạn đã gửi", description = "Lấy danh sách lời mời kết bạn đã gửi đi")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy danh sách lời mời kết bạn đã gửi thành công")
        })
        @GetMapping("/sent")
        public ResponseEntity<ApiResponse<List<FriendDto>>> getSentFriendRequests(
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                List<FriendDto> result = friendService.getSentFriendRequests(userId);
                ApiResponse<List<FriendDto>> response = ApiResponse.<List<FriendDto>>builder()
                                .success(true)
                                .message("Lấy danh sách lời mời kết bạn đã gửi thành công")
                                .payload(result)
                                .build();
                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Thu hồi lời mời kết bạn", description = "Thu hồi lời mời kết bạn đã gửi")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Thu hồi lời mời kết bạn thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền thu hồi lời mời này"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy lời mời kết bạn")
        })
        @DeleteMapping("/withdraw/{friendshipId}")
        public ResponseEntity<ApiResponse<FriendDto>> withdrawFriendRequest(
                        @Parameter(description = "Friendship ID", required = true) @PathVariable Long friendshipId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                FriendDto result = friendService.withdrawFriendRequest(friendshipId, userId);

                // Gửi thông báo realtime đến người nhận lời mời kết bạn
                messagingTemplate.convertAndSend("/queue/user/" + result.getReceiverId() + "/friend-updates", result);

                ApiResponse<FriendDto> response = ApiResponse.<FriendDto>builder()
                                .success(true)
                                .message("Thu hồi lời mời kết bạn thành công")
                                .payload(result)
                                .build();
                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Tìm kiếm bạn bè theo tên", description = "Tìm kiếm bạn bè theo tên hiển thị")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tìm kiếm bạn bè thành công")
        })
        @GetMapping("/search")
        public ResponseEntity<ApiResponse<List<FriendDto>>> searchFriendsByName(
                        @Parameter(description = "Search term for display name", required = true) @RequestParam String searchTerm,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                List<FriendDto> result = friendService.searchFriendsByName(userId, searchTerm);
                ApiResponse<List<FriendDto>> response = ApiResponse.<List<FriendDto>>builder()
                                .success(true)
                                .message("Tìm kiếm bạn bè theo tên thành công")
                                .payload(result)
                                .build();
                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Xóa bạn bè", description = "Xóa quan hệ bạn bè với người dùng khác")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Xóa bạn bè thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền xóa quan hệ bạn bè này"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy quan hệ bạn bè")
        })
        @DeleteMapping("/{friendshipId}")
        public ResponseEntity<ApiResponse<FriendDto>> deleteFriend(
                        @Parameter(description = "Friendship ID", required = true) @PathVariable Long friendshipId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                FriendDto result = friendService.deleteFriend(friendshipId, userId);

                // Gửi thông báo realtime đến người bạn bị xóa
                if (result.getSenderId().equals(userId)) {
                        messagingTemplate.convertAndSend("/queue/user/" + result.getReceiverId() + "/friend-updates",
                                        result);
                } else {
                        messagingTemplate.convertAndSend("/queue/user/" + result.getSenderId() + "/friend-updates",
                                        result);
                }

                ApiResponse<FriendDto> response = ApiResponse.<FriendDto>builder()
                                .success(true)
                                .message("Xóa bạn bè thành công")
                                .payload(result)
                                .build();
                return ResponseEntity.ok(response);
        }
}