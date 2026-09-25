from typing import Literal, Optional

from pydantic import BaseModel, Field

from .features import CHANNELS, MERCHANT_CATEGORIES


class PredictionRequest(BaseModel):
    transaction_id: str
    account_id: str
    amount: float = Field(gt=0)
    hour_of_day: int = Field(ge=0, le=23)
    is_weekend: bool = False
    merchant_category: Literal[tuple(MERCHANT_CATEGORIES)]  # type: ignore[valid-type]
    channel: Literal[tuple(CHANNELS)]  # type: ignore[valid-type]
    is_international: bool = False

    # Behavioral/velocity signals computed by the Spring Boot VelocityService (Redis).
    txn_count_1h: int = 0
    txn_count_24h: int = 0
    amount_sum_1h: float = 0.0
    avg_amount_7d: Optional[float] = None

    # Device signals.
    is_new_device: bool = False
    device_trust_score: float = Field(default=0.5, ge=0, le=1)

    # Account signals.
    account_age_days: int = 0
    prior_chargebacks: int = 0


class RiskFactor(BaseModel):
    feature: str
    description: str
    contribution: float


class PredictionResponse(BaseModel):
    transaction_id: str
    fraud_probability: float
    risk_level: Literal["LOW", "MEDIUM", "HIGH", "CRITICAL"]
    top_risk_factors: list[RiskFactor]
    model_version: str
    latency_ms: float
