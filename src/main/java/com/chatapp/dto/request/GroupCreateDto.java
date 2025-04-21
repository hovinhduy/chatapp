package com.chatapp.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupCreateDto {
    private String name;
    private String avatarUrl;
    private List<Long> memberIds;
}