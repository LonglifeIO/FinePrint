# FinePrint's own tracker signatures (`fp-*`)

Signatures for trackers the Exodus list lacks. They live in `pipeline/fp_trackers.json`, are merged
into `trackers.json` by `pipeline/fetch_trackers.py`, and follow Exodus's rules: a regex searched in
slash-separated class names (`com/foo/Bar`), where `.` matches any character. A hit is evidence
tier `contains_code` only. Like the rest of `trackers.json`, these entries are ODbL 1.0.

## fp-arity — Arity (Allstate Corporation)

- **Signature:** `com.arity.coreengine.|com.arity.coreEngine.`
- **Class prefixes matched:** `com/arity/coreengine/…` (current builds) and `com/arity/coreEngine/…`
  (capital E, Life360 19.8.0 to 23.27.0). The broader `com.arity.` is avoided on purpose: an
  unrelated developer, Arity Infoway, publishes apps under `com.arity.arityhrmpro` and
  `com.arity.newshunt`.
- **Other names (`aka` in bundle.json), checked against the matched classes (2026-10-04, same APK):**
  - "Arity CoreEngine": the classes' own names, e.g. `com/arity/coreengine/driving/CoreEngineManager`
    and `CoreEngineForegroundService`.
  - "Arity Driving Engine SDK": the Texas AG petition's name for the SDK. Inside the prefix,
    `com/arity/coreengine/driving/d` logs "Driving Engine started!" and `CoreEngineManager` logs
    "insertLog can be called only from Arity SDK".
- **How it was verified (2026-10-02):** Life360 26.37.0 (versionCode 2924500), base APK
  SHA-256 `e468187c69ffa8439dc0a55705e608ec83387e458d796d5a1ea4ad49bfa8af20`, the same file
  Exodus report 785809 analysed from Google Play.
  - It defines 642 classes under `com/arity/coreengine` and `com/arity/sensor`.
  - Its manifest registers `com.arity.coreengine.driving.CoreEngineForegroundService`.
  - Its dex strings include "Driving Logs. Arity SDK is started".
  - Across eight signature-verified APKs (Life360 builds from 2020 to 2026, MyRadar 8.74.0,
    Arity's own Routely 5.4.0; about 344,000 classes) the signature matched every Arity build and
    nothing outside `com/arity`.
- **Network hosts seen in the SDK code (for a future network signature):** `api.arity.com`,
  `tracking.arity.com`. Not yet observed in traffic.

## TODO

- Contribute `fp-arity` upstream as an Exodus tracker proposal (ETIP, https://etip.exodus-privacy.eu.org/),
  with the evidence above, so Exodus reports can show it too. Once accepted, switch to its
  `exodus-<n>` id and drop the `fp-` entry.
