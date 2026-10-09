#!/usr/bin/env python3
"""Tests for fetch_sources.py and check_quotes.py:  python3 pipeline/test_sources.py"""
from __future__ import annotations

import contextlib
import io
import json
import shutil
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock

import requests

sys.path.insert(0, str(Path(__file__).resolve().parent))
import check_quotes  # noqa: E402
import fetch_sources  # noqa: E402

FIXTURES = Path(__file__).resolve().parent / "watch" / "fixtures"
SOURCE = {"url": "https://example.org/policy", "type": "privacy_policy", "as_of": "2026-01-01",
          "status": "self_disclosed", "quote": "We’ll use information … to personalize ads"}
MANUAL = "https://sdk.example/privacy"  # a policy whose text only appears once JavaScript runs


class QuoteTest(unittest.TestCase):
    def test_a_store_tagline_is_checked_as_a_quote(self):
        record = {"store_tagline": {"text": "A little connection can go a long way", "as_of": "2026-10-07",
                                    "source_url": "https://play.google.com/store/apps/details?id=com.facebook.katana"}}
        found = check_quotes.sources_in(record, [])
        self.assertEqual([(s["url"], s["quote"]) for s in found],
                         [("https://play.google.com/store/apps/details?id=com.facebook.katana", "A little connection can go a long way")])

    def test_fragments_must_appear_in_order(self):
        page = "We'll use information that partners provide us\nto personalize ads that we show you."
        self.assertTrue(check_quotes.found_in_order(page, SOURCE["quote"]))
        self.assertFalse(check_quotes.found_in_order(page, "to personalize ads … We’ll use information"))
        self.assertFalse(check_quotes.found_in_order(page, "We’ll use information …  … ads"))

    def test_locate_spans_the_quote_in_the_folded_page(self):
        page = check_quotes.fold("Intro.  We’ll use information that partners provide us to personalize ads. End.")
        start, end = check_quotes.locate(page, SOURCE["quote"])
        self.assertEqual(page[start:end], "We'll use information that partners provide us to personalize ads")
        self.assertIsNone(check_quotes.locate(page, "to personalize ads … We’ll use information"))
        self.assertIsNone(check_quotes.locate(page, ""))

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

    def test_a_comment_naming_noscript_keeps_the_page(self):
        # placer.ai: "<noscript>" in a head comment used to open a block that ran to the body's last </noscript>.
        page = (FIXTURES / "placer-noscript-comment.html").read_text(encoding="utf-8")
        self.assertEqual(fetch_sources.text_of_html(page), "SDK Privacy Policy\n"
                         "This policy explains what the SDK collects from the apps it is built into.\n"
                         "The SDK collects precise location data from mobile devices.")

    def test_comments_end_where_a_browser_ends_them(self):
        # At --!> and at <!-->, and a script's "<!--" opens no comment.
        page = "<p>A</p><!-- note --!><p>B</p><!--><p>C</p><script>var s = '<!--';</script><p>D</p><!-- x -->"
        self.assertEqual(fetch_sources.text_of_html(page), "A\nB\nC\nD")

    def test_utf8_without_a_charset_decodes(self):
        self.assertEqual(fetch_sources.decode("driver’s".encode("utf-8")), "driver’s")
        self.assertEqual(fetch_sources.decode(b"caf\xe9"), "café")

    def test_a_declared_charset_decodes_what_isnt_utf8(self):
        russian = "Операторы связи обязаны".encode("cp1251")
        self.assertEqual(fetch_sources.decode(russian, "text/html; charset=windows-1251"), "Операторы связи обязаны")
        self.assertEqual(fetch_sources.decode(b'<meta charset="windows-1251">' + russian), '<meta charset="windows-1251">Операторы связи обязаны')
        self.assertEqual(fetch_sources.decode("driver’s".encode("utf-8"), "text/html; charset=iso-8859-1"), "driver’s")

    def test_a_word_file_is_saved_as_docx_and_read_by_paragraph(self):
        # China's national law database serves laws as .docx; its content type contains "xml".
        body = ('<w:document><w:body><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:t>第七条　任何组织</w:t></w:r>'
                '<w:r><w:t>和公民</w:t></w:r></w:p><w:p w:rsidR="1"><w:r><w:t>A &amp; B</w:t></w:r></w:p></w:body></w:document>')
        buf = io.BytesIO()
        with zipfile.ZipFile(buf, "w") as z:
            z.writestr("word/document.xml", body)
        resp = requests.Response()
        resp._content, resp.url = buf.getvalue(), "https://example.org/law.docx"
        resp.headers["content-type"] = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        with tempfile.TemporaryDirectory() as d:
            out, fetch_sources.OUT = fetch_sources.OUT, Path(d)
            try:
                entry = fetch_sources.save("law", resp.url, resp)
                self.assertEqual(entry["file"], "law.docx")
                self.assertEqual((Path(d) / "law.txt").read_text(encoding="utf-8"), "第七条　任何组织和公民\nA & B")
            finally:
                fetch_sources.OUT = out

    def test_a_page_is_saved_by_its_type(self):
        self.assertEqual(fetch_sources.suffix_for("application/octet-stream", b"%PDF-1.7"), ".pdf")
        self.assertEqual(fetch_sources.suffix_for("application/vnd.openxmlformats-officedocument.wordprocessingml.document", b"PK"), ".docx")
        self.assertEqual(fetch_sources.suffix_for("application/json; charset=utf-8", b"{}"), ".json")
        self.assertEqual(fetch_sources.suffix_for("application/rss+xml", b"<rss/>"), ".html")
        self.assertEqual(fetch_sources.suffix_for("text/plain", b"law"), ".raw")

    def test_list_lines(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "list.txt"
            path.write_text("# comment\nmeta-policy https://example.org/p#section\n", encoding="utf-8")
            self.assertEqual(fetch_sources.read_list(path), [("meta-policy", "https://example.org/p#section")])
            path.write_text("Bad Name https://example.org/\n", encoding="utf-8")
            with self.assertRaises(ValueError):
                fetch_sources.read_list(path)


class ManualTest(unittest.TestCase):
    """A source marked "manual": true: only a person can capture its page, so its copy is saved by hand."""

    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root)
        self.out, self.reviewed = self.root / "sources", self.root / "reviewed"
        self.reviewed.mkdir()
        self.source = dict(SOURCE, url=MANUAL, quote="share it with advertisers", manual=True)
        record = {"trackers": [{"id": "exodus-1", "consequences": [{"sources": [self.source]}]}]}
        (self.reviewed / "tracker-example.json").write_text(json.dumps(record), encoding="utf-8")
        for patch in (mock.patch.object(fetch_sources, "OUT", self.out), mock.patch.object(fetch_sources, "INDEX", self.out / "index.json"),
                      mock.patch.object(check_quotes, "REVIEWED", self.reviewed)):
            patch.start()
            self.addCleanup(patch.stop)
        self.page = self.root / "Privacy Policy.html"  # as a browser saves the page it shows
        self.page.write_text("<div id=app><p>We may share it with advertisers.</p></div><script>render()</script>", encoding="utf-8")

    def main(self, *argv) -> tuple[int, str]:
        out = io.StringIO()
        with mock.patch.object(sys, "argv", ["fetch_sources.py", *argv]), contextlib.redirect_stdout(out), \
                contextlib.redirect_stderr(io.StringIO()):
            return fetch_sources.main(), out.getvalue()

    def test_a_copy_saved_by_hand_is_registered_and_its_quote_checked(self):
        self.assertEqual(check_quotes.manual_urls(self.reviewed), {MANUAL})
        self.assertEqual(check_quotes.problems(self.source, {}), ["no saved copy: the page is saved by hand (fetch_sources.py --hand-saved)"])
        code, said = self.main("--hand-saved", "sdk-policy", MANUAL, str(self.page))
        self.assertEqual((code, said), (0, f"  saved by hand  sdk-policy  {MANUAL}\n"))
        entry = json.loads((self.out / "index.json").read_text(encoding="utf-8"))["sdk-policy"]
        self.assertEqual({k: entry[k] for k in ("url", "final_url", "content_type", "file", "manual")},
                         {"url": MANUAL, "final_url": MANUAL, "content_type": "text/html", "file": "sdk-policy.html", "manual": True})
        self.assertEqual((self.out / "sdk-policy.txt").read_text(encoding="utf-8"), "We may share it with advertisers.")
        self.assertEqual(check_quotes.problems(self.source, check_quotes.copies(self.out)), [])
        # A name already holding another page is refused, as a LIST line is.
        self.assertEqual(self.main("--hand-saved", "sdk-policy", "https://sdk.example/terms", str(self.page))[0], 1)

    def test_text_copied_by_hand_is_kept_as_it_is(self):
        text = self.root / "policy.txt"
        text.write_text("We may share it with advertisers.\n", encoding="utf-8")
        self.out.mkdir()
        entry = fetch_sources.hand_saved("sdk-text", MANUAL, text)
        self.assertEqual((entry["file"], entry["content_type"]), ("sdk-text.raw", "text/plain"))
        self.assertEqual((self.out / "sdk-text.txt").read_text(encoding="utf-8"), "We may share it with advertisers.\n")
        with self.assertRaises(ValueError):
            fetch_sources.hand_saved("sdk-shot", MANUAL, self.root / "policy.png")

    def test_a_manual_page_is_never_fetched_even_with_force(self):
        self.main("--hand-saved", "sdk-policy", MANUAL, str(self.page))
        listed = self.root / "list.txt"
        listed.write_text(f"sdk-policy {MANUAL}\nother https://example.org/other\n", encoding="utf-8")
        resp = requests.Response()
        resp.status_code, resp._content, resp.url = 200, b"<p>Other</p>", "https://example.org/other"
        resp.headers["content-type"] = "text/html"
        with mock.patch.object(fetch_sources.requests, "get", return_value=resp) as get:
            code, said = self.main(str(listed), "--force", "--delay", "0")
        self.assertEqual([c.args[0] for c in get.call_args_list], ["https://example.org/other"])
        self.assertIn("  skipped (manual: saved by hand, see --hand-saved)  sdk-policy", said)
        self.assertEqual(code, 0)
        self.assertEqual((self.out / "sdk-policy.txt").read_text(encoding="utf-8"), "We may share it with advertisers.")


if __name__ == "__main__":
    unittest.main()
