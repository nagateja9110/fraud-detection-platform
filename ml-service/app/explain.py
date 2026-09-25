import numpy as np

from .features import FEATURE_DESCRIPTIONS, FEATURE_ORDER
from .schemas import RiskFactor


def top_risk_factors(shap_values: np.ndarray, top_n: int = 5) -> list[RiskFactor]:
    """Return the top-N features pushing the score toward fraud (positive SHAP
    contribution), most influential first. Only positive contributors are
    surfaced since those are the actionable "risk factors" for a reviewer."""
    order = np.argsort(shap_values)[::-1]
    factors: list[RiskFactor] = []
    for idx in order:
        if len(factors) >= top_n:
            break
        contribution = float(shap_values[idx])
        if contribution <= 0:
            continue
        name = FEATURE_ORDER[idx]
        factors.append(
            RiskFactor(
                feature=name,
                description=FEATURE_DESCRIPTIONS.get(name, name),
                contribution=round(contribution, 4),
            )
        )
    return factors


def risk_level(probability: float) -> str:
    if probability < 0.3:
        return "LOW"
    if probability < 0.6:
        return "MEDIUM"
    if probability < 0.85:
        return "HIGH"
    return "CRITICAL"
