package com.chatapp.dto.request;

import com.chatapp.enums.GroupRole;
import lombok.Data;

@Data
public class GroupMemberDto {
    private Long id;
    private Long groupId;
    private UserDto user;
    private GroupRole role;
}