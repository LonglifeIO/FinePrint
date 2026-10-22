#!/usr/bin/env python3
"""Tests for schema 1.8 (guided paths, short body names, changes on companies and trackers):  python3 pipeline/test_schema18.py"""
from __future__ import annotations

import copy
import datetime as dt
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build  # noqa: E402

VALIDATOR = build.jsonschema.Draft202012Validator(
    json.loads(build.SCHEMA.read_text(encoding="utf-8")), format_checker=build.jsonschema.FormatChecker())
HELP = "https://help.example.com/articles/1-privacy-center"
HELP_SOURCE = {"url": HELP, "type": "company_site", "as_of": "2026-05-11", "status": "self_disclosed", "quote": "Tap on Privacy Center."}
POLICY = {"url": "https://example.com/privacy", "type": "privacy_policy", "as_of": "2026-08-26", "status": "self_disclosed", "quote": "q"}
FLOW = {"id": "flow-a", "data": "precise_location", "recipient_label": "x", "purpose": "p", "bucket": "goes_elsewhere",
        "status": "self_disclosed", "sources": [POLICY]}
PATH = {"steps": [{"screen": "Example", "tap": "Settings", "note": "In the upper left corner."}, {"screen": "Settings", "tap": "Privacy Center"}],
        "ends_in": {"kind": "open_app"}, "sources": [HELP_SOURCE]}
CONTROL = {"id": "ctl-a", "label": "Your Privacy Choices", "how": "In the app: Settings > Privacy Center", "effect": "The app doesn't say what changes.",
           "limits": ["flow-a"], "sources": [POLICY], "path": PATH, "help_url": HELP, "verified_on": {"app_version": "26.37.0", "date": "2026-10-20"}}
EVENT = {"date": "2025-09-23", "title": "Joint investigation", "body": "Privacy commissioners of Canada, Quebec, British Columbia and Alberta",
         "body_short": "four Canadian privacy commissioners", "type": "regulator_action", "status": "adjudicated",
         "status_kind": "regulator_finding", "sources": [POLICY]}


def app(control: dict | None = None) -> dict:
    return {"package_id": "com.example", "display_name": "Example", "summary": "s", "trackers": ["exodus-12"], "consequences": [],
            "coverage": "curated", "last_reviewed": "2026-10-01", "data_flows": [FLOW], "controls": [control or CONTROL]}


def company(**extra) -> dict:
    registry = {"url": "https://example.org/registry", "type": "company_registry", "as_of": "2026-01-01", "status": "self_disclosed", "quote": "Delaware"}
    return dict({"id": "co-x", "name": "X", "roles": ["developer"], "jurisdiction": "US", "jurisdiction_sources": [registry],
                 "last_reviewed": "2026-10-01"}, **extra)


def doc(revision: str = "1.8", **sections) -> dict:
    base = {"schema_version": 1, "schema_revision": revision, "bundle_version": "2026.10.22", "generated_at": "2026-10-22T12:00:00-03:00",
            "apps": [], "trackers": [], "companies": [], "permissions": [], "device_reach": []}
    return dict(base, **sections)


def schema_errors(bundle: dict) -> list[str]:
    return [f"{'/'.join(map(str, e.absolute_path))}: {e.message}" for e in VALIDATOR.iter_errors(bundle)]


def with_control(**changes) -> dict:
    """CONTROL with [changes]; a value of None leaves that key out."""
    control = copy.deepcopy(CONTROL)
    for key, value in changes.items():
        if value is None:
            control.pop(key, None)
        else:
            control[key] = value
    return control


def path_errors(control: dict, built: str = "2026-10-22") -> list[str]:
    return build.path_problems(app(control), built)


