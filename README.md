# FinePrint

Your phone already tells you *which* permissions an app has. FinePrint tells you
where the data goes once it has them.

For each installed app, FinePrint shows the third-party tracker SDKs embedded in
it, who owns those trackers, what they sell and to whom, and why that could matter
to you in real terms — with a source for every claim and a button to the settings
page where you can revoke access. The reference case: Life360 says "Location:
allowed." FinePrint says "driving data → Arity SDK → Allstate → sold to insurers
for risk scoring (alleged in a 2025 Texas AG lawsuit) — tap here to revoke."

No accounts. No telemetry. No server that ever learns what's on your phone: the
knowledge base is a single JSON file the app downloads whole and reads locally.
Android first; iOS via App Privacy Report import later. Free, open source, and
supported by exactly one hardcoded ad that knows nothing about you.

## Screenshots

<p>
  <img src="docs/screenshots/home-light.png" width="300" alt="The home: At a glance, where your apps' data can go, then the Flagged apps">
  <img src="docs/screenshots/detail-light.png" width="300" alt="Life360's page: its tier, its own Play description, and the fine print, each line with its source">
</p>

Drawn by the screenshot tests (`./gradlew readmeScreenshots`), never by hand, from knowledge bundle 2026.10.07
and the test emulator's apps. In dark mode: [the home](docs/screenshots/home-dark.png) and
[Life360](docs/screenshots/detail-dark.png).

## Layout

