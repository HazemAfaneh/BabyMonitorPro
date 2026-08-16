package com.hazemafaneh.babymonitorpro.audio

import com.hazemafaneh.babymonitorpro.core.AudioConfig
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatInt16
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.posix.memcpy

actual fun createAudioPlayer(config: AudioConfig): AudioPlayer? = IosAudioPlayer(config)

/**
 * AVAudioEngine with a player node fed straight from the socket's PCM chunks.
 * The session category is Playback so the ringer switch does not silence a monitor.
 */
@OptIn(ExperimentalForeignApi::class)
private class IosAudioPlayer(private val config: AudioConfig) : AudioPlayer {

    private val engine = AVAudioEngine()
    private val player = AVAudioPlayerNode()
    private val format = AVAudioFormat(
        commonFormat = AVAudioPCMFormatInt16,
        sampleRate = config.sampleRate.toDouble(),
        channels = config.channels.toUInt(),
        interleaved = true,
    )

    private var running = false

    override fun start() {
        if (running) return
        runCatching {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
            AVAudioSession.sharedInstance().setActive(true, null)

            engine.attachNode(player)
            engine.connect(player, engine.mainMixerNode, format)
            engine.startAndReturnError(null)
            player.play()
            running = true
        }
    }

    override fun write(pcm: ByteArray) {
        if (!running || pcm.isEmpty()) return
        val frames = pcm.size / (config.bytesPerSample * config.channels)
        if (frames == 0) return

        val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = frames.toUInt())
        buffer.frameLength = frames.toUInt()

        val destination = buffer.int16ChannelData?.get(0) ?: return
        pcm.usePinned { pinned ->
            memcpy(destination, pinned.addressOf(0), pcm.size.toULong())
        }
        player.scheduleBuffer(buffer, null)
    }

    override fun stop() {
        if (!running) return
        runCatching {
            player.stop()
            engine.stop()
            AVAudioSession.sharedInstance().setActive(false, null)
        }
        running = false
    }
}
