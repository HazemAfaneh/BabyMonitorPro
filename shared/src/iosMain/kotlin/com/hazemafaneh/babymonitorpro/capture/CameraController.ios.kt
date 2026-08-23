package com.hazemafaneh.babymonitorpro.capture

import com.hazemafaneh.babymonitorpro.core.AudioConfig
import com.hazemafaneh.babymonitorpro.core.CaptureConfig
import com.hazemafaneh.babymonitorpro.core.nowMillis
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import kotlinx.cinterop.value
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioConverter
import platform.AVFAudio.AVAudioConverterInputStatus
import platform.AVFAudio.AVAudioConverterInputStatus_HaveData
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatInt16
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.setActive
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDeviceDiscoverySession
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset1280x720
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoOrientation
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeLeft
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeRight
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoOrientationPortraitUpsideDown
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureTorchModeOff
import platform.AVFoundation.AVCaptureTorchModeOn
import platform.AVFoundation.hasTorch
import platform.AVFoundation.setTorchMode
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreImage.CIContext
import platform.CoreImage.CIImage
import platform.CoreImage.JPEGRepresentationOfImage
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMSampleBufferRef
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientation
import platform.ImageIO.kCGImageDestinationLossyCompressionQuality
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create
import platform.posix.memcpy

/**
 * AVCaptureSession with a video-data output, re-encoded to JPEG per frame by CoreImage.
 *
 * A photo output would give better JPEGs but only on request; the stream needs a steady
 * flow of frames, throttled here to the configured fps.
 */
