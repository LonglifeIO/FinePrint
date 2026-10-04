# pipeline/

Runs on the Mac Mini. Scripts, not a package. Python 3.12+, stdlib + `requests` + `jsonschema`.

Build order (each is a separate script, each is idempotent):

1. `fetch_trackers.py` — Exodus `/api/trackers` plus `fp_trackers.json` (Fine Print's own, evidenced signatures)
   → the Android asset `android/app/src/main/assets/trackers.json` (ODbL). A `data/trackers.sqlite` copy is not built yet.
2. `fetch_app.py <package>` — Exodus report, Play listing metadata, privacy-policy URL → `raw/` (gitignored).
3. `draft.py <package>` — LLM draft of tracker/app records with mandatory `sources`; refuses to emit
   a consequence with no source. Writes to `drafts/` (gitignored). Hand-run via Codex CLI for now.
4. `review.py` — opens the draft queue; approved records move into `reviewed/`.
5. `build.py` — validates `reviewed/` against `../bundle/schema.json`, checks every `source_url`
   resolves (HTTP 200), writes `../bundle/bundle.json`.

Serve for dev: `python -m http.server 8080 --directory ../bundle` behind Tailscale.
