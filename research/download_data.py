"""Download the Kaggle "Credit Card Fraud Detection" dataset into research/data/.

Requires ~/.kaggle/kaggle.json (Kaggle API credentials). Run once:
    python download_data.py
"""
import pathlib
import zipfile

from kaggle.api.kaggle_api_extended import KaggleApi

DATASET = "mlg-ulb/creditcardfraud"
DATA_DIR = pathlib.Path(__file__).parent / "data"


def main() -> None:
    DATA_DIR.mkdir(exist_ok=True)
    csv_path = DATA_DIR / "creditcard.csv"
    if csv_path.exists():
        print(f"Already present: {csv_path}")
        return

    api = KaggleApi()
    api.authenticate()
    print(f"Downloading {DATASET} ...")
    api.dataset_download_files(DATASET, path=str(DATA_DIR), quiet=False)

    zip_path = DATA_DIR / "creditcardfraud.zip"
    with zipfile.ZipFile(zip_path) as zf:
        zf.extractall(DATA_DIR)
    zip_path.unlink()
    print(f"Saved to {csv_path}")


if __name__ == "__main__":
    main()
