"""Shared feature engineering for the production fraud model.

Imported by both the training script (ml-service/train/train_production_model.py)
and the serving app (ml-service/app/model.py) so the exact same transformation
is applied at train time and inference time -- avoids train/serve skew.
"""
from __future__ import annotations

MERCHANT_CATEGORIES = [
    "grocery",
    "restaurant",
    "subscription",
    "travel",
    "electronics",
    "gift_card",
    "jewelry",
    "gambling",
]

# Rough relative "riskiness" per merchant category, used as a numeric feature.
# Gift cards / gambling / jewelry are classic money-laundering / cash-out
# vectors, electronics and travel are common high-value fraud targets.
MERCHANT_CATEGORY_RISK = {
    "grocery": 0.05,
    "restaurant": 0.05,
    "subscription": 0.10,
    "travel": 0.30,
    "electronics": 0.40,
    "jewelry": 0.55,
    "gift_card": 0.65,
    "gambling": 0.70,
}

CHANNELS = ["POS", "ONLINE", "ATM"]

# Final, ordered list of numeric features fed to the XGBoost model.
FEATURE_ORDER = [
    "amount",
    "amount_to_avg_ratio",
    "hour_of_day",
    "is_night",
    "is_weekend",
    "merchant_category_risk",
    "channel_online",
    "channel_atm",
    "is_international",
    "txn_count_1h",
    "txn_count_24h",
    "amount_sum_1h",
    "is_new_device",
    "device_trust_score",
    "account_age_days",
    "prior_chargebacks",
]

# Human-readable descriptions used by explain.py to turn a SHAP contribution
# into an "explainable risk factor" a fraud reviewer can act on.
FEATURE_DESCRIPTIONS = {
    "amount": "Transaction amount",
    "amount_to_avg_ratio": "Amount vs. this account's typical spend",
    "hour_of_day": "Hour of day the transaction occurred",
    "is_night": "Occurred late night / early morning (12am-5am)",
    "is_weekend": "Occurred on a weekend",
    "merchant_category_risk": "Merchant category risk level",
    "channel_online": "Card-not-present / online channel",
    "channel_atm": "ATM channel",
    "is_international": "Cross-border / geo-mismatched transaction",
    "txn_count_1h": "Number of transactions in the last hour",
    "txn_count_24h": "Number of transactions in the last 24 hours",
    "amount_sum_1h": "Total amount spent in the last hour",
    "is_new_device": "First transaction seen from this device",
    "device_trust_score": "Trust score of the originating device",
    "account_age_days": "Account age in days",
    "prior_chargebacks": "Prior chargebacks on this account",
}


def build_feature_vector(record: dict) -> list[float]:
    """Turn a flat dict of raw fields into the ordered numeric vector the model expects.

    Expected keys in `record`: amount, avg_amount_7d, hour_of_day, is_weekend,
    merchant_category, channel, is_international, txn_count_1h, txn_count_24h,
    amount_sum_1h, is_new_device, device_trust_score, account_age_days,
    prior_chargebacks.
    """
    amount = float(record["amount"])
    avg_amount_7d = float(record.get("avg_amount_7d") or 0.0)
    amount_to_avg_ratio = amount / avg_amount_7d if avg_amount_7d > 0 else 1.0

    hour = int(record["hour_of_day"])
    is_night = 1.0 if (hour >= 0 and hour < 5) else 0.0

    channel = record.get("channel", "POS")
    channel_online = 1.0 if channel == "ONLINE" else 0.0
    channel_atm = 1.0 if channel == "ATM" else 0.0

    merchant_category = record.get("merchant_category", "grocery")
    merchant_risk = MERCHANT_CATEGORY_RISK.get(merchant_category, 0.2)

    values = {
        "amount": amount,
        "amount_to_avg_ratio": amount_to_avg_ratio,
        "hour_of_day": float(hour),
        "is_night": is_night,
        "is_weekend": float(record.get("is_weekend", 0)),
        "merchant_category_risk": merchant_risk,
        "channel_online": channel_online,
        "channel_atm": channel_atm,
        "is_international": float(record.get("is_international", 0)),
        "txn_count_1h": float(record.get("txn_count_1h", 0)),
        "txn_count_24h": float(record.get("txn_count_24h", 0)),
        "amount_sum_1h": float(record.get("amount_sum_1h", 0.0)),
        "is_new_device": float(record.get("is_new_device", 0)),
        "device_trust_score": float(record.get("device_trust_score", 0.5)),
        "account_age_days": float(record.get("account_age_days", 0)),
        "prior_chargebacks": float(record.get("prior_chargebacks", 0)),
    }
    return [values[name] for name in FEATURE_ORDER]
