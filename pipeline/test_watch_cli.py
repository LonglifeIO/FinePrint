#!/usr/bin/env python3
"""Tests for the watcher's command line, watch.py:  python3 pipeline/test_watch_cli.py"""
from __future__ import annotations

import contextlib
import io
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import watch  # noqa: E402
import watch_store  # noqa: E402
from test_watch_fetch import FakeHttp  # noqa: E402

REPO = Path(__file__).resolve().parent.parent
IGNORED = ["pipeline/watch/snapshots/", "pipeline/watch/queue/", "pipeline/watch/acked/", "pipeline/watch/digest/", "pipeline/watch/local.json"]


class CliTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root)
        shutil.copy(REPO / "pipeline" / "watch" / "sources.json", self.root / "sources.json")

    def run_main(self, *argv: str) -> tuple[int, str, str]:
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = watch.main(list(argv), root=self.root)
        return code, out.getvalue(), err.getvalue()

    def test_without_a_contact_the_watcher_refuses_to_run(self):
        http = FakeHttp({})
        config = json.loads((self.root / "sources.json").read_text(encoding="utf-8"))
        with contextlib.redirect_stderr(io.StringIO()) as err:
            code = watch.poll(watch_store.Store(self.root), config, {"contact": " "}, http=http, sleep=lambda s: None)
        self.assertEqual(code, 2)
        self.assertIn("refusing to run", err.getvalue())
        self.assertEqual(http.calls, [])
        code, _, err = self.run_main("poll")  # no local.json at all
        self.assertEqual(code, 2)
        self.assertFalse((self.root / "snapshots").exists())

    def test_status_reads_an_empty_store(self):
        code, out, _ = self.run_main("status")
        self.assertEqual(code, 0)
        self.assertIn("0 sources tracked; last check never", out)

    def test_the_denylist_holds_play_canlii_and_exodus(self):
        denylist = json.loads((REPO / "pipeline" / "watch" / "sources.json").read_text(encoding="utf-8"))["denylist"]
        for rule in ("play.google.com/store/apps/datasafety", "play.google.com", "canlii.org", "exodus-privacy.eu.org"):
            self.assertIn(rule, denylist)


class IgnoredTest(unittest.TestCase):
    def test_the_watchers_copies_are_ignored_and_never_tracked(self):
        try:
            tracked = subprocess.run(["git", "ls-files", "--", *IGNORED], cwd=REPO, capture_output=True, text=True, check=True).stdout
        except (OSError, subprocess.CalledProcessError):
            self.skipTest("not a git checkout")
        self.assertEqual(tracked, "")
        probes = [p + "probe" if p.endswith("/") else p for p in IGNORED]
        ignored = subprocess.run(["git", "check-ignore", "--no-index", *probes], cwd=REPO, capture_output=True, text=True).stdout.split()
        self.assertEqual(sorted(ignored), sorted(probes))


if __name__ == "__main__":
    unittest.main()
