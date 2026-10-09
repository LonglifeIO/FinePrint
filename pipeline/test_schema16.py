#!/usr/bin/env python3
"""Tests for schema 1.6's fields in bundle/schema.json and build.py:  python3 pipeline/test_schema16.py"""
from __future__ import annotations

import copy
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build  # noqa: E402

SCHEMA = json.loads(build.SCHEMA.read_text(encoding="utf-8"))
SOURCE = {"url": "https://example.org/a", "type": "company_site", "as_of": "2026-01-01", "status": "self_disclosed", "quote": "q"}
LAWSUIT = {"url": "https://example.org/suit", "type": "lawsuit", "as_of": "2025-01-13", "status": "alleged", "quote": "alleges"}


def flow(**extra) -> dict:
    return dict({"id": "flow-a", "data": "app_activity", "recipient_label": "Partners", "purpose": "ads",
                 "bucket": "goes_elsewhere", "status": "self_disclosed", "sources": [SOURCE]}, **extra)


def event(**extra) -> dict:
    return dict({"date": "2025-01-13", "title": "A v. B", "type": "lawsuit", "status": "alleged", "sources": [LAWSUIT]}, **extra)


def company(**extra) -> dict:
    return dict({"id": "co-x", "name": "X", "roles": ["tracker_owner"], "jurisdiction": "US",
                 "jurisdiction_sources": [SOURCE], "last_reviewed": "2026-10-01"}, **extra)


def tracker(**extra) -> dict:
    return dict({"id": "exodus-12", "owner": "X", "categories": ["analytics"], "consequences": [], "last_reviewed": "2026-10-01"}, **extra)


def app(**extra) -> dict:
    return dict({"package_id": "com.example", "display_name": "Example", "summary": "s", "trackers": [],
                 "consequences": [], "coverage": "curated", "last_reviewed": "2026-10-04"}, **extra)


def bundle(**sections) -> dict:
    base = {"schema_version": 1, "schema_revision": "1.6", "bundle_version": "2026.10.08",
            "generated_at": "2026-10-08T12:00:00-03:00", "apps": [], "trackers": [], "companies": [],
            "permissions": [], "device_reach": []}
    return dict(base, **sections)


def schema_errors(doc: dict) -> list[str]:
    return build.schema_problems(SCHEMA, doc)


class SchemaTest(unittest.TestCase):
    def test_every_new_field_validates(self):
        doc = bundle(
            apps=[app(checked_on="2026-10-04", data_flows=[flow(conditional="if the developer turns on data sharing"), flow(id="flow-b", status="alleged", forum="regulator", sources=[LAWSUIT])],
                      consequences=[{"text": "A complaint alleges it.", "status": "alleged", "forum": "regulator", "sources": [LAWSUIT]}])],
            trackers=[tracker(purpose={"kind": "crash_reporting", "sources": [SOURCE]}, confidence="medium",
                              confidence_note={"text": "Who makes this SDK isn't certain.", "sources": [SOURCE]})],
            companies=[company(owner_history=[{"date": "2021", "event": "acquired", "from": "X-Mode Social", "to": "Digital Envoy", "sources": [SOURCE]}],
                               confidence_note={"text": "Reports disagree about the parent.", "sources": [SOURCE]},
                               regulatory_history=[event(forum="court")])],
        )
        self.assertEqual([], schema_errors(doc))

    def test_the_revision_is_one_six(self):
        self.assertEqual([], schema_errors(bundle()))
        self.assertTrue(schema_errors(bundle(schema_revision="1.5")))

    def test_bad_values_fail(self):
        bad = [
            bundle(companies=[company(regulatory_history=[event(forum="tribunal")])]),
            bundle(trackers=[tracker(purpose={"kind": "marketing", "sources": [SOURCE]})]),
            bundle(trackers=[tracker(purpose={"kind": "ads"})]),  # a purpose is sourced like any claim
            bundle(trackers=[tracker(confidence_note={"text": "unsourced"})]),
            bundle(companies=[company(owner_history=[{"date": "2023", "event": "merged", "from": "A", "to": "B"}])]),
            bundle(apps=[app(checked_on="4 October 2026")]),
            bundle(apps=[app(data_flows=[flow(conditional="")])]),
        ]
        for doc in bad:
            with self.subTest(doc=json.dumps(doc)[-160:]):
                self.assertTrue(schema_errors(doc))


class CrossCheckTest(unittest.TestCase):
    def test_forum_is_for_legal_lines(self):
        doc = bundle(apps=[app(data_flows=[flow(forum="court")])])
        self.assertIn("'flow-a': forum is for alleged or adjudicated lines", build.cross_check(doc, set()))
        self.assertEqual([], [e for e in build.cross_check(bundle(companies=[company(regulatory_history=[event(forum="court")])]), set()) if "forum" in e])

    def test_checked_on_is_not_after_the_build(self):
        doc = bundle(apps=[app(checked_on="2026-10-09")])
        self.assertIn("com.example: checked_on 2026-10-09 is after the build (2026-10-08)", build.cross_check(doc, set()))

    def test_owner_history_runs_oldest_first(self):
        history = [{"date": "2023-11-29", "event": "merged", "from": "A", "to": "B", "sources": [SOURCE]},
                   {"date": "2021", "event": "acquired", "from": "C", "to": "A", "sources": [SOURCE]}]
        self.assertIn("co-x: owner_history isn't oldest first (2023-11-29 before 2021)", build.cross_check(bundle(companies=[company(owner_history=history)]), set()))


class DiffTest(unittest.TestCase):
    def test_a_conditional_flow_is_not_structure(self):
        old = app(data_flows=[flow()])
        new = copy.deepcopy(old)
        new["data_flows"].append(flow(id="flow-b", data="device_identifiers", conditional="if the developer turns on data sharing"))
        self.assertEqual([], build.structural_diff(old, new))

    def test_forum_is_not_structure(self):
        old = app(consequences=[{"text": "t", "status": "alleged", "sources": [LAWSUIT]}])
        new = copy.deepcopy(old)
        new["consequences"][0]["forum"] = "court"
        self.assertEqual([], build.structural_diff(old, new))


if __name__ == "__main__":
    unittest.main()
