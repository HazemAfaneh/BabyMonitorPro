package com.hazemafaneh.babymonitorpro.audio

import com.hazemafaneh.babymonitorpro.core.AudioConfig
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive

actual fun createAudioPlayer(config: AudioConfig): AudioPlayer? = IosAudioPlayer(config)

/**
 * AVAudioEngine with a player node fed from the socket's PCM chunks.
 * The session category is Playback so the ringer switch does not silence a monitor.
 *
 * The wire format is interleaved 16-bit PCM, which AVAudioEngine will not accept on a node
 * connection: `connect` and `scheduleBuffer` want a *standard* format — deinterleaved
 * float32. Handing it anything else raises an Objective-C exception that Kotlin cannot
 * catch, or leaves the engine running silently. So every chunk is converted on the way in.
 */
@OptIn(ExperimentalForeignApi::class)
private class IosAudioPlayer(private val config: AudioConfig) : AudioPlayer {

    private val engine = AVAudioEngine()
    private val player = AVAudioPlayerNode()
    private val format = AVAudioFormat(
        standardFormatWithSampleRate = config.sampleRate.toDouble(),
        channels = config.channels.toUInt(),
    )

    private var running = false

    override fun start() {
        if (running) return
        runCatching {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
            AVAudioSession.sharedInstance().setActive(true, null)

            engine.attachNode(player)
            engine.connect(player, engine.mainMixerNode, format)
            engine.prepare()
            if (!engine.startAndReturnError(null)) return
            player.play()
            running = true
        }
    }

    override fun write(pcm: ByteArray) {
        if (!running || pcm.isEmpty()) return
        val channels = config.channels
        val bytesPerFrame = config.bytesPerSample * channels
        val frames = pcm.size / bytesPerFrame
        if (frames == 0) return

        val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = frames.toUInt())
        buffer.frameLength = frames.toUInt()

        val data = buffer.floatChannelData ?: return
        for (channel in 0 until channels) {
            val target = data[channel] ?: return
            for (i in 0 until frames) {
                val at = (i * channels + channel) * BYTES_PER_SAMPLE
                // Little-endian: low byte first. The high byte keeps its sign.
                val sample = (pcm[at + 1].toInt() shl 8) or (pcm[at].toInt() and 0xFF)
                target[i] = sample.toShort() / FULL_SCALE
            }
        }
        player.scheduleBuffer(buffer, null)
    }

    override fun stop() {
        if (!running) return
        running = false
        runCatching {
            player.stop()
            engine.stop()
            engine.disconnectNodeInput(engine.mainMixerNode)
            engine.detachNode(player)
            AVAudioSession.sharedInstance().setActive(false, null)
        }
    }

    private companion object {
        const val BYTES_PER_SAMPLE = 2
        const val FULL_SCALE = 32768f
    }
}
