import json
import pathlib

import numpy as np
import shap
from joblib import load

from .features import FEATURE_ORDER, build_feature_vector

MODELS_DIR = pathlib.Path(__file__).parent.parent / "models"


class FraudModel:
    def __init__(self) -> None:
        model_path = MODELS_DIR / "model.pkl"
        meta_path = MODELS_DIR / "feature_list.json"
        if not model_path.exists() or not meta_path.exists():
            raise FileNotFoundError(
                f"Model artifacts not found in {MODELS_DIR}. "
                "Run `python -m train.generate_synthetic_data` then "
                "`python -m train.train_production_model` from ml-service/."
            )
        self.model = load(model_path)
        meta = json.loads(meta_path.read_text())
        self.feature_order = meta["features"]
        self.threshold = meta["threshold"]
        self.version = meta["version"]
        assert self.feature_order == FEATURE_ORDER, "feature_list.json is out of sync with features.py"
        self.explainer = shap.TreeExplainer(self.model)

    def predict(self, record: dict) -> tuple[float, np.ndarray]:
        vector = np.array([build_feature_vector(record)])
        proba = float(self.model.predict_proba(vector)[0, 1])
        shap_values = self.explainer.shap_values(vector)[0]
        return proba, shap_values
