package com.chatapp.dto.request;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MessageDto {
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String content;
    private LocalDateTime createdAt;

    // Thêm các trường bổ sung
    private Long receiverId;
    private Long groupId;
    private String type;
    private String fileUrl;
    private String senderName;

}