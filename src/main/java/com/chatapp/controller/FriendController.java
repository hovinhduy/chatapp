package com.chatapp.controller;

import com.chatapp.dto.FriendDto;
import com.chatapp.service.FriendService;
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

import java.util.List;

@RestController
@RequestMapping("/api/friends")
@Tag(name = "Friend Management", description = "APIs for managing friend relationships")
@SecurityRequirement(name = "bearerAuth")
public class FriendController {

        private final FriendService friendService;
        private final UserService userService;

        public FriendController(FriendService friendService, UserService userService) {
                this.friendService = friendService;
                this.userService = userService;
        }

        @Operation(summary = "Send friend request", description = "Sends a friend request to another user")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Friend request sent successfully"),
                        @ApiResponse(responseCode = "404", description = "User not found"),
                        @ApiResponse(responseCode = "409", description = "Friend request already exists")
        })
        @PostMapping("/request/{userId}")
        public ResponseEntity<FriendDto> sendFriendRequest(
                        @Parameter(description = "User ID to send friend request to", required = true) @PathVariable Long userId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long senderId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                return ResponseEntity.ok(friendService.sendFriendRequest(senderId, userId));
        }

        @Operation(summary = "Accept friend request", description = "Accepts a pending friend request")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Friend request accepted successfully"),
                        @ApiResponse(responseCode = "403", description = "Not authorized to accept this request"),
                        @ApiResponse(responseCode = "404", description = "Friend request not found")
        })
        @PostMapping("/accept/{friendshipId}")
        public ResponseEntity<FriendDto> acceptFriendRequest(
                        @Parameter(description = "Friendship ID", required = true) @PathVariable Long friendshipId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                return ResponseEntity.ok(friendService.acceptFriendRequest(friendshipId, userId));
        }

        @Operation(summary = "Reject friend request", description = "Rejects a pending friend request")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Friend request rejected successfully"),
                        @ApiResponse(responseCode = "403", description = "Not authorized to reject this request"),
                        @ApiResponse(responseCode = "404", description = "Friend request not found")
        })
        @PostMapping("/reject/{friendshipId}")
        public ResponseEntity<FriendDto> rejectFriendRequest(
                        @Parameter(description = "Friendship ID", required = true) @PathVariable Long friendshipId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                return ResponseEntity.ok(friendService.rejectFriendRequest(friendshipId, userId));
        }

        @Operation(summary = "Block friend", description = "Blocks a user from sending friend requests")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "User blocked successfully"),
                        @ApiResponse(responseCode = "404", description = "User not found")
        })
        @PostMapping("/block/{userId}")
        public ResponseEntity<FriendDto> blockFriend(
                        @Parameter(description = "User ID to block", required = true) @PathVariable Long userId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long currentUserId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                return ResponseEntity.ok(friendService.blockFriend(currentUserId, userId));
        }

        @Operation(summary = "Unblock friend", description = "Unblocks a previously blocked user")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "User unblocked successfully"),
                        @ApiResponse(responseCode = "404", description = "User not found")
        })
        @PostMapping("/unblock/{userId}")
        public ResponseEntity<FriendDto> unblockFriend(
                        @Parameter(description = "User ID to unblock", required = true) @PathVariable Long userId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long currentUserId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                return ResponseEntity.ok(friendService.unblockFriend(currentUserId, userId));
        }

        @Operation(summary = "Get friends", description = "Retrieves the list of all friends")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Successfully retrieved friends list")
        })
        @GetMapping
        public ResponseEntity<List<FriendDto>> getFriends(
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                return ResponseEntity.ok(friendService.getFriends(userId));
        }

        @Operation(summary = "Get pending requests", description = "Retrieves the list of pending friend requests")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Successfully retrieved pending requests")
        })
        @GetMapping("/pending")
        public ResponseEntity<List<FriendDto>> getPendingFriendRequests(
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                return ResponseEntity.ok(friendService.getPendingFriendRequests(userId));
        }

        @Operation(summary = "Get sent requests", description = "Retrieves the list of sent friend requests")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Successfully retrieved sent requests")
        })
        @GetMapping("/sent")
        public ResponseEntity<List<FriendDto>> getSentFriendRequests(
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                return ResponseEntity.ok(friendService.getSentFriendRequests(userId));
        }

        @Operation(summary = "Withdraw friend request", description = "Withdraws a sent friend request")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Friend request withdrawn successfully"),
                        @ApiResponse(responseCode = "403", description = "Not authorized to withdraw this request"),
                        @ApiResponse(responseCode = "404", description = "Friend request not found")
        })
        @DeleteMapping("/withdraw/{friendshipId}")
        public ResponseEntity<FriendDto> withdrawFriendRequest(
                        @Parameter(description = "Friendship ID", required = true) @PathVariable Long friendshipId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                return ResponseEntity.ok(friendService.withdrawFriendRequest(friendshipId, userId));
        }

        @Operation(summary = "Search friends by name", description = "Searches friends by their display name")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Successfully retrieved search results")
        })
        @GetMapping("/search")
        public ResponseEntity<List<FriendDto>> searchFriendsByName(
                        @Parameter(description = "Search term for display name", required = true) @RequestParam String searchTerm,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                return ResponseEntity.ok(friendService.searchFriendsByName(userId, searchTerm));
        }
}