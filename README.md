# CMF Social Remote

Native Android (Kotlin, Jetpack Compose) companion app that lets a **CMF Watch Pro** scroll and like in Instagram, YouTube Shorts, Facebook Reels and TikTok.

> **Read this first.** The original CMF Watch Pro runs a closed operating system. A phone app cannot install a watch app, add custom gestures to its screen, or change its firmware. So this app uses the one thing the watch can already do: send **Music-screen commands** to the phone. See [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) for the full investigation and feature matrix.

| On the watch → Music screen | Does |
|---|---|
| ▶▶ next | Next Reel / Short / post |
| ◀◀ previous | Previous |
| ⏯ twice | Like; again to unlike (where supported) |
| ⏯ once | Nothing |

The same three gestures exist on the in-app circular dial, so everything can be tested without the watch.

## Status

- Source is complete and covers every item in the brief: BLE discovery and connection with reconnect, gesture engine, four app profiles, accessibility-based scroll and like with result verification, app launching, permission onboarding, Home / Watch / Apps / Settings screens.
- **It has not been compiled or run.** The authoring environment had no Android SDK or access to Maven / Google repositories. A syntax-level pass with the Kotlin compiler found no syntax errors, but expect to fix a few compile errors on the first build, and the behaviour has not been tested on a phone or a watch.
- Items that can only be confirmed with the real hardware are listed in the compatibility doc under *Known risks*.

## Build

1. Open the `CmfSocialRemote` folder in Android Studio (Ladybug or newer, JDK 17). Let it sync; it will create the Gradle wrapper.
2. Run on an Android 8.0+ phone (minSdk 26).
3. Or from a shell with Gradle 8.9+: `gradle :app:assembleDebug`.

Versions: AGP 8.7.2, Kotlin 2.0.21, Compose BOM 2024.10.01, compileSdk / targetSdk 35.

## First run

1. Onboarding explains the limitation, then asks for Bluetooth and notification permission.
2. Enable **CMF Social Remote** in Android's accessibility settings. If Android says the setting is restricted, open **App info → ⋮ → Allow restricted settings** and retry.
3. *Watch* tab → pick your paired CMF Watch Pro.
4. *Apps* tab → pick the app. *Remote* tab → **Start Remote**.
5. On the watch open **Music**. Keep the social app full screen, on a Reel / Short.
6. If the Music keys control another player, use *Settings → Re-claim watch music controls*.

## Architecture

```
app/src/main/java/dev/nikhil/cmfsocialremote/
├── ble/        BleScanner, WatchLinkMonitor (ACL events), WatchConnection (GATT + backoff), CmfCompatibility
├── gesture/    GestureEngine (single / double tap, cooldown), MediaButtonSource (watch Music keys), RemoteProtocol (BLE command channel)
├── control/    ControlAccessibilityService, AppProfiles + AppLauncher, LikeNodeFinder, Haptics, ServiceBridge
├── service/    RemoteService (foreground service owning the media session)
├── data/       SettingsStore, Permissions
├── model/      Shared types incl. ActionResult
├── ui/         Compose screens, GestureDial, components, theme
└── RemoteHub   Wires sources → engine → accessibility actions; exposes state to the UI
```

Flow: command source (watch media keys, BLE channel, or test dial) → `GestureEngine` → `RemoteHub` maps to an action using the active app's config → `ControlAccessibilityService.perform` → `ActionResult` → UI and haptics.

## Reliability and privacy rules the code follows

- Success is only reported as **Confirmed** when the phone observed a change; otherwise it shows "Sent, not confirmed", "Not supported", "Wrong app" or "Failed".
- Unsupported interactions return a clear reason (for example unlike not allowed, or no Like button found).
- No passwords are collected; no login automation; no INTERNET permission; accessibility scope limited to the four apps; password fields skipped.
- Direct BLE session is optional and off by default.

## Extending

- **New app:** add an entry to `SocialApp`, a profile in `AppProfiles`, its package in `accessibility_service_config.xml` and the manifest `<queries>`.
- **Better Like detection:** adjust the id / description hints in the profile; use *Layout Inspector* or `uiautomator dump` to find the current ids.
- **A watch or device that can run code:** expose the service described in `RemoteProtocol.kt`; the app needs no changes.
