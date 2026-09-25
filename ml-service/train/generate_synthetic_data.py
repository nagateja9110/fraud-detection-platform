"""Generate a synthetic but realistically-correlated transaction dataset.

The public Kaggle fraud dataset (used in research/) has no user/device/account
fields and PCA-anonymized features, so it can't be used to train an
interpretable, explainable production model. This script instead simulates
accounts transacting over time, computing velocity/behavioral features from
each account's *own preceding history* (no lookahead / no label leakage), and
injects realistic fraud episodes (account-takeover style bursts: new device +
velocity spike + odd hour + geo mismatch + amount spike) alongside legitimate
look-alike anomalies (e.g. a genuine big one-off purchase, genuine travel)
so the classification task isn't trivially separable on any single feature.
"""
import random
from datetime import datetime, timedelta

import numpy as np
import pandas as pd

from app.features import CHANNELS, MERCHANT_CATEGORIES

RNG_SEED = 42
N_ACCOUNTS = 3000
SIM_DAYS = 60
OUT_PATH = "train/data/transactions.csv"

random.seed(RNG_SEED)
np.random.seed(RNG_SEED)


def weighted_choice(options, weights):
    return random.choices(options, weights=weights, k=1)[0]


def normal_hour() -> int:
    # Bias towards daytime/evening hours, like real consumer spending.
    return int(np.clip(np.random.normal(15, 5), 0, 23))


def odd_hour() -> int:
    return random.choice([0, 1, 2, 3, 4, 23])


class Account:
    def __init__(self, account_id: int):
        self.account_id = account_id
        self.account_age_days = random.randint(1, 2500)
        self.prior_chargebacks = np.random.choice([0, 0, 0, 0, 1, 2], p=[0.75, 0.1, 0.05, 0.05, 0.03, 0.02])
        self.typical_amount = float(np.random.lognormal(mean=3.2, sigma=0.6))  # ~ $25-$120 median-ish
        self.known_devices: set[str] = {f"dev-{account_id}-0"}
        self.home_is_international = False
        # rolling history: list of (timestamp, amount)
        self.history: list[tuple[datetime, float]] = []
        self.will_have_fraud_episode = random.random() < 0.08
        self.episode_done = False

    def velocity_features(self, now: datetime) -> dict:
        last_1h = [a for t, a in self.history if now - t <= timedelta(hours=1)]
        last_24h = [a for t, a in self.history if now - t <= timedelta(hours=24)]
        last_7d = [a for t, a in self.history if now - t <= timedelta(days=7)]
        avg_7d = float(np.mean(last_7d)) if last_7d else self.typical_amount
        return {
            "txn_count_1h": len(last_1h),
            "txn_count_24h": len(last_24h),
            "amount_sum_1h": float(sum(last_1h)),
            "avg_amount_7d": avg_7d,
        }

    def record(self, ts: datetime, amount: float) -> None:
        self.history.append((ts, amount))


def normal_transaction(acct: Account, ts: datetime) -> dict:
    amount = float(np.random.lognormal(mean=np.log(max(acct.typical_amount, 1)), sigma=0.4))
    category = weighted_choice(MERCHANT_CATEGORIES, [0.30, 0.20, 0.15, 0.08, 0.12, 0.05, 0.03, 0.07])
    channel = weighted_choice(CHANNELS, [0.45, 0.50, 0.05])
    device = random.choice(list(acct.known_devices)) if random.random() > 0.02 else f"dev-{acct.account_id}-{len(acct.known_devices)}"
    is_new_device = device not in acct.known_devices
    if is_new_device:
        acct.known_devices.add(device)
    hour = normal_hour()
    vel = acct.velocity_features(ts)
    is_international = 1 if random.random() < 0.03 else 0

    label = 0
    # Rare legitimate look-alike anomaly: a genuine large one-off purchase or
    # trip abroad, so "amount spike" / "international" alone isn't a giveaway.
    if random.random() < 0.01:
        amount *= random.uniform(3, 8)
    if random.random() < 0.015:
        is_international = 1

    row = {
        "account_id": acct.account_id,
        "timestamp": ts,
        "amount": round(amount, 2),
        "hour_of_day": hour,
        "is_weekend": int(ts.weekday() >= 5),
        "merchant_category": category,
        "channel": channel,
        "is_international": is_international,
        "device_id": device,
        "is_new_device": int(is_new_device),
        "device_trust_score": round(random.uniform(0.0, 0.3) if is_new_device else random.uniform(0.7, 1.0), 2),
        "account_age_days": acct.account_age_days,
        "prior_chargebacks": int(acct.prior_chargebacks),
        **vel,
        "label": label,
    }
    acct.record(ts, amount)
    return row


