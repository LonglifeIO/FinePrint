#!/usr/bin/env python3
"""Build bundle/bundle.json from the human-reviewed records in pipeline/reviewed/.

    python3 pipeline/build.py                 # validate, check every source URL, write bundle/bundle.json
    python3 pipeline/build.py --out preview.json --skip-url-check   # dry runs
    python3 pipeline/build.py --previous old-record.json            # derive a new change's direction first

Every reviewed file is a JSON object holding any of the arrays apps, trackers, companies,
permissions, device_reach and jurisdictions; they are merged, validated against bundle/schema.json
(jurisdictions go to their own file, bundle/jurisdictions.json), cross-checked
(company ids, derives_from ids, tracker ids against bundle/trackers.json and one explanation per
tracker id, a quote on every source, one definition per source id, qualified regulatory_action
tags), and every source URL must answer
HTTP 200, and not by redirecting to a not-found page, or its verify_url when the page blocks scripts
(an OK result is cached in pipeline/raw/ for 30 days). Exodus pages are never fetched (see CLAUDE.md,
Exodus etiquette). Nothing reaches bundle.json without review.

An app record's changes[] are written by the reviewer (date, text, sources). Their direction is
never typed: given the record as it was before (--previous, e.g. from `git show HEAD:<file>`),
build.py diffs its structure, writes the diff and the direction it implies back into the reviewed
file, and on every build checks that each change's direction still follows from its diff.
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
from urllib.parse import parse_qs, urlparse
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
JURISDICTIONS = "jurisdictions"  # written to bundle/jurisdictions.json, not bundle.json
UNIONS = {"EU"}  # jurisdictions entries for a union of countries: each law lists the countries it binds (applies_to)
LICENCE = "CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/), attribution: FinePrint"
STALE_DAYS = 180
URL_CACHE_DAYS = 30
NOT_FOUND = re.compile(r"(?<!\d)404(?!\d)")  # a redirect's path naming 404, not a number such as 18404
NOT_FETCHED_HOSTS = ("exodus-privacy.eu.org",)
# Vendor archive pages that block scripts, and the API address of the same article.
VENDOR_VERIFY_URLS = {
    "https://legal.corp.life360.com/hc/en-us/articles/40254028461463-Life360-Privacy-Policy-previous-to-May-5-2026":
        "https://legal.corp.life360.com/api/v2/help_center/en-us/articles/40254028461463.json",
}
USER_AGENT = "FinePrint-pipeline (+https://github.com/LonglifeIO/FinePrint)"
BUCKET_RANK = {"stays_here": 0, "used_for_more": 1, "goes_elsewhere": 2}
WORSENING = ("flow added", "moved away from stays here", "data kind added", "tracker added", "control removed", "now on by default")
IMPROVING = ("flow removed", "moved toward stays here", "data kind removed", "tracker removed", "control added", "now off by default")
# A flow's evidence and the legal items naming an app count by what they did to the tier (tier_before, tier_after).
BY_TIER = ("flow status", "event added", "event removed", "event status")
TIERS = ("expected", "caution", "flagged")
LEGAL = ("alleged", "adjudicated")
# Past filing: a judge let the case go ahead, or a regulator opened a formal proceeding. Only then does an alleged line count.
LET_PROCEED = ("survived_motion_to_dismiss", "proceeding_opened")


def merge(paths: list[Path]) -> dict[str, list]:
    merged: dict[str, list] = {k: [] for k in SECTIONS + (JURISDICTIONS,)}
    for path in paths:
        doc = json.loads(path.read_text(encoding="utf-8"))
        unknown = set(doc) - set(merged)
        if unknown:
            raise ValueError(f"{path.name}: unknown sections {sorted(unknown)}")
        for key in merged:
            merged[key].extend(doc.get(key, []))
    for key, id_field in (("apps", "package_id"), ("trackers", "id"), ("companies", "id"),
                          ("permissions", "id"), ("device_reach", "id"), (JURISDICTIONS, "id")):
        ids = [r[id_field] for r in merged[key]]
        repeated = sorted({i for i in ids if ids.count(i) > 1})
        if repeated:
            raise ValueError(f"duplicate {key}: {repeated}")
    return merged


def mark_stale(records: list[dict], today: dt.date) -> None:
    """stale = last reviewed more than STALE_DAYS before the build (app records, and each law)."""
    for record in records:
        reviewed = dt.date.fromisoformat(record["last_reviewed"])
        record["stale"] = (today - reviewed).days > STALE_DAYS


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
        errors.extend(standing_problems(n))
        errors.extend(government_problems(n))
        errors.extend(forum_problems(n))
        if "owner_history" in n:
            dates = [h["date"] for h in n["owner_history"]]
            errors.extend(f"{n['id']}: owner_history isn't oldest first ({a} before {b})" for a, b in zip(dates, dates[1:]) if b < a)

    walk(bundle, visit)
    built = bundle.get("generated_at", "")[:10]  # a partial bundle in a test may have none
    for app in bundle["apps"]:
        if built and app.get("checked_on", "") > built:
            errors.append(f"{app['package_id']}: checked_on {app['checked_on']} is after the build ({built})")
        errors += [f"{app['package_id']}: tracker {t!r} not in trackers.json" for t in app["trackers"] if t not in tracker_ids]
        errors += risk_tag_problems(app)
        errors += tagline_problems(app)
        errors += control_problems(app)
        errors += change_problems(app)
        errors += [f"{app['package_id']}: in_owner_apps is for tracker records" for f in app.get("data_flows", []) if "in_owner_apps" in f]
    errors += tracker_problems(bundle["trackers"], tracker_ids)
    errors += inheritance_problems(bundle["companies"])
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


def inheritance_problems(companies: list[dict]) -> list[str]:
    """Default flows need a prefix to reach any app, unique flow ids, and no tracker-only or government fields."""
    errors = []
    for c in companies:
        flows = c.get("default_flows", [])
        if (flows or c.get("default_notes")) and not c.get("package_prefixes"):
            errors.append(f"{c['id']}: default_flows and default_notes need package_prefixes to reach an app")
        ids = [f["id"] for f in flows if "id" in f]
        errors += [f"{c['id']}: default flow id {i!r} used twice" for i in sorted({i for i in ids if ids.count(i) > 1})]
        errors += [f"{c['id']}: a default flow can't use {k}" for f in flows for k in ("in_owner_apps", "recipient_kind") if k in f]
    prefixes = [(p, c["id"]) for c in companies for p in c.get("package_prefixes", [])]
    errors += [f"package prefix {p!r} ({a}) overlaps {q!r} ({b})" for p, a in prefixes for q, b in prefixes if a != b and p.startswith(q)]
    return errors


def forum_problems(item: dict) -> list[str]:
    """forum says where a matter is decided (the app writes "not proven in court" or "not yet decided"), so
    only an alleged or adjudicated line carries it."""
    if "forum" not in item or item.get("status") in LEGAL:
        return []
    return [f"{item.get('id') or item.get('title') or item.get('text', '')[:60]!r}: forum is for alleged or adjudicated lines"]


def standing_problems(item: dict) -> list[str]:
    """An ended matter (closed_date) is neither in force nor under appeal, and ends on or after its date."""
    if "closed_date" not in item:
        return []
    what = item.get("title") or item.get("text", "")[:60]
    errors = [f"{what!r}: closed_date with {k}" for k in ("in_force", "appeal_pending") if item.get(k)]
    if "date" in item and item["closed_date"] < item["date"]:
        errors.append(f"{what!r}: closed_date {item['closed_date']} is before its date {item['date']}")
    return errors


def independent_sources(sources: list[dict]) -> int:
    """Copies (derives_from) and sources resting on one investigation (single_source) count once."""
    return 1 if any(s.get("single_source") for s in sources) else sum(1 for s in sources if "derives_from" not in s)


def government_problems(line: dict) -> list[str]:
    """A Can compel line cites the law itself first; a reported Has used line needs two independent sources."""
    if line.get("recipient_kind") != "government_body":
        return []
    what = line.get("recipient_label") or line.get("text", "")[:60]
    errors = []
    if line.get("government_line") == "can_compel" and line["sources"][0].get("type") != "statute":
        errors.append(f"{what!r}: a Can compel line's first source is the law's own text (type statute)")
    if line.get("government_line") == "has_used" and line.get("status") == "reported" and independent_sources(line["sources"]) < 2:
        errors.append(f"{what!r}: a Has used line needs two independent sources")
    return errors


def law_problems(places: dict) -> list[str]:
    """Each law has one id, cites its own text first, quotes every source, and, in a union's entry, names the countries it binds."""
    laws = [law for j in places[JURISDICTIONS] for law in j["laws"]]
    ids = [law["id"] for law in laws]
    errors = [f"law id {i!r} used twice" for i in sorted({i for i in ids if ids.count(i) > 1})]
    errors += [f"{law['id']}: its first source is the law's own text (type statute)" for law in laws if law["sources"][0]["type"] != "statute"]
    errors += [f"{law['id']}: a law in the {j['id']} entry says which countries it binds (applies_to)"
               for j in places[JURISDICTIONS] if j["id"] in UNIONS for law in j["laws"] if not law.get("applies_to")]
    def visit(n: dict) -> None:
        if "url" in n and not str(n.get("quote", "")).strip():
            errors.append(f"source without a quote: {n['url']}")
    walk(places, visit)
    return errors


