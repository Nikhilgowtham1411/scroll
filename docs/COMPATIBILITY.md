# CMF Watch Pro compatibility

What the **original CMF Watch Pro** (2023, Bluetooth 5.3, proprietary OS, CMF Watch app) can and cannot do with CMF Social Remote.

## What was investigated

From Nothing/CMF's published specs, support pages and reviews:

- The watch runs CMF's own operating system. There is **no app store**, no developer SDK and no way to install third-party watch apps.
- Phone-facing features are all delivered through the **CMF Watch app** (now also the Nothing X app): notifications, music control, camera remote, find my phone, weather, dial gallery.
- **Music control** is documented for Android 8.0+. The watch's Music screen offers play/pause, previous, next and volume for whatever is playing on the phone.
- **Camera remote** is documented for Android 8.0+.
- **Watch faces** come from the app's Dial Gallery (cloud dials, photo-album dials, customised dials). They are display templates; they cannot run code or send touch events to the phone.
- Bluetooth 5.3 only. No public GATT specification for the watch's data channel was found, and none is documented by CMF. This project does not reverse-engineer or replay the proprietary protocol.

## The limitation

> **A phone app cannot install a native watch app, add gestures to the watch's touch screen, or replace its firmware. The original CMF Watch Pro therefore cannot send "swipe up", "swipe down" or "double tap" from its own screen to this app.**

No part of this project claims otherwise.

## The best compatible implementation

The watch can already send **media commands** to the phone from its Music screen. CMF Social Remote holds the phone's active media session, so Android delivers those commands to it instead of a music player (no audio is played, no audio focus is requested):

| On the watch (Music screen) | Arrives as | Mapped to |
|---|---|---|
| ▶▶ next | `MEDIA_NEXT` | Swipe up → next Reel / Short / post |
| ◀◀ previous | `MEDIA_PREVIOUS` | Swipe down → previous |
| ⏯ pressed once | `MEDIA_PLAY_PAUSE` | Single tap → **no action** |
| ⏯ pressed twice within the window | two `PLAY_PAUSE` | Double tap → like; again → unlike |

The double-press window follows the *Gesture sensitivity* setting (about 280–650 ms).

The session's title and artist are set to the active app and a short legend, so the watch's Music screen may show them as the "track". This depends on the CMF Watch app and is not guaranteed.

## Feature matrix

| Capability | Original CMF Watch Pro | Notes |
|---|---|---|
| Next / previous via Music screen | **Works — verify on your phone** | Depends on the CMF Watch app relaying the keys to the active session and on your phone maker's media routing. Not testable without the hardware. Fallback: Settings → *Re-claim watch music controls*. |
| Double-press ⏯ → like / unlike | **Works — verify on your phone** | Same path as above. |
| Single tap = no action | **Works** | Reported as "Single tap — no action". |
| Phone-side scroll / like in the 4 apps | **Works** | Accessibility service; per-app profiles. |
| Confirmed / not confirmed result on the phone | **Works** | Phone feedback only. |
| Watch connection status | **Works** | From Android's Bluetooth connection events for the paired watch. |
| Battery level | **Maybe** | Only if the watch exposes the standard Battery Service and accepts a second connection while the CMF Watch app is connected. Off by default. |
| Scan and list BLE devices, connect / disconnect, auto-reconnect | **Works** (generic BLE) | For the watch itself the system's paired list is more reliable: a watch connected to the CMF Watch app usually stops advertising. |
| Direct BLE command channel (`RemoteProtocol`) | **Needs watch-side protocol support** | Implemented and ready, but the original watch cannot expose the custom service. Works with any BLE device that can (ESP32, nRF52, a future watch app). |
| Custom swipe / double-tap gestures drawn on the watch | **Not possible** | Closed OS, no SDK. |
| Gesture-pad watch face | **Not possible** | Dials are static or cloud templates. |
| Watch-side haptic or on-screen success / failure | **Not possible** | The watch cannot be told the outcome; feedback is on the phone. |
| Volume keys as extra gestures | **Not implemented** | They change the phone's volume as a side effect. |
| Camera-shutter button as a trigger | **Not implemented** | Its transport is undocumented. |

Legend: **Works** = implemented and not dependent on the watch; **verify on your phone** = designed against documented Android behaviour but needs a real watch to confirm; **Needs watch-side protocol support** = code is here, hardware cannot use it yet; **Not possible** = blocked by the watch platform.

## Reference BLE protocol (for watch-side implementers)

Service `c3f1a000-5a1d-4b6e-9c0e-0c1f5c0e5a11`, one notify characteristic `c3f1a001-5a1d-4b6e-9c0e-0c1f5c0e5a11`. Each notification is one byte:

| Byte | Command |
|---|---|
| `0x01` | Swipe up (next) |
| `0x02` | Swipe down (previous) |
| `0x03` | Double tap (like / unlike) |

Unknown bytes are ignored. The app subscribes automatically when a direct BLE session finds the service.

## Phone-side behaviour by app

Profiles live in `control/AppProfiles.kt`. All Like-button detection is heuristic (resource ids and content descriptions) because the apps publish no stable interface; app updates and non-English locales can break it.

| App | Scroll | Like | Unlike | Open |
|---|---|---|---|---|
| Instagram (Reels / Feed) | Swipe; fast for Reels, slow shorter swipe for Feed | Like button, else double-tap | Like button | Deep link to Reels, else launcher |
| YouTube Shorts | Swipe | Like button, else double-tap | Like button | Deep link to Shorts, else launcher |
| Facebook Reels | Swipe | Like button, else double-tap | Like button | Launcher |
| TikTok | Swipe | Like button, else double-tap | Like button | Launcher |

When an action is not supported, the app says so (for example, "Already liked — a double-tap can't unlike in Instagram") instead of pretending.

## How results are verified

| Result | Meaning | Shown as |
|---|---|---|
| Confirmed | The phone saw the change: the screen content changed after a swipe, or the Like control's state flipped. | Green |
| Sent, not confirmed | The input was delivered but nothing on screen proved it (end of list, still loading, app doesn't expose the Like state, double-tap fallback). | Amber |
| Not supported / wrong app / accessibility off / failed | Nothing was done, or Android rejected it. | Red |
| Skipped | Single tap, or faster than the cooldown. | Grey |

"Confirmed" for scrolling is best effort: it means the visible screen changed, not that a specific item appeared.

## Security and privacy

- No social-media passwords are requested, stored or read. Password fields are skipped. The app never automates login.
- The manifest has **no INTERNET permission**; nothing can leave the phone.
- The accessibility service is limited by `accessibility_service_config.xml` to the six package names of the four apps and acts only on a command from the watch or the on-screen test dial.
- Screen text is hashed in memory only to detect "the screen changed" after a swipe; it is never stored or logged.
- Android requires the user to enable the service manually. On Android 13+ sideloaded installs may need **App info → ⋮ → Allow restricted settings** first (shown in onboarding).
- `allowBackup` is off.

## Known risks that need a real device

1. **Media-key routing.** Android sends media keys to the most recently "playing" session. The app re-claims priority when a supported app comes to the foreground and offers a manual re-claim, but some phones or players may still take the keys.
2. **How the CMF Watch app relays Music-screen commands.** If it controls a specific player rather than the active session, this path would not work. This could not be verified without the watch.
3. **Like-button detection** can change with any social app update.
4. **Direct BLE session** may compete with the CMF Watch app for the watch's single connection; it is off by default.
5. **Not compiled in the authoring environment.** See README.
