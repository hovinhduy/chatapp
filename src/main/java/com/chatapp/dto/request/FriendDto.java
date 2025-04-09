package com.chatapp.dto.request;

import com.chatapp.enums.FriendStatus;
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
    private FriendStatus status;
    private LocalDateTime createdAt;
}