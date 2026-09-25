package com.frauddetection.entity;

import com.fasterxml.jackson.annotation.JsonValue;

/** Must match ml-service/app/features.py:MERCHANT_CATEGORIES exactly. */
public enum MerchantCategory {
    GROCERY,
    RESTAURANT,
    SUBSCRIPTION,
    TRAVEL,
    ELECTRONICS,
    GIFT_CARD,
    JEWELRY,
    GAMBLING;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }
}
