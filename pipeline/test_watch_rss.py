#!/usr/bin/env python3
"""Tests for the rss adapter, watch_rss.py:  python3 pipeline/test_watch_rss.py"""
from __future__ import annotations

import datetime as dt
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import watch_fetch  # noqa: E402
import watch_queue  # noqa: E402
import watch_rss  # noqa: E402
import watch_store  # noqa: E402
from test_watch_fetch import Clock, FakeHttp  # noqa: E402

FIXTURES = Path(__file__).resolve().parent / "watch" / "fixtures"
WHEN = dt.datetime(2026, 10, 8, 9, 30, tzinfo=watch_store.HALIFAX)
FEED = "https://regulator.example/rss/"


class RssTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root)
        self.reviewed = self.root / "reviewed"
        self.reviewed.mkdir()
        (self.reviewed / "app-life360.json").write_text(json.dumps({
            "apps": [{"package_id": "com.life360.android.safetymapd", "display_name": "Life360"}],
            "companies": [{"id": "co-allstate", "name": "The Allstate Corporation", "short_name": "Allstate"}]}), encoding="utf-8")
        self.store = watch_store.Store(self.root / "watch")

    def poll(self, body: bytes, days: int = 0) -> list[dict]:
        clock = Clock()
        http = FakeHttp({"https://regulator.example/robots.txt": [(404, {}, b"")], FEED: [(200, {"content-type": "application/rss+xml"}, body)]}, clock)
        run = watch_store.Run(self.store, watch_fetch.Fetcher("o@example.org", [], http=http, sleep=clock.sleep, clock=clock), WHEN + dt.timedelta(days=days), [])
        watch_rss.poll(run, {"every_days": 1, "max_age_days": 30, "feeds": [{"name": "Example regulator", "url": FEED, "licence_class": "lead_only"}]}, self.reviewed)
        for item in run.written:
            self.assertEqual(watch_queue.problems(item), [])
        return run.written

    def test_the_real_samples_parse(self):
        opc = watch_rss.parse((FIXTURES / "opc-pipeda.xml").read_bytes(), "https://www.priv.gc.ca/en/rss/pipeda/")
        bc = watch_rss.parse((FIXTURES / "oipc-rulings.xml").read_bytes(), "https://www.oipc.bc.ca/RulingsAndReports.xml")
        self.assertEqual((len(opc), len(bc)), (3, 3))
        self.assertTrue(opc[0].link.startswith("https://www.priv.gc.ca/en/opc-actions-and-decisions/"))  # the feed's links are relative
        self.assertEqual(opc[0].published.year, 2026)  # and its years have two digits
        self.assertTrue(all(e.title and e.link.startswith("https://") and e.published for e in opc + bc))

    def test_an_entry_naming_a_recorded_app_is_one_new_event(self):
        items = self.poll((FIXTURES / "made-up-life360.xml").read_bytes())
        self.assertEqual([i["kind"] for i in items], ["new_event"])
        item = items[0]
        self.assertEqual(item["event"]["matched"], ["Life360"])
        self.assertEqual(item["event"]["link"], "https://regulator.example/findings/2026-001/")
        self.assertEqual(item["event"]["published"], "2026-10-06T01:00:00-03:00")
        self.assertEqual(item["record_refs"], ["app-life360.json:com.life360.android.safetymapd"])
        self.assertEqual(item["licence_class"], "lead_only")

    def test_entries_naming_no_one_raise_nothing(self):
        self.assertEqual(self.poll((FIXTURES / "opc-pipeda.xml").read_bytes()), [])

    def test_an_entry_is_queued_once_across_runs(self):
        body = (FIXTURES / "made-up-life360.xml").read_bytes()
        self.assertEqual(len(self.poll(body)), 1)
        self.assertEqual(self.poll(body, days=1), [])

    def test_names_match_whole_words_in_any_case(self):
        names = watch_rss.record_names(self.reviewed)
        hits = lambda text: sorted(n for n, (p, _) in names.items() if p.search(text))
        self.assertEqual(hits("LIFE360 and allstate's filing"), ["Allstate", "Life360"])
        self.assertEqual(hits("Life3600, Allstates"), [])
        self.assertEqual(hits("The Allstate Corporation."), ["Allstate", "The Allstate Corporation"])

    def test_atom_feeds_parse_too(self):
        atom = b"""<feed xmlns="http://www.w3.org/2005/Atom"><title>t</title><entry><id>urn:x:1</id><title>Life360 order</title>
        <link rel="alternate" href="/orders/1"/><updated>2026-10-07T12:00:00Z</updated><summary>s</summary></entry></feed>"""
        [entry] = watch_rss.parse(atom, "https://regulator.example/feed")
        self.assertEqual((entry.id, entry.link, entry.published.isoformat()), ("urn:x:1", "https://regulator.example/orders/1", "2026-10-07T09:00:00-03:00"))


if __name__ == "__main__":
    unittest.main()