def flow_name(f: dict) -> str:
    return f"{f['data']} to {f.get('recipient') or f.get('recipient_label')}"


def match_flows(before: list[dict], after: list[dict]) -> tuple[list[tuple[dict, dict]], list[dict], list[dict]]:
    """Pairs flows across two versions of a record: by id, else by data and recipient, in order when a
    record has two of the same (Facebook's two flows of app activity to Meta). A reworded unnamed
    recipient pairs with the flow of the same data and bucket. Returns pairs, removed, added."""
    def key(f: dict):
        return f.get("id") or (f["data"], f.get("recipient") or f.get("recipient_label"))
    left: dict = {}
    for f in before:
        left.setdefault(key(f), []).append(f)
    pairs, added = [], []
    for f in after:
        if left.get(key(f)):
            pairs.append((left[key(f)].pop(0), f))
        else:
            added.append(f)
    removed = [f for same in left.values() for f in same]
    for f in list(added):
        twin = next((r for r in removed if "recipient" not in r and "recipient" not in f
                     and (r["data"], r["bucket"]) == (f["data"], f["bucket"])), None)
        if twin:
            pairs.append((twin, f))
            removed.remove(twin)
            added.remove(f)
    return pairs, removed, added


def independent_sources(sources: list[dict]) -> int:
    """Copies (derives_from) and claims that rest on one investigation (single_source) count once."""
    return 1 if any(s.get("single_source") for s in sources) else sum(1 for s in sources if not s.get("derives_from"))


