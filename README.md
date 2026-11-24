# tal-copy

iOS ports of Tal Azulay's Android apps, built on GitHub Actions.

| App | Android original | iOS status |
| --- | --- | --- |
| ShiftHours / WorkHours | Kotlin + Jetpack Compose | Ported (`WorkHours-iOS/`), builds to an unsigned `.ipa` |
| PocketFlow | Java + Android Views + SQLite | Ported (`PocketFlow-iOS/`), builds to an unsigned `.ipa` |

## Getting the app onto an iPhone

1. Open the **Actions** tab, pick the latest green **iOS build** run.
2. Download the `WorkHours-unsigned-ipa` and/or `PocketFlow-unsigned-ipa` artifact and unzip it.
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
  `platform/src/commonMain/kotlin` (`java.time`,
  `BigDecimal`, `org.json`, zip, `String.format`, `Context`,
  `SharedPreferences`, date/time/file pickers, ...). These are plain Kotlin and
  were checked against the real JDK with randomized tests.
- **A short list of explicit patches** (`tools/port_workhours_patches.py`)
  handles the places that are genuinely Android-specific. Each patch must match
  exactly once, so an upstream change that breaks one fails the port loudly.
- **Strings** from all 10 `strings.xml` files become Kotlin tables, so the
  in-app language switch works without restarting.
- **Hand-written iOS pieces** live in `.../com/tal/workhours/ios/` (app root,
  localization, backup screen) and `platform/src/iosMain` (Foundation/UIKit
  glue).
- The Android **unit tests** are ported the same way and run on every push.

### Updating to a new Android version

```
# replace upstream/WorkHours-Android/app/src with the new sources, then:
python3 tools/port_workhours.py
git add -A && git commit -m "Update WorkHours to <version>" && git push
```

If a patch no longer applies, the script names it; adjust the patch and rerun.

### Differences from the Android app

- **Home-server sync** works, but only while the app is open (Android also
  syncs every 15 minutes in the background).
- **Not ported yet:** home-screen widgets and Google Drive backup. Manual
  backup to a file works.

## How the PocketFlow port works

PocketFlow is Java with classic Android views, neither of which exists on iOS.

1. `tools/j2k` converts the Java to Kotlin. It runs the real Java compiler for
   parsing and type information and prints Kotlin that keeps Java's behaviour
   literally (nullable everything, explicit numeric conversions).
   `tools/port_pocketflow.sh` regenerates
   `PocketFlow-iOS/shared/src/commonMain/kotlin/com/tal/pocketflow` from
   `upstream/PocketFlow-Android`; `tools/pocketflow/patch_sources.py` lists the
   few edits made to the Java first.
2. The converted code runs on a small model of `android.view`, `android.widget`
   and `android.app` in `platform/src/commonMain/kotlin/android` (views are
   plain objects that store what the app sets).
3. `platform/.../compat/android/compose/ViewRenderer.kt` draws that view tree
   with Compose, and `ActivityHost.kt` runs the Activity (lifecycle, dialogs,
   date pickers, toasts). SQLite is the engine bundled with `androidx.sqlite`.

The JVM test `screenshots/ScreenshotTest` drives the app through its screens
and CI prints the pictures into the job log.

### Differences from the Android app

- **App lock** uses Face ID / Touch ID / the device passcode.
- **Not ported:** Google Drive backup (manual backup to a file works), the
  daily reminder notification, home-screen widgets.

## Additions that are not in the Android apps

- **Salary to PocketFlow.** The ShiftHours snapshot screen has a button that
  sends the period's salary to PocketFlow as an income entry. The amount can be
  changed first, and PocketFlow asks for confirmation. While the period is open
  it is sent as an *expected* income; sending again at month end updates it.
- **Cost in work hours.** Once ShiftHours has sent a salary, PocketFlow also
  shows expenses as hours of work.
- **Apple Pay capture.** PocketFlow adds an action to the Shortcuts app
  ("Add movement to PocketFlow"). iOS does not let apps create automations, so
  it is switched on once per card: Shortcuts -> Automation -> New -> Wallet
  (Transaction) -> pick the card -> Run Immediately -> add the PocketFlow action
  -> set Amount, Merchant and Card from "Shortcut Input". After a payment
  PocketFlow opens with the movement filled in, ready to confirm.
- Both use `pocketflow://` links, see `PocketFlow-iOS/.../ios/Links.kt`.

## Layout

```
.github/workflows/ios.yml   Linux check (tests + iOS compile), then macOS .ipa build
platform/                   Kotlin look-alikes of the JDK and Android APIs, shared by both ports
WorkHours-iOS/shared/       All app code (Kotlin Multiplatform: iOS + a JVM target for the tests)
WorkHours-iOS/composeApp/   Thin module that links the iOS framework
WorkHours-iOS/iosApp/       XcodeGen spec, Swift entry point, app icon
PocketFlow-iOS/             Same layout; shared/ holds the code converted from Java
upstream/                   Unmodified Android sources the ports are generated from
tools/                      Port scripts, patch lists, Java-to-Kotlin converter (j2k)
```
