package com.hazemafaneh.babymonitorpro.store

import com.hazemafaneh.babymonitorpro.core.Bmp
import com.hazemafaneh.babymonitorpro.core.CaptureConfig
import com.hazemafaneh.babymonitorpro.core.Role
import com.hazemafaneh.babymonitorpro.core.defaultDeviceName

/** Everything the app remembers between launches. Nothing here leaves the device. */
class AppSettings(private val store: KeyValueStore) {

    var lastRole: Role?
        get() = store.getString(KEY_ROLE)?.let { name -> Role.entries.firstOrNull { it.name == name } }
        set(value) {
            if (value == null) store.remove(KEY_ROLE) else store.putString(KEY_ROLE, value.name)
        }

    var deviceName: String
        get() = store.getString(KEY_DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: defaultDeviceName()
        set(value) = store.putString(KEY_DEVICE_NAME, value)

    var motionSensitivity: Int
        get() = store.getInt(KEY_MOTION, 50)
        set(value) = store.putInt(KEY_MOTION, value.coerceIn(0, 100))

    var soundSensitivity: Int
        get() = store.getInt(KEY_SOUND, 50)
        set(value) = store.putInt(KEY_SOUND, value.coerceIn(0, 100))

    var nightMode: Boolean
        get() = store.getBoolean(KEY_NIGHT_MODE, false)
        set(value) = store.putBoolean(KEY_NIGHT_MODE, value)

    var lastManualHost: String
        get() = store.getString(KEY_LAST_HOST).orEmpty()
        set(value) = store.putString(KEY_LAST_HOST, value)

    /**
     * Whether opening a camera turns the sound on straight away.
     *
     * Off by default, because the first thing a parent does is usually look, and a phone
     * that starts talking the moment it is unlocked in a quiet room is the wrong surprise.
     * A tapped alert still overrides it — the app heard something, so the parent wants to
     * hear it too.
     */
    var startWithSound: Boolean
        get() = store.getBoolean(KEY_START_WITH_SOUND, false)
        set(value) = store.putBoolean(KEY_START_WITH_SOUND, value)

    /**
     * Whether a *movement* event raises a system notification while watching.
     *
     * Separate from [soundAlerts], and that separation is the point. One switch governed
     * both, so turning off the sound alerts — the ones that fire when a lorry goes past —
     * silently took the movement alerts with them, and a parent who wanted fewer
     * interruptions got none at all. They are different questions about different sensors:
     * a nursery on a noisy street wants movement only; a cot in a dark room with the camera
     * pointed at a wall wants sound only.
     */
    var motionAlerts: Boolean
        get() = store.getBoolean(KEY_MOTION_ALERTS, legacyAlertsDefault())
        set(value) = store.putBoolean(KEY_MOTION_ALERTS, value)

    /** Whether a *sound* event raises a system notification while watching. See [motionAlerts]. */
    var soundAlerts: Boolean
        get() = store.getBoolean(KEY_SOUND_ALERTS, legacyAlertsDefault())
        set(value) = store.putBoolean(KEY_SOUND_ALERTS, value)

    /**
     * What an alert does to the sound.
     *
     * The app has just said it heard something, and the next thing a parent wants is to hear
     * it — on a television that is otherwise a walk to the remote and a press on Sound, by
     * which time whatever made the noise has stopped. What it should do *afterwards* is a
     * genuinely different question, so it is the same setting rather than a second one:
     * leaving a bedroom speaker playing an empty nursery all night is its own kind of wrong.
     *
     * Nothing here ever mutes sound the parent turned on themselves — see
     * [ListenOnAlert.UNTIL_QUIET].
     */
    var listenOnAlert: ListenOnAlert
        get() = store.getString(KEY_LISTEN_ON_ALERT)
            ?.let { name -> ListenOnAlert.entries.firstOrNull { it.name == name } }
            ?: ListenOnAlert.UNTIL_QUIET
        set(value) = store.putString(KEY_LISTEN_ON_ALERT, value.name)

    /**
     * Which screen the app opens on.
     *
     * A household device has one job. The phone that lives in the nursery is *always* the
     * camera; the tablet in the kitchen is *always* watching — and both of them were asking
     * the same question every single launch, in the middle of the night, of someone holding a
     * baby. [StartupRole.ASK] stays the default because a fresh install genuinely does not
     * know yet, but any device that has settled into a job can say so once.
     */
    var startupRole: StartupRole
        get() = store.getString(KEY_STARTUP_ROLE)
            ?.let { name -> StartupRole.entries.firstOrNull { it.name == name } }
            ?: StartupRole.ASK
        set(value) = store.putString(KEY_STARTUP_ROLE, value.name)

    /**
     * Cameras this device has watched before, newest first.
     *
     * This is the only way to reach a camera that mDNS cannot find, and that is not an edge
     * case: **a tailnet carries no multicast**, so a camera reachable over Tailscale is
     * invisible to discovery by construction, no matter how well the WiFi works. The same is
     * true of a guest network, a mesh with client isolation, or any router that filters
     * mDNS. Remembering what worked before turns "type an address you have to look up
     * somewhere else" into one tap.
     *
     * Stored as `host|port|name` per line, because this is a KeyValueStore of strings and a
     * list of at most six entries does not justify a serialiser. Host and port are
     * pipe-free by construction; the name is sanitised on the way in.
     */
    var recentCameras: List<RecentCamera>
        get() = store.getString(KEY_RECENTS)
            .orEmpty()
            .lineSequence()
            .mapNotNull(RecentCamera::parse)
            .toList()
        set(value) {
            val text = value.take(MAX_RECENTS).joinToString("\n", transform = RecentCamera::encode)
            if (text.isBlank()) store.remove(KEY_RECENTS) else store.putString(KEY_RECENTS, text)
        }

    /**
     * Records a camera that actually worked, newest first, de-duplicated by host and port.
     *
     * Called when a viewer connects rather than when one is offered, because the list is
     * meant to answer "where have I successfully watched from" — an address that refused is
     * not worth offering again tomorrow.
     */
    fun rememberCamera(name: String, host: String, port: Int, atMillis: Long) {
        val entry = RecentCamera(name = name, host = host, port = port, lastSeenMillis = atMillis)
        recentCameras = listOf(entry) + recentCameras.filterNot {
            it.host == host && it.port == port
        }
    }

    fun forgetCamera(host: String, port: Int) {
        recentCameras = recentCameras.filterNot { it.host == host && it.port == port }
    }

    /**
     * The most frames per second to ask a camera for, or null for everything it sends.
     *
     * For watching from outside the house — over Tailscale from work, say — where the video
     * is coming out of a mobile data plan. MJPEG sends a whole picture per frame, so the
     * saving is very nearly linear: 2 fps is a sixth of the data of 12, and for "is the baby
     * still asleep" two frames a second is plenty.
     *
     * Null on the same WiFi, where the bandwidth is free and the smoothness is worth having.
     */
    var dataSaverFps: Int?
        get() = store.getInt(KEY_DATA_SAVER_FPS, 0).takeIf { it > 0 }
        set(value) {
            if (value == null) store.remove(KEY_DATA_SAVER_FPS) else store.putInt(KEY_DATA_SAVER_FPS, value)
        }

    /**
     * Whether the live view holds the screen on.
     *
     * On by default, which is what a monitor propped on a bedside table needs. A parent
     * watching from a phone they also want to put in a pocket can turn it off.
     */
    var keepScreenAwake: Boolean
        get() = store.getBoolean(KEY_KEEP_AWAKE, true)
        set(value) = store.putBoolean(KEY_KEEP_AWAKE, value)

    /**
     * Which lens this camera starts on.
     *
     * Remembered rather than always front, because where the phone ends up decides it: a
     * handset propped against the cot bars films with whichever lens faces the baby, and a
     * parent who had to switch it every single night was answering a question the app had
     * already been told the answer to.
     */
    var useFrontCamera: Boolean
        get() = store.getBoolean(KEY_FRONT_CAMERA, true)
        set(value) = store.putBoolean(KEY_FRONT_CAMERA, value)

    /**
     * Picture size, as the height of the frame: 720, 480 or 360.
     *
     * 720p by default because that is what the transport was designed around, but it is the
     * setting most worth turning down: MJPEG sends a whole JPEG per frame, so halving the
     * height roughly quarters both the WiFi traffic and the encode work on the nursery
     * phone — which is the phone running hot on a charger all night. A television upscales
     * whatever it receives, so 480p across the room costs a parent nothing they can see.
     */
    var videoHeight: Int
        get() = store.getInt(KEY_VIDEO_HEIGHT, 720).let { height ->
            if (VIDEO_HEIGHTS.any { it.height == height }) height else 720
        }
        set(value) = store.putInt(KEY_VIDEO_HEIGHT, value)

    /** Frames per second: 15, 12 or 8. Lower is less traffic and less heat, per [videoHeight]. */
    var frameRate: Int
        get() = store.getInt(KEY_FPS, 12).coerceIn(4, 30)
        set(value) = store.putInt(KEY_FPS, value.coerceIn(4, 30))

    /**
     * The port the camera listens on.
     *
     * The default moved off 8080 to 47821, which nothing else is likely to want. It stays
     * settable anyway: the camera screen's one-tap "try the next port" only survives until
     * the next start, and this is where that choice becomes permanent.
     */
    var port: Int
        get() = store.getInt(KEY_PORT, Bmp.DEFAULT_PORT).coerceIn(1024, 65535)
        set(value) = store.putInt(KEY_PORT, value.coerceIn(1024, 65535))

    /** The capture settings as the broadcaster wants them, assembled from the three above. */
    fun captureConfig(): CaptureConfig {
        val height = videoHeight
        val width = VIDEO_HEIGHTS.first { it.height == height }.width
        return CaptureConfig(
            width = width,
            height = height,
            fps = frameRate,
            useFrontCamera = useFrontCamera,
        )
    }

    /**
     * Back to the defaults, every key removed rather than overwritten.
     *
     * Removed, so a later change of default reaches a parent who reset — an overwritten key
     * pins today's default into their device forever. The device name goes too: it falls
     * back to the model name, which is what a fresh install shows.
     */
    fun reset() {
        ALL_KEYS.forEach(store::remove)
    }

    /**
     * What the two alert switches default to for a device that already has the old combined
     * setting stored. A parent who turned notifications off before the split meant "stop
     * interrupting me", and honouring that on both is the reading that cannot surprise them.
     */
    private fun legacyAlertsDefault(): Boolean = store.getBoolean(KEY_LEGACY_ALERTS, true)

    private companion object {
        const val KEY_ROLE = "role"
        const val KEY_DEVICE_NAME = "device_name"
        const val KEY_MOTION = "motion_sensitivity"
        const val KEY_SOUND = "sound_sensitivity"
        const val KEY_NIGHT_MODE = "night_mode"
        const val KEY_LAST_HOST = "last_manual_host"
        const val KEY_START_WITH_SOUND = "start_with_sound"
        const val KEY_MOTION_ALERTS = "motion_alert_notifications"
        const val KEY_SOUND_ALERTS = "sound_alert_notifications"

        /** The single switch these two replaced. Read only, and only as a default. */
        const val KEY_LEGACY_ALERTS = "alert_notifications"
        const val KEY_LISTEN_ON_ALERT = "listen_on_alert"
        const val KEY_DATA_SAVER_FPS = "data_saver_fps"
        const val KEY_RECENTS = "recent_cameras"
        const val KEY_STARTUP_ROLE = "startup_role"

        /** Six. Long enough for a house with two cameras and a tailnet address or two. */
        const val MAX_RECENTS = 6
        const val KEY_KEEP_AWAKE = "keep_screen_awake"
        const val KEY_FRONT_CAMERA = "use_front_camera"
        const val KEY_VIDEO_HEIGHT = "video_height"
        const val KEY_FPS = "frame_rate"
        const val KEY_PORT = "port"

        val ALL_KEYS = listOf(
            KEY_ROLE, KEY_DEVICE_NAME, KEY_MOTION, KEY_SOUND, KEY_NIGHT_MODE, KEY_LAST_HOST,
            KEY_START_WITH_SOUND, KEY_MOTION_ALERTS, KEY_SOUND_ALERTS, KEY_LEGACY_ALERTS,
            KEY_LISTEN_ON_ALERT, KEY_DATA_SAVER_FPS, KEY_RECENTS, KEY_STARTUP_ROLE,
            KEY_KEEP_AWAKE, KEY_FRONT_CAMERA, KEY_VIDEO_HEIGHT, KEY_FPS, KEY_PORT,
        )
    }
}

/**
 * What the app does when it opens.
 *
 * Deliberately three states rather than a pair of switches. Two toggles can both be on, and
 * "open as the camera AND open watching" has no meaning — a device can only do one of them
 * first. One choice of three cannot express the contradiction.
 */
enum class StartupRole(val label: String, val description: String) {
    /** Show the role picker. The right answer for a device that does both. */
    ASK("Ask me", "Opens on the Monitor tab and lets you choose"),