def evidence(f: dict) -> str:
    """What the tier rules read of a flow's evidence: its status, whether a report has the second
    independent source it needs to raise a tier, and whether an alleged line's case has gone past filing."""
    if f["status"] == "reported" and independent_sources(f["sources"]) < 2:
        return "reported, one source"
    if f["status"] == "alleged" and f.get("status_kind") not in LET_PROCEED:
        return f"alleged, {f.get('status_kind', 'filed')}"
    return f["status"]


def standing(item: dict) -> str:
    """What the tier rules read of a legal item: 'adjudicated/ruling, in force, closed 2026-08-24'."""
    parts = (item["status"] + (f"/{item['status_kind']}" if item.get("status_kind") else ""),
             "in force" if item.get("in_force") else "", "appeal pending" if item.get("appeal_pending") else "",
             f"closed {item['closed_date']}" if item.get("closed_date") else "")
    return ", ".join(p for p in parts if p)


def legal_items(app: dict, companies: list[dict] = (), trackers: list[dict] = ()) -> dict[str, str]:
    """The legal items the tier rules read for an app, each with its standing: every company's actions
    naming the app (breaches aside), by company, date and type; the app's own alleged or adjudicated
    lines, and those of the trackers in it that name the app, by owner and first source."""
    pkg, found = app["package_id"], {}

    def put(key: str, item: dict):
        k, n = key, 2
        while k in found:
            k, n = f"{key} #{n}", n + 1
        found[k] = standing(item)
    for c in companies:
        for e in c.get("regulatory_history", []):
            if e.get("concerns_app") == pkg and e["type"] != "breach":
                put(f"{c['id']} {e['date']} {e['type']}", e)
    for q in app.get("consequences", []):
        if q["status"] in LEGAL and q.get("concerns_app") in (None, pkg):
            put(f"{pkg} {q['sources'][0]['url']}", q)
    ids = set(app.get("trackers", []))
    for t in trackers:
        if t["id"] in ids or ids & set(t.get("covers", [])):
            for q in t.get("consequences", []):
                if q["status"] in LEGAL and q.get("concerns_app") == pkg:
                    put(f"{t['id']} {q['sources'][0]['url']}", q)
    return found


