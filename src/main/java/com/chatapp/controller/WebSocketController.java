package com.chatapp.controller;

import com.chatapp.dto.MessageDto;
import com.chatapp.service.MessageService;
import com.chatapp.service.UserService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class WebSocketController {

    private final MessageService messageService;
    private final UserService userService;

    public WebSocketController(MessageService messageService, UserService userService) {
        this.messageService = messageService;
        this.userService = userService;
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
}