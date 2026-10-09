"""The watcher's digest: pipeline/watch/digest/YYYY-MM-DD.md and .json, from the items created that day, in
this order: quotes no longer on the page, text changed around a quote, pages that moved, new regulator items,
then failures, parked URLs and refusals. One line per item: a sentence, the URL, the item id. A day with no
items says how many sources were checked. Every digest then lists the sources the watcher can't check (parked:
the site refused or kept failing; on the denylist; or marked manual in their record, their copies saved by hand),
which a person checks by hand before each release. If local.json names copy_to, both files are copied there too.
"""
from __future__ import annotations

import datetime as dt
import shutil
from pathlib import Path

import watch_store
from watch_store import Run, Store

SECTIONS = [
    ("Quotes no longer on the page", ("quote_missing",)),
    ("Text changed around a quote", ("context_changed",)),
    ("Pages that moved", ("url_moved",)),
    ("New regulator items naming a recorded company, app or tracker", ("new_event",)),
    ("Failures, parked URLs and refusals", ("fetch_failure", "parked", "refused")),
]


def log_run(run: Run, adapters: list[str]) -> None:
    path = run.store.digest / f"runs-{run.when.date().isoformat()}.json"
    runs = watch_store.read_json(path, [])
    runs.append({"at": run.when.isoformat(timespec="seconds"), "adapters": adapters, "checked": sorted(set(run.checked)),
                 "requests": run.fetcher.requests if run.fetcher else 0, "written": [i["id"] for i in run.written]})
    watch_store.write_json(path, runs)


def records(item: dict) -> str:
    ids = sorted({r.split(":")[1] for r in item["record_refs"] if r.count(":") >= 1})
    if not ids:
        return "a record"
    return ", ".join(ids[:3]) + (f" and {len(ids) - 3} more" if len(ids) > 3 else "")


def sentence(item: dict) -> str:
    quote = (item.get("quote") or "")
    quote = quote if len(quote) <= 80 else quote[:79] + "…"
    kind = item["kind"]
    if kind == "quote_missing":
        return f"A quote cited by {records(item)} is no longer on the page: “{quote}”."
    if kind == "context_changed":
        return f"The text around a quote cited by {records(item)} changed: “{quote}”."
    if kind == "url_moved":
        return f"The page cited by {records(item)} now ends at {item['final_url']}."
    if kind == "new_event":
        return item["notes"]
    first = item["notes"].split(". ")[0].rstrip(".")
    return f"{first} ({records(item)})." if item["record_refs"] else f"{first}."


def by_hand_reason(meta: dict) -> str:
    if meta.get("parked"):
        return meta.get("parked_reason") or "parked"
    if meta.get("refused"):
        return f"{meta['refused']} is on the denylist"
    return "marked manual: its copy is saved by hand"


def line(item: dict) -> str:
    url = item["event"]["link"] if item["kind"] == "new_event" else item["source_url"]
    return f"- {sentence(item)} {url} ({item['id']})"


def build(store: Store, day: dt.date) -> tuple[str, dict]:
    stamp = day.isoformat()
    items = [watch_store.read_json(p) for folder in (store.queue, store.acked) for p in sorted(folder.glob("*.json"))]
    items = sorted((i for i in items if i and i["created_at"].startswith(stamp)), key=lambda i: (i["created_at"], i["id"]))
    runs = watch_store.read_json(store.digest / f"runs-{stamp}.json", [])
    checked = len({url for r in runs for url in r["checked"]})
    metas = [watch_store.read_json(p, {}) for p in store.snapshots.glob("*/meta.json")]
    by_hand = sorted((m["url"], by_hand_reason(m)) for m in metas if m.get("parked") or m.get("refused") or m.get("manual"))
    md = [f"# FinePrint watcher digest, {stamp}", ""]
    ordered = []
    if not items:
        md.append(f"No changes at {checked} sources checked.")
    else:
        md.append(f"{len(items)} new items from {checked} sources checked.")
        for title, kinds in SECTIONS:
            section = sorted((i for i in items if i["kind"] in kinds), key=lambda i: (kinds.index(i["kind"]), i["source_url"]))
            if section:
                md += ["", f"## {title}", ""] + [line(i) for i in section]
                ordered += section
    if by_hand:
        md += ["", f"## Checked by hand before each release ({len(by_hand)})", ""] + [f"- {url} ({why})" for url, why in by_hand]
    doc = {"date": stamp, "sources_checked": checked, "runs": runs, "items": ordered,
           "checked_by_hand": [{"url": url, "why": why} for url, why in by_hand]}
    return "\n".join(md) + "\n", doc


def write(store: Store, day: dt.date, copy_to: str | None = None) -> Path:
    md, doc = build(store, day)
    path = store.digest / f"{day.isoformat()}.md"
    store.digest.mkdir(parents=True, exist_ok=True)
    path.write_text(md, encoding="utf-8")
    watch_store.write_json(path.with_suffix(".json"), doc)
    if copy_to:
        target = Path(copy_to).expanduser()
        target.mkdir(parents=True, exist_ok=True)
        for p in (path, path.with_suffix(".json")):
            shutil.copy(p, target / p.name)
    return path
