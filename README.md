# Fraud Detection & Risk Assessment Platform

ML-powered fraud detection platform that scores transactions in real time using
transaction, behavioral, device, and account signals. A Spring Boot API handles
ingestion, Redis-based velocity tracking, and risk decisions; a dedicated
FastAPI service runs an XGBoost model and returns explainable risk factors.

## Architecture

```
Client / demo script
      |
      v
Spring Boot API (:8081) -- PostgreSQL (:5434)   [accounts, devices, transactions, risk_assessments]
      |        |
      |        +-- Redis (:6380)                [velocity sorted sets: counts + sums per account]
      |
      v (WebClient, per-transaction)
FastAPI ML service (:8001) -- XGBoost model + SHAP explainer, loaded at startup
      |
      +-- fraud probability, risk level, top contributing risk factors, latency
```

`POST /api/transactions` flow:
1. Spring Boot resolves/creates the account + device rows in Postgres.
2. `VelocityService` updates Redis sorted sets for the account and computes
   transaction-count/amount-sum features over 1min/1hr/24hr windows.
3. Spring Boot calls the FastAPI `/predict` endpoint with the transaction +
   behavioral + device + account + velocity features.
4. FastAPI engineers the feature vector, runs XGBoost, computes SHAP
   contributions, and returns fraud probability, risk level, and the top
   contributing risk factors.
5. Spring Boot applies decision thresholds (APPROVE / REVIEW / DECLINE),
   persists the `RiskAssessment`, and returns the full result.

## Why two models

The public Kaggle "Credit Card Fraud Detection" dataset (284,807 real
transactions, 492 frauds) is the standard benchmark for imbalanced fraud
classification, so it's used to produce honest, citable evaluation numbers.
But its 28 features are anonymized PCA components (`V1..V28`) with no
semantic meaning, and it has no user/device/account fields at all -- so it
can't power "explainable risk factors" or demonstrate behavioral/device
feature engineering or Redis velocity tracking.

So this project trains **two models**:

- **Benchmark model** (`research/`) -- trained directly on the real Kaggle
  dataset. This is the evidence for the evaluation methodology.
- **Production model** (`ml-service/train/`) -- trained on a synthetically
  generated but realistically-correlated dataset with *interpretable*
  features (amount, merchant category, channel, behavioral velocity, device
  trust, account age) and rule-based fraud-episode injection (account
  takeover-style bursts: new device + velocity spike + odd hour + geo
  mismatch + amount spike). This is the model actually served by `ml-service/`,
  because its features produce meaningful SHAP-based risk factors and map
  directly onto what `VelocityService` computes in real time.

### Benchmark model results (Kaggle dataset, real data)

| Metric | Value |
|---|---|
| Precision | 0.776 |
| Recall | 0.847 |
| F1 | 0.810 |
| ROC-AUC | 0.980 |
| PR-AUC | 0.869 |

Full report: [research/reports/metrics.md](research/reports/metrics.md)

### Production model results (synthetic interpretable-feature dataset)

| Metric | Value |
|---|---|
| Precision | 0.845 |
| Recall | 1.000 |
| F1 | 0.916 |
| ROC-AUC | 0.999 |
| PR-AUC | 0.891 |

Top features by mean \|SHAP value\|: `device_trust_score`, `amount_to_avg_ratio`,
`amount_sum_1h`, `amount`, `hour_of_day`. Full report:
[ml-service/train/reports/metrics.md](ml-service/train/reports/metrics.md)

(The production model's near-perfect recall reflects the injected fraud
pattern being a fairly distinct cluster in synthetic data -- the rigor behind
the evaluation methodology itself is demonstrated by the benchmark model
above, against real, standard data.)

## Tech stack

Java 21, Spring Boot 3, PostgreSQL, Redis, Python 3.11, FastAPI, XGBoost, SHAP, Docker Compose.

## Running it

```bash
cp .env.example .env
docker compose up --build
```

This starts Postgres (`:5434`), Redis (`:6380`), the ML service (`:8001`),
and the backend (`:8081`). API docs: `http://localhost:8081/swagger-ui.html`.

### Live demo dashboard

`http://localhost:8081/dashboard.html` -- a small live console: submit a
transaction and see the fraud score / risk factors render in real time, plus
a polling feed of recent transactions color-coded by risk level. Click
"Simulate fraud burst" for a one-click demo of the velocity-driven escalation
to CRITICAL/DECLINE.

### Try it

```bash
curl -X POST http://localhost:8081/api/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "accountNumber": "acct-1",
    "ownerName": "Jane Doe",
    "homeCountry": "US",
    "deviceFingerprint": "device-abc",
    "amount": 42.50,
    "merchantCategory": "grocery",
    "channel": "POS",
    "country": "US"
  }'
```

A normal transaction like this scores LOW risk / APPROVE. A rapid burst of
large, international, gift-card purchases from a new device on the same
account escalates to CRITICAL / DECLINE with velocity- and device-related
risk factors -- see `demo/` for a scripted walkthrough.

Review queue: `GET /api/transactions?riskLevel=HIGH`
Account risk summary: `GET /api/accounts/{accountNumber}/risk-summary`

## Regenerating the models

```bash
# Benchmark model (requires ~/.kaggle/kaggle.json)
cd research && python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python download_data.py
python train_benchmark_model.py

# Production model (served by ml-service)
cd ml-service && python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python -m train.generate_synthetic_data
python -m train.train_production_model
```

## Testing

```bash
# ml-service
cd ml-service && source .venv/bin/activate && python -m pytest tests/ -v

# backend (needs Docker running -- Testcontainers spins up real Postgres/Redis)
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test
```

**Note:** the backend build requires JDK 21 specifically -- Lombok's
annotation processor does not yet support newer JDKs (e.g. 25) on this
machine's Lombok version, which silently disables getter/setter/builder
generation and breaks compilation. Set `JAVA_HOME` to a JDK 21 install before
running Maven.
