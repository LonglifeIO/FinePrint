# bundle/

The knowledge base. `schema.json` is the contract; `bundle.json` is the reviewed,
sourced output of the pipeline. Fine Print's own records are CC BY 4.0
(https://creativecommons.org/licenses/by/4.0/) — attribution to Fine Print.

## Licensing plan (G2)

Exodus tracker data is ODbL 1.0, and its share-alike must not reach our records, so the bundle
will ship as two files joined on tracker id (`exodus-<n>` / `fp-<slug>`):

- `bundle/trackers.json` — Exodus-derived tracker data (names, signatures, categories) plus
  Fine Print's own `fp-*` signatures. ODbL 1.0, contents DbCL 1.0, with the εxodus attribution.
- `bundle/bundle.json` — our reviewed records (owners, consequences, sources). CC BY 4.0. It
  references trackers by id only and does not copy Exodus fields.

Today the Exodus-derived file lives at `android/app/src/main/assets/trackers.json` (built by
`pipeline/fetch_trackers.py`); moving it here is G2 work.

Rules: nothing goes into `bundle.json` without human review and a resolving
`source_url` for every consequence statement. See `CLAUDE.md` for evidence standards.
