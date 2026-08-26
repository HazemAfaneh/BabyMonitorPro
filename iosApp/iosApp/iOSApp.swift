import Shared
import SwiftUI

@main
struct iOSApp: App {

    // Until the bridge is installed, every LiveActivityManager.start fails with "The
    // KMPLiveActivities Swift package is not registered" — and the Kotlin side drops the
    // failure on purpose, so the only symptom would be nothing ever appearing.
    init() {
        if #available(iOS 16.2, *) {
            LiveActivityManager.shared.register(bridge: LiveActivityKitBridge())
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
