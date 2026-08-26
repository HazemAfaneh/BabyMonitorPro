import ActivityKit
import KMPLiveActivities
import SwiftUI
import WidgetKit

// MARK: - The Kotlin payloads

// Field names must match `ViewingAttributes` / `BroadcastingAttributes` and their content
// states in `shared/.../notify/LiveSessions.mobile.kt` exactly: the payload is produced by
// kotlinx.serialization with no name mapping, and a mismatch decodes to nil, which renders
// as an empty activity rather than as an error anyone would see.

private struct ViewingAttributesDTO: Decodable {
    let cameraName: String
    let host: String
    let port: Int
}

private struct ViewingStateDTO: Decodable {
    let statusLabel: String
    let alert: String?
}

private struct BroadcastingAttributesDTO: Decodable {
    let deviceName: String
}

private struct BroadcastingStateDTO: Decodable {
    let statusLabel: String
    let viewerCount: Int
    let address: String?
}

private enum KotlinType {
    static let viewing = "com.hazemafaneh.babymonitorpro.notify.ViewingAttributes"
    static let broadcasting = "com.hazemafaneh.babymonitorpro.notify.BroadcastingAttributes"
}

// MARK: - Colour

/// The semantic layer from `shared/.../ui/theme/Semantic.kt`, restated here because a Widget
/// Extension is a separate process that cannot read a Compose theme.
///
/// Amber means live and healthy and nothing else — the discipline the app screens keep. A
/// widget that painted "Connecting" amber would teach the parent that amber means "the app is
/// doing something", which is the habit that made the colour worthless before.
private enum StatusTone {
    case live, degraded, waiting, fault

    var color: Color {
        switch self {
        case .live: return Color(red: 1.0, green: 0.776, blue: 0.42)      // 0xFFFFC66B
        case .degraded: return Color(red: 0.604, green: 0.651, blue: 0.91) // 0xFF9AA6E8
        case .waiting: return Color(red: 0.714, green: 0.729, blue: 0.796) // 0xFFB6BACB
        case .fault: return Color(red: 0.906, green: 0.569, blue: 0.529)   // 0xFFE79187
        }
    }

    /// The label is the only thing the payload carries, so the tone is recovered from it.
    /// The strings come from `VideoStatus.describe()` and `publishBroadcastSession()`.
    static func of(_ label: String) -> StatusTone {
        switch label {
        case "Live", "Broadcasting": return .live
        case "Test pattern", "Sound only": return .degraded
        case "Connecting", "Reconnecting", "Starting": return .waiting
        default: return .fault  // "Connected · no picture", "Camera stopped"
        }
    }
}

/// One steady dot. It never pulses or blinks: anything that blinks in a dark bedroom is both
/// tuned out within a night and lighting the room while it is ignored.
private struct StatusDot: View {
    let tone: StatusTone
    var size: CGFloat = 8

    var body: some View {
        Circle()
            .fill(tone.color)
            .frame(width: size, height: size)
    }
}

// MARK: - The widget

/// A single `Widget`, not one per session.
///
/// Every activity started through KMP-LiveActivities is backed by the same
/// `KMPLiveActivityAttributes`, and ActivityKit routes to a configuration by attributes type —
/// so two configurations for the two sessions would collide on one type and only one of them
/// would ever run. Which session this is comes from `attributesTypeName` instead.
@available(iOS 16.2, *)
struct BabyMonitorLiveActivity: Widget {

    var body: some WidgetConfiguration {
        KMPLiveActivityWidget.configuration { context in
            LockScreenView(context: context)
                .padding(16)
                .activityBackgroundTint(Color.black.opacity(0.45))
        } dynamicIsland: { context in
            island(context)
        }
    }

    private func island(
        _ context: ActivityViewContext<KMPLiveActivityAttributes>
    ) -> DynamicIsland {
        let session = Session(context)
        return DynamicIsland {
            DynamicIslandExpandedRegion(.leading) {
                StatusDot(tone: session.tone).padding(.leading, 4)
            }
            DynamicIslandExpandedRegion(.center) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(session.title)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(session.headline)
                        .font(.headline)
                        .lineLimit(2)
                    if let detail = session.detail {
                        Text(detail)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        } compactLeading: {
            StatusDot(tone: session.tone, size: 7)
        } compactTrailing: {
            // The compact trailing slot is a few characters wide, so it carries the count
            // when broadcasting and nothing at all when the headline would be truncated to
            // meaninglessness.
            if let compact = session.compactTrailing {
                Text(compact).font(.caption2)
            }
        } minimal: {
            StatusDot(tone: session.tone, size: 7)
        }
    }
}

@available(iOS 16.2, *)
private struct LockScreenView: View {
    let context: ActivityViewContext<KMPLiveActivityAttributes>

    var body: some View {
        let session = Session(context)
        HStack(alignment: .top, spacing: 10) {
            StatusDot(tone: session.tone).padding(.top, 5)
            VStack(alignment: .leading, spacing: 3) {
                Text(session.title)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text(session.headline)
                    .font(.headline)
                if let detail = session.detail {
                    Text(detail)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: 0)
        }
    }
}

// MARK: - One shape for both sessions

/// The two sessions reduced to the four things every surface here needs, so the Lock Screen
/// and the three Dynamic Island presentations decode the payload once and never disagree.
@available(iOS 16.2, *)
private struct Session {
    let title: String
    /// The line the eye lands on. For a watching session an alert takes it — that is the
    /// reason the parent is looking — and the connection state falls back to it.
    let headline: String
    let detail: String?
    let tone: StatusTone
    let compactTrailing: String?

    init(_ context: ActivityViewContext<KMPLiveActivityAttributes>) {
        switch context.attributes.attributesTypeName {
        case KotlinType.viewing:
            let attributes = try? context.attributes.decoded(as: ViewingAttributesDTO.self)
            let state = try? context.state.decoded(as: ViewingStateDTO.self)
            let status = state?.statusLabel ?? ""
            title = attributes?.cameraName ?? "Camera"
            // An alert replaces the one before it rather than stacking: this activity is
            // updated in place, never posted a second time.
            headline = state?.alert ?? status
            detail = state?.alert == nil ? nil : status
            tone = StatusTone.of(status)
            compactTrailing = nil

        case KotlinType.broadcasting:
            let attributes = try? context.attributes.decoded(as: BroadcastingAttributesDTO.self)
            let state = try? context.state.decoded(as: BroadcastingStateDTO.self)
            let status = state?.statusLabel ?? ""
            let viewers = state?.viewerCount ?? 0
            title = attributes?.deviceName ?? "This device"
            // Zero is printed, exactly as it is on the camera screen and in the Android
            // renderer: a count that disappears reads as a broken count, not as nobody there.
            headline = "\(status) · \(Self.watching(viewers))"
            detail = state?.address
            tone = StatusTone.of(status)
            compactTrailing = "\(viewers)"

        default:
            title = ""
            headline = ""
            detail = nil
            tone = .waiting
            compactTrailing = nil
        }
    }

    private static func watching(_ count: Int) -> String {
        switch count {
        case 0: return "nobody watching"
        case 1: return "1 watching"
        default: return "\(count) watching"
        }
    }
}
