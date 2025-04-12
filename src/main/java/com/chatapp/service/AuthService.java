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
import com.chatapp.model.RefreshToken;
import com.chatapp.model.User;
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
            TokenBlacklistService tokenBlacklistService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.userService = userService;
        this.otpRepository = otpRepository;
        this.refreshTokenService = refreshTokenService;
        this.tokenBlacklistService = tokenBlacklistService;
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
            throw new ResourceAlreadyExistsException("Phone number already registered");
        }
        // // kiểm tra email tồn tại chưa
        // if (userRepository.existsByEmail(registerDto.getEmail())) {
        // throw new ResourceAlreadyExistsException("Email already registered");
        // }
        // // kiểm tra email đã verify chưa
        // if (!otpRepository.existsByEmailAndStatus(registerDto.getEmail(),
        // OtpStatus.VERIFIED)) {
        // throw new ResourceAlreadyExistsException("Email not verified");
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

        // Tạo refresh token
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
     * @return AuthResponseDto Đối tượng chứa token và thông tin người dùng
     * @throws UnauthorizedException Nếu thông tin đăng nhập không hợp lệ
     */
    public AuthResponseDto login(LoginDto loginDto) {
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

            // Tạo refresh token
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(userDto.getUserId());

            AuthResponseDto authResponse = new AuthResponseDto(token, userDto);
            authResponse.setRefreshToken(refreshToken.getToken());

            return authResponse;
        } catch (Exception e) {
            throw new UnauthorizedException("Invalid phone number or password");
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
                    String token = tokenProvider.generateToken(user.getPhone());
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
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        return true;
    }
}