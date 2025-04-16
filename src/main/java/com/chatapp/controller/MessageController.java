package com.chatapp.controller;

import com.chatapp.dto.request.MessageDto;
import com.chatapp.service.FileStorageService;
import com.chatapp.service.MessageService;
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
@RequestMapping("/api/messages")
@Tag(name = "Message Management", description = "Các API quản lý tin nhắn trò chuyện")
@SecurityRequirement(name = "bearerAuth")
public class MessageController {

        private final MessageService messageService;
        private final UserService userService;
        private final FileStorageService fileStorageService;

        public MessageController(MessageService messageService, UserService userService,
                        FileStorageService fileStorageService) {
                this.messageService = messageService;
                this.userService = userService;
                this.fileStorageService = fileStorageService;
        }

        @Operation(summary = "Gửi tin nhắn trực tiếp", description = "Gửi tin nhắn trực tiếp đến người dùng khác")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Gửi tin nhắn thành công"),
                        @ApiResponse(responseCode = "400", description = "Dữ liệu tin nhắn không hợp lệ"),
                        @ApiResponse(responseCode = "404", description = "Không tìm thấy người nhận")
        })
        @PostMapping("/direct")
        public ResponseEntity<MessageDto> sendDirectMessage(
                        @Parameter(description = "Message details", required = true) @RequestBody MessageDto messageDto,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                return ResponseEntity.ok(messageService.sendDirectMessage(messageDto, userId));
        }

        @Operation(summary = "Gửi tin nhắn nhóm", description = "Gửi tin nhắn vào nhóm trò chuyện")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Gửi tin nhắn thành công"),
                        @ApiResponse(responseCode = "400", description = "Dữ liệu tin nhắn không hợp lệ"),
                        @ApiResponse(responseCode = "404", description = "Không tìm thấy nhóm")
        })
        @PostMapping("/group")
        public ResponseEntity<MessageDto> sendGroupMessage(
                        @Parameter(description = "Message details", required = true) @RequestBody MessageDto messageDto,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                return ResponseEntity.ok(messageService.sendGroupMessage(messageDto, userId));
        }

        @Operation(summary = "Tải lên tệp tin", description = "Tải lên tệp tin để chia sẻ trong tin nhắn")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Tải tệp tin thành công"),
                        @ApiResponse(responseCode = "400", description = "Định dạng hoặc kích thước tệp tin không hợp lệ")
        })
        @PostMapping("/file")
        public ResponseEntity<String> uploadFile(
                        @Parameter(description = "File to upload", required = true) @RequestParam("file") MultipartFile file)
                        throws IOException {

                String fileUrl = fileStorageService.uploadFile(file);
                return ResponseEntity.ok(fileUrl);
        }

        @Operation(summary = "Lấy tin nhắn trực tiếp", description = "Lấy lịch sử trò chuyện giữa hai người dùng")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Lấy tin nhắn thành công"),
                        @ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng")
        })
        @GetMapping("/direct/{userId}")
        public ResponseEntity<List<MessageDto>> getDirectMessages(
                        @Parameter(description = "User ID to get chat history with", required = true) @PathVariable Long userId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long currentUserId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                return ResponseEntity.ok(messageService.getDirectMessages(currentUserId, userId));
        }

        @Operation(summary = "Lấy tin nhắn nhóm", description = "Lấy lịch sử trò chuyện của nhóm")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Lấy tin nhắn thành công"),
                        @ApiResponse(responseCode = "404", description = "Không tìm thấy nhóm")
        })
        @GetMapping("/group/{groupId}")
        public ResponseEntity<List<MessageDto>> getGroupMessages(
                        @Parameter(description = "Group ID", required = true) @PathVariable Long groupId,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                return ResponseEntity.ok(messageService.getGroupMessages(groupId, userId));
        }

        @Operation(summary = "Cập nhật tin nhắn", description = "Cập nhật nội dung tin nhắn đã gửi")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Cập nhật tin nhắn thành công"),
                        @ApiResponse(responseCode = "403", description = "Không có quyền cập nhật tin nhắn này"),
                        @ApiResponse(responseCode = "404", description = "Không tìm thấy tin nhắn")
        })
        @PutMapping("/{id}")
        public ResponseEntity<MessageDto> updateMessage(
                        @Parameter(description = "Message ID", required = true) @PathVariable Long id,
                        @Parameter(description = "New message content", required = true) @RequestParam String content,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                return ResponseEntity.ok(messageService.updateMessage(id, content, userId));
        }

        @Operation(summary = "Xóa tin nhắn", description = "Xóa tin nhắn đã gửi")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Xóa tin nhắn thành công"),
                        @ApiResponse(responseCode = "403", description = "Không có quyền xóa tin nhắn này"),
                        @ApiResponse(responseCode = "404", description = "Không tìm thấy tin nhắn")
        })
        @DeleteMapping("/{id}")
        public ResponseEntity<?> deleteMessage(
                        @Parameter(description = "Message ID", required = true) @PathVariable Long id,
                        @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

                Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
                messageService.deleteMessage(id, userId);
                return ResponseEntity.ok().build();
        }
}