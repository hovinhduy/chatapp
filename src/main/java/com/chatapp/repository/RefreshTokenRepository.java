package com.chatapp.repository;

import com.chatapp.model.DeviceSession;
import com.chatapp.model.RefreshToken;
import com.chatapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByUserAndDeviceType(User user, DeviceSession.DeviceType deviceType);

    Optional<RefreshToken> findByUserAndDeviceId(User user, String deviceId);

    Optional<RefreshToken> findByDeviceId(String deviceId);

    List<RefreshToken> findByUser(User user);

    @Modifying
    int deleteByUser(User user);

    @Modifying
    int deleteByUserAndDeviceType(User user, DeviceSession.DeviceType deviceType);

    @Modifying
    int deleteByDeviceId(String deviceId);
}