def legal_diff(old: dict[str, str], new: dict[str, str]) -> list[str]:
    """Legal items added, removed or changed in standing. A line reworded or cited to another first
    source pairs with a removed line of the same owner and standing."""
    came = {k: v for k, v in new.items() if k not in old}
    gone = {k: v for k, v in old.items() if k not in new}
    for k, v in list(came.items()):
        twin = next((g for g, w in gone.items() if "://" in g and "://" in k and g.split(" ")[0] == k.split(" ")[0] and w == v), None)
        if twin:
            del came[k], gone[twin]
    return ([f"event added: {k} ({v})" for k, v in came.items()] + [f"event removed: {k} ({v})" for k, v in gone.items()]
            + [f"event status: {k} ({old[k]} to {v})" for k, v in new.items() if k in old and old[k] != v])


def structural_diff(old: dict, new: dict, old_legal: dict | None = None, new_legal: dict | None = None) -> list[str]:
    """What changed in an app record's structure, one '<kind>: <what>' line each: what feeds the tier
    rules or the controls (docs/METHOD.md, Your Reviewed marks). Purposes, wording and the store
    tagline aren't structure: a change to them alone leaves the diff empty, and so does a conditional
    flow, which is never scored. [old_legal] and [new_legal] are legal_items() with the companies and
    trackers; without them, the record's own."""
    def current(r: dict) -> list[dict]:
        return [f for f in r.get("data_flows", []) if not f.get("historical") and not f.get("conditional")]
    pairs, removed, added = match_flows(current(old), current(new))
    ended = {(f["data"], f.get("recipient") or f.get("recipient_label")) for f in new.get("data_flows", []) if f.get("historical")}
    diff = [f"flow added: {flow_name(f)} ({f['bucket']})" for f in added if f["bucket"] != "stays_here"]
    diff += [f"flow removed: {flow_name(f)} ({f['bucket']}"
             + (", now a past practice)" if (f["data"], f.get("recipient") or f.get("recipient_label")) in ended else ")")
             for f in removed if f["bucket"] != "stays_here"]
    for a, b in pairs:
        if BUCKET_RANK[b["bucket"]] != BUCKET_RANK[a["bucket"]]:
            way = "toward" if BUCKET_RANK[b["bucket"]] < BUCKET_RANK[a["bucket"]] else "away from"
            diff.append(f"moved {way} stays here: {flow_name(b)} ({a['bucket']} to {b['bucket']})")
        was_on, is_on = a.get("default", "on") == "on", b.get("default", "on") == "on"
        if was_on != is_on:  # off and opt_in both mean it doesn't happen unless you act
            diff.append(f"now {'on' if is_on else 'off'} by default: {flow_name(b)} ({a.get('default', 'on')} to {b.get('default', 'on')})")
        if evidence(a) != evidence(b):
            diff.append(f"flow status: {flow_name(b)} ({evidence(a)} to {evidence(b)})")
    for what, before, after in (
        ("data kind", {f["data"] for f in current(old)}, {f["data"] for f in current(new)}),
        ("tracker", set(old.get("trackers", [])), set(new.get("trackers", []))),
        ("control", {c["id"] for c in old.get("controls", [])}, {c["id"] for c in new.get("controls", [])}),
    ):
        diff += [f"{what} added: {k}" for k in sorted(after - before)]
        diff += [f"{what} removed: {k}" for k in sorted(before - after)]
    return diff + legal_diff(legal_items(old) if old_legal is None else old_legal, legal_items(new) if new_legal is None else new_legal)


