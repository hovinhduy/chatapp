package com.chatapp.service;

import com.chatapp.enums.OtpStatus;
import com.chatapp.model.Otp;
import com.chatapp.repository.OtpRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;

@Service
public class OtpService {
    private static final int OTP_LENGTH = 6;
    private static final int OTP_EXPIRY_MINUTES = 5;

    @Autowired
    private OtpRepository otpRepository;

    @Autowired
    private JavaMailSender mailSender;

    public void sendOtp(String email) {
        // Generate OTP
        String otpCode = generateOtp();

        // Save OTP to database
        Otp otp = new Otp();
        otp.setEmail(email);
        otp.setCode(otpCode);
        otp.setExpiryTime(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        otp.setStatus(OtpStatus.UNVERIFIED);
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