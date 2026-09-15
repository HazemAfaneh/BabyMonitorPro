package com.hazemafaneh.babymonitorpro.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Whether this process currently holds [permission]. */
internal fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/**
 * The Activity behind a Compose `LocalContext`.
 *
 * Compose hands out a ContextThemeWrapper rather than the Activity itself, and both
 * `shouldShowRequestPermissionRationale` and a Settings intent that lands on the right task
 * need the real thing.
 */
internal tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * Whether the system would still draw a dialog for [permission].
 *
 * Android reports this as "should we explain ourselves first", which is true only in the
 * middle state: asked once, refused once. It is false both before the first ask and after
 * the refusal that switches the dialog off for good — so this answer is only worth reading
 * once we know a request has actually been through, which is what the callers track.
 */
internal fun Context.wouldShowDialog(permission: String): Boolean =
    activity()?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, permission) } ?: false

/**
 * Opens this app's page in Settings.
 *
 * The only route left once the system has stopped asking. NEW_TASK because the call can
 * arrive from a context that is not the Activity, and Android refuses a plain start then.
 */
internal fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

/**
 * Runs [block] on every resume, including the first.
 *
 * The permission screens need it because Settings is a round trip out of the process. A
 * parent who turns the camera back on there returns to a composition that saw nothing
 * happen, and without this it would still be telling them their camera is off.
 */
@Composable
internal fun OnResume(block: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val current by rememberUpdatedState(block)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) current()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
