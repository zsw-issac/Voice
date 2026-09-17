package com.example.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

class MockVoiceSimulator(
    private val onAudioChunk: (ByteArray) -> Unit,
    private val onTranscript: (isUser: Boolean, text: String, isFinal: Boolean) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var responseJob: Job? = null
    private var isSimulatingResponse = false

    private val simulatedAnswers = listOf(
        "你好！我已经接收到你的全双工语音流。在全双工模式下，你可以随时说话插话打断我，不需要等待我讲完。",
        "应用端目前已成功启动 AudioRecord 采集，并激活了硬件级声学回声消除（AEC），有效防止了扬声器自激啸叫。",
        "如果你在自己电脑上搭建了 WebSocket 服务，只需要在设置面板中填入你电脑局域网的 IP 和端口，即可实时连接你的大模型端！",
        "全双工的核心在于低延迟流式传输：应用端按 40 毫秒一包实时上报音频，服务端边思考边回传音频，AudioTrack 在 STREAM 模式下零延迟播放。",
        "刚才听到你说话了，我立刻响应。这就是类似豆包或 GPT-4o 实时语音的交互体验。"
    )
    private var answerIndex = 0

    fun onUserSpeechDetected() {
        if (isSimulatingResponse) {
            // User interrupted while AI was talking
            interrupt()
            return
        }
    }

    fun onUserSpeechFinished(sampleTranscript: String = "你好，听到我的声音了吗？") {
        if (isSimulatingResponse) return

        responseJob?.cancel()
        responseJob = scope.launch {
            // Show recognized user text
            onTranscript(true, sampleTranscript, true)
            delay(400) // simulated server latency (400ms)

            isSimulatingResponse = true
            val fullText = simulatedAnswers[answerIndex % simulatedAnswers.size]
            answerIndex++

            // Stream characters in chunks while generating soundwave PCM
            val chars = fullText.chunked(2)
            for (chunk in chars) {
                if (!isActive) break
                onTranscript(false, chunk, false)

                // Generate ~120ms of audio PCM tones corresponding to speech syllables
                val pcmChunk = generateToneChunk(sampleRate = 16000, durationMs = 120)
                onAudioChunk(pcmChunk)
                delay(110)
            }

            onTranscript(false, "", true)
            isSimulatingResponse = false
        }
    }

    /**
     * Synthesize natural-sounding PCM tones for voice emulation
     */
    private fun generateToneChunk(sampleRate: Int, durationMs: Int): ByteArray {
        val numSamples = (sampleRate * durationMs / 1000)
        val buffer = ByteArray(numSamples * 2)
        val freq1 = 280.0 + (Math.random() * 80.0) // Vocal fundamental frequency (F0)
        val freq2 = freq1 * 1.5 // Second harmonic

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = sin(Math.PI * i / numSamples) // smooth attack/decay
            val s1 = sin(2.0 * Math.PI * freq1 * t)
            val s2 = 0.5 * sin(2.0 * Math.PI * freq2 * t)
            val sampleVal = ((s1 + s2) * envelope * 9000.0).toInt().coerceIn(-32768, 32767).toShort()

            buffer[i * 2] = (sampleVal.toInt() and 0xFF).toByte()
            buffer[i * 2 + 1] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
        }
        return buffer
    }

    fun interrupt() {
        responseJob?.cancel()
        responseJob = null
        isSimulatingResponse = false
    }
}
