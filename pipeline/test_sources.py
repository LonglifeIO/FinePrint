#!/usr/bin/env python3
"""Tests for fetch_sources.py and check_quotes.py:  python3 pipeline/test_sources.py"""
from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import check_quotes  # noqa: E402
import fetch_sources  # noqa: E402

SOURCE = {"url": "https://example.org/policy", "type": "privacy_policy", "as_of": "2026-01-01",
          "status": "self_disclosed", "quote": "We’ll use information … to personalize ads"}


class QuoteTest(unittest.TestCase):
    def test_fragments_must_appear_in_order(self):
        page = "We'll use information that partners provide us\nto personalize ads that we show you."
        self.assertTrue(check_quotes.found_in_order(page, SOURCE["quote"]))
        self.assertFalse(check_quotes.found_in_order(page, "to personalize ads … We’ll use information"))
        self.assertFalse(check_quotes.found_in_order(page, "We’ll use information …  … ads"))

    def test_curly_quotes_dashes_and_whitespace_fold(self):
        self.assertTrue(check_quotes.found_in_order("the “Services” – apps, sites", '"Services" - apps, sites'))
        # A link's closing tag leaves a space before the comma in the saved text.
        self.assertTrue(check_quotes.found_in_order("data regarding geolocation , incognito ( browsing )", "geolocation, incognito (browsing)"))

    def test_word_cap_spares_us_federal_works(self):
        long_quote = " ".join(["word"] * 15)
        self.assertEqual(check_quotes.words(long_quote + " … more"), 16)
        self.assertEqual(check_quotes.words("€1.2 billion … EU/EEA"), 3)
        with tempfile.TemporaryDirectory() as d:
            saved = self.saved(Path(d), "https://www.ftc.gov/a", long_quote)
            self.assertEqual(check_quotes.problems(dict(SOURCE, url="https://www.ftc.gov/a", quote=long_quote), saved), [])
            saved = self.saved(Path(d), "https://news.example/a", long_quote)
            self.assertTrue(check_quotes.problems(dict(SOURCE, url="https://news.example/a", quote=long_quote), saved))

    def test_verify_url_copy_stands_in_for_the_page(self):
        with tempfile.TemporaryDirectory() as d:
            archived = "https://web.archive.org/web/2026/https://example.org/policy"
            saved = self.saved(Path(d), archived, "We'll use information to personalize ads")
            self.assertEqual(check_quotes.problems(dict(SOURCE, verify_url=archived), saved), [])
            self.assertIn("no saved copy (run fetch_sources.py)", check_quotes.problems(SOURCE, saved))

    def saved(self, folder: Path, url: str, text: str) -> dict:
        name = f"copy{len(list(folder.glob('*.txt')))}"
        (folder / f"{name}.txt").write_text(text, encoding="utf-8")
        index_path = folder / "index.json"
        index = json.loads(index_path.read_text(encoding="utf-8")) if index_path.exists() else {}
        index[name] = {"url": url, "file": f"{name}.html"}
        index_path.write_text(json.dumps(index), encoding="utf-8")
        return check_quotes.copies(folder)


class FetchTest(unittest.TestCase):
    def test_visible_text_drops_scripts_and_keeps_blocks(self):
        page = "<p>One&nbsp;line</p><script>var x = 'hidden';</script><div>Two</div>"
        self.assertEqual(fetch_sources.text_of_html(page), "One line\nTwo")

    def test_utf8_without_a_charset_decodes(self):
        self.assertEqual(fetch_sources.decode("driver’s".encode("utf-8")), "driver’s")
        self.assertEqual(fetch_sources.decode(b"caf\xe9"), "café")

    def test_list_lines(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "list.txt"
            path.write_text("# comment\nmeta-policy https://example.org/p#section\n", encoding="utf-8")
            self.assertEqual(fetch_sources.read_list(path), [("meta-policy", "https://example.org/p#section")])
            path.write_text("Bad Name https://example.org/\n", encoding="utf-8")
            with self.assertRaises(ValueError):
                fetch_sources.read_list(path)


if __name__ == "__main__":
    unittest.main()
