package com.chatapp.dto;

import com.chatapp.model.Friend;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendDto {
    private Long id;
    private UserDto user1;
    private UserDto user2;
    private Friend.FriendStatus status;
    private LocalDateTime createdAt;
}