# Claude Code prompt — Argus G1: thin Android scanner

## Goal
A minimal Kotlin/Jetpack Compose Android app that lists every user-installed app on the device, shows each app's declared and granted permissions, and detects embedded third-party tracker SDKs by scanning each APK's dex class names against Exodus Privacy's tracker signatures. Running on my physical Android phone via ADB. No network calls in this slice.

## Constraints
- Kotlin, Jetpack Compose, Material 3. Single module. minSdk 29, targetSdk current stable.
- Declare `QUERY_ALL_PACKAGES` in the manifest (sideloaded debug build; Play policy is a later concern).
- SDK detection: read `ApplicationInfo.sourceDir` (and split APKs), extract `classes*.dex`, enumerate class names with dexlib2 (Apache 2.0), match against Exodus signatures. Ship the signatures as a bundled JSON asset at `android/app/src/main/assets/trackers.json`, fetched once by a script from `https://reports.exodus-privacy.eu.org/api/trackers` — do not call the API from the app.
- Do the scan off the main thread with a progress indicator; cache results in memory for the session.
- No analytics, no crash reporting, no third-party SDKs beyond dexlib2 and AndroidX.
- Keep each file under ~300 lines; prefer plain functions over frameworks (no DI library, no Room).

## Files to create
- `android/` — Gradle project (`settings.gradle.kts`, `build.gradle.kts`, `app/`).
- `app/src/main/java/.../scan/PackageScanner.kt` — enumerate apps + permissions (declared vs granted, flag dangerous ones).
- `app/src/main/java/.../scan/DexTrackerScanner.kt` — dex class-name scan against signatures.
- `app/src/main/java/.../scan/TrackerSignatures.kt` — load/parse `trackers.json`.
- `app/src/main/java/.../ui/AppListScreen.kt`, `ui/AppDetailScreen.kt` — list → detail with permissions and detected trackers.
- `scripts/fetch_trackers.py` — downloads the Exodus tracker list into the assets path.
- `android/README.md` — build/install steps (`./gradlew installDebug`, wireless debugging).

## Acceptance criteria
- `./gradlew installDebug` succeeds on macOS and installs to a connected phone.
- App list shows user-installed apps (filter out system apps by default, toggle to show them).
- Tapping Life360 (`com.life360.android.safetymapd`) shows its permissions with granted/denied state and a list of detected trackers with Exodus tracker IDs and names.
- Full scan of ~100 apps completes without ANR; log total scan time.
- Detail screen has a button that opens `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` for that package.

## Do not
- Do not add any network code, VPN service, or background service.
- Do not fork or vendor TrackerControl/NetGuard code.
- Do not add explanation text or a knowledge bundle — that is the next slice.
- Do not add an app icon, onboarding, or settings screen.