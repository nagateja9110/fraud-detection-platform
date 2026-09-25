package com.frauddetection.dto.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Mirrors ml-service/app/schemas.py:PredictionResponse field-for-field. */
public record MlPredictionResponse(
        @JsonProperty("transaction_id") String transactionId,
        @JsonProperty("fraud_probability") double fraudProbability,
        @JsonProperty("risk_level") String riskLevel,
        @JsonProperty("top_risk_factors") List<MlRiskFactor> topRiskFactors,
        @JsonProperty("model_version") String modelVersion,
        @JsonProperty("latency_ms") double latencyMs) {
}
