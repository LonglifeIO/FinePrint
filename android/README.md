# Fine Print — Android app

Lists installed apps, shows each app's declared permissions and whether they are granted, and
finds embedded tracker SDKs by scanning the app's dex code against tracker signatures (Exodus
Privacy's list plus Fine Print's own). Since G2 it also downloads the knowledge bundle
(`bundle.json` + `trackers.json`, whole files, once per launch) and explains each app by data:
what it collects, who gets it, and whether that stays in the app, is used for more, or goes
elsewhere. Every line carries a status badge and its tappable sources, primary first; a legal
claim also says where the case stands (a dismissal, an appeal), with its own sources. Trackers and
permissions sit under a collapsed "Evidence" section. Apps without a reviewed record get an "Auto" view
inferred from tracker categories.

Kotlin, Jetpack Compose, Material 3, single module, minSdk 29, targetSdk 37.
Scanning code lives in `com.longlifeio.fineprint.egress`.

## Toolchain (headless Mac)

Set up on the Mac mini, command-line only:

- **JDK 21:** `brew install openjdk@21` (AGP needs 17 or newer).
- **Android SDK command-line tools 22.0** in `~/Library/Android/sdk/cmdline-tools/latest`, plus
  `platform-tools` (adb), `emulator`, and `system-images;android-37.0;google_apis;arm64-v8a`. AGP
  downloads the platform and build-tools it needs on the first build.
  - Not 23.0: its `sdkmanager` fetches an unpinned "Android CLI" binary, which includes usage
    metrics, on first use.
  - Avoid running `cmdline-tools/latest/bin/android` for the same reason.
- **Shell environment:** `~/.zshrc` exports `JAVA_HOME`, `ANDROID_HOME` and adds adb to `PATH`
  (the block marked "Fine Print Android toolchain").
- **`android/local.properties`** holds `sdk.dir=…`. It is gitignored; Gradle also honours `ANDROID_HOME`.

Versions: AGP 9.4.1 (built-in Kotlin, so no `kotlin-android` plugin), Kotlin 2.4.20 (Compose
compiler plugin), Gradle 9.6.1 via the wrapper, Compose BOM 2026.09.00, dexlib2 3.0.10 (its
unused Guava dependency is excluded).

## Build and test

```sh
cd android
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # JVM tests, no device needed
```

The unit tests check:

- the matcher against `java.util.regex` running the real signatures;
- name extraction against Exodus's own regex;
- scans of synthetic APKs built with dexlib2. These include archives that `ZipFile` rejects, a
  hostile class-name length, and a tracker type that is referenced but not defined (which must not
  count).

## Try it on the emulator (no phone needed)

```sh
emulator -avd fineprint37 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect &
adb wait-for-device && ./gradlew installDebug
adb install-multiple base.apk split_config.*.apk     # an app delivered as splits, e.g. from an .xapk
adb shell svc power stayon true                      # keep the screen awake for honest timings
adb shell am start -n com.longlifeio.fineprint/.MainActivity
adb logcat -s FinePrint                              # "Tracker scan of N apps took X ms with 3 workers: ..."
mkdir -p captures && adb exec-out screencap -p > captures/screen.png
```

- **The AVD:** `fineprint37` is a Pixel 8 profile on Android 17 (API 37), arm64. It can't install
  apps that ship only 32-bit native libraries.
- **Screenshots** go to `android/captures/`, which is gitignored. They show your app list and
  permissions, so keep them out of the repo.
- **Don't launch the APKs you install.** Android keeps a freshly installed app stopped until it is
  first opened, so its code never runs.

## Serve the bundle to the emulator (G2)

```sh
python3 pipeline/build.py                        # from the repo root; writes bundle/bundle.json
python3 -m http.server 8787 --bind 127.0.0.1 --directory bundle
adb shell pm grant com.longlifeio.fineprint android.permission.ACCESS_LOCAL_NETWORK
adb logcat -s FinePrint | grep NET               # exactly two GETs per launch
```

- **The URL** comes from `fineprint.bundleUrl` in `local.properties` (default
  `http://10.0.2.2:8080/`; 10.0.2.2 is the Mac as seen from the emulator). Pick a port nothing
  else on the Mac is using: on the dev Mac, 8080 and 8090 are taken by other services. A
  Tailscale host set there is allowed for cleartext in debug builds only, via a generated
  network security config, so it never lands in the repo.
- **Android 17 local network protection** blocks a targetSdk 37 app from reaching LAN or host
  addresses until it holds `ACCESS_LOCAL_NETWORK`. Only the debug manifest requests it, and the
  `adb shell pm grant` above grants it. Reinstalling keeps the grant; uninstalling drops it.
