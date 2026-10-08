#!/usr/bin/env python3
"""Tests for the watcher's client, watch_fetch.py, against a fake requests:  python3 pipeline/test_watch_fetch.py"""
from __future__ import annotations

import sys
import unittest
from pathlib import Path

import requests

sys.path.insert(0, str(Path(__file__).resolve().parent))
import watch_fetch  # noqa: E402

ALLOW = (200, {}, b"User-agent: *\nDisallow: /private/\n")


class FakeResponse:
    def __init__(self, status: int, headers: dict, body: bytes):
        self.status_code, self.headers, self.body = status, headers, body

    def iter_content(self, size: int):
        for i in range(0, len(self.body), size):
            yield self.body[i:i + size]

    def close(self):
        pass


class FakeHttp:
    """Answers each URL from a list (the last answer repeats); an Exception in the list is raised."""
    def __init__(self, routes: dict, clock=None):
        self.routes, self.calls, self.clock = routes, [], clock

    def get(self, url, headers=None, **kw):
        self.calls.append((url, dict(headers or {}), kw, self.clock.t if self.clock else None))
        answers = self.routes.get(url) or [(404, {}, b"not found")]
        answer = answers.pop(0) if len(answers) > 1 else answers[0]
        if isinstance(answer, Exception):
            raise answer
        return FakeResponse(*answer)

    def urls(self) -> list[str]:
        return [c[0] for c in self.calls]


class Clock:
    def __init__(self):
        self.t, self.sleeps = 1000.0, []

    def __call__(self) -> float:
        return self.t

    def sleep(self, seconds: float):
        self.sleeps.append(seconds)
        self.t += seconds


def fetcher(routes: dict, denylist=("canlii.org", "play.google.com")):
    clock = Clock()
    http = FakeHttp(routes, clock)
    return watch_fetch.Fetcher("owner@example.org", list(denylist), http=http, sleep=clock.sleep, clock=clock), http, clock


