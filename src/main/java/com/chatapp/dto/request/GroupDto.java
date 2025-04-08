package com.chatapp.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupDto {
    private Long groupId;
    private String name;
    private String avatarUrl;
    private LocalDateTime createdAt;
    private List<GroupMemberDto> members;
}