class GuidedPathTest(unittest.TestCase):
    def test_a_path_validates_and_passes_the_build_checks(self):
        bundle = doc(apps=[app()])
        self.assertEqual(schema_errors(bundle), [])
        self.assertEqual(build.cross_check(bundle, {"exodus-12"}), [])

    def test_the_schema_holds_a_paths_shape(self):
        def path(**changes) -> dict:
            return dict(copy.deepcopy(PATH), **changes)
        broken = {
            "no steps": with_control(path=path(steps=[])),
            "a step with nothing to tap": with_control(path=path(steps=[{"screen": "Settings"}])),
            "a step with a field of its own": with_control(path=path(steps=[{"screen": "Settings", "tap": "Privacy", "x": 1}])),
            "a web page without its address": with_control(path=path(ends_in={"kind": "url"})),
            "the app with an address": with_control(path=path(ends_in={"kind": "open_app", "url": HELP})),
            "an ending of another kind": with_control(path=path(ends_in={"kind": "deep_link"})),
            "no sources": with_control(path={k: v for k, v in PATH.items() if k != "sources"}),
            "a source without a quote": with_control(path=path(sources=[{k: v for k, v in HELP_SOURCE.items() if k != "quote"}])),
            "a path without its help page": with_control(help_url=None),
            "verified_on without a path": with_control(path=None, help_url=None),
            "verified_on without a version": with_control(verified_on={"date": "2026-10-20"}),
        }
        for what, control in broken.items():
            with self.subTest(what):
                self.assertTrue(schema_errors(doc(apps=[app(control)])), what)
        web = with_control(path=dict(copy.deepcopy(PATH), ends_in={"kind": "url", "url": "https://example.com/settings"}))
        self.assertEqual(schema_errors(doc(apps=[app(web)])), [])

    def test_a_path_nobody_has_walked_stands_on_its_help_page(self):
        unwalked = with_control(verified_on=None)
        self.assertEqual(schema_errors(doc(apps=[app(unwalked)])), [])
        self.assertEqual(build.cross_check(doc(apps=[app(unwalked)]), {"exodus-12"}), [])
        undated = with_control(verified_on=None, path=dict(copy.deepcopy(PATH), sources=[
            {k: v for k, v in dict(HELP_SOURCE, accessed="2026-10-22").items() if k != "as_of"}]))
        self.assertEqual(path_errors(undated), ["com.example: control 'ctl-a': its help page has no date of its own (as_of), which the last card gives"])

    def test_the_build_checks_each_step_the_date_walked_and_the_help_page(self):
        self.assertEqual(path_errors(CONTROL), [])
        blank = with_control(path=dict(copy.deepcopy(PATH), steps=[{"screen": " ", "tap": "Settings"}]))
        self.assertEqual(path_errors(blank), ["com.example: control 'ctl-a': step 1 names no screen or nothing to tap"])
        empty = with_control(path=dict(copy.deepcopy(PATH), steps=[]))
        self.assertIn("com.example: control 'ctl-a': its path has no steps", path_errors(empty))
        future = with_control(verified_on={"app_version": "26.37.0", "date": "2026-10-23"})
        self.assertEqual(path_errors(future), ["com.example: control 'ctl-a': verified_on 2026-10-23 is after the build (2026-10-22)"])
        self.assertEqual(path_errors(with_control(verified_on={"app_version": "26.37.0", "date": "2026-10-22"})), [])
        elsewhere = with_control(help_url="https://help.example.com/articles/2-other")
        self.assertEqual(path_errors(elsewhere), ["com.example: control 'ctl-a': help_url isn't one of the pages its path quotes"])

    def test_a_paths_sources_carry_quotes_like_any_source_and_its_ending_is_no_source(self):
        unquoted = with_control(path=dict(copy.deepcopy(PATH), sources=[dict(HELP_SOURCE, quote=" ")]))
        self.assertIn(f"source without a quote: {HELP}", build.cross_check(doc(apps=[app(unquoted)]), {"exodus-12"}))
        web = with_control(path=dict(copy.deepcopy(PATH), ends_in={"kind": "url", "url": "https://example.com/settings"}))
        self.assertEqual(build.cross_check(doc(apps=[app(web)]), {"exodus-12"}), [])

    def test_only_a_walk_goes_stale_90_days_on_and_a_path_nobody_walked_never_does(self):
        def stale(page: str, walked: str | None) -> bool:
            path = dict(copy.deepcopy(PATH), sources=[dict(HELP_SOURCE, as_of=page)])
            control = with_control(path=path, verified_on={"app_version": "1", "date": walked} if walked else None)
            apps = [app(control)]
            build.mark_paths_stale(apps, dt.date(2026, 10, 22))
            return apps[0]["controls"][0]["path"]["stale"]
        self.assertEqual([stale("2026-05-11", "2026-07-23"), stale("2026-05-11", "2026-07-24")], [True, False])  # 91 and 90 days
        self.assertFalse(stale("2026-05-11", None), "Life360's help page, 164 days old, nobody has walked it: never stale")
        self.assertFalse(stale("2020-01-01", None), "however old the company's page is")
        self.assertTrue(stale("2026-10-01", "2026-05-01"), "a walk older than 90 days, however new the page")
        with tempfile.TemporaryDirectory() as d:
            reviewed = Path(d) / "app.json"
            reviewed.write_text(json.dumps({"apps": [app()]}), encoding="utf-8")
            built = build.build([reviewed], dt.datetime(2026, 10, 22, 12, tzinfo=build.HALIFAX))
        self.assertEqual(built["schema_revision"], "1.8")
        control = built["apps"][0]["controls"][0]
        self.assertEqual((control["path"]["stale"], control["verified_on"]), (False, {"app_version": "26.37.0", "date": "2026-10-20"}))
        self.assertEqual(schema_errors(built), [])


