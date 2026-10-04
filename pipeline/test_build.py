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
    base = {"schema_version": 1, "schema_revision": "1.3", "bundle_version": "2026.10.04",
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


class CoversTest(unittest.TestCase):
    def tracker(self, id_: str, **extra) -> dict:
        return dict({"id": id_, "owner": "Meta Platforms, Inc.", "categories": ["advertising"], "consequences": [],
                     "last_reviewed": "2026-10-04"}, **extra)

    def test_one_explanation_covers_several_ids(self):
        meta = self.tracker("fp-meta", covers=["exodus-47", "exodus-65"])
        self.assertEqual(build.tracker_problems([meta], {"exodus-47", "exodus-65"}), [])
        self.assertEqual(schema_errors(doc(trackers=[meta])), [])

    def test_every_covered_id_is_in_trackers_json(self):
        meta = self.tracker("fp-meta", covers=["exodus-47", "exodus-99999"])
        self.assertEqual(build.tracker_problems([meta], {"exodus-47"}),
                         ["tracker record 'fp-meta': 'exodus-99999' not in trackers.json"])
        self.assertEqual(build.tracker_problems([self.tracker("fp-nothing")], {"exodus-47"}),
                         ["tracker record 'fp-nothing': 'fp-nothing' not in trackers.json"])

    def test_an_id_has_one_explanation(self):
        ids = {"exodus-47", "exodus-65"}
        problems = build.tracker_problems([self.tracker("fp-meta", covers=["exodus-47", "exodus-65"]), self.tracker("exodus-65")], ids)
        self.assertEqual(problems, ["tracker 'exodus-65' has two explanations: 'fp-meta' and 'exodus-65'"])

    def test_in_owner_apps_is_for_tracker_records_with_an_owner(self):
        flow = {"data": "app_activity", "recipient_label": "x", "purpose": "p", "bucket": "goes_elsewhere",
                "status": "self_disclosed", "sources": [SOURCE], "in_owner_apps": "used_for_more"}
        self.assertEqual(build.tracker_problems([self.tracker("exodus-65", data_flows=[flow])], {"exodus-65"}),
                         ["tracker record 'exodus-65': in_owner_apps needs owner_company"])
        owned = self.tracker("exodus-65", owner_company="co-meta", data_flows=[flow])
        self.assertEqual(build.tracker_problems([owned], {"exodus-65"}), [])
        bundle = doc(apps=[dict(app("2026-10-01"), data_flows=[flow])])
        self.assertIn("com.example: in_owner_apps is for tracker records", build.cross_check(bundle, {"exodus-12"}))


class StandingTest(unittest.TestCase):
    def event(self, **extra) -> dict:
        return dict({"date": "2019-07-24", "title": "Order", "type": "order", "status": "adjudicated", "status_kind": "consent_order",
                     "sources": [SOURCE]}, **extra)

    def test_an_ended_matter_is_not_in_force_or_under_appeal(self):
        self.assertEqual(build.standing_problems(self.event(closed_date="2020-04-23")), [])
        self.assertEqual(build.standing_problems(self.event(in_force=True)), [])
        self.assertEqual(build.standing_problems(self.event(closed_date="2020-04-23", in_force=True)), ["'Order': closed_date with in_force"])
        self.assertEqual(build.standing_problems(self.event(closed_date="2020-04-23", appeal_pending=True)), ["'Order': closed_date with appeal_pending"])
        self.assertEqual(build.standing_problems(self.event(closed_date="2018-01-01")), ["'Order': closed_date 2018-01-01 is before its date 2019-07-24"])
        company = {"id": "co-x", "name": "X", "roles": ["developer"], "last_reviewed": "2026-10-01",
                   "regulatory_history": [self.event(in_force=True), self.event(closed_date="2020-04-23")]}
        self.assertEqual(schema_errors(doc(companies=[company])), [])


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


class ControlTest(unittest.TestCase):
    def test_a_control_limits_flows_of_its_own_app(self):
        flow = {"id": "flow-a", "data": "precise_location", "recipient_label": "x", "purpose": "p", "bucket": "goes_elsewhere",
                "status": "self_disclosed", "sources": [SOURCE]}
        control = {"id": "ctl-a", "label": "Setting", "how": "In the app", "effect": "The app doesn't say what changes.",
                   "limits": ["flow-a"], "sources": [SOURCE]}
        good = dict(app("2026-10-01"), data_flows=[flow], controls=[control])
        self.assertEqual(build.control_problems(good), [])
        self.assertEqual(schema_errors(doc(apps=[good])), [])
        bad = dict(good, controls=[dict(control, limits=["flow-missing"])], data_flows=[flow, flow])
        problems = build.control_problems(bad)
        self.assertTrue(any("flow-missing" in p for p in problems))
        self.assertTrue(any("used twice" in p for p in problems))

    def test_an_inferred_limit_says_what_is_inferred(self):
        flow = {"id": "flow-a", "data": "precise_location", "recipient_label": "x", "purpose": "p", "bucket": "goes_elsewhere",
                "status": "self_disclosed", "sources": [SOURCE]}
        control = {"id": "ctl-a", "label": "Setting", "how": "In the app", "effect": "The app doesn't say what changes.",
                   "limits": [{"flow": "flow-a", "inferred": True, "note": "The policy says X; FinePrint infers Y."}], "sources": [SOURCE]}
        good = dict(app("2026-10-01"), data_flows=[flow], controls=[control])
        self.assertEqual(build.control_problems(good), [])
        self.assertEqual(schema_errors(doc(apps=[good])), [])
        silent = dict(good, controls=[dict(control, limits=[{"flow": "flow-a", "inferred": True}])])
        self.assertTrue(any("says nothing about why" in p for p in build.control_problems(silent)))


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
