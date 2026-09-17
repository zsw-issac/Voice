package com.example.model

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

enum class ConversationState(val label: String) {
    IDLE("等待就绪"),
    LISTENING("正在聆听..."),
    THINKING("思考中..."),
    SPEAKING("正在回应 (随时说话打断)")
}

enum class ProtocolMode(val displayName: String, val description: String) {
    BINARY_PCM("二进制 PCM 流", "直接发送原始 PCM 音频块，低延迟，推荐自定义服务使用"),
    JSON_BASE64("JSON (Base64)", "OpenAI/豆包 Realtime 规范，JSON 包装 Base64 音频块")
}

data class DuplexSettings(
    val serverUrl: String = "ws://10.0.2.2:8080/ws/duplex",
    val sampleRate: Int = 16000,
    val protocolMode: ProtocolMode = ProtocolMode.BINARY_PCM,
    val enableAEC: Boolean = true,
    val enableNoiseSuppressor: Boolean = true,
    val vadThresholdDb: Float = -38f, // dB threshold to trigger barge-in
    val useSimulator: Boolean = false // Test without external server
)

data class TranscriptItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val isFinal: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class DiagnosticsInfo(
    val aecHardwareAvailable: Boolean = false,
    val aecEnabled: Boolean = false,
    val nsHardwareAvailable: Boolean = false,
    val roundTripLatencyMs: Long = 0,
    val bytesSent: Long = 0,
    val bytesReceived: Long = 0,
    val packetsSent: Long = 0,
    val packetsReceived: Long = 0
)
