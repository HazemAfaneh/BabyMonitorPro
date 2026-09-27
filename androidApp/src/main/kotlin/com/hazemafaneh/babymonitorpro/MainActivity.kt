package com.hazemafaneh.babymonitorpro

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.hazemafaneh.babymonitorpro.core.DeepLinks
import com.hazemafaneh.babymonitorpro.ui.PopupPlayer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // The popup player is this activity drawn small, so it needs the activity itself —
        // shared code holds the application context and cannot enter picture-in-picture with
        // it. Released in onDestroy so a rotation does not leak the window.
        PopupPlayer.attach(this)

        // The bmpro scheme has been registered in the manifest all along, but nothing ever
        // read the intent — so a scanned pairing link, or a tapped alert notification,
        // opened the app at whatever screen it happened to be on. Both entry points end up
        // here; the launcher intent carries no data and is ignored.
        handle(intent)

        setContent {
            App()
        }
    }

    // launchMode is singleTask, so an alert tapped while the app is already up arrives here
    // rather than starting a second copy of the activity.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    override fun onDestroy() {
        PopupPlayer.detach(this)
        super.onDestroy()
    }

    /**
     * Home or recents pressed. On Android 11 and below this is the only chance to shrink into
     * the popup instead of being backgrounded; from 12 the params carry the auto-enter flag
     * and this is not delivered for the gesture swipe at all.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        PopupPlayer.onUserLeaving()
    }

    /**
     * Entering and leaving the popup. The live view reads this to strip itself back to the
     * picture — a rail and a bottom bar in a 200dp window cover the cot completely.
     */
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PopupPlayer.onModeChanged(isInPictureInPictureMode)
    }

    private fun handle(intent: Intent?) {
        val link = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data?.toString() ?: return
        DeepLinks.offerUri(link)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
