package com.hazemafaneh.babymonitorpro.ui.video

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import com.hazemafaneh.babymonitorpro.core.Bmp
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import com.hazemafaneh.babymonitorpro.core.nowMillis

// Minimal hand-written DOM externals — the app needs four properties, not a wrapper
// dependency, and these declarations compile for both js and wasmJs.
private external interface DomStyle {
    var position: String
    var left: String
    var top: String
    var width: String
    var height: String
    var objectFit: String
    var zIndex: String
    var pointerEvents: String
    var background: String
}

private external interface DomImage {
    var src: String
    val style: DomStyle
    fun remove()
}

private external interface DomBody {
    fun appendChild(child: DomImage)
}

private external interface DomDocument {
    /** Typed as DomImage because this call site only ever creates <img> elements. */
    fun createElement(tagName: String): DomImage
    val body: DomBody
}

private external val document: DomDocument

/**
 * The browser renders the stream itself: an `<img>` pointed at `/stream` decodes
 * `multipart/x-mixed-replace` natively, which no amount of wasm decoding would match.
 * The element is layered over the Compose canvas and tracks the composable's bounds.
 */
@Composable
actual fun MjpegVideo(
    endpoint: CameraEndpoint,
    modifier: Modifier,
    maxFps: Int?,
    onStatus: (VideoStatus) -> Unit,
    onFrame: (Long) -> Unit,
    onError: (String?) -> Unit,
    // The <img> owns decoding, so the frame's dimensions never pass through Kotlin here.
    // The browser aspect-fits it with object-fit anyway, inside a band the layout already
    // reserves — so nothing downstream needs the number.
    onAspectRatio: (Float) -> Unit,
) {
    val density = LocalDensity.current
    var left by remember { mutableStateOf(0f) }
    var top by remember { mutableStateOf(0f) }
    var width by remember { mutableStateOf(0f) }
    var height by remember { mutableStateOf(0f) }

    // The browser decodes the multipart stream itself, so the rate can only be asked for —
    // which is exactly what the query parameter is for.
    val url = remember(endpoint.id, maxFps) {
        buildString {
            append(endpoint.baseUrl).append(Bmp.PATH_STREAM)
            if (maxFps != null) append('?').append(Bmp.QUERY_FPS).append('=').append(maxFps)
        }
    }

    val element = remember(url) {
        document.createElement("img").also { image ->
            image.style.position = "fixed"
            image.style.objectFit = "contain"
            // Above the Compose canvas, which is not transparent — hence the live view
            // reserving a band for the video rather than drawing controls underneath it.
            image.style.zIndex = "1"
            // The Compose canvas keeps every gesture; the image is purely a picture.
            image.style.pointerEvents = "none"
            image.style.background = "#000000"
            image.src = url
        }
    }

    DisposableEffect(element) {
        document.body.appendChild(element)
        onStatus(VideoStatus.CONNECTING)
        onDispose { element.remove() }
    }

    LaunchedEffect(element, left, top, width, height) {
        element.style.left = "${left / density.density}px"
        element.style.top = "${top / density.density}px"
        element.style.width = "${width / density.density}px"
        element.style.height = "${height / density.density}px"
        if (width > 0f && height > 0f) {
            onStatus(VideoStatus.LIVE)
            // The <img> decodes on its own; the browser owns any transport error, so there
            // is never a message to hand up here.
            onError(null)
            onFrame(nowMillis())
        }
    }

    Box(
        modifier.onGloballyPositioned { coordinates ->
            val position = coordinates.positionInWindow()
            left = position.x
            top = position.y
            width = coordinates.size.width.toFloat()
            height = coordinates.size.height.toFloat()
        },
    )
}

/** Web never decodes frames itself — see [MjpegVideo]. */
actual fun decodeJpegFrame(bytes: ByteArray, maxWidth: Int): ImageBitmap? = null

actual val videoRendersBehindUi: Boolean = true
