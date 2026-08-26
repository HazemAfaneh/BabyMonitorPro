package com.hazemafaneh.babymonitorpro

import android.app.Application
import com.hazemafaneh.babymonitorpro.core.AndroidPlatformContext
import com.hazemafaneh.babymonitorpro.notify.BroadcastingAttributes
import com.hazemafaneh.babymonitorpro.notify.BroadcastingState
import com.hazemafaneh.babymonitorpro.notify.ViewingAttributes
import com.hazemafaneh.babymonitorpro.notify.ViewingState
import io.github.hazemafaneh.liveactivities.AttributedLiveActivityRenderer
import io.github.hazemafaneh.liveactivities.LiveActivityManager
import io.github.hazemafaneh.liveactivities.LiveActivityNotificationContent
import io.github.hazemafaneh.liveactivities.ProgressStyleData

class BabyMonitorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Shared code reaches Android services (preferences, camera, mDNS) through this.
        AndroidPlatformContext.install(this)

        // Live Updates. Here rather than in :shared because a renderer produces Android
        // notification content, which is a platform type the shared module has no business
        // knowing about — and because this has to run before the first start(), which the
        // broadcaster can trigger as soon as the camera screen opens.
        LiveActivityManager.init(this)
        registerLiveUpdateRenderers()
    }
}

/**
 * How the two sessions look in the shade and on the status-bar chip.
 *
 * Both use an indeterminate progress style, which sounds odd until you notice why it is
 * there: Android 16 only *promotes* a notification — gives it the chip and the top-of-shade
 * slot — when it carries one of a few required styles, and `ProgressStyle` is the one that
 * fits. Neither of these jobs has a percentage; watching a baby has no end state to fill a
 * bar towards. Indeterminate says "ongoing" without inventing a number.
 */
private fun registerLiveUpdateRenderers() {
    LiveActivityManager.registerAttributedRenderer(
        stateType = ViewingState::class,
        renderer = AttributedLiveActivityRenderer<ViewingAttributes, ViewingState> { attributes, state ->
            LiveActivityNotificationContent(
                title = attributes.cameraName,
                // The alert is the reason the parent is looking, so it takes the line the eye
                // lands on; the connection state falls back to it when nothing has happened.
                text = state.alert ?: state.statusLabel,
                subText = if (state.alert != null) state.statusLabel else null,
                progressStyle = ProgressStyleData(isIndeterminate = true),
            )
        },
    )

    LiveActivityManager.registerAttributedRenderer(
        stateType = BroadcastingState::class,
        renderer = AttributedLiveActivityRenderer<BroadcastingAttributes, BroadcastingState> { attributes, state ->
            LiveActivityNotificationContent(
                title = attributes.deviceName,
                // Zero is printed, exactly as it is on the camera screen: a count that
                // disappears reads as a broken count rather than as nobody watching.
                text = when (state.viewerCount) {
                    0 -> "${state.statusLabel} · nobody watching"
                    1 -> "${state.statusLabel} · 1 watching"
                    else -> "${state.statusLabel} · ${state.viewerCount} watching"
                },
                subText = state.address,
                progressStyle = ProgressStyleData(isIndeterminate = true),
            )
        },
    )
}
