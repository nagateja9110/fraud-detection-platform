package com.frauddetection.service;

import com.frauddetection.dto.TransactionRequest;
import com.frauddetection.entity.Account;
import com.frauddetection.entity.Device;
import com.frauddetection.repository.DeviceRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public record Lookup(Device device, boolean isNew) {
    }

    public Lookup findOrCreate(Account account, TransactionRequest request) {
        var existing = deviceRepository.findByAccountAndDeviceFingerprint(account, request.deviceFingerprint());
        if (existing.isPresent()) {
            return new Lookup(existing.get(), false);
        }
        Device created = deviceRepository.save(Device.builder()
                .account(account)
                .deviceFingerprint(request.deviceFingerprint())
                .deviceType(request.deviceType())
                .firstSeenAt(Instant.now())
                .build());
        return new Lookup(created, true);
    }
}
