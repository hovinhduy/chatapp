package com.chatapp.enums;

public enum QrLoginStatus {
    PENDING, // Chờ quét QR
    SCANNED, // Đã quét QR, chờ xác nhận
    CONFIRMED, // Đã xác nhận đăng nhập
    EXPIRED, // Đã hết hạn
    REJECTED, // Từ chối đăng nhập
    USED // Đã sử dụng
}