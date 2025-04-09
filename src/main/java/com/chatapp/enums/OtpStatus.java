package com.chatapp.enums;

public enum OtpStatus {
    UNVERIFIED, // OTP đã gửi nhưng chưa xác thực
    VERIFIED, // OTP đã được xác thực thành công
    EXPIRED // OTP đã hết hạn
}
