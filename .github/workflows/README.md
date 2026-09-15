# CI workflows

| Workflow | Trigger | What it does |
|---|---|---|
| `android-ci.yml` | push to `main`, every PR, manual | `assembleDebug`, Detekt, Lint, Konsist, unit + Room migration tests, Roborazzi `verify` (fails on screenshot diffs, uploads the diff images), Kover XML report. The manual `record-screenshots` job regenerates baselines in CI's environment and publishes them as an artifact. |
| `backend-ci.yml` | changes under `backend/` | Ruff, mypy, pytest — skipped until `backend/pyproject.toml` exists (M4). |
| `ml-eval.yml` | changes under `ml/` | Classifier evaluation gate — skipped until `ml/pyproject.toml` exists (M2). |

`release.yml` (Play internal track) is added with the v1.0 release work package.
