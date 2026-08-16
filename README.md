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
`NSAppTransportSecurity.NSAllowsLocalNetworking`, and the `bmpro` URL scheme.

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

## Privacy

Video and audio travel directly between your own devices over your own WiFi. There is no
account, no cloud relay, no recording and no analytics. The camera binds to the local
network interface so other devices can reach it; the app never forwards a port and never
opens a connection to the internet. The optional 6-digit PIN keeps other people on the same
WiFi from opening the stream — it is access control, not encryption.

## Not in v1

Cloud relay, accounts, recording and playback, two-way talk, encryption beyond the PIN,
cry classification, multi-camera grid. The code is structured so these are additive.
