package com.hazemafaneh.babymonitorpro.notify

import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import io.github.hazemafaneh.liveactivities.DismissalPolicy
import io.github.hazemafaneh.liveactivities.LiveActivityAndroidConfig
import io.github.hazemafaneh.liveactivities.LiveActivityAttributes
import io.github.hazemafaneh.liveactivities.LiveActivityConfig
import io.github.hazemafaneh.liveactivities.LiveActivityContentState
import io.github.hazemafaneh.liveactivities.LiveActivityManager
import io.github.hazemafaneh.liveactivities.StatusChipConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Static metadata of a viewer session — fixed for as long as the parent watches one camera.
 *
 * The host and port ride along because the iOS widget and the Android renderer both want to
 * be able to say *which* camera this is when several are on the network, and neither can ask
 * the app once the app is in the background.
 */
@Serializable
data class ViewingAttributes(
    val cameraName: String,
    val host: String,
    val port: Int,
) : LiveActivityAttributes

/** What changes while watching: the connection's health, and the last thing it heard or saw. */
@Serializable
data class ViewingState(
    val statusLabel: String,
    val alert: String? = null,
) : LiveActivityContentState

@Serializable
data class BroadcastingAttributes(
    val deviceName: String,
) : LiveActivityAttributes

/**
 * What changes while broadcasting. [viewerCount] is carried as a number rather than a
 * pre-rendered string so the widget can print "Nobody watching" for zero in its own voice —
 * and so a future grid can count devices without parsing English.
 */
@Serializable
data class BroadcastingState(
    val statusLabel: String,
    val viewerCount: Int,
    val address: String? = null,
) : LiveActivityContentState

/**
 * Live Activities (iOS 16.2+) and Live Updates (Android 16+), through one library.
 *
 * Everything here is deliberately unable to fail loudly. `LiveActivityManager` returns
 * `Result` rather than throwing, and every result is dropped: a parent who has turned Live
 * Activities off, or is on Android 13, must get exactly the app they had before — the picture
 * and the in-app banner are the product, and this is a convenience laid on top. A broadcast
 * that stopped because a notification surface refused would be the tail wagging the dog.
 *
 * One [Mutex] serialises the whole thing. The calls are suspending and the call sites are
 * not, so each one is launched; without the lock a `stop` launched a frame after a `start`
 * could win the race and leave an activity running with nothing tracking it.
 */
internal object KmpLiveActivitySessions : LiveSessions {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()

    private var viewingId: String? = null
    private var broadcastingId: String? = null

    override fun startViewing(endpoint: CameraEndpoint) {
        scope.launch {
            lock.withLock {
                if (viewingId != null) return@withLock
                LiveActivityManager.start(
                    attributes = ViewingAttributes(
                        cameraName = endpoint.name,
                        host = endpoint.host,
                        port = endpoint.port,
                    ),
                    initialState = ViewingState(statusLabel = "Connecting"),
                    config = config(chip = "LIVE"),
                ).onSuccess { viewingId = it.id }
            }
        }
    }

    override fun updateViewing(statusLabel: String, alert: String?) {
        scope.launch {
            lock.withLock {
                val id = viewingId ?: return@withLock
                LiveActivityManager.update(id, ViewingState(statusLabel, alert))
            }
        }
    }

    override fun stopViewing() {
        scope.launch {
            lock.withLock {
                val id = viewingId ?: return@withLock
                viewingId = null
                LiveActivityManager.end(id, DismissalPolicy.Immediate)
            }
        }
    }

    override fun startBroadcasting(deviceName: String) {
        scope.launch {
            lock.withLock {
                if (broadcastingId != null) return@withLock
                LiveActivityManager.start(
                    attributes = BroadcastingAttributes(deviceName),
                    initialState = BroadcastingState(statusLabel = "Starting", viewerCount = 0),
                    config = config(chip = "ON AIR"),
                ).onSuccess { broadcastingId = it.id }
            }
        }
    }

    override fun updateBroadcasting(statusLabel: String, viewerCount: Int, address: String?) {
        scope.launch {
            lock.withLock {
                val id = broadcastingId ?: return@withLock
                LiveActivityManager.update(
                    id,
                    BroadcastingState(statusLabel, viewerCount, address),
                )
            }
        }
    }

    override fun stopBroadcasting() {
        scope.launch {
            lock.withLock {
                val id = broadcastingId ?: return@withLock
                broadcastingId = null
                // Held for a moment rather than yanked. "It stopped" is information the
                // parent may well have wanted to see, and an activity that vanishes at the
                // same instant the picture does looks like the phone lost it.
                LiveActivityManager.end(id, DismissalPolicy.After(BROADCAST_LINGER))
            }
        }
    }

    /**
     * [chip] is the Android 16 status-bar chip, which renders about seven characters before
     * it truncates — so it is a word, not a sentence.
     *
     * [LiveActivityConfig.staleAfter] is generous on purpose: this app updates on real
     * events, and a settled camera with nobody watching legitimately says nothing for a long
     * time. Marking that stale after a minute would dim the one surface whose whole job is
     * to still be there at 4am.
     */
    private fun config(chip: String) = LiveActivityConfig(
        staleAfter = STALE_AFTER,
        androidChannelId = CHANNEL_ID,
        androidSmallIconResName = SMALL_ICON,
        androidConfig = LiveActivityAndroidConfig(
            channelId = CHANNEL_ID,
            channelName = "Live status",
            channelDescription = "Shows broadcasting and watching while the app is in the background.",
            statusChip = StatusChipConfig.CriticalText(chip),
        ),
    )

    private val STALE_AFTER = 30.minutes
    private val BROADCAST_LINGER = 5.seconds

    /** Shared with the renderers registered in the Android app module. */
    internal const val CHANNEL_ID = "live_status"

    /**
     * Resolved by name against the *application* package at post time, so the drawable has
     * to live in `androidApp`, not in this module.
     */
    internal const val SMALL_ICON = "ic_live_activity"
}

actual val liveSessions: LiveSessions = KmpLiveActivitySessions
