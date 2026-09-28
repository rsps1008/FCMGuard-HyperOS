# FCM Guard for HyperOS — Agent Guide

## Project purpose and non-negotiable product boundaries

FCM Guard is a small native Android application for **China-ROM HyperOS 3**
phones where Google Play services is installed and normally usable, but Xiaomi
power-management can remove it from the vendor no-restrictions list and
interrupt its long-lived FCM/MCS connection.

The app's primary job is deliberately narrow:

1. Read `Settings.System.MILLET_NO_RESTRICT_APP`.
2. Preserve its existing comma-separated package entries.
3. Add `com.google.android.gms` only if it is absent.
4. Optionally keep that state protected through a targeted observer plus a
   low-frequency in-process fallback.
5. Send best-effort Google Play services / GSF heartbeat broadcasts **only**
   after a real repair or an explicit user action.

This is not a generic battery-management app and must stay low privilege. Do
not add root, persistent ADB, Accessibility, VPN, overlay, device-admin,
traffic inspection, account access, `QUERY_ALL_PACKAGES`,
`AlarmManager`, repeating exact alarms, or a `WakeLock` merely to make a change
easier. Treat Xiaomi/HyperOS internals as vendor-specific and best-effort.
Never promise push delivery for every receiving app: protecting the shared GMS
transport does not override per-app battery, process, network, or notification
policies.

## Repository map

```text
app/
  build.gradle                         Android module, SDK/version/signing settings
  src/main/AndroidManifest.xml         permissions, visibility, components
  src/main/java/com/reed/fcmguard/
    MainActivity.java                  UI wiring, manual repair and system deep links
    SettingsGuard.java                 whitelist read/parse/repair/last-good state
    GuardService.java                  observer, debounce, fallback and notification mode
    BootReceiver.java                  restart after boot/package replacement
    FcmReconnect.java                  explicit, best-effort heartbeat broadcasts
    FcmAppScanner.java                 on-demand FCM/GCM manifest-signal scan
    AutostartStatusReader.java         read-only Xiaomi AppOps status probe
    ShizukuAutostartManager.java       optional consent/binding/write coordinator
    ShizukuAutostartService.java       short-lived Shizuku user service
  src/main/aidl/                       user-service binder contract
    HyperOsSettings.java               Xiaomi settings intents with Android fallbacks
    ThemeHelper.java / LocaleHelper.java
                                      appearance and per-app locale persistence
    StickyDashboardLayout.java /
    CollapsingStatusCard.java          dashboard geometry and visual behavior
  src/main/res/                         XML UI, drawable, dimensions, 10 locales
tools/check_layout_profiles.py          static compact-width layout guard
.github/workflows/build-apk.yml         CI build/artifact/release workflow
README.md / README.zh-CN.md             user-facing contract (keep in sync)
```

There are no unit/instrumentation test sources at present. The meaningful
automated checks are the layout script and an Android debug build. Device
validation remains required for HyperOS-specific behavior.

## Runtime architecture

### Whitelist repair (`SettingsGuard`)

- The configurable preference defaults are defined by localized resources:
  setting key `MILLET_NO_RESTRICT_APP` and required package
  `com.google.android.gms`.
- `repair()` first checks `Settings.System.canWrite()`. Without it, it reports a
  failure and must not write.
- `hasRequiredItem()` compares complete trimmed comma-separated entries; never
  replace this with substring matching.
- A healthy value is a strict no-op: no Settings write and no unnecessary
  `SharedPreferences` write.
- When the system value is empty, restore the remembered normalized last-good
  list when available. Only when no useful prior list exists, seed the existing
  conservative fallback entries (`com.tencent.mm`, `com.android.vending`) before
  appending the required item.
- Maintain ordering and de-duplicate using the existing `LinkedHashSet` flow.
  Do not overwrite the vendor key with only GMS or silently discard user/vendor
  packages.

