package com.chatapp.service;

import com.chatapp.dto.request.DeviceInfoDto;
import com.chatapp.dto.request.QrLoginConfirmRequest;
import com.chatapp.dto.request.QrLoginScanRequest;
import com.chatapp.dto.response.QrLoginResponse;
import com.chatapp.enums.QrLoginStatus;
import com.chatapp.enums.UserStatus;
import com.chatapp.exception.BadRequestException;
import com.chatapp.exception.ResourceNotFoundException;
import com.chatapp.model.QrLoginSession;
import com.chatapp.model.RefreshToken;
import com.chatapp.model.User;
import com.chatapp.model.UserDeviceSession;
import com.chatapp.repository.QrLoginSessionRepository;
import com.chatapp.repository.UserRepository;
import com.chatapp.security.JwtTokenProvider;
import com.google.zxing.WriterException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class QrLoginService {

    @Autowired
    private QrLoginSessionRepository qrLoginSessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QrCodeService qrCodeService;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private DeviceSessionService deviceSessionService;

    @Value("${app.base-url}")
    private String baseUrl;

    /**
     * Tạo QR code để đăng nhập
     */
    public QrLoginResponse generateQRCode(DeviceInfoDto deviceInfo) {
        try {
            // Tạo session token duy nhất
            String sessionToken = UUID.randomUUID().toString();

            // Tạo QR login session
            QrLoginSession session = new QrLoginSession();
            session.setSessionToken(sessionToken);
            session.setDeviceId(deviceInfo.getDeviceId());
            session.setDeviceInfo(deviceInfo.toString());
            session.setStatus(QrLoginStatus.PENDING);

            qrLoginSessionRepository.save(session);

            // Tạo QR code data và image
            String qrData = qrCodeService.createQRData(sessionToken, baseUrl);
            String qrCodeImage = qrCodeService.generateQRCodeImage(qrData, 300, 300);

            return new QrLoginResponse(
                    sessionToken,
                    qrCodeImage,
                    QrLoginStatus.PENDING.name(),
                    session.getExpiresAt(),
                    "QR code đã được tạo thành công");

        } catch (WriterException | IOException e) {
            log.error("Lỗi khi tạo QR code: ", e);
            throw new BadRequestException("Không thể tạo QR code");
        }
    }

    /**
     * Quét QR code và lấy thông tin session
     */
    public Map<String, Object> scanQRCode(QrLoginScanRequest request) {
        QrLoginSession session = qrLoginSessionRepository.findBySessionToken(request.getSessionToken())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy session QR"));

        // Kiểm tra session có hết hạn không
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            session.setStatus(QrLoginStatus.EXPIRED);
            qrLoginSessionRepository.save(session);
            throw new BadRequestException("QR code đã hết hạn");
        }

        // Kiểm tra trạng thái session
        if (session.getStatus() != QrLoginStatus.PENDING) {
            throw new BadRequestException("QR code không hợp lệ hoặc đã được sử dụng");
        }

        // Cập nhật trạng thái thành SCANNED
        session.setStatus(QrLoginStatus.SCANNED);
        qrLoginSessionRepository.save(session);

        Map<String, Object> response = new HashMap<>();
        response.put("sessionToken", session.getSessionToken());
        response.put("deviceInfo", session.getDeviceInfo());
        response.put("status", session.getStatus().name());
        response.put("message", "QR code đã được quét thành công. Vui lòng xác nhận đăng nhập.");

        return response;
    }

    /**
     * Xác nhận hoặc từ chối đăng nhập QR
     */
    @Transactional
    public Map<String, Object> confirmQRLogin(QrLoginConfirmRequest request, Long userId) {
        QrLoginSession session = qrLoginSessionRepository.findBySessionToken(request.getSessionToken())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy session QR"));

        // Kiểm tra session có hết hạn không
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            session.setStatus(QrLoginStatus.EXPIRED);
            qrLoginSessionRepository.save(session);
            throw new BadRequestException("QR code đã hết hạn");
        }

        // Kiểm tra trạng thái session
        if (session.getStatus() != QrLoginStatus.SCANNED) {
            throw new BadRequestException("QR code chưa được quét hoặc không hợp lệ");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user"));

        Map<String, Object> response = new HashMap<>();

        if ("confirm".equals(request.getAction())) {
            // Xác nhận đăng nhập
            session.setStatus(QrLoginStatus.CONFIRMED);
            session.setUser(user);
            session.setConfirmedAt(LocalDateTime.now());
            qrLoginSessionRepository.save(session);

            response.put("status", "confirmed");
            response.put("message", "Đăng nhập đã được xác nhận thành công");
        } else if ("reject".equals(request.getAction())) {
            // Từ chối đăng nhập
            session.setStatus(QrLoginStatus.REJECTED);
            qrLoginSessionRepository.save(session);

            response.put("status", "rejected");
            response.put("message", "Đăng nhập đã bị từ chối");
        } else {
            throw new BadRequestException("Action không hợp lệ. Chỉ chấp nhận 'confirm' hoặc 'reject'");
        }

        response.put("sessionToken", session.getSessionToken());
        return response;
    }

    /**
     * Kiểm tra trạng thái QR login và tạo JWT token nếu đã confirmed
     */
    public Map<String, Object> checkQRLoginStatus(String sessionToken, DeviceInfoDto deviceInfo) {
        QrLoginSession session = qrLoginSessionRepository.findBySessionToken(sessionToken)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy session QR"));

        Map<String, Object> response = new HashMap<>();
        response.put("sessionToken", sessionToken);
        response.put("status", session.getStatus().name());

        switch (session.getStatus()) {
            case PENDING:
                response.put("message", "Đang chờ quét QR code");
                break;
            case SCANNED:
                response.put("message", "QR code đã được quét, đang chờ xác nhận");
                break;
            case CONFIRMED:
                // Tạo JWT token cho user
                if (session.getUser() != null) {
                    User user = session.getUser();

                    // Tạo device session mới
                    UserDeviceSession deviceSession = deviceSessionService.createDeviceSession(user,
                            deviceInfo);

                    // Generate tokens với sessionId
                    String accessToken = tokenProvider.generateToken(user.getPhone(),
                            deviceSession.getId());
                    RefreshToken refreshToken = refreshTokenService
                            .createRefreshToken(user.getUserId());

                    // Tính toán thời gian hết hạn
                    LocalDateTime accessExpiry = LocalDateTime.now().plusHours(24); // 24 giờ
                    LocalDateTime refreshExpiry = refreshToken.getExpiryDate().atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime();

                    // Tạo token record
                    deviceSessionService.createTokenForSession(deviceSession, accessToken,
                            refreshToken.getToken(),
                            accessExpiry, refreshExpiry);

                    // Cập nhật trạng thái user
                    user.setLastLogin(LocalDateTime.now());
                    user.setStatus(UserStatus.ONLINE);
                    userRepository.save(user);

                    // Đánh dấu session đã được sử dụng
                    session.setStatus(QrLoginStatus.USED);
                    qrLoginSessionRepository.save(session);

                    // Tạo response giống AuthResponseDto
                    Map<String, Object> loginData = new HashMap<>();
                    loginData.put("accessToken", accessToken);
                    loginData.put("refreshToken", refreshToken.getToken());
                    loginData.put("user", convertUserToDto(user));

                    response.put("message", "Đăng nhập thành công");
                    response.put("loginData", loginData);
                } else {
                    throw new BadRequestException("Dữ liệu user không hợp lệ");
                }
                break;
            case REJECTED:
                response.put("message", "Đăng nhập đã bị từ chối");
                break;
            case EXPIRED:
                response.put("message", "QR code đã hết hạn");
                break;
            case USED:
                response.put("message", "QR code đã được sử dụng");
                break;
        }

        return response;
    }

    /**
     * Chuyển đổi User entity thành DTO
     */
    private Map<String, Object> convertUserToDto(User user) {
        Map<String, Object> userDto = new HashMap<>();
        userDto.put("userId", user.getUserId());
        userDto.put("displayName", user.getDisplayName());
        userDto.put("phone", user.getPhone());
        userDto.put("email", user.getEmail());
        userDto.put("gender", user.getGender());
        userDto.put("dateOfBirth", user.getDateOfBirth());
        userDto.put("avatarUrl", user.getAvatarUrl());
        userDto.put("lastLogin", user.getLastLogin());
        userDto.put("status", user.getStatus());
        userDto.put("createdAt", user.getCreatedAt());
        userDto.put("updatedAt", user.getUpdatedAt());
        return userDto;
    }

    /**
     * Dọn dẹp các session hết hạn (chạy mỗi giờ)
     */
    @Scheduled(fixedRate = 3600000) // 1 giờ
    @Async
    @Transactional
    public void cleanupExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();

        // Đánh dấu các session hết hạn
        qrLoginSessionRepository.markExpiredSessions(QrLoginStatus.EXPIRED, now, QrLoginStatus.PENDING);
        qrLoginSessionRepository.markExpiredSessions(QrLoginStatus.EXPIRED, now, QrLoginStatus.SCANNED);

        // Xóa các session cũ hơn 24 giờ
        LocalDateTime cutoffTime = now.minusHours(24);
        qrLoginSessionRepository.deleteExpiredSessions(cutoffTime);

        log.info("Đã dọn dẹp các QR login session hết hạn");
    }
}