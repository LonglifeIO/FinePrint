"""Where a quote sits in a page's text, for the watcher's quote_drift adapter. Pages are compared as
check_quotes.py folds them, so the watcher and the build agree on whether a quote is there.

window: the text within WINDOW characters either side of a quote, which every item shows its reader.
context: what decides whether the text around a quote changed: the paragraph holding the quote, plus the
paragraph either side when it reads as a full sentence (terminal punctuation, at least 40 characters).
Related links, sidebars and menus rarely do, so their churn doesn't count as a change.
where_it_was: where a missing quote would be now.
"""
from __future__ import annotations

import re

import check_quotes

WINDOW = 300
FULL_SENTENCE = re.compile(r"[.!?…][\"'”’)\]]*$")


def paragraphs(text: str) -> list[str]:
    """The page's blocks (fetch_sources.text_of writes one per line), each folded."""
    return [b for b in (check_quotes.fold(line) for line in text.splitlines()) if b]


def full_sentence(block: str) -> bool:
    return len(block) >= 40 and bool(FULL_SENTENCE.search(block))


def context(text: str, quote: str) -> str | None:
    """The paragraph(s) holding the quote, with each neighbour that reads as a full sentence; None if the
    quote can't be placed in a paragraph."""
    parts = paragraphs(text)
    starts, pos = [], 0
    for part in parts:
        starts.append(pos)
        pos += len(part) + 1
    at = check_quotes.locate(" ".join(parts), quote)
    if at is None:
        return None
    first = max(i for i, start in enumerate(starts) if start <= at[0])
    last = max(i for i, start in enumerate(starts) if start < at[1])
    if first > 0 and full_sentence(parts[first - 1]):
        first -= 1
    if last + 1 < len(parts) and full_sentence(parts[last + 1]):
        last += 1
    return " ".join(parts[first:last + 1])


def window(page: str, at: int, end: int | None = None) -> str:
    return page[max(0, at - WINDOW):(at if end is None else end) + WINDOW]


def where_it_was(new_page: str, quote: str, old_page: str | None) -> int | None:
    """Where a missing quote would be in the new page: one of its fragments, else the words just before or
    after it in the old page, else the same share of the way through."""
    for fragment in sorted((check_quotes.fold(f) for f in quote.split("…")), key=len, reverse=True):
        if len(fragment) >= 20 and fragment in new_page:
            return new_page.index(fragment)
    at = check_quotes.locate(old_page, quote) if old_page else None
    if at is None:
        return None
    share = round(at[0] / max(1, len(old_page)) * len(new_page))
    before, after = old_page[max(0, at[0] - 80):at[0]], old_page[at[1]:at[1] + 80]
    # Boilerplate repeats, so of the places the neighbouring words appear, take the one nearest the same share.
    for context, offset in ((before, len(before)), (after, 0)):
        spots = [i + offset for i in occurrences(new_page, context)] if len(context) >= 20 else []
        if spots:
            return min(spots, key=lambda spot: abs(spot - share))
    return share


def occurrences(page: str, text: str) -> list[int]:
    found, at = [], page.find(text)
    while at >= 0:
        found.append(at)
        at = page.find(text, at + 1)
    return found
