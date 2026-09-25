package com.frauddetection.dto;

import com.frauddetection.entity.Channel;
import com.frauddetection.entity.MerchantCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionRequest(
        @NotBlank String accountNumber,
        String ownerName,
        String accountType,
        String homeCountry,
        @NotBlank String deviceFingerprint,
        String deviceType,
        @NotNull @Positive BigDecimal amount,
        String currency,
        @NotNull MerchantCategory merchantCategory,
        @NotNull Channel channel,
        String country,
        Instant occurredAt) {
}
