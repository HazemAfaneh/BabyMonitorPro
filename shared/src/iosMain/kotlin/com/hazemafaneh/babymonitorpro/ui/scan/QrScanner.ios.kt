package com.hazemafaneh.babymonitorpro.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectZero
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

/** AVFoundation reads QR codes natively — no barcode dependency needed on iOS. */
actual val qrScanningSupported: Boolean = true

private enum class CameraAccess { PENDING, GRANTED, DENIED }

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun QrScanner(
    modifier: Modifier,
    onResult: (String) -> Unit,
    onUnavailable: () -> Unit,
) {
    val mediaType = remember { AVMediaTypeVideo ?: "vide" }
    var access by remember { mutableStateOf(currentAccess(mediaType)) }

    // Ask before building the session. Wiring up an input first only attaches a device that
    // delivers nothing until the user answers, and nothing re-runs after they tap Allow —
    // the scanner then stays blank until the screen is closed and reopened.
    DisposableEffect(mediaType) {
        if (access == CameraAccess.PENDING) {
            AVCaptureDevice.requestAccessForMediaType(mediaType) { granted ->
                // The callback lands on an arbitrary queue; Compose state is main-thread only.
                dispatch_async(dispatch_get_main_queue()) {
                    access = if (granted) CameraAccess.GRANTED else CameraAccess.DENIED
                }
            }
        }
        onDispose { }
    }

    // The screen around this needs to know, so it can offer manual entry rather than leave
    // the parent staring at an instruction the phone cannot follow.
    LaunchedEffect(access) {
        if (access == CameraAccess.DENIED) onUnavailable()
    }

    // Every branch paints black. Compose punches a transparent hole through its canvas for
    // an interop view, and the app window behind that hole is white — so any state that
    // does not draw a preview shows up as a white screen rather than as nothing.
    when (access) {
        CameraAccess.GRANTED -> ScannerPreview(modifier, mediaType, onResult)

        CameraAccess.PENDING -> Box(modifier.background(Color.Black))

        CameraAccess.DENIED -> Box(
            modifier.background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Camera access is off. Turn it on in Settings › BabyMonitor Pro, " +
                    "or type the address shown on the camera screen instead.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun ScannerPreview(
    modifier: Modifier,
    mediaType: String,
    onResult: (String) -> Unit,
) {
    val currentOnResult by rememberUpdatedState(onResult)

    val session = remember { AVCaptureSession() }
    val previewLayer = remember {
        AVCaptureVideoPreviewLayer(session = session).apply {
            videoGravity = AVLayerVideoGravityResizeAspectFill
        }
    }
    val container = remember { PreviewContainer(previewLayer) }

    val delegate = remember {
        object : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
            override fun captureOutput(
                output: AVCaptureOutput,
                didOutputMetadataObjects: List<*>,
                fromConnection: AVCaptureConnection,
            ) {
                val code = didOutputMetadataObjects
                    .filterIsInstance<AVMetadataMachineReadableCodeObject>()
                    .firstNotNullOfOrNull { it.stringValue }
                if (code != null) currentOnResult(code)
            }
        }
    }

    DisposableEffect(session) {
        val device = AVCaptureDevice.defaultDeviceWithMediaType(mediaType)
        val input = device?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, null) }

        if (input != null && session.canAddInput(input)) {
            session.sessionPreset = AVCaptureSessionPresetHigh
            session.addInput(input)

            val metadata = AVCaptureMetadataOutput()
            if (session.canAddOutput(metadata)) {
                session.addOutput(metadata)
                metadata.setMetadataObjectsDelegate(delegate, dispatch_get_main_queue())
                // Only valid once the output belongs to the session — before that the
                // supported-types list is empty and this assignment raises.
                metadata.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
            }

            // startRunning blocks until the camera is configured, which is long enough to
            // stall a frame or two of the UI if it runs on the main thread.
            dispatch_async(dispatch_get_global_queue(0, 0u)) { session.startRunning() }
        }

        onDispose {
            dispatch_async(dispatch_get_global_queue(0, 0u)) { session.stopRunning() }
        }
    }

    UIKitView(
        factory = { container },
        modifier = modifier,
        // Sizing is handled by the container's own layoutSubviews; an update callback fires
        // on recomposition, which is not when UIKit hands the view its bounds.
        update = { },
        // A preview has nothing to tap. Leaving it interactive lets the native view take
        // touches that belong to the Cancel button drawn over it.
        properties = UIKitInteropProperties(interactionMode = null),
    )
}

/**
 * A preview layer is not managed by auto layout, so it keeps whatever frame it was created
 * with — zero — unless something resizes it on every layout pass. A zero-sized layer draws
 * nothing, and the interop hole shows the white window behind it.
 */
@OptIn(ExperimentalForeignApi::class)
private class PreviewContainer(
    private val previewLayer: AVCaptureVideoPreviewLayer,
) : UIView(frame = CGRectZero.readValue()) {

    init {
        backgroundColor = UIColor.blackColor
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        previewLayer.setFrame(bounds)
        CATransaction.commit()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun currentAccess(mediaType: String): CameraAccess =
    when (AVCaptureDevice.authorizationStatusForMediaType(mediaType)) {
        AVAuthorizationStatusAuthorized -> CameraAccess.GRANTED
        AVAuthorizationStatusNotDetermined -> CameraAccess.PENDING
        else -> CameraAccess.DENIED
    }
