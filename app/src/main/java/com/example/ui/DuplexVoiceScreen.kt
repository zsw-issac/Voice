package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.ConnectionState
import com.example.model.ConversationState
import com.example.model.TranscriptItem
import com.example.viewmodel.VoiceDuplexViewModel

@Composable
fun DuplexVoiceScreen(
    viewModel: VoiceDuplexViewModel
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val conversationState by viewModel.conversationState.collectAsState()
    val userLevel by viewModel.userAudioLevel.collectAsState()
    val aiLevel by viewModel.aiAudioLevel.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val transcripts by viewModel.transcripts.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            viewModel.startSession()
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(transcripts.size) {
        if (transcripts.isNotEmpty()) {
            listState.animateScrollToItem(transcripts.size - 1)
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF0A0F1D)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0A0F1D),
                            Color(0xFF0F172A),
                            Color(0xFF090D16)
                        )
                    )
                )
        ) {
            // 1. Top Header Bar
            TopBar(
                connectionState = connectionState,
                isSimulator = settings.useSimulator,
                onOpenSettings = { showSettings = true },
                onClearTranscripts = { viewModel.clearTranscripts() }
            )

            // 2. Status Badge & Capabilities Chip
            StatusBar(
                conversationState = conversationState,
                connectionState = connectionState,
                aecActive = diagnostics.aecEnabled,
                sampleRate = settings.sampleRate
            )

            // 3. Central Voice Orb Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.42f),
                contentAlignment = Alignment.Center
            ) {
                VoiceOrb(
                    sizeDp = 210.dp,
                    conversationState = conversationState,
                    userLevel = userLevel,
                    aiLevel = aiLevel
                )

                // Waveform energy indicator below orb
                if (connectionState == ConnectionState.CONNECTED) {
                    WaveformBar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        level = if (conversationState == ConversationState.SPEAKING) aiLevel else userLevel,
                        color = if (conversationState == ConversationState.SPEAKING) Color(0xFFC084FC) else Color(0xFF38BDF8)
                    )
                }
            }

            // 4. Live Dialogue Transcripts Subtitle Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.38f)
                    .padding(horizontal = 16.dp)
            ) {
                TranscriptCard(
                    transcripts = transcripts,
                    conversationState = conversationState,
                    connectionState = connectionState,
                    listState = listState
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 5. Bottom Interactive Control Deck
            ControlDeck(
                connectionState = connectionState,
                conversationState = conversationState,
                isMuted = isMuted,
                onToggleCall = {
                    if (connectionState == ConnectionState.CONNECTED) {
                        viewModel.stopSession()
                    } else {
                        if (!hasMicPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            viewModel.startSession()
                        }
                    }
                },
                onToggleMute = { viewModel.toggleMute() },
                onInterrupt = { viewModel.triggerBargeIn() }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showSettings) {
        SettingsSheet(
            currentSettings = settings,
            diagnostics = diagnostics,
            onDismiss = { showSettings = false },
            onSave = { updated ->
                viewModel.updateSettings(updated)
            }
        )
    }
}

@Composable
private fun TopBar(
    connectionState: ConnectionState,
    isSimulator: Boolean,
    onOpenSettings: () -> Unit,
    onClearTranscripts: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "VoiceLive",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = when (connectionState) {
                        ConnectionState.CONNECTED -> Color(0xFF065F46)
                        ConnectionState.CONNECTING -> Color(0xFF78350F)
                        ConnectionState.ERROR -> Color(0xFF7F1D1D)
                        ConnectionState.DISCONNECTED -> Color(0xFF334155)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = when (connectionState) {
                            ConnectionState.CONNECTED -> if (isSimulator) "本地模拟中" else "已连接模型"
                            ConnectionState.CONNECTING -> "正在握手..."
                            ConnectionState.ERROR -> "连接失败"
                            ConnectionState.DISCONNECTED -> "离线待命"
                        },
                        color = when (connectionState) {
                            ConnectionState.CONNECTED -> Color(0xFF34D399)
                            ConnectionState.CONNECTING -> Color(0xFFFBBF24)
                            ConnectionState.ERROR -> Color(0xFFF87171)
                            ConnectionState.DISCONNECTED -> Color(0xFF94A3B8)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Text(
                text = "应用端全双工实时语音交互系统",
                fontSize = 12.sp,
                color = Color(0xFF64748B)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onClearTranscripts,
                modifier = Modifier.testTag("clear_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = "清空记录",
                    tint = Color(0xFF94A3B8)
                )
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "服务配置",
                    tint = Color(0xFF38BDF8)
                )
            }
        }
    }
}

