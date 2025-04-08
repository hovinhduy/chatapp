package com.chatapp.repository;

import com.chatapp.enums.OtpStatus;
import com.chatapp.model.Otp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpRepository extends JpaRepository<Otp, Long> {
    Optional<Otp> findByEmailAndCodeAndStatus(String email, String code, OtpStatus status);

    Optional<Otp> findByEmailAndStatus(String email, OtpStatus status);

    // kiểm tra email đã verify chưa
    boolean existsByEmailAndStatus(String email, OtpStatus status);
}