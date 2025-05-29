package com.chatapp.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Request xóa nhiều tin nhắn")
public class DeleteMultipleMessagesRequest {

    @Schema(description = "Danh sách ID tin nhắn cần xóa", required = true)
    private List<Long> messageIds;

}