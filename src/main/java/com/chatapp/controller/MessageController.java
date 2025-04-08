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
@Tag(name = "Message Management", description = "APIs for managing chat messages")
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

    @Operation(summary = "Send direct message", description = "Sends a direct message to another user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Message sent successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid message data"),
            @ApiResponse(responseCode = "404", description = "Recipient not found")
    })
    @PostMapping("/direct")
    public ResponseEntity<MessageDto> sendDirectMessage(
            @Parameter(description = "Message details", required = true) @RequestBody MessageDto messageDto,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
        return ResponseEntity.ok(messageService.sendDirectMessage(messageDto, userId));
    }

    @Operation(summary = "Send group message", description = "Sends a message to a group chat")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Message sent successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid message data"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    @PostMapping("/group")
    public ResponseEntity<MessageDto> sendGroupMessage(
            @Parameter(description = "Message details", required = true) @RequestBody MessageDto messageDto,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
        return ResponseEntity.ok(messageService.sendGroupMessage(messageDto, userId));
    }

    @Operation(summary = "Upload file", description = "Uploads a file for sharing in messages")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "File uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file format or size")
    })
    @PostMapping("/file")
    public ResponseEntity<String> uploadFile(
            @Parameter(description = "File to upload", required = true) @RequestParam("file") MultipartFile file)
            throws IOException {

        String fileUrl = fileStorageService.uploadFile(file);
        return ResponseEntity.ok(fileUrl);
    }

    @Operation(summary = "Get direct messages", description = "Retrieves the chat history between two users")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved messages"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/direct/{userId}")
    public ResponseEntity<List<MessageDto>> getDirectMessages(
            @Parameter(description = "User ID to get chat history with", required = true) @PathVariable Long userId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        Long currentUserId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
        return ResponseEntity.ok(messageService.getDirectMessages(currentUserId, userId));
    }

    @Operation(summary = "Get group messages", description = "Retrieves the chat history of a group")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved messages"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<MessageDto>> getGroupMessages(
            @Parameter(description = "Group ID", required = true) @PathVariable Long groupId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
        return ResponseEntity.ok(messageService.getGroupMessages(groupId, userId));
    }

    @Operation(summary = "Update message", description = "Updates the content of an existing message")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Message updated successfully"),
            @ApiResponse(responseCode = "403", description = "Not authorized to update this message"),
            @ApiResponse(responseCode = "404", description = "Message not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<MessageDto> updateMessage(
            @Parameter(description = "Message ID", required = true) @PathVariable Long id,
            @Parameter(description = "New message content", required = true) @RequestParam String content,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
        return ResponseEntity.ok(messageService.updateMessage(id, content, userId));
    }

    @Operation(summary = "Delete message", description = "Deletes an existing message")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Message deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Not authorized to delete this message"),
            @ApiResponse(responseCode = "404", description = "Message not found")
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