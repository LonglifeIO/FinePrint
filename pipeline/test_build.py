#!/usr/bin/env python3
"""Tests for build.py:  python3 pipeline/test_build.py"""
from __future__ import annotations

import datetime as dt
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build  # noqa: E402

SOURCE = {"url": "https://example.org/a", "type": "journalism", "as_of": "2026-01-01", "status": "reported", "quote": "q"}
VALIDATOR = build.jsonschema.Draft202012Validator(
    json.loads(build.SCHEMA.read_text(encoding="utf-8")), format_checker=build.jsonschema.FormatChecker())


def doc(**sections) -> dict:
    base = {"schema_version": 1, "schema_revision": "1.1", "bundle_version": "2026.10.04",
            "generated_at": "2026-10-04T12:00:00-03:00", "apps": [], "trackers": [], "companies": [],
            "permissions": [], "device_reach": []}
    return dict(base, **sections)


def schema_errors(bundle: dict) -> list[str]:
    return [e.message for e in VALIDATOR.iter_errors(bundle)]


def app(last_reviewed: str) -> dict:
    return {"package_id": "com.example", "display_name": "Example", "summary": "s", "trackers": ["exodus-12"],
            "consequences": [], "coverage": "curated", "last_reviewed": last_reviewed}


class StaleTest(unittest.TestCase):
    def test_stale_after_180_days(self):
        apps = [app("2026-01-01"), app("2026-04-07"), app("2026-04-08")]
        build.mark_stale(apps, dt.date(2026, 10, 5))
        self.assertEqual([a["stale"] for a in apps], [True, True, False])  # 277, 181, 180 days


class MergeAndCheckTest(unittest.TestCase):
    def write(self, folder: Path, name: str, doc: dict) -> Path:
        path = folder / name
        path.write_text(json.dumps(doc), encoding="utf-8")
        return path

    def test_merge_rejects_duplicate_ids(self):
        with tempfile.TemporaryDirectory() as d:
            a = self.write(Path(d), "a.json", {"apps": [app("2026-10-01")]})
            b = self.write(Path(d), "b.json", {"apps": [app("2026-10-01")]})
            with self.assertRaises(ValueError):
                build.merge([a, b])

    def test_cross_check_finds_unknown_companies_sources_and_trackers(self):
        bundle = {
            "apps": [dict(app("2026-10-01"), trackers=["exodus-12", "exodus-99999"])],
            "trackers": [],
            "companies": [{"id": "co-known", "name": "K", "roles": ["developer"], "last_reviewed": "2026-10-01"}],
            "permissions": [],
            "device_reach": [],
            "x": [{"recipient": "co-unknown", "sources": [dict(SOURCE, derives_from="src-missing")]}],
        }
        errors = build.cross_check(bundle, {"exodus-12"})
        self.assertTrue(any("co-unknown" in e for e in errors))
        self.assertTrue(any("src-missing" in e for e in errors))
        self.assertTrue(any("exodus-99999" in e for e in errors))

    def test_source_id_names_one_source(self):
        a = dict(SOURCE, id="src-a")
        bundle = doc(apps=[dict(app("2026-10-01"), consequences=[
            {"text": "t", "status": "reported", "sources": [a]},
            {"text": "u", "status": "reported", "sources": [dict(a, quote="a different quote")]},
        ])])
        self.assertIn("source id 'src-a' is defined differently in two places", build.cross_check(bundle, {"exodus-12"}))

    def test_android_test_fixture_follows_the_schema(self):  # keeps both sides of the contract in step
        fixture = build.REPO / "android" / "app" / "src" / "test" / "resources" / "bundle-fixture.json"
        self.assertEqual(schema_errors(json.loads(fixture.read_text(encoding="utf-8"))), [])

    def test_reviewed_records_validate(self):
        bundle = build.build(sorted(build.REVIEWED.glob("*.json")), dt.datetime(2026, 10, 4, tzinfo=build.HALIFAX))
        schema = json.loads(build.SCHEMA.read_text(encoding="utf-8"))
        validator = build.jsonschema.Draft202012Validator(schema, format_checker=build.jsonschema.FormatChecker())
        self.assertEqual([e.message for e in validator.iter_errors(bundle)], [])
        tracker_ids = {t["id"] for t in json.loads(build.TRACKERS.read_text(encoding="utf-8"))["trackers"]}
        self.assertEqual(build.cross_check(bundle, tracker_ids), [])


class SourceRulesTest(unittest.TestCase):
    def with_source(self, source: dict) -> dict:
        return doc(apps=[dict(app("2026-10-01"), consequences=[{"text": "t", "status": "reported", "sources": [source]}])])

    def test_every_source_needs_a_quote(self):
        bundle = self.with_source({k: v for k, v in SOURCE.items() if k != "quote"})
        self.assertIn("'quote' is a required property", schema_errors(bundle))
        self.assertIn("source without a quote: https://example.org/a", build.cross_check(bundle, {"exodus-12"}))
        self.assertTrue(schema_errors(self.with_source(dict(SOURCE, quote="  "))))

    def test_an_undated_page_carries_accessed_instead_of_as_of(self):
        undated = {k: v for k, v in SOURCE.items() if k != "as_of"}
        self.assertEqual(schema_errors(self.with_source(dict(undated, accessed="2026-10-02"))), [])
        self.assertTrue(schema_errors(self.with_source(undated)))

    def test_event_dates_may_be_year_and_month(self):
        def event(date: str) -> dict:
            return doc(companies=[{"id": "co-x", "name": "X", "roles": ["developer"], "last_reviewed": "2026-10-01",
                                   "regulatory_history": [{"date": date, "title": "t", "type": "breach",
                                                           "status": "reported", "sources": [SOURCE]}]}])
        self.assertEqual(schema_errors(event("2024-03")), [])
        self.assertEqual(schema_errors(event("2024-07-20")), [])
        self.assertTrue(schema_errors(event("2024-13")))
        self.assertTrue(schema_errors(event("March 2024")))


class RiskTagTest(unittest.TestCase):
    def record(self, subject: str, **extra) -> dict:
        action = {"text": "t", "status": "alleged", "status_kind": "filed", "subject_company": subject, "sources": [SOURCE]}
        return dict(app("2026-10-01"), developer_company="co-dev", risk_tags=["regulatory_action"],
                    consequences=[action], **extra)

    def test_regulatory_action_against_someone_else_needs_a_qualifier(self):
        self.assertTrue(build.risk_tag_problems(self.record("co-other")))
        noted = self.record("co-other", risk_tag_notes={"regulatory_action": "against Other concerning this app's data"})
        self.assertEqual(build.risk_tag_problems(noted), [])

    def test_regulatory_action_against_the_developer_needs_none(self):
        self.assertEqual(build.risk_tag_problems(self.record("co-dev")), [])

    def test_a_note_needs_its_tag(self):
        stray = dict(self.record("co-dev"), risk_tag_notes={"breach": "x"})
        self.assertTrue(build.risk_tag_problems(stray))


if __name__ == "__main__":
    unittest.main()
