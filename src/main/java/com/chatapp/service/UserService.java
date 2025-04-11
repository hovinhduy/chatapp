package com.chatapp.service;

import com.chatapp.dto.request.UserDto;
import com.chatapp.enums.Gender;
import com.chatapp.enums.UserStatus;
import com.chatapp.exception.ResourceNotFoundException;
import com.chatapp.model.User;
import com.chatapp.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.chatapp.dto.request.RegisterRequest;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service class để xử lý các thao tác liên quan đến người dùng
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Constructor để dependency injection
     * 
     * @param userRepository  Repository xử lý thao tác với database của User
     * @param passwordEncoder Bean mã hóa mật khẩu
     */
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Tạo mới một người dùng
     * 
     * @param userDto Đối tượng chứa thông tin người dùng cần tạo
     * @return UserDto Thông tin người dùng đã được tạo
     */
    public UserDto createUser(UserDto userDto) {
        User user = new User();
        user.setDisplayName(userDto.getDisplayName());
        user.setPhone(userDto.getPhone());
        user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        user.setAvatarUrl(userDto.getAvatarUrl());
        user.setEmail(userDto.getEmail());
        user.setGender(userDto.getGender());
        user.setDateOfBirth(userDto.getDateOfBirth());
        user.setCreatedAt(LocalDateTime.now());
        user.setStatus(UserStatus.ONLINE);


        User savedUser = userRepository.save(user);
        return mapToDto(savedUser);
    }

    /**
     * Lấy thông tin người dùng theo ID
     * 
     * @param id ID của người dùng cần tìm
     * @return UserDto Thông tin người dùng
     * @throws ResourceNotFoundException Nếu không tìm thấy người dùng
     */
    public UserDto getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return mapToDto(user);
    }

    /**
     * Lấy thông tin người dùng theo số điện thoại
     * 
     * @param phone Số điện thoại của người dùng cần tìm
     * @return UserDto Thông tin người dùng
     * @throws ResourceNotFoundException Nếu không tìm thấy người dùng
     */
    public UserDto getUserByPhone(String phone) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with phone: " + phone));
        return mapToDto(user);
    }

    /**
     * Lấy danh sách tất cả người dùng
     * 
     * @return List<UserDto> Danh sách người dùng
     */
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Cập nhật thông tin người dùng
     * 
     * @param id      ID của người dùng cần cập nhật
     * @param userDto Đối tượng chứa thông tin cần cập nhật
     * @return UserDto Thông tin người dùng sau khi cập nhật
     * @throws ResourceNotFoundException Nếu không tìm thấy người dùng
     */
    public UserDto updateUser(Long id, UserDto userDto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        // Cập nhật thông tin cơ bản
        if (userDto.getDisplayName() != null) {
            if (userDto.getDisplayName().length() < 2 || userDto.getDisplayName().length() > 40) {
                throw new IllegalArgumentException("Tên hiển thị phải có từ 2 đến 40 ký tự");
            }
            user.setDisplayName(userDto.getDisplayName());
        }
        if (userDto.getAvatarUrl() != null) {
            user.setAvatarUrl(userDto.getAvatarUrl());
        }
        if (userDto.getEmail() != null) {
            user.setEmail(userDto.getEmail());
        }
        if (userDto.getGender() != null) {
            user.setGender(userDto.getGender());
        }
        if (userDto.getDateOfBirth() != null) {
            if (userDto.getDateOfBirth().isBefore(LocalDate.now().minusYears(14))) {
                throw new IllegalArgumentException("Người dùng phải lớn hơn 14 tuổi");
            }
            user.setDateOfBirth(userDto.getDateOfBirth());
        }

        // Cập nhật thời gian chỉnh sửa
        user.setUpdatedAt(LocalDateTime.now());

        User updatedUser = userRepository.save(user);
        return mapToDto(updatedUser);
    }

    /**
     * Xóa người dùng
     * 
     * @param id ID của người dùng cần xóa
     * @throws ResourceNotFoundException Nếu không tìm thấy người dùng
     */
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        userRepository.delete(user);
    }

    /**
     * Thay đổi mật khẩu của người dùng
     * 
     * @param id          ID của người dùng
     * @param oldPassword Mật khẩu cũ
     * @param newPassword Mật khẩu mới
     * @return boolean True nếu thay đổi thành công, False nếu mật khẩu cũ không
     *         đúng
     * @throws ResourceNotFoundException Nếu không tìm thấy người dùng
     */
    public boolean changePassword(Long id, String oldPassword, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        if (passwordEncoder.matches(oldPassword, user.getPassword())) {
            if (newPassword.length() < 6 || newPassword.length() > 20) {
                throw new IllegalArgumentException("Mật khẩu phải có từ 6 đến 20 ký tự");
            }
            if (!newPassword.matches("^(?=.*[A-Za-z])(?=.*[^A-Za-z0-9]).+$")) {
                throw new IllegalArgumentException("Mật khẩu phải có chữ và kí tự đặt biệt");
            }
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            return true;
        }
        return false;
    }

    /**
     * Chuyển đổi đối tượng User thành UserDto
     * 
     * @param user Đối tượng User cần chuyển đổi
     * @return UserDto Đối tượng DTO tương ứng
     */
    public UserDto mapToDto(User user) {
        UserDto dto = new UserDto();
        dto.setUserId(user.getUserId());
        dto.setPhone(user.getPhone());
        dto.setDisplayName(user.getDisplayName());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setEmail(user.getEmail());
        dto.setGender(user.getGender());
        dto.setDateOfBirth(user.getDateOfBirth());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setUpdatedAt(user.getUpdatedAt());
        return dto;
    }

    /**
     * Phương thức đăng ký người dùng mới sau khi xác thực Firebase
     *
     * @param registerRequest thông tin đăng ký người dùng
     * @return ResponseEntity chứa thông tin phản hồi
     */
    public ResponseEntity<?> registerUser(RegisterRequest registerRequest) {
        try {
            // Kiểm tra số điện thoại đã tồn tại chưa
            if (userRepository.existsByPhone(registerRequest.getPhone())) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Số điện thoại đã được đăng ký");
                return ResponseEntity.badRequest().body(response);
            }

            // Tạo người dùng mới
            User newUser = new User();
            newUser.setPhone(registerRequest.getPhone());
            newUser.setDisplayName(registerRequest.getDisplayName());
            newUser.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
            newUser.setGender(Gender.valueOf(registerRequest.getGender()));
            if (registerRequest.getDateOfBirth() != null) {
                if (registerRequest.getDateOfBirth().isAfter(LocalDate.now().minusYears(14))) {
                    throw new IllegalArgumentException("Người dùng phải lớn hơn 14 tuổi");
                }
                newUser.setDateOfBirth(registerRequest.getDateOfBirth());
            }
            newUser.setCreatedAt(LocalDateTime.now());
            newUser.setUpdatedAt(LocalDateTime.now());
            newUser.setStatus(UserStatus.OFFLINE);

            // Lưu người dùng vào database
            User savedUser = userRepository.save(newUser);

            // Tạo phản hồi
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Đăng ký thành công");
            response.put("userId", savedUser.getUserId());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Lỗi khi đăng ký: " + e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }
}