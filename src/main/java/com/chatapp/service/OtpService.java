package com.chatapp.service;

import com.chatapp.enums.OtpStatus;
import com.chatapp.exception.OtpRateLimitException;
import com.chatapp.model.Otp;
import com.chatapp.repository.OtpRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Random;

@Service
public class OtpService {
    private static final int OTP_LENGTH = 6;
    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int OTP_RESEND_LIMIT_SECONDS = 60; // 1 phút

    @Autowired
    private OtpRepository otpRepository;

    @Autowired
    private JavaMailSender mailSender;

    public boolean canSendOtp(String email) {
        Optional<Otp> existingOtp = otpRepository.findByEmailAndStatus(email, OtpStatus.UNVERIFIED);

        if (existingOtp.isPresent()) {
            Otp otp = existingOtp.get();

            // Kiểm tra nếu đã gửi OTP trong vòng 1 phút
            if (otp.getLastSentTime() != null) {
                long secondsSinceLastSent = ChronoUnit.SECONDS.between(otp.getLastSentTime(), LocalDateTime.now());
                return secondsSinceLastSent >= OTP_RESEND_LIMIT_SECONDS;
            }
        }

        return true;
    }

    public void sendOtp(String email) {
        // Kiểm tra xem có thể gửi OTP không
        if (!canSendOtp(email)) {
            throw new OtpRateLimitException("Bạn chỉ có thể yêu cầu OTP mới sau 1 phút. Vui lòng thử lại sau.");
        }

        // Generate OTP
        String otpCode = generateOtp();

        // Kiểm tra xem đã có OTP chưa xác thực không
        Optional<Otp> existingOtp = otpRepository.findByEmailAndStatus(email, OtpStatus.UNVERIFIED);
        Otp otp;

        if (existingOtp.isPresent()) {
            // Nếu có, cập nhật OTP hiện tại
            otp = existingOtp.get();
            otp.setCode(otpCode);
            otp.setExpiryTime(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
            otp.setLastSentTime(LocalDateTime.now());
        } else {
            // Nếu không, tạo OTP mới
            otp = new Otp();
            otp.setEmail(email);
            otp.setCode(otpCode);
            otp.setExpiryTime(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
            otp.setStatus(OtpStatus.UNVERIFIED);
            otp.setLastSentTime(LocalDateTime.now());
        }

        otpRepository.save(otp);

        // Send email
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Xác thực OTP");
        message.setText("Mã OTP của bạn là: " + otpCode + "\nMã sẽ hết hạn sau " + OTP_EXPIRY_MINUTES + " phút.");
        mailSender.send(message);
    }

    public boolean verifyOtp(String email, String code) {
        Otp otp = otpRepository.findByEmailAndCodeAndStatus(email, code, OtpStatus.UNVERIFIED)
                .orElse(null);

        if (otp == null) {
            return false;
        }

        if (otp.getExpiryTime().isBefore(LocalDateTime.now())) {
            otp.setStatus(OtpStatus.EXPIRED);
            otpRepository.save(otp);
            return false;
        }

        otp.setStatus(OtpStatus.VERIFIED);
        otpRepository.save(otp);
        return true;
    }

    private String generateOtp() {
        Random random = new Random();
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < OTP_LENGTH; i++) {
            otp.append(random.nextInt(10));
        }
        return otp.toString();
    }
}