#!/usr/bin/env python3
"""Tests for the quote_drift adapter, watch_quotes.py, against a fake web:  python3 pipeline/test_watch_quotes.py"""
from __future__ import annotations

import collections
import datetime as dt
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import check_quotes  # noqa: E402
import fetch_sources  # noqa: E402
import watch_fetch  # noqa: E402
import watch_queue  # noqa: E402
import watch_quotes  # noqa: E402
import watch_store  # noqa: E402
import watch_digest  # noqa: E402
import watch_text  # noqa: E402
from test_watch_fetch import ALLOW, Clock, FakeHttp  # noqa: E402

FIXTURES = Path(__file__).resolve().parent / "watch" / "fixtures"

WHEN = dt.datetime(2026, 10, 8, 9, 30, tzinfo=watch_store.HALIFAX)
URL = "https://a.example/policy"
QUOTE = "We share driving data with insurers"
FILLER = "Our services help families stay connected. " * 12
PAGE = f"<h1>Privacy</h1><p>{FILLER}</p><p>{QUOTE} for risk scoring.</p><p>{FILLER}</p>"
DENYLIST = ["play.google.com", "canlii.org"]


def record(url: str = URL, quote: str = QUOTE, **source) -> dict:
    return {"apps": [{"package_id": "com.example.app", "store_tagline": {"text": "Stay close", "source_url": "https://play.google.com/store/apps/details?id=com.example.app"},
                      "data_flows": [{"sources": [dict(url=url, quote=quote, title="policy", **source)]}]}]}


class QuoteDriftTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root)
        self.reviewed, self.saved = self.root / "reviewed", self.root / "saved"
        self.reviewed.mkdir()
        self.saved.mkdir()
        self.write_record(record())
        self.store = watch_store.Store(self.root / "watch")

    def write_record(self, doc: dict):
        (self.reviewed / "app-example.json").write_text(json.dumps(doc), encoding="utf-8")

    def poll(self, routes: dict, days: int = 0, **settings) -> list[dict]:
        clock = Clock()
        http = FakeHttp({"https://a.example/robots.txt": [ALLOW], "https://b.example/robots.txt": [ALLOW], **routes}, clock)
        fetcher = watch_fetch.Fetcher("owner@example.org", DENYLIST, http=http, sleep=clock.sleep, clock=clock)
        run = watch_store.Run(self.store, fetcher, WHEN + dt.timedelta(days=days), DENYLIST)
        watch_quotes.poll(run, {"every_days": 7, "licence_class": "quotable", **settings}, self.reviewed, self.saved)
        self.http = http
        for item in run.written:
            self.assertEqual(watch_queue.problems(item), [], item["kind"])
        return [i for i in run.written if i["kind"] != "refused"]

    def kinds(self, items) -> list[str]:
        return sorted(i["kind"] for i in items)

    def test_a_changed_quote_is_one_quote_missing_with_the_text_around_it(self):
        self.assertEqual(self.poll({URL: [(200, {"content-type": "text/html"}, PAGE.encode())]}), [])
        changed = PAGE.replace(QUOTE, "We share driving data with our partners")
        items = self.poll({URL: [(200, {"content-type": "text/html"}, changed.encode())]}, days=7)
        self.assertEqual(self.kinds(items), ["quote_missing"])
        item = items[0]
        self.assertIn(QUOTE, item["diff_window"]["before"])
        self.assertIn("We share driving data with our partners", item["diff_window"]["after"])
        self.assertLessEqual(len(item["diff_window"]["after"]), 2 * watch_quotes.WINDOW)
        self.assertEqual(item["record_refs"], ["app-example.json:com.example.app:/data_flows/0/sources/0"])
        self.assertEqual(item["prior_snapshot_sha256"], watch_queue.sha256(PAGE.encode()))

    def test_an_unchanged_page_raises_nothing(self):
        self.poll({URL: [(200, {"content-type": "text/html", "etag": '"1"'}, PAGE.encode())]})
        self.assertEqual(self.poll({URL: [(200, {"content-type": "text/html"}, PAGE.encode())]}, days=7), [])
        self.assertEqual(self.poll({URL: [(304, {}, b"")]}, days=14), [])
        self.assertEqual(len(list(self.store.folder(URL).glob("*.html"))), 1)

    def test_a_changed_neighbouring_sentence_is_context_changed(self):
        self.poll({URL: [(200, {"content-type": "text/html"}, PAGE.encode())]})
        changed = PAGE.replace(" for risk scoring.", " for risk scoring and pricing.")
        items = self.poll({URL: [(200, {"content-type": "text/html"}, changed.encode())]}, days=7)
        self.assertEqual(self.kinds(items), ["context_changed"])
        self.assertIn("risk scoring and pricing", items[0]["diff_window"]["after"])

    def test_related_links_changing_beside_an_unchanged_paragraph_raise_nothing(self):
        before, after = (FIXTURES / "article.html").read_bytes(), (FIXTURES / "article-sidebar-changed.html").read_bytes()
        self.poll({URL: [(200, {"content-type": "text/html"}, before)]})
        self.assertEqual(self.poll({URL: [(200, {"content-type": "text/html"}, after)]}, days=7), [])
        # The ±300-character windows differ, so comparing them alone would have raised an item.
        old, new = (check_quotes.fold(fetch_sources.text_of_html(b.decode())) for b in (before, after))
        self.assertNotEqual(watch_text.window(old, *check_quotes.locate(old, QUOTE)), watch_text.window(new, *check_quotes.locate(new, QUOTE)))

    def test_a_sentence_added_beside_the_quote_is_context_changed(self):
        self.poll({URL: [(200, {"content-type": "text/html"}, (FIXTURES / "article.html").read_bytes())]})
        items = self.poll({URL: [(200, {"content-type": "text/html"}, (FIXTURES / "article-sentence-added.html").read_bytes())]}, days=7)
        self.assertEqual(self.kinds(items), ["context_changed"])
        self.assertIn("set the price of your policy", items[0]["diff_window"]["after"])

    def test_a_neighbour_counts_only_when_it_reads_as_a_full_sentence(self):
        text = "Related\nWhy data centres matter for family apps\nWe share driving data with insurers.\nYou can turn this off in the app's settings at any time."
        self.assertEqual(watch_text.context(text, QUOTE), "We share driving data with insurers. You can turn this off in the app's settings at any time.")

    def test_tiktoks_policy_redirecting_to_404_is_parked_and_checked_by_hand(self):
        case = json.loads((FIXTURES / "tiktok-us-policy.json").read_text(encoding="utf-8"))
        self.write_record(record(case["url"], "U.S. Privacy Policy Last updated: July 15, 2026"))
        items = self.poll({"https://www.tiktok.com/robots.txt": [(200, {}, case["robots_txt"].encode())],
                           case["url"]: [(case["answer"]["status"], {"Location": case["answer"]["location"]}, b"")],
                           "https://www.tiktok.com/404": [(200, {"content-type": "text/html"}, b"<div id=app></div>")]})
        self.assertEqual(self.kinds(items), ["parked"])  # a refusal, not url_moved
        self.assertEqual(items[0]["notes"], "Parked: redirects the watcher to a not-found page. Not checked again until this item is acked.")
        self.assertEqual(items[0]["final_url"], "https://www.tiktok.com/404")
        self.assertNotIn("https://www.tiktok.com/404", self.http.urls())
        md, _ = watch_digest.build(self.store, WHEN.date())
        self.assertIn(f"- {case['url']} (redirects the watcher to a not-found page)", md[md.index("## Checked by hand"):])

    def test_the_first_check_compares_with_the_copy_the_quote_was_verified_against(self):
        (self.saved / "policy.txt").write_text(check_quotes.fold(f"{FILLER} {QUOTE} for risk scoring. {FILLER}"), encoding="utf-8")
        (self.saved / "index.json").write_text(json.dumps({"policy": {"url": URL, "file": "policy.html", "sha256": "c" * 64}}), encoding="utf-8")
        items = self.poll({URL: [(200, {"content-type": "text/html"}, PAGE.replace(QUOTE, "We sell driving data").encode())]})
        self.assertEqual(self.kinds(items), ["quote_missing"])
        self.assertEqual(items[0]["prior_snapshot_sha256"], "c" * 64)
        self.assertIn("verified", items[0]["notes"])

    def test_404_is_a_fetch_failure_and_three_failed_runs_park_the_url(self):
        items = [self.poll({URL: [(404, {}, b"gone")]}, days=d) for d in (0, 7, 14, 21)]
        self.assertEqual([self.kinds(i) for i in items], [["fetch_failure"], [], ["parked"], []])
        self.assertEqual(self.http.calls, [])  # parked: not even robots.txt

    def test_a_301_is_url_moved_and_the_quote_is_still_checked(self):
        items = self.poll({URL: [(301, {"location": "https://b.example/privacy"}, b"")],
                           "https://b.example/privacy": [(200, {"content-type": "text/html"}, PAGE.encode())]})
        self.assertEqual(self.kinds(items), ["url_moved"])
        self.assertEqual(items[0]["final_url"], "https://b.example/privacy")

    def test_a_robots_disallow_parks_without_requesting_the_page(self):
        items = self.poll({"https://a.example/robots.txt": [(200, {}, b"User-agent: FinePrint-watcher\nDisallow: /\n")]})
        self.assertEqual(self.kinds(items), ["parked"])
        self.assertNotIn(URL, self.http.urls())

    def test_a_denylisted_source_is_refused_without_a_request(self):
        clock = Clock()
        http = FakeHttp({"https://a.example/robots.txt": [ALLOW], URL: [(200, {"content-type": "text/html"}, PAGE.encode())]}, clock)
        run = watch_store.Run(self.store, watch_fetch.Fetcher("o@example.org", DENYLIST, http=http, sleep=clock.sleep, clock=clock), WHEN, DENYLIST)
        watch_quotes.poll(run, {}, self.reviewed, self.saved)
        refused = [i for i in run.written if i["kind"] == "refused"]
        self.assertEqual([i["source_url"] for i in refused], ["https://play.google.com/store/apps/details?id=com.example.app"])
        self.assertTrue(all("play.google.com" not in u for u in http.urls()))

    def test_a_manual_source_is_never_requested_and_is_checked_by_hand(self):
        self.write_record(record(manual=True))
        self.assertEqual(self.poll({URL: [(200, {"content-type": "text/html"}, PAGE.encode())]}), [])
        self.assertEqual(self.http.urls(), [])  # not even robots.txt
        md, _ = watch_digest.build(self.store, WHEN.date())
        self.assertIn(f"- {URL} (marked manual: its copy is saved by hand)", md[md.index("## Checked by hand"):])
        # Unmarked, it is polled again and leaves the list.
        self.write_record(record())
        self.poll({URL: [(200, {"content-type": "text/html"}, PAGE.encode())]})
        self.assertIn(URL, self.http.urls())
        self.assertNotIn("manual", self.store.meta(URL))
        self.assertNotIn(f"- {URL} (", watch_digest.build(self.store, WHEN.date())[0])

    def test_findings_are_written_once_across_runs(self):
        changed = PAGE.replace(QUOTE, "We share data")
        self.poll({URL: [(200, {"content-type": "text/html"}, PAGE.encode())]})
        first = self.poll({URL: [(200, {"content-type": "text/html"}, changed.encode())]}, days=7)
        self.store.save_meta(dict(self.store.meta(URL), copy=None))  # forget the copy, so the next run compares again
        again = self.poll({URL: [(200, {"content-type": "text/html"}, changed.encode())]}, days=14)
        self.assertEqual((len(first), again), (1, []))

    def test_a_page_without_text_is_one_failure_not_a_missing_quote_each(self):
        items = self.poll({URL: [(200, {"content-type": "text/html"}, b"<div id=root></div><script>app()</script>")]})
        self.assertEqual(self.kinds(items), ["fetch_failure"])
        self.assertIn("JavaScript", items[0]["notes"])

    def test_a_copy_the_extractor_cant_read_parks_the_url_and_the_run_goes_on(self):
        docx = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        items = self.poll({URL: [(200, {"content-type": docx}, b"not a zip file")]})
        self.assertEqual(self.kinds(items), ["parked"])
        self.assertIn("BadZipFile", items[0]["notes"])
        self.assertTrue(self.store.meta(URL)["parked"])

    def test_a_pdf_without_text_is_parked_with_a_note(self):
        with mock.patch("fetch_sources.text_of_pdf", return_value=""):
            items = self.poll({URL: [(200, {"content-type": "application/pdf"}, b"%PDF-1.7 scanned pages")]})
        self.assertEqual(self.kinds(items), ["parked"])
        self.assertIn("0 characters from a PDF", items[0]["notes"])

    def test_a_source_can_have_its_own_size_cap(self):
        big = PAGE.replace("</p>", "</p>" + "<p>" + "x" * 1000 + "</p>", 1).encode() + b" " * (watch_fetch.BODY_CAP + 1)
        self.assertEqual(self.kinds(self.poll({URL: [(200, {"content-type": "text/html"}, big)]})), ["fetch_failure"])
        self.store.save_meta(dict(self.store.meta(URL), last_checked=None))
        items = self.poll({URL: [(200, {"content-type": "text/html"}, big)]}, sources={URL: {"max_bytes": 20 * 1024 * 1024}})
        self.assertEqual(items, [])
        self.assertEqual(self.store.meta(URL)["failures"], 0)

    def test_an_archived_verify_url_is_not_watched_but_a_publishers_copy_is(self):
        self.assertEqual(watch_quotes.watched({"url": URL, "verify_url": "https://web.archive.org/web/2026/" + URL}), URL)
        self.assertEqual(watch_quotes.watched({"url": URL, "verify_url": "https://a.example/api/policy.json"}), "https://a.example/api/policy.json")


class SameQuotesAsTheBuildTest(unittest.TestCase):
    def test_the_watcher_finds_every_quote_check_quotes_checks(self):
        watched = collections.Counter((s["url"], s["quote"]) for s, _ in watch_quotes.sources_with_refs(check_quotes.REVIEWED))
        checked = collections.Counter((s["url"], s["quote"]) for p in sorted(check_quotes.REVIEWED.glob("*.json"))
                                      for s in check_quotes.sources_in(json.loads(p.read_text(encoding="utf-8")), []))
        self.assertEqual(watched, checked)
        self.assertGreater(sum(checked.values()), 0)

    def test_the_watcher_matches_quotes_exactly_as_check_quotes_does(self):
        cases = [("the “Services” – apps", '"Services" - apps'), ("geolocation , incognito", "geolocation, incognito"),
                 ("one two three", "one … three"), ("one two three", "three … one"), ("abc", ""), ("We’ll use", "We'll use")]
        for page, quote in cases:
            self.assertEqual(check_quotes.locate(check_quotes.fold(page), quote) is not None, check_quotes.found_in_order(page, quote), (page, quote))


if __name__ == "__main__":
    unittest.main()
