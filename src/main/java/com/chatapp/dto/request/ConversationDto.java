package com.chatapp.dto.request;

import com.chatapp.enums.ConversationType;
import com.chatapp.model.Conversation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDto {
    private Long id;
    private ConversationType type;
    private List<UserDto> participants;
    private LocalDateTime createdAt;

    // Thông tin bổ sung cho nhóm
    private Long groupId;
    private String groupName;
    private String groupAvatarUrl;
}