package com.frauddetection.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "devices", uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "device_fingerprint"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "device_fingerprint", nullable = false)
    private String deviceFingerprint;

    private String deviceType;

    @Column(nullable = false, updatable = false)
    private Instant firstSeenAt;

    /** Trust grows with a device's track record; used as a proxy for device_trust_score. */
    public double trustScore() {
        long daysKnown = java.time.temporal.ChronoUnit.DAYS.between(firstSeenAt, Instant.now());
        if (daysKnown <= 0) {
            return 0.15;
        }
        return Math.min(0.95, 0.3 + daysKnown * 0.05);
    }
}
