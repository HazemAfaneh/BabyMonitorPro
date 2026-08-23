package com.hazemafaneh.babymonitorpro.capture

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.hazemafaneh.babymonitorpro.core.AndroidPlatformContext
import com.hazemafaneh.babymonitorpro.core.AudioConfig
import com.hazemafaneh.babymonitorpro.core.CaptureConfig
import com.hazemafaneh.babymonitorpro.core.nowMillis
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * CameraX `ImageAnalysis` in RGBA mode, re-encoded to JPEG per frame.
 *
 * Analysis rather than a capture use case because the app needs a continuous frame flow
 * it can throttle to the configured fps, not still images.
 */
actual class CameraController actual constructor(private val config: CaptureConfig) {

    private val context = AndroidPlatformContext.require()
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val lifecycleOwner = CaptureLifecycleOwner()

    private val frameFlow = MutableSharedFlow<ByteArray>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var lensFacing =
        if (config.useFrontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
    private var lastEmittedAt = 0L

    actual val frames: Flow<ByteArray> = frameFlow.asSharedFlow()

    actual suspend fun start() {
        val cameraProvider = awaitCameraProvider()
        withContext(Dispatchers.Main) {
            provider = cameraProvider
            lifecycleOwner.markResumed()
            bind(cameraProvider)
        }
    }

    actual suspend fun stop() {
        withContext(Dispatchers.Main) {
            provider?.unbindAll()
            lifecycleOwner.markDestroyed()
            provider = null
            camera = null
        }
        analysisExecutor.shutdown()
    }

    actual fun switchCamera() {
        val cameraProvider = provider ?: return
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        ContextCompat.getMainExecutor(context).execute {
            cameraProvider.unbindAll()
            bind(cameraProvider)
        }
    }

    actual fun setTorch(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }

    private fun bind(cameraProvider: ProcessCameraProvider) {
        val analysis = ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            // The stream is live: a late frame is worth less than the current one.
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analysis.setAnalyzer(analysisExecutor) { image -> onImage(image) }

        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        camera = runCatching {
            cameraProvider.bindToLifecycle(lifecycleOwner, selector, analysis)
        }.onFailure {
            // Swallowing this silently makes a dead camera indistinguishable from a working
            // one: the server still answers /stream, just with a body that never produces
            // a byte, and every viewer blames its own decoder.
            Log.e(TAG, "CameraX bindToLifecycle failed; no video will be produced", it)
        }.getOrNull()
    }

    private fun onImage(image: ImageProxy) {
        try {
            val now = nowMillis()
            if (now - lastEmittedAt < config.frameIntervalMs) return
            lastEmittedAt = now

            val bitmap = image.toBitmap().rotated(image.imageInfo.rotationDegrees)
            val output = ByteArrayOutputStream(bitmap.width * bitmap.height / 4)
            bitmap.compress(Bitmap.CompressFormat.JPEG, config.jpegQuality, output)
            bitmap.recycle()
            frameFlow.tryEmit(output.toByteArray())
        } catch (_: Exception) {
            // A dropped frame is not worth tearing the stream down for.
        } finally {
            image.close()
        }
    }

    private fun Bitmap.rotated(degrees: Int): Bitmap {
        if (degrees == 0) return this
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        if (rotated != this) recycle()
        return rotated
    }

    private suspend fun awaitCameraProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                { continuation.resume(future.get()) },
                ContextCompat.getMainExecutor(context),
            )
        }

    private companion object {
        const val TAG = "CameraController"
    }

    /** CameraX binds to a lifecycle; this one exists purely for the broadcast session. */
    private class CaptureLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry

        fun markResumed() {
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun markDestroyed() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}

actual class MicController actual constructor(private val config: AudioConfig) {

    private var recorder: AudioRecord? = null

    actual val samples: Flow<ByteArray> = flow {
        val source = recorder ?: return@flow
        val buffer = ByteArray(config.chunkBytes)
        while (true) {
            val read = source.read(buffer, 0, buffer.size)
            if (read <= 0) break
            emit(if (read == buffer.size) buffer.copyOf() else buffer.copyOf(read))
        }
    }.flowOn(Dispatchers.IO)

    actual suspend fun start() {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) return
        withContext(Dispatchers.IO) {
            recorder = runCatching {
                val minimum = AudioRecord.getMinBufferSize(
                    config.sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
                @Suppress("MissingPermission")
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    config.sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minimum, config.chunkBytes * 4),
                ).apply { startRecording() }
            }.getOrNull()
        }
    }

    actual suspend fun stop() {
        withContext(Dispatchers.IO) {
            runCatching {
                recorder?.stop()
                recorder?.release()
            }
            recorder = null
        }
    }
}

private fun hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(AndroidPlatformContext.require(), permission) ==
        PackageManager.PERMISSION_GRANTED

actual fun isCameraAvailable(): Boolean {
    val context = AndroidPlatformContext.applicationContext ?: return false
    val hasHardware = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    return hasHardware && hasPermission(Manifest.permission.CAMERA)
}

actual fun hasMultipleCameras(): Boolean {
    val context = AndroidPlatformContext.applicationContext ?: return false
    val packages = context.packageManager
    // FEATURE_CAMERA means a rear-facing one specifically, so this is "front and back".
    return packages.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT) &&
        packages.hasSystemFeature(PackageManager.FEATURE_CAMERA)
}

actual fun isMicrophoneAvailable(): Boolean {
    val context = AndroidPlatformContext.applicationContext ?: return false
    val hasHardware = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    return hasHardware && hasPermission(Manifest.permission.RECORD_AUDIO)
}
