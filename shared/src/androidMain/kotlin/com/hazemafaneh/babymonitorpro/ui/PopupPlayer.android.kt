package com.hazemafaneh.babymonitorpro.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hazemafaneh.babymonitorpro.core.AndroidPlatformContext
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

/**
 * Android picture-in-picture, driven from the one activity this app has.
 *
 * Deliberately *not* a new window of our own. An overlay drawn with `SYSTEM_ALERT_WINDOW`
 * would look similar and would need a permission the parent grants from a full-screen system
 * warning, would have to be drawn and dragged by hand, and would keep an Activity-less
 * rendering path alive that nothing else in this app uses. Picture-in-picture is the same
 * activity, the same composition and the same stream, drawn small by the system — so nothing
 * about the connection, the audio pump or the detectors changes when it happens.
 */
actual object PopupPlayer {

    /**
     * Weak, because this object outlives the activity and holding it strongly would leak the
     * whole window every time the parent rotates the phone.
     */
    private var host: WeakReference<Activity>? = null
    private var aspect = 0f
    private var autoEnter = false

    /**
     * Snapshot state rather than a flow: it is written from the activity's own callback on
     * the main thread and read during composition, which is exactly what this is for.
     */
    internal var inPopup by mutableStateOf(false)
        private set

    /**
     * Resolved once from the system feature, which is what decides whether the call below
     * does anything. Absent on plenty of budget hardware and on many Android TV boxes, so
     * this is what the button's presence keys off — a control that reliably does nothing is
     * worse than no control.
     */
    actual val isSupported: Boolean by lazy {
        AndroidPlatformContext.applicationContext
            ?.packageManager
            ?.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) == true
    }

    /** Called by the activity, which is the only thing that can enter the popup. */
    fun attach(activity: Activity) {
        host = WeakReference(activity)
        inPopup = activity.isInPictureInPictureMode
    }

    fun detach(activity: Activity) {
        if (host?.get() === activity) {
            host = null
            inPopup = false
        }
    }

    /** From `Activity.onPictureInPictureModeChanged`. */
    fun onModeChanged(active: Boolean) {
        inPopup = active
    }

    /**
     * From `Activity.onUserLeaveHint` — home or recents pressed.
     *
     * Only the fallback path. From Android 12 the params carry `setAutoEnterEnabled`, which
     * the system honours for the gesture-navigation swipe as well; this callback is not
     * delivered for that swipe at all, which is why the flag is the primary mechanism and
     * this covers the versions that do not have it.
     */
    fun onUserLeaving() {
        if (!autoEnter) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        enter()
    }

    actual fun enter(): Boolean {
        if (!isSupported) return false
        val activity = host?.get() ?: return false
        if (activity.isInPictureInPictureMode) return true
        // Throws IllegalStateException when the activity is not in a state that can be put
        // into the popup, and returns false when the parent has turned the popup off for
        // this app in system settings. Neither is worth crashing over.
        return runCatching { activity.enterPictureInPictureMode(params()) }.getOrDefault(false)
    }

    actual fun setAspectRatio(aspect: Float) {
        if (aspect <= 0f || this.aspect == aspect) return
        this.aspect = aspect
        push()
    }

    actual fun setAutoEnter(enabled: Boolean) {
        if (autoEnter == enabled) return
        autoEnter = enabled
        push()
    }

    /**
     * Keeps the system's copy of the params current while the app is still in the foreground,
     * which is what makes the auto-enter flag and the window's shape take effect without the
     * parent pressing anything.
     */
    private fun push() {
        if (!isSupported || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val activity = host?.get() ?: return
        runCatching { activity.setPictureInPictureParams(params()) }
    }

    private fun params(): PictureInPictureParams = PictureInPictureParams.Builder()
        .setAspectRatio(rational())
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setAutoEnterEnabled(autoEnter)
                // The picture is a still frame between decodes, so letting the system scale
                // the last frame during the resize animation is exactly right — the
                // alternative is a black window for the length of the animation.
                setSeamlessResizeEnabled(true)
            }
        }
        .build()

    /**
     * The window's shape, as a ratio the system will accept.
     *
     * Android rejects anything outside roughly 1:2.39 .. 2.39:1 by throwing, so the camera's
     * real aspect is clamped rather than passed through. A phone filming a cot in portrait is
     * 9:16, which is inside the range; the clamp is there for the frame that arrives before
     * the first decode and for anything exotic.
     */
    private fun rational(): Rational {
        val ratio = (aspect.takeIf { it > 0f } ?: DEFAULT_ASPECT).coerceIn(MIN_ASPECT, MAX_ASPECT)
        return Rational((ratio * PRECISION).roundToInt(), PRECISION)
    }

    /** 16:9 — what the camera role sends until a frame has been measured. */
    private const val DEFAULT_ASPECT = 16f / 9f
    private const val MIN_ASPECT = 0.42f
    private const val MAX_ASPECT = 2.39f

    /** Denominator for the rational: three digits is finer than any window is wide. */
    private const val PRECISION = 1000
}

@Composable
actual fun isInPopupPlayer(): Boolean = PopupPlayer.inPopup
