package com.hazemafaneh.babymonitorpro.audio

import com.hazemafaneh.babymonitorpro.core.AudioConfig
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

actual fun createAudioPlayer(config: AudioConfig): AudioPlayer? = JvmAudioPlayer(config)

private class JvmAudioPlayer(private val config: AudioConfig) : AudioPlayer {

    private var line: SourceDataLine? = null

    override fun start() {
        if (line != null) return
        line = runCatching {
            val format = AudioFormat(
                config.sampleRate.toFloat(),
                config.bitsPerSample,
                config.channels,
                true,
                false,
            )
            val info = DataLine.Info(SourceDataLine::class.java, format)
            (AudioSystem.getLine(info) as SourceDataLine).apply {
                open(format, config.chunkBytes * 4)
                start()
            }
        }.getOrNull()
    }

    override fun write(pcm: ByteArray) {
        runCatching { line?.write(pcm, 0, pcm.size) }
    }

    override fun stop() {
        runCatching {
            line?.drain()
            line?.stop()
            line?.close()
        }
        line = null
    }
}
