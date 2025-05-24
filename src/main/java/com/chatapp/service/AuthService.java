package com.chatapp.service;

import com.chatapp.dto.request.LogoutRequest;
import com.chatapp.dto.request.RefreshTokenRequest;
import com.chatapp.dto.response.AuthResponseDto;
import com.chatapp.dto.response.TokenRefreshResponse;
import com.chatapp.enums.Gender;
import com.chatapp.enums.UserStatus;
import com.chatapp.dto.request.ForgotPasswordRequest;
import com.chatapp.dto.request.LoginDto;
import com.chatapp.dto.request.RegisterRequest;
import com.chatapp.dto.request.UserDto;
import com.chatapp.dto.request.DeviceInfoDto;
import com.chatapp.exception.ResourceAlreadyExistsException;
import com.chatapp.exception.ResourceNotFoundException;
import com.chatapp.exception.TokenRefreshException;
import com.chatapp.exception.UnauthorizedException;
import com.chatapp.model.RefreshToken;
import com.chatapp.model.User;
import com.chatapp.model.UserDeviceSession;
import com.chatapp.model.Token;
import com.chatapp.repository.OtpRepository;
import com.chatapp.repository.UserRepository;
import com.chatapp.security.JwtTokenProvider;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Service xử lý các chức năng xác thực và đăng ký người dùng
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserService userService;
    private final OtpRepository otpRepository;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final DeviceSessionService deviceSessionService;

    /**
     * Constructor để dependency injection
     * 
     * @param userRepository        Repository xử lý thao tác với database của User
     * @param passwordEncoder       Bean mã hóa mật khẩu
     * @param authenticationManager Manager xử lý xác thực
     * @param tokenProvider         Provider tạo và xác thực JWT token
     * @param userService           Service xử lý các thao tác liên quan đến User
     * @param deviceSessionService  Service xử lý device session
     */
    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager, JwtTokenProvider tokenProvider,
            UserService userService, OtpRepository otpRepository, RefreshTokenService refreshTokenService,
            TokenBlacklistService tokenBlacklistService, DeviceSessionService deviceSessionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.userService = userService;
        this.otpRepository = otpRepository;
        this.refreshTokenService = refreshTokenService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.deviceSessionService = deviceSessionService;
    }

    /**
     * Đăng ký người dùng mới
     * 
     * @param registerRequest Đối tượng chứa thông tin đăng ký
     * @param deviceInfo      Thông tin thiết bị
     * @return AuthResponseDto Đối tượng chứa token và thông tin người dùng
     * @throws ResourceAlreadyExistsException Nếu số điện thoại đã được đăng ký
     */
    public AuthResponseDto register(RegisterRequest registerRequest, DeviceInfoDto deviceInfo) {
        // Check if phone already exists
        if (userRepository.existsByPhone(registerRequest.getPhone())) {
            throw new ResourceAlreadyExistsException("Số điện thoại đã được đăng ký");
        }

        // Create user
        UserDto userDto = new UserDto();
        userDto.setDisplayName(registerRequest.getDisplayName());
        userDto.setPhone(registerRequest.getPhone());
        userDto.setGender(Gender.valueOf(registerRequest.getGender()));
        if (registerRequest.getDateOfBirth() != null) {
            if (registerRequest.getDateOfBirth().isAfter(LocalDate.now().minusYears(14))) {
                throw new IllegalArgumentException("Người dùng phải lớn hơn 14 tuổi");
            }
            userDto.setDateOfBirth(registerRequest.getDateOfBirth());
        }
        userDto.setPassword(registerRequest.getPassword());
        userDto.setCreatedAt(LocalDateTime.now());
        userDto.setStatus(UserStatus.ONLINE);

        UserDto savedUser = userService.createUser(userDto);
        User user = userRepository.findById(savedUser.getUserId()).orElseThrow();

        // Tạo device session mới
        UserDeviceSession deviceSession = deviceSessionService.createDeviceSession(user, deviceInfo);

        // Generate tokens với sessionId
        String accessToken = tokenProvider.generateToken(registerRequest.getPhone(), deviceSession.getId());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(savedUser.getUserId());

        // Tính toán thời gian hết hạn
        LocalDateTime accessExpiry = LocalDateTime.now().plusHours(24); // 24 giờ
        LocalDateTime refreshExpiry = refreshToken.getExpiryDate().atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime();

        // Tạo token record
        deviceSessionService.createTokenForSession(deviceSession, accessToken, refreshToken.getToken(),
                accessExpiry, refreshExpiry);

        AuthResponseDto authResponse = new AuthResponseDto(accessToken, savedUser);
        authResponse.setRefreshToken(refreshToken.getToken());

        return authResponse;
    }

    /**
     * Đăng nhập người dùng
     * 
     * @param loginDto   Đối tượng chứa thông tin đăng nhập (số điện thoại và mật
     *                   khẩu)
     * @param deviceInfo Thông tin thiết bị
     * @return AuthResponseDto Đối tượng chứa token và thông tin người dùng
     * @throws UnauthorizedException Nếu thông tin đăng nhập không hợp lệ
     */
    public AuthResponseDto login(LoginDto loginDto, DeviceInfoDto deviceInfo) {
        try {
            // Kiểm tra xem số điện thoại có tồn tại không
            if (!userRepository.existsByPhone(loginDto.getPhone())) {
                throw new UnauthorizedException("Số điện thoại không tồn tại");
            }

            try {
                Authentication authentication = authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                loginDto.getPhone(),
                                loginDto.getPassword()));

                SecurityContextHolder.getContext().setAuthentication(authentication);

                UserDto userDto = userService.getUserByPhone(loginDto.getPhone());
                User user = userRepository.findById(userDto.getUserId()).orElseThrow();

                // Tạo device session mới
                UserDeviceSession deviceSession = deviceSessionService.createDeviceSession(user, deviceInfo);

                // Generate tokens với sessionId
                String accessToken = tokenProvider.generateToken(loginDto.getPhone(), deviceSession.getId());
                RefreshToken refreshToken = refreshTokenService.createRefreshToken(userDto.getUserId());

                // Tính toán thời gian hết hạn
                LocalDateTime accessExpiry = LocalDateTime.now().plusHours(24); // 24 giờ
                LocalDateTime refreshExpiry = refreshToken.getExpiryDate().atZone(java.time.ZoneId.systemDefault())
                        .toLocalDateTime();

                // Tạo token record
                deviceSessionService.createTokenForSession(deviceSession, accessToken, refreshToken.getToken(),
                        accessExpiry, refreshExpiry);

                // Cập nhật user info
                userDto.setLastLogin(LocalDateTime.now());
                userDto.setStatus(UserStatus.ONLINE);
                userService.updateUser(userDto.getUserId(), userDto);

                AuthResponseDto authResponse = new AuthResponseDto(accessToken, userDto);
                authResponse.setRefreshToken(refreshToken.getToken());

                return authResponse;
            } catch (Exception e) {
                // Nếu số điện thoại tồn tại nhưng xác thực thất bại, thì lỗi là do sai mật khẩu
                throw new UnauthorizedException("Mật khẩu không chính xác");
            }
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException("Có lỗi trong quá trình đăng nhập");
        }
    }

    /**
     * Refreshes a JWT token
     * 
     * @param request Đối tượng chứa refresh token
     * @return TokenRefreshResponse Đối tượng chứa access token và refresh token mới
     */
    public TokenRefreshResponse refreshToken(RefreshTokenRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    // Tìm device session từ refresh token
                    String sessionId = null;
                    try {
                        // Kiểm tra nếu refresh token có sessionId
                        sessionId = tokenProvider.getSessionIdFromToken(requestRefreshToken);
                    } catch (Exception e) {
                        // Nếu không có sessionId trong token, tạo token cũ (tương thích ngược)
                    }

                    String newAccessToken;
                    if (sessionId != null) {
                        newAccessToken = tokenProvider.generateToken(user.getPhone(), sessionId);
                    } else {
                        newAccessToken = tokenProvider.generateToken(user.getPhone());
                    }

                    return new TokenRefreshResponse(newAccessToken, requestRefreshToken);
                })
                .orElseThrow(() -> new TokenRefreshException(requestRefreshToken,
                        "Refresh token không tồn tại trong hệ thống"));
    }

    /**
     * Đăng xuất người dùng và vô hiệu hóa token
     * 
     * @param request Đối tượng chứa access token và refresh token
     * @return true nếu đăng xuất thành công
     */
    public boolean logout(LogoutRequest request) {
        try {
            // Lấy sessionId từ access token
            String sessionId = null;
            try {
                sessionId = tokenProvider.getSessionIdFromToken(request.getAccessToken());
            } catch (Exception e) {
                // Token không có sessionId, xử lý logout cũ
            }

            // Đăng xuất device session nếu có sessionId
            if (sessionId != null) {
                deviceSessionService.logoutDeviceSession(sessionId, "manual_logout");
            }

            // Thêm access token vào blacklist
            tokenBlacklistService.blacklistToken(request.getAccessToken());

            // Xóa refresh token khỏi database
            tokenBlacklistService.deleteRefreshToken(request.getRefreshToken());

            // Cập nhật trạng thái người dùng
            String username = tokenProvider.getUsernameFromToken(request.getAccessToken());
            UserDto userDto = userService.getUserByPhone(username);
            userDto.setStatus(UserStatus.OFFLINE);
            userService.updateUser(userDto.getUserId(), userDto);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Quên mật khẩu
     * 
     * @param request
     * @return
     */
    public boolean forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByPhone(request.getPhone())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        return true;
    }
}