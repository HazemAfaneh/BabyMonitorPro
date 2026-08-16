package com.hazemafaneh.babymonitorpro.audio

import com.hazemafaneh.babymonitorpro.core.AudioConfig

/**
 * TODO(platform): web audio playback is not implemented.
 *
 * The blocker is getting raw PCM from Kotlin/Wasm into WebAudio: a Float32Array has to be
 * built on the JS side, which needs either a browser-wrapper dependency (not in the
 * approved set) or an extra endpoint serving the same PCM as a chunked WAV that an
 * `<audio>` element could play the way `<img>` plays the MJPEG stream. Both are protocol
 * or dependency decisions rather than code to guess at, so the web viewer is video-only
 * for now and the audio toggle stays hidden there.
 */
actual fun createAudioPlayer(config: AudioConfig): AudioPlayer? = null