### Continuous protection (`GuardService` and `BootReceiver`)

- `GuardService` watches only the URI for the configured settings key; it does
  not observe all settings.
- Observer changes are debounced for 400 ms. Keep the debounce when changing
  observer logic to avoid reacting to intermediate/vendor multi-write states.
- Its 30-minute fallback (`FALLBACK_INTERVAL_MS`) is an in-process handler
  callback. Do not convert it into a wake-up alarm/polling job.
- The service only reconnects FCM after `SettingsGuard.Result.changed` is true.
- Persistent-notification mode is optional and default-on. Its foreground
  channel is deliberately silent and low importance; quiet mode must remain
  available.
- `BootReceiver` starts the service only when protection was enabled by the
  user. Respect the Android O foreground-service path and existing failure
  containment.

### FCM assistant and HyperOS integrations

- `FcmAppScanner` is invoked on demand. A manifest handler for
  `com.google.firebase.MESSAGING_EVENT` or
  `com.google.android.c2dm.intent.RECEIVE` identifies a likely FCM/GCM client,
  not proof of its notification transport.
- Keep package visibility in the manifest narrow. Do not add
  `QUERY_ALL_PACKAGES`.
- `AutostartStatusReader` reflects Xiaomi AppOps 10008 and 10053 in a strictly
  read-only best-effort probe. If either reading is unavailable or ambiguous,
  return/display `UNKNOWN`; never infer `DISABLED`.
- The optional Shizuku action is the sole exception to the read-only AppOps
  boundary. It must remain explicit-user-action only: require a live binder and
  Shizuku consent, execute both operations through the validated user service,
  and label the result unverified unless the subsequent read reports the
  expected state. It must never enable USB debugging or start Shizuku itself.
- `HyperOsSettings` and diagnostics launches must retain safe Android fallbacks
  because component names vary by ROM.
- `FcmReconnect` broadcasts are best effort, not a delivery guarantee. Preserve
  the condition that limits them to real repair/manual use.

### UI, localization, and layout invariants

- The UI is classic XML Views/Java, not Compose. Follow the current resource
  qualifiers rather than hard-coding pixel sizes in Java.
- Supported locales are English plus `zh-rCN`, `zh-rTW`, French, Japanese,
  Korean, Spanish, Portuguese, German, and Russian. User-visible string changes
  should update `values/strings.xml` and every matching localized
  `strings.xml`/`feature_strings.xml` file, or explicitly state which
  translations are pending.
- Keep system/light/dark behavior through `ThemeHelper`; retain system as the
  default. Apply locale/theme through `attachBaseContext` as `MainActivity`
  currently does.
- `StickyDashboardLayout` and `CollapsingStatusCard` implement coupled visual
  geometry. Do not replace/remove their required IDs or custom
  `CollapsingStatusCard` behavior without updating the layout checker.
- `tools/check_layout_profiles.py` protects 320, 360, 393, 411, 430, and 480dp
  configurations. Any resource/layout change must preserve its assertions or
  intentionally update the checker and explain the new supported geometry.

## Android and build conventions

- Use **JDK 21** on this Windows development machine for Android/Gradle work:
  `C:\Program Files\Java\jdk-21.0.11\bin`.
- Application source is Java and is compiled with Java 17 language/API target
  (`app/build.gradle`). Do not introduce Kotlin or a Java language level that
  contradicts the module configuration without an intentional build migration.
- The app compiles with SDK 35 and has `minSdk`/`targetSdk` 23. API 23 is needed
  by the optional official Shizuku provider. Do not raise `targetSdk` casually:
  this legacy target is still part of the user-grantable `WRITE_SETTINGS`
  compatibility strategy for Xiaomi's private setting.
- CI uses AGP 8.6.1, JDK 17, Android platform/build-tools 35, runs the Python
  layout checker, and builds `:app:assembleDebug`. Main publishes/replaces the
  versioned and stable APK GitHub Release; `feature/**` branches only validate
  and upload an Actions artifact.
