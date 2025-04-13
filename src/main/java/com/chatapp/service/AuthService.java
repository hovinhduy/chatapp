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
import com.chatapp.exception.ResourceAlreadyExistsException;
import com.chatapp.exception.ResourceNotFoundException;
import com.chatapp.exception.TokenRefreshException;
import com.chatapp.exception.UnauthorizedException;
import com.chatapp.model.DeviceSession;
import com.chatapp.model.RefreshToken;
import com.chatapp.model.User;
import com.chatapp.repository.OtpRepository;
import com.chatapp.repository.UserRepository;
import com.chatapp.security.JwtTokenProvider;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;

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
     * @return AuthResponseDto Đối tượng chứa token và thông tin người dùng
     * @throws ResourceAlreadyExistsException Nếu số điện thoại đã được đăng ký
     */
    public AuthResponseDto register(RegisterRequest registerRequest) {
        // Check if phone already exists
        if (userRepository.existsByPhone(registerRequest.getPhone())) {
            throw new ResourceAlreadyExistsException("Số điện thoại đã được đăng ký");
        }
        // // kiểm tra email tồn tại chưa
        // if (userRepository.existsByEmail(registerDto.getEmail())) {
        // throw new ResourceAlreadyExistsException("Email đã được đăng ký");
        // }
        // // kiểm tra email đã verify chưa
        // if (!otpRepository.existsByEmailAndStatus(registerDto.getEmail(),
        // OtpStatus.VERIFIED)) {
        // throw new ResourceAlreadyExistsException("Email chưa được xác thực");
        // }

        // Create user
        UserDto userDto = new UserDto();
        userDto.setDisplayName(registerRequest.getDisplayName());
        userDto.setPhone(registerRequest.getPhone());
        // userDto.setEmail(registerDto.getEmail());
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

        // Generate token
        String token = tokenProvider.generateToken(savedUser.getPhone());

        // Tạo refresh token mà không liên kết với thiết bị cụ thể
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(savedUser.getUserId());

        AuthResponseDto authResponse = new AuthResponseDto(token, savedUser);
        authResponse.setRefreshToken(refreshToken.getToken());

        return authResponse;
    }

    /**
     * Đăng nhập người dùng
     * 
     * @param loginDto Đối tượng chứa thông tin đăng nhập (số điện thoại và mật
     *                 khẩu)
     * @param request  HTTP request chứa thông tin về thiết bị đăng nhập
     * @return AuthResponseDto Đối tượng chứa token và thông tin người dùng
     * @throws UnauthorizedException Nếu thông tin đăng nhập không hợp lệ
     */
    public AuthResponseDto login(LoginDto loginDto, HttpServletRequest request) {
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

                String token = tokenProvider.generateToken(loginDto.getPhone());
                UserDto userDto = userService.getUserByPhone(loginDto.getPhone());
                userDto.setLastLogin(LocalDateTime.now());
                userDto.setStatus(UserStatus.ONLINE);
                userService.updateUser(userDto.getUserId(), userDto);

                // Lấy thông tin thiết bị
                User user = userRepository.findByPhone(loginDto.getPhone())
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
                DeviceSession deviceSession = deviceSessionService.createSession(user, request);

                // Tạo refresh token cho thiết bị
                RefreshToken refreshToken = refreshTokenService.createRefreshToken(
                        userDto.getUserId(),
                        deviceSession.getDeviceId(),
                        deviceSession.getDeviceType());

                AuthResponseDto authResponse = new AuthResponseDto(token, userDto);
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
                .map(refreshToken -> {
                    User user = refreshToken.getUser();
                    String token = tokenProvider.generateToken(user.getPhone());

                    // Sử dụng lại refresh token hiện tại thay vì tạo mới
                    return new TokenRefreshResponse(token, requestRefreshToken);
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
            // Thêm access token vào blacklist
            tokenBlacklistService.blacklistToken(request.getAccessToken());

            // Lấy thông tin từ refresh token
            String refreshToken = request.getRefreshToken();
            RefreshToken token = refreshTokenService.findByToken(refreshToken)
                    .orElse(null);

            if (token != null) {
                // Lấy thông tin thiết bị từ token
                String deviceId = token.getDeviceId();

                // Xóa refresh token
                refreshTokenService.deleteByToken(refreshToken);

                // Đăng xuất thiết bị nếu có deviceId trong token
                if (deviceId != null && !deviceId.isEmpty()) {
                    deviceSessionService.logoutDevice(deviceId);
                }
                // Nếu không có deviceId trong token nhưng có trong request, sử dụng deviceId từ
                // request
                else if (request.getDeviceId() != null && !request.getDeviceId().isEmpty()) {
                    deviceSessionService.logoutDevice(request.getDeviceId());
                }
            }
            // Nếu không tìm thấy token nhưng có deviceId trong request
            else if (request.getDeviceId() != null && !request.getDeviceId().isEmpty()) {
                deviceSessionService.logoutDevice(request.getDeviceId());
            }

            // Cập nhật trạng thái người dùng
            String username = tokenProvider.getUsernameFromToken(request.getAccessToken());
            UserDto userDto = userService.getUserByPhone(username);

            // Kiểm tra xem người dùng còn phiên đăng nhập nào không
            User user = userRepository.findByPhone(username)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
            List<DeviceSession> activeSessions = deviceSessionService.getActiveSessions(user);

            // Nếu không còn phiên đăng nhập nào, đặt trạng thái người dùng là offline
            if (activeSessions.isEmpty()) {
                userDto.setStatus(UserStatus.OFFLINE);
                userService.updateUser(userDto.getUserId(), userDto);
            }

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

    /**
     * Lấy danh sách thiết bị đang đăng nhập của người dùng
     * 
     * @param userId ID của người dùng
     * @return danh sách thiết bị đang đăng nhập
     */
    public List<DeviceSession> getActiveDevices(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        return deviceSessionService.getActiveSessions(user);
    }

    /**
     * Lấy lịch sử đăng nhập của người dùng
     * 
     * @param userId ID của người dùng
     * @return lịch sử đăng nhập
     */
    public List<DeviceSession> getLoginHistory(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        return deviceSessionService.getAllSessions(user);
    }

    /**
     * Đăng xuất khỏi thiết bị cụ thể
     * 
     * @param userId   ID của người dùng
     * @param deviceId ID của thiết bị cần đăng xuất
     * @return true nếu đăng xuất thành công, false nếu không tìm thấy thiết bị
     */
    public boolean logoutDevice(Long userId, String deviceId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        // Đảm bảo thiết bị thuộc về người dùng này
        List<DeviceSession> activeSessions = deviceSessionService.getActiveSessions(user);
        boolean deviceBelongsToUser = activeSessions.stream()
                .anyMatch(session -> session.getDeviceId().equals(deviceId));

        if (!deviceBelongsToUser) {
            throw new UnauthorizedException("Thiết bị không thuộc về người dùng này");
        }

        return deviceSessionService.logoutDevice(deviceId);
    }

    /**
     * Đăng xuất tất cả thiết bị của người dùng
     * 
     * @param userId ID của người dùng
     */
    public void logoutAllDevices(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        deviceSessionService.logoutAllDevices(user);
    }

    /**
     * Lấy thông tin người dùng từ token
     * 
     * @param token JWT token
     * @return số điện thoại của người dùng
     */
    public String getUsernameFromToken(String token) {
        return tokenProvider.getUsernameFromToken(token);
    }
}