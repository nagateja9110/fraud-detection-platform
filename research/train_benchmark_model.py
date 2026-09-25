"""Benchmark XGBoost against the real, public Kaggle credit-card-fraud dataset.

This is the evidence for the resume's evaluation claim (Precision/Recall/F1/
ROC-AUC/PR-AUC on real, heavily imbalanced fraud data). It is not the model
served live by ml-service/ -- see ml-service/train/train_production_model.py
and the "two models" note in the project README for why.
"""
import json
import pathlib

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import xgboost as xgb
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

HERE = pathlib.Path(__file__).parent
DATA_PATH = HERE / "data" / "creditcard.csv"
REPORTS_DIR = HERE / "reports"
DECISION_THRESHOLD = 0.5


def load_data() -> pd.DataFrame:
    if not DATA_PATH.exists():
        raise FileNotFoundError(
            f"{DATA_PATH} not found. Run `python download_data.py` first "
            "(requires ~/.kaggle/kaggle.json)."
        )
    return pd.read_csv(DATA_PATH)


def main() -> None:
    REPORTS_DIR.mkdir(exist_ok=True)
    df = load_data()

    X = df.drop(columns=["Class"])
    y = df["Class"]

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, stratify=y, random_state=42
    )

    fraud_rate = y_train.mean()
    scale_pos_weight = (1 - fraud_rate) / fraud_rate
    print(f"Train fraud rate: {fraud_rate:.5f}  scale_pos_weight={scale_pos_weight:.1f}")

    model = xgb.XGBClassifier(
        n_estimators=300,
        max_depth=5,
        learning_rate=0.05,
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
    plt.title("Benchmark model (Kaggle data) - Confusion Matrix")
    plt.savefig(REPORTS_DIR / "confusion_matrix.png", bbox_inches="tight")
    plt.close()

    PrecisionRecallDisplay.from_predictions(y_test, proba, name="XGBoost")
    plt.title("Benchmark model (Kaggle data) - Precision-Recall Curve")
    plt.savefig(REPORTS_DIR / "pr_curve.png", bbox_inches="tight")
    plt.close()

    RocCurveDisplay.from_predictions(y_test, proba, name="XGBoost")
    plt.title("Benchmark model (Kaggle data) - ROC Curve")
    plt.savefig(REPORTS_DIR / "roc_curve.png", bbox_inches="tight")
    plt.close()

    importances = pd.Series(model.feature_importances_, index=X.columns).sort_values(
        ascending=False
    )

    with open(REPORTS_DIR / "metrics.md", "w") as f:
        f.write("# Benchmark model - Kaggle Credit Card Fraud dataset\n\n")
        f.write(
            f"Dataset: {len(df):,} transactions, {int(df['Class'].sum())} fraud "
            f"({df['Class'].mean() * 100:.3f}%)\n\n"
        )
        f.write("## Metrics (test split, threshold=0.5)\n\n")
        f.write("| Metric | Value |\n|---|---|\n")
        for k in ["precision", "recall", "f1", "roc_auc", "pr_auc"]:
            f.write(f"| {k} | {metrics[k]:.4f} |\n")
        f.write(f"\nTest set: {metrics['n_test']:,} txns, {metrics['n_test_fraud']} fraud.\n\n")
        f.write("## Top 10 features by importance\n\n")
        f.write("| Feature | Importance |\n|---|---|\n")
        for feat, imp in importances.head(10).items():
            f.write(f"| {feat} | {imp:.4f} |\n")
        f.write(
            "\nFeatures are PCA-anonymized (`V1..V28`) plus `Time`/`Amount` -- "
            "not human-interpretable, which is why the live system "
            "(ml-service/) uses a separate model trained on engineered, "
            "explainable features. See project README.\n"
        )

    print(f"\nWrote reports to {REPORTS_DIR}")


if __name__ == "__main__":
    main()
