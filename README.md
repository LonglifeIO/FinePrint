# FinePrint

**Status: pre-release.** There is no released build; test builds go out by invitation. The
[preview build](#preview-build) is a CI artifact for browsing the design — it scans nothing.

FinePrint shows, for each app on your phone, what data it collects, who gets it, and whether it's used
beyond running the app. Android shows which permissions an app has. FinePrint shows where the data can go.

- **Sourced and quoted.** Each claim cites a dated source and quotes the words that support it. A person
  checks it against that source before it reaches the app. A claim FinePrint infers from tracker code
  alone carries the badge Auto: no person has checked it.
- **The companies' own words come first.** Where FinePrint has it, an app's page opens with Their words:
  the app's own short description on Google Play, word for word. Its privacy policy is quoted, not
  paraphrased.
- **A tier describes the data, not the app.** Flagged, Caution and Expected follow
  [published rules](docs/METHOD.md#tiers), the same for every app.
- **No advice to uninstall.** FinePrint shows the settings that limit where data goes.
- **Open data.** Every claim FinePrint has checked, with its sources, is under CC BY 4.0.

Take Life360. Android shows that it can use your location. FinePrint's page for it reads: *Why it's
Flagged: Life360 says your location goes to other companies.* According to Life360's privacy policy
dated 2026-08-26, "select business partners" can use your precise location for "their own monetization
purposes".

The Texas Attorney General sued Allstate and its subsidiary Arity on 2025-01-13. The Attorney General
alleged that they collected driving data through "secretly embedded software in mobile apps, such as
Life360" (not proven in court). Life360 is not a defendant. Part of the case was dismissed on
jurisdiction grounds and is under appeal.

Under What you can do, FinePrint names the settings that limit that sharing. One is Trusted Partners, in
the Privacy section of Life360's account settings. FinePrint can't change it. You tick it once you've
changed it yourself.

No accounts, no analytics, no crash reporting, no ads and no trackers. FinePrint checks your apps on your
phone, and nothing about them leaves it. It downloads its knowledge bundle whole, so no server learns
which apps you have. Android first; iOS later, by importing the iPhone's App Privacy Report. Free and
open source.

## Screenshots

<p>
  <img src="docs/screenshots/home-light.png" width="300" alt="The home: At a glance, where your apps' data can go, then the Flagged apps">
  <img src="docs/screenshots/detail-light.png" width="300" alt="Life360's page: its tier and why, its own Play description, and the fine print, what set the tier first, each line with its source">
</p>

Drawn by the screenshot tests (`./gradlew readmeScreenshots`), never by hand, from bundle 2026.10.09 and the
scan fixture. In dark mode: [the home](docs/screenshots/home-dark.png) and [Life360](docs/screenshots/detail-dark.png).

## How it works

Three parts, with `bundle/schema.json` as the contract between them.

**The app** (`android/`, Kotlin and Jetpack Compose). Its scanning module, `egress`, lists your apps and their
permissions. It reads each app's dex files with dexlib2. It matches the names of the classes they define against
tracker signatures: εxodus's, plus FinePrint's own, such as Arity's. Classes an app only references don't count.
So a match means the tracker's code is in the app, not that data was sent. The app joins the scan to the bundle on
the phone and sets each tier by the rules in `docs/METHOD.md`.

**The bundle** (`bundle/`). `bundle.json` holds the checked, sourced records of apps, companies and trackers.
`jurisdictions.json` holds each country's laws that let its government compel data, and `trackers.json` the
tracker signatures. The app downloads all three files whole.

**The pipeline** (`pipeline/`, Python scripts on a Mac Mini). An LLM drafts the records, with a source for every
claim. A person checks each one before it moves to `pipeline/reviewed/`. `check_quotes.py` matches every quote
against a saved copy of its page. `build.py` validates the records against the schema, checks that every source
URL resolves, and writes the bundle. The watcher (`watch.py`) re-reads every quoted page weekly and two
regulators' feeds daily, and queues what changed for a person to check. It never edits a record. The rules are in
[How records are made](docs/METHOD.md#how-records-are-made).

`docs/METHOD.md` is How to read this, which the app ships word for word. `docs/` also holds FinePrint's privacy
page, the guide for invited testers and the [roadmap](docs/ROADMAP.md). `prompts/` holds the Claude Code prompts
for each build slice. CI builds both flavours of the app, `device` (the app itself) and `preview`, and runs the
pipeline and JVM tests on every push.

## Preview build

A debug build for browsing the design on any phone. It installs beside the real app as FinePrint preview, and
shows the scan fixture's sample apps with the bundle built in. It scans nothing and makes no network calls: its
manifest has no internet or package-query permission.

- Build it: `cd android && ./gradlew assemblePreviewDebug`. The APK is in
  `android/app/build/outputs/apk/preview/debug/`.
- Or download it: each CI run keeps the `fineprint-preview` artifact for 30 days, on the run's page under
  Actions (signed-in GitHub users only). It's never attached to a release.
- Install it: open the APK on the phone and allow unknown apps when asked, or `adb install` it. It's
  debug-signed, so Play Protect warns that it comes from an unknown developer.

## Before committing

```sh
brew install pre-commit && pre-commit install   # pre-commit and pre-push hooks
pre-commit run --all-files
```

The hooks run gitleaks and refuse keystores, `local.properties`, `.env` and anything in `pipeline/raw/`,
`pipeline/drafts/` or the watcher's ignored folders. They also fail on home-directory paths, Tailscale addresses
and local host names (`tools/check_private.py`). Put your own private strings, such as your email, one per line
in `.git/private-patterns`, which is never committed. Commit with your GitHub noreply address.

## Status

G1 to G3 are signed off on an Android 17 emulator. Built so far:

- the scanner, with εxodus's signatures and FinePrint's own;
- the bundle, at schema 1.8: checked records for Life360, Facebook, TikTok and Google Maps, 24 tracker records,
  26 company records and the laws of ten places;
- the UI, design and reading passes;
- the watcher;
- guided paths for Life360's three settings, from its help page (G6, slice 1).

Next: the tracker batches' last follow-ups, the rest of the guided paths (G6), then a signed release. The list in
order, and what's logged for later, is in [docs/ROADMAP.md](docs/ROADMAP.md).

## Licence

Code: AGPL-3.0-or-later. Data in `bundle/`: CC BY 4.0 (`bundle.json` and `jurisdictions.json`). Tracker
signatures (`bundle/trackers.json`, copied into the app as `android/app/src/main/assets/trackers.json`) are
derived from the [εxodus](https://exodus-privacy.eu.org/) tracker database and are under the Open Database
License (ODbL) 1.0. FinePrint is not affiliated with or endorsed by Exodus Privacy.
