# pipeline/

Runs on the Mac Mini. Scripts, not a package. Python 3.12+, stdlib + `requests` + `jsonschema`.

Build order (each is a separate script, each is idempotent):

1. `fetch_trackers.py` — Exodus `/api/trackers` plus `fp_trackers.json` (Fine Print's own, evidenced signatures)
   → the Android asset `android/app/src/main/assets/trackers.json` and the identical `../bundle/trackers.json` (ODbL).
   Always rebuild with `--from-file raw/exodus-api-trackers-<date>.json`; a live fetch is a deliberate, rare act.
2. `fetch_app.py <package>` — Exodus report, Play listing metadata, privacy-policy URL → `raw/` (gitignored).
3. `draft.py <package>` — LLM draft of tracker/app records with mandatory `sources`; refuses to emit
   a consequence with no source. Writes to `drafts/` (gitignored). Hand-run via Codex CLI for now.
4. `review.py` — opens the draft queue; approved records move into `reviewed/`.
5. `build.py` — merges `reviewed/*.json`, rejects duplicate ids, marks records older than 180 days
   `stale`, validates against `../bundle/schema.json`, cross-checks company, source and tracker ids,
   rejects any source without a `quote`, checks every `source_url` resolves (HTTP 200; a source may
   name a `verify_url` when the page itself blocks scripts), and writes `../bundle/bundle.json`. URL
   results are cached for 30 days in `raw/url-checks.json`. Use `--out <path>` for a preview and
   `--skip-url-check` offline. Tests: `python3 -m unittest test_build.py`. The evidence rules are in
   `../bundle/README.md`.

Serve for dev: `python3 -m http.server <port> --directory ../bundle` (see `android/README.md`).

## Test APKs: where they come from

APKs used to check detection (e.g. Life360 for `fp-arity`) are someone else's copyrighted code.
They stay outside the repo (the scratch area or `pipeline/raw/`, both untracked) and are never
launched, on the Mac or on the emulator; installing without launching keeps an app's code stopped.

1. Download with EFF's `apkeep` from APKPure, which has served modified apps before, so this is for
   research only: `apkeep -d apk-pure -o 'acknowledge_dangers=true,arch=arm64-v8a' -a <package>@<version> <dir>`.
2. Unzip the `.xapk`; the base APK is the `<package>.apk` inside it.
3. Verify before use: the base APK's SHA-256 must equal the one in the Exodus report for the same
   version (Exodus downloads from Google Play). If it doesn't match, delete it. Also run
   `apksigner verify --print-certs`.
4. Record the version, versionCode and SHA-256 in any evidence that cites the APK (see
   `fp_trackers.json`).

Recorded so far: Life360 26.37.0 (versionCode 2924500), base APK SHA-256
`e468187c69ffa8439dc0a55705e608ec83387e458d796d5a1ea4ad49bfa8af20`, matching Exodus report 785809.
