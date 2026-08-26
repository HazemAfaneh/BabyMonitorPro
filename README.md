# BabyMonitor Pro

Watch and hear your baby from any device on the same WiFi. No cloud, no accounts, no
internet connection required — one device runs as the **Camera** and streams straight to
however many **Viewers** you have open.

Kotlin Multiplatform + Compose Multiplatform, targeting **Android, iOS, desktop (JVM) and
web (wasmJs)**. The camera role runs on Android, iOS and desktop; the web build is
viewer-only, because a browser cannot host a server.

- Transport and message schemas: [PROTOCOL.md](PROTOCOL.md)
- Video: MJPEG over HTTP. Audio: raw PCM over WebSocket. Deliberately simple, identical on
  every target, zero native media dependencies.

---

## Running it

Everything is driven by the Gradle wrapper; no local Gradle install is needed. JDK 17+.

### Desktop (macOS / Windows / Linux)

```bash
./gradlew :desktopApp:run
```

Packaging a native bundle:

```bash
./gradlew :desktopApp:packageDistributionForCurrentOS
```

### Android

```bash
./gradlew :androidApp:installDebug          # build and install on a connected device
./gradlew :androidApp:assembleDebug         # just build the APK
```

### Web

```bash
./gradlew :webApp:wasmJsBrowserDevelopmentRun
```

Opens on `http://localhost:8080` by default. Enter the camera's address on the "Find a
camera" screen — browsers cannot do mDNS discovery.

### iOS

Open `iosApp/iosApp.xcodeproj` in Xcode and run, or from the command line:

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 16' build
```

Set `TEAM_ID` in `iosApp/Configuration/Config.xcconfig` before running on a device.

The project depends on one Swift package, `KMPLiveActivities`, used by the app target and by
the `BabyMonitorWidgets` extension. It is referenced as a **local** package at
`../../kmp-live-activities` — i.e. a clone of `github.com/HazemAfaneh/kmp-live-activities`
checked out next to this repo:

```bash
git clone https://github.com/HazemAfaneh/kmp-live-activities \
  "$(dirname "$PWD")/kmp-live-activities"
```

This is temporary. The package is referenced locally only because the upstream repository has
no `0.1.0` tag and no `Package.swift` at its root yet, so SwiftPM cannot resolve it by URL —
`no versions of 'kmp-live-activities' match the requirement 0.1.0..<1.0.0`. The root manifest
is committed and tagged in that clone; once it is pushed, swap the
`XCLocalSwiftPackageReference` in `project.pbxproj` back to an `XCRemoteSwiftPackageReference`
on the repository URL, from `0.1.0`, and delete this paragraph.

From the command line, add `-allowProvisioningUpdates` the first time so the extension's
bundle identifier gets a profile.

### Tests

```bash
./gradlew :shared:jvmTest      # detectors, protocol, QR, MJPEG parsing, live server round-trips
./gradlew build                # everything, all targets
```

---

## Permissions

### Android — `androidApp/src/main/AndroidManifest.xml`

Already declared:

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | LAN sockets. Nothing leaves the network. |
| `CHANGE_WIFI_MULTICAST_STATE` | mDNS discovery on some devices. |
| `CAMERA`, `RECORD_AUDIO` | Camera role only. Requested at runtime on the camera screen. |
| `FOREGROUND_SERVICE` + `_CAMERA` + `_MICROPHONE` | Keeps capture alive with the screen off. |
| `POST_NOTIFICATIONS` | The broadcasting notification and motion/sound alerts. |
| `WAKE_LOCK` | Keep-screen-on while broadcasting. |

The `bmpro://` scheme is registered on `MainActivity` for scanned pairing codes.

### iOS — `iosApp/iosApp/Info.plist`

Already declared: `NSCameraUsageDescription`, `NSMicrophoneUsageDescription`,
`NSLocalNetworkUsageDescription`, `NSBonjourServices` (`_babymonitorpro._tcp`),
`NSAppTransportSecurity.NSAllowsLocalNetworking`, `NSSupportsLiveActivities`, and the
`bmpro` URL scheme.

Live Activities need no runtime permission prompt, but without `NSSupportsLiveActivities`
ActivityKit refuses every request — and the parent can still turn them off per app in
Settings ▸ BabyMonitor Pro ▸ Live Activities.

**Both** local-network keys are required from iOS 14: without `NSBonjourServices`, Bonjour
finds nothing and reports no error.

### macOS desktop — `desktopApp/entitlements.plist`

A packaged macOS build is sandboxed and needs `com.apple.security.device.camera`,
`.device.audio-input`, `.network.server` (the embedded HTTP server) and `.network.client`.
These are wired into `nativeDistributions.macOS` along with the usage-description strings.
Running via `./gradlew :desktopApp:run` is unsandboxed and prompts on first use instead.

---

## How it fits together

```
shared/src/
  commonMain/   protocol, detectors, QR encoder, viewer client, UI, DI, settings
    hostMain/   Ktor server, capture, mDNS  (Android + iOS + desktop only)
      jvmAndroidMain/   code identical on both JVM platforms (LAN addresses)
    webMain/    viewer-only actuals: capture and discovery throw or return null
```

The split is deliberate: nothing server-side is compiled into the browser bundle.