@OptIn(ExperimentalForeignApi::class)
actual class CameraController actual constructor(private val config: CaptureConfig) {

    private val session = AVCaptureSession()
    private val output = AVCaptureVideoDataOutput()
    private val queue = dispatch_queue_create("com.hazemafaneh.babymonitorpro.capture", null)
    private val context = CIContext()
    private val colorSpace = CGColorSpaceCreateDeviceRGB()

    private val frameFlow = MutableSharedFlow<ByteArray>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var input: AVCaptureDeviceInput? = null
    private var device: AVCaptureDevice? = null
    private var useFront = config.useFrontCamera
    private var lastEmittedAt = 0L
    private var orientation: AVCaptureVideoOrientation = AVCaptureVideoOrientationPortrait

    /**
     * The camera is pinned to the configured rate (see [applyFrameRate]), but delivery
     * jitters either side of the interval. Comparing against the exact interval throws away
     * every frame that arrives a millisecond early, which silently halves the frame rate and
     * makes the picture lurch; a quarter-interval of slack absorbs the jitter without
     * letting a genuinely early frame through.
     */
    private val minEmitIntervalMs = config.frameIntervalMs - config.frameIntervalMs / 4

    actual val frames: Flow<ByteArray> = frameFlow.asSharedFlow()

    private val delegate = object : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
        override fun captureOutput(
            output: AVCaptureOutput,
            didOutputSampleBuffer: CMSampleBufferRef?,
            fromConnection: AVCaptureConnection,
        ) {
            val now = nowMillis()
            if (now - lastEmittedAt < minEmitIntervalMs) return
            lastEmittedAt = now

            val pixels = CMSampleBufferGetImageBuffer(didOutputSampleBuffer) ?: return
            val image = CIImage.imageWithCVImageBuffer(pixels)
            val options = mapOf<Any?, Any?>(
                kCGImageDestinationLossyCompressionQuality to
                    NSNumber(double = config.jpegQuality / 100.0),
            )
            val jpeg = context.JPEGRepresentationOfImage(image, colorSpace, options) ?: return
            frameFlow.tryEmit(jpeg.toByteArray())
        }
    }

    // `startRunning` and `stopRunning` block until the camera has actually spun up or down —
    // hundreds of milliseconds. Both are reached from the UI on some paths (stopping the
    // broadcast, restarting it after a settings change), and on the main thread that is a visible
    // freeze of the whole app, Compose included.
    actual suspend fun start() = withContext(Dispatchers.Default) {
        // Read on the main thread, where UIKit insists device orientation is read, and then
        // held for the life of the broadcast. A nursery camera is put down in one position
        // and left there; a stream that reorients itself because somebody nudged the phone
        // is worse than one that keeps the framing it was set up with.
        orientation = withContext(Dispatchers.Main) { currentVideoOrientation() }

        // One transaction: the session is not asked to re-plumb itself once per line.
        session.beginConfiguration()
        session.sessionPreset = AVCaptureSessionPreset1280x720
        attachInput()

        output.setSampleBufferDelegate(delegate, queue)
        output.alwaysDiscardsLateVideoFrames = true
        if (session.canAddOutput(output)) session.addOutput(output)
        applyOrientation()
        session.commitConfiguration()

        // After commit, not before: committing can pick a different active format, and the
        // frame duration is a property of whichever format ends up active.
        device?.let(::applyFrameRate)

        session.startRunning()
    }

    actual suspend fun stop() = withContext(Dispatchers.Default) {
        session.stopRunning()
        input?.let { session.removeInput(it) }
        input = null
        device = null
    }

    actual fun switchCamera() {
        useFront = !useFront
        // Swapping the input tears down and rebuilds the capture graph, which blocks for
        // long enough to drop frames on the floor. It is triggered by a button, so without
        // this hop it blocks the main thread. The delegate queue is the right place for it:
        // running there means no sample is delivered mid-reconfiguration.
        val target = queue ?: return
        dispatch_async(target) {
            session.beginConfiguration()
            input?.let { session.removeInput(it) }
            attachInput()
            applyOrientation()
            session.commitConfiguration()
            device?.let(::applyFrameRate)
        }
    }

    actual fun setTorch(enabled: Boolean) {
        val current = device ?: return
        if (!current.hasTorch) return
        runCatching {
            current.lockForConfiguration(null)
            current.setTorchMode(if (enabled) AVCaptureTorchModeOn else AVCaptureTorchModeOff)
            current.unlockForConfiguration()
        }
    }

    /**
     * Rotates the delivered buffers to match how the phone is being held.
     *
     * Without this the frames arrive in the sensor's own orientation — landscape, with no
     * regard for the device — so a phone stood upright in a nursery streams a picture lying
     * on its side. Android has always corrected for this by rotating each bitmap by the
     * analyser's `rotationDegrees`; iOS was sending whatever the camera handed over.
     *
     * Setting it on the connection means the rotation happens before the frame reaches this
     * code, so nothing downstream has to know about orientation at all.
     */
    private fun applyOrientation() {
        val connection = output.connectionWithMediaType(AVMediaTypeVideo ?: "vide") ?: return
        if (!connection.isVideoOrientationSupported()) return
        connection.videoOrientation = orientation
    }

    /**
     * Device orientation, not interface orientation: the app can be locked to portrait while
     * the phone itself is on its side, and it is the phone the camera is bolted to.
     *
     * The landscape cases cross over on purpose. `LandscapeLeft` describes which way the
     * device was rotated; `AVCaptureVideoOrientationLandscapeRight` describes which way the
     * image has to be turned to compensate, and those are opposites.
     */
    private fun currentVideoOrientation(): AVCaptureVideoOrientation =
        when (UIDevice.currentDevice.orientation) {
            UIDeviceOrientation.UIDeviceOrientationLandscapeLeft ->
                AVCaptureVideoOrientationLandscapeRight
            UIDeviceOrientation.UIDeviceOrientationLandscapeRight ->
                AVCaptureVideoOrientationLandscapeLeft
            UIDeviceOrientation.UIDeviceOrientationPortraitUpsideDown ->
                AVCaptureVideoOrientationPortraitUpsideDown
            // Face up, face down and unknown all land here. A phone flat on a table has no
            // meaningful camera orientation, and portrait is the one a parent propping it
            // against a cot will have been holding a moment earlier.
            else -> AVCaptureVideoOrientationPortrait
        }

    /**
     * Caps the sensor at the streaming rate instead of letting it run at 30 fps only for the
     * delegate to throw two frames in three away. The discarded frames are not free — the
     * ISP still processes every one of them — and on a phone left broadcasting for hours the
     * heat that buys nothing ends in thermal throttling, which is when delivery turns ragged.
     *
     * Only the *minimum* duration is set. Pinning the maximum too would stop the camera
     * lengthening its exposure in a dark room, and a dark room is the whole point of this app.
     */
    private fun applyFrameRate(target: AVCaptureDevice) {
        val duration = CMTimeMake(value = 1, timescale = config.fps)
        runCatching {
            target.lockForConfiguration(null)
            target.activeVideoMinFrameDuration = duration
            target.unlockForConfiguration()
        }
    }

    private fun attachInput() {
        val wanted = if (useFront) AVCaptureDevicePositionFront else AVCaptureDevicePositionBack
        val mediaType = AVMediaTypeVideo ?: "vide"
        val discovery = AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
            deviceTypes = listOf(AVCaptureDeviceTypeBuiltInWideAngleCamera),
            mediaType = mediaType,
            position = wanted,
        )
        val candidate = discovery.devices.filterIsInstance<AVCaptureDevice>().firstOrNull()
            ?: AVCaptureDevice.defaultDeviceWithMediaType(mediaType)
            ?: return

        val deviceInput = AVCaptureDeviceInput.deviceInputWithDevice(candidate, null) ?: return
        if (session.canAddInput(deviceInput)) {
            session.addInput(deviceInput)
            input = deviceInput
            device = candidate
        }
    }
}

