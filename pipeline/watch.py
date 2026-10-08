#!/usr/bin/env python3
"""FinePrint's watcher: notices change at the pages FinePrint quotes and at regulators' feeds, and puts it in
front of a person. It writes queue items and a digest under pipeline/watch/ and nothing else: never a record,
a bundle file or anything in pipeline/reviewed/. Nothing it finds reaches the app until a person has
reviewed it and the bundle is rebuilt.

    python3 pipeline/watch.py poll [--adapter NAME] [--dry-run]   # the sources that are due (each adapter's every_days)
    python3 pipeline/watch.py ack ITEM_ID [--note TEXT]               # done with an item: it moves to acked/
    python3 pipeline/watch.py status

Configuration: pipeline/watch/sources.json (committed: adapters, feeds, cadences, the denylist) and
pipeline/watch/local.json (gitignored): {"contact": "<email or URL>", "copy_to": "<folder>"}. The contact
goes in every request's User-Agent, and without one the watcher refuses to run. See CLAUDE.md, the watcher.
"""
from __future__ import annotations

import argparse
import collections
import sys
import time

import requests

import watch_fetch
import watch_queue
import watch_quotes
import watch_store
from watch_store import Run, Store

ADAPTERS = {"quote_drift": watch_quotes.poll}  # name -> poll(run, settings)


def poll(store: Store, config: dict, local: dict, adapter: str | None = None, dry_run: bool = False,
         http=requests, sleep=time.sleep, when=None) -> int:
    contact = str(local.get("contact") or "").strip()
    if not contact:
        print("refusing to run: no contact in pipeline/watch/local.json. Every request names FinePrint and a "
              'contact; add {"contact": "<email or URL>"} there (the file is gitignored).', file=sys.stderr)
        return 2
    names = [adapter] if adapter else [n for n in config["adapters"] if n in ADAPTERS]
    unknown = [n for n in names if n not in ADAPTERS or n not in config["adapters"]]
    if unknown:
        print(f"unknown adapter {unknown[0]!r}; configured: {', '.join(config['adapters']) or 'none'}", file=sys.stderr)
        return 2
    fetcher = None if dry_run else watch_fetch.Fetcher(contact, config["denylist"], http=http, sleep=sleep)
    run = Run(store, fetcher, when or watch_store.now(), config["denylist"], dry_run)
    for name in names:
        ADAPTERS[name](run, config["adapters"][name])
    for line in run.notes:
        print(line)
    tail = " (dry run: nothing fetched or written)" if dry_run else f", {fetcher.requests} requests"
    print(f"checked {len(run.checked)} sources, {len(run.written)} new items{tail}")
    return 0


def status(store: Store) -> int:
    metas = [watch_store.read_json(p, {}) for p in sorted(store.snapshots.glob("*/meta.json"))]
    open_items = collections.Counter(watch_store.read_json(p, {}).get("kind", "?") for p in store.queue.glob("*.json"))
    checked = [m["last_checked"] for m in metas if m.get("last_checked")]
    print(f"{len(metas)} sources tracked; last check {max(checked) if checked else 'never'}")
    print(f"{sum(open_items.values())} open items" + (": " + ", ".join(f"{n} {k}" for k, n in sorted(open_items.items())) if open_items else ""))
    print(f"{len(list(store.acked.glob('*.json')))} acked")
    parked = [m for m in metas if m.get("parked")]
    print(f"{len(parked)} parked" + (":" if parked else ""))
    for m in parked:
        print(f"  {m['url']}  ({m.get('parked_reason')}, since {m.get('parked_since')})")
    return 0


def ack(store: Store, key: str, note: str) -> int:
    item = watch_queue.ack(store, key, note, watch_store.now())
    if item is None:
        print(f"no queued item {key!r} (or more than one starts with it)", file=sys.stderr)
        return 1
    print(f"acked {item['id']} ({item['kind']}: {item['source_url']})" + ("; it will be checked again" if item["kind"] == "parked" else ""))
    return 0


def main(argv: list[str] | None = None, root=watch_store.WATCH) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    p = commands.add_parser("poll", help="check the sources that are due")
    p.add_argument("--adapter", help="only this adapter")
    p.add_argument("--dry-run", action="store_true", help="say what is due; fetch and write nothing")
    a = commands.add_parser("ack", help="move a queue item to acked/, with a note")
    a.add_argument("item_id", help="the item's id, or enough of it to be unique (6 digits or more)")
    a.add_argument("--note", default="", help="what was done about it")
    commands.add_parser("status", help="open items, parked URLs, the last check")
    args = parser.parse_args(argv)

    store = Store(root)
    if args.command == "status":
        return status(store)
    if args.command == "ack":
        return ack(store, args.item_id, args.note)
    config = watch_store.read_json(root / "sources.json")
    if config is None:
        print(f"no readable {root / 'sources.json'}", file=sys.stderr)
        return 2
    return poll(store, config, watch_store.read_json(root / "local.json", {}), args.adapter, args.dry_run)


if __name__ == "__main__":
    sys.exit(main())
