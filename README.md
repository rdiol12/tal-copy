# tal-copy

iOS ports of Tal Azulay's Android apps, built on GitHub Actions.

| App | Android original | iOS status |
| --- | --- | --- |
| ShiftHours / WorkHours | Kotlin + Jetpack Compose | Ported (`WorkHours-iOS/`), builds to an unsigned `.ipa` |
| PocketFlow | Java + Android Views + SQLite | Not started - needs a rewrite |

## Getting the app onto an iPhone

1. Open the **Actions** tab, pick the latest green **iOS build** run.
2. Download the `WorkHours-unsigned-ipa` artifact and unzip it.
3. Sign and install the `.ipa` yourself. It is built **without** a signature, so
   it will not install as-is. Use whatever you normally sign with (Sideloadly,
   AltStore, or `codesign` with your own certificate and provisioning profile).
   With a free Apple ID the signature expires after 7 days.

To build on demand: Actions -> iOS build -> Run workflow. `Debug` builds faster;
`Release` is what you want on a phone.

## How the WorkHours port works

The Android app is not rewritten. `tools/port_workhours.py` regenerates the
shared code from the untouched Android sources in `upstream/WorkHours-Android`:

- **Imports are redirected** from JDK/Android classes to look-alikes in
  `WorkHours-iOS/shared/src/commonMain/kotlin/compat` (`java.time`,
  `BigDecimal`, `org.json`, zip, `String.format`, `Context`,
  `SharedPreferences`, date/time/file pickers, ...). These are plain Kotlin and
  were checked against the real JDK with randomized tests.
- **A short list of explicit patches** (`tools/port_workhours_patches.py`)
  handles the places that are genuinely Android-specific. Each patch must match
  exactly once, so an upstream change that breaks one fails the port loudly.
- **Strings** from all 10 `strings.xml` files become Kotlin tables, so the
  in-app language switch works without restarting.
- **Hand-written iOS pieces** live in `.../com/tal/workhours/ios/` (app root,
  localization, backup screen) and `shared/src/iosMain` (Foundation/UIKit
  glue).
- The Android **unit tests** are ported the same way and run on every push.

### Updating to a new Android version

```
# replace upstream/WorkHours-Android/app/src with the new sources, then:
python3 tools/port_workhours.py
git add -A && git commit -m "Update WorkHours to <version>" && git push
```

If a patch no longer applies, the script names it; adjust the patch and rerun.

### Not ported yet

Android-only features with no iOS implementation: home-screen widgets, Google
Drive backup (manual backup to a file works), and home-server sync.

## Layout

```
.github/workflows/ios.yml   Linux check (tests + iOS compile), then macOS .ipa build
WorkHours-iOS/shared/       All app code (Kotlin Multiplatform: iOS + a JVM target for the tests)
WorkHours-iOS/composeApp/   Thin module that links the iOS framework
WorkHours-iOS/iosApp/       XcodeGen spec, Swift entry point, app icon
upstream/                   Unmodified Android sources the port is generated from
tools/                      Port script and its patch list
```
