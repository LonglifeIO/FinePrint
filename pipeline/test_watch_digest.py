#!/usr/bin/env python3
"""Tests for the watcher's digest and a whole poll, watch.py and watch_digest.py:  python3 pipeline/test_watch_digest.py"""
from __future__ import annotations

import contextlib
import datetime as dt
import functools
import io
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import watch  # noqa: E402
import watch_queue  # noqa: E402
import watch_quotes  # noqa: E402
import watch_rss  # noqa: E402
import watch_store  # noqa: E402
from test_watch_fetch import ALLOW, Clock, FakeHttp  # noqa: E402
from test_watch_quotes import PAGE, QUOTE, URL, record  # noqa: E402

FIXTURES = Path(__file__).resolve().parent / "watch" / "fixtures"
WHEN = dt.datetime(2026, 10, 8, 9, 30, tzinfo=watch_store.HALIFAX)
FEED = "https://regulator.example/rss/"


class DigestTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root)
        self.reviewed, saved = self.root / "reviewed", self.root / "saved"
        self.reviewed.mkdir()
        saved.mkdir()
        doc = record()
        doc["apps"][0]["display_name"] = "Life360"
        (self.reviewed / "app-example.json").write_text(json.dumps(doc), encoding="utf-8")
        self.store = watch_store.Store(self.root / "watch")
        self.config = {"denylist": ["play.google.com", "canlii.org"], "adapters": {
            "quote_drift": {"every_days": 7, "licence_class": "quotable"},
            "rss": {"every_days": 1, "max_age_days": 30, "feeds": [{"name": "Example regulator", "url": FEED, "licence_class": "lead_only"}]}}}
        self.adapters = {"quote_drift": functools.partial(watch_quotes.poll, reviewed=self.reviewed, saved_folder=saved),
                         "rss": functools.partial(watch_rss.poll, reviewed=self.reviewed)}

    def poll(self, routes: dict, days: int = 0, local: dict | None = None) -> tuple[int, str]:
        clock = Clock()
        http = FakeHttp({"https://a.example/robots.txt": [ALLOW], "https://b.example/robots.txt": [ALLOW],
                         "https://regulator.example/robots.txt": [ALLOW], **routes}, clock)
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = watch.poll(self.store, self.config, local or {"contact": "o@example.org"}, http=http, sleep=clock.sleep,
                              when=WHEN + dt.timedelta(days=days), adapters=self.adapters)
        self.http = http
        return code, out.getvalue()

    def digest(self, days: int = 0) -> str:
        return (self.store.digest / f"{(WHEN + dt.timedelta(days=days)).date()}.md").read_text(encoding="utf-8")

    def test_a_poll_writes_the_digest_in_order_one_line_per_item(self):
        changed = PAGE.replace(QUOTE, "We share data")
        code, _ = self.poll({URL: [(301, {"location": "https://b.example/policy"}, b"")],
                             "https://b.example/policy": [(200, {"content-type": "text/html"}, changed.encode())],
                             FEED: [(200, {}, (FIXTURES / "made-up-life360.xml").read_bytes())]})
        self.assertEqual(code, 0)
        md = self.digest()
        headings = [l for l in md.splitlines() if l.startswith("## ")]
        self.assertEqual(headings, ["## Quotes no longer on the page", "## Pages that moved",
                                    "## New regulator items naming a recorded company, app or tracker", "## Failures, parked URLs and refusals",
                                    "## Checked by hand before each release (1)"])
        items = {i["kind"]: i for i in (watch_store.read_json(p) for p in self.store.queue.glob("*.json"))}
        self.assertEqual(sorted(items), ["new_event", "quote_missing", "refused", "url_moved"])
        for kind, item in items.items():
            url = item["event"]["link"] if kind == "new_event" else item["source_url"]
            [line] = [l for l in md.splitlines() if l.endswith(f"({item['id']})")]
            self.assertIn(f" {url} (", line)
        self.assertIn("Not fetched: play.google.com is on the denylist (com.example.app). https://play.google.com/", md)
        doc = watch_store.read_json(self.store.digest / f"{WHEN.date()}.json")
        self.assertEqual([i["kind"] for i in doc["items"]], ["quote_missing", "url_moved", "new_event", "refused"])
        self.assertEqual(doc["sources_checked"], 2)

    def test_a_quiet_day_says_how_many_sources_were_checked(self):
        self.poll({URL: [(200, {"content-type": "text/html"}, PAGE.encode())], FEED: [(200, {}, (FIXTURES / "opc-pipeda.xml").read_bytes())]})
        self.poll({URL: [(304, {}, b"")], FEED: [(200, {}, (FIXTURES / "opc-pipeda.xml").read_bytes())]}, days=7)
        md = self.digest(days=7)
        self.assertTrue(md.startswith(f"# FinePrint watcher digest, {(WHEN + dt.timedelta(days=7)).date()}\n\nNo changes at 2 sources checked.\n\n"))
        self.assertNotIn("\n- ", md[:md.index("## Checked by hand")])  # no items, only the standing list

    def test_the_digest_is_copied_where_local_json_says(self):
        target = self.root / "hermes"
        self.poll({URL: [(200, {"content-type": "text/html"}, PAGE.encode())]}, local={"contact": "o@example.org", "copy_to": str(target)})
        self.assertEqual(sorted(p.name for p in target.iterdir()), [f"{WHEN.date()}.json", f"{WHEN.date()}.md"])

    def test_a_dry_run_fetches_and_writes_nothing(self):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = watch.poll(self.store, self.config, {"contact": "o@example.org"}, dry_run=True, when=WHEN, adapters=self.adapters,
                              http=FakeHttp({}))
        self.assertEqual(code, 0)
        self.assertIn(f"due: {URL}", out.getvalue())
        self.assertFalse(self.store.root.exists())

    def test_failures_are_grouped_by_kind(self):
        self.poll({URL: [(403, {}, b"no")], FEED: [(404, {}, b"")]})
        md = self.digest()
        tail = md[md.index("## Failures"):md.index("## Checked by hand")].splitlines()[2:]
        self.assertEqual([l.split(":")[0] for l in tail if l.startswith("- ")], ["- Fetch failed", "- Parked", "- Not fetched"])

    def test_every_digest_lists_the_sources_checked_by_hand(self):
        self.poll({URL: [(403, {}, b"no")], FEED: [(200, {}, (FIXTURES / "opc-pipeda.xml").read_bytes())]})
        self.poll({FEED: [(200, {}, (FIXTURES / "opc-pipeda.xml").read_bytes())]}, days=1)  # a quiet day
        for days in (0, 1):
            md = self.digest(days)
            hand = md[md.index("## Checked by hand before each release (2)"):].splitlines()[2:]
            self.assertEqual(hand, [f"- {URL} (HTTP 403)",
                                    "- https://play.google.com/store/apps/details?id=com.example.app (play.google.com is on the denylist)"])
        self.assertIn("No changes at 1 sources checked.", self.digest(1))

    def test_every_item_a_poll_writes_validates(self):
        self.poll({URL: [(403, {}, b"no")], FEED: [(200, {}, (FIXTURES / "made-up-life360.xml").read_bytes())]})
        items = [watch_store.read_json(p) for p in self.store.queue.glob("*.json")]
        self.assertEqual(sorted(i["kind"] for i in items), ["new_event", "parked", "refused"])
        self.assertTrue(all(watch_queue.problems(i) == [] for i in items))


if __name__ == "__main__":
    unittest.main()
