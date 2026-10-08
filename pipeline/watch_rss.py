"""Adapter rss: reads regulators' feeds (RSS 2.0 or Atom, with xml.etree) and queues each entry whose title or
summary names a company, app or tracker FinePrint has a record of (whole words, any case): a new_event with
its title, link, date and the names it matched. Each entry is queued once (by its guid or id, else its link).
Entries older than max_age_days are skipped, so a feed's first check doesn't replay its archive. Feeds are
listed in pipeline/watch/sources.json with their licence class: quotable, or lead_only until their terms are
checked (a lead needs a primary source before anything is quoted), and may set their own max_bytes.
"""
from __future__ import annotations

import datetime as dt
import email.utils
import json
import re
import xml.etree.ElementTree as ET
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urljoin

import check_quotes
import fetch_sources
import watch_queue
import watch_store
from watch_fetch import BODY_CAP, Fetched, denied
from watch_queue import sha256
from watch_store import Run

ATOM = "{http://www.w3.org/2005/Atom}"


@dataclass
class Entry:
    id: str
    title: str
    link: str
    published: dt.datetime | None
    summary: str


def plain(markup: str | None) -> str:
    return " ".join(fetch_sources.text_of_html(markup or "").split())


def when(value: str | None) -> dt.datetime | None:
    """An RSS pubDate (RFC 822, two-digit years too) or an Atom date (RFC 3339), in Halifax time."""
    if not value or not value.strip():
        return None
    value = value.strip()
    try:
        parsed = email.utils.parsedate_to_datetime(value)
    except (TypeError, ValueError, IndexError):
        try:
            parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
        except ValueError:
            return None
    return (parsed if parsed.tzinfo else parsed.replace(tzinfo=dt.timezone.utc)).astimezone(watch_store.HALIFAX)


def parse(body: bytes, base: str) -> list[Entry]:
    root = ET.fromstring(body)
    entries = []
    for item in root.iter("item"):
        link = urljoin(base, (item.findtext("link") or "").strip())
        guid = (item.findtext("guid") or "").strip()
        entries.append(Entry(urljoin(base, guid) if guid else link, plain(item.findtext("title")), link,
                             when(item.findtext("pubDate")), plain(item.findtext("description"))))
    for entry in root.iter(ATOM + "entry"):
        links = entry.findall(ATOM + "link")
        alternate = next((l for l in links if l.get("rel", "alternate") == "alternate"), links[0] if links else None)
        link = urljoin(base, alternate.get("href", "")) if alternate is not None else ""
        summary = entry.findtext(ATOM + "summary") or entry.findtext(ATOM + "content")
        entries.append(Entry((entry.findtext(ATOM + "id") or "").strip() or link, plain(entry.findtext(ATOM + "title")), link,
                             when(entry.findtext(ATOM + "published") or entry.findtext(ATOM + "updated")), plain(summary)))
    return entries


def record_names(reviewed: Path = check_quotes.REVIEWED) -> dict[str, tuple[re.Pattern, list[str]]]:
    """name -> (whole-word pattern, the records that carry it): companies' names, short names and aliases,
    apps' display names and trackers' names."""
    refs = defaultdict(set)
    for path in sorted(reviewed.glob("*.json")):
        doc = json.loads(path.read_text(encoding="utf-8"))
        for c in doc.get("companies", []):
            for name in [c.get("name"), c.get("short_name"), *c.get("aliases", [])]:
                refs[name].add(f"{path.name}:{c['id']}")
        for a in doc.get("apps", []):
            refs[a.get("display_name")].add(f"{path.name}:{a['package_id']}")
        for t in doc.get("trackers", []):
            refs[t.get("name")].add(f"{path.name}:{t['id']}")
    return {name: (re.compile(r"(?<!\w)" + re.escape(name) + r"(?!\w)", re.IGNORECASE), sorted(found))
            for name, found in refs.items() if name and len(name.strip()) >= 3}


def poll(run: Run, settings: dict, reviewed: Path = check_quotes.REVIEWED) -> None:
    names = record_names(reviewed)
    for feed in settings.get("feeds", []):
        url, licence = feed["url"], feed["licence_class"]
        rule = denied(url, run.denylist)
        if rule:
            watch_queue.put(run, watch_queue.failure(run, "refused", url, {}, Fetched("refused", url, url, reason=f"{rule} is on the denylist"), licence))
            if not run.dry_run:  # the digest lists it with the sources checked by hand
                run.store.save_meta(dict(run.store.meta(url), refused=rule))
            continue
        meta = run.store.meta(url)
        if not watch_store.due(meta, feed.get("every_days", settings.get("every_days", 1)), run.when):
            continue
        if run.dry_run:
            run.notes.append(f"  due: {feed['name']} ({url})")
            continue
        got = run.fetcher.fetch(url, meta, feed.get("max_bytes", BODY_CAP))
        kind = watch_store.settle(meta, got, run.when) if got.outcome != "refused" else "refused"
        if kind:
            watch_queue.put(run, watch_queue.failure(run, kind, url, meta, got, licence))
            run.notes.append(f"  {got.status or '---'}  {kind}: {feed['name']} ({got.reason})")
        if got.outcome in ("ok", "unchanged"):
            run.checked.append(url)
        if got.outcome == "ok":
            read_feed(run, feed, meta, got, names, settings.get("max_age_days", 30))
        run.store.save_meta(meta)


def read_feed(run: Run, feed: dict, meta: dict, got: Fetched, names: dict, max_age_days: float) -> None:
    stamp, digest = run.when.isoformat(timespec="seconds"), sha256(got.body)
    if digest != meta.get("sha256"):
        folder, name = run.store.folder(feed["url"]), f"{run.when.strftime('%Y%m%dT%H%M%S%z')}.xml"
        folder.mkdir(parents=True, exist_ok=True)
        (folder / name).write_bytes(got.body)
        meta.update(copy=name, sha256=digest)
    try:
        entries = parse(got.body, got.final_url)
    except ET.ParseError as e:
        watch_queue.put(run, watch_queue.make("fetch_failure", feed["url"], digest, run.when, feed["licence_class"],
                                              f"The feed doesn't parse as RSS or Atom: {e}.", final_url=got.final_url,
                                              fetched_at=stamp, http_status=got.status))
        return
    oldest = run.when - dt.timedelta(days=max_age_days)
    for entry in entries:
        if entry.published and entry.published < oldest:
            continue
        hits = {name: refs for name, (pattern, refs) in names.items() if pattern.search(f"{entry.title}\n{entry.summary}")}
        if not hits:
            continue
        matched = sorted(hits)
        published = entry.published.isoformat(timespec="seconds") if entry.published else None
        notes = f"{feed['name']} published “{entry.title}”" + (f" on {entry.published.date()}" if entry.published else "") + f", naming {', '.join(matched)}."
        event = {"feed": feed["name"], "title": entry.title, "link": entry.link, "published": published, "matched": matched}
        watch_queue.put(run, watch_queue.make("new_event", entry.link if entry.link.startswith("http") else feed["url"], sha256(entry.id or entry.link),
                                              run.when, feed["licence_class"], notes, final_url=got.final_url, fetched_at=stamp,
                                              http_status=got.status, record_refs=[r for refs in hits.values() for r in refs], event=event))
