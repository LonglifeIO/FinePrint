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
REGISTRY = {"url": "https://example.org/registry", "type": "company_registry", "as_of": "2026-01-01", "status": "self_disclosed", "quote": "Delaware"}
STATUTE = {"url": "https://example.org/law", "type": "statute", "as_of": "2018-03-23", "status": "self_disclosed", "quote": "shall disclose"}


def company(id_: str, **extra) -> dict:
    return dict({"id": id_, "name": "X", "roles": ["developer"], "jurisdiction": "US", "jurisdiction_sources": [REGISTRY],
                 "last_reviewed": "2026-10-01"}, **extra)
VALIDATOR = build.jsonschema.Draft202012Validator(
    json.loads(build.SCHEMA.read_text(encoding="utf-8")), format_checker=build.jsonschema.FormatChecker())


def doc(**sections) -> dict:
    base = {"schema_version": 1, "schema_revision": "1.5", "bundle_version": "2026.10.04",
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
            "companies": [company("co-known")],
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

    def test_android_jurisdictions_fixture_follows_the_schema(self):
        fixture = build.REPO / "android" / "app" / "src" / "test" / "resources" / "jurisdictions-fixture.json"
        schema = json.loads(build.SCHEMA.read_text(encoding="utf-8"))
        self.assertEqual(build.schema_problems(schema, json.loads(fixture.read_text(encoding="utf-8")), "jurisdictions_file"), [])

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
        record = company("co-x", regulatory_history=[self.event(in_force=True), self.event(closed_date="2020-04-23")])
        self.assertEqual(schema_errors(doc(companies=[record])), [])


class ChangeDirectionTest(unittest.TestCase):
    FLOW = {"id": "flow-partners", "data": "precise_location", "recipient_label": "Partners", "purpose": "Their own use",
            "bucket": "goes_elsewhere", "status": "self_disclosed", "sources": [SOURCE]}

    def record(self, flows=(), trackers=("exodus-12",), controls=()) -> dict:
        return dict(app("2026-10-01"), data_flows=list(flows), trackers=list(trackers), controls=list(controls))

    def test_wording_alone_is_neutral(self):
        old = self.record([self.FLOW])
        new = self.record([dict(self.FLOW, purpose="Partners' own purposes", wording="According to its policy.")])
        self.assertEqual(build.structural_diff(old, dict(new, summary="Reworded.")), [])
        self.assertEqual(build.direction_of([]), "neutral")
        # A reworded unnamed recipient is still the same flow, even without a flow id.
        unnamed = {k: v for k, v in self.FLOW.items() if k != "id"}
        self.assertEqual(build.structural_diff(self.record([unnamed]), self.record([dict(unnamed, recipient_label="Selected partners")])), [])

    def test_two_flows_of_the_same_data_to_the_same_recipient_pair_in_order(self):
        # Facebook's record has two: ads on Meta's apps, and ads in other apps through Audience Network.
        named = {"data": "app_activity", "recipient": "co-meta", "purpose": "Ads on Meta's apps", "bucket": "used_for_more",
                 "status": "self_disclosed", "sources": [SOURCE]}
        twice = [named, dict(named, purpose="Ads in other apps")]
        self.assertEqual(build.structural_diff(self.record(twice), self.record(twice)), [])
        self.assertEqual(build.structural_diff(self.record(twice), self.record(twice[:1])), ["flow removed: app_activity to co-meta (used_for_more)"])
        # Every reviewed app against itself, with the legal items its companies and trackers hold.
        merged = build.merge(sorted((Path(__file__).resolve().parent / "reviewed").glob("*.json")))
        for a in merged["apps"]:
            items = build.legal_items(a, merged["companies"], merged["trackers"])
            self.assertEqual(build.structural_diff(a, a, items, dict(items)), [], a["package_id"])
        facebook = next(a for a in merged["apps"] if a["package_id"] == "com.facebook.katana")
        self.assertEqual(len(build.legal_items(facebook, merged["companies"], merged["trackers"])), 12)  # 8 Meta actions, 4 of its own lines

    ACTION = {"date": "2026-09-01", "title": "A ruling", "body": "A regulator", "type": "fine", "status": "adjudicated",
              "status_kind": "ruling", "concerns_app": "com.example", "sources": [SOURCE]}
    LINE = {"text": "A court fined it.", "status": "adjudicated", "status_kind": "ruling", "sources": [SOURCE]}

    def legal(self, actions=(), lines=(), tracker_lines=()) -> dict:
        tracker = {"id": "fp-x", "covers": ["exodus-12"], "consequences": list(tracker_lines)}
        return build.legal_items(dict(self.record(), consequences=list(lines)), [company("co-x", regulatory_history=list(actions))], [tracker])

    def test_a_ruling_or_lawsuit_naming_the_app_counts_and_moves_with_the_tier(self):
        r, none, one = self.record(), self.legal(), self.legal([self.ACTION])
        added = build.structural_diff(r, r, none, one)
        self.assertEqual(added, ["event added: co-x 2026-09-01 fine (adjudicated/ruling)"])
        self.assertEqual(build.direction_of(added, "caution", "flagged"), "worsened")
        self.assertEqual(build.direction_of(added, "flagged", "flagged"), "neutral")
        closed = self.legal([dict(self.ACTION, closed_date="2026-10-01")])
        self.assertEqual(build.structural_diff(r, r, one, closed),
                         ["event status: co-x 2026-09-01 fine (adjudicated/ruling to adjudicated/ruling, closed 2026-10-01)"])
        self.assertEqual(build.direction_of(build.structural_diff(r, r, one, closed), "flagged", "caution"), "improved")
        self.assertEqual(build.structural_diff(r, r, one, none), ["event removed: co-x 2026-09-01 fine (adjudicated/ruling)"])
        # Reworded: no change. About another app, or a breach: not counted.
        self.assertEqual(build.structural_diff(r, r, one, self.legal([dict(self.ACTION, title="A fine", body="The regulator")])), [])
        self.assertEqual(self.legal([dict(self.ACTION, concerns_app="com.other"), dict(self.ACTION, type="breach")]), {})

    def test_the_apps_and_its_trackers_own_legal_lines_count(self):
        r, line = self.record(), self.LINE
        self.assertEqual(build.structural_diff(r, r, self.legal(), self.legal(lines=[line])),
                         ["event added: com.example https://example.org/a (adjudicated/ruling)"])
        lawsuit = dict(line, status="alleged", status_kind="filed")
        self.assertEqual(build.structural_diff(r, r, self.legal(lines=[lawsuit]), self.legal(lines=[dict(lawsuit, status_kind="survived_motion_to_dismiss")])),
                         ["event status: com.example https://example.org/a (alleged/filed to alleged/survived_motion_to_dismiss)"])
        # Reworded, or its source swapped for another: the same line.
        moved = dict(line, text="A regulator fined it.", sources=[dict(SOURCE, url="https://example.org/b")])
        self.assertEqual(build.structural_diff(r, r, self.legal(lines=[line]), self.legal(lines=[moved])), [])
        # A reported line isn't legal; a tracker's line counts only when it names the app.
        self.assertEqual(self.legal(lines=[dict(line, status="reported")]), {})
        naming = dict(line, concerns_app="com.example", sources=[dict(SOURCE, url="https://example.org/c")])
        self.assertEqual(list(self.legal(tracker_lines=[line, naming])), ["fp-x https://example.org/c"])

    def test_a_flows_evidence_counts_and_moves_with_the_tier(self):
        one = dict(self.FLOW, status="reported")
        two = dict(one, sources=[SOURCE, dict(SOURCE, url="https://example.org/b")])
        diff = build.structural_diff(self.record([one]), self.record([two]))
        self.assertEqual(diff, ["flow status: precise_location to Partners (reported, one source to reported)"])
        self.assertEqual(build.direction_of(diff, "caution", "flagged"), "worsened")
        self.assertEqual(build.structural_diff(self.record([two]), self.record([dict(two, status="self_disclosed")])),
                         ["flow status: precise_location to Partners (reported to self_disclosed)"])
        # A copy of the same report isn't a second source.
        copy = dict(one, sources=[SOURCE, dict(SOURCE, url="https://example.org/b", derives_from="https://example.org/a")])
        self.assertEqual(build.structural_diff(self.record([one]), self.record([copy])), [])

    def test_a_legal_or_evidence_change_records_the_tiers(self):
        change = {"date": "2026-10-05", "text": "A ruling.", "direction": "neutral",
                  "diff": ["event added: co-x 2026-09-01 fine (adjudicated/ruling)"], "sources": [SOURCE]}
        self.assertEqual(build.change_problems(dict(self.record(), changes=[change])), [
            "com.example: change of 2026-10-05 names a legal item or a flow's evidence; record tier_before and tier_after, which set its direction"])
        tiered = dict(change, tier_before="caution", tier_after="flagged", direction="worsened")
        self.assertEqual(build.change_problems(dict(self.record(), changes=[tiered])), [])
        self.assertEqual(schema_errors(doc(apps=[dict(self.record(), changes=[tiered])])), [])

    def test_derive_directions_reads_the_previous_companies_too(self):
        change = {"date": "2026-10-05", "text": "A regulator ruled.", "tier_before": "caution", "tier_after": "flagged", "sources": [SOURCE]}
        with tempfile.TemporaryDirectory() as d:
            files = {name: Path(d) / name for name in ("app-example.json", "companies.json", "old-app.json", "old-companies.json")}
            files["app-example.json"].write_text(json.dumps({"apps": [dict(self.record(), changes=[change])]}), encoding="utf-8")
            files["companies.json"].write_text(json.dumps({"companies": [company("co-x", regulatory_history=[self.ACTION])]}), encoding="utf-8")
            files["old-app.json"].write_text(json.dumps(self.record()), encoding="utf-8")
            files["old-companies.json"].write_text(json.dumps({"companies": [company("co-x")]}), encoding="utf-8")
            done = build.derive_directions([files["app-example.json"], files["companies.json"]], [files["old-app.json"], files["old-companies.json"]])
            self.assertEqual(done, ["com.example, change of 2026-10-05: worsened (1 structural changes)"])
            derived = json.loads(files["app-example.json"].read_text(encoding="utf-8"))["apps"][0]["changes"][0]
        self.assertEqual(derived["diff"], ["event added: co-x 2026-09-01 fine (adjudicated/ruling)"])

    def test_a_new_flow_beyond_running_the_app_is_worse(self):
        diff = build.structural_diff(self.record(), self.record([self.FLOW]))
        self.assertEqual(diff, ["flow added: precise_location to Partners (goes_elsewhere)", "data kind added: precise_location"])
        self.assertEqual(build.direction_of(diff), "worsened")

    def test_moving_toward_stays_here_is_better(self):
        diff = build.structural_diff(self.record([self.FLOW]), self.record([dict(self.FLOW, bucket="stays_here")]))
        self.assertEqual(diff, ["moved toward stays here: precise_location to Partners (goes_elsewhere to stays_here)"])
        self.assertEqual(build.direction_of(diff), "improved")
        self.assertEqual(build.direction_of(build.structural_diff(self.record([dict(self.FLOW, bucket="used_for_more")]), self.record([self.FLOW]))), "worsened")

    def test_a_flow_that_ends_trackers_and_controls(self):
        ended = build.structural_diff(self.record([self.FLOW]), self.record([dict(self.FLOW, historical=True)]))
        self.assertEqual(ended, ["flow removed: precise_location to Partners (goes_elsewhere, now a past practice)", "data kind removed: precise_location"])
        self.assertEqual(build.direction_of(ended), "improved")
        control = {"id": "ctl-a", "label": "Setting", "how": "In the app", "effect": "x", "limits": ["flow-partners"], "sources": [SOURCE]}
        better = build.structural_diff(self.record([self.FLOW], trackers=("exodus-12", "exodus-65")), self.record([self.FLOW], controls=[control]))
        self.assertEqual(better, ["tracker removed: exodus-65", "control added: ctl-a"])
        self.assertEqual(build.direction_of(better), "improved")
        self.assertEqual(build.direction_of(build.structural_diff(self.record(), self.record(trackers=("exodus-12", "exodus-65")))), "worsened")

    def test_a_flow_that_stops_happening_by_default_is_better(self):
        off = build.structural_diff(self.record([self.FLOW]), self.record([dict(self.FLOW, default="off")]))
        self.assertEqual(off, ["now off by default: precise_location to Partners (on to off)"])
        self.assertEqual(build.direction_of(off), "improved")
        on = build.structural_diff(self.record([dict(self.FLOW, default="opt_in")]), self.record([self.FLOW]))
        self.assertEqual(build.direction_of(on), "worsened")
        self.assertEqual(build.structural_diff(self.record([dict(self.FLOW, default="off")]), self.record([dict(self.FLOW, default="opt_in")])), [])
        self.assertEqual(schema_errors(doc(apps=[dict(self.record([dict(self.FLOW, default="off")]), policy_region="US")])), [])
        self.assertTrue(schema_errors(doc(apps=[dict(self.record([dict(self.FLOW, default="sometimes")]))])))

    def test_a_change_that_does_both_is_worse(self):
        mixed = build.structural_diff(self.record([self.FLOW]), self.record([dict(self.FLOW, historical=True)], trackers=("exodus-12", "exodus-65")))
        self.assertEqual(build.direction_of(mixed), "worsened")

    def test_derive_directions_writes_the_reviewed_file_and_checks_hold(self):
        change = {"date": "2026-10-05", "text": "Partners now get precise location.", "sources": [SOURCE]}
        with tempfile.TemporaryDirectory() as d:
            reviewed = Path(d) / "app-example.json"
            reviewed.write_text(json.dumps({"apps": [dict(self.record([self.FLOW]), changes=[change])]}, indent=1) + "\n", encoding="utf-8")
            pending = json.loads(reviewed.read_text(encoding="utf-8"))["apps"][0]
            self.assertIn("has no direction yet", build.change_problems(pending)[0])
            previous = Path(d) / "old.json"
            previous.write_text(json.dumps(self.record()), encoding="utf-8")
            self.assertEqual(build.derive_directions([reviewed], [previous]), ["com.example, change of 2026-10-05: worsened (2 structural changes)"])
            derived = json.loads(reviewed.read_text(encoding="utf-8"))["apps"][0]
        self.assertEqual(list(derived["changes"][0]), ["date", "text", "direction", "diff", "sources"])
        self.assertEqual(build.change_problems(derived), [])
        self.assertEqual(schema_errors(doc(apps=[derived])), [])
        typed = dict(derived, changes=[dict(derived["changes"][0], direction="improved")])
        self.assertEqual(build.change_problems(typed), ["com.example: change of 2026-10-05 says improved, but its diff makes it worsened"])


class GovernmentTest(unittest.TestCase):
    def line(self, kind: str, status: str = "self_disclosed", sources=(STATUTE,)) -> dict:
        return {"data": "precise_location", "recipient_kind": "government_body", "recipient_label": "A government agency",
                "government_line": kind, "jurisdiction": "US", "purpose": "p", "bucket": "goes_elsewhere",
                "status": status, "sources": list(sources)}

    def test_a_company_names_where_it_is_registered_with_sources(self):
        self.assertEqual(schema_errors(doc(companies=[company("co-x", headquarters="US")])), [])
        bare = {k: v for k, v in company("co-x").items() if k not in ("jurisdiction", "jurisdiction_sources")}
        self.assertEqual(sorted(schema_errors(doc(companies=[bare]))),
                         ["'jurisdiction' is a required property", "'jurisdiction_sources' is a required property"])
        self.assertTrue(schema_errors(doc(companies=[company("co-x", jurisdiction="USA")])))

    def test_a_government_line_names_its_kind_and_country_and_no_company(self):
        good = dict(app("2026-10-01"), data_flows=[self.line("can_compel")])
        self.assertEqual(schema_errors(doc(apps=[good])), [])
        self.assertEqual(build.cross_check(doc(apps=[good]), {"exodus-12"}), [])
        missing = {k: v for k, v in self.line("has_bought").items() if k != "jurisdiction"}
        self.assertTrue(schema_errors(doc(apps=[dict(app("2026-10-01"), data_flows=[missing])])))
        with_company = dict(self.line("has_bought"), recipient="co-x")
        self.assertTrue(schema_errors(doc(apps=[dict(app("2026-10-01"), data_flows=[with_company])])))
        stray = {k: v for k, v in self.line("has_bought").items() if k != "recipient_kind"}
        self.assertTrue(schema_errors(doc(apps=[dict(app("2026-10-01"), data_flows=[stray])])))

    def test_can_compel_cites_the_law_and_has_used_needs_two_sources(self):
        self.assertEqual(build.government_problems(self.line("can_compel", sources=[SOURCE])),
                         ["'A government agency': a Can compel line's first source is the law's own text (type statute)"])
        one = self.line("has_used", status="reported", sources=[SOURCE])
        self.assertEqual(build.government_problems(one), ["'A government agency': a Has used line needs two independent sources"])
        two = self.line("has_used", status="reported", sources=[dict(SOURCE, url="https://example.org/b"), SOURCE])
        self.assertEqual(build.government_problems(two), [])

    def test_the_jurisdictions_file(self):
        law = {"id": "law-us-cloud-act", "name": "CLOUD Act", "citation": "18 U.S.C. § 2713", "text": "t", "status": "self_disclosed",
               "sources": [STATUTE], "last_reviewed": "2026-10-04",
               "scope": {"text": "It binds providers.", "sources": [STATUTE]}}
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "jurisdictions.json"
            path.write_text(json.dumps({"jurisdictions": [{"id": "US", "name": "United States", "laws": [law], "last_reviewed": "2026-10-04"}]}))
            places = build.build_jurisdictions([path], dt.datetime(2026, 10, 4, tzinfo=build.HALIFAX))
            self.assertNotIn("jurisdictions", build.build([path], dt.datetime(2026, 10, 4, tzinfo=build.HALIFAX)))
        schema = json.loads(build.SCHEMA.read_text(encoding="utf-8"))
        self.assertEqual(build.schema_problems(schema, places, "jurisdictions_file"), [])
        self.assertEqual(build.law_problems(places), [])
        twice = dict(places, jurisdictions=[dict(places["jurisdictions"][0], laws=[law, dict(law, sources=[SOURCE])])])
        self.assertEqual(build.law_problems(twice), ["law id 'law-us-cloud-act' used twice",
                                                     "law-us-cloud-act: its first source is the law's own text (type statute)"])
        self.assertEqual(build.jurisdictions_path(Path("bundle/bundle.json")), Path("bundle/jurisdictions.json"))

    def test_each_law_carries_its_own_review_date_and_goes_stale_like_an_app(self):
        law = {"id": "law-us-x", "name": "X Act", "citation": "1 U.S.C. § 1", "text": "t", "status": "self_disclosed", "sources": [STATUTE],
               "scope": {"text": "It binds providers.", "sources": [STATUTE]}}
        def places(*laws):
            with tempfile.TemporaryDirectory() as d:
                path = Path(d) / "jurisdictions.json"
                path.write_text(json.dumps({"jurisdictions": [{"id": "US", "name": "United States", "laws": list(laws),
                                                               "last_reviewed": "2026-10-07"}]}))
                return build.build_jurisdictions([path], dt.datetime(2026, 10, 7, tzinfo=build.HALIFAX))
        schema = json.loads(build.SCHEMA.read_text(encoding="utf-8"))
        self.assertIn("'last_reviewed' is a required property", " ".join(build.schema_problems(schema, places(law), "jurisdictions_file")))
        fresh, old = dict(law, last_reviewed="2026-04-10"), dict(law, id="law-us-y", last_reviewed="2026-04-09")
        built = places(fresh, old)
        self.assertEqual(build.schema_problems(schema, built, "jurisdictions_file"), [])
        self.assertEqual([l["stale"] for l in built["jurisdictions"][0]["laws"]], [False, True])  # 180 and 181 days

    def test_every_law_says_who_it_binds(self):
        law = {"id": "law-us-x", "name": "X Act", "citation": "1 U.S.C. § 1", "text": "t", "status": "self_disclosed", "sources": [STATUTE],
               "last_reviewed": "2026-10-07"}
        places = {"schema_version": 1, "generated_at": "2026-10-07T00:00:00-03:00",
                  "jurisdictions": [{"id": "US", "name": "United States", "laws": [law], "last_reviewed": "2026-10-07"}]}
        schema = json.loads(build.SCHEMA.read_text(encoding="utf-8"))
        self.assertIn("'scope' is a required property", " ".join(build.schema_problems(schema, places, "jurisdictions_file")))
        scoped = dict(places, jurisdictions=[dict(places["jurisdictions"][0], laws=[dict(law, scope={"text": "It binds providers.", "sources": [STATUTE]})])])
        self.assertEqual(build.schema_problems(schema, scoped, "jurisdictions_file"), [])
        unsourced = dict(places, jurisdictions=[dict(places["jurisdictions"][0], laws=[dict(law, scope={"text": "It binds providers.", "sources": []})])])
        self.assertTrue(build.schema_problems(schema, unsourced, "jurisdictions_file"))

    def test_a_union_entry_says_which_countries_each_law_binds(self):
        law = {"id": "law-eu-x", "name": "X Regulation", "citation": "Regulation (EU) 1/2026", "text": "t", "status": "self_disclosed",
               "sources": [STATUTE], "last_reviewed": "2026-10-07",
               "scope": {"text": "It binds providers.", "sources": [STATUTE]}}
        eu = {"id": "EU", "name": "European Union", "laws": [law], "last_reviewed": "2026-10-07"}
        places = {"schema_version": 1, "generated_at": "2026-10-07T00:00:00-03:00", "jurisdictions": [eu]}
        self.assertEqual(build.law_problems(places), ["law-eu-x: a law in the EU entry says which countries it binds (applies_to)"])
        keyed = dict(places, jurisdictions=[dict(eu, laws=[dict(law, applies_to=["DE", "IE"])])])
        self.assertEqual(build.law_problems(keyed), [])
        schema = json.loads(build.SCHEMA.read_text(encoding="utf-8"))
        self.assertEqual(build.schema_problems(schema, keyed, "jurisdictions_file"), [])
        self.assertTrue(build.schema_problems(schema, dict(places, jurisdictions=[dict(eu, laws=[dict(law, applies_to=["DEU"])])]),
                                              "jurisdictions_file"))
        self.assertEqual(build.jurisdictions_path(Path("/tmp/bundle-preview.json")), Path("/tmp/jurisdictions-preview.json"))


class InheritanceTest(unittest.TestCase):
    FLOW = {"id": "flow-google-ads", "data": "app_activity", "recipient": "co-google", "purpose": "Ads", "bucket": "used_for_more",
            "status": "self_disclosed", "sources": [SOURCE]}

    def test_default_flows_reach_apps_through_a_prefix(self):
        google = company("co-google", package_prefixes=["com.google."], default_flows=[self.FLOW],
                         default_notes=[{"text": "t", "status": "self_disclosed", "sources": [SOURCE]}])
        self.assertEqual(build.inheritance_problems([google]), [])
        self.assertEqual(schema_errors(doc(companies=[google])), [])
        self.assertTrue(schema_errors(doc(companies=[company("co-google", package_prefixes=["com.google"])])))  # needs the dot
        orphan = company("co-x", default_flows=[self.FLOW])
        self.assertEqual(build.inheritance_problems([orphan]), ["co-x: default_flows and default_notes need package_prefixes to reach an app"])

    def test_flow_ids_prefixes_and_fields(self):
        twice = company("co-google", package_prefixes=["com.google."], default_flows=[self.FLOW, dict(self.FLOW, in_owner_apps="stays_here")])
        self.assertEqual(build.inheritance_problems([twice]), ["co-google: default flow id 'flow-google-ads' used twice",
                                                               "co-google: a default flow can't use in_owner_apps"])
        nested = [company("co-google", package_prefixes=["com.google."]), company("co-x", package_prefixes=["com.google.android."])]
        self.assertEqual(build.inheritance_problems(nested), ["package prefix 'com.google.android.' (co-x) overlaps 'com.google.' (co-google)"])


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
            return doc(companies=[company("co-x", regulatory_history=[{"date": date, "title": "t", "type": "breach",
                                                                       "status": "reported", "sources": [SOURCE]}])])
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

    def test_a_store_tagline_quotes_this_apps_own_play_listing(self):
        good = {"text": "Videos, Music & Live Streams", "as_of": "2026-10-04",
                "source_url": "https://play.google.com/store/apps/details?id=com.example&hl=en_CA&gl=CA"}
        self.assertEqual(build.tagline_problems(dict(app("2026-10-01"), store_tagline=good)), [])
        self.assertEqual(schema_errors(doc(apps=[dict(app("2026-10-01"), store_tagline=good)])), [])
        other = dict(good, source_url="https://play.google.com/store/apps/details?id=com.other&hl=en_CA")
        self.assertEqual(len(build.tagline_problems(dict(app("2026-10-01"), store_tagline=other))), 1)
        site = dict(good, source_url="https://example.com/?id=com.example")
        self.assertEqual(len(build.tagline_problems(dict(app("2026-10-01"), store_tagline=site))), 1)
        self.assertTrue(schema_errors(doc(apps=[dict(app("2026-10-01"), store_tagline=dict(good, text="x" * 81))])))
        self.assertTrue(schema_errors(doc(apps=[dict(app("2026-10-01"), store_tagline={"text": "t"})])))

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
