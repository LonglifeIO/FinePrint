#!/usr/bin/env python3
"""Tests for build.py's source URL check, against a fake requests:  python3 pipeline/test_build_urls.py"""
from __future__ import annotations

import datetime as dt
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build  # noqa: E402

NOW = dt.datetime(2026, 10, 8, 14, 0, tzinfo=build.HALIFAX)


class Answer:
    def __init__(self, status: int, final: str, redirected: bool):
        self.status_code, self.url, self.history = status, final, [object()] if redirected else []

    def close(self):
        pass


class UrlCheckTest(unittest.TestCase):
    def check(self, answers: dict) -> list[str]:
        sources = [{"url": url, "quote": "q"} for url in answers]
        with tempfile.TemporaryDirectory() as d, mock.patch.object(build, "URL_CACHE", Path(d) / "url-checks.json"), \
                mock.patch.object(build.requests, "get", side_effect=lambda url, **kw: Answer(*answers[url])), \
                mock.patch("builtins.print"):
            return build.check_urls({"apps": [{"sources": sources}]}, NOW)

    def test_a_redirect_to_a_not_found_page_fails_even_when_it_answers_200(self):
        errors = self.check({"https://a.example/legal/policy": (200, "https://a.example/404", True)})
        self.assertEqual(errors, ["source URL redirects to a not-found page (https://a.example/404): https://a.example/legal/policy"])

    def test_a_final_404_fails(self):
        self.assertEqual(self.check({"https://a.example/gone": (404, "https://a.example/gone", False)}),
                         ["source URL did not answer 200 (404): https://a.example/gone"])

    def test_other_pages_and_moves_still_pass(self):
        self.assertEqual(self.check({
            "https://a.example/old": (200, "https://a.example/new-place/", True),          # an ordinary move
            "https://a.example/documents/2020-18404": (200, "https://a.example/documents/2020-18404", False),
            "https://a.example/d/18404": (200, "https://a.example/full/2020-18404.txt", True),  # 404 inside a number
        }), [])

    def test_a_refusal_is_reported_as_before(self):
        # Hosts that block scripts are cited through verify_url; the check itself is unchanged for them.
        self.assertEqual(self.check({"https://a.example/blocked": (403, "https://a.example/blocked", False)}),
                         ["source URL did not answer 200 (403): https://a.example/blocked"])


if __name__ == "__main__":
    unittest.main()