- CI currently selects Gradle 9.6.1 explicitly. Before changing wrapper or Gradle
  versions, make the local wrapper, CI setup, Android Gradle Plugin, and Java
  compatibility deliberate and consistent. Do not commit generated wrapper or
  signing files merely because a local build created them.
- `ci-signing/fcmguard-debug.keystore` is generated/restored only in CI and is
  ignored. Never add keystores, tokens, local SDK paths, or credentials to Git.

### Commands

Run from repository root in PowerShell. Prefer the checked-in wrapper when it
is tracked and compatible; otherwise use the CI-compatible Gradle invocation.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
python tools/check_layout_profiles.py
.\gradlew.bat :app:assembleDebug --stacktrace
```

If the wrapper files are absent from the committed revision or are known local
untracked setup artifacts, do not silently add them. State that constraint and
use the project/CI-approved Gradle tooling available in the environment.

For changes affecting vendor behavior, build success alone is insufficient.
Validate on the requested HyperOS device/ROM when available: settings-write
permission, preservation of a nontrivial existing list, no-op repeat repair,
observer repair, reboot/package-replace restart, foreground/quiet notification
behavior, system deep-link fallback, `UNKNOWN` AppOps fallback, Shizuku
unavailable/denied/granted states, both AppOps writes, and write read-back.

## Change workflow

1. Start with `git status --short` and preserve unrelated dirty or untracked
   work. This checkout may contain local Gradle wrapper artifacts; do not absorb
   them into an unrelated change.
2. Read the entire relevant call chain before editing. For repair changes trace
   `MainActivity` / `GuardService` → `SettingsGuard` →
   `Settings.System`; for scanner changes trace manifest `<queries>` → scanner
   → `AutostartStatusReader` → UI rendering.
3. Keep changes narrow and fail safely. Existing broad `Throwable` guards around
   vendor/reflection paths intentionally prevent a ROM incompatibility from
   crashing the app; do not remove them without an equivalent user-visible,
   tested failure path.
4. Update both READMEs whenever functionality, permission, SDK policy,
   supported language, supported device width, build/release behavior, or
   user-visible limitations change. Keep English and Simplified Chinese claims
   semantically aligned.
5. Run `python tools/check_layout_profiles.py` for every UI/resource/layout
   change, and `:app:assembleDebug` for any source/resource/manifest/Gradle
   change. Report the exact command and outcome.
6. Separately state unverified scope: a successful static check/APK build does
   not prove a HyperOS vendor setting write, AppOps reflection result, reboot
   behavior, FCM delivery, CI release publication, or phone installation.

## Git and release hygiene

- Use `rg` / `rg --files` for searches.
- Keep commits focused. Do not amend/revert/reset unrelated user work.
- Treat version changes in `app/build.gradle` as release-affecting: `versionName`
  is used by the workflow to derive the GitHub tag and APK names.
- Do not push, create a release, or alter GitHub resources unless the user
  explicitly asks. Before such a request, check the branch, the exact version,
  and whether a same-tag release will be updated rather than created.
- Never claim that an Actions artifact or an overall green workflow establishes
  physical-device FCM reliability; those are distinct evidence levels.

## Review checklist

Before handing off a change, verify:

- Existing whitelist entries cannot be lost or duplicated.
- Permission denial, missing vendor key, rejected write, reflection failure, and
  missing Xiaomi activities degrade clearly and safely.
- Normal healthy monitoring remains idle: no write/reconnect on a no-op repair.
- `UNKNOWN` remains distinct from disabled/partial Autostart status.
- No broad-package-discovery capability was added, and optional Shizuku use is
  limited to user-requested per-app Autostart changes with read-back reporting.
- Relevant static layout and APK-build checks passed, plus device evidence if
  the requested behavior needs it.
- README claims and all affected translations match the implementation.
