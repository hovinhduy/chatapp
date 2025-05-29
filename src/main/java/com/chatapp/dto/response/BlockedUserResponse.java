package com.chatapp.dto.response;

import com.chatapp.dto.request.UserDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockedUserResponse {

    private Long conversationId;
    private UserDto blockedUser;
    private LocalDateTime blockedAt;
}