package com.chatapp.util;

public class AppConstants {
    // JWT Constants
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String HEADER_STRING = "Authorization";

    // WebSocket Constants
    public static final String DIRECT_DESTINATION_PREFIX = "/queue/messages";
    public static final String GROUP_DESTINATION_PREFIX = "/topic/group";

    // File Constants
    public static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    public static final String[] ALLOWED_FILE_TYPES = {
            "image/jpeg", "image/png", "image/gif",
            "video/mp4", "application/pdf",
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    };

    // Pagination Constants
    public static final String DEFAULT_PAGE_NUMBER = "0";
    public static final String DEFAULT_PAGE_SIZE = "20";
    public static final String DEFAULT_SORT_BY = "createdAt";
    public static final String DEFAULT_SORT_DIRECTION = "desc";
}