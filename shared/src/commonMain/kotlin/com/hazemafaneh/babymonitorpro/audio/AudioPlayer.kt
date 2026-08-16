package com.hazemafaneh.babymonitorpro.audio

import com.hazemafaneh.babymonitorpro.core.AudioConfig

/**
 * Plays the PCM arriving on `WS /audio`: 16-bit signed little-endian, mono, 16 kHz.
 *
 * Chunks are written as they arrive with no jitter buffer — a baby monitor wants the
 * present, not a smooth past, and 100 ms chunks over WiFi are steady enough in practice.
 */
interface AudioPlayer {
    fun start()
    fun write(pcm: ByteArray)
    fun stop()
}

/** Null where playback is not implemented; the viewer then hides the audio toggle. */
expect fun createAudioPlayer(config: AudioConfig): AudioPlayer?
