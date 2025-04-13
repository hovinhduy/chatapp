package com.chatapp.service;

import com.chatapp.model.BlacklistedToken;
import com.chatapp.repository.BlacklistedTokenRepository;
import com.chatapp.security.JwtTokenProvider;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;

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
     * Thêm token vào blacklist
     * 
     * @param token Token cần thêm vào blacklist
     */
    public void blacklistToken(String token) {
        try {
            if (!blacklistedTokenRepository.existsByToken(token)) {
                BlacklistedToken blacklistedToken = new BlacklistedToken();
                blacklistedToken.setToken(token);

                // Thử lấy thời gian hết hạn từ JWT token
                try {
                    Date expiryDate = jwtTokenProvider.getExpirationDateFromToken(token);
                    blacklistedToken.setExpiryDate(expiryDate.toInstant());
                } catch (Exception e) {
                    // Nếu không phải là JWT token, có thể là refresh token
                    // Đặt thời gian hết hạn là 30 ngày từ hiện tại
                    blacklistedToken.setExpiryDate(Instant.now().plusSeconds(30 * 24 * 60 * 60));
                }

                blacklistedTokenRepository.save(blacklistedToken);
            }
        } catch (Exception e) {
            // Token không hợp lệ hoặc đã hết hạn, không cần thêm vào blacklist
        }
    }

    /**
     * Xóa refresh token khỏi database
     * 
     * @param token Refresh token cần xóa
     */
    public void deleteRefreshToken(String token) {
        try {
            // Thêm token vào blacklist trước khi xóa
            blacklistToken(token);
            // Sau đó xóa khỏi database
            refreshTokenService.deleteByToken(token);
        } catch (Exception e) {
            // Không tìm thấy token hoặc lỗi xảy ra
        }
    }

    /**
     * Lên lịch xóa các token đã hết hạn khỏi blacklist
     * Chạy hàng ngày vào lúc 3 giờ sáng
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        Instant now = Instant.now();
        blacklistedTokenRepository.deleteAllExpiredTokens(now);
    }
}