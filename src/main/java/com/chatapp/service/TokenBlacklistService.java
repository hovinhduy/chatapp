package com.chatapp.service;

import com.chatapp.model.BlacklistedToken;
import com.chatapp.repository.BlacklistedTokenRepository;
import com.chatapp.security.JwtTokenProvider;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class TokenBlacklistService {

    @Autowired
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RefreshTokenService refreshTokenService;

    /**
     * Kiểm tra xem token có trong blacklist hay không
     * 
     * @param token Token cần kiểm tra
     * @return true nếu token đã bị blacklist, ngược lại false
     */
    public boolean isTokenBlacklisted(String token) {
        return blacklistedTokenRepository.existsByToken(token);
    }

    /**
     * Thêm token vào blacklist với thông tin chi tiết
     * 
     * @param token           Token cần thêm vào blacklist
     * @param deviceSessionId ID của device session
     * @param userId          ID của user
     * @param reason          Lý do blacklist
     */
    public void addToBlacklist(String token, String deviceSessionId, Long userId, String reason) {
        try {
            if (!blacklistedTokenRepository.existsByToken(token)) {
                BlacklistedToken blacklistedToken = new BlacklistedToken();
                blacklistedToken.setToken(token);
                blacklistedToken.setDeviceSessionId(deviceSessionId);
                blacklistedToken.setUserId(userId);
                blacklistedToken.setReason(reason);
                blacklistedToken.setBlacklistedAt(LocalDateTime.now());

                blacklistedTokenRepository.save(blacklistedToken);
            }
        } catch (Exception e) {
            // Token không hợp lệ hoặc đã hết hạn, không cần thêm vào blacklist
        }
    }

    /**
     * Thêm access token vào blacklist (method cũ để tương thích)
     * 
     * @param token Access token cần thêm vào blacklist
     */
    public void blacklistToken(String token) {
        addToBlacklist(token, null, null, "manual_logout");
    }

    /**
     * Xóa refresh token khỏi database
     * 
     * @param token Refresh token cần xóa
     */
    public void deleteRefreshToken(String token) {
        try {
            refreshTokenService.deleteByToken(token);
            // Thêm refresh token vào blacklist
            addToBlacklist(token, null, null, "token_refresh");
        } catch (Exception e) {
            // Không tìm thấy token hoặc lỗi xảy ra
        }
    }

    /**
     * Blacklist tất cả token của một device session
     * 
     * @param deviceSessionId ID của device session
     * @param userId          ID của user
     * @param reason          Lý do blacklist
     */
    @Transactional
    public void blacklistAllTokensForSession(String deviceSessionId, Long userId, String reason) {
        // Method này sẽ được gọi từ DeviceSessionService
        // Token cụ thể sẽ được thêm vào blacklist từ bên ngoài
    }

    /**
     * Lên lịch xóa các token cũ khỏi blacklist
     * Chạy hàng ngày vào lúc 3 giờ sáng
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        // Xóa blacklisted token cũ hơn 90 ngày (tùy chọn, có thể giữ để audit)
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);
        blacklistedTokenRepository.deleteOldBlacklistedTokens(cutoffDate);
    }
}