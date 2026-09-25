# Benchmark model - Kaggle Credit Card Fraud dataset

Dataset: 284,807 transactions, 492 fraud (0.173%)

## Metrics (test split, threshold=0.5)

| Metric | Value |
|---|---|
| precision | 0.7757 |
| recall | 0.8469 |
| f1 | 0.8098 |
| roc_auc | 0.9800 |
| pr_auc | 0.8686 |

Test set: 56,962 txns, 98 fraud.

## Top 10 features by importance

| Feature | Importance |
|---|---|
| V14 | 0.3324 |
| V10 | 0.1206 |
| V4 | 0.0778 |
| V12 | 0.0604 |
| V8 | 0.0348 |
| V20 | 0.0331 |
| Amount | 0.0233 |
| V19 | 0.0231 |
| V3 | 0.0219 |
| V13 | 0.0210 |

Features are PCA-anonymized (`V1..V28`) plus `Time`/`Amount` -- not human-interpretable, which is why the live system (ml-service/) uses a separate model trained on engineered, explainable features. See project README.
