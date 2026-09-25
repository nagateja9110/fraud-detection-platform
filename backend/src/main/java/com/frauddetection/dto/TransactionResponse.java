package com.frauddetection.dto;

import com.frauddetection.entity.Channel;
import com.frauddetection.entity.MerchantCategory;
import com.frauddetection.entity.RiskDecision;
import com.frauddetection.entity.RiskLevel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionResponse(
        UUID transactionId,
        String accountNumber,
        BigDecimal amount,
        String currency,
        MerchantCategory merchantCategory,
        Channel channel,
        boolean international,
        Instant occurredAt,
        double fraudProbability,
        RiskLevel riskLevel,
        RiskDecision decision,
        List<RiskFactorDto> topRiskFactors,
        VelocitySnapshotDto velocity,
        String modelVersion,
        double mlLatencyMs) {
}
