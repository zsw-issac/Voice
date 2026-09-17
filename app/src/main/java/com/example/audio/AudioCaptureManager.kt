package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
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
import kotlin.math.log10
import kotlin.math.sqrt

class AudioCaptureManager(
    private val onAudioChunk: (ByteArray) -> Unit,
    private val onSpeechDetected: () -> Unit
) {
    private val tag = "AudioCaptureManager"

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null

    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    var aecAvailable: Boolean = false
        private set
    var nsAvailable: Boolean = false
        private set
    var aecEnabled: Boolean = false
        private set

    var vadThresholdDb: Float = -38f
    private var consecutiveSpeechFrames = 0

    init {
        aecAvailable = AcousticEchoCanceler.isAvailable()
        nsAvailable = NoiseSuppressor.isAvailable()
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
    }

    @SuppressLint("MissingPermission")
    fun startCapture(
        sampleRate: Int = 16000,
        enableAEC: Boolean = true,
        enableNS: Boolean = true
    ): Boolean {
        stopCapture()

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize <= 0) {
            Log.e(tag, "Invalid minBufferSize: $minBufferSize")
            return false
        }

        // Buffer size for ~40ms chunks (16000 * 0.04 * 2 = 1280 bytes)
        val chunkSizeBytes = (sampleRate * 0.04 * 2).toInt()
        val internalBufferSize = maxOf(minBufferSize * 2, chunkSizeBytes * 4)

        try {
            // Using VOICE_COMMUNICATION source enables OS-level echo cancellation and call routing
            val audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION
            audioRecord = AudioRecord(
                audioSource,
                sampleRate,
                channelConfig,
                audioFormat,
                internalBufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(tag, "AudioRecord initialization failed")
                return false
            }

            val sessionId = audioRecord!!.audioSessionId

            // Attach Acoustic Echo Canceler if available and enabled
            if (enableAEC && AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                    enabled = true
                }
                aecEnabled = echoCanceler?.enabled == true
            }

            // Attach Noise Suppressor
            if (enableNS && NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                    enabled = true
                }
            }

            // Attach Automatic Gain Control
            if (AutomaticGainControl.isAvailable()) {
                gainControl = AutomaticGainControl.create(sessionId)?.apply {
                    enabled = true
                }
            }

            audioRecord?.startRecording()
            _isRecording.value = true

            startReadLoop(chunkSizeBytes)
            return true
        } catch (e: Exception) {
            Log.e(tag, "Error starting AudioRecord", e)
            stopCapture()
            return false
        }
    }

    private fun startReadLoop(chunkSizeBytes: Int) {
        captureJob = scope.launch {
            val buffer = ByteArray(chunkSizeBytes)
            while (isActive && _isRecording.value) {
                val record = audioRecord ?: break
                val readBytes = record.read(buffer, 0, buffer.size)

                if (readBytes > 0) {
                    if (_isMuted.value) {
                        _audioLevel.value = 0f
                        continue
                    }

                    val pcmData = buffer.copyOf(readBytes)
                    val (level, db) = calculateRmsAndDb(pcmData)
                    _audioLevel.value = level

                    // Local Voice Activity Detection (VAD)
                    if (db > vadThresholdDb) {
                        consecutiveSpeechFrames++
                        if (consecutiveSpeechFrames >= 2) {
                            onSpeechDetected()
                        }
                    } else {
                        consecutiveSpeechFrames = 0
                    }

                    onAudioChunk(pcmData)
                }
            }
        }
    }

    private fun calculateRmsAndDb(pcm: ByteArray): Pair<Float, Float> {
        var sum = 0.0
        val sampleCount = pcm.size / 2
        if (sampleCount == 0) return Pair(0f, -100f)

        for (i in 0 until sampleCount) {
            val sample = ((pcm[i * 2 + 1].toInt() shl 8) or (pcm[i * 2].toInt() and 0xFF)).toShort()
            sum += sample * sample
        }
        val rms = sqrt(sum / sampleCount)
        val normalized = (rms / 10000.0).coerceIn(0.0, 1.0).toFloat()
        val db = if (rms > 0) (20 * log10(rms / 32767.0)).toFloat() else -100f
        return Pair(normalized, db)
    }

    fun stopCapture() {
        _isRecording.value = false
        _audioLevel.value = 0f
        captureJob?.cancel()
        captureJob = null

        try {
            echoCanceler?.release()
            echoCanceler = null
            noiseSuppressor?.release()
            noiseSuppressor = null
            gainControl?.release()
            gainControl = null

            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
            audioRecord = null
        } catch (e: Exception) {
            Log.e(tag, "Error stopping AudioRecord", e)
        }
        aecEnabled = false
    }
}
