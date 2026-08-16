package com.hazemafaneh.babymonitorpro.ui.scan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.CoreGraphics.CGRectZero
import platform.QuartzCore.CATransaction
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

/** AVFoundation reads QR codes natively — no barcode dependency needed on iOS. */
actual val qrScanningSupported: Boolean = true

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun QrScanner(
    modifier: Modifier,
    onResult: (String) -> Unit,
) {
    val currentOnResult by rememberUpdatedState(onResult)

    val session = remember { AVCaptureSession() }
    val previewLayer = remember { AVCaptureVideoPreviewLayer(session = session) }
    val delegate = remember {
        object : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
            override fun captureOutput(
                output: platform.AVFoundation.AVCaptureOutput,
                didOutputMetadataObjects: List<*>,
                fromConnection: platform.AVFoundation.AVCaptureConnection,
            ) {
                val code = didOutputMetadataObjects
                    .filterIsInstance<AVMetadataMachineReadableCodeObject>()
                    .firstNotNullOfOrNull { it.stringValue }
                if (code != null) currentOnResult(code)
            }
        }
    }

    val container = remember { UIView(frame = CGRectZero.readValue()) }

    DisposableEffect(session) {
        val mediaType = AVMediaTypeVideo ?: "vide"
        val device = AVCaptureDevice.defaultDeviceWithMediaType(mediaType)
        val input = device?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, null) }

        if (input != null && session.canAddInput(input)) {
            session.sessionPreset = AVCaptureSessionPresetHigh
            session.addInput(input)

            val metadata = AVCaptureMetadataOutput()
            if (session.canAddOutput(metadata)) {
                session.addOutput(metadata)
                metadata.setMetadataObjectsDelegate(delegate, dispatch_get_main_queue())
                metadata.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
            }

            previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
            container.layer.addSublayer(previewLayer)
            session.startRunning()
        }

        onDispose {
            session.stopRunning()
            previewLayer.removeFromSuperlayer()
        }
    }

    UIKitView(
        factory = { container },
        modifier = modifier,
        update = { view ->
            // The preview layer is not auto-laid-out; keep it on the view's bounds without
            // animating every resize.
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            previewLayer.frame = view.bounds
            CATransaction.commit()
        },
    )
}
