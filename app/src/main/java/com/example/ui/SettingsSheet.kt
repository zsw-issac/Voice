package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DiagnosticsInfo
import com.example.model.DuplexSettings
import com.example.model.ProtocolMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    currentSettings: DuplexSettings,
    diagnostics: DiagnosticsInfo,
    onDismiss: () -> Unit,
    onSave: (DuplexSettings) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var url by remember { mutableStateOf(currentSettings.serverUrl) }
    var sampleRate by remember { mutableIntStateOf(currentSettings.sampleRate) }
    var protocolMode by remember { mutableStateOf(currentSettings.protocolMode) }
    var enableAEC by remember { mutableStateOf(currentSettings.enableAEC) }
    var enableNS by remember { mutableStateOf(currentSettings.enableNoiseSuppressor) }
    var vadThreshold by remember { mutableFloatStateOf(currentSettings.vadThresholdDb) }
    var useSimulator by remember { mutableStateOf(currentSettings.useSimulator) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "全双工对接与参数配置",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }

            Text(
                text = "配置应用端与自建模型端的 WebSocket 通信与声学参数",
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)),
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            // Local Simulator Toggle Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (useSimulator) Color(0xFF1E293B) else Color(0xFF131D31)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "本地自测模拟演示模式",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (useSimulator) Color(0xFF38BDF8) else Color.White
                            )
                        )
                        Text(
                            text = "未启动后端服务时，开启此模式可直接在手机/模拟器上完整体验双工语音、麦克风拾音与实时插话打断",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = useSimulator,
                        onCheckedChange = { useSimulator = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF38BDF8),
                            checkedTrackColor = Color(0xFF0369A1)
                        ),
                        modifier = Modifier.testTag("simulator_switch")
                    )
                }
            }

            // Server URL configuration
            Text(
                text = "模型端 WebSocket 地址",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFCBD5E1)
                ),
                modifier = Modifier.padding(bottom = 6.dp)
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                enabled = !useSimulator,
                label = { Text("WebSocket URL (ws:// 或 wss://)") },
                placeholder = { Text("ws://192.168.1.100:8080/ws/duplex") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("server_url_input")
            )

            // Preset Quick Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { url = "ws://10.0.2.2:8080/ws/duplex" },
                    enabled = !useSimulator,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("模拟器宿主 (10.0.2.2)", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = { url = "ws://192.168.1.100:8080/ws/duplex" },
                    enabled = !useSimulator,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("局域网真机", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Protocol Mode
            Text(
                text = "传输协议格式",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFCBD5E1)
                ),
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = protocolMode == ProtocolMode.BINARY_PCM,
                    onClick = { protocolMode = ProtocolMode.BINARY_PCM },
                    label = { Text("二进制原始 PCM 流") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF1E293B),
                        selectedLabelColor = Color(0xFF38BDF8)
                    )
                )
                FilterChip(
                    selected = protocolMode == ProtocolMode.JSON_BASE64,
                    onClick = { protocolMode = ProtocolMode.JSON_BASE64 },
                    label = { Text("JSON (Base64)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF1E293B),
                        selectedLabelColor = Color(0xFF38BDF8)
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Audio Sample Rate
            Text(
                text = "音频采样率",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFCBD5E1)
                ),
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = sampleRate == 16000,
                    onClick = { sampleRate = 16000 },
                    label = { Text("16000 Hz (业界标准)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF1E293B),
                        selectedLabelColor = Color(0xFF38BDF8)
                    )
                )
                FilterChip(
                    selected = sampleRate == 24000,
                    onClick = { sampleRate = 24000 },
                    label = { Text("24000 Hz (高清)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF1E293B),
                        selectedLabelColor = Color(0xFF38BDF8)
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hardware Acoustic Echo Canceler (AEC)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "硬件回声消除 (AEC)",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (diagnostics.aecHardwareAvailable) "设备已支持" else "无硬件支持",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (diagnostics.aecHardwareAvailable) Color(0xFF4ADE80) else Color(0xFFF87171)
                                )
                            )
                        }
                        Text(
                            text = "全双工关键：防止扬声器声音被麦克风二次录入，消除自问自答",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Switch(
                        checked = enableAEC,
                        onCheckedChange = { enableAEC = it }
                    )
                }
            }

            // Noise Suppressor
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "降噪抑制 (Noise Suppressor)",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "过滤环境底噪，使语音识别更纯净",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Switch(
                        checked = enableNS,
                        onCheckedChange = { enableNS = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // VAD Barge-in Threshold
            Text(
                text = "本地插话打断灵敏度 (VAD 能量阈值: ${vadThreshold.toInt()} dB)",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFCBD5E1)
                )
            )
            Text(
                text = "当 AI 正在说话时，说话音量达到此阈值将立刻触发硬件打断并清空缓冲",
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)),
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )

            Slider(
                value = vadThreshold,
                onValueChange = { vadThreshold = it },
                valueRange = -55f..-20f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF38BDF8),
                    activeTrackColor = Color(0xFF0284C7)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Live Diagnostics Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "实时通信诊断",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• RTT 延迟: ${diagnostics.roundTripLatencyMs} ms", fontSize = 12.sp, color = Color(0xFFE2E8F0))
                    Text("• 上行发送: ${diagnostics.bytesSent / 1024} KB (${diagnostics.packetsSent} 包)", fontSize = 12.sp, color = Color(0xFFE2E8F0))
                    Text("• 下行接收: ${diagnostics.bytesReceived / 1024} KB (${diagnostics.packetsReceived} 包)", fontSize = 12.sp, color = Color(0xFFE2E8F0))
                    Text("• AEC 激活状态: ${if (diagnostics.aecEnabled) "已生效" else "未生效"}", fontSize = 12.sp, color = Color(0xFFE2E8F0))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        onSave(
                            currentSettings.copy(
                                serverUrl = url.trim(),
                                sampleRate = sampleRate,
                                protocolMode = protocolMode,
                                enableAEC = enableAEC,
                                enableNoiseSuppressor = enableNS,
                                vadThresholdDb = vadThreshold,
                                useSimulator = useSimulator
                            )
                        )
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_settings_button")
                ) {
                    Text("保存应用")
                }
            }
        }
    }
}
