# Roadmap

What comes next, in order, and what's logged for later. The gates are in `CLAUDE.md`. The phone test is done
(main at 4be58f0); the Tailscale path, for a debug build on a phone, is still deferred by choice.

## Next, in order

1. Follow-ups to the tracker batches: Mintegral's and InMobi's privacy policies, saved by hand
   (`manual`). With the first `manual` source, `build.py` passes the flag through to the bundle (an
   added field), so a source's Open button takes a page saved by hand to its top, not to the quoted
   words; today it drops the flag.
2. G6: a walk of Life360's three paths in the app (`verified_on`); the rest of the first batch; and Guide mode.
3. Signed release — after guided paths (G6). The release carries the bundle's three files, which the release
   build downloads from `releases/latest/download/`. Its address and a private channel for testers replace the
   placeholders in `docs/privacy.md` and `docs/TESTING.md`. Terms, and a legal review of About's note on sources and
   legal advice, are still to come.
4. AGP 9.5.0, once it's stable: move to it and take out the lint line in `android/gradle.properties`. Until then the
   release build runs lint from AGP 9.5.0-rc01, because 9.4.1's crashes on `build.gradle.kts`.

## Logged for G6 (not built yet)

- Guided paths, still to come (slice 1 built the schema and the cards): fallback steps for screens
  that have changed ("If you don't see Privacy, tap Search in Settings and type 'privacy choices'"),
  and the rest of the first batch, the ten most useful controls across Google's ad settings, Meta's ad
  preferences and off-Facebook activity, and TikTok's personalization (Life360's three are in). Help-page
  changes reach a path through the watcher, which re-reads its quotes as it does every source's.
- Guide mode. The same steps as a small, movable floating card (Next / Back / Done) over the real
  app while the user moves through it themselves. Draw-only: it needs only the overlay permission,
  reads nothing, captures nothing, has no input fields, and is opt-in per walkthrough, never
  persistent. Permission prompt: "This lets FinePrint show step cards over other apps. It cannot
  see what's on your screen." The deep link makes the first jump; the user moves on by tapping
  Next; Done, or going back to FinePrint, closes the card and offers to tick the control with
  today's date. How to read this explains why FinePrint's use of an overlay differs from the
  device-access warning about overlays. For Play: declare the overlay permission's purpose. Never
  in scope: a sandbox copy of another app with a test account, or any Accessibility-based
  highlighting.

## Queued: laws to check (not started)

- Ireland's domestic powers over companies based there: its e-Evidence implementing act and the
  warrant or production powers Gardaí use.
- Norway: Criminal Procedure Act § 210 b (orders for evidence that will come into someone's
  possession) and § 215 a (data preservation orders).
- Germany: StPO § 100j (subscriber data) and TDDDG §§ 22–24.
- Singapore: Criminal Procedure Code ss. 39–40 (access to computers and decryption information).
- India: Digital Personal Data Protection Act s. 36.
- US national law beyond the CLOUD Act and FISA: the Stored Communications Act, 18 U.S.C. § 2703
  (the everyday warrant, order and subpoena route for app data); national security letters,
  § 2709; FISA Title I, 50 U.S.C. § 1805(c)(2)(B).

## Logged for later

- Open, for the owner to decide: a fourth government line type for voluntary hand-over on
  request, applied evenly to every country (US 18 U.S.C. § 2702; GDPR arts. 6 and 48; Canada's
  PIPEDA s. 7(3)(c.1); the rest), or whether that belongs at the company-policy layer instead.
- Open, for the owner to decide: court findings about a law as a line of their own under
  Jurisdictions, applied evenly to every law or not at all. Podchasov v. Russia (ECtHR, 2024: the
  decryption duty breached art. 8; Russia left the Convention in 2022 and the ruling doesn't change
  its law) for art. 10.1; Schrems II (CJEU, 2020) for FISA 702; whatever exists for the others.
- A "What matters to me" setting that lets the user choose which jurisdictions and ways of getting
  data to highlight (from the government-access plan; the rest shipped in v1.3).
- v2: data exports the user supplies (Facebook's "Download your information", Google Takeout),
  read on the phone to check the in-app settings they report, the same pattern as the iOS App
  Privacy Report import.
- The watcher's slice 2 (archived copies for parked hosts, more regulator and court sources,
  app-version signals): `pipeline/README.md`, "Watcher: slice 2".
- Sources sheet: an archived-copy link beside each source, so a cited page that goes dead for users
  still opens.
