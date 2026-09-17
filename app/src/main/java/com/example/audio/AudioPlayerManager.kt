package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.sqrt

class AudioPlayerManager {

    private val tag = "AudioPlayerManager"

    private var audioTrack: AudioTrack? = null
    private var sampleRate: Int = 16000
    private var minBufferSize: Int = 0

    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var playbackJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    @Synchronized
    fun init(rate: Int = 16000): Int {
        this.sampleRate = rate
        release()

        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize * 2, 4096)

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfig)
            .setEncoding(audioFormat)
            .build()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        try {
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(tag, "Failed to start AudioTrack", e)
        }

        startPlaybackLoop()
        return audioTrack?.audioSessionId ?: AudioManager.AUDIO_SESSION_ID_GENERATE
    }

    private fun startPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            val emptyBackoffMs = 10L
            while (isActive) {
                val chunk = audioQueue.poll()
                if (chunk != null && chunk.isNotEmpty()) {
                    _isPlaying.value = true
                    calculateAndEmitLevel(chunk)
                    val track = audioTrack
                    if (track != null && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        try {
                            track.write(chunk, 0, chunk.size)
                        } catch (e: Exception) {
                            Log.e(tag, "Error writing audio chunk", e)
                        }
                    }
                } else {
                    if (audioQueue.isEmpty()) {
                        _isPlaying.value = false
                        _audioLevel.value = 0f
                    }
                    kotlinx.coroutines.delay(emptyBackoffMs)
                }
            }
        }
    }

    fun enqueueAudio(pcmChunk: ByteArray) {
        if (pcmChunk.isEmpty()) return
        audioQueue.offer(pcmChunk)
    }

    /**
     * Instant Barge-in / Interruption:
     * Clears pending queue and flushes hardware playback buffer immediately.
     */
    @Synchronized
    fun interrupt() {
        audioQueue.clear()
        _audioLevel.value = 0f
        _isPlaying.value = false
        try {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.flush()
                    track.play()
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to flush AudioTrack on interrupt", e)
        }
    }

    private fun calculateAndEmitLevel(pcm: ByteArray) {
        var sum = 0.0
        val sampleCount = pcm.size / 2
        if (sampleCount == 0) return

        for (i in 0 until sampleCount) {
            val sample = ((pcm[i * 2 + 1].toInt() shl 8) or (pcm[i * 2].toInt() and 0xFF)).toShort()
            sum += sample * sample
        }
        val rms = sqrt(sum / sampleCount)
        // Normalize 0..32767 to 0..1
        val normalized = (rms / 12000.0).coerceIn(0.0, 1.0).toFloat()
        _audioLevel.value = normalized
    }

    @Synchronized
    fun release() {
        playbackJob?.cancel()
        playbackJob = null
        audioQueue.clear()
        _isPlaying.value = false
        _audioLevel.value = 0f

        try {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error releasing AudioTrack", e)
        }
        audioTrack = null
    }
}
