package com.chatapp.dto.response;

import com.chatapp.enums.Platform;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceSessionDto {
    private String sessionId;
    private String deviceId;
    private Platform platform;
    private String deviceName;
    private String ipAddress;
    private String location;
    private LocalDateTime loginTime;
    private LocalDateTime lastActiveTime;
    private LocalDateTime logoutTime;
    private boolean isCurrentSession;
}