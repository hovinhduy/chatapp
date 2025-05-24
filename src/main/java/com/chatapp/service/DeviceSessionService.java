package com.chatapp.service;

import com.chatapp.dto.request.DeviceInfoDto;
import com.chatapp.dto.response.DeviceSessionDto;
import com.chatapp.enums.Platform;
import com.chatapp.model.Token;
import com.chatapp.model.User;
import com.chatapp.model.UserDeviceSession;
import com.chatapp.repository.TokenRepository;
import com.chatapp.repository.UserDeviceSessionRepository;
import com.chatapp.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DeviceSessionService {

    @Autowired
    private UserDeviceSessionRepository deviceSessionRepository;

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    /**
     * Tạo device session mới khi đăng nhập
     */
    @Transactional
    public UserDeviceSession createDeviceSession(User user, DeviceInfoDto deviceInfo) {
        // Kiểm tra và đóng các session cũ trên cùng platform (giới hạn 1 session cho cả
        // WEB và MOBILE)
        List<UserDeviceSession> activeSessions = deviceSessionRepository
                .findActiveSessionsByUserAndPlatform(user.getUserId(), deviceInfo.getPlatform());

        // Đóng session cũ nếu có (áp dụng cho cả WEB và MOBILE)
        if (!activeSessions.isEmpty()) {
            for (UserDeviceSession oldSession : activeSessions) {
                logoutDeviceSession(oldSession.getId(), "session_kick");
            }
        }

        // Tạo session mới
        UserDeviceSession newSession = new UserDeviceSession();
        newSession.setUser(user);
        newSession.setDeviceId(deviceInfo.getDeviceId());
        newSession.setPlatform(deviceInfo.getPlatform());
        newSession.setDeviceName(deviceInfo.getDeviceModel());
        newSession.setIpAddress(deviceInfo.getIpAddress());
        newSession.setLocation(deviceInfo.getLocation());
        newSession.setLoginTime(LocalDateTime.now());
        newSession.setLastActiveTime(LocalDateTime.now());

        return deviceSessionRepository.save(newSession);
    }

    /**
     * Tạo token cho device session
     */
    @Transactional
    public Token createTokenForSession(UserDeviceSession session, String accessToken, String refreshToken,
            LocalDateTime accessExpiry, LocalDateTime refreshExpiry) {
        Token token = new Token();
        token.setToken(accessToken);
        token.setExpirationDate(accessExpiry);
        token.setRefreshToken(refreshToken);
        token.setRefreshExpirationDate(refreshExpiry);
        token.setDeviceSession(session);

        return tokenRepository.save(token);
    }

    /**
     * Đăng xuất device session
     */
    @Transactional
    public void logoutDeviceSession(String sessionId, String reason) {
        UserDeviceSession session = deviceSessionRepository.findById(sessionId).orElse(null);
        if (session != null && session.getLogoutTime() == null) {
            // Cập nhật logout time
            session.setLogoutTime(LocalDateTime.now());
            deviceSessionRepository.save(session);

            // Vô hiệu hóa tất cả token của session
            tokenRepository.revokeAllTokensBySessionId(sessionId);

            // Thêm token vào blacklist
            List<Token> tokens = tokenRepository.findByDeviceSession(session);
            for (Token token : tokens) {
                tokenBlacklistService.addToBlacklist(token.getToken(), sessionId,
                        session.getUser().getUserId(), reason);
                if (token.getRefreshToken() != null) {
                    tokenBlacklistService.addToBlacklist(token.getRefreshToken(), sessionId,
                            session.getUser().getUserId(), reason);
                }
            }
        }
    }

    /**
     * Lấy thông tin thiết bị từ request header
     */
    public DeviceInfoDto extractDeviceInfo(HttpServletRequest request) {
        String platform = request.getHeader("X-Platform");
        String deviceModel = request.getHeader("X-Device-Model");
        String deviceId = request.getHeader("X-Device-Id");
        String ipAddress = request.getHeader("X-IP-Address");
        String location = request.getHeader("X-Location");

        // Debug log
        System.out.println("=== EXTRACTING DEVICE INFO ===");
        System.out.println("Raw X-Platform header: '" + platform + "'");
        System.out.println("Raw X-Device-Model header: '" + deviceModel + "'");
        System.out.println("Raw X-Device-Id header: '" + deviceId + "'");
        System.out.println("Raw X-IP-Address header: '" + ipAddress + "'");
        System.out.println("Raw X-Location header: '" + location + "'");

        // Kiểm tra và đặt giá trị mặc định nếu header không có
        if (platform == null || platform.trim().isEmpty()) {
            platform = "WEB";
            System.out.println("Platform not provided, defaulting to: WEB");
        }

        if (deviceModel == null || deviceModel.trim().isEmpty()) {
            String userAgent = request.getHeader("User-Agent");
            deviceModel = userAgent != null ? "Browser - " + userAgent.substring(0, Math.min(50, userAgent.length()))
                    : "Unknown Device";
            System.out.println("Device model not provided, defaulting to: " + deviceModel);
        }

        if (deviceId == null || deviceId.trim().isEmpty()) {
            deviceId = "default-" + System.currentTimeMillis();
            System.out.println("Device ID not provided, defaulting to: " + deviceId);
        }

        // IP Address - ưu tiên từ header, fallback về detect từ request
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            ipAddress = getClientIpAddress(request);
            System.out.println("IP Address not provided in header, detected from request: " + ipAddress);
        } else {
            System.out.println("IP Address from header: " + ipAddress);
        }

        // Location - ưu tiên từ header, fallback về Unknown
        if (location == null || location.trim().isEmpty()) {
            location = "Unknown";
            System.out.println("Location not provided in header, defaulting to: Unknown");
        } else {
            System.out.println("Location from header: " + location);
        }

        try {
            DeviceInfoDto deviceInfo = new DeviceInfoDto(platform, deviceModel, deviceId);
            deviceInfo.setIpAddress(ipAddress);
            deviceInfo.setLocation(location);

            System.out.println("=== FINAL DEVICE INFO ===");
            System.out.println("Platform: " + deviceInfo.getPlatform());
            System.out.println("Device Model: " + deviceInfo.getDeviceModel());
            System.out.println("Device ID: " + deviceInfo.getDeviceId());
            System.out.println("IP Address: " + deviceInfo.getIpAddress());
            System.out.println("Location: " + deviceInfo.getLocation());
            System.out.println("=== END EXTRACTION ===");

            return deviceInfo;
        } catch (Exception e) {
            System.err.println("Error creating DeviceInfoDto: " + e.getMessage());
            e.printStackTrace();

            // Fallback với giá trị mặc định an toàn
            DeviceInfoDto fallbackInfo = new DeviceInfoDto();
            fallbackInfo.setPlatform(com.chatapp.enums.Platform.WEB);
            fallbackInfo.setDeviceModel("Unknown Device");
            fallbackInfo.setDeviceId("fallback-" + System.currentTimeMillis());
            fallbackInfo.setIpAddress(ipAddress != null ? ipAddress : getClientIpAddress(request));
            fallbackInfo.setLocation(location != null ? location : "Unknown");

            return fallbackInfo;
        }
    }

    /**
     * Lấy địa chỉ IP thực của client
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED",
                "HTTP_VIA",
                "REMOTE_ADDR"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }

    /**
     * Lấy danh sách session đang hoạt động của user
     */
    public List<DeviceSessionDto> getActiveSessionsByUser(Long userId, String currentSessionId) {
        List<UserDeviceSession> sessions = deviceSessionRepository.findActiveSessionsByUser(userId);

        return sessions.stream().map(session -> {
            DeviceSessionDto dto = new DeviceSessionDto();
            dto.setSessionId(session.getId());
            dto.setDeviceId(session.getDeviceId());
            dto.setPlatform(session.getPlatform());
            dto.setDeviceName(session.getDeviceName());
            dto.setIpAddress(session.getIpAddress());
            dto.setLocation(session.getLocation());
            dto.setLoginTime(session.getLoginTime());
            dto.setLastActiveTime(session.getLastActiveTime());
            dto.setLogoutTime(session.getLogoutTime());
            dto.setCurrentSession(session.getId().equals(currentSessionId));
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Lấy tất cả session của user (bao gồm cả đã đăng xuất)
     */
    public List<DeviceSessionDto> getAllSessionsByUser(Long userId, String currentSessionId) {
        List<UserDeviceSession> sessions = deviceSessionRepository.findAllByUser_UserId(userId);

        return sessions.stream()
                .sorted((s1, s2) -> {
                    // Sắp xếp: session hiện tại đầu tiên, sau đó theo thời gian login giảm dần
                    if (s1.getId().equals(currentSessionId))
                        return -1;
                    if (s2.getId().equals(currentSessionId))
                        return 1;
                    return s2.getLoginTime().compareTo(s1.getLoginTime());
                })
                .map(session -> {
                    DeviceSessionDto dto = new DeviceSessionDto();
                    dto.setSessionId(session.getId());
                    dto.setDeviceId(session.getDeviceId());
                    dto.setPlatform(session.getPlatform());
                    dto.setDeviceName(session.getDeviceName());
                    dto.setIpAddress(session.getIpAddress());
                    dto.setLocation(session.getLocation());
                    dto.setLoginTime(session.getLoginTime());
                    dto.setLastActiveTime(session.getLastActiveTime());
                    dto.setLogoutTime(session.getLogoutTime());
                    dto.setCurrentSession(session.getId().equals(currentSessionId));
                    return dto;
                }).collect(Collectors.toList());
    }

    /**
     * Lấy danh sách session đã đăng xuất của user
     */
    public List<DeviceSessionDto> getLoggedOutSessionsByUser(Long userId, String currentSessionId) {
        List<UserDeviceSession> sessions = deviceSessionRepository.findLoggedOutSessionsByUser(userId);

        return sessions.stream()
                .sorted((s1, s2) -> s2.getLogoutTime().compareTo(s1.getLogoutTime())) // Sắp xếp theo thời gian đăng
                                                                                      // xuất giảm dần
                .map(session -> {
                    DeviceSessionDto dto = new DeviceSessionDto();
                    dto.setSessionId(session.getId());
                    dto.setDeviceId(session.getDeviceId());
                    dto.setPlatform(session.getPlatform());
                    dto.setDeviceName(session.getDeviceName());
                    dto.setIpAddress(session.getIpAddress());
                    dto.setLocation(session.getLocation());
                    dto.setLoginTime(session.getLoginTime());
                    dto.setLastActiveTime(session.getLastActiveTime());
                    dto.setLogoutTime(session.getLogoutTime());
                    dto.setCurrentSession(false); // Session đã đăng xuất không thể là current session
                    return dto;
                }).collect(Collectors.toList());
    }

    /**
     * Cập nhật last active time cho session
     */
    @Transactional
    public void updateLastActiveTime(String sessionId) {
        deviceSessionRepository.updateLastActiveTime(sessionId, LocalDateTime.now());
    }

    /**
     * Lên lịch dọn dẹp các session cũ
     */
    @Scheduled(cron = "0 0 2 * * ?") // Chạy lúc 2 giờ sáng mỗi ngày
    @Transactional
    public void cleanupOldSessions() {
        // Xóa session đã logout cách đây 30 ngày
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        deviceSessionRepository.deleteOldLoggedOutSessions(cutoffDate);

        // Xóa token đã hết hạn
        tokenRepository.deleteExpiredTokens(LocalDateTime.now());
    }
}