package com.hazemafaneh.babymonitorpro.server

import com.hazemafaneh.babymonitorpro.capture.CameraController
import com.hazemafaneh.babymonitorpro.capture.MicController
import com.hazemafaneh.babymonitorpro.capture.SyntheticVideoSource
import com.hazemafaneh.babymonitorpro.capture.isCameraAvailable
import com.hazemafaneh.babymonitorpro.capture.isMicrophoneAvailable
import com.hazemafaneh.babymonitorpro.capture.downscaleToGray
import com.hazemafaneh.babymonitorpro.capture.hasMultipleCameras
import com.hazemafaneh.babymonitorpro.capture.allIpv4Addresses
import com.hazemafaneh.babymonitorpro.capture.localIpv4Addresses
import com.hazemafaneh.babymonitorpro.discovery.Tailnet
import com.hazemafaneh.babymonitorpro.client.ViewerClient
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import com.hazemafaneh.babymonitorpro.core.nowMillis
import com.hazemafaneh.babymonitorpro.core.readBattery
import com.hazemafaneh.babymonitorpro.detect.MotionDetector
import com.hazemafaneh.babymonitorpro.detect.SoundDetector
import com.hazemafaneh.babymonitorpro.discovery.createAdvertiser
import com.hazemafaneh.babymonitorpro.notify.liveSessions
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
import kotlinx.coroutines.flow.StateFlow
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
    private val _soundLevel = MutableStateFlow(0f)
    override val soundLevel: StateFlow<Float> = _soundLevel
    private val _motionLevel = MutableStateFlow(0f)
    override val motionLevel: StateFlow<Float> = _motionLevel
    private var videoJob: Job? = null
    private var audioJob: Job? = null
    private var startedAt: Long = 0

    /** When a frame last arrived from the camera. The watchdog's only input. */
    private var lastFrameAt: Long = 0

    override var onRemoteSettings: ((ControlMessage.SetCameraSettings) -> Unit)? = null

    private val server = BroadcastServer(
        scope = scope,
        frames = frameFlow.asSharedFlow(),
        audio = audioFlow.asSharedFlow(),
        outbound = eventFlow.asSharedFlow(),
        statusProvider = ::currentStatus,
        infoProvider = ::currentInfo,
        onViewerCountChanged = { count ->
            _state.update { it.copy(viewerCount = count) }
            publishBroadcastSession()
        },
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
        startVideo(config, synthetic)

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
        // Driven from here rather than from the camera screen: the screen is disposed the
        // moment the parent opens Settings or backgrounds the app, and those are precisely
        // the moments this surface exists for. The server's lifetime is the honest one.
        liveSessions.startBroadcasting(config.deviceName)

        _state.value = BroadcastState(
            running = true,
            viewerCount = 0,
            // LAN addresses first, then any tailnet address this device holds. A parent
            // pairing a device that will watch from outside the house needs the 100.x
            // address, and it is not an address they can look up anywhere else on the phone.
            addresses = localIpv4Addresses() + allIpv4Addresses().filter(Tailnet::isTailnetAddress),
            port = config.port,
            videoActive = true,
            audioActive = micAvailable,
            syntheticVideo = synthetic,
            usingFrontCamera = config.capture.useFrontCamera,
            canSwitchCamera = !synthetic && hasMultipleCameras(),
            motionSensitivity = config.motionSensitivity,
            soundSensitivity = config.soundSensitivity,
        )
        publishBroadcastSession()

        scope.launch { verifyListening(config) }
        lastFrameAt = nowMillis()
        watchCapture(config, synthetic)

        // A status every couple of seconds while broadcasting.
        //
        // Status used to be sent only when something changed, which is right for everything
        // in it except the two live readings — and those are what a viewer's threshold
        // sliders draw their marks from. Two seconds is slow enough to be invisible on the
        // wire (one small JSON object per viewer) and quick enough that a mark moves while a
        // parent is watching the room it describes.
        scope.launch {
            while (true) {
                delay(LEVEL_BROADCAST_MILLIS)
                if (!_state.value.running) continue
                eventFlow.tryEmit(currentStatus())
            }
        }
    }

    /**
     * Opens the camera and pumps its frames, and can be called again to reopen it.
     *
     * Separate from [start] because the camera is not ours to keep. Locking the nursery phone
     * hands the whole camera stack to the system, and it comes back disconnected: the capture
     * session is closed underneath CameraX, the frame flow simply stops, and nothing throws —
     * the server goes on answering `/stream` with the last frame it had. That is the worst
     * possible failure for a baby monitor, because a frozen picture of a sleeping baby looks
     * exactly like a sleeping baby.
     *
     * So the pipeline is restartable, and [watchCapture] restarts it.
     */
    private fun startVideo(config: BroadcastConfig, synthetic: Boolean) {
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
                lastFrameAt = nowMillis()
                frameFlow.emit(jpeg)
                inspectForMotion(jpeg)
            }
        }
    }

    /**
     * Notices a camera that has stopped producing and opens it again.
     *
     * Frames stopping is not an error anyone reports — the flow just goes quiet — so the only
     * way to know is to watch the clock. [CAPTURE_STALL_MILLIS] is several times the slowest
     * frame interval the app offers, so a merely slow camera is never restarted; only a dead
     * one is.
     *
     * Restarting means tearing the controller down and building a new one, because a
     * disconnected CameraX session cannot be revived — rebinding to the same instance gets the
     * same closed device back.
     */
    private fun watchCapture(config: BroadcastConfig, synthetic: Boolean) {
        scope.launch {
            while (true) {
                delay(CAPTURE_CHECK_MILLIS)
                val state = _state.value
                if (!state.running || !state.videoActive) continue
                if (nowMillis() - lastFrameAt < CAPTURE_STALL_MILLIS) continue

                videoJob?.cancelAndJoin()
                camera?.stop()
                camera = null
                // Marked before the restart, so a camera that takes a moment to open is not
                // immediately declared stalled again.
                lastFrameAt = nowMillis()
                startVideo(config, synthetic)
            }
        }
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
            // Torn all the way down rather than left half-up. The capture pipeline was
            // running the whole time this probe was failing, so leaving it there burns the
            // camera and the battery to feed a socket nothing can reach — and the pairing
            // card would still be printing an address that only ever refuses.
            teardown()
            _state.value = BroadcastState(
                port = config.port,
                portUnavailable = true,
                lastError = "Port ${config.port} is already in use on this device.",
            )
            liveSessions.stopBroadcasting()
        } finally {
            probe.close()
        }
    }

    override suspend fun stop() {
        teardown()
        _state.value = BroadcastState(running = false)
        liveSessions.stopBroadcasting()
    }

    /**
     * Everything [stop] does except resetting the state, so the port-conflict path can tear
     * the pipeline down and then publish a fault instead of a blank slate.
     */
    private suspend fun teardown() {
        // A meter left holding the last level a stopped microphone heard is a meter that
        // says the room is noisy when nothing is listening to it.
        _soundLevel.value = 0f
        _motionLevel.value = 0f
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
    }

    override fun requestStop() {
        // The broadcaster's own scope, not the caller's: this has to survive the caller.
        scope.launch { stop() }
    }

    override fun retryOnPort(port: Int) {
        val previous = config ?: return
        // The broadcaster's scope for the same reason as requestStop: the tap that starts
        // this recomposes the screen that made it.
        scope.launch { start(previous.copy(port = port)) }
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
        // Mirrored into the state so a viewer's torch switch shows what the room is actually
        // doing rather than what it last asked for.
        _state.update { it.copy(torchOn = enabled) }
        eventFlow.tryEmit(currentStatus())
    }

    /**
     * Mirrors the current state onto the system's live surface.
     *
     * Same vocabulary the camera screen's status chip uses, because the two are read within
     * seconds of each other and disagreeing about what the camera is doing would be worse
     * than saying nothing.
     */
    private fun publishBroadcastSession() {
        val snapshot = _state.value
        if (!snapshot.running) return
        liveSessions.updateBroadcasting(
            statusLabel = when {
                snapshot.syntheticVideo -> "Test pattern"
                !snapshot.videoActive -> "Sound only"
                else -> "Broadcasting"
            },
            viewerCount = snapshot.viewerCount,
            address = snapshot.primaryAddress?.let { "$it:${snapshot.port}" },
        )
    }

    private fun inspectForMotion(jpeg: ByteArray) {
        val gray = downscaleToGray(jpeg, DETECT_WIDTH, DETECT_HEIGHT) ?: return
        val fired = motionDetector.submit(gray, nowMillis())
        // Published on every frame, not only on a firing one — the readings that did *not*
        // raise an alert are exactly the ones a parent is setting the threshold against.
        _motionLevel.value = motionDetector.lastRatio
        if (fired) {
            eventFlow.tryEmit(
                ControlMessage.MotionEvent(
                    atMillis = nowMillis(),
                    intensity = motionDetector.lastRatio,
                ),
            )
        }
    }

    private fun inspectForSound(pcm: ByteArray) {
        val fired = soundDetector.submit(pcm, nowMillis())
        // Published on every chunk, not only on a firing one: the meter's job is to show the
        // parent what the room sounds like while they set the threshold against it, which is
        // precisely the levels that did *not* raise an alert.
        _soundLevel.value = soundDetector.lastLevel
        if (fired) {
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
            is ControlMessage.SetCameraSettings -> applyRemoteSettings(message)
            else -> Unit
        }
    }

    /**
     * A viewer changing this camera's settings from the other end of the house.
     *
     * Null means "leave it alone", so a parent turning the torch on does not have to restate
     * the sensitivity they set last week. Everything that can be applied without touching the
     * capture pipeline is applied immediately; geometry and frame rate need the camera
     * rebound, which is a second of black picture, so they are done last and only when they
     * actually changed.
     *
     * Every path ends in a fresh Status to *all* viewers, because two parents watching the
     * same cot must not disagree about which way the lens is pointing.
     */
    private fun applyRemoteSettings(message: ControlMessage.SetCameraSettings) {
        val current = config ?: return

        // Persisted before it is applied, so a change survives the restart that some of these
        // require — and so this device's own settings screen agrees with what the parent just
        // did from the other room.
        onRemoteSettings?.invoke(message)

        if (message.motionSensitivity != null || message.soundSensitivity != null) {
            setSensitivity(
                message.motionSensitivity ?: _state.value.motionSensitivity,
                message.soundSensitivity ?: _state.value.soundSensitivity,
            )
        }
        message.useFrontCamera?.let { wanted ->
            if (wanted != _state.value.usingFrontCamera) switchCamera()
        }
        message.torchOn?.let { wanted ->
            setTorch(wanted)
            _state.update { it.copy(torchOn = wanted) }
        }

        val renamed = message.deviceName?.takeIf { it.isNotBlank() && it != current.deviceName }
        val capture = current.capture
        val geometry = capture.copy(
            width = message.videoWidth ?: capture.width,
            height = message.videoHeight ?: capture.height,
            fps = message.frameRate ?: capture.fps,
            // Keep whichever lens is live now, not the one the config was built with.
            useFrontCamera = _state.value.usingFrontCamera,
        )

        if (geometry != capture || renamed != null) {
            // A restart, because capture geometry is fixed when the camera binds and the
            // advertised name is fixed when mDNS registers. The viewer that asked for this
            // knows a blink is coming; the others get their Status either way.
            scope.launch {
                start(current.copy(deviceName = renamed ?: current.deviceName, capture = geometry))
            }
            return
        }

        eventFlow.tryEmit(currentStatus())
    }

    private fun currentStatus(): ControlMessage.Status {
        val snapshot = _state.value
        val battery = readBattery()
        val capture = config?.capture
        return ControlMessage.Status(
            deviceName = config?.deviceName ?: "Camera",
            viewerCount = snapshot.viewerCount,
            streaming = snapshot.running,
            audioAvailable = snapshot.audioActive,
            motionSensitivity = snapshot.motionSensitivity,
            soundSensitivity = snapshot.soundSensitivity,
            uptimeMillis = if (startedAt == 0L) 0 else nowMillis() - startedAt,
            // Read fresh on every status rather than cached: a status is sent on connect and
            // whenever something changes, which is exactly when a viewer wants a current
            // number — and reading a battery percentage costs a system property lookup.
            batteryPercent = battery.percent,
            charging = battery.charging,
            usingFrontCamera = snapshot.usingFrontCamera,
            canSwitchCamera = snapshot.canSwitchCamera,
            torchOn = snapshot.torchOn,
            videoWidth = capture?.width ?: 1280,
            videoHeight = capture?.height ?: 720,
            frameRate = capture?.fps ?: 12,
            soundLevel = if (snapshot.audioActive) _soundLevel.value else -1f,
            motionLevel = if (snapshot.videoActive) _motionLevel.value else -1f,
        )
    }

    private fun currentInfo(): DeviceInfoResponse {
        val current = config
        val battery = readBattery()
        return DeviceInfoResponse(
            deviceName = current?.deviceName ?: "Camera",
            streaming = _state.value.running,
            videoWidth = current?.capture?.width ?: 1280,
            videoHeight = current?.capture?.height ?: 720,
            audioSampleRate = current?.audio?.sampleRate ?: 16_000,
            batteryPercent = battery.percent,
            charging = battery.charging,
        )
    }

    private companion object {
        // The detector's working resolution, per the protocol notes in PROTOCOL.md.
        const val DETECT_WIDTH = 64
        const val DETECT_HEIGHT = 48

        const val LOOPBACK = "127.0.0.1"
        const val SELF_CHECK_TIMEOUT_MILLIS = 5_000L
        const val SELF_CHECK_RETRY_MILLIS = 250L
        const val LEVEL_BROADCAST_MILLIS = 2_000L

        /**
         * Six seconds without a frame is a dead camera. The slowest rate the app offers is
         * 8 fps, so this is roughly fifty missed frames — far outside anything a working
         * camera does, and quick enough that a parent glancing over sees a picture rather
         * than a still.
         */
        const val CAPTURE_STALL_MILLIS = 6_000L
        const val CAPTURE_CHECK_MILLIS = 2_000L
    }
}

actual fun createBroadcaster(): Broadcaster? = KtorBroadcaster()
