# Production model - synthetic interpretable-feature dataset

Dataset: 88,721 transactions, 985 fraud (1.11%)

## Metrics (test split, threshold=0.5)

| Metric | Value |
|---|---|
| precision | 0.8455 |
| recall | 1.0000 |
| f1 | 0.9163 |
| roc_auc | 0.9992 |
| pr_auc | 0.8910 |

Test set: 17,745 txns, 197 fraud.

## Top features by mean |SHAP value|

| Feature | Mean |SHAP| |
|---|---|
| device_trust_score | 7.1562 |
| amount_to_avg_ratio | 1.3032 |
| amount_sum_1h | 1.2133 |
| amount | 1.2071 |
| hour_of_day | 0.5111 |
| merchant_category_risk | 0.3058 |
| txn_count_1h | 0.2786 |
| account_age_days | 0.2785 |
| txn_count_24h | 0.2514 |
| channel_online | 0.2408 |
