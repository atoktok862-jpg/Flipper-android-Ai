package com.pocketmultitool.app.hardware.tools

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.sin

class ToneEngine {
    private val handler = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    private val sampleRate = 48000

    val isPlaying: Boolean get() = track?.playState == AudioTrack.PLAYSTATE_PLAYING

    fun play(frequency: Int, durationMs: Int = 800): String {
        stop()
        val safeFrequency = frequency.coerceIn(20, 20000)
        val frames = (sampleRate.toLong() * durationMs / 1000L).toInt().coerceAtLeast(sampleRate / 10)
        val samples = ShortArray(frames)
        val step = 2.0 * PI * safeFrequency / sampleRate
        for (i in samples.indices) {
            samples[i] = (sin(step * i) * 26000.0).toInt().toShort()
        }
        val bytes = samples.size * 2
        val created: AudioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, bytes, AudioTrack.MODE_STATIC)
        }
        created.write(samples, 0, samples.size)
        created.play()
        track = created
        handler.postDelayed({ stop() }, durationMs.toLong() + 120L)
        return "ТОН $safeFrequency Гц / $durationMs мс"
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        track?.let {
            try {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.stop()
            } catch (_: Exception) {}
            it.release()
        }
        track = null
    }
}
