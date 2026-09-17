package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.ConversationState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VoiceOrb(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 220.dp,
    conversationState: ConversationState,
    userLevel: Float,
    aiLevel: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_rotation")

    // Smooth continuous ambient rotation
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Breathing pulse
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    // Smooth transition of audio response scale
    val activeLevel = if (conversationState == ConversationState.SPEAKING) aiLevel else userLevel
    val smoothAudioBoost by animateFloatAsState(
        targetValue = activeLevel,
        animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
        label = "audio_boost"
    )

    // Color tones based on current conversation state
    val (primaryColor, secondaryColor, accentGlow) = when (conversationState) {
        ConversationState.SPEAKING -> Triple(
            Color(0xFF8B5CF6), // Vivid Violet
            Color(0xFFEC4899), // Pink
            Color(0xFFC084FC)  // Light Purple
        )
        ConversationState.LISTENING -> Triple(
            Color(0xFF06B6D4), // Cyan
            Color(0xFF3B82F6), // Blue
            Color(0xFF22D3EE)  // Bright Aqua
        )
        ConversationState.THINKING -> Triple(
            Color(0xFFF59E0B), // Amber
            Color(0xFF8B5CF6), // Purple
            Color(0xFFFDE047)  // Yellow glow
        )
        ConversationState.IDLE -> Triple(
            Color(0xFF64748B), // Slate
            Color(0xFF475569), // Muted Dark Slate
            Color(0xFF94A3B8)  // Soft light
        )
    }

    Box(
        modifier = modifier.size(sizeDp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(sizeDp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2f) * 0.58f

            // Dynamic scale combining breathing and live soundwave energy
            val scale = breathingScale * (1f + smoothAudioBoost * 0.45f)
            val currentRadius = baseRadius * scale

            // 1. Outer ambient halo glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentGlow.copy(alpha = 0.35f * (1f + smoothAudioBoost)),
                        primaryColor.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = currentRadius * 1.55f
                ),
                radius = currentRadius * 1.55f,
                center = center
            )

            // 2. Dynamic energy rings when active
            if (conversationState == ConversationState.SPEAKING || conversationState == ConversationState.LISTENING) {
                val ringCount = 3
                for (i in 1..ringCount) {
                    val ringRadius = currentRadius * (1f + i * 0.14f * (1f + smoothAudioBoost * 0.6f))
                    val ringAlpha = (0.4f / i) * (0.4f + smoothAudioBoost * 0.6f)
                    drawCircle(
                        color = primaryColor.copy(alpha = ringAlpha),
                        radius = ringRadius,
                        center = center,
                        style = Stroke(width = 2.5f * (ringCount - i + 1))
                    )
                }
            }

            // 3. Main Orb Core with multi-stop dynamic radial gradient
            val radAngle = Math.toRadians(rotationAngle.toDouble())
            val lightOffset = Offset(
                center.x + (cos(radAngle) * currentRadius * 0.28f).toFloat(),
                center.y + (sin(radAngle) * currentRadius * 0.28f).toFloat()
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.9f),
                        accentGlow,
                        primaryColor,
                        secondaryColor,
                        Color(0xFF0F172A) // Deep dark base
                    ),
                    center = lightOffset,
                    radius = currentRadius
                ),
                radius = currentRadius,
                center = center
            )

            // 4. Subtle inner light rim highlight
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.4f),
                        accentGlow.copy(alpha = 0.1f),
                        Color.White.copy(alpha = 0.4f)
                    ),
                    center = center
                ),
                radius = currentRadius * 0.98f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
