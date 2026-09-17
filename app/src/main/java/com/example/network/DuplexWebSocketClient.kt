package com.example.network

import android.util.Base64
import android.util.Log
import com.example.model.ConnectionState
import com.example.model.ProtocolMode
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class DuplexWebSocketClient(
    private val onStateChanged: (ConnectionState, String?) -> Unit,
    private val onAudioChunkReceived: (ByteArray) -> Unit,
    private val onTranscriptReceived: (isUser: Boolean, text: String, isFinal: Boolean) -> Unit,
    private val onServerInterrupt: () -> Unit,
    private val onLatencyMeasured: (Long) -> Unit
) {
    private val tag = "DuplexWebSocketClient"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(5, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for streaming
        .build()

    private var webSocket: WebSocket? = null
    private var currentProtocolMode = ProtocolMode.BINARY_PCM

    val bytesSent = AtomicLong(0)
    val bytesReceived = AtomicLong(0)
    val packetsSent = AtomicLong(0)
    val packetsReceived = AtomicLong(0)

    private var lastPingTimeMs = 0L

    fun connect(url: String, protocolMode: ProtocolMode) {
        disconnect()
        currentProtocolMode = protocolMode
        onStateChanged(ConnectionState.CONNECTING, null)

        val request = try {
            Request.Builder().url(url).build()
        } catch (e: Exception) {
            onStateChanged(ConnectionState.ERROR, "非法 URL: ${e.message}")
            return
        }

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(tag, "WebSocket opened: ${response.message}")
                onStateChanged(ConnectionState.CONNECTED, null)

                // Send session initialization handshake
                sendSessionConfig()
                measurePing()
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                packetsReceived.incrementAndGet()
                bytesReceived.addAndGet(bytes.size.toLong())
                onAudioChunkReceived(bytes.toByteArray())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                packetsReceived.incrementAndGet()
                bytesReceived.addAndGet(text.toByteArray().size.toLong())
                handleJsonMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket closing: $code / $reason")
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket closed: $code / $reason")
                onStateChanged(ConnectionState.DISCONNECTED, reason)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(tag, "WebSocket failure: ${t.message}", t)
                onStateChanged(ConnectionState.ERROR, t.localizedMessage ?: "连接失败")
            }
        })
    }

    private fun handleJsonMessage(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val type = json.optString("type", json.optString("event", ""))

            when {
                // OpenAI / Doubao Realtime style audio delta
                type.contains("audio.delta") || type == "audio" -> {
                    val base64 = json.optString("delta", json.optString("audio", ""))
                    if (base64.isNotEmpty()) {
                        val pcm = Base64.decode(base64, Base64.NO_WRAP)
                        onAudioChunkReceived(pcm)
                    }
                }

                // AI transcript delta or final
                type.contains("transcript") || type.contains("text") || type == "reply" -> {
                    val text = json.optString("delta", json.optString("text", json.optString("transcript", "")))
                    val isFinal = json.optBoolean("final", false)
                    val isUser = json.optBoolean("is_user", false)
                    if (text.isNotEmpty()) {
                        onTranscriptReceived(isUser, text, isFinal)
                    }
                }

                // User speech recognition result from server VAD/ASR
                type.contains("input_audio_transcription") || type == "user_speech" -> {
                    val text = json.optString("transcript", json.optString("text", ""))
                    if (text.isNotEmpty()) {
                        onTranscriptReceived(true, text, true)
                    }
                }

                // Server detected user speaking and interrupted ongoing response
                type.contains("interrupted") || type == "cancel" -> {
                    onServerInterrupt()
                }

                // Heartbeat / pong response
                type == "pong" -> {
                    val rtt = System.currentTimeMillis() - lastPingTimeMs
                    onLatencyMeasured(rtt)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not parse message as JSON, treated as raw text: $jsonStr")
            onTranscriptReceived(false, jsonStr, false)
        }
    }

    fun sendAudioChunk(pcmData: ByteArray) {
        val ws = webSocket ?: return
        packetsSent.incrementAndGet()
        bytesSent.addAndGet(pcmData.size.toLong())

        if (currentProtocolMode == ProtocolMode.BINARY_PCM) {
            ws.send(pcmData.toByteString())
        } else {
            val base64 = Base64.encodeToString(pcmData, Base64.NO_WRAP)
            val json = JSONObject().apply {
                put("type", "input_audio_buffer.append")
                put("audio", base64)
            }
            ws.send(json.toString())
        }
    }

    /**
     * Send interrupt signal to server so it stops generating downstream audio/tokens.
     */
    fun sendInterrupt() {
        val ws = webSocket ?: return
        val json = JSONObject().apply {
            put("type", "response.cancel")
            put("event", "interrupt")
            put("timestamp", System.currentTimeMillis())
        }
        ws.send(json.toString())
    }

    private fun sendSessionConfig() {
        val ws = webSocket ?: return
        val json = JSONObject().apply {
            put("type", "session.update")
            put("session", JSONObject().apply {
                put("modalities", org.json.JSONArray().apply {
                    put("audio")
                    put("text")
                })
                put("input_audio_format", "pcm16")
                put("output_audio_format", "pcm16")
                put("voice", "gentle")
            })
        }
        ws.send(json.toString())
    }

    private fun measurePing() {
        lastPingTimeMs = System.currentTimeMillis()
        val json = JSONObject().apply {
            put("type", "ping")
            put("timestamp", lastPingTimeMs)
        }
        webSocket?.send(json.toString())
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (e: Exception) {
            Log.e(tag, "Error closing websocket", e)
        }
        webSocket = null
        onStateChanged(ConnectionState.DISCONNECTED, null)
    }
}
