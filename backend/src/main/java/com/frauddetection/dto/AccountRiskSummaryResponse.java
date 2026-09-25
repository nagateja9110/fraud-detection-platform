package com.frauddetection.dto;

import com.frauddetection.entity.RiskLevel;
import java.util.List;

public record AccountRiskSummaryResponse(
        String accountNumber,
        long totalTransactions,
        long flaggedTransactions,
        RiskLevel lastRiskLevel,
        List<TransactionSummaryDto> recentTransactions) {
}