    /** Straight to the camera screen. For the phone that lives in the nursery. */
    CAMERA("Camera", "Opens broadcasting, for the phone that stays in the nursery"),

    /** Straight to Find a camera. For the tablet or television that only ever watches. */
    VIEWER("Watching", "Opens looking for a camera, for a device that only watches"),
}

/** What an alert does to the sound on the watching device. See [AppSettings.listenOnAlert]. */
enum class ListenOnAlert(val label: String, val description: String) {
    /** An alert changes nothing. For a parent who decides for themselves when to listen. */
    OFF(
        label = "Off",
        description = "Alerts leave the sound exactly as you set it",
    ),

    /**
     * Sound comes on with the alert and goes off again once the room has settled.
     *
     * The default, because it is the behaviour that needs no second thought: you hear what
     * woke the camera, and when the nursery has been quiet for a while the speaker stops.
     * It only ever undoes *its own* switch-on — sound the parent turned on by hand stays on,
     * because an app that mutes a monitor somebody deliberately opened is an app that cannot
     * be trusted with the night.
     */
    UNTIL_QUIET(
        label = "Until quiet",
        description = "Sound comes on with an alert and stops once the room settles",
    ),

    /** Sound comes on with the alert and stays on until it is turned off by hand. */
    STAY_ON(
        label = "Keep listening",
        description = "Sound comes on with an alert and stays on",
    ),
}

/**
 * A camera this device has watched before.
 *
 * Carries the name it announced at the time, so the list reads "Nursery" rather than
 * "100.87.4.19" — which matters most for exactly the addresses discovery cannot find, since
 * a tailnet address is the least memorable string in the house.
 */
data class RecentCamera(
    val name: String,
    val host: String,
    val port: Int,
    val lastSeenMillis: Long,
) {
    fun encode(): String = listOf(host, port.toString(), lastSeenMillis.toString(), sanitised(name))
        .joinToString("|")

    private fun sanitised(value: String): String = value.replace('|', ' ').trim()

    companion object {
        fun parse(line: String): RecentCamera? {
            val parts = line.split('|')
            if (parts.size < 4) return null
            val port = parts[1].toIntOrNull() ?: return null
            val host = parts[0].takeIf { it.isNotBlank() } ?: return null
            return RecentCamera(
                host = host,
                port = port,
                lastSeenMillis = parts[2].toLongOrNull() ?: 0L,
                name = parts.drop(3).joinToString("|").ifBlank { host },
            )
        }
    }
}

/** One offered picture size. 16:9 throughout, because the capture preset asks for it. */
data class VideoSize(val height: Int, val width: Int, val label: String)

/** The three sizes the settings screen offers, largest first. */
val VIDEO_HEIGHTS = listOf(
    VideoSize(720, 1280, "720p"),
    VideoSize(480, 854, "480p"),
    VideoSize(360, 640, "360p"),
)

/** The three frame rates, smoothest first. */
val FRAME_RATES = listOf(15, 12, 8)

/**
 * What the data-saver row offers. Null is "everything", which is right on home WiFi.
 *
 * 5 fps still reads as movement; 2 is a slideshow that answers "is the baby still there";
 * 1 is for a connection that is barely working at all.
 */
val DATA_SAVER_CHOICES: List<Pair<String, Int?>> = listOf(
    "Off" to null,
    "5 fps" to 5,
    "2 fps" to 2,
    "1 fps" to 1,
)

/**
 * Writes a viewer's remote change into this device's own settings.
 *
 * Null means "leave it alone" on the wire, and it means the same here — a parent turning the
 * torch on from the kitchen must not silently reset the sensitivity they spent a week getting
 * right. The lens is stored as the flag the camera screen reads on its next start, so the
 * change outlives the session it was made in.
 */
fun AppSettings.applyRemote(change: com.hazemafaneh.babymonitorpro.protocol.ControlMessage.SetCameraSettings) {
    change.motionSensitivity?.let { motionSensitivity = it }
    change.soundSensitivity?.let { soundSensitivity = it }
    change.useFrontCamera?.let { useFrontCamera = it }
    change.deviceName?.takeIf { it.isNotBlank() }?.let { deviceName = it }
    change.videoHeight?.let { videoHeight = it }
    change.frameRate?.let { frameRate = it }
    // The torch is deliberately not stored: it is a thing the room is doing right now, not a
    // preference, and a camera that came back from a restart with the light on would be a
    // camera that woke the baby.
}

