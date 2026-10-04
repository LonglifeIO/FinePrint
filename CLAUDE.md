# CLAUDE.md — Fine Print

Read this before touching anything.

## What this is
A free, AGPLv3 Android app (iOS later) that scans installed apps and explains, per app,
where its data goes: embedded tracker SDKs → owning company → what they sell → why it
matters to the user, every claim sourced. Not a permission manager; the explanation
layer is the product.

Naming: product is **Fine Print**. The scanning/detection module is **egress**
(package `com.longlifeio.fineprint.egress`). Repo was briefly "Argus" — if you see it,
it means Fine Print.

## Architecture (three parts, one contract)
- `bundle/schema.json` is the contract between pipeline and app. Change it only with
  a deliberate version bump and matching changes on both sides.
- `pipeline/` (Python, runs on the Mac Mini): fetch → extract → LLM draft with mandatory
  citations → human review queue → `build.py` validates against the schema, checks every
  `source_url` resolves, writes `bundle/bundle.json`.
- `android/` (Kotlin, Compose, Material 3, single module, minSdk 29): enumerates apps
  and permissions, detects tracker SDKs by scanning dex class names against bundled
  Exodus signatures, joins results to the bundle, renders explanations, deep-links to
  `ACTION_APPLICATION_DETAILS_SETTINGS`.

## Decisions already made (don't relitigate)
- The app downloads the **whole** bundle file; it never queries a server per app.
  Per-app lookups would reveal the user's installed apps. Dev: served over Tailscale
  from the Mac Mini; prod: a GitHub release or CDN. Same shape either way.
- No backend, no accounts, no analytics, no crash reporting, no third-party SDKs
  beyond AndroidX and dexlib2. The only network call the app ever makes is fetching
  the bundle.
- Thin app, not a TrackerControl fork, for the static-scan build. Fork TrackerControl
  only if/when we build the local-VPN monitoring (F-Droid) variant — Gate G3.
- Play build is static-scan only (no VpnService). F-Droid build may add live monitoring.
- LLM in the app: none by default. If ever added, opt-in, on-device first.
- Licence: code AGPL-3.0-or-later; `bundle/` data CC BY 4.0, except Exodus-derived tracker data,
  which is ODbL 1.0 (see `bundle/README.md`).

## Evidence standards for `bundle/`
- Every consequence statement has a `source_url` and a `status`: `self_disclosed`
  (the app's own policy/labels), `reported` (journalism/research), `alleged`
  (complaint or lawsuit, not adjudicated), `adjudicated` (ruling, settlement, order).
- Wording follows status: "according to its privacy policy dated …", "reported by …",
  "alleged in a lawsuit filed by … on … (not proven in court)".
- Findings carry an `evidence_tier`: `contains_code` (SDK present in APK) vs
  `observed_contact` (traffic actually seen). Never upgrade one into the other.
- Nothing lands in `bundle/bundle.json` without human review. Drafts live in
  `pipeline/drafts/` (gitignored). Raw fetched policies live in `pipeline/raw/`
  (gitignored — copyrighted).

## Gates (owner decides; don't skip)
- **G0** — TrackerControl from F-Droid on the dev phone, watching Life360: confirm the
  Arity SDK (or its traffic) is detectable at all. If not, the static-scan premise is
  weak and the VPN path moves up.
- **G1** — thin app lists apps, permissions, detected trackers; Life360 shows Arity.
  Prompt: `prompts/g1-android-scanner.md`.
- **G2** — bundle fetched over Tailscale; Life360 screen renders the insurance chain
  with a tappable citation and a working "open settings" button.
- **G3** — fork-vs-thin decision for the VPN/F-Droid build.
- Later: QUERY_ALL_PACKAGES declaration on an internal Play track; Google developer
  verification; F-Droid inclusion.

## Conventions
- Timestamps: America/Halifax. Canadian spelling in user-facing text.
- Kotlin files under ~300 lines; plain functions over frameworks (no DI library,
  no Room). Python: stdlib + `requests` + `jsonschema`; keep it scripts, not a package.
- Commit only reviewed records to `bundle/`. Add a pre-commit check that
  `bundle/bundle.json` validates and every `source_url` resolves.
- Don't estimate timelines in docs or commits. Next steps, blockers, dependencies.

## Do not
- Add network calls to the app beyond the bundle fetch.
- Send package names, permission grants, or detections off-device for any reason.
- Vendor TrackerControl/NetGuard code before G3.
- Commit anything from `pipeline/raw/` or `pipeline/drafts/`.
- Commit keystores, `local.properties`, tokens, or `.env`.