- **Offline:** both files are cached in the app's private storage after a successful download.
  With the server down, the app logs a failed GET and keeps using the cached copy.
- **About** (top bar) shows the bundle version and age, the source URL, "Update now", and the
  licences.

## Or a spare phone (wireless debugging, Android 11+)

Use a test device rather than a personal one: wireless debugging gives the Mac shell-level access
to the phone until you turn it off. The phone and the Mac must be on the same network.

1. On the phone, open Settings → About phone and tap Build number seven times.
2. Settings → System → Developer options → turn on **Wireless debugging**.
3. Tap **Pair device with pairing code**. It shows an `IP:port` and a six-digit code.
4. On the Mac: `adb pair <IP>:<pairing port> <code>`.
5. On the Mac: `adb connect <IP>:<port>`. This port is the one on the main Wireless debugging
   screen, not the pairing one.
6. Install and run with `./gradlew installDebug` and the `am start` / `logcat` commands above.

For the G1 timing, unlock the phone and keep the screen on: turn on Developer options →
**Stay awake**, then charge. A run during which the phone slept is throttled and shows up as an
outlier.

System apps are hidden by default; the switch in the top bar shows them (and scans them).
The detail screen's **Open app settings** button opens Android's app-info page
(`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`) for that package.

## Tracker signatures

`app/src/main/assets/trackers.json` is built by `pipeline/fetch_trackers.py` from two sources:

- **The Exodus list** (`/api/trackers`), with ids `exodus-<n>`.
- **Fine Print's own entries**, with ids `fp-<slug>`, from `pipeline/fp_trackers.json`.

```sh
python3 pipeline/fetch_trackers.py                        # from the repo root; fetches the Exodus list
python3 pipeline/fetch_trackers.py --from-file saved.json # build from a saved API response
cd android && ./gradlew testDebugUnitTest                 # then commit the asset
```

The script refuses to write the asset in two cases:

- **The usable signature count drops by more than 10%,** for example because the API changed shape.
  Use `--force` if the drop is real.
- **Two entries share an id.**

The app never calls the Exodus API. Exodus allows 3 requests per minute on that endpoint and bans
IPs that exceed it, so fetch rarely.

`pipeline/fp_trackers.json` holds one entry per tracker, in Exodus's signature format, and every
entry must cite evidence a reviewer can re-check:

```json
{"trackers": [{"id": "fp-arity", "name": "Arity",
  "code_signature": "com.arity.coreengine.|com.arity.coreEngine.",
  "categories": ["Location"], "evidence": ["…APK SHA-256, classes found, sources…"]}]}
```

The first entry is Arity (Allstate), which Exodus does not list. Evidence: Life360 26.37.0's base
APK is byte-identical to the Google Play file Exodus analysed, and it defines 642 classes under
`com/arity/coreengine` and `com/arity/sensor`.

## How detection works

1. **Listing apps.** The app enumerates installed packages. It can see them all because the manifest
   declares `QUERY_ALL_PACKAGES`.
2. **Permissions.** They come from `requestedPermissions` and `requestedPermissionsFlags`.
   "Dangerous" means `PermissionInfo.protection == PROTECTION_DANGEROUS`. The grant bit alone
   misleads in three cases, so the app reads the raw app-op mode for each:
   - **Special-access permissions:** `PROTECTION_FLAG_APPOP` permissions such as `SYSTEM_ALERT_WINDOW`,
     `MANAGE_EXTERNAL_STORAGE` and `SCHEDULE_EXACT_ALARM`.
   - **Runtime permissions with a hidden denial:** the bit says granted, but the user denied it.
     This happens for apps targeting below 23, and for permissions granted implicitly by a platform
     split, such as Android 17's `ACCESS_LOCAL_NETWORK`.
   - **Background location for apps targeting below 23:** it has no app-op of its own, so it
     follows the foreground location op.

   The mode is read raw, because the evaluated mode turns "allowed while in use" into "ignored"
   whenever the scanned app is not on screen.
3. **Finding dex code.** For each app it opens `base.apk` and every split APK, and reads each zip
   entry whose path matches Exodus's `classes.*\.dex` rule.
   - If `java.util.zip` rejects an APK because of one odd entry, a small central-directory reader
     takes over. Odd means an encrypted flag, an unknown compression method, or a name or comment
     that isn't valid UTF-8 (Android 10–14 decode those strictly). The reader extracts only the dex
     entries, as leniently as Android itself. Adware uses those tricks to break analysis tools.
   - Each dex is parsed with dexlib2, including DEX v41 containers.
   - Hostile input is bounded. A malformed class-name length is checked before dexlib2 allocates
     it, and each distinct class name is matched once per entry. One app's scan stops after 10
     million class definitions; Google Play services has about 210,000.
