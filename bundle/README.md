# bundle/

The knowledge base, in two files joined on tracker id (`exodus-<n>` / `fp-<slug>`). `schema.json`
is the contract for `bundle.json`. The app downloads both files whole and joins them on the device;
it never asks a server about a particular app.

## Two files, two licences

Exodus tracker data is ODbL 1.0, and its share-alike must not reach our records, so:

- `trackers.json` — Exodus-derived tracker data (names, signatures, categories, websites) plus
  Fine Print's own `fp-*` signatures. ODbL 1.0, contents DbCL 1.0, with the εxodus attribution in
  the file. Built by `pipeline/fetch_trackers.py`, which writes the same file into the app's assets
  so scanning works before the first download.
- `bundle.json` — our reviewed records: apps, tracker explanations, companies, permission and
  device-reach boilerplate. CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/), attribution
  Fine Print. It references trackers by id only and does not copy Exodus fields. Built by
  `pipeline/build.py` from `pipeline/reviewed/`.

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
- A `regulatory_action` tag on an app whose record has no action against its own developer needs a
  `risk_tag_notes` qualifier, e.g. "against Allstate/Arity concerning this app's data".
