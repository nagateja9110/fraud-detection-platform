import logging
import time
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException

from .explain import risk_level, top_risk_factors
from .model import FraudModel
from .schemas import PredictionRequest, PredictionResponse

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("fraud-ml-service")

_model: FraudModel | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _model
    _model = FraudModel()
    logger.info("Loaded fraud model version %s", _model.version)
    yield


app = FastAPI(title="Fraud Detection ML Service", version="1.0.0", lifespan=lifespan)


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "model_loaded": _model is not None}


@app.post("/predict", response_model=PredictionResponse)
def predict(request: PredictionRequest) -> PredictionResponse:
    if _model is None:
        raise HTTPException(status_code=503, detail="Model not loaded")

    start = time.perf_counter()
    proba, shap_values = _model.predict(request.model_dump())
    latency_ms = (time.perf_counter() - start) * 1000

    return PredictionResponse(
        transaction_id=request.transaction_id,
        fraud_probability=round(proba, 6),
        risk_level=risk_level(proba),
        top_risk_factors=top_risk_factors(shap_values),
        model_version=_model.version,
        latency_ms=round(latency_ms, 3),
    )
