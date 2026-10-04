# Claude Code prompt — Fine Print G1: thin Android scanner

Read `CLAUDE.md` and `bundle/schema.json` first. This slice does not consume the bundle,
but detected tracker IDs must use the same `exodus-<n>` form the schema expects.

## Goal
A minimal Kotlin / Jetpack Compose Android app that lists every user-installed app on the
device, shows each app's declared and granted permissions, and detects embedded third-party
tracker SDKs by scanning each APK's dex class names against Exodus Privacy's tracker
signatures. Runs on my physical Android phone via ADB. No network calls in this slice.

## Constraints
- Kotlin, Jetpack Compose, Material 3. Single module. Application ID `com.longlifeio.fineprint`.
  minSdk 29, targetSdk current stable.
- Scanning code lives in package `com.longlifeio.fineprint.egress`.
- Declare `QUERY_ALL_PACKAGES` in the manifest (sideloaded debug build; Play policy is a later concern).
- SDK detection: read `ApplicationInfo.sourceDir` and `splitSourceDirs`, extract `classes*.dex`,
  enumerate class names with dexlib2 (Apache 2.0), match against Exodus `code_signature` regexes.
  Ship signatures as a bundled asset at `android/app/src/main/assets/trackers.json`, produced
  once by `pipeline/fetch_trackers.py` from `https://reports.exodus-privacy.eu.org/api/trackers`.
  The app never calls that API.
- Scan off the main thread with a progress indicator; cache results in memory for the session.
- No analytics, no crash reporting, no third-party SDKs beyond dexlib2 and AndroidX.
- Each Kotlin file under ~300 lines; plain functions over frameworks (no DI library, no Room).

## Files to create
- `android/` — Gradle project (`settings.gradle.kts`, `build.gradle.kts`, version catalog, `app/`).
- `app/src/main/java/com/longlifeio/fineprint/egress/PackageScanner.kt` — enumerate apps + permissions
  (declared vs granted; flag `PROTECTION_DANGEROUS`).
- `.../egress/DexTrackerScanner.kt` — dex class-name scan against signatures.
- `.../egress/TrackerSignatures.kt` — load/parse `trackers.json`.
- `.../ui/AppListScreen.kt`, `.../ui/AppDetailScreen.kt` — list → detail with permissions and detected trackers.
- `pipeline/fetch_trackers.py` — downloads the Exodus tracker list into the assets path; stdlib + requests only.
- `android/README.md` — build/install steps (`./gradlew installDebug`, wireless debugging setup).

## Acceptance criteria
- `./gradlew installDebug` succeeds on macOS and installs to a connected phone.
- App list shows user-installed apps; system apps hidden by default with a toggle.
- Tapping Life360 (`com.life360.android.safetymapd`) shows its permissions with granted/denied
  state and detected trackers with Exodus IDs (`exodus-<n>`) and names.
- Full scan of ~100 apps completes without ANR; total scan time logged.
- Detail screen has a button that opens `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` for that package.

## Do not
- Add any network code, VpnService, or background service.
- Fork or vendor TrackerControl/NetGuard code.
- Add explanation text or bundle loading — that is G2.
- Add an app icon, onboarding, or settings screen.