def direction_of(diff: list[str], tier_before: str | None = None, tier_after: str | None = None) -> str:
    """Worsened if anything got worse (a gain is never netted against a loss), else improved if
    anything got better, else neutral: the wording changed, not the practice. A flow's evidence and a
    legal item count by the tier: worse if it rose, better if it fell, neither if it stayed."""
    kinds = {line.split(": ", 1)[0] for line in diff}
    moved = TIERS.index(tier_after) - TIERS.index(tier_before) if kinds & set(BY_TIER) and tier_before and tier_after else 0
    if kinds & set(WORSENING) or moved > 0:
        return "worsened"
    return "improved" if kinds & set(IMPROVING) or moved < 0 else "neutral"


def change_problems(app: dict) -> list[str]:
    """Every change has the direction its diff implies; a new one needs --previous first."""
    errors = []
    for c in app.get("changes", []):
        if "direction" not in c or "diff" not in c:
            errors.append(f"{app['package_id']}: change of {c['date']} has no direction yet; run build.py --previous <the record before it>")
        elif {line.split(": ", 1)[0] for line in c["diff"]} & set(BY_TIER) and not (c.get("tier_before") and c.get("tier_after")):
            errors.append(f"{app['package_id']}: change of {c['date']} names a legal item or a flow's evidence; "
                          "record tier_before and tier_after, which set its direction")
        elif c["direction"] != (implied := direction_of(c["diff"], c.get("tier_before"), c.get("tier_after"))):
            errors.append(f"{app['package_id']}: change of {c['date']} says {c['direction']}, but its diff makes it {implied}")
    return errors


