#!/usr/bin/env python3
"""Tests for the watcher's queue, watch_queue.py:  python3 pipeline/test_watch_queue.py"""
from __future__ import annotations

import contextlib
import datetime as dt
import hashlib
import io
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import watch  # noqa: E402
import watch_queue  # noqa: E402
import watch_store  # noqa: E402
from watch_fetch import Fetched  # noqa: E402

WHEN = dt.datetime(2026, 10, 8, 9, 30, tzinfo=watch_store.HALIFAX)
SHA = "a" * 64


def item(**kw) -> dict:
    fields = dict(quote_id="q-123456789abc", quote="to personalize ads", record_refs=["app-x.json:com.x:/data_flows/0/sources/0"],
                  diff_window={"before": "… to personalize ads …", "after": "… to show ads …"}, http_status=200, final_url="https://a.example/p",
                  fetched_at=WHEN.isoformat(timespec="seconds"), prior="b" * 64)
    fields.update(kw)
    return watch_queue.make("quote_missing", "https://a.example/p", SHA, WHEN, "quotable", "The quote is no longer on the page.", **fields)


class QueueTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root)
        self.store = watch_store.Store(self.root)

    def test_an_item_validates_and_its_id_is_the_finding(self):
        i = item()
        self.assertEqual(watch_queue.problems(i), [])
        self.assertEqual(i["id"], hashlib.sha256(("quote_missing" + "https://a.example/p" + SHA + "q-123456789abc").encode()).hexdigest()[:16])
        self.assertNotEqual(i["id"], item(quote_id="q-other")["id"])  # two quotes missing from one page are two items

    def test_items_hold_facts_only(self):
        for key in ("status_suggestion", "tier", "confidence"):
            self.assertTrue(watch_queue.problems(dict(item(), **{key: "flagged"})), key)
        with self.assertRaises(ValueError):
            watch_queue.write(self.store, dict(item(), kind="looks_bad"))

    def test_a_finding_is_written_once_even_after_it_is_acked(self):
        self.assertTrue(watch_queue.write(self.store, item()))
        self.assertFalse(watch_queue.write(self.store, item()))
        watch_queue.ack(self.store, item()["id"], "", WHEN)
        self.assertFalse(watch_queue.write(self.store, item()))
        self.assertTrue(watch_queue.write(self.store, item(prior=SHA, quote_id="q-another")))

    def test_ack_moves_the_item_with_its_note(self):
        i = item()
        watch_queue.write(self.store, i)
        with contextlib.redirect_stdout(io.StringIO()):
            code = watch.main(["ack", i["id"][:8], "--note", "record updated"], root=self.root)
        self.assertEqual(code, 0)
        self.assertFalse((self.store.queue / f"{i['id']}.json").exists())
        acked = watch_store.read_json(self.store.acked / f"{i['id']}.json")
        self.assertEqual(acked["ack"]["note"], "record updated")
        self.assertEqual(watch_queue.problems(acked), [])
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(watch.main(["ack", "ffffffffffffffff"], root=self.root), 1)

    def test_acking_a_parked_url_lets_it_be_checked_again(self):
        url = "https://a.example/p"
        meta = self.store.meta(url)
        run = watch_store.Run(self.store, None, WHEN)
        got = Fetched("parked", url, url, 403, reason="HTTP 403")
        self.assertEqual(watch_store.settle(meta, got, WHEN), "parked")
        self.store.save_meta(meta)
        parked = watch_queue.failure(run, "parked", url, meta, got, "quotable")
        self.assertEqual(watch_queue.problems(parked), [])
        watch_queue.write(self.store, parked)
        self.assertFalse(watch_store.due(self.store.meta(url), 7, WHEN + dt.timedelta(days=30)))
        watch_queue.ack(self.store, parked["id"], "checked by hand", WHEN)
        self.assertTrue(watch_store.due(self.store.meta(url), 7, WHEN + dt.timedelta(days=30)))

    def test_three_failed_runs_park_a_url_with_one_failure_item_and_one_parked_item(self):
        meta, url = self.store.meta("https://a.example/p"), "https://a.example/p"
        got = Fetched("failed", url, url, 503, reason="HTTP 503 after 3 tries")
        kinds = [watch_store.settle(meta, got, WHEN + dt.timedelta(days=d)) for d in (0, 7, 14)]
        self.assertEqual(kinds, ["fetch_failure", None, "parked"])
        self.assertTrue(meta["parked"])
        self.assertEqual(meta["failing_since"], "2026-10-08")


if __name__ == "__main__":
    unittest.main()