| | Android | iOS | Desktop | Web |
|---|---|---|---|---|
| Camera capture | CameraX `ImageAnalysis` | `AVCaptureSession` | webcam-capture¹ | — |
| Microphone | `AudioRecord` | `AVAudioEngine` | `TargetDataLine` | — |
| Audio playback | `AudioTrack` | `AVAudioEngine` | `SourceDataLine` | not yet² |
| Discovery | `NsdManager` | `NSNetService` | JmDNS | manual entry |
| QR scanning | ML Kit barcode | `AVCaptureMetadataOutput` | — | — |
| Alerts | Notification | `UNUserNotificationCenter` | tray message | in-app banner |
| Live session | Live Update (Android 16) | Live Activity (16.2+) | — | — |

¹ webcam-capture's bundled driver has no aarch64 build, so on Apple Silicon the desktop
camera reports "no camera" and the broadcaster falls back to a built-in test pattern.
Desktop audio capture and the full viewer role work normally.

² Web audio playback is stubbed with a `TODO(platform)`; getting raw PCM into WebAudio from
Kotlin/Wasm needs either a browser-wrapper dependency or a second endpoint serving WAV.
The web viewer plays video only.

The live view is full-bleed with auto-hiding controls everywhere except the browser: there
the video is an `<img>` layered over the Compose canvas, which is not transparent, so the
web build gives the video its own band between a fixed top row and bottom bar instead of
drawing controls that the picture would cover.

---

## The live session

While a device is broadcasting, or a parent is watching, the app keeps one entry on the
system's own live surface: a **Live Activity** on the iOS Lock Screen and in the Dynamic
Island, and a **Live Update** on Android 16. Both are driven from one place —
`shared/src/mobileMain/.../notify/LiveSessions.mobile.kt`, through the
`io.github.hazemafaneh.liveactivities` library — so the two platforms cannot drift apart on
what the camera is currently doing.

What it shows:

| | Watching | Broadcasting |
|---|---|---|
| Title | camera name | device name |
| Headline | the last alert, else the connection state | `Broadcasting · 3 watching` |
| Caption | connection state, when an alert took the headline | `192.168.1.24:8080` |

Zero viewers is printed as "nobody watching" rather than hidden: a count that disappears
reads as a broken count. An alert replaces the previous one instead of stacking, because the
running session is updated in place — a parent who slept through two alerts should wake to
the current state, not to a pile.

Nothing here can fail loudly. Every call returns a `Result` that is deliberately dropped: a
parent who has Live Activities switched off, or is on Android 13, gets exactly the app they
had before. The picture and the in-app banner are the product; this is a convenience laid on
top, and a broadcast that stopped because a notification surface refused would be the tail
wagging the dog.

### On iOS

Three pieces have to be present, and the failure mode if any is missing is silence rather
than an error:

- **`iosApp/BabyMonitorWidgets/`** — the Widget Extension. Apple requires Lock Screen and
  Dynamic Island views to be SwiftUI compiled into an extension, so this cannot live in
  `:shared`. It is one `Widget`, not two: every activity is backed by the same
  `KMPLiveActivityAttributes`, and ActivityKit routes by attributes type, so two
  configurations would collide on one type. Which session it is comes from
  `attributes.attributesTypeName`.
- **`iosApp/iosApp/LiveActivityKitBridge.swift`** — `ActivityKit` is Swift-only with no
  Objective-C surface, so Kotlin/Native cannot reach it through cinterop. The Kotlin side
  serialises to JSON and calls this bridge, registered once in `iOSApp.init`. Until
  `register` runs, every start fails with *"The KMPLiveActivities Swift package is not
  registered"* — which is swallowed, so the symptom is that nothing ever appears.
- **`export(libs.live.activities)`** on the iOS framework in `shared/build.gradle.kts`.
  Without it `LiveActivityManager` and `LiveActivityBridge` are absent from `Shared.h` and
  the bridge does not compile at all.

The widget decodes the payload into its own `Decodable` structs, whose field names must match
the Kotlin state classes exactly — kotlinx.serialization applies no name mapping, and a
mismatch decodes to `nil`, which renders as an empty activity.

Verifying needs a real device or an iOS 16.2+ simulator. Open the camera role: the
broadcasting session should appear at once and its viewer count change as devices connect.
Open the live view elsewhere: the watching session appears there and updates when motion or
sound fires.

### On Android

The renderers live in `androidApp/.../BabyMonitorApp.kt` rather than in `:shared`, because a
renderer produces Android notification content. `POST_NOTIFICATIONS` is requested at runtime
on API 33+; the status-bar chip on Android 16 needs only a manifest declaration. The chip
text is one word ("LIVE", "ON AIR") — it truncates at about seven characters.

---

## Privacy

Video and audio travel directly between your own devices over your own WiFi. There is no
account, no cloud relay, no recording and no analytics. The camera binds to the local
network interface so other devices can reach it; the app never forwards a port and never
opens a connection to the internet. There is no access control: any device on the same WiFi
that knows the address can open the stream.

## Not in v1

Cloud relay, accounts, recording and playback, two-way talk, access control, encryption,
cry classification, multi-camera grid. The code is structured so these are additive.
