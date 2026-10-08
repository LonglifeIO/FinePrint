"""The watcher's queue: one JSON file per finding in pipeline/watch/queue/, validated against
pipeline/watch/queue-item.schema.json before it is written. Items state facts (what changed, where, when);
they never suggest a status or a tier. A finding is written once: an item whose id (kind, source URL,
snapshot and quote) is already in queue/ or acked/ is not written again. `watch.py ack` moves an item to
acked/ with a note; acking a parked URL's item lets the watcher try it again.
"""
from __future__ import annotations

import datetime as dt
import hashlib
from pathlib import Path

import jsonschema

import watch_store
from watch_store import PARK_AFTER, Run, Store

SCHEMA = Path(__file__).resolve().parent / "watch" / "queue-item.schema.json"
_validator = None


def sha256(data: str | bytes) -> str:
    return hashlib.sha256(data.encode("utf-8") if isinstance(data, str) else data).hexdigest()


def item_id(kind: str, source_url: str, snapshot_sha256: str, quote_id: str | None = None) -> str:
    return sha256(kind + source_url + snapshot_sha256 + (quote_id or ""))[:16]


def make(kind: str, source_url: str, snapshot_sha256: str, when: dt.datetime, licence_class: str, notes: str, *,
         final_url=None, fetched_at=None, prior=None, record_refs=(), quote_id=None, quote=None,
         diff_window=None, http_status=None, event=None) -> dict:
    return {
        "id": item_id(kind, source_url, snapshot_sha256, quote_id),
        "kind": kind,
        "created_at": when.isoformat(timespec="seconds"),
        "source_url": source_url,
        "final_url": final_url,
        "fetched_at": fetched_at,
        "snapshot_sha256": snapshot_sha256,
        "prior_snapshot_sha256": prior,
        "record_refs": sorted(set(record_refs)),
        "quote_id": quote_id,
        "quote": quote,
        "diff_window": diff_window,
        "http_status": http_status,
        "licence_class": licence_class,
        "notes": notes,
        "event": event,
    }


def problems(item: dict) -> list[str]:
    global _validator
    if _validator is None:
        _validator = jsonschema.Draft202012Validator(watch_store.read_json(SCHEMA))
    return [f"{'/'.join(map(str, e.absolute_path)) or '(item)'}: {e.message}" for e in _validator.iter_errors(item)]


def write(store: Store, item: dict) -> bool:
    """Writes a new item to queue/; False if this finding is already in queue/ or acked/."""
    found = problems(item)
    if found:
        raise ValueError(f"queue item {item.get('id')} doesn't validate: {'; '.join(found)}")
    name = f"{item['id']}.json"
    if (store.queue / name).exists() or (store.acked / name).exists():
        return False
    watch_store.write_json(store.queue / name, item)
    return True


def put(run: Run, item: dict) -> None:
    """Queues an item for this run, unless it is a dry run."""
    if run.dry_run:
        run.notes.append(f"  would queue {item['kind']}: {item['source_url']}")
    elif write(run.store, item):
        run.written.append(item)


def failure(run: Run, kind: str, url: str, meta: dict, got, licence_class: str, record_refs=()) -> dict:
    """The item for a refusal (denylist), a parked URL or a failed fetch."""
    if kind == "refused":
        basis, notes = got.reason, f"Not fetched: {got.reason}."
    elif kind == "parked":
        basis = f"parked since {meta['parked_since']}: {meta['parked_reason']}"
        notes = f"Parked: {meta['parked_reason']}. Not checked again until this item is acked."
    else:
        basis = f"failing since {meta['failing_since']}"
        notes = f"Fetch failed: {got.reason}. A URL is parked after {PARK_AFTER} failed runs in a row."
    return make(kind, url, sha256(basis), run.when, licence_class, notes, final_url=got.final_url or None,
                http_status=got.status, record_refs=record_refs)


def find(store: Store, key: str) -> Path | None:
    """The queued item with this id, or the only one whose id starts with key (at least 6 digits)."""
    exact = store.queue / f"{key}.json"
    if exact.exists():
        return exact
    matches = list(store.queue.glob(f"{key}*.json")) if len(key) >= 6 else []
    return matches[0] if len(matches) == 1 else None


def ack(store: Store, key: str, note: str, when: dt.datetime) -> dict | None:
    path = find(store, key)
    if path is None:
        return None
    item = watch_store.read_json(path)
    item["ack"] = {"at": when.isoformat(timespec="seconds"), "note": note}
    found = problems(item)
    if found:
        raise ValueError(f"acked item {item['id']} doesn't validate: {'; '.join(found)}")
    watch_store.write_json(store.acked / path.name, item)
    path.unlink()
    if item["kind"] == "parked":
        meta = store.meta(item["source_url"])
        watch_store.unpark(meta)
        store.save_meta(meta)
    return item
