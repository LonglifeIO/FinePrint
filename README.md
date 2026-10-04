# Fine Print

Your phone already tells you *which* permissions an app has. Fine Print tells you
where the data goes once it has them.

For each installed app, Fine Print shows the third-party tracker SDKs embedded in
it, who owns those trackers, what they sell and to whom, and why that could matter
to you in real terms — with a source for every claim and a button to the settings
page where you can revoke access. The reference case: Life360 says "Location:
allowed." Fine Print says "driving data → Arity SDK → Allstate → sold to insurers
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

## Status

G1 signed off on an Android 17 emulator (2026-10-04): the scanner lists apps, permissions and
embedded tracker SDKs, and shows Arity in Life360. A real-device check is deferred by choice.
Results in `android/README.md`. See `CLAUDE.md` for the gates.

## Licence

Code: AGPL-3.0-or-later. Data in `bundle/`: CC BY 4.0. Tracker signatures
(`android/app/src/main/assets/trackers.json`) are derived from the [εxodus](https://exodus-privacy.eu.org/)
tracker database and are under the Open Database License (ODbL) 1.0.
