"""Adapter quote_drift: re-reads the pages FinePrint quotes and checks that each quote is still there, in the
same surroundings, using check_quotes.py's own matching (so the build and the watcher always agree).

Quotes come from everything build.py reads: pipeline/reviewed/*.json (app, company and tracker records, and
the laws that become bundle/jurisdictions.json), store taglines included. A source is watched at its
verify_url when that is the publisher's own copy (an API or a download of the same text), otherwise at its
url: an archived copy never changes. Items: quote_missing (with the text around the quote in the earlier copy
and around the same place now), context_changed (the quote is there, but its paragraph, or a neighbouring
paragraph that reads as a full sentence, changed: watch_text.context), url_moved, and the failure items.
Every item shows the 300 characters either side of the quote. The first check of a page compares with the
copy fetch_sources.py saved when the quote was verified, if there is one. A copy whose text the shared
extractor (fetch_sources.text_of) can't read parks its URL with a note. sources.json can give a source its
own max_bytes.
"""
from __future__ import annotations

import json
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from urllib.parse import urldefrag, urlparse

import check_quotes
import watch_queue
import watch_store
from watch_fetch import BODY_CAP, Fetched, denied
from watch_queue import sha256
from watch_store import Run
from watch_text import WINDOW, context, where_it_was, window

ARCHIVES = ("archive.org", "archive.ph", "archive.today")
SPARSE = 1000  # characters: a page this short with none of its quotes is a shell or a bot check, not a policy


class Unreadable(Exception):
    """The shared extractor got no usable text out of a copy (a scanned or damaged PDF, say)."""


@dataclass
class Quote:
    quote: str
    quote_id: str
    lookups: list = field(default_factory=list)   # where check_quotes.py looks for its saved copy, in order
    refs: set = field(default_factory=set)


def archived(url: str) -> bool:
    host = urlparse(url).hostname or ""
    return any(host == a or host.endswith("." + a) for a in ARCHIVES)


def watched(source: dict) -> str:
    verify = source.get("verify_url")
    return verify if verify and not archived(verify) else source["url"]


def sources_with_refs(reviewed: Path) -> list[tuple[dict, str]]:
    """Every source check_quotes.sources_in finds, with <file>:<record id>:<pointer> for each."""
    found = []
    for path in sorted(reviewed.glob("*.json")):
        doc = json.loads(path.read_text(encoding="utf-8"))

        def walk(node, trail: list):
            if isinstance(node, dict):
                if "url" in node and "quote" in node:
                    found.append((node, ref(path.name, doc, trail)))
                if "source_url" in node and "text" in node:  # a store tagline: its text is the quote
                    found.append(({"url": node["source_url"], "quote": node["text"]}, ref(path.name, doc, trail)))
                for key, value in node.items():
                    walk(value, trail + [key])
            elif isinstance(node, list):
                for i, value in enumerate(node):
                    walk(value, trail + [i])

        walk(doc, [])
    return found


def ref(file: str, doc: dict, trail: list) -> str:
    if len(trail) < 2:
        return f"{file}:/{'/'.join(map(str, trail))}"
    record = doc[trail[0]][trail[1]]
    record_id = record.get("package_id") or record.get("id") or f"{trail[0]}[{trail[1]}]"
    return f"{file}:{record_id}:/{'/'.join(map(str, trail[2:]))}"


def quotes_by_url(reviewed: Path = check_quotes.REVIEWED) -> dict[str, dict[str, Quote]]:
    """watched URL -> quote text -> Quote, merging the records that cite the same words."""
    by_url: dict[str, dict[str, Quote]] = defaultdict(dict)
    for source, where in sources_with_refs(reviewed):
        url = watched(source)
        q = by_url[url].get(source["quote"])
        if q is None:
            q = by_url[url][source["quote"]] = Quote(source["quote"], source.get("id") or "q-" + sha256(source["url"] + "\n" + source["quote"])[:12])
        q.refs.add(where)
        for key in ("verify_url", "url", "wayback_url", "vendor_archive_url"):
            if source.get(key) and source[key] not in q.lookups:
                q.lookups.append(source[key])
    return by_url