class ShortBodyTest(unittest.TestCase):
    def test_a_short_body_name_comes_with_its_body_and_is_shorter(self):
        self.assertEqual(schema_errors(doc(companies=[company(regulatory_history=[EVENT])])), [])
        self.assertEqual(build.body_short_problems(EVENT), [])
        alone = {k: v for k, v in EVENT.items() if k != "body"}
        self.assertEqual(build.body_short_problems(alone), ["'Joint investigation': body_short needs a body"])
        longer = dict(EVENT, body_short=EVENT["body"] + " together")
        self.assertEqual(build.body_short_problems(longer), ["'Joint investigation': body_short is no shorter than its body"])
        self.assertTrue(schema_errors(doc(companies=[company(regulatory_history=[dict(EVENT, body_short="")])])))
        self.assertIn("'Joint investigation': body_short needs a body", build.cross_check(doc(companies=[company(regulatory_history=[alone])]), set()))


class RecordChangesTest(unittest.TestCase):
    CHANGE = {"date": "2026-10-20", "text": "FinePrint corrected the company's head office.", "sources": [POLICY]}

    def test_companies_and_trackers_carry_dated_changes_with_sources_and_no_direction(self):
        tracker = {"id": "exodus-12", "owner": "X", "categories": ["analytics"], "consequences": [], "last_reviewed": "2026-10-01"}
        self.assertEqual(schema_errors(doc(companies=[company(changes=[self.CHANGE])], trackers=[dict(tracker, changes=[self.CHANGE])])), [])
        unsourced = {k: v for k, v in self.CHANGE.items() if k != "sources"}
        self.assertTrue(schema_errors(doc(companies=[company(changes=[unsourced])])))
        self.assertTrue(schema_errors(doc(trackers=[dict(tracker, changes=[dict(self.CHANGE, direction="improved")])])))


class OldBundlesTest(unittest.TestCase):
    def test_a_17_bundle_still_validates_and_no_other_revision_does(self):
        plain = app({k: v for k, v in CONTROL.items() if k not in ("path", "help_url", "verified_on")})
        self.assertEqual(schema_errors(doc("1.7", apps=[plain])), [])
        self.assertEqual(schema_errors(doc("1.8", apps=[plain])), [])
        self.assertTrue(schema_errors(doc("1.9", apps=[plain])))


if __name__ == "__main__":
    unittest.main()
