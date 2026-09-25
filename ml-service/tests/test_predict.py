import pytest
from fastapi.testclient import TestClient

from app.main import app


@pytest.fixture(scope="module")
def client():
    with TestClient(app) as c:
        yield c


def _base_payload(**overrides):
    payload = {
        "transaction_id": "t-1",
        "account_id": "a-1",
        "amount": 40.0,
        "hour_of_day": 14,
        "is_weekend": False,
        "merchant_category": "grocery",
        "channel": "POS",
        "is_international": False,
        "txn_count_1h": 1,
        "txn_count_24h": 2,
        "amount_sum_1h": 40.0,
        "avg_amount_7d": 45.0,
        "is_new_device": False,
        "device_trust_score": 0.9,
        "account_age_days": 500,
        "prior_chargebacks": 0,
    }
    payload.update(overrides)
    return payload


def test_health(client):
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json()["model_loaded"] is True


def test_predict_normal_transaction_is_low_risk(client):
    resp = client.post("/predict", json=_base_payload())
    assert resp.status_code == 200
    body = resp.json()
    assert body["risk_level"] in ("LOW", "MEDIUM")
    assert 0.0 <= body["fraud_probability"] <= 1.0
    assert body["model_version"]


def test_predict_fraud_shaped_transaction_is_high_risk(client):
    resp = client.post(
        "/predict",
        json=_base_payload(
            amount=900.0,
            hour_of_day=3,
            merchant_category="gift_card",
            channel="ONLINE",
            is_international=True,
            txn_count_1h=6,
            amount_sum_1h=3000.0,
            is_new_device=True,
            device_trust_score=0.05,
            prior_chargebacks=1,
        ),
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["risk_level"] in ("HIGH", "CRITICAL")
    assert body["fraud_probability"] > 0.6
    assert len(body["top_risk_factors"]) > 0


def test_predict_rejects_invalid_merchant_category(client):
    resp = client.post("/predict", json=_base_payload(merchant_category="not_a_category"))
    assert resp.status_code == 422