def saved_copies(folder: Path = check_quotes.SOURCES) -> dict[str, tuple[Path, str]]:
    """URL -> (saved text, sha256 of the page as served), from fetch_sources.py's index."""
    index = watch_store.read_json(folder / "index.json", {})
    texts = check_quotes.copies(folder)
    shas = {entry.get(k): entry.get("sha256") for entry in index.values() for k in ("url", "final_url") if entry.get(k)}
    return {url: (path, shas.get(url)) for url, path in texts.items() if path.exists()}


def by_host_in_turn(urls) -> list[str]:
    """One URL from each host in turn, so no host is asked twice while others wait."""
    queues = defaultdict(list)
    for url in sorted(urls):
        queues[urlparse(url).hostname or ""].append(url)
    order = []
    while any(queues.values()):
        for host in sorted(queues):
            if queues[host]:
                order.append(queues[host].pop(0))
    return order


def poll(run: Run, settings: dict, reviewed: Path = check_quotes.REVIEWED, saved_folder: Path = check_quotes.SOURCES) -> None:
    every, licence = settings.get("every_days", 7), settings.get("licence_class", "quotable")
    groups, saved = quotes_by_url(reviewed), saved_copies(saved_folder)
    for url in by_host_in_turn(groups):
        quotes = list(groups[url].values())
        refs = sorted(set().union(*(q.refs for q in quotes)))
        rule = denied(url, run.denylist)
        if rule:
            got = Fetched("refused", url, url, reason=f"{rule} is on the denylist")
            watch_queue.put(run, watch_queue.failure(run, "refused", url, {}, got, licence, refs))
            if not run.dry_run:  # the digest lists it with the sources checked by hand
                run.store.save_meta(dict(run.store.meta(url), refused=rule))
            continue
        meta = run.store.meta(url)
        if not watch_store.due(meta, every, run.when):
            continue
        if run.dry_run:
            run.notes.append(f"  due: {url} ({len(quotes)} quotes)")
            continue
        got = run.fetcher.fetch(url, meta, settings.get("sources", {}).get(url, {}).get("max_bytes", BODY_CAP))
        if got.outcome == "refused":  # a redirect onto the denylist
            watch_queue.put(run, watch_queue.failure(run, "refused", url, meta, got, licence, refs))
            meta["last_checked"] = run.when.isoformat(timespec="seconds")
            run.store.save_meta(meta)
            continue
        kind = watch_store.settle(meta, got, run.when)
        if kind:
            watch_queue.put(run, watch_queue.failure(run, kind, url, meta, got, licence, refs))
            run.notes.append(f"  {got.status or '---'}  {kind}: {url} ({got.reason})")
        if got.outcome in ("ok", "unchanged"):
            run.checked.append(url)
            moved(run, url, got, licence, refs)
        if got.outcome == "ok":
            try:
                compare(run, url, meta, got, quotes, saved, licence)
            except Exception as e:  # one unreadable copy mustn't stop the run: park it for a person to read
                why = str(e) if isinstance(e, Unreadable) else f"{e.__class__.__name__}: {e}"
                meta.update(parked=True, parked_reason=f"the shared extractor couldn't read its text ({why})"[:300],
                            parked_since=run.when.date().isoformat())
                watch_queue.put(run, watch_queue.failure(run, "parked", url, meta, got, licence, refs))
                run.notes.append(f"  {got.status}  parked: {url} ({meta['parked_reason']})")
        run.store.save_meta(meta)


