#!/usr/bin/env python3
"""Check every source quote in the reviewed records against the saved copy of its page.

    python3 pipeline/check_quotes.py                                   # every pipeline/reviewed/*.json
    python3 pipeline/check_quotes.py pipeline/reviewed/app-facebook.json

A quote passes when each of its fragments (joined by " … ") appears, in order, in the visible text
of the copy fetch_sources.py saved of the source's verify_url, url or wayback_url, once curly
quotes, dashes and whitespace are folded. Quotes from copyrighted pages must stay under 15 words;
works of the US federal government are exempt (17 U.S.C. § 105). The saved copies are gitignored,
so this runs on the machine that fetched them, not in CI.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from urllib.parse import urlparse

REPO = Path(__file__).resolve().parent.parent
REVIEWED = REPO / "pipeline" / "reviewed"
SOURCES = REPO / "pipeline" / "raw" / "sources"  # gitignored; written by fetch_sources.py
WORD_CAP = 15
# US federal works: agency releases and federal court opinions.
PUBLIC_DOMAIN_HOSTS = ("ftc.gov", "justice.gov", "uscourts.gov", "courtlistener.com")
FOLD = str.maketrans({"‘": "'", "’": "'", "“": '"', "”": '"', "–": "-", "—": "-",
                      " ": " ", "­": None, "​": None, "‌": None, "‍": None, "⁠": None,
                      "﻿": None})


def fold(text: str) -> str:
    """Curly quotes and dashes to plain ones, one space for any run, and none where a tag boundary
    split a link from its punctuation ("geolocation , incognito")."""
    text = re.sub(r"\s+", " ", text.translate(FOLD))
    return re.sub(r"([(\[]) | ([,.;:!?)\]])", r"\1\2", text).strip()


def words(quote: str) -> int:
    return sum(1 for token in quote.replace("…", " ").split() if re.search(r"\w", token))


def found_in_order(text: str, quote: str) -> bool:
    page, pos = fold(text), 0
    for fragment in (fold(f) for f in quote.split("…")):
        at = page.find(fragment, pos)
        if not fragment or at < 0:
            return False
        pos = at + len(fragment)
    return True


def public_domain(url: str) -> bool:
    host = urlparse(url).hostname or ""
    return any(host == h or host.endswith("." + h) for h in PUBLIC_DOMAIN_HOSTS)


def copies(sources_dir: Path) -> dict[str, Path]:
    """URL -> saved text, from fetch_sources.py's index."""
    try:
        index = json.loads((sources_dir / "index.json").read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}
    by_url = {}
    for name, entry in index.items():
        for key in ("url", "final_url"):
            if entry.get(key):
                by_url[entry[key]] = sources_dir / f"{name}.txt"
    return by_url


def problems(source: dict, saved: dict[str, Path]) -> list[str]:
    found = []
    if not public_domain(source["url"]) and words(source["quote"]) >= WORD_CAP:
        found.append(f"{words(source['quote'])} words (cap is under {WORD_CAP})")
    lookups = [source.get(k) for k in ("verify_url", "url", "wayback_url", "vendor_archive_url") if source.get(k)]
    copy = next((saved[u] for u in lookups if u in saved and saved[u].exists()), None)
    if copy is None:
        found.append("no saved copy (run fetch_sources.py)")
    elif not found_in_order(copy.read_text(encoding="utf-8"), source["quote"]):
        found.append(f"not found in {copy.name}")
    return found


def sources_in(node, out: list) -> list:
    if isinstance(node, dict):
        if "url" in node and "quote" in node:
            out.append(node)
        for value in node.values():
            sources_in(value, out)
    elif isinstance(node, list):
        for value in node:
            sources_in(value, out)
    return out


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("files", nargs="*", type=Path, help="reviewed files (default: all)")
    parser.add_argument("--sources", type=Path, default=SOURCES, help="folder of saved copies")
    args = parser.parse_args()

    saved = copies(args.sources)
    seen, bad = set(), 0
    for path in args.files or sorted(REVIEWED.glob("*.json")):
        for source in sources_in(json.loads(path.read_text(encoding="utf-8")), []):
            key = (source["url"], source.get("verify_url"), source["quote"])
            if key in seen:
                continue
            seen.add(key)
            found = problems(source, saved)
            bad += bool(found)
            label = source.get("title", source["url"])[:40]
            print(f"{'BAD' if found else 'OK '} {words(source['quote']):2}w  {label:40}  {source['quote'][:60]}"
                  + (f"\n      {path.name}: {'; '.join(found)}" if found else ""))
    print(f"{len(seen)} distinct quotes, {bad} with problems")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
