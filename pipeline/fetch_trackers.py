#!/usr/bin/env python3
"""Fetch Exodus Privacy's tracker list and write the Android app's bundled signature asset.

    python3 pipeline/fetch_trackers.py

Writes android/app/src/main/assets/trackers.json, which is committed and shipped inside the APK.
The app never calls the Exodus API itself; this script is the only thing that does.

FinePrint's own signatures (ids "fp-<slug>", for trackers Exodus does not list) are merged in
from pipeline/fp_trackers.json when that file exists. Every entry there must cite its evidence.

Licence: the εxodus database is ODbL 1.0 (contents DbCL 1.0), so the asset is a Derivative
Database under the ODbL, whatever the rest of the repository is licensed under. The notice is
written into the file and the app shows it next to tracker names.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import sys
import tempfile
from pathlib import Path
from zoneinfo import ZoneInfo

import requests

API_URL = "https://reports.exodus-privacy.eu.org/api/trackers"
REPO = Path(__file__).resolve().parent.parent
DEFAULT_OUT = REPO / "android" / "app" / "src" / "main" / "assets" / "trackers.json"
BUNDLE_OUT = REPO / "bundle" / "trackers.json"  # served with bundle.json; same content as the asset
DEFAULT_FP = REPO / "pipeline" / "fp_trackers.json"
RAW = REPO / "pipeline" / "raw"  # gitignored
HALIFAX = ZoneInfo("America/Halifax")
# Exodus asks API users to set their own User-Agent, and limits this endpoint to 3 requests a minute.
USER_AGENT = "FinePrint-pipeline (+https://github.com/LonglifeIO/FinePrint)"
LICENCE = "ODbL-1.0"
LICENCE_URL = "https://opendatacommons.org/licenses/odbl/1-0/"
ATTRIBUTION = (
    "Contains information from the εxodus tracker database (https://reports.exodus-privacy.eu.org/), "
    "made available under the Open Database License (ODbL) 1.0; individual contents under the "
    "Database Contents License (DbCL) 1.0."
)

# Signatures made only of these characters (literal text, '.' wildcards, '|' alternation) take the
# app's fast matcher. Anything else still works on-device but falls back to java.util.regex.
SIMPLE_SIGNATURE = re.compile(r"^[A-Za-z0-9_.|-]*$")
FP_ID = re.compile(r"^fp-[a-z0-9-]+$")


def fetch(timeout: float) -> dict:
    resp = requests.get(
        API_URL,
        timeout=timeout,
        headers={"Accept": "application/json", "User-Agent": USER_AGENT},
    )
    resp.raise_for_status()
    return resp.json()


def check_tracker(where: str, name: object, signature: object, categories: object) -> None:
    if not isinstance(name, str) or not name.strip():
        raise ValueError(f"{where}: missing name")
    if not isinstance(signature, str):
        raise ValueError(f"{where}: code_signature is not a string")
    if signature:
        re.compile(signature)  # a broken regex would silently never match on-device
    if not isinstance(categories, list) or not all(isinstance(c, str) for c in categories):
        raise ValueError(f"{where}: categories is not a list of strings")


def convert_exodus(payload: dict) -> list[dict]:
    raw = payload.get("trackers")
    if not isinstance(raw, dict) or not raw:
        raise ValueError("unexpected API response: no 'trackers' object")
    trackers = []
    for key, t in raw.items():
        tid = t.get("id")
        if not isinstance(tid, int) or str(tid) != key:
            raise ValueError(f"tracker {key!r}: id {tid!r} does not match its key")
        if "code_signature" not in t:  # a renamed field must not turn into "no signatures at all"
            raise ValueError(f"tracker {key}: no code_signature field; has the API changed?")
        signature = t["code_signature"] or ""
        categories = t.get("categories") or []
        check_tracker(f"exodus-{tid}", t.get("name"), signature, categories)
        trackers.append({
            "id": f"exodus-{tid}",
            "name": t["name"].strip(),
            "code_signature": signature,
            "categories": categories,
            "website": t.get("website") or "",
        })
    trackers.sort(key=lambda t: int(t["id"].split("-", 1)[1]))
    return trackers


def load_fp(path: Path) -> list[dict]:
    if not path.exists():
        return []
    entries = json.loads(path.read_text(encoding="utf-8")).get("trackers", [])
    trackers = []
    for e in entries:
        tid = e.get("id", "")
        if not FP_ID.match(tid):
            raise ValueError(f"{path.name}: id {tid!r} must look like fp-<slug>")
        check_tracker(tid, e.get("name"), e.get("code_signature"), e.get("categories", []))
        if not e.get("code_signature"):
            raise ValueError(f"{path.name}: {tid} has no code_signature")
        if not e.get("evidence"):
            raise ValueError(f"{path.name}: {tid} cites no evidence")
        trackers.append({
            "id": tid,
            "name": e["name"].strip(),
            "code_signature": e["code_signature"],
            "categories": e.get("categories", []),
            "website": e.get("website", ""),
        })
    return trackers


def write_atomically(path: Path, doc: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=path.parent, prefix=".trackers-", suffix=".json")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(doc, f, indent=1, ensure_ascii=False)
            f.write("\n")
        os.chmod(tmp, 0o644)  # mkstemp creates 0600; this is a normal, committed file
        os.replace(tmp, path)
    except BaseException:
        os.unlink(tmp)
        raise


def usable(trackers: list[dict]) -> list[dict]:
    """Trackers the app can detect: exodus-core ignores signatures of 3 characters or fewer."""
    return [t for t in trackers if len(t.get("code_signature") or "") > 3]


def existing_usable_count(path: Path) -> int:
    try:
        return len(usable(json.loads(path.read_text(encoding="utf-8")).get("trackers", [])))
    except (OSError, ValueError):
        return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT, help="app asset path to write")
    parser.add_argument("--bundle-out", type=Path, default=BUNDLE_OUT, help="bundle copy to write")
    parser.add_argument("--fp", type=Path, default=DEFAULT_FP, help="FinePrint's own signatures")
    parser.add_argument("--timeout", type=float, default=60.0, help="HTTP timeout in seconds")
    parser.add_argument("--force", action="store_true",
                        help="write even if the number of usable signatures dropped by more than 10%%")
    parser.add_argument("--from-file", type=Path,
                        help="build from a saved /api/trackers response instead of fetching one")
    args = parser.parse_args()

    if args.from_file:
        payload = json.loads(args.from_file.read_text(encoding="utf-8"))
        fetched_at = dt.datetime.fromtimestamp(args.from_file.stat().st_mtime, HALIFAX)
    else:
        try:
            payload = fetch(args.timeout)
        except requests.RequestException as e:
            print(f"could not fetch {API_URL}: {e}", file=sys.stderr)
            return 1
        fetched_at = dt.datetime.now(HALIFAX)
        # Keep the raw response so every later build can use --from-file instead of the API.
        RAW.mkdir(parents=True, exist_ok=True)
        cached = RAW / f"exodus-api-trackers-{fetched_at:%Y-%m-%d}.json"
        cached.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
        print(f"cached the API response at {cached}; rebuild with --from-file {cached}")
    trackers = convert_exodus(payload) + load_fp(args.fp)
    ids = [t["id"] for t in trackers]
    repeated = sorted({i for i in ids if ids.count(i) > 1})
    if repeated:  # the app keys tracker rows by id
        raise ValueError(f"duplicate tracker ids: {repeated}")

    with_code = usable(trackers)
    before = existing_usable_count(args.out)
    if not with_code or (len(with_code) < 0.9 * before and not args.force):
        print(f"refusing to write {args.out}: {len(with_code)} usable signatures, down from {before}."
              " Check the API response; rerun with --force if the drop is real.", file=sys.stderr)
        return 1
    slow = [t["id"] for t in with_code if not SIMPLE_SIGNATURE.match(t["code_signature"])]
    # An empty or all-'.' alternative ("foo|", "a||b") would make Exodus's regex match nearly every
    # class. The app skips such alternatives instead; flag them so a human looks at the data.
    degenerate = [t["id"] for t in with_code if t["id"] not in slow
                  and any(not alt.strip(".") for alt in t["code_signature"].split("|"))]

    doc = {
        "source": API_URL,
        "fetched_at": fetched_at.isoformat(timespec="seconds"),
        "licence": LICENCE,
        "licence_url": LICENCE_URL,
        "attribution": ATTRIBUTION,
        "trackers": trackers,
    }
    write_atomically(args.out, doc)
    write_atomically(args.bundle_out, doc)
    print(f"wrote {args.bundle_out}")
    fp_count = sum(1 for t in trackers if t["id"].startswith("fp-"))
    print(f"wrote {args.out} ({args.out.stat().st_size:,} bytes)")
    print(f"  {len(trackers) - fp_count} Exodus trackers + {fp_count} FinePrint trackers;"
          f" {len(with_code)} have a usable code signature (was {before})")
    if slow:
        print(f"  regex fallback on-device for: {', '.join(slow)}")
    if degenerate:
        print(f"  WARNING: empty or all-wildcard alternatives (skipped on-device) in: {', '.join(degenerate)}")
    print("  next: cd android && ./gradlew testDebugUnitTest, then commit the asset")
    return 0


if __name__ == "__main__":
    sys.exit(main())