class FetchTest(unittest.TestCase):
    def test_a_page_is_fetched_with_the_identified_agent_after_robots_txt(self):
        f, http, _ = fetcher({"https://a.example/robots.txt": [ALLOW], "https://a.example/p": [(200, {"ETag": '"v1"'}, b"<p>Hi</p>")]})
        got = f.fetch("https://a.example/p")
        self.assertEqual((got.outcome, got.status, got.body, got.headers["etag"]), ("ok", 200, b"<p>Hi</p>", '"v1"'))
        self.assertEqual(http.urls(), ["https://a.example/robots.txt", "https://a.example/p"])
        agent = http.calls[1][1]["User-Agent"]
        self.assertEqual(agent, "FinePrint-watcher/0.1 (+https://github.com/LonglifeIO/FinePrint; owner@example.org)")
        self.assertEqual((http.calls[1][2]["timeout"], http.calls[1][2]["allow_redirects"]), (30, False))

    def test_no_contact_no_client(self):
        with self.assertRaises(ValueError):
            watch_fetch.Fetcher("  ", [])

    def test_a_robots_disallow_parks_the_url_without_requesting_it(self):
        f, http, _ = fetcher({"https://a.example/robots.txt": [ALLOW]})
        got = f.fetch("https://a.example/private/page")
        self.assertEqual(got.outcome, "parked")
        self.assertIn("robots.txt", got.reason)
        self.assertEqual(http.urls(), ["https://a.example/robots.txt"])

    def test_robots_txt_that_refuses_or_fails_stops_everything_and_a_missing_one_allows(self):
        f, http, _ = fetcher({"https://a.example/robots.txt": [(403, {}, b"")], "https://b.example/robots.txt": [(503, {}, b"")],
                              "https://c.example/p": [(200, {}, b"ok")]})
        refused = f.fetch("https://a.example/p")
        self.assertEqual(refused.outcome, "parked")
        self.assertEqual(refused.reason, "robots.txt at https://a.example answered HTTP 403, which counts as disallowing everything")
        self.assertEqual(f.fetch("https://b.example/p").outcome, "failed")
        self.assertEqual(f.fetch("https://c.example/p").outcome, "ok")  # robots.txt 404: no rules
        self.assertNotIn("https://a.example/p", http.urls())
        self.assertNotIn("https://b.example/p", http.urls())

    def test_robots_txt_is_read_once_per_host(self):
        f, http, _ = fetcher({"https://a.example/robots.txt": [ALLOW], "https://a.example/p": [(200, {}, b"1")], "https://a.example/q": [(200, {}, b"2")]})
        f.fetch("https://a.example/p")
        f.fetch("https://a.example/q")
        self.assertEqual(http.urls().count("https://a.example/robots.txt"), 1)

    def test_the_denylist_is_never_requested(self):
        f, http, _ = fetcher({})
        for url in ("https://www.canlii.org/en/ca/scc/doc/2026/1.html", "https://play.google.com/store/apps/details?id=x"):
            got = f.fetch(url)
            self.assertEqual(got.outcome, "refused")
        self.assertEqual(http.calls, [])
        self.assertEqual(watch_fetch.denied("https://play.google.com/store/apps/datasafety?id=x",
                                            ["play.google.com/store/apps/datasafety", "play.google.com"]), "play.google.com/store/apps/datasafety")
        self.assertIsNone(watch_fetch.denied("https://notcanlii.org/", ["canlii.org"]))

    def test_403_parks_and_404_fails_without_retrying(self):
        f, http, _ = fetcher({"https://a.example/robots.txt": [ALLOW], "https://a.example/gone": [(404, {}, b"")],
                              "https://a.example/no": [(403, {}, b"denied")]})
        self.assertEqual((f.fetch("https://a.example/no").outcome, f.fetch("https://a.example/gone").outcome), ("parked", "failed"))
        self.assertEqual(http.urls().count("https://a.example/gone"), 1)
        self.assertEqual(http.urls().count("https://a.example/no"), 1)

    def test_server_errors_back_off_three_tries_and_honour_retry_after(self):
        f, http, clock = fetcher({"https://a.example/robots.txt": [ALLOW],
                                  "https://a.example/p": [(503, {"Retry-After": "7"}, b""), requests.ConnectionError(), (500, {}, b"")]})
        got = f.fetch("https://a.example/p")
        self.assertEqual((got.outcome, got.status), ("failed", 500))
        self.assertEqual(http.urls().count("https://a.example/p"), 3)
        self.assertIn(7.0, clock.sleeps)       # Retry-After
        self.assertIn(20.0, clock.sleeps)      # backoff after the second try: 5 s × 2²

    def test_a_long_retry_after_is_left_for_the_next_run(self):
        f, http, clock = fetcher({"https://a.example/robots.txt": [ALLOW], "https://a.example/p": [(429, {"Retry-After": "3600"}, b"")]})
        got = f.fetch("https://a.example/p")
        self.assertEqual(got.outcome, "failed")
        self.assertIn("next run", got.reason)
        self.assertEqual(http.urls().count("https://a.example/p"), 1)
        self.assertNotIn(3600.0, clock.sleeps)

    def test_redirects_are_followed_hop_by_hop_with_robots_and_denylist_checks(self):
        f, http, _ = fetcher({"https://a.example/robots.txt": [ALLOW], "https://b.example/robots.txt": [ALLOW],
                              "https://a.example/old": [(301, {"Location": "https://b.example/new"}, b"")],
                              "https://b.example/new": [(200, {}, b"moved")],
                              "https://a.example/app": [(302, {"Location": "https://play.google.com/store/apps/details?id=x"}, b"")]})
        got = f.fetch("https://a.example/old")
        self.assertEqual((got.outcome, got.url, got.final_url, got.body), ("ok", "https://a.example/old", "https://b.example/new", b"moved"))
        self.assertLess(http.urls().index("https://b.example/robots.txt"), http.urls().index("https://b.example/new"))
        self.assertEqual(f.fetch("https://a.example/app").outcome, "refused")
        self.assertNotIn("https://play.google.com/store/apps/details?id=x", http.urls())

    def test_a_redirect_to_a_not_found_page_is_a_refusal_not_a_move(self):
        f, http, _ = fetcher({"https://a.example/robots.txt": [ALLOW], "https://a.example/policy": [(302, {"Location": "/404"}, b"")],
                              "https://a.example/404": [(200, {}, b"<p>Not found</p>")],
                              "https://a.example/doc": [(301, {"Location": "/documents/2020-18404"}, b"")],
                              "https://a.example/documents/2020-18404": [(200, {}, b"<p>A notice</p>")]})
        got = f.fetch("https://a.example/policy")
        self.assertEqual((got.outcome, got.reason, got.final_url), ("parked", "redirects the watcher to a not-found page", "https://a.example/404"))
        self.assertNotIn("https://a.example/404", http.urls())  # never followed
        self.assertEqual(f.fetch("https://a.example/doc").outcome, "ok")  # 404 inside a number is not a not-found page

    def test_the_last_copys_validators_make_it_a_conditional_get(self):
        f, http, _ = fetcher({"https://a.example/robots.txt": [ALLOW], "https://a.example/p": [(304, {}, b"")]})
        got = f.fetch("https://a.example/p", {"etag": '"v1"', "last_modified": "Wed, 07 Oct 2026 10:00:00 GMT"})
        self.assertEqual(got.outcome, "unchanged")
        self.assertEqual(http.calls[1][1]["If-None-Match"], '"v1"')
        self.assertEqual(http.calls[1][1]["If-Modified-Since"], "Wed, 07 Oct 2026 10:00:00 GMT")

    def test_requests_to_one_host_are_at_least_five_seconds_apart(self):
        f, http, _ = fetcher({"https://a.example/robots.txt": [ALLOW], "https://a.example/p": [(200, {}, b"1")],
                              "https://a.example/q": [(200, {}, b"2")], "https://b.example/r": [(200, {}, b"3")]})
        for url in ("https://a.example/p", "https://a.example/q", "https://b.example/r"):
            f.fetch(url)
        at = {c[0]: c[3] for c in http.calls}
        self.assertGreaterEqual(at["https://a.example/p"] - at["https://a.example/robots.txt"], 5)
        self.assertGreaterEqual(at["https://a.example/q"] - at["https://a.example/p"], 5)
        self.assertEqual(at["https://b.example/robots.txt"], at["https://a.example/q"])  # another host need not wait

    def test_a_body_over_the_cap_is_dropped(self):
        big = b"x" * (watch_fetch.BODY_CAP + 1)
        f, _, _ = fetcher({"https://a.example/robots.txt": [ALLOW], "https://a.example/big": [(200, {}, big)]})
        got = f.fetch("https://a.example/big")
        self.assertEqual((got.outcome, got.body), ("failed", b""))
        self.assertIn("5 MB", got.reason)
        f, _, _ = fetcher({"https://a.example/robots.txt": [ALLOW], "https://a.example/big": [(200, {}, big)]})
        got = f.fetch("https://a.example/big", max_bytes=20 * 1024 * 1024)  # a source's own max_bytes
        self.assertEqual((got.outcome, len(got.body)), ("ok", len(big)))

    def test_retry_after_reads_seconds_or_a_date(self):
        self.assertEqual(watch_fetch.retry_after("12"), 12.0)
        self.assertEqual(watch_fetch.retry_after("Wed, 21 Oct 2015 07:28:00 GMT"), 0.0)  # in the past
        self.assertIsNone(watch_fetch.retry_after("soon"))


if __name__ == "__main__":
    unittest.main()
