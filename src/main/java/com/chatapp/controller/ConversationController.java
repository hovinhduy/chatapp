package com.chatapp.controller;

import com.chatapp.dto.request.ConversationDto;
import com.chatapp.dto.request.MessageDto;
import com.chatapp.enums.MessageType;
import com.chatapp.model.Message;
import com.chatapp.model.User;
import com.chatapp.service.ConversationService;
import com.chatapp.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.chatapp.repository.UserRepository;
import com.chatapp.repository.ConversationRepository;
import com.chatapp.repository.MessageRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import java.time.LocalDateTime;
import java.util.List;
import java.security.Principal;
import com.chatapp.dto.request.UserDto;

@RestController
@RequestMapping("/api/conversations")
@Tag(name = "Conversation Management", description = "APIs for managing chat conversations")
@SecurityRequirement(name = "bearerAuth")
public class ConversationController {

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Operation(summary = "Get conversations", description = "Retrieves all conversations for the current user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved conversations")
    })
    @GetMapping
    public ResponseEntity<List<ConversationDto>> getConversations(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
        return ResponseEntity.ok(conversationService.getConversationsByUserId(userId));
    }

    @Operation(summary = "Create conversation", description = "Creates a new conversation with multiple participants")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Conversation created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid participant data"),
            @ApiResponse(responseCode = "404", description = "One or more participants not found")
    })
    @PostMapping
    public ResponseEntity<ConversationDto> createConversation(
            @Parameter(description = "Conversation request with participant IDs", required = true) @RequestBody ConversationRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
        return ResponseEntity.ok(conversationService.createConversation(userId, request.getParticipantIds()));
    }

    @Operation(summary = "Get or create one-to-one conversation", description = "Retrieves an existing one-to-one conversation or creates a new one")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved or created conversation"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PostMapping("/user/{userId}")
    public ResponseEntity<ConversationDto> getOrCreateOneToOneConversation(
            @Parameter(description = "User ID to create/get conversation with", required = true) @PathVariable Long userId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        Long currentUserId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
        return ResponseEntity.ok(conversationService.getOrCreateOneToOneConversation(currentUserId, userId));
    }

    @Operation(summary = "Get conversation messages", description = "Retrieves all messages in a conversation")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved messages"),
            @ApiResponse(responseCode = "403", description = "Not authorized to access this conversation"),
            @ApiResponse(responseCode = "404", description = "Conversation not found")
    })
    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<List<MessageDto>> getMessages(
            @Parameter(description = "Conversation ID", required = true) @PathVariable Long conversationId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userService.getUserByPhone(userDetails.getUsername()).getUserId();
        return ResponseEntity.ok(conversationService.getMessagesByConversationId(conversationId, userId));
    }

    @Operation(summary = "Send message", description = "Sends a new message in a conversation")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Message sent successfully"),
            @ApiResponse(responseCode = "403", description = "Not authorized to send messages in this conversation"),
            @ApiResponse(responseCode = "404", description = "Conversation not found")
    })
    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<MessageDto> sendMessage(
            @Parameter(description = "Conversation ID", required = true) @PathVariable Long conversationId,
            @Parameter(description = "Message details", required = true) @RequestBody MessageDto messageDto,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        UserDto userDto = userService.getUserByPhone(userDetails.getUsername());
        Long senderId = userDto.getUserId();

        messageDto.setConversationId(conversationId);

        if (!conversationService.isUserInConversation(conversationId, senderId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Message message = new Message();
        message.setSender(userRepository.findById(senderId).orElseThrow());
        message.setConversation(conversationRepository.findById(conversationId).orElseThrow());
        message.setContent(messageDto.getContent());
        message.setType(MessageType.TEXT);
        message.setCreatedAt(LocalDateTime.now());

        Message savedMessage = messageRepository.save(message);

        MessageDto savedMessageDto = conversationService.mapToMessageDto(savedMessage);
        messagingTemplate.convertAndSend("/topic/conversation/" + conversationId, savedMessageDto);

        return ResponseEntity.ok(savedMessageDto);
    }

    @Operation(summary = "Handle WebSocket message", description = "Handles incoming WebSocket messages in a conversation")
    @MessageMapping("/conversation/{conversationId}")
    @SendTo("/topic/conversation/{conversationId}")
    public MessageDto handleMessage(
            @Parameter(description = "Conversation ID", required = true) @DestinationVariable Long conversationId,
            @Parameter(description = "Message details", required = true) MessageDto messageDto,
            @Parameter(hidden = true) Principal principal) {
        UserDto userDto = userService.getUserByPhone(principal.getName());
        User sender = userRepository.findById(userDto.getUserId()).orElseThrow();

        messageDto.setConversationId(conversationId);

        if (!conversationService.isUserInConversation(conversationId, sender.getUserId())) {
            throw new AccessDeniedException("Bạn không phải là thành viên của cuộc trò chuyện này");
        }

        Message message = new Message();
        message.setSender(sender);
        message.setConversation(conversationRepository.findById(conversationId).orElseThrow());
        message.setContent(messageDto.getContent());
        message.setType(MessageType.TEXT);
        message.setCreatedAt(LocalDateTime.now());

        Message savedMessage = messageRepository.save(message);

        return conversationService.mapToMessageDto(savedMessage);
    }

    public static class ConversationRequest {
        private List<Long> participantIds;

        public List<Long> getParticipantIds() {
            return participantIds;
        }

        public void setParticipantIds(List<Long> participantIds) {
            this.participantIds = participantIds;
        }
    }
}