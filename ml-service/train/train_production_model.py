"""Train the production fraud model on the synthetic, interpretable-feature
dataset (see generate_synthetic_data.py). Saves the model + feature list that
ml-service/app/model.py loads at serving time, plus evaluation reports.

Run from the ml-service/ directory:
    python -m train.train_production_model
"""
import json
import pathlib

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import shap
import xgboost as xgb
from joblib import dump
from sklearn.metrics import (
    ConfusionMatrixDisplay,
    PrecisionRecallDisplay,
    RocCurveDisplay,
    average_precision_score,
    confusion_matrix,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import train_test_split

from app.features import FEATURE_ORDER, build_feature_vector

HERE = pathlib.Path(__file__).parent
DATA_PATH = HERE / "data" / "transactions.csv"
MODELS_DIR = HERE.parent / "models"
REPORTS_DIR = HERE / "reports"
DECISION_THRESHOLD = 0.5


def load_matrix(df: pd.DataFrame) -> tuple[np.ndarray, np.ndarray]:
    X = np.array([build_feature_vector(row) for row in df.to_dict("records")])
    y = df["label"].to_numpy()
    return X, y


def main() -> None:
    if not DATA_PATH.exists():
        raise FileNotFoundError(f"{DATA_PATH} not found. Run generate_synthetic_data.py first.")

    MODELS_DIR.mkdir(exist_ok=True)
    REPORTS_DIR.mkdir(exist_ok=True)

    df = pd.read_csv(DATA_PATH)
    X, y = load_matrix(df)

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, stratify=y, random_state=42
    )

    fraud_rate = y_train.mean()
    scale_pos_weight = (1 - fraud_rate) / fraud_rate
    print(f"Train fraud rate: {fraud_rate:.4f}  scale_pos_weight={scale_pos_weight:.1f}")

    model = xgb.XGBClassifier(
        n_estimators=250,
        max_depth=4,
        learning_rate=0.08,
        subsample=0.8,
        colsample_bytree=0.8,
        eval_metric="aucpr",
        scale_pos_weight=scale_pos_weight,
        random_state=42,
        n_jobs=-1,
    )
    model.fit(X_train, y_train)

    proba = model.predict_proba(X_test)[:, 1]
    preds = (proba >= DECISION_THRESHOLD).astype(int)

    metrics = {
        "threshold": DECISION_THRESHOLD,
        "precision": precision_score(y_test, preds),
        "recall": recall_score(y_test, preds),
        "f1": f1_score(y_test, preds),
        "roc_auc": roc_auc_score(y_test, proba),
        "pr_auc": average_precision_score(y_test, proba),
        "n_test": int(len(y_test)),
        "n_test_fraud": int(y_test.sum()),
    }
    print(json.dumps(metrics, indent=2))

    cm = confusion_matrix(y_test, preds)
    ConfusionMatrixDisplay(cm, display_labels=["Legit", "Fraud"]).plot()
    plt.title("Production model - Confusion Matrix")
    plt.savefig(REPORTS_DIR / "confusion_matrix.png", bbox_inches="tight")
    plt.close()

    PrecisionRecallDisplay.from_predictions(y_test, proba, name="XGBoost")
    plt.title("Production model - Precision-Recall Curve")
    plt.savefig(REPORTS_DIR / "pr_curve.png", bbox_inches="tight")
    plt.close()

    RocCurveDisplay.from_predictions(y_test, proba, name="XGBoost")
    plt.title("Production model - ROC Curve")
    plt.savefig(REPORTS_DIR / "roc_curve.png", bbox_inches="tight")
    plt.close()

    explainer = shap.TreeExplainer(model)
    shap_values = explainer.shap_values(X_test[:2000])
    shap.summary_plot(
        shap_values, X_test[:2000], feature_names=FEATURE_ORDER, show=False, plot_size=(8, 6)
    )
    plt.title("Production model - SHAP feature importance")
    plt.savefig(REPORTS_DIR / "shap_summary.png", bbox_inches="tight")
    plt.close()

    mean_abs_shap = pd.Series(np.abs(shap_values).mean(axis=0), index=FEATURE_ORDER).sort_values(
        ascending=False
    )

    with open(REPORTS_DIR / "metrics.md", "w") as f:
        f.write("# Production model - synthetic interpretable-feature dataset\n\n")
        f.write(
            f"Dataset: {len(df):,} transactions, {int(df['label'].sum())} fraud "
            f"({df['label'].mean() * 100:.2f}%)\n\n"
        )
        f.write("## Metrics (test split, threshold=0.5)\n\n")
        f.write("| Metric | Value |\n|---|---|\n")
        for k in ["precision", "recall", "f1", "roc_auc", "pr_auc"]:
            f.write(f"| {k} | {metrics[k]:.4f} |\n")
        f.write(f"\nTest set: {metrics['n_test']:,} txns, {metrics['n_test_fraud']} fraud.\n\n")
        f.write("## Top features by mean |SHAP value|\n\n")
        f.write("| Feature | Mean |SHAP| |\n|---|---|\n")
        for feat, val in mean_abs_shap.head(10).items():
            f.write(f"| {feat} | {val:.4f} |\n")

    dump(model, MODELS_DIR / "model.pkl")
    with open(MODELS_DIR / "feature_list.json", "w") as f:
        json.dump({"features": FEATURE_ORDER, "threshold": DECISION_THRESHOLD, "version": "1.0.0"}, f, indent=2)

    print(f"\nSaved model to {MODELS_DIR}, reports to {REPORTS_DIR}")


if __name__ == "__main__":
    main()