/**
 * AVAudioEngine tap on the input node, converted down to the 16 kHz mono 16-bit PCM the
 * protocol carries. The hardware format is whatever the device feels like (usually 48 kHz
 * float), so the converter is not optional.
 */
@OptIn(ExperimentalForeignApi::class)
actual class MicController actual constructor(private val config: AudioConfig) {

    private val engine = AVAudioEngine()
    private val outputFormat = AVAudioFormat(
        commonFormat = AVAudioPCMFormatInt16,
        sampleRate = config.sampleRate.toDouble(),
        channels = config.channels.toUInt(),
        interleaved = true,
    )

    private val sampleFlow = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    actual val samples: Flow<ByteArray> = sampleFlow.asSharedFlow()

    actual suspend fun start() {
        runCatching {
            AVAudioSession.sharedInstance()
                .setCategory(AVAudioSessionCategoryPlayAndRecord, null)
            AVAudioSession.sharedInstance().setActive(true, null)

            val inputNode = engine.inputNode
            val inputFormat = inputNode.inputFormatForBus(0u)
            val converter = AVAudioConverter(fromFormat = inputFormat, toFormat = outputFormat)

            inputNode.installTapOnBus(
                bus = 0u,
                bufferSize = TAP_FRAMES,
                format = inputFormat,
            ) { buffer, _ ->
                if (buffer == null || converter == null) return@installTapOnBus

                val ratio = outputFormat.sampleRate / inputFormat.sampleRate
                val capacity = (buffer.frameLength.toDouble() * ratio).toUInt() + 1u
                val converted = AVAudioPCMBuffer(pCMFormat = outputFormat, frameCapacity = capacity)

                var delivered = false
                converter.convertToBuffer(converted, null) { _, status ->
                    // One shot per tap: hand the buffer over once, then report no more data.
                    status?.pointed?.value = AVAudioConverterInputStatus_HaveData
                    if (delivered) null else buffer.also { delivered = true }
                }

                val frames = converted.frameLength.toInt()
                if (frames > 0) {
                    val source = converted.int16ChannelData?.get(0)
                    if (source != null) {
                        val bytes = ByteArray(frames * config.bytesPerSample * config.channels)
                        bytes.usePinned { pinned ->
                            memcpy(pinned.addressOf(0), source, bytes.size.toULong())
                        }
                        sampleFlow.tryEmit(bytes)
                    }
                }
            }

            engine.startAndReturnError(null)
        }
    }

    // Same reasoning as the camera: tearing the audio engine down and handing the session
    // back are both blocking calls, and this is reached from the UI when the parent stops
    // broadcasting.
    actual suspend fun stop() = withContext(Dispatchers.Default) {
        runCatching {
            engine.inputNode.removeTapOnBus(0u)
            engine.stop()
            AVAudioSession.sharedInstance().setActive(false, null)
        }
        Unit
    }

    private companion object {
        const val TAP_FRAMES: UInt = 4096u
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).also { output ->
        output.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}

actual fun isCameraAvailable(): Boolean =
    AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo ?: "vide") != null

actual fun isMicrophoneAvailable(): Boolean = true

@OptIn(ExperimentalForeignApi::class)
actual fun hasMultipleCameras(): Boolean {
    val mediaType = AVMediaTypeVideo ?: "vide"
    fun countAt(position: Long) = AVCaptureDeviceDiscoverySession
        .discoverySessionWithDeviceTypes(
            deviceTypes = listOf(AVCaptureDeviceTypeBuiltInWideAngleCamera),
            mediaType = mediaType,
            position = position,
        )
        .devices.size

    return countAt(AVCaptureDevicePositionFront) > 0 && countAt(AVCaptureDevicePositionBack) > 0
}
