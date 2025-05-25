package com.chatapp.controller;

import com.chatapp.dto.request.FriendDto;
import com.chatapp.dto.request.MessageDto;
import com.chatapp.dto.response.FriendAcceptanceResponse;
import com.chatapp.service.FriendService;
import com.chatapp.service.MessageService;
import com.chatapp.service.UserService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class WebSocketController {

    private final MessageService messageService;
    private final UserService userService;
    private final FriendService friendService;

    public WebSocketController(MessageService messageService, UserService userService, FriendService friendService) {
        this.messageService = messageService;
        this.userService = userService;
        this.friendService = friendService;
    }

    @MessageMapping("/chat.sendDirectMessage")
    public void sendDirectMessage(@Payload MessageDto messageDto, Principal principal,
            SimpMessageHeaderAccessor headerAccessor) {
        Long senderId = userService.getUserByPhone(principal.getName()).getUserId();
        messageService.sendDirectMessage(messageDto, senderId);
    }

    @MessageMapping("/chat.sendGroupMessage")
    public void sendGroupMessage(@Payload MessageDto messageDto, Principal principal,
            SimpMessageHeaderAccessor headerAccessor) {
        Long senderId = userService.getUserByPhone(principal.getName()).getUserId();
        messageService.sendGroupMessage(messageDto, senderId);
    }

    @MessageMapping("/friend.sendRequest/{receiverId}")
    @SendTo("/queue/user/{receiverId}/friend-requests")
    public FriendDto sendFriendRequest(@DestinationVariable Long receiverId, Principal principal) {
        Long senderId = userService.getUserByPhone(principal.getName()).getUserId();
        return friendService.sendFriendRequest(senderId, receiverId);
    }

    @MessageMapping("/friend.acceptRequest/{friendshipId}")
    @SendTo("/queue/user/{senderId}/friend-updates")
    public FriendAcceptanceResponse acceptFriendRequest(@DestinationVariable Long friendshipId,
            @DestinationVariable Long senderId,
            Principal principal) {
        Long userId = userService.getUserByPhone(principal.getName()).getUserId();
        return friendService.acceptFriendRequestWithConversation(friendshipId, userId);
    }

    @MessageMapping("/friend.rejectRequest/{friendshipId}")
    @SendTo("/queue/user/{senderId}/friend-updates")
    public FriendDto rejectFriendRequest(@DestinationVariable Long friendshipId,
            @DestinationVariable Long senderId,
            Principal principal) {
        Long userId = userService.getUserByPhone(principal.getName()).getUserId();
        return friendService.rejectFriendRequest(friendshipId, userId);
    }
}