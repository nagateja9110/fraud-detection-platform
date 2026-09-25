package com.frauddetection.dto.ml;

public record MlRiskFactor(String feature, String description, double contribution) {
}