def fraud_episode_transactions(acct: Account, start_ts: datetime) -> list[dict]:
    """A short account-takeover-style burst: new device, velocity spike,
    odd hour, elevated geo-mismatch odds, amount spike toward risky categories.
    Not every transaction in the episode is guaranteed fraudulent-looking to
    keep the pattern learnable rather than a deterministic rule."""
    n = random.randint(3, 7)
    rows = []
    fraud_device = f"dev-{acct.account_id}-fraud"
    ts = start_ts
    for i in range(n):
        ts = ts + timedelta(minutes=random.uniform(1, 8))
        amount = float(acct.typical_amount * random.uniform(3, 12))
        category = weighted_choice(
            MERCHANT_CATEGORIES, [0.03, 0.02, 0.05, 0.10, 0.25, 0.25, 0.15, 0.15]
        )
        is_new_device = fraud_device not in acct.known_devices
        vel = acct.velocity_features(ts)
        is_international = 1 if random.random() < 0.6 else 0
        # ~85% of episode transactions are labeled fraud; the rest look
        # identical on paper (a legit but unusual burst) to add label noise.
        label = 1 if random.random() < 0.85 else 0

        row = {
            "account_id": acct.account_id,
            "timestamp": ts,
            "amount": round(amount, 2),
            "hour_of_day": odd_hour() if random.random() < 0.7 else normal_hour(),
            "is_weekend": int(ts.weekday() >= 5),
            "merchant_category": category,
            "channel": weighted_choice(CHANNELS, [0.10, 0.85, 0.05]),
            "is_international": is_international,
            "device_id": fraud_device,
            "is_new_device": int(is_new_device),
            "device_trust_score": round(random.uniform(0.0, 0.2), 2),
            "account_age_days": acct.account_age_days,
            "prior_chargebacks": int(acct.prior_chargebacks),
            **vel,
            "label": label,
        }
        acct.known_devices.add(fraud_device)
        acct.record(ts, amount)
        rows.append(row)
    return rows


def main() -> None:
    accounts = [Account(i) for i in range(N_ACCOUNTS)]
    start = datetime(2026, 1, 1)
    all_rows: list[dict] = []

    for acct in accounts:
        # Each account transacts every ~1-3 days on average over the sim window.
        n_txns = max(1, int(np.random.normal(SIM_DAYS / 2, 8)))
        offsets = sorted(random.uniform(0, SIM_DAYS * 24 * 60) for _ in range(n_txns))
        episode_at = random.choice(offsets) if acct.will_have_fraud_episode and offsets else None

        for off in offsets:
            ts = start + timedelta(minutes=off)
            if episode_at is not None and abs(off - episode_at) < 1e-6 and not acct.episode_done:
                all_rows.extend(fraud_episode_transactions(acct, ts))
                acct.episode_done = True
            else:
                all_rows.append(normal_transaction(acct, ts))

    df = pd.DataFrame(all_rows).sort_values(["account_id", "timestamp"]).reset_index(drop=True)
    print(f"Generated {len(df):,} transactions, fraud rate {df['label'].mean() * 100:.2f}%")
    import pathlib

    out = pathlib.Path(OUT_PATH)
    out.parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(out, index=False)
    print(f"Saved to {out.resolve()}")


if __name__ == "__main__":
    main()
