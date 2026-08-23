package com.hazemafaneh.babymonitorpro.server

import com.hazemafaneh.babymonitorpro.capture.CameraController
import com.hazemafaneh.babymonitorpro.capture.MicController
import com.hazemafaneh.babymonitorpro.capture.SyntheticVideoSource
import com.hazemafaneh.babymonitorpro.capture.isCameraAvailable
import com.hazemafaneh.babymonitorpro.capture.isMicrophoneAvailable
import com.hazemafaneh.babymonitorpro.capture.downscaleToGray
import com.hazemafaneh.babymonitorpro.capture.hasMultipleCameras
import com.hazemafaneh.babymonitorpro.capture.localIpv4Addresses
import com.hazemafaneh.babymonitorpro.client.ViewerClient
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import com.hazemafaneh.babymonitorpro.core.nowMillis
import com.hazemafaneh.babymonitorpro.detect.MotionDetector
import com.hazemafaneh.babymonitorpro.detect.SoundDetector
import com.hazemafaneh.babymonitorpro.discovery.createAdvertiser
import com.hazemafaneh.babymonitorpro.protocol.ControlMessage
import com.hazemafaneh.babymonitorpro.protocol.DeviceInfoResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class KtorBroadcaster : Broadcaster {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // replay = 1 so a viewer that connects mid-stream sees a picture immediately;
    // DROP_OLDEST so one slow viewer can never stall capture.
    private val frameFlow = MutableSharedFlow<ByteArray>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val audioFlow = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val eventFlow = MutableSharedFlow<ControlMessage>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _state = MutableStateFlow(BroadcastState())
    override val state = _state.asStateFlow()
    override val frames: Flow<ByteArray> = frameFlow.asSharedFlow()
    override val events: Flow<ControlMessage> = eventFlow.asSharedFlow()

    private var config: BroadcastConfig? = null
    private var camera: CameraController? = null
    private var mic: MicController? = null
    private val advertiser = createAdvertiser()
    private val motionDetector = MotionDetector()
    private val soundDetector = SoundDetector()
    private var videoJob: Job? = null
    private var audioJob: Job? = null
    private var startedAt: Long = 0

    private val server = BroadcastServer(
        scope = scope,
        frames = frameFlow.asSharedFlow(),
        audio = audioFlow.asSharedFlow(),
        outbound = eventFlow.asSharedFlow(),
        statusProvider = ::currentStatus,
        infoProvider = ::currentInfo,
        onViewerCountChanged = { count -> _state.update { it.copy(viewerCount = count) } },
        onControlMessage = ::handleControlMessage,
    )

    override suspend fun start(config: BroadcastConfig) {
        stop()
        this.config = config
        startedAt = nowMillis()

        motionDetector.sensitivity = config.motionSensitivity
        soundDetector.sensitivity = config.soundSensitivity
        motionDetector.reset()
        soundDetector.reset()

        val synthetic = config.useSyntheticVideo || !isCameraAvailable()
        videoJob = scope.launch {
            val source = if (synthetic) {
                SyntheticVideoSource(config.capture).frames
            } else {
                val controller = CameraController(config.capture)
                camera = controller
                controller.start()
                controller.frames
            }
            source.collect { jpeg ->
                frameFlow.emit(jpeg)
                inspectForMotion(jpeg)
            }
        }

        val micAvailable = isMicrophoneAvailable()
        if (micAvailable) {
            audioJob = scope.launch {
                val controller = MicController(config.audio)
                mic = controller
                controller.start()
                controller.samples.collect { chunk ->
                    audioFlow.emit(chunk)
                    inspectForSound(chunk)
                }
            }
        }

        server.start(config.port)
        advertiser?.start(config.deviceName, config.port)
        PlatformBroadcastSession.begin(config.deviceName)

        _state.value = BroadcastState(
            running = true,
            viewerCount = 0,
            addresses = localIpv4Addresses(),
            port = config.port,
            videoActive = true,
            audioActive = micAvailable,
            syntheticVideo = synthetic,
            usingFrontCamera = config.capture.useFrontCamera,
            canSwitchCamera = !synthetic && hasMultipleCameras(),
            motionSensitivity = config.motionSensitivity,
            soundSensitivity = config.soundSensitivity,
        )

        scope.launch { verifyListening(config) }
    }

    /**
     * CIO binds on the engine's own coroutine, so `start()` returning is not evidence that
     * anything is listening. Without this the camera screen prints a pairing address and a
     * QR code for a port that refuses every connection, and the viewer takes the blame.
     */
    private suspend fun verifyListening(config: BroadcastConfig) {
        val endpoint = CameraEndpoint(name = config.deviceName, host = LOOPBACK, port = config.port)
        val probe = ViewerClient(endpoint)
        try {
            val deadline = nowMillis() + SELF_CHECK_TIMEOUT_MILLIS
            while (nowMillis() < deadline) {
                if (runCatching { probe.fetchInfo() }.isSuccess) return
                delay(SELF_CHECK_RETRY_MILLIS)
            }
            _state.update {
                it.copy(
                    lastError = "Could not open port ${config.port}. " +
                        "Another app may be using it — stop it and start broadcasting again.",
                )
            }
        } finally {
            probe.close()
        }
    }

    override suspend fun stop() {
        videoJob?.cancelAndJoin()
        audioJob?.cancelAndJoin()
        videoJob = null
        audioJob = null
        camera?.stop()
        mic?.stop()
        camera = null
        mic = null
        advertiser?.stop()
        server.stop()
        PlatformBroadcastSession.end()
        _state.value = BroadcastState(running = false)
    }

    override fun requestStop() {
        // The broadcaster's own scope, not the caller's: this has to survive the caller.
        scope.launch { stop() }
    }

    override fun setSensitivity(motion: Int, sound: Int) {
        motionDetector.sensitivity = motion
        soundDetector.sensitivity = sound
        _state.update {
            it.copy(
                motionSensitivity = motion.coerceIn(0, 100),
                soundSensitivity = sound.coerceIn(0, 100),
            )
        }
        // Tell every connected viewer, not just the one that moved the slider.
        eventFlow.tryEmit(currentStatus())
    }

    override fun switchCamera() {
        val controller = camera ?: return
        controller.switchCamera()
        // The controller owns the lens; mirroring the flag here keeps the UI honest
        // without widening the expect class.
        _state.update { it.copy(usingFrontCamera = !it.usingFrontCamera) }
        eventFlow.tryEmit(currentStatus())
    }

    override fun setTorch(enabled: Boolean) {
        camera?.setTorch(enabled)
    }

    private fun inspectForMotion(jpeg: ByteArray) {
        val gray = downscaleToGray(jpeg, DETECT_WIDTH, DETECT_HEIGHT) ?: return
        if (motionDetector.submit(gray, nowMillis())) {
            eventFlow.tryEmit(
                ControlMessage.MotionEvent(
                    atMillis = nowMillis(),
                    intensity = motionDetector.lastRatio,
                ),
            )
        }
    }

    private fun inspectForSound(pcm: ByteArray) {
        if (soundDetector.submit(pcm, nowMillis())) {
            eventFlow.tryEmit(
                ControlMessage.SoundEvent(
                    atMillis = nowMillis(),
                    level = soundDetector.lastLevel,
                ),
            )
        }
    }

    private fun handleControlMessage(message: ControlMessage) {
        when (message) {
            is ControlMessage.SetSensitivity -> setSensitivity(message.motion, message.sound)
            else -> Unit
        }
    }

    private fun currentStatus(): ControlMessage.Status {
        val snapshot = _state.value
        return ControlMessage.Status(
            deviceName = config?.deviceName ?: "Camera",
            viewerCount = snapshot.viewerCount,
            streaming = snapshot.running,
            audioAvailable = snapshot.audioActive,
            motionSensitivity = snapshot.motionSensitivity,
            soundSensitivity = snapshot.soundSensitivity,
            uptimeMillis = if (startedAt == 0L) 0 else nowMillis() - startedAt,
        )
    }

    private fun currentInfo(): DeviceInfoResponse {
        val current = config
        return DeviceInfoResponse(
            deviceName = current?.deviceName ?: "Camera",
            streaming = _state.value.running,
            videoWidth = current?.capture?.width ?: 1280,
            videoHeight = current?.capture?.height ?: 720,
            audioSampleRate = current?.audio?.sampleRate ?: 16_000,
        )
    }

    private companion object {
        // The detector's working resolution, per the protocol notes in PROTOCOL.md.
        const val DETECT_WIDTH = 64
        const val DETECT_HEIGHT = 48

        const val LOOPBACK = "127.0.0.1"
        const val SELF_CHECK_TIMEOUT_MILLIS = 5_000L
        const val SELF_CHECK_RETRY_MILLIS = 250L
    }
}

actual fun createBroadcaster(): Broadcaster? = KtorBroadcaster()
