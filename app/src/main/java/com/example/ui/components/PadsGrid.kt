package com.example.ui.components

import android.view.MotionEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SamplerPad

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PadsGrid(
    pads: List<SamplerPad>,
    selectedPadId: Int,
    currentBank: Int,
    deleteModeActive: Boolean,
    recordingPadIndex: Int?,
    padLevels: Map<Int, Float>,
    onPadPress: (Int) -> Unit,
    onPadRelease: (Int) -> Unit,
    onPadSelect: (Int) -> Unit,
    onBankSelect: (Int) -> Unit,
    onToggleDeleteMode: () -> Unit,
    onStartRecording: (Int) -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(8.dp)
    ) {
        // Banks Selection A, B, C, D & Mode Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Banks Row (A, B, C, D)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val bankList = listOf("BANK A", "BANK B", "BANK C", "BANK D")
                bankList.forEachIndexed { idx, label ->
                    val isActive = currentBank == idx
                    val selectColor by animateColorAsState(
                        targetValue = if (isActive) Color(0xFFB3261E) else Color(0xFF2B2930)
                    )
                    Box(
                        modifier = Modifier
                            .testTag("bank_select_$idx")
                            .clip(RoundedCornerShape(6.dp))
                            .background(selectColor)
                            .clickable { onBankSelect(idx) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isActive) Color.White else Color(0xFFCAC4D0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Global Actions (Erase Mode)
            val binColor by animateColorAsState(
                targetValue = if (deleteModeActive) Color(0xFFB3261E) else Color(0xFF2B2930)
            )
            IconButton(
                onClick = onToggleDeleteMode,
                modifier = Modifier
                    .testTag("action_erase")
                    .clip(RoundedCornerShape(6.dp))
                    .background(binColor)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Toggle Delete Mode",
                    tint = if (deleteModeActive) Color.White else Color(0xFFB3261E)
                )
            }
        }

        // 4x4 Grid of Tactile Pads
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (row in 0 until 4) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (col in 0 until 4) {
                        val padIndex = row * 4 + col
                        val pad = pads.getOrNull(padIndex)

                        if (pad != null) {
                            val isSelected = selectedPadId == padIndex
                            val isPadRecording = recordingPadIndex == padIndex
                            val liveLevel = padLevels[padIndex] ?: 0.0f

                            // Reactive level expansion animation
                            val glowingBorderWidth by animateFloatAsState(
                                targetValue = if (isSelected) 2.5f else 1f
                            )

                            // Pad hardware state visual determination
                            val hasSound = pad.audioData != null
                            val padTypeBg = when {
                                isPadRecording -> Color(0xFFB3261E)
                                isSelected -> Color(0xFF49454F)
                                hasSound -> Color(0xFF2B2930)
                                else -> Color(0xFF1C1B1F)
                            }

                            val glowColor = Color(pad.color)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .testTag("pad_$padIndex")
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(padTypeBg)
                                    .border(
                                        width = glowingBorderWidth.dp,
                                        color = when {
                                            isPadRecording -> Color(0xFFB3261E)
                                            isSelected -> glowColor
                                            hasSound -> glowColor.copy(alpha = 0.5f)
                                            else -> Color(0xFF36343B)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    // Hardware LED glow background effect based on real audio level metrics
                                    .drawBehind {
                                        if (hasSound && liveLevel > 0.012f) {
                                            drawRoundRect(
                                                brush = Brush.radialGradient(
                                                    colors = listOf(
                                                        glowColor.copy(alpha = liveLevel * 0.45f),
                                                        Color.Transparent
                                                    ),
                                                    center = center,
                                                    radius = size.minDimension * 0.8f
                                                ),
                                                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                                            )
                                        }
                                    }
                                    .pointerInteropFilter { motionEvent ->
                                        // Trigger-on-press low latency technology
                                        when (motionEvent.action) {
                                            MotionEvent.ACTION_DOWN -> {
                                                onPadSelect(padIndex)
                                                onPadPress(padIndex)
                                                true
                                            }
                                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                                onPadRelease(padIndex)
                                                true
                                            }
                                            else -> false
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier.padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Row showing index & recording status
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${"ABCD"[currentBank]}${padIndex + 1}",
                                            color = if (hasSound) Color(0xFFCAC4D0) else Color(0xFF49454F),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        if (isPadRecording) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(RoundedCornerShape(50))
                                                    .background(Color(0xFFB3261E))
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Display direct audio mini-waveform inside actual Pad
                                    if (hasSound && pad.audioData != null) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            // Mini-waveform decoration layout
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                val data = pad.audioData
                                                val step = (data.size / 16).coerceAtLeast(1)
                                                val width = size.width
                                                val height = size.height
                                                val midY = height / 2

                                                for (i in 0 until 16) {
                                                    val rawIdx = i * step
                                                    if (rawIdx in data.indices) {
                                                        val amp = Math.abs(data[rawIdx]).coerceIn(0f, 1f)
                                                        val lineLength = (amp * (height * 0.8f)).coerceAtLeast(2f)
                                                        val x = (i.toFloat() / 15f) * width
                                                        drawLine(
                                                            color = glowColor.copy(alpha = 0.65f),
                                                            start = Offset(x, midY - lineLength / 2),
                                                            end = Offset(x, midY + lineLength / 2),
                                                            strokeWidth = 1.5.dp.toPx()
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // Double visual mode for Empty Pads: Allow direct hold to record trigger
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            IconButton(
                                                onClick = {},
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .pointerInteropFilter { ev ->
                                                        when (ev.action) {
                                                            MotionEvent.ACTION_DOWN -> {
                                                                onStartRecording(padIndex)
                                                                true
                                                            }
                                                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                                                onStopRecording()
                                                                true
                                                            }
                                                            else -> false
                                                        }
                                                    }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Mic,
                                                    contentDescription = "Hold to Rec",
                                                    tint = Color(0xFF49454F),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    // Label text representing Sample/Instrument Details
                                    Text(
                                        text = pad.label,
                                        color = if (hasSound) Color(0xFFE6E1E5) else Color(0xFF49454F),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
