# CLAUDE.md — FinePrint

Read this before touching anything.

Before planning, read ingest/ — notes compiled from videos the owner saved. Reference material, not decisions.

## What this is
A free, AGPLv3 Android app (iOS later) that scans installed apps and explains, per app,
where its data goes: embedded tracker SDKs → owning company → what they sell → why it
matters to the user, every claim sourced. Not a permission manager; the explanation
layer is the product.

Naming: the product is **FinePrint**, one word, everywhere (app label, screens, docs). The
scanning/detection module is **egress** (package `com.longlifeio.fineprint.egress`). Repo was
briefly "Argus" — if you see it, it means FinePrint.

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
  only if/when we build the local-VPN monitoring (F-Droid) variant — the observed-traffic gate.
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
- Consolidated or revised statutes: quote only text the editor marks in force, cite the section
  the words actually sit in, and record the amending act and commencement date where the
  annotation gives them; otherwise the consolidation's current-to date is the evidence it is in
  force. Deleted and not-yet-commenced text stays in the page and passes the quote check.
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
  with a tappable citation and a working "open settings" button. Signed off on the emulator
  (2026-10-04); the Tailscale path is tested when a second machine or a phone is available.
- **G3** — the UI pass: list sorted by published tiers, the same sections with definitions on
  every page, a Sources sheet, How to read this (`docs/METHOD.md`, word for word), 48dp tap
  targets, on-device Reviewed marks and the "What you can do" checklist.
- Next, in order: tracker records for common SDKs (they make the auto view useful); three more
  curated apps (Facebook, TikTok, Google Maps; drafts in `pipeline/drafts/`, same review gate as
  Life360); CI (a GitHub Action running assembleDebug and the pipeline tests on push).
- Later gates: observed traffic on a test device, which is also the fork-vs-thin decision for
  the VPN/F-Droid build; the QUERY_ALL_PACKAGES declaration on an internal Play track; the public
  website; Google developer verification; F-Droid inclusion.

## Conventions
- Timestamps: America/Halifax. Canadian spelling in user-facing text.
- Kotlin files under ~300 lines; plain functions over frameworks (no DI library,
  no Room). Python: stdlib + `requests` + `jsonschema`; keep it scripts, not a package.
- Debug builds may carry Compose's preview tooling (`ui-tooling`, `ui-tooling-preview`) for `@Preview`
  mockups; release builds never do. `checkReleaseClasspath` fails the build, and CI, if any Compose
  tooling reaches the release runtime classpath.
- Commit only reviewed records to `bundle/`. Add a pre-commit check that
  `bundle/bundle.json` validates and every `source_url` resolves.
- Don't estimate timelines in docs or commits. Next steps, blockers, dependencies.
- Exodus etiquette: Exodus Privacy is a volunteer non-profit whose data we depend on. Fetch
  the tracker list from the API endpoint (`/api/trackers`) once; `fetch_trackers.py` caches the
  response in `pipeline/raw/`, and every rebuild uses `--from-file`. Never crawl Exodus report
  pages, and never fetch from Exodus in parallel subagents. (Their limit is 3 requests per minute
  on that endpoint; a burst on 2026-10-02 got this Mac's IP blocked.)
- Before any push: `git fetch origin`, then `git merge-base --is-ancestor origin/main HEAD` must
  succeed. A push runs as its own command after the checks have been read, never chained with `;`.
- Never `--no-verify`, nor any other way round a hook (such as `core.hooksPath`), rebase conflict
  resolutions included. If a hook blocks a resolution, fix the content or stop and report.

## The watcher (`pipeline/watch.py`)
- It writes only under `pipeline/watch/`: queue items and a digest, never a record, a bundle file or anything
  in `pipeline/reviewed/`. Items state facts; they never suggest a status or a tier.
- `pipeline/watch/snapshots/`, `queue/`, `acked/`, `digest/` and `local.json` are gitignored and refused by the
  forbidden-files hook, as `pipeline/raw/` is: they hold third-party copies.
- It identifies itself: `FinePrint-watcher/<version> (+https://github.com/LonglifeIO/FinePrint; <contact>)`. The
  contact comes only from `pipeline/watch/local.json` and never appears in a tracked file (it is listed in
  `.git/private-patterns`, so the private-strings hook enforces it). Without a contact the watcher refuses to run.
- A refusal is a refusal: robots.txt is read for each host before any path, and a disallow or a 401, 403 or 451
  parks the URL and writes one digest line. No headless browser, no JavaScript, no retries with a different
  client, ever.
- A 401 or 403 on robots.txt counts as disallowing everything: stricter than RFC 9309's 4xx rule on purpose, because a block page is a refusal in plain words.
- Politeness: one request at a time per host, at least 5 s apart; conditional GETs (ETag, Last-Modified);
  `Retry-After` honoured; exponential backoff, three tries, then a `fetch_failure` item; after three failed runs
  a URL is parked until a person acks its item. 30 s timeout, 5 MB body cap.
- The denylist in `pipeline/watch/sources.json` is refused even when a record cites it:
  `play.google.com/store/apps/datasafety`, Play pages of any kind (the owner captures them by hand), `canlii.org`
  and Exodus.
- Python stdlib + `requests` + `jsonschema` only (RSS and Atom through `xml.etree`); scripts, not a package;
  files under ~300 lines; timestamps in America/Halifax.

## Do not
- Add network calls to the app beyond the bundle fetch.
- Send package names, permission grants, or detections off-device for any reason.
- Vendor TrackerControl/NetGuard code before the observed-traffic gate.
- Commit anything from `pipeline/raw/`, `pipeline/drafts/`, the watcher's ignored folders or `ingest/`
  (third-party pages and transcripts, and personal material).
- Commit keystores, `local.properties`, tokens, or `.env`.
- Never read other apps' screens: no Accessibility Service, no screenshots, no OCR, no overlay that
  captures input. FinePrint may draw over other apps (Guide mode, opt-in, per session) but never
  reads from them.
