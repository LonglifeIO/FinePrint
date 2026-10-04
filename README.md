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

## Layout

- `bundle/` — the knowledge base: `schema.json` (the contract) and `bundle.json` (reviewed, sourced records). CC BY 4.0.
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

G1 signed off on an Android 17 emulator (2026-10-04): the scanner lists apps, permissions and
embedded tracker SDKs, and shows Arity in Life360. A real-device check is deferred by choice.
Results in `android/README.md`. See `CLAUDE.md` for the gates.

G2 signed off on the emulator (2026-10-04): the app downloads the bundle whole and explains each
app by data, every claim sourced. The Tailscale path gets tested when a second machine or a phone
is available. Life360 is the first reviewed record.

G3 in progress: the UI pass. The list is sorted by a published tier (`docs/METHOD.md`), every page
has the same sections with their definitions, each line's sources open in one sheet, and you can
mark an app reviewed (on this phone only). Tap targets are 48dp or more, enforced by a test.

Next: tracker records for common SDKs, so the auto view says more; reviewed records for Facebook,
TikTok and Google Maps; CI running the build and tests on every push. Later gates are in
`CLAUDE.md`.

## Licence

Code: AGPL-3.0-or-later. Data in `bundle/`: CC BY 4.0. Tracker signatures
(`android/app/src/main/assets/trackers.json`) are derived from the [εxodus](https://exodus-privacy.eu.org/)
tracker database and are under the Open Database License (ODbL) 1.0.
