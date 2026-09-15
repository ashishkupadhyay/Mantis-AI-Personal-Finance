# ml

Training, calibration, export and evaluation for the on-device models (transaction classifier, item classifier) and the evaluation harnesses (pace, forecast, recurring, receipts, LLM fallback, assistant).

## Data handling
- `data/raw/` (bank exports, receipt photos) is git-ignored and DVC-tracked to private storage. Never commit raw statements or images.
- Only OCR dumps + labels for receipts are committed (`data/receipts/`), never the images.
- The frozen test sets (`data/test_v1.parquet`, `items/items_test_v1.parquet`) are committed once and never edited.

Placeholder until the intelligence milestone.
