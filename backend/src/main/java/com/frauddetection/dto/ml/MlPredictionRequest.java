package com.frauddetection.dto.ml;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Mirrors ml-service/app/schemas.py:PredictionRequest field-for-field. */
public record MlPredictionRequest(
        @JsonProperty("transaction_id") String transactionId,
        @JsonProperty("account_id") String accountId,
        double amount,
        @JsonProperty("hour_of_day") int hourOfDay,
        @JsonProperty("is_weekend") boolean isWeekend,
        @JsonProperty("merchant_category") String merchantCategory,
        String channel,
        @JsonProperty("is_international") boolean isInternational,
        @JsonProperty("txn_count_1h") int txnCount1h,
        @JsonProperty("txn_count_24h") int txnCount24h,
        @JsonProperty("amount_sum_1h") double amountSum1h,
        @JsonProperty("avg_amount_7d") Double avgAmount7d,
        @JsonProperty("is_new_device") boolean isNewDevice,
        @JsonProperty("device_trust_score") double deviceTrustScore,
        @JsonProperty("account_age_days") long accountAgeDays,
        @JsonProperty("prior_chargebacks") int priorChargebacks) {
}
