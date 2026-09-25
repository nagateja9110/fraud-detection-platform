package com.frauddetection.repository;

import com.frauddetection.entity.Account;
import com.frauddetection.entity.Device;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, UUID> {
    Optional<Device> findByAccountAndDeviceFingerprint(Account account, String deviceFingerprint);
}
