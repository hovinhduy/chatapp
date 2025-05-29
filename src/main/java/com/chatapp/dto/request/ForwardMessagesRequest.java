package com.chatapp.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Request chuyển tiếp nhiều tin nhắn")
public class ForwardMessagesRequest {

    @Schema(description = "Danh sách ID tin nhắn cần chuyển tiếp", required = true)
    private List<Long> messageIds;

}