4. **Naming classes.** Each class the APK defines becomes the name Exodus would use: exodus-core
   runs `re.findall(r'[A-Z]+((?:\w+\/)+\w+)')`, so `Lcom/foo/Bar$1;` becomes `com/foo/Bar`.
5. **Matching.** Signatures are applied with Exodus's semantics: unanchored `re.search`, and
   signatures of three characters or fewer are ignored. A trie-indexed matcher does this fast enough
   for a phone.
6. **Scheduling.**
   - Scans share a fair semaphore sized to the heap: up to three at once, each holding one dex
     entry, with entries capped at 64 MB.
   - Opening an app's detail screen scans it next.
   - A scan that runs out of memory keeps whatever it already found and is retried once on its own.
   - Results stay in memory for the life of the process.

These differences from Exodus are deliberate:

- **Only classes defined in the APK count.** That is what evidence tier `contains_code` claims.
  exodus-core also matches types an app merely references (interfaces, field and method types,
  locals). For Life360 26.37.0, Exodus reports 27 trackers; Fine Print finds 12 of those plus
  Arity. The other 15 are ad SDKs referenced only by an ad-quality library inside Life360, and
  their code is not in the APK. The detail screen says so in one line ("Exodus may list up to N;
  M are adapter references with no code in this app"), counted on-device from every type the code
  mentions, which can run slightly above Exodus's own figure (29 vs 27 for Life360).
- **Split APKs are scanned too.** Exodus downloads only the base APK.
- **APKs nested inside an APK are not opened.** Exodus recurses into them.

## Results so far

| Check | Result |
|---|---|
| exodus-core's 7 fixture APKs, Exodus's logic re-run with the SDK's `dexdump` | Identical, except francetv's 2 referenced-only trackers |
| Android 17 emulator (API 37, arm64): Life360 26.37.0 plus 5 fixtures | 7 user apps in 1.6 s; 260 system apps in 2.3 s; no ANR |
| Life360 detail screen | 13 trackers (12 Exodus ids plus `fp-arity`), 48 permissions |
| Grant states | Flip correctly after `pm grant` and app-op changes |
| Special cases | A legacy app's Camera allowed while in use; location "while in use" and "don't allow"; a denied implicit `ACCESS_LOCAL_NETWORK` |
| Settings button | Opens Android's app info for the app |

The emulator runs on the Mac's M4, so expect a phone to be several times slower.

## Privacy

- **One network use.** The app holds `INTERNET` only to download `bundle.json` and `trackers.json`
  whole, with no query string, cookies or app data in the request. The server learns that someone
  opened Fine Print, never which apps they have. Release builds fetch over HTTPS only.
- **Proof in debug builds.** The bundle client tags its sockets and logs each request as
  `NET GET <url> -> <code>`. StrictMode flags any untagged socket, so a stray SDK or library
  connection would show up in logcat.
- **Nothing about you is stored.** Scan results are held in memory only and never written or
  sent anywhere. The only thing written to disk is the downloaded bundle.
- **Logs.** Per-app log lines, which contain package names, are written only by debuggable builds.
  Release builds log totals only.

## Tracker data licence

`trackers.json` is derived from the εxodus tracker database, which is published under the Open
Database License (ODbL) 1.0, with contents under DbCL 1.0. The file stays under the ODbL, and that
covers Fine Print's own entries merged into it. It carries the notice in its `attribution` field,
and the app shows that notice under every tracker list.

## Scanning APKs on the Mac

To check results against an Exodus report, or to find the package names of an SDK neither list
covers, run the scanner harness on APK files:

```sh
FINEPRINT_APKS=/path/to/app-dir,/path/other.apk FINEPRINT_GREP=arity \
  ./gradlew :app:testDebugUnitTest --tests '*ApkScanHarness*' --rerun
cat app/build/reports/apk-scan.txt
```

An entry is an APK, or a directory holding one app's `base.apk` and splits. The report lists:

- the trackers the app would show, each with the class that matched;
- trackers Exodus would add through referenced-only types;
- packages matching `FINEPRINT_GREP`.

APKs are someone else's copyrighted code: keep them outside the repo (`*.apk` is gitignored). If
an APK comes from a mirror, check its SHA-256 or signing certificate against a trusted source
first. The Exodus report for that version is one such source.
