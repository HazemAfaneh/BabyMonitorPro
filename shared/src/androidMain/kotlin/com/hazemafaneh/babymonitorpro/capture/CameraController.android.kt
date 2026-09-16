package com.hazemafaneh.babymonitorpro.capture

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
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
    private var analysis: ImageAnalysis? = null
    private var lensFacing =
        if (config.useFrontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
    private var lastEmittedAt = 0L

    /**
     * Same slack iOS applies, for the same reason. CameraX delivers on the sensor's clock,
     * not ours, and a frame that lands a millisecond inside the exact interval is thrown
     * away — at a 30 fps sensor and a 12 fps target that is every third frame skipped
     * *and* the one after it, which came out at 10 fps. A quarter interval absorbs the
     * jitter without letting a genuinely early frame through.
     */
    private val minEmitIntervalMs = config.frameIntervalMs - config.frameIntervalMs / 4

    actual val frames: Flow<ByteArray> = frameFlow.asSharedFlow()

    /**
     * Keeps [ImageAnalysis.setTargetRotation] pointed at how the device is physically lying.
     *
     * This is the fix for a picture that arrives on its side, and the reason it has to be the
     * *device's* orientation rather than the display's is two-fold. The activity declares
     * `configChanges="orientation|screenSize"`, so turning the device never recreates it and
     * CameraX never rebinds — leaving the target rotation frozen at whatever it was when the
     * broadcast started. And a camera propped on a shelf is very often a device with rotation
     * lock on, where the display orientation has stopped describing the world entirely.
     *
     * It also makes tablets work. `targetRotation` is expressed relative to the device's
     * *natural* orientation, which on a Galaxy Tab S8+ is landscape, not portrait — CameraX
     * combines it with the sensor's own mounting angle, so the same code that is correct on a
     * portrait-natural phone is correct here too. Reading the display and assuming a phone is
     * exactly what put the tablet's picture 90° out.
     */
    private val orientationListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            // A device lying flat has no meaningful orientation; keep the last good one
            // rather than snapping the picture round because it was picked up.
            if (orientation == ORIENTATION_UNKNOWN) return
            val rotation = when (orientation) {
                in 45 until 135 -> Surface.ROTATION_270
                in 135 until 225 -> Surface.ROTATION_180
                in 225 until 315 -> Surface.ROTATION_90
                else -> Surface.ROTATION_0
            }
            // Settable on a live use case: no rebind, no dropped frames, no torn-down server.
            analysis?.targetRotation = rotation
        }
    }

    actual suspend fun start() {
        val cameraProvider = awaitCameraProvider()
        withContext(Dispatchers.Main) {
            provider = cameraProvider
            lifecycleOwner.markResumed()
            bind(cameraProvider)
            if (orientationListener.canDetectOrientation()) orientationListener.enable()
        }
    }

    actual suspend fun stop() {
        withContext(Dispatchers.Main) {
            orientationListener.disable()
            provider?.unbindAll()
            lifecycleOwner.markDestroyed()
            provider = null
            camera = null
            analysis = null
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
        val useCase = ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            // The stream is live: a late frame is worth less than the current one.
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            // Without this, CaptureConfig's width and height were decoration: ImageAnalysis
            // with no selector falls back to CameraX's own 640x480 default, so a config
            // asking for 1280x720 produced a 4:3 frame that every viewer then letterboxed
            // into a band. NOT_FOUND_FALLBACK_CLOSEST_HIGHER_THEN_LOWER rather than an exact
            // match, because a device that cannot do exactly this size should give the
            // nearest thing it can rather than silently revert to the default.
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(config.width, config.height),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build(),
            )
            .build()

        useCase.setAnalyzer(analysisExecutor) { image -> onImage(image) }
        analysis = useCase

        // The requested lens, then the other one, then whatever this device has.
        //
        // Asking for a lens the device does not have throws `No available camera can be
        // found`, and the failure is silent in every way that matters: the server still
        // answers /stream, with a body that never produces a byte, and the camera screen sits
        // on "Starting" forever while the parent waits for a picture that cannot arrive.
        //
        // Plenty of hardware has one camera — tablets, TV boxes, an old handset with a broken
        // front module — and the app defaults to the front lens. On any of them this was the
        // difference between a working monitor and a dead one, for a preference that does not
        // matter nearly as much as having a picture at all.
        val preferred = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val fallbackLens = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        val alternatives = listOf(
            preferred,
            CameraSelector.Builder().requireLensFacing(fallbackLens).build(),
            // No lens requirement at all: bind to whatever exists.
            CameraSelector.Builder().build(),
        )

        camera = alternatives.firstNotNullOfOrNull { selector ->
            runCatching { cameraProvider.bindToLifecycle(lifecycleOwner, selector, useCase) }
                .onFailure { Log.w(TAG, "Lens unavailable, trying the next selector", it) }
                .getOrNull()
        }

        if (camera == null) {
            // Everything failed, which means there is genuinely no camera here. Said loudly,
            // because a silent one is indistinguishable from a working camera pointed at a
            // dark room.
            Log.e(TAG, "No camera could be bound; no video will be produced")
        } else if (camera?.cameraInfo?.lensFacing != lensFacing) {
            // The picture is coming from the other lens. Mirrored back into the flag so the
            // camera screen and the viewer's remote controls both say which one is live.
            lensFacing = camera?.cameraInfo?.lensFacing ?: lensFacing
        }
    }

    private fun onImage(image: ImageProxy) {
        try {
            val now = nowMillis()
            if (now - lastEmittedAt < minEmitIntervalMs) return
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

/**
 * Whether this device can actually record.
 *
 * The feature flag alone is not enough, and that is not a theoretical worry: an Android TV
 * box declares no `FEATURE_MICROPHONE` at all — a television has no built-in mic — while
 * quite happily recording from a USB or virtual input. The old check believed the flag, so
 * every viewer was told "this camera has no microphone available" about a camera whose
 * microphone worked.
 *
 * So the flag is treated as a hint, and the fallback is to ask the audio system directly:
 * build an AudioRecord at the configured format and see whether it initialises. That is the
 * only question that matters, it costs a few milliseconds, and it is asked once per broadcast
 * rather than per chunk.
 */
actual fun isMicrophoneAvailable(): Boolean {
    val context = AndroidPlatformContext.applicationContext ?: return false
    if (!hasPermission(Manifest.permission.RECORD_AUDIO)) return false
    if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) return true
    return canOpenMicrophone()
}

/** Opens a recorder, asks whether it came up, and closes it again. */
private fun canOpenMicrophone(): Boolean = runCatching {
    val config = AudioConfig()
    val minimum = AudioRecord.getMinBufferSize(
        config.sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
    )
    if (minimum <= 0) return@runCatching false
    @Suppress("MissingPermission")
    val probe = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        config.sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        maxOf(minimum, config.chunkBytes * 4),
    )
    val ready = probe.state == AudioRecord.STATE_INITIALIZED
    probe.release()
    ready
}.getOrDefault(false)
