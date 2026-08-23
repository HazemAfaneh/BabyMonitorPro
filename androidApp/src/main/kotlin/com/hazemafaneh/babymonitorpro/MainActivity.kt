package com.hazemafaneh.babymonitorpro

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.hazemafaneh.babymonitorpro.core.DeepLinks

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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
