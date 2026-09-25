package com.frauddetection.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.frauddetection.entity.RiskDecision;
import org.junit.jupiter.api.Test;

class RiskDecisionServiceTest {

    private final RiskDecisionService service = new RiskDecisionService(0.3, 0.85);

    @Test
    void lowProbabilityIsApproved() {
        assertThat(service.decide(0.05)).isEqualTo(RiskDecision.APPROVE);
    }

    @Test
    void midProbabilityGoesToReview() {
        assertThat(service.decide(0.3)).isEqualTo(RiskDecision.REVIEW);
        assertThat(service.decide(0.6)).isEqualTo(RiskDecision.REVIEW);
    }

    @Test
    void highProbabilityIsDeclined() {
        assertThat(service.decide(0.85)).isEqualTo(RiskDecision.DECLINE);
        assertThat(service.decide(0.99)).isEqualTo(RiskDecision.DECLINE);
    }
}
