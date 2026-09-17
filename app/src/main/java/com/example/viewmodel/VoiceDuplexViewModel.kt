package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioCaptureManager
import com.example.audio.AudioPlayerManager
import com.example.audio.MockVoiceSimulator
import com.example.model.ConnectionState
import com.example.model.ConversationState
import com.example.model.DiagnosticsInfo
import com.example.model.DuplexSettings
import com.example.model.TranscriptItem
import com.example.network.DuplexWebSocketClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VoiceDuplexViewModel(application: Application) : AndroidViewModel(application) {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _conversationState = MutableStateFlow(ConversationState.IDLE)
    val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()

    private val _settings = MutableStateFlow(DuplexSettings())
    val settings: StateFlow<DuplexSettings> = _settings.asStateFlow()

    private val _transcripts = MutableStateFlow<List<TranscriptItem>>(emptyList())
    val transcripts: StateFlow<List<TranscriptItem>> = _transcripts.asStateFlow()

    private val _diagnostics = MutableStateFlow(DiagnosticsInfo())
    val diagnostics: StateFlow<DiagnosticsInfo> = _diagnostics.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val audioPlayer = AudioPlayerManager()

    private val audioCapture = AudioCaptureManager(
        onAudioChunk = { pcmChunk ->
            handleOutgoingAudio(pcmChunk)
        },
        onSpeechDetected = {
            handleUserSpeechActivity()
        }
    )

    private val webSocketClient = DuplexWebSocketClient(
        onStateChanged = { state, error ->
            _connectionState.value = state
            if (error != null) {
                _errorMessage.value = error
            }
            if (state == ConnectionState.CONNECTED) {
                _conversationState.value = ConversationState.LISTENING
            } else if (state == ConnectionState.DISCONNECTED || state == ConnectionState.ERROR) {
                _conversationState.value = ConversationState.IDLE
            }
            updateDiagnostics()
        },
        onAudioChunkReceived = { pcmChunk ->
            handleIncomingAudio(pcmChunk)
        },
        onTranscriptReceived = { isUser, text, isFinal ->
            handleTranscript(isUser, text, isFinal)
        },
        onServerInterrupt = {
            triggerBargeIn(fromServer = true)
        },
        onLatencyMeasured = { rtt ->
            _diagnostics.update { it.copy(roundTripLatencyMs = rtt) }
        }
    )

    private val mockSimulator = MockVoiceSimulator(
        onAudioChunk = { pcmChunk ->
            handleIncomingAudio(pcmChunk)
        },
        onTranscript = { isUser, text, isFinal ->
            handleTranscript(isUser, text, isFinal)
        }
    )

    val userAudioLevel: StateFlow<Float> = audioCapture.audioLevel
    val aiAudioLevel: StateFlow<Float> = audioPlayer.audioLevel
    val isMuted: StateFlow<Boolean> = audioCapture.isMuted
    val isRecording: StateFlow<Boolean> = audioCapture.isRecording

    private var userSilenceTimerJob: Job? = null

    init {
        audioCapture.vadThresholdDb = _settings.value.vadThresholdDb
        updateDiagnostics()

        // Observe player playback state to transition conversation state
        viewModelScope.launch {
            audioPlayer.isPlaying.collect { playing ->
                if (playing && _conversationState.value != ConversationState.SPEAKING) {
                    _conversationState.value = ConversationState.SPEAKING
                } else if (!playing && _conversationState.value == ConversationState.SPEAKING) {
                    _conversationState.value = ConversationState.LISTENING
                }
            }
        }
    }

    fun startSession() {
        val currentSettings = _settings.value
        _errorMessage.value = null

        // 1. Initialize streaming audio output
        audioPlayer.init(currentSettings.sampleRate)

        // 2. Start audio capture with AEC
        val captureStarted = audioCapture.startCapture(
            sampleRate = currentSettings.sampleRate,
            enableAEC = currentSettings.enableAEC,
            enableNS = currentSettings.enableNoiseSuppressor
        )

        if (!captureStarted) {
            _errorMessage.value = "麦克风采集启动失败，请检查录音权限"
            return
        }

        // 3. Connect to backend or start simulator
        if (currentSettings.useSimulator) {
            _connectionState.value = ConnectionState.CONNECTED
            _conversationState.value = ConversationState.LISTENING
            appendTranscript(
                TranscriptItem(
                    isUser = false,
                    text = "已启动本地全双工模拟演示。对麦克风说话，AI 将实时语音回应；当 AI 正在说话时，你随时说话即可打断！",
                    isFinal = true
                )
            )
        } else {
            webSocketClient.connect(currentSettings.serverUrl, currentSettings.protocolMode)
        }

        updateDiagnostics()
    }

    fun stopSession() {
        audioCapture.stopCapture()
        audioPlayer.release()
        mockSimulator.interrupt()
        webSocketClient.disconnect()
        _connectionState.value = ConnectionState.DISCONNECTED
        _conversationState.value = ConversationState.IDLE
        updateDiagnostics()
    }

    fun toggleMute() {
        val current = audioCapture.isMuted.value
        audioCapture.setMuted(!current)
    }

    /**
     * Trigger Barge-in / Interrupt:
     * Cuts off playing response, flushes buffer, notifies server/simulator.
     */
    fun triggerBargeIn(fromServer: Boolean = false) {
        if (_conversationState.value == ConversationState.SPEAKING || audioPlayer.isPlaying.value) {
            audioPlayer.interrupt()
            mockSimulator.interrupt()

            if (!fromServer && !_settings.value.useSimulator) {
                webSocketClient.sendInterrupt()
            }

            _conversationState.value = ConversationState.LISTENING
        }
    }

    private fun handleOutgoingAudio(pcmChunk: ByteArray) {
        if (_connectionState.value != ConnectionState.CONNECTED) return

        if (_settings.value.useSimulator) {
            // Simulator: track user speech and trigger simulated response upon pause
            resetUserSilenceTimer()
        } else {
            webSocketClient.sendAudioChunk(pcmChunk)
            updateDiagnostics()
        }
    }

    private fun handleIncomingAudio(pcmChunk: ByteArray) {
        if (_conversationState.value != ConversationState.SPEAKING) {
            _conversationState.value = ConversationState.SPEAKING
        }
        audioPlayer.enqueueAudio(pcmChunk)
        updateDiagnostics()
    }

    private fun handleUserSpeechActivity() {
        // If AI is speaking and user starts talking -> trigger Barge-in!
        if (_conversationState.value == ConversationState.SPEAKING) {
            triggerBargeIn(fromServer = false)
        }

        if (_settings.value.useSimulator) {
            mockSimulator.onUserSpeechDetected()
        }
    }

    private fun resetUserSilenceTimer() {
        userSilenceTimerJob?.cancel()
        userSilenceTimerJob = viewModelScope.launch {
            delay(1200) // 1.2s silence after speaking
            if (_conversationState.value != ConversationState.SPEAKING) {
                mockSimulator.onUserSpeechFinished()
            }
        }
    }

    private fun handleTranscript(isUser: Boolean, text: String, isFinal: Boolean) {
        if (text.isEmpty() && !isFinal) return

        _transcripts.update { current ->
            val list = current.toMutableList()
            if (list.isNotEmpty() && list.last().isUser == isUser && !list.last().isFinal) {
                // Append to ongoing message bubble
                val last = list.removeAt(list.size - 1)
                val newText = if (isFinal && text.isEmpty()) last.text else last.text + text
                list.add(last.copy(text = newText, isFinal = isFinal))
            } else {
                // Start new message
                list.add(TranscriptItem(isUser = isUser, text = text, isFinal = isFinal))
            }
            list
        }
    }

    private fun appendTranscript(item: TranscriptItem) {
        _transcripts.update { it + item }
    }

    fun clearTranscripts() {
        _transcripts.value = emptyList()
    }

    fun updateSettings(newSettings: DuplexSettings) {
        _settings.value = newSettings
        audioCapture.vadThresholdDb = newSettings.vadThresholdDb
        updateDiagnostics()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun updateDiagnostics() {
        _diagnostics.value = DiagnosticsInfo(
            aecHardwareAvailable = audioCapture.aecAvailable,
            aecEnabled = audioCapture.aecEnabled,
            nsHardwareAvailable = audioCapture.nsAvailable,
            roundTripLatencyMs = _diagnostics.value.roundTripLatencyMs,
            bytesSent = webSocketClient.bytesSent.get(),
            bytesReceived = webSocketClient.bytesReceived.get(),
            packetsSent = webSocketClient.packetsSent.get(),
            packetsReceived = webSocketClient.packetsReceived.get()
        )
    }

    override fun onCleared() {
        super.onCleared()
        stopSession()
    }
}
