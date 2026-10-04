# bundle/

The knowledge base, in three files: `bundle.json` and `trackers.json` joined on tracker id
(`exodus-<n>` / `fp-<slug>`), and `jurisdictions.json`, joined on country code. `schema.json` is
the contract for `bundle.json` and `jurisdictions.json` (`$defs/jurisdictions_file`). The app
downloads all three whole and joins them on the device; it never asks a server about a particular
app.

## Three files, two licences

Exodus tracker data is ODbL 1.0, and its share-alike must not reach our records, so:

- `trackers.json` — Exodus-derived tracker data (names, signatures, categories, websites) plus
  FinePrint's own `fp-*` signatures. ODbL 1.0, contents DbCL 1.0, with the εxodus attribution in
  the file. Built by `pipeline/fetch_trackers.py`, which writes the same file into the app's assets
  so scanning works before the first download.
- `bundle.json` — our reviewed records: apps, tracker explanations, companies, permission and
  device-reach boilerplate. CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/), attribution
  FinePrint. It references trackers by id only and does not copy Exodus fields. Built by
  `pipeline/build.py` from `pipeline/reviewed/`.
- `jurisdictions.json` — for each country, the laws in force that let its government compel a
  company to hand over data, each quoted from its own text. CC BY 4.0, attribution FinePrint.
  Built by `pipeline/build.py` from the `jurisdictions` section in `pipeline/reviewed/`; the owner
  reviews every entry.

## Evidence rules

See `CLAUDE.md` for the status levels and the wording that goes with each.

- Nothing goes into `bundle.json` without human review.
- Every source carries a verbatim `quote` that supports the claim it is cited for. Fragments may be
  joined with " … "; quotes from copyrighted sources stay under 15 words.
- Every source has a date: `as_of` for the source's own date, or `accessed` for an undated page.
- Every source URL must answer HTTP 200 at build time. When a page blocks scripts, the source names
  a `verify_url` for the check (the same article's API, or an archived copy); `url` stays the page
  people read.
- A claim's first source is its primary one. A source `id` names one source, and every copy of it
  must be identical. `derives_from` marks a re-report, so copies don't count as independent.
- A legal claim may carry a `procedural_note` (a dismissal, an appeal) with its own sources. It
  never changes the claim's status.
- A legal item may say it is `in_force` (an order or settlement whose terms still bind) or give the
  `closed_date` it ended; with `appeal_pending`, these decide whether it is ongoing. Only ongoing
  items, and those that ended (or, without a closed_date, are dated) within three years, count
  toward a tier; older ones are shown under Past. A closed_date can't sit with in_force or
  appeal_pending.
- An app record's `changes[]` say what changed in the record and when, with sources. The direction
  (improved, worsened, neutral) is never typed: `build.py --previous` derives it from a structural
  diff of the record and stores the diff beside it (rules in `docs/METHOD.md`, "Changes to a
  record").
- A company record says where the company is registered (`jurisdiction`, so whose law it is subject
  to) and, when a source says so, where its head office is (`headquarters`), with corporate
  registration sources (`jurisdiction_sources`). Where its servers are is never asserted from a
  record. A line about a government has `recipient_kind: government_body`, its kind
  (`government_line`: can_compel, has_bought, has_used) and its country; a Can compel line cites
  the law's own text first (source type `statute`), and a reported Has used line needs two
  independent sources. Government lines never count toward a tier.
- A `regulatory_action` tag on an app whose record has no action against its own developer needs a
  `risk_tag_notes` qualifier, e.g. "against Allstate/Arity concerning this app's data". Tags are
  for sorting and filtering; the app never shows them as a list of labels.
- A tracker record explains the tracker with its own id, or every id in its `covers` (one company's
  kits, such as Meta's); each tracker id has one explanation at most. A tracker flow's
  `in_owner_apps` gives its bucket inside apps the tracker's owner makes; without it, a
  goes-elsewhere flow is left out there, since nothing leaves the company.
- An app's `controls` are its in-app settings for the "What you can do" checklist. Each names the
  flows it limits by `id` (flows in the same record), says what turning it off changes (`effect`:
  the app's own quoted words, attributed, or that it doesn't say), and carries its own sources.
- Wording follows the copy rules in `docs/METHOD.md` ("How records are made").
