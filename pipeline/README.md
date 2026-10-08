# pipeline/

Runs on the Mac Mini. Scripts, not a package. Python 3.12+, stdlib + `requests` + `jsonschema`.

Build order (each is a separate script, each is idempotent):

1. `fetch_trackers.py` — Exodus `/api/trackers` plus `fp_trackers.json` (FinePrint's own, evidenced signatures)
   → the Android asset `android/app/src/main/assets/trackers.json` and the identical `../bundle/trackers.json` (ODbL).
   Always rebuild with `--from-file raw/exodus-api-trackers-<date>.json`; a live fetch is a deliberate, rare act.
2. `fetch_app.py <package>` — Exodus report, Play listing metadata, privacy-policy URL → `raw/` (gitignored).
3. `draft.py <package>` — LLM draft of tracker/app records with mandatory `sources`; refuses to emit
   a consequence with no source. Writes to `drafts/` (gitignored). Hand-run via Codex CLI for now.
4. `review.py` — opens the draft queue; approved records move into `reviewed/`.
5. `build.py` — merges `reviewed/*.json`, rejects duplicate ids, marks records older than 180 days
   `stale`, validates against `../bundle/schema.json`, cross-checks company, source and tracker ids
   (a tracker record may cover several ids; each id has one explanation at most),
   rejects any source without a `quote`, checks every `source_url` resolves (HTTP 200, not reached by a
   redirect to a not-found page; a source may name a `verify_url` when the page itself blocks scripts), and writes `../bundle/bundle.json`. URL
   results are cached for 30 days in `raw/url-checks.json`. Use `--out <path>` for a preview and
   `--skip-url-check` offline. A new entry in an app's `changes[]` (date, text, sources) gets its
   direction from `--previous <file>`, the record as it was before the change (for example
   `git show HEAD:pipeline/reviewed/app-life360.json > /tmp/old.json`): build.py diffs the two,
   writes `diff` and `direction` into the reviewed file for review, and on every build checks
   that each direction still follows from its diff. Tests: `python3 -m unittest test_build.py`. The evidence rules are in
   `../bundle/README.md`.

Serve for dev: `python3 -m http.server <port> --directory ../bundle` (see `android/README.md`).

## The watcher

`watch.py` notices change at the pages the reviewed records quote and at regulators' feeds, and puts it in
front of a person: queue items in `watch/queue/` and a digest a day in `watch/digest/`. It never writes a
record, a bundle file or anything in `reviewed/`; what it finds reaches the app only through review and a
rebuild. The rules it keeps are in `../CLAUDE.md` ("The watcher").

    python3 pipeline/watch.py poll [--adapter quote_drift|rss] [--dry-run]   # what's due; writes today's digest
    python3 pipeline/watch.py digest [--date YYYY-MM-DD]
    python3 pipeline/watch.py ack ITEM_ID [--note TEXT]                       # done with an item; it moves to acked/
    python3 pipeline/watch.py status                                           # open items, parked URLs, the last check

- `quote_drift` (`watch_quotes.py`), every 7 days: each page a reviewed record quotes, at its `verify_url` when
  that is the publisher's own copy, otherwise at its `url` (an archived copy never changes). Each quote is
  found with `check_quotes.py`'s own matching. Items: `quote_missing`; `context_changed` when the quote's
  paragraph, or a neighbouring paragraph that reads as a full sentence (terminal punctuation, 40 characters or
  more), changed, so related links and sidebars don't count; and `url_moved`. Every item shows the text within
  300 characters of the quote, before and now. A page's first check compares with the copy `fetch_sources.py`
  saved when the quote was verified. A copy the shared extractor can't read parks its URL with a note, and
  `sources` in `watch/sources.json` can give a page its own `max_bytes` (5 MB otherwise).
- `rss` (`watch_rss.py`), daily: the OPC's "Investigations into businesses" and BC OIPC's "Rulings and
  Reports". An entry naming a recorded company, app or tracker is a `new_event`. The FTC's feed is not watched:
  ftc.gov refuses the watcher (see `watch/sources.json`).
- Failures: `fetch_failure` on a URL's first failed run, `parked` after three in a row or at once on a
  robots.txt disallow (a 401 or 403 on robots.txt itself counts as one), a 401, 403 or 451, or a redirect to
  a not-found page (a refusal, not a move), and `refused` for the denylist. A parked URL is checked again
  once its item is acked; a host that refuses for good belongs on the denylist instead. Every digest ends
  with the parked and denylisted sources, for checking by hand before each release.
- Items follow `watch/queue-item.schema.json`: facts, never a status or a tier. Each finding is written once.
- `watch/sources.json` (committed) holds the adapters, feeds, cadences and denylist. `watch/local.json`
  (gitignored) holds `{"contact": "<email or URL>", "copy_to": "<folder>"}`; the contact goes in every
  request's User-Agent, and without it the watcher refuses to run. `copy_to` is optional (Hermes reads the
  digest there). `snapshots/`, `queue/`, `acked/` and `digest/` are gitignored: they hold other people's pages.

The daily run is a launchd template, `watch/launchd/com.longlifeio.fineprint.watch.plist` (06:15 each day);
it isn't installed by anything. To install it, from the repository's folder:

```sh
sed "s|__FINEPRINT__|$PWD|g" pipeline/watch/launchd/com.longlifeio.fineprint.watch.plist \
  > ~/Library/LaunchAgents/com.longlifeio.fineprint.watch.plist
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.longlifeio.fineprint.watch.plist
launchctl print gui/$(id -u)/com.longlifeio.fineprint.watch | head   # loaded?
```

`gui/` needs someone logged in at the Mac; on a Mac nobody logs in to, use `user/$(id -u)` in both commands.
The run's output goes to `watch/digest/launchd.log`. `/usr/bin/python3` needs `requests` and `jsonschema`.
To remove it: `launchctl bootout gui/$(id -u)/com.longlifeio.fineprint.watch`, then delete the file.

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
