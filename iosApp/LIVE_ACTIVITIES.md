# Live Activities — remaining iOS steps

The Kotlin half is done and compiles: `shared/src/mobileMain/.../notify/LiveSessions.mobile.kt`
drives `LiveActivityManager` for both sessions, and `Info.plist` already declares
`NSSupportsLiveActivities`.

What is left is Swift, and it needs Xcode — a new Widget Extension target cannot be added by
editing `project.pbxproj` by hand with any confidence, and none of it can be built or verified
from the command line here. Four steps.

## 1. Add the Swift package

Xcode → **File ▸ Add Package Dependencies…** → `https://github.com/hazemafaneh/kmp-live-activities`,
rule *Up to Next Major* from `0.1.0`. Add the `KMPLiveActivities` product to **both** the
`iosApp` target and the widget extension target created in step 3.

## 2. Register the ActivityKit bridge at launch

`ActivityKit` is Swift-only, so the Kotlin side talks to it through a bridge the app installs
once. Add `iosApp/iosApp/LiveActivityKitBridge.swift`:

```swift
import Shared
import KMPLiveActivities

@available(iOS 16.2, *)
final class LiveActivityKitBridge: LiveActivityBridge {
    init() {
        let c = KMPLiveActivityController.shared
        c.onStartResult = { id, ok, err in
            LiveActivityManager.shared.notifyStartResult(activityId: id, success: ok, errorKind: err)
        }
        c.onPushToken = { id, tok in
            LiveActivityManager.shared.notifyPushToken(activityId: id, token: tok)
        }
        c.onStatusChanged = { id, s in
            LiveActivityManager.shared.notifyStatusChanged(activityId: id, status: s)
        }
    }

    func areActivitiesEnabled() -> Bool {
        KMPLiveActivityController.shared.areActivitiesEnabled
    }

    func start(activityId: String, attributesTypeName: String, attributesJson: String,
               contentStateJson: String, staleAfterSeconds: Double, requestPushToken: Bool) {
        KMPLiveActivityController.shared.start(
            activityId: activityId, attributesTypeName: attributesTypeName,
            attributesJson: attributesJson, contentStateJson: contentStateJson,
            staleAfterSeconds: staleAfterSeconds, requestPushToken: requestPushToken)
    }

    func update(activityId: String, contentStateJson: String, staleAfterSeconds: Double) {
        KMPLiveActivityController.shared.update(
            activityId: activityId, contentStateJson: contentStateJson,
            staleAfterSeconds: staleAfterSeconds)
    }

    func end(activityId: String, finalContentStateJson: String?, dismissalSeconds: Double) {
        KMPLiveActivityController.shared.end(
            activityId: activityId, finalContentStateJson: finalContentStateJson,
            dismissalSeconds: dismissalSeconds)
    }
}
```

Then in `iOSApp.swift`:

```swift
@main
struct iOSApp: App {
    init() {
        if #available(iOS 16.2, *) {
            LiveActivityManager.shared.register(bridge: LiveActivityKitBridge())
        }
    }
    var body: some Scene { WindowGroup { ContentView() } }
}
```

Until `register` runs, every `start` fails with "The KMPLiveActivities Swift package is not
registered" — which the Kotlin side swallows, so the symptom is simply that nothing appears.

## 3. Add the Widget Extension target

**File ▸ New ▸ Target… ▸ Widget Extension**, name `BabyMonitorWidgets`, **uncheck** "Include
Configuration App Intent", **check** "Include Live Activity". Embed it in `iosApp`.

## 4. Write the two widgets

The Kotlin state types serialize with these exact field names — the Swift DTOs must match them
or `decoded(as:)` returns nil and the widget renders empty:

| Kotlin type | `attributesTypeName` sent to the bridge | JSON fields |
|---|---|---|
| `ViewingAttributes` | `com.hazemafaneh.babymonitorpro.notify.ViewingAttributes` | `cameraName: String`, `host: String`, `port: Int` |
| `ViewingState` | — | `statusLabel: String`, `alert: String?` |
| `BroadcastingAttributes` | `com.hazemafaneh.babymonitorpro.notify.BroadcastingAttributes` | `deviceName: String` |
| `BroadcastingState` | — | `statusLabel: String`, `viewerCount: Int`, `address: String?` |

```swift
import WidgetKit
import SwiftUI
import KMPLiveActivities

struct ViewingStateDTO: Decodable {
    let statusLabel: String
    let alert: String?
}

struct BroadcastingStateDTO: Decodable {
    let statusLabel: String
    let viewerCount: Int
    let address: String?
}

struct BabyMonitorWidgets: Widget {
    var body: some WidgetConfiguration {
        KMPLiveActivityWidget.configuration { context in
            let state = try? context.state.decoded(as: ViewingStateDTO.self)
            return VStack(alignment: .leading, spacing: 4) {
                Text(state?.alert ?? state?.statusLabel ?? "")
                    .font(.headline)
                if state?.alert != nil {
                    Text(state?.statusLabel ?? "").font(.caption)
                }
            }
            .padding()
        } dynamicIsland: { context in
            let state = try? context.state.decoded(as: ViewingStateDTO.self)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.center) {
                    Text(state?.alert ?? state?.statusLabel ?? "")
                }
            } compactLeading: {
                Image(systemName: "dot.radiowaves.left.and.right")
            } compactTrailing: {
                Text(state?.statusLabel ?? "")
            } minimal: {
                Image(systemName: "dot.radiowaves.left.and.right")
            }
        }
    }
}
```

Add a second `Widget` of the same shape for `BroadcastingStateDTO` (title from
`attributes.deviceName`, body `"\(statusLabel) · \(viewerCount) watching"` with zero printed as
"nobody watching", `address` as the caption).

Design notes worth keeping when you build these views: amber is reserved for live-and-healthy
and nothing else, the status indicator is a steady dot that never pulses, and an alert replaces
the previous one rather than stacking. The Android renderers in
`androidApp/.../BabyMonitorApp.kt` already follow all three — match them.

## Verifying

Live Activities need a real device or an iOS 16.2+ simulator, and Settings ▸ BabyMonitor Pro ▸
**Live Activities** must be on. Open the camera role: the broadcast session should appear
immediately and its viewer count should change as devices connect. Open the live view on
another device: the watching session appears there and updates when motion or sound fires.