- `bundle/` — the knowledge base: `schema.json` (the contract), `bundle.json` (reviewed, sourced records) and `jurisdictions.json` (each country's laws for compelled access). CC BY 4.0.
- `pipeline/` — Python scripts that fetch sources, draft records with an LLM, queue them for human review, and build the bundle. Runs on a Mac Mini.
- `android/` — Kotlin / Jetpack Compose app. The scanning module is called `egress`.
- `prompts/` — Claude Code prompts for each build slice.

## Before committing

```sh
brew install pre-commit && pre-commit install   # pre-commit and pre-push hooks
pre-commit run --all-files
```

The hooks run `gitleaks`, block keystores, `local.properties`, `.env` and anything in
`pipeline/raw/` or `pipeline/drafts/`, and fail on macOS home-directory paths, Tailscale addresses
and host names, and mDNS host names (patterns in `tools/check_private.py`). Private strings such as your own
email go one per line in `.git/private-patterns`: that file is never committed, so the strings it
guards stay out of the repo too.

Owner's manual steps on GitHub: turn on secret scanning push protection (Settings → Code security),
and commit with the GitHub noreply address (`git config user.email <id>+<user>@users.noreply.github.com`).

## Status

G1 to G3 are signed off on an Android 17 emulator, and schema v1.3 is complete:

- G1, the scanner: installed apps, their permissions and the tracker SDKs in their code (Arity in
  Life360).
- G2, the knowledge bundle: downloaded whole, and a data-first explanation per app, every claim
  sourced. Reviewed records for Life360, Facebook, TikTok and Google Maps, with company records for
  the companies behind them.
- G3, the UI pass: tiers by a published formula (`docs/METHOD.md`), the same sections with their
  definitions on every page, a Sources sheet, How to read this, Reviewed marks and the "What you
  can do" checklist, with tap targets of 48dp or more enforced by a test.
- Schema v1.3: one tracker record covers several Exodus ids (`covers[]`); a record's history, with
  the direction of each change (`changes[]`); ongoing and past legal actions, where only ongoing
  or recent ones set a tier; government access, with a reviewed table of laws per country
  (`bundle/jurisdictions.json`) and where each company is based; a policy's region; flows that are
  off by default or opt-in; and apps that came with the phone showing their maker's policy lines.
- The laws table, reviewed entry by entry: eight laws in force (the US, Canada, China, the EU,
  Israel and Russia), each with who it binds, whether the company may tell you it handed data
  over, its own review date and a Stale marker; an EU regulation is keyed to the member states it
  binds. Laws that bind only licensed telecoms, or that permit disclosure rather than compel it,
  are parked as drafts.

A real-device check and the Tailscale path are deferred by choice. See `CLAUDE.md` for the gates.

Next, in order:

1. G5, a design pass: mockups of three directions first, in a debug-only source set; then the
   chosen direction on the production screens.
2. Tracker records for common SDKs (`kb/trackers`). Company records there need `jurisdiction` and
   `jurisdiction_sources` (schema v1.3) before they merge.
3. G6: guided paths and Guide mode.
4. A test on a phone.

Logged for G5 (not built yet):
- "Their words / the fine print". The detail screen's summary card opens with the app's Play Store
  short description, verbatim and attributed ("— Google Play listing, <date>"), with asterisk
  footnotes: each footnote is an existing sourced line from the record, in small type under it. The
  quoted text is never edited. Schema: `store_tagline` {text, source_url, as_of} on app records,
  fetched by the pipeline and quote-checked like everything else.
- Step 2, after a direction is picked: apply it to the production screens; a segmented progress
  bar, not a ring; home sections keep their expanded state; Reviewed shown as "Flagged ✓" in rows;
  an onboarding of three screens (what FinePrint does; the three buckets; "FinePrint relays the
  public record; it doesn't judge — nobody's telling you to uninstall anything"); screenshot tests
  (Roborazzi or the Compose Preview screenshot plugin) and `enableAccessibilityChecks()` in the
  instrumented tests; a TalkBack and 200%-font pass. A release checklist from the brief's
  anti-pattern list, added to `docs/METHOD.md`.

Logged for G6 (not built yet):
- Guided paths. Each in-app control gets `path.steps[]` (screen → the exact label to tap, verbatim
  from the company's help page → what it changes), shown inside FinePrint as swipeable step cards
  with generic grey wireframe thumbnails and the target row highlighted: no replicas of other apps'
  screens and no logos; the app's own icon from the device is the only brand mark. Each path ends
  in the real deep link or `settings_url`. Steps carry `help_url`, quotes under the usual rule and
  `verified_on` {app_version, date}; help-page changes go through the diff job and the stale flag.
  Fallback steps for screens that have changed ("If you don't see Privacy, tap Search in Settings
  and type 'privacy choices'"). First batch: the ten most useful controls across Google's ad
  settings, Meta's ad preferences and off-Facebook activity, TikTok's personalization and Life360's
  three.
- Guide mode. The same steps as a small, movable floating card (Next / Back / Done) over the real
  app while the user moves through it themselves. Draw-only: it needs only the overlay permission,
  reads nothing, captures nothing, has no input fields, and is opt-in per walkthrough, never
  persistent. Permission prompt: "This lets FinePrint show step cards over other apps. It cannot
  see what's on your screen." The deep link makes the first jump; the user moves on by tapping
  Next; Done, or going back to FinePrint, closes the card and offers to tick the control with
  today's date. How to read this explains why FinePrint's use of an overlay differs from the
  device-access warning about overlays. For Play: declare the overlay permission's purpose. Never
  in scope: a sandbox copy of another app with a test account, or any Accessibility-based
  highlighting.

Schema items queued: `settings_url` on controls; `store_tagline` {text, source_url, as_of} on app
records (quote-checked); `controls[].path.steps[]`, `help_url` and `verified_on`.

Queued: laws to review (not started):
- Ireland's domestic powers over companies based there: its e-Evidence implementing act and the
  warrant or production powers Gardaí use.
- US national law beyond the CLOUD Act and FISA: the Stored Communications Act, 18 U.S.C. § 2703
  (the everyday warrant, order and subpoena route for app data); national security letters,
  § 2709; FISA Title I, 50 U.S.C. § 1805(c)(2)(B).
- Israel, Arrest and Search Ordinance s. 43: add "companies included" to its scope, citing the
  Interpretation Law, 1981, s. 4 ("מקום שמדובר באדם – אף חבר-בני-אדם במשמע, בין שהוא תאגיד ובין
  שאינו תאגיד"). A text copy is now saved (Nevo, current to 2023-09-18); JSON first.

Logged for later:
- After the push, the owner decides: a fourth government line type for voluntary hand-over on
  request, applied evenly to every country (US 18 U.S.C. § 2702; GDPR arts. 6 and 48; Canada's
  PIPEDA s. 7(3)(c.1); the rest), or whether that belongs at the company-policy layer instead.
- After the push, the owner decides: court findings about a law as a line of their own under
  Jurisdictions, applied evenly to every law or not at all. Podchasov v. Russia (ECtHR, 2024: the
  decryption duty breached art. 8; Russia left the Convention in 2022 and the ruling doesn't change
  its law) for art. 10.1; Schrems II (CJEU, 2020) for FISA 702; whatever exists for the others.
- A "What matters to me" setting that lets the user choose which jurisdictions and ways of getting
  data to highlight (from the government-access plan; the rest shipped in v1.3).
- v2: data exports the user supplies (Facebook's "Download your information", Google Takeout),
  read on the phone to check the in-app settings they report, the same pattern as the iOS App
  Privacy Report import.

## Licence

Code: AGPL-3.0-or-later. Data in `bundle/`: CC BY 4.0. Tracker signatures
(`android/app/src/main/assets/trackers.json`) are derived from the [εxodus](https://exodus-privacy.eu.org/)
tracker database and are under the Open Database License (ODbL) 1.0.