def moved(run: Run, url: str, got: Fetched, licence: str, refs: list) -> None:
    final = urldefrag(got.final_url)[0]
    if final and final != urldefrag(url)[0]:
        watch_queue.put(run, watch_queue.make("url_moved", url, sha256(final), run.when, licence, f"{url} now ends at {final}.",
                                              final_url=final, fetched_at=run.when.isoformat(timespec="seconds"),
                                              http_status=got.status, record_refs=refs))


def compare(run: Run, url: str, meta: dict, got: Fetched, quotes: list[Quote], saved: dict, licence: str) -> None:
    """Keeps the new copy if its text changed (or it is the first), then checks each quote against it."""
    earlier, prior = run.store.text(meta), meta.get("sha256")
    name, text = run.store.save_copy(url, got.body, got.headers.get("content-type", ""), run.when)
    folder = run.store.folder(url)
    if earlier is not None and check_quotes.fold(earlier) == check_quotes.fold(text):
        (folder / name).unlink()
        (folder / name).with_suffix(".txt").unlink()
        return
    meta.update(copy=name, sha256=sha256(got.body))
    page, stamp = check_quotes.fold(text), run.when.isoformat(timespec="seconds")
    if len(page) < SPARSE and not any(check_quotes.locate(page, q.quote) for q in quotes):
        if name.endswith(".pdf"):
            raise Unreadable(f"{len(page)} characters from a PDF")
        missing = "without its quote" if len(quotes) == 1 else f"with none of its {len(quotes)} quotes"
        notes = f"The page came back with {len(page)} characters of text, {missing}; it may need JavaScript, which the watcher never runs."
        watch_queue.put(run, watch_queue.make("fetch_failure", url, meta["sha256"], run.when, licence, notes, final_url=got.final_url,
                                              fetched_at=stamp, prior=prior, http_status=got.status,
                                              record_refs=set().union(*(q.refs for q in quotes))))
        return
    for q in quotes:
        before, before_sha, basis = earlier, prior, "the watcher's previous copy"
        if before is None:  # the first check: the copy the quote was verified against
            copy = next((saved[u] for u in q.lookups if u in saved), None)
            before, before_sha = (copy[0].read_text(encoding="utf-8"), copy[1]) if copy else (None, None)
            basis = "the copy saved when the quote was verified" if copy else "no earlier copy (the first check)"
        old_page = check_quotes.fold(before) if before is not None else None
        at, old_at = check_quotes.locate(page, q.quote), check_quotes.locate(old_page, q.quote) if old_page else None
        common = dict(final_url=got.final_url, fetched_at=stamp, prior=before_sha, record_refs=q.refs, quote_id=q.quote_id,
                      quote=q.quote, http_status=got.status)
        if at is None:
            spot = where_it_was(page, q.quote, old_page)
            diff = {"before": window(old_page, *old_at) if old_at else None, "after": window(page, spot) if spot is not None else None}
            notes = f"The quote is not on the page as fetched. Compared with {basis}."
            watch_queue.put(run, watch_queue.make("quote_missing", url, meta["sha256"], run.when, licence, notes, diff_window=diff, **common))
        elif old_at and changed_around(before, text, q.quote, (old_page, old_at), (page, at)):
            diff = {"before": window(old_page, *old_at), "after": window(page, *at)}
            notes = ("The quote is still on the page; its paragraph, or a neighbouring paragraph that reads as a full sentence, "
                     f"changed. Compared with {basis}.")
            if diff["before"] == diff["after"]:
                notes += f" The change is more than {WINDOW} characters from the quote."
            watch_queue.put(run, watch_queue.make("context_changed", url, meta["sha256"], run.when, licence, notes, diff_window=diff, **common))


def changed_around(before: str, text: str, quote: str, old: tuple, new: tuple) -> bool:
    """The quote's paragraph and its full-sentence neighbours differ; the windows decide only when a page's
    paragraphs can't be told apart."""
    was, now = context(before, quote), context(text, quote)
    if was is not None and now is not None:
        return was != now
    return window(old[0], *old[1]) != window(new[0], *new[1])
