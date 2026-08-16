package com.hazemafaneh.babymonitorpro.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.hazemafaneh.babymonitorpro.core.AudioConfig

actual fun createAudioPlayer(config: AudioConfig): AudioPlayer? = AndroidAudioPlayer(config)

private class AndroidAudioPlayer(private val config: AudioConfig) : AudioPlayer {

    private var track: AudioTrack? = null

    override fun start() {
        if (track != null) return
        track = runCatching {
            val minimum = AudioTrack.getMinBufferSize(
                config.sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(config.sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minimum, config.chunkBytes * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                .apply { play() }
        }.getOrNull()
    }

    override fun write(pcm: ByteArray) {
        runCatching { track?.write(pcm, 0, pcm.size) }
    }

    override fun stop() {
        runCatching {
            track?.pause()
            track?.flush()
            track?.release()
        }
        track = null
    }
}
