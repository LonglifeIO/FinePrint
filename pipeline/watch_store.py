"""What the watcher keeps, all under pipeline/watch/ (see CLAUDE.md, the watcher).

    snapshots/<sha256(url)>/   each copy as served (<timestamp>.<ext>), its visible text (<timestamp>.txt),
                               and meta.json: status, final URL, etag, last-modified, sha256, last_checked,
                               failures, parked
    queue/, acked/             queue items (watch_queue.py)
    digest/                    the digests and each day's run log
    local.json                 the contact for the user agent, and optionally copy_to (a folder for the digest)

All of these are gitignored and refused by the forbidden-files hook: they hold other people's pages. The
committed configuration is sources.json. The watcher writes nowhere else.
"""
from __future__ import annotations

import datetime as dt
import hashlib
import json
import os
import tempfile
from dataclasses import dataclass, field
from pathlib import Path
from zoneinfo import ZoneInfo

import fetch_sources

REPO = Path(__file__).resolve().parent.parent
WATCH = REPO / "pipeline" / "watch"
HALIFAX = ZoneInfo("America/Halifax")
PARK_AFTER = 3     # failed runs in a row before a URL is parked
SLACK = dt.timedelta(hours=1)  # a daily run that starts a little early still counts as a day later


def now() -> dt.datetime:
    return dt.datetime.now(HALIFAX)


def read_json(path: Path, default=None):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return default


def write_json(path: Path, doc) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=path.parent, prefix=".", suffix=".json")
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        json.dump(doc, f, indent=1, ensure_ascii=False, sort_keys=True)
        f.write("\n")
    os.replace(tmp, path)


class Store:
    def __init__(self, root: Path = WATCH):
        self.root = root
        self.snapshots, self.queue, self.acked, self.digest = (root / d for d in ("snapshots", "queue", "acked", "digest"))

    def folder(self, url: str) -> Path:
        return self.snapshots / hashlib.sha256(url.encode("utf-8")).hexdigest()

    def meta(self, url: str) -> dict:
        return read_json(self.folder(url) / "meta.json") or {"url": url, "failures": 0, "parked": False}

    def save_meta(self, meta: dict) -> None:
        write_json(self.folder(meta["url"]) / "meta.json", meta)

    def save_copy(self, url: str, body: bytes, ctype: str, when: dt.datetime) -> tuple[str, str]:
        """Keeps a copy as served and its visible text; returns (file name, text)."""
        folder = self.folder(url)
        folder.mkdir(parents=True, exist_ok=True)
        ctype = ctype.lower()
        raw = folder / f"{when.strftime('%Y%m%dT%H%M%S%z')}{fetch_sources.suffix_for(ctype, body)}"
        raw.write_bytes(body)
        text = fetch_sources.text_of(raw, ctype)
        raw.with_suffix(".txt").write_text(text, encoding="utf-8")
        return raw.name, text

    def text(self, meta: dict) -> str | None:
        """The visible text of the copy meta points at, if there is one."""
        if not meta.get("copy"):
            return None
        try:
            return (self.folder(meta["url"]) / meta["copy"]).with_suffix(".txt").read_text(encoding="utf-8")
        except OSError:
            return None


def due(meta: dict, every_days: float, when: dt.datetime) -> bool:
    if meta.get("parked"):
        return False
    last = meta.get("last_checked")
    return not last or when - dt.datetime.fromisoformat(last) >= dt.timedelta(days=every_days) - SLACK


def settle(meta: dict, got, when: dt.datetime) -> str | None:
    """Records a fetch's outcome in meta. Returns the failure item it calls for: "fetch_failure" for the
    first failed run in a row, "parked" for a refusal or the PARK_AFTER-th failed run, else None."""
    meta["last_checked"] = when.isoformat(timespec="seconds")
    meta["status"] = got.status
    if got.outcome in ("ok", "unchanged"):
        meta.update(failures=0, failing_since=None, last_failure=None, final_url=got.final_url)
        if got.outcome == "ok":
            meta.update(etag=got.headers.get("etag"), last_modified=got.headers.get("last-modified"))
        return None
    if got.outcome == "parked":
        meta.update(parked=True, parked_reason=got.reason, parked_since=when.date().isoformat())
        return "parked"
    meta["failures"] = meta.get("failures", 0) + 1
    meta["failing_since"] = meta.get("failing_since") or when.date().isoformat()
    meta["last_failure"] = got.reason
    if meta["failures"] >= PARK_AFTER:
        meta.update(parked=True, parked_reason=f"{PARK_AFTER} failed runs in a row; the last: {got.reason}",
                    parked_since=when.date().isoformat())
        return "parked"
    return "fetch_failure" if meta["failures"] == 1 else None


def unpark(meta: dict) -> None:
    meta.update(parked=False, parked_reason=None, parked_since=None, failures=0, failing_since=None)


@dataclass
class Run:
    """One poll: what the adapters share, and what they report back for the digest."""
    store: Store
    fetcher: object            # watch_fetch.Fetcher; None on a dry run, which makes no requests
    when: dt.datetime
    denylist: list = field(default_factory=list)
    dry_run: bool = False
    checked: list = field(default_factory=list)   # every URL that answered this run (200 or 304)
    written: list = field(default_factory=list)   # queue items written this run
    notes: list = field(default_factory=list)     # lines for the console
