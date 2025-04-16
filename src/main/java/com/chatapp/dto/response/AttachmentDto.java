package com.chatapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AttachmentDto {
    private Long id;
    private String name;
    private String type;
    private String url;
    private double size;
    private String createdAt;
    private String updatedAt;
}