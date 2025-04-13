package com.chatapp.service;

import com.chatapp.exception.TokenRefreshException;
import com.chatapp.model.DeviceSession;
import com.chatapp.model.RefreshToken;
import com.chatapp.model.User;
import com.chatapp.repository.RefreshTokenRepository;
import com.chatapp.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {
    @Value("${jwt.refresh-expiration}")
    private Long refreshTokenDurationMs;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    /**
     * Tạo refresh token mới cho người dùng
     * 
     * @param userId ID của người dùng
     * @return RefreshToken mới được tạo
     */
    public RefreshToken createRefreshToken(Long userId) {
        return createRefreshToken(userId, null, null);
    }

    /**
     * Tạo refresh token mới cho người dùng và thiết bị cụ thể
     * 
     * @param userId     ID của người dùng
     * @param deviceId   ID của thiết bị
     * @param deviceType Loại thiết bị
     * @return RefreshToken mới được tạo
     */
    public RefreshToken createRefreshToken(Long userId, String deviceId, DeviceSession.DeviceType deviceType) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new RuntimeException("Không tìm thấy người dùng với id: " + userId);
        }

        User user = userOpt.get();

        // Nếu thiết bị đã có refresh token, xóa token cũ
        if (deviceType != null) {
            refreshTokenRepository.findByUserAndDeviceType(user, deviceType)
                    .ifPresent(refreshTokenRepository::delete);
        } else if (deviceId != null) {
            refreshTokenRepository.findByUserAndDeviceId(user, deviceId)
                    .ifPresent(refreshTokenRepository::delete);
        }

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenDurationMs));
        refreshToken.setToken(UUID.randomUUID().toString());

        // Lưu thông tin thiết bị
        if (deviceId != null) {
            refreshToken.setDeviceId(deviceId);
        }

        if (deviceType != null) {
            refreshToken.setDeviceType(deviceType);
        }

        refreshToken = refreshTokenRepository.save(refreshToken);
        return refreshToken;
    }

    /**
     * Kiểm tra và xác nhận refresh token còn hạn sử dụng
     * 
     * @param token Token cần kiểm tra
     * @return RefreshToken đã xác nhận
     * @throws TokenRefreshException nếu token hết hạn
     */
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token);
            throw new TokenRefreshException(token.getToken(), "Refresh token đã hết hạn. Vui lòng đăng nhập lại");
        }

        return token;
    }

    /**
     * Xóa tất cả refresh token của người dùng
     * 
     * @param userId ID của người dùng
     * @return số lượng token đã xóa
     */
    @Transactional
    public int deleteByUserId(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        return userOpt.map(refreshTokenRepository::deleteByUser).orElse(0);
    }

    /**
     * Xóa refresh token theo token
     * 
     * @param token Token cần xóa
     */
    @Transactional
    public void deleteByToken(String token) {
        refreshTokenRepository.findByToken(token)
                .ifPresent(refreshTokenRepository::delete);
    }

    /**
     * Xóa refresh token theo deviceId
     * 
     * @param deviceId ID của thiết bị
     * @return số lượng token đã xóa
     */
    @Transactional
    public int deleteByDeviceId(String deviceId) {
        return refreshTokenRepository.deleteByDeviceId(deviceId);
    }

    /**
     * Xóa refresh token theo user và deviceType
     * 
     * @param userId     ID của người dùng
     * @param deviceType Loại thiết bị
     * @return số lượng token đã xóa
     */
    @Transactional
    public int deleteByUserAndDeviceType(Long userId, DeviceSession.DeviceType deviceType) {
        Optional<User> userOpt = userRepository.findById(userId);
        return userOpt.map(user -> refreshTokenRepository.deleteByUserAndDeviceType(user, deviceType)).orElse(0);
    }

    /**
     * Lấy danh sách refresh token của người dùng
     * 
     * @param userId ID của người dùng
     * @return Danh sách RefreshToken
     */
    public List<RefreshToken> getTokensByUserId(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        return userOpt.map(refreshTokenRepository::findByUser).orElse(List.of());
    }
}