package com.chatapp.dto.request;

import com.chatapp.enums.Platform;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceInfoDto {
    private Platform platform;
    private String deviceModel;
    private String deviceId;
    private String ipAddress;
    private String location;

    public DeviceInfoDto(String platform, String deviceModel, String deviceId) {
        this.platform = Platform.valueOf(platform.toUpperCase());
        this.deviceModel = deviceModel;
        this.deviceId = deviceId;
    }
}