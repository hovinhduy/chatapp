package com.chatapp.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberDto {
    private Long id;
    private Long groupId;
    private UserDto user;
    private boolean isAdmin;
}