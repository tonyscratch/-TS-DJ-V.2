package com.tonyscratch.tsdj.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import kotlin.math.max

/**
 * Runs the mixer on a high-priority audio thread and streams it to the speaker or headphones.
 * Starts with a very small buffer for low delay and grows it only if the phone can't keep up.
 */
class AudioEngine(context: Context) {
    val sampleRate: Int
    private val burst: Int
    val mixer: Mixer

    private var audioTrack: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile var bufferFrames = 0
        private set

    init {
        val am = context.getSystemService(AudioManager::class.java)
        sampleRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()?.takeIf { it in 22050..192000 } ?: 48000
        burst = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull()?.takeIf { it in 32..4096 } ?: 256
        mixer = Mixer(sampleRate)
    }

    /** Approximate output delay in milliseconds. */
    val latencyMs: Int get() = bufferFrames * 1000 / sampleRate

    fun start() {
        if (running) return
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(max(minBytes, burst * 2 * 4 * 8))
            .build()

        var frames = burst * 2
        track.setBufferSizeInFrames(frames)
        bufferFrames = track.bufferSizeInFrames
        track.play()
        audioTrack = track
        running = true

        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val block = burst
            val buf = FloatArray(block * 2)
            var lastUnderruns = track.underrunCount
            var counter = 0
            while (running) {
                mixer.render(buf, block)
                track.write(buf, 0, block * 2, AudioTrack.WRITE_BLOCKING)
                if (++counter >= 200) {
                    counter = 0
                    val u = track.underrunCount
                    if (u > lastUnderruns) {
                        lastUnderruns = u
                        if (frames + burst <= track.bufferCapacityInFrames) {
                            frames += burst
                            track.setBufferSizeInFrames(frames)
                            bufferFrames = track.bufferSizeInFrames
                        }
                    }
                }
            }
        }, "TSDj-audio").also { it.start() }
    }

    fun stop() {
        running = false
        try { thread?.join(500) } catch (_: InterruptedException) {}
        thread = null
        audioTrack?.let {
            try { it.pause(); it.flush(); it.stop() } catch (_: Exception) {}
            it.release()
        }
        audioTrack = null
    }
}
