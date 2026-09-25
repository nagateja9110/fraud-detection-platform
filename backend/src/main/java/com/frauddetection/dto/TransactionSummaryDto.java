package com.frauddetection.dto;

import com.frauddetection.entity.Channel;
import com.frauddetection.entity.MerchantCategory;
import com.frauddetection.entity.RiskDecision;
import com.frauddetection.entity.RiskLevel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionSummaryDto(
        UUID transactionId,
        String accountNumber,
        BigDecimal amount,
        MerchantCategory merchantCategory,
        Channel channel,
        Instant occurredAt,
        double fraudProbability,
        RiskLevel riskLevel,
        RiskDecision decision) {
}
