package com.longlifeio.fineprint.ui

private const val SOURCE =
    """{ "url": "https://example.org/policy", "type": "privacy_policy", "title": "Policy", "as_of": "2026-09-12", "status": "self_disclosed", "quote": "partners for their own use" }"""

/** A record with an improvement and a later worsening, for the changes UI: no reviewed record has changes yet. */
const val CHANGES_FIXTURE = """
{
 "schema_version": 1, "bundle_version": "2026.10.04", "generated_at": "2026-10-04T12:00:00-03:00",
 "apps": [{
  "package_id": "org.example.changes", "display_name": "Changes Example", "summary": "Synthetic record with two changes.",
  "trackers": [], "consequences": [], "coverage": "curated", "last_reviewed": "2026-10-01",
  "changes": [
   { "date": "2025-11-03", "text": "Added a setting to turn off partner sharing.", "direction": "improved",
     "diff": ["control added: ctl-partners"], "sources": [$SOURCE] },
   { "date": "2026-09-12", "text": "Now lets partners use your precise location for their own purposes.", "direction": "worsened",
     "diff": ["flow added: precise_location to Partners (goes_elsewhere)"], "tier_before": "caution", "tier_after": "flagged", "sources": [$SOURCE] }
  ]
 }],
 "trackers": [], "companies": [], "permissions": [], "device_reach": []
}
"""
