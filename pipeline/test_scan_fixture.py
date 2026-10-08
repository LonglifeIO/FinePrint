#!/usr/bin/env python3
"""Every app record is in the scan fixture, so the preview build shows it:  python3 pipeline/test_scan_fixture.py

android/app/src/debug/assets/scan-fixture.json holds the test emulator's apps as FinePrint scanned them; the app's
tests, the README's screenshots and the preview build read it. An app with a record that isn't installed on the
emulator is there too, with no trackers and "note": "not scanned".
"""
from __future__ import annotations

import json
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
REVIEWED = REPO / "pipeline" / "reviewed"
FIXTURE = REPO / "android" / "app" / "src" / "debug" / "assets" / "scan-fixture.json"


def record_packages(reviewed: Path = REVIEWED) -> set[str]:
    """The package of every reviewed app record."""
    packages = set()
    for path in reviewed.glob("*.json"):
        doc = json.loads(path.read_text(encoding="utf-8"))
        if isinstance(doc, dict):
            packages |= {app["package_id"] for app in doc.get("apps", [])}
    return packages


def fixture_apps(path: Path = FIXTURE) -> dict[str, dict]:
    return {app["package"]: app for app in json.loads(path.read_text(encoding="utf-8"))["apps"]}


class ScanFixtureTest(unittest.TestCase):
    def test_every_app_record_is_in_the_fixture(self):
        missing = sorted(record_packages() - fixture_apps().keys())
        self.assertEqual(missing, [], 'add each to scan-fixture.json: scanned on the test emulator, or "note": "not scanned" with no trackers')

    def test_an_app_that_was_not_scanned_says_so_and_detects_nothing(self):
        for package, app in fixture_apps().items():
            if "note" in app:
                with self.subTest(package):
                    self.assertEqual(app["note"], "not scanned")
                    self.assertEqual((app["trackers"], app["referenced_only"], app["dex_files"], app["classes"]), ([], [], 0, 0))


if __name__ == "__main__":
    unittest.main()
