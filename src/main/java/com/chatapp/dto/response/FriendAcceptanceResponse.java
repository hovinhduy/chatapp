package com.chatapp.dto.response;

import com.chatapp.dto.request.ConversationDto;
import com.chatapp.dto.request.FriendDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO trả về khi chấp nhận lời mời kết bạn
 * Bao gồm thông tin về mối quan hệ bạn bè và cuộc trò chuyện được tạo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendAcceptanceResponse {
    /**
     * Thông tin về mối quan hệ bạn bè
     */
    private FriendDto friendship;

    /**
     * Thông tin về cuộc trò chuyện được tạo
     */
    private ConversationDto conversation;
}