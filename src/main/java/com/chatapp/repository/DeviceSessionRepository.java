package com.chatapp.repository;

import com.chatapp.model.DeviceSession;
import com.chatapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceSessionRepository extends JpaRepository<DeviceSession, Long> {
    List<DeviceSession> findByUserAndActiveTrue(User user);

    Optional<DeviceSession> findByUserAndDeviceTypeAndActiveTrue(User user, DeviceSession.DeviceType deviceType);

    List<DeviceSession> findByUserOrderByLoginTimeDesc(User user);

    Optional<DeviceSession> findByDeviceIdAndActiveTrue(String deviceId);
}