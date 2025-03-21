package com.chatapp.service;

import com.chatapp.dto.UserDto;
import com.chatapp.exception.ResourceNotFoundException;
import com.chatapp.model.User;
import com.chatapp.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

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

        user.setDisplayName(userDto.getDisplayName());
        if (userDto.getAvatarUrl() != null) {
            user.setAvatarUrl(userDto.getAvatarUrl());
        }

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
        return dto;
    }
}