@Composable
private fun StatusBar(
    conversationState: ConversationState,
    connectionState: ConnectionState,
    aecActive: Boolean,
    sampleRate: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Conversation dynamic state pill
        Surface(
            color = when (conversationState) {
                ConversationState.SPEAKING -> Color(0xFF2E1065)
                ConversationState.LISTENING -> Color(0xFF083344)
                ConversationState.THINKING -> Color(0xFF451A03)
                ConversationState.IDLE -> Color(0xFF1E293B)
            },
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when (conversationState) {
                                ConversationState.SPEAKING -> Color(0xFFC084FC)
                                ConversationState.LISTENING -> Color(0xFF38BDF8)
                                ConversationState.THINKING -> Color(0xFFFBBF24)
                                ConversationState.IDLE -> Color(0xFF64748B)
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (connectionState == ConnectionState.CONNECTED) conversationState.label else "等待发起通话",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Hardware AEC badge
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AEC 回声消除: ${if (aecActive) "已激活" else "就绪"}",
                fontSize = 11.sp,
                color = if (aecActive) Color(0xFF34D399) else Color(0xFF64748B)
            )
            Text(
                text = " • ",
                fontSize = 11.sp,
                color = Color(0xFF475569)
            )
            Text(
                text = "${sampleRate / 1000}kHz 16-bit PCM",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
private fun WaveformBar(
    modifier: Modifier = Modifier,
    level: Float,
    color: Color
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val barCount = 7
        for (i in 0 until barCount) {
            val dist = kotlin.math.abs(i - barCount / 2)
            val scale = (1f - dist * 0.2f).coerceAtLeast(0.2f)
            val barHeight = (8 + level * 28 * scale).dp
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = 0.5f + level * 0.5f))
            )
        }
    }
}

@Composable
private fun TranscriptCard(
    transcripts: List<TranscriptItem>,
    conversationState: ConversationState,
    connectionState: ConnectionState,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E).copy(alpha = 0.85f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        if (transcripts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8).copy(alpha = 0.6f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (connectionState == ConnectionState.CONNECTED) {
                            "已就绪，请直接对麦克风说话\n模型端将实时生成语音，支持随时插话打断"
                        } else {
                            "点击下方「发起实时通话」开启双工会话\n支持在右上角设置中连接自建模型服务"
                        },
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transcripts, key = { it.id }) { item ->
                    TranscriptBubble(item = item)
                }
            }
        }
    }
}

@Composable
private fun TranscriptBubble(item: TranscriptItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (item.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (item.isUser) Color(0xFF0369A1) else Color(0xFF2E1065)
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (item.isUser) 16.dp else 4.dp,
                bottomEnd = if (item.isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = if (item.isUser) "我" else "AI 助手",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (item.isUser) Color(0xFFBAE6FD) else Color(0xFFE9D5FF)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.text,
                    fontSize = 14.sp,
                    color = Color.White,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun ControlDeck(
    connectionState: ConnectionState,
    conversationState: ConversationState,
    isMuted: Boolean,
    onToggleCall: () -> Unit,
    onToggleMute: () -> Unit,
    onInterrupt: () -> Unit
) {
    val isConnected = connectionState == ConnectionState.CONNECTED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Microphone Mute Button
        IconButton(
            onClick = onToggleMute,
            enabled = isConnected,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(if (isMuted) Color(0xFF991B1B) else Color(0xFF1E293B))
                .testTag("mute_button")
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (isMuted) "取消静音" else "麦克风静音",
                tint = if (isMuted) Color.White else Color(0xFF38BDF8)
            )
        }

        // 2. Main Call / Connect Button
        Button(
            onClick = onToggleCall,
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isConnected) Color(0xFFDC2626) else Color(0xFF059669)
            ),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
            modifier = Modifier.testTag("call_action_button")
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Default.CallEnd else Icons.Default.Call,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (isConnected) "结束实时通话" else "发起实时通话",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // 3. Barge-in / Interrupt Button
        IconButton(
            onClick = onInterrupt,
            enabled = isConnected && conversationState == ConversationState.SPEAKING,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    if (conversationState == ConversationState.SPEAKING) Color(0xFF7C3AED) else Color(0xFF1E293B)
                )
                .testTag("interrupt_button")
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "打断模型说话",
                tint = if (conversationState == ConversationState.SPEAKING) Color.White else Color(0xFF64748B)
            )
        }
    }
}
