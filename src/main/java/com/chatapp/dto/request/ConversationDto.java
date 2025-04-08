package com.chatapp.dto;

import com.chatapp.model.Conversation;

import java.time.LocalDateTime;
import java.util.List;

public class ConversationDto {
    private Long id;
    private Conversation.ConversationType type;
    private List<UserDto> participants;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Conversation.ConversationType getType() {
        return type;
    }

    public void setType(Conversation.ConversationType type) {
        this.type = type;
    }

    public List<UserDto> getParticipants() {
        return participants;
    }

    public void setParticipants(List<UserDto> participants) {
        this.participants = participants;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}