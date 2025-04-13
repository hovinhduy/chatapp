package com.chatapp.service;

import com.chatapp.model.DeviceSession;
import com.chatapp.model.RefreshToken;
import com.chatapp.model.User;
import com.chatapp.repository.DeviceSessionRepository;
import com.chatapp.repository.RefreshTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DeviceSessionService {

    @Autowired
    private DeviceSessionRepository deviceSessionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    /**
     * Xác định loại thiết bị từ User-Agent
     * 
     * @param userAgent chuỗi User-Agent từ request
     * @return loại thiết bị (WEB hoặc MOBILE)
     */
    public DeviceSession.DeviceType determineDeviceType(String userAgent) {
        if (userAgent == null) {
            return DeviceSession.DeviceType.WEB;
        }

        // Kiểm tra User-Agent cho các thiết bị di động phổ biến
        String userAgentLower = userAgent.toLowerCase();
        if (userAgentLower.contains("mobile") ||
                userAgentLower.contains("android") ||
                userAgentLower.contains("iphone") ||
                userAgentLower.contains("ipad") ||
                userAgentLower.contains("ipod")) {
            return DeviceSession.DeviceType.MOBILE;
        }

        return DeviceSession.DeviceType.WEB;
    }

    /**
     * Tạo phiên đăng nhập mới cho thiết bị
     * 
     * @param user    người dùng đăng nhập
     * @param request HTTP request chứa thông tin thiết bị
     * @return phiên đăng nhập đã tạo
     */
    public DeviceSession createSession(User user, HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        DeviceSession.DeviceType deviceType = determineDeviceType(userAgent);

        // Tạo ID thiết bị ngẫu nhiên nếu không có
        String deviceId = UUID.randomUUID().toString();

        // Kiểm tra xem người dùng đã có phiên đăng nhập trên cùng loại thiết bị chưa
        Optional<DeviceSession> existingSession = deviceSessionRepository.findByUserAndDeviceTypeAndActiveTrue(user,
                deviceType);

        // Nếu đã có phiên đăng nhập trên cùng loại thiết bị, đánh dấu phiên cũ là không
        // còn hoạt động và blacklist token
        existingSession.ifPresent(session -> {
            // Đánh dấu phiên đăng nhập cũ là không hoạt động
            session.setActive(false);
            session.setLogoutTime(Instant.now());
            deviceSessionRepository.save(session);

            // Tìm refresh token liên kết với thiết bị cũ
            Optional<RefreshToken> oldRefreshToken = refreshTokenRepository.findByUserAndDeviceType(user, deviceType);
            oldRefreshToken.ifPresent(token -> {
                // Thêm token vào blacklist
                tokenBlacklistService.blacklistToken(token.getToken());
                // Xóa refresh token cũ
                refreshTokenRepository.delete(token);
            });
        });

        // Tạo phiên đăng nhập mới
        DeviceSession newSession = new DeviceSession();
        newSession.setUser(user);
        newSession.setDeviceId(deviceId);
        newSession.setDeviceType(deviceType);
        newSession.setDeviceInfo(userAgent != null ? userAgent : "Unknown");
        newSession.setLoginTime(Instant.now());
        newSession.setActive(true);

        return deviceSessionRepository.save(newSession);
    }

    /**
     * Đăng xuất khỏi thiết bị
     * 
     * @param deviceId ID của thiết bị cần đăng xuất
     * @return true nếu đăng xuất thành công, false nếu không tìm thấy phiên
     */
    public boolean logoutDevice(String deviceId) {
        Optional<DeviceSession> sessionOpt = deviceSessionRepository.findByDeviceIdAndActiveTrue(deviceId);
        if (sessionOpt.isPresent()) {
            DeviceSession session = sessionOpt.get();
            session.setActive(false);
            session.setLogoutTime(Instant.now());
            deviceSessionRepository.save(session);

            // Tìm và blacklist refresh token liên kết với thiết bị này
            Optional<RefreshToken> deviceRefreshToken = refreshTokenRepository.findByDeviceId(deviceId);
            deviceRefreshToken.ifPresent(token -> {
                tokenBlacklistService.blacklistToken(token.getToken());
                refreshTokenRepository.delete(token);
            });

            return true;
        }
        return false;
    }

    /**
     * Đăng xuất tất cả thiết bị của người dùng
     * 
     * @param user người dùng cần đăng xuất
     */
    public void logoutAllDevices(User user) {
        List<DeviceSession> activeSessions = deviceSessionRepository.findByUserAndActiveTrue(user);
        for (DeviceSession session : activeSessions) {
            session.setActive(false);
            session.setLogoutTime(Instant.now());
        }
        deviceSessionRepository.saveAll(activeSessions);

        // Blacklist tất cả refresh token của user
        List<RefreshToken> userTokens = refreshTokenRepository.findByUser(user);
        for (RefreshToken token : userTokens) {
            tokenBlacklistService.blacklistToken(token.getToken());
            refreshTokenRepository.delete(token);
        }
    }

    /**
     * Lấy danh sách tất cả các phiên đăng nhập của người dùng
     * 
     * @param user người dùng cần lấy danh sách
     * @return danh sách phiên đăng nhập
     */
    public List<DeviceSession> getAllSessions(User user) {
        return deviceSessionRepository.findByUserOrderByLoginTimeDesc(user);
    }

    /**
     * Lấy danh sách các phiên đăng nhập đang hoạt động của người dùng
     * 
     * @param user người dùng cần lấy danh sách
     * @return danh sách phiên đăng nhập đang hoạt động
     */
    public List<DeviceSession> getActiveSessions(User user) {
        return deviceSessionRepository.findByUserAndActiveTrue(user);
    }
}