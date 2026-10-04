#!/usr/bin/env python3
"""Build bundle/bundle.json from the human-reviewed records in pipeline/reviewed/.

    python3 pipeline/build.py                 # validate, check every source URL, write bundle/bundle.json
    python3 pipeline/build.py --out preview.json --skip-url-check   # dry runs

Every reviewed file is a JSON object holding any of the arrays apps, trackers, companies,
permissions, device_reach; they are merged, validated against bundle/schema.json, cross-checked
(company ids, derives_from ids, tracker ids against bundle/trackers.json and one explanation per
tracker id, a quote on every source, one definition per source id, qualified regulatory_action
tags), and every source URL must answer
HTTP 200, or its verify_url when the page blocks scripts (an OK result is cached in pipeline/raw/
for 30 days). Exodus pages are never fetched (see CLAUDE.md, Exodus etiquette). Nothing reaches
bundle.json without review.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import sys
import tempfile
from pathlib import Path
from urllib.parse import urlparse
from zoneinfo import ZoneInfo

import jsonschema
import requests

REPO = Path(__file__).resolve().parent.parent
REVIEWED = REPO / "pipeline" / "reviewed"
SCHEMA = REPO / "bundle" / "schema.json"
TRACKERS = REPO / "bundle" / "trackers.json"
DEFAULT_OUT = REPO / "bundle" / "bundle.json"
URL_CACHE = REPO / "pipeline" / "raw" / "url-checks.json"  # gitignored
HALIFAX = ZoneInfo("America/Halifax")
SECTIONS = ("apps", "trackers", "companies", "permissions", "device_reach")
STALE_DAYS = 180
URL_CACHE_DAYS = 30
NOT_FETCHED_HOSTS = ("exodus-privacy.eu.org",)
# Vendor archive pages that block scripts, and the API address of the same article.
VENDOR_VERIFY_URLS = {
    "https://legal.corp.life360.com/hc/en-us/articles/40254028461463-Life360-Privacy-Policy-previous-to-May-5-2026":
        "https://legal.corp.life360.com/api/v2/help_center/en-us/articles/40254028461463.json",
}
USER_AGENT = "FinePrint-pipeline (+https://github.com/LonglifeIO/FinePrint)"


def merge(paths: list[Path]) -> dict[str, list]:
    merged: dict[str, list] = {k: [] for k in SECTIONS}
    for path in paths:
        doc = json.loads(path.read_text(encoding="utf-8"))
        unknown = set(doc) - set(SECTIONS)
        if unknown:
            raise ValueError(f"{path.name}: unknown sections {sorted(unknown)}")
        for key in SECTIONS:
            merged[key].extend(doc.get(key, []))
    for key, id_field in (("apps", "package_id"), ("trackers", "id"), ("companies", "id"),
                          ("permissions", "id"), ("device_reach", "id")):
        ids = [r[id_field] for r in merged[key]]
        repeated = sorted({i for i in ids if ids.count(i) > 1})
        if repeated:
            raise ValueError(f"duplicate {key}: {repeated}")
    return merged


def mark_stale(apps: list[dict], today: dt.date) -> None:
    """stale = last reviewed more than STALE_DAYS before the build."""
    for app in apps:
        reviewed = dt.date.fromisoformat(app["last_reviewed"])
        app["stale"] = (today - reviewed).days > STALE_DAYS


def walk(node, visit) -> None:
    if isinstance(node, dict):
        visit(node)
        for value in node.values():
            walk(value, visit)
    elif isinstance(node, list):
        for value in node:
            walk(value, visit)


def cross_check(bundle: dict, tracker_ids: set[str]) -> list[str]:
    errors = []
    companies = {c["id"] for c in bundle.get("companies", [])}
    sources_by_id: dict[str, dict] = {}
    redefined: set[str] = set()

    def collect(n: dict) -> None:  # a source id names one source; every copy must match it exactly
        if "url" in n and "id" in n and sources_by_id.setdefault(n["id"], n) != n:
            redefined.add(n["id"])

    walk(bundle, collect)
    errors += [f"source id {i!r} is defined differently in two places" for i in sorted(redefined)]

    def visit(n: dict) -> None:
        for key in ("recipient", "subject_company", "owner_company", "developer_company", "parent"):
            if key in n and isinstance(n[key], str) and n[key].startswith("co-") and n[key] not in companies:
                errors.append(f"unknown company {n[key]!r} in {key}")
        if "derives_from" in n and n["derives_from"] not in sources_by_id:
            errors.append(f"derives_from {n['derives_from']!r} has no source with that id")
        if "url" in n and not str(n.get("quote", "")).strip():
            errors.append(f"source without a quote: {n['url']}")

    walk(bundle, visit)
    for app in bundle["apps"]:
        errors += [f"{app['package_id']}: tracker {t!r} not in trackers.json" for t in app["trackers"] if t not in tracker_ids]
        errors += risk_tag_problems(app)
        errors += control_problems(app)
        errors += [f"{app['package_id']}: in_owner_apps is for tracker records" for f in app.get("data_flows", []) if "in_owner_apps" in f]
    errors += tracker_problems(bundle["trackers"], tracker_ids)
    return errors


def tracker_problems(trackers: list[dict], tracker_ids: set[str]) -> list[str]:
    """A record explains the tracker with its own id, or every id it covers (then its own id may be
    FinePrint's alone); each tracker id has one explanation at most."""
    errors, explained = [], {}
    for t in trackers:
        keys = t.get("covers", []) + [t["id"]]
        missing = [k for k in (t.get("covers") or [t["id"]]) if k not in tracker_ids]
        errors += [f"tracker record {t['id']!r}: {k!r} not in trackers.json" for k in missing]
        for key in keys:
            if explained.setdefault(key, t["id"]) != t["id"]:
                errors.append(f"tracker {key!r} has two explanations: {explained[key]!r} and {t['id']!r}")
        if "owner_company" not in t and any("in_owner_apps" in f for f in t.get("data_flows", [])):
            errors.append(f"tracker record {t['id']!r}: in_owner_apps needs owner_company")
    return errors


def control_problems(app: dict) -> list[str]:
    """Flow ids are unique within an app, and every control limits flows that exist in that app."""
    ids = [f["id"] for f in app.get("data_flows", []) if "id" in f]
    errors = [f"{app['package_id']}: flow id {i!r} used twice" for i in sorted({i for i in ids if ids.count(i) > 1})]
    for control in app.get("controls", []):
        for limit in control["limits"]:
            flow = limit if isinstance(limit, str) else limit["flow"]
            if flow not in ids:
                errors.append(f"{app['package_id']}: control {control['id']!r} limits unknown flow {flow!r}")
            if isinstance(limit, dict) and limit.get("inferred") and not limit.get("note", "").strip():
                errors.append(f"{app['package_id']}: control {control['id']!r} infers it limits {flow!r} but says nothing about why")
    return errors


def risk_tag_problems(app: dict) -> list[str]:
    """A regulatory_action tag must not read as an action against the developer when it isn't one."""
    tags, notes = app.get("risk_tags", []), app.get("risk_tag_notes", {})
    errors = [f"{app['package_id']}: risk_tag_notes has {t!r}, which is not in risk_tags" for t in notes if t not in tags]
    developer = app.get("developer_company")
    legal = [c for c in app.get("consequences", []) if c["status"] in ("alleged", "adjudicated")]
    against_developer = any(c.get("subject_company", developer) == developer for c in legal)
    if "regulatory_action" in tags and not against_developer and "regulatory_action" not in notes:
        errors.append(f"{app['package_id']}: regulatory_action needs risk_tag_notes.regulatory_action "
                      "(no action in the record is against the app's developer)")
    return errors


def check_urls(bundle: dict, now: dt.datetime) -> list[str]:
    urls = set()
    def collect(n: dict) -> None:
        if "url" in n:  # a source: its verify_url stands in for url when the page blocks scripts
            urls.add(n.get("verify_url", n["url"]))
        for k in ("wayback_url", "vendor_archive_url"):
            if k in n:
                urls.add(VENDOR_VERIFY_URLS.get(n[k], n[k]))

    walk(bundle, collect)
    try:
        cache = json.loads(URL_CACHE.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        cache = {}
    errors = []
    for url in sorted(urls):
        if urlparse(url).hostname and urlparse(url).hostname.endswith(NOT_FETCHED_HOSTS):
            print(f"  not fetched (Exodus etiquette): {url}")
            continue
        hit = cache.get(url)
        if hit and hit["status"] == 200 and (now - dt.datetime.fromisoformat(hit["checked_at"])).days < URL_CACHE_DAYS:
            continue
        try:
            resp = requests.get(url, timeout=30, headers={"User-Agent": USER_AGENT}, allow_redirects=True, stream=True)
            status = resp.status_code
            resp.close()
        except requests.RequestException as e:
            status = f"error: {e.__class__.__name__}"
        print(f"  {status}  {url}")
        if status == 200:
            cache[url] = {"status": 200, "checked_at": now.isoformat(timespec="seconds")}
        else:
            errors.append(f"source URL did not answer 200 ({status}): {url}")
    URL_CACHE.parent.mkdir(parents=True, exist_ok=True)
    URL_CACHE.write_text(json.dumps(cache, indent=1, sort_keys=True), encoding="utf-8")
    return errors


def write_atomically(path: Path, doc: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=path.parent, prefix=".bundle-", suffix=".json")
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        json.dump(doc, f, indent=1, ensure_ascii=False)
        f.write("\n")
    os.chmod(tmp, 0o644)
    os.replace(tmp, path)


def build(reviewed: list[Path], now: dt.datetime) -> dict:
    merged = merge(reviewed)
    mark_stale(merged["apps"], now.date())
    return {
        "schema_version": 1,
        "schema_revision": "1.3",
        "bundle_version": now.strftime("%Y.%m.%d"),
        "generated_at": now.isoformat(timespec="seconds"),
        "licence": "CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/), attribution: FinePrint",
        **merged,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--reviewed", type=Path, default=REVIEWED)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--skip-url-check", action="store_true", help="dry runs only; never for a real build")
    args = parser.parse_args()

    now = dt.datetime.now(HALIFAX)
    bundle = build(sorted(args.reviewed.glob("*.json")), now)
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    validator = jsonschema.Draft202012Validator(schema, format_checker=jsonschema.FormatChecker())
    errors = [f"schema: {'/'.join(map(str, e.absolute_path))}: {e.message}" for e in validator.iter_errors(bundle)]
    tracker_ids = {t["id"] for t in json.loads(TRACKERS.read_text(encoding="utf-8"))["trackers"]}
    errors += cross_check(bundle, tracker_ids)
    if not args.skip_url_check:
        errors += check_urls(bundle, now)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        print(f"not written: {len(errors)} problem(s)", file=sys.stderr)
        return 1
    write_atomically(args.out, bundle)
    counts = ", ".join(f"{len(bundle[k])} {k}" for k in SECTIONS)
    print(f"wrote {args.out} (bundle {bundle['bundle_version']}: {counts})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
