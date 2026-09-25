package com.frauddetection.service;

import com.frauddetection.entity.RiskDecision;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RiskDecisionService {

    private final double reviewThreshold;
    private final double declineThreshold;

    public RiskDecisionService(
            @Value("${fraud.decision.review-threshold}") double reviewThreshold,
            @Value("${fraud.decision.decline-threshold}") double declineThreshold) {
        this.reviewThreshold = reviewThreshold;
        this.declineThreshold = declineThreshold;
    }

    public RiskDecision decide(double fraudProbability) {
        if (fraudProbability >= declineThreshold) {
            return RiskDecision.DECLINE;
        }
        if (fraudProbability >= reviewThreshold) {
            return RiskDecision.REVIEW;
        }
        return RiskDecision.APPROVE;
    }
}