def derive_directions(reviewed: list[Path], previous: list[Path]) -> list[str]:
    """For each app in the previous records, diffs it against the reviewed record and fills in the
    diff and direction of the reviewed record's one change that has none, writing the file back.
    Pass the earlier version of every file that changed, company and tracker records too: a file left
    out counts as unchanged."""
    docs = {path: json.loads(path.read_text(encoding="utf-8")) for path in reviewed}
    now: dict[str, dict] = {"companies": {}, "trackers": {}}
    for doc in docs.values():
        for kind, found in now.items():
            found.update({r["id"]: r for r in doc.get(kind, [])})
    before, then = {}, {kind: dict(found) for kind, found in now.items()}
    for path in previous:
        doc = json.loads(path.read_text(encoding="utf-8"))
        if "package_id" in doc:  # a bare app record
            before[doc["package_id"]] = doc
            continue
        before.update({a["package_id"]: a for a in doc.get("apps", [])})
        for kind, found in then.items():
            found.update({r["id"]: r for r in doc.get(kind, [])})
    done = []
    for path, doc in docs.items():
        touched = False
        for app in doc.get("apps", []):
            changes = app.get("changes", [])
            pending = [i for i, c in enumerate(changes) if "direction" not in c]
            if app["package_id"] not in before or not pending:
                continue
            if len(pending) > 1:
                raise ValueError(f"{app['package_id']}: {len(pending)} changes have no direction; derive them one record version at a time")
            old = before[app["package_id"]]
            diff = structural_diff(old, app, legal_items(old, list(then["companies"].values()), list(then["trackers"].values())),
                                   legal_items(app, list(now["companies"].values()), list(now["trackers"].values())))
            c = changes[pending[0]]
            changes[pending[0]] = {k: v for k, v in (
                ("date", c["date"]), ("text", c["text"]), ("direction", direction_of(diff, c.get("tier_before"), c.get("tier_after"))), ("diff", diff),
                ("tier_before", c.get("tier_before")), ("tier_after", c.get("tier_after")), ("sources", c["sources"]),
            ) if v is not None}
            touched = True
            done.append(f"{app['package_id']}, change of {c['date']}: {changes[pending[0]]['direction']} ({len(diff)} structural changes)")
        if touched:
            path.write_text(json.dumps(doc, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    return done


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


def tagline_problems(app: dict) -> list[str]:
    """A store tagline quotes this app's own Google Play listing."""
    tagline = app.get("store_tagline")
    if not tagline:
        return []
    url = urlparse(tagline["source_url"])
    ids = parse_qs(url.query).get("id", [])
    if url.hostname != "play.google.com" or not url.path.startswith("/store/apps/details") or ids != [app["package_id"]]:
        return [f"{app['package_id']}: store_tagline must cite this app's Google Play listing, not {tagline['source_url']}"]
    return []


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
        if "source_url" in n:  # a store tagline's listing
            urls.add(n["source_url"])
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
        final, redirected = url, False
        try:
            resp = requests.get(url, timeout=30, headers={"User-Agent": USER_AGENT}, allow_redirects=True, stream=True)
            status, final, redirected = resp.status_code, resp.url, bool(resp.history)
            resp.close()
        except requests.RequestException as e:
            status = f"error: {e.__class__.__name__}"
        print(f"  {status}  {url}")
        if status == 200 and redirected and NOT_FOUND.search(urlparse(final).path):
            errors.append(f"source URL redirects to a not-found page ({final}): {url}")
        elif status == 200:
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
        "schema_revision": "1.6",
        "bundle_version": now.strftime("%Y.%m.%d"),
        "generated_at": now.isoformat(timespec="seconds"),
        "licence": LICENCE,
        **{k: merged[k] for k in SECTIONS},
    }


def build_jurisdictions(reviewed: list[Path], now: dt.datetime) -> dict:
    """bundle/jurisdictions.json: each country's laws that let its government compel data."""
    places = merge(reviewed)[JURISDICTIONS]
    # A law without its own review date fails the schema check; one without it can't be marked stale either.
    mark_stale([law for j in places for law in j["laws"] if "last_reviewed" in law], now.date())
    return {"schema_version": 1, "generated_at": now.isoformat(timespec="seconds"), "licence": LICENCE, JURISDICTIONS: places}


def jurisdictions_path(out: Path) -> Path:
    """bundle.json -> jurisdictions.json beside it; bundle-preview.json -> jurisdictions-preview.json."""
    stem = out.stem.replace("bundle", "jurisdictions") if "bundle" in out.stem else out.stem + "-jurisdictions"
    return out.with_name(stem + out.suffix)


def schema_problems(schema: dict, doc: dict, ref: str | None = None) -> list[str]:
    """Validates doc against the schema, or against one of its $defs (e.g. jurisdictions_file)."""
    target = {"$schema": schema["$schema"], "$defs": schema["$defs"], "$ref": f"#/$defs/{ref}"} if ref else schema
    validator = jsonschema.Draft202012Validator(target, format_checker=jsonschema.FormatChecker())
    return [f"schema{' ' + ref if ref else ''}: {'/'.join(map(str, e.absolute_path))}: {e.message}" for e in validator.iter_errors(doc)]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--reviewed", type=Path, default=REVIEWED)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--skip-url-check", action="store_true", help="dry runs only; never for a real build")
    parser.add_argument("--previous", type=Path, nargs="+", default=[],
                        help="earlier versions of the changed files (app, company and tracker records): derive the direction of each app's new change")
    args = parser.parse_args()

    for line in derive_directions(sorted(args.reviewed.glob("*.json")), args.previous):
        print(f"derived {line}")
    now = dt.datetime.now(HALIFAX)
    reviewed = sorted(args.reviewed.glob("*.json"))
    bundle, places = build(reviewed, now), build_jurisdictions(reviewed, now)
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    errors = schema_problems(schema, bundle) + schema_problems(schema, places, "jurisdictions_file")
    tracker_ids = {t["id"] for t in json.loads(TRACKERS.read_text(encoding="utf-8"))["trackers"]}
    errors += cross_check(bundle, tracker_ids) + law_problems(places)
    if not args.skip_url_check:
        errors += check_urls({"bundle": bundle, JURISDICTIONS: places}, now)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        print(f"not written: {len(errors)} problem(s)", file=sys.stderr)
        return 1
    write_atomically(args.out, bundle)
    write_atomically(jurisdictions_path(args.out), places)
    counts = ", ".join(f"{len(bundle[k])} {k}" for k in SECTIONS)
    print(f"wrote {args.out} (bundle {bundle['bundle_version']}: {counts})")
    print(f"wrote {jurisdictions_path(args.out)} ({len(places[JURISDICTIONS])} jurisdictions)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
