package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SamplerPad
import kotlin.math.abs

enum class DragHandle {
    START, END, LOOP_START
}

@Composable
fun WaveformEditor(
    pad: SamplerPad?,
    onUpdatePad: (SamplerPad) -> Unit,
    onAutoChop: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (pad == null || pad.audioData == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No Waveform Selected",
                    color = Color(0xFF6B6E7D),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap any pad containing a sound to edit, or hold REC to capture live microphone audio.",
                    color = Color(0xFF4C4F5C),
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
        return
    }

    val audioData = pad.audioData!!
    val density = LocalDensity.current

    // Drag Interaction Locks
    var activeDragHandle by remember { mutableStateOf<DragHandle?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(12.dp)
    ) {
        // Sample Name & Slices Bar Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = pad.label,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${"ABCD"[pad.bankId]}${pad.id + 1} • ${(audioData.size.toFloat() / pad.sampleRate * 1000).toInt()}ms",
                    color = Color(0xFFA0A5B5),
                    fontSize = 11.sp
                )
            }

            // Quick equal-slice autopresser block
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("CHOP:", color = Color(0xFFB3261E), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                listOf(4, 8, 16).forEach { count ->
                    Button(
                        onClick = { onAutoChop(count) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2B2930)
                        ),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("chop_$count"),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(text = "$count", color = Color.White, fontSize = 10.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // High Fidelity Interactive Waveform Canvas Workspace
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF000000))
                .border(1.dp, Color(0xFF36343B), RoundedCornerShape(8.dp))
                .testTag("waveform_canvas_box")
                .pointerInput(pad, audioData) {
                    // Identify drag captures based on proximate touch measurements
                    detectDragGestures(
                        onDragStart = { offset ->
                            val width = size.width.toFloat()
                            val touchXFactor = (offset.x / width).coerceIn(0f, 1f)

                            val distStart = abs(touchXFactor - pad.startFactor)
                            val distEnd = abs(touchXFactor - pad.endFactor)
                            val distLoop = abs(touchXFactor - pad.loopStartFactor)

                            // 12% width proximity range detection limit
                            val threshold = 0.12f
                            var minDistance = threshold
                            var selected: DragHandle? = null

                            if (distStart < minDistance) {
                                minDistance = distStart
                                selected = DragHandle.START
                            }
                            if (distEnd < minDistance) {
                                minDistance = distEnd
                                selected = DragHandle.END
                            }
                            if (distLoop < minDistance) {
                                minDistance = distLoop
                                selected = DragHandle.LOOP_START
                            }

                            activeDragHandle = selected
                        },
                        onDrag = { change, _ ->
                            val handle = activeDragHandle ?: return@detectDragGestures
                            val width = size.width.toFloat()
                            val touchXFactor = (change.position.x / width).coerceIn(0f, 1f)

                            val updatedPad = when (handle) {
                                DragHandle.START -> {
                                    pad.copy(startFactor = touchXFactor.coerceAtMost(pad.endFactor - 0.01f))
                                }
                                DragHandle.END -> {
                                    pad.copy(endFactor = touchXFactor.coerceAtLeast(pad.startFactor + 0.01f))
                                }
                                DragHandle.LOOP_START -> {
                                    pad.copy(loopStartFactor = touchXFactor)
                                }
                            }
                            onUpdatePad(updatedPad)
                        },
                        onDragEnd = {
                            activeDragHandle = null
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val midY = height / 2

                // Draw downsampled background wave mirroring spikes
                val drawLinesCount = 300
                val dataStep = (audioData.size / drawLinesCount).coerceAtLeast(1)

                for (i in 0 until drawLinesCount) {
                    val dataIdx = i * dataStep
                    if (dataIdx in audioData.indices) {
                        val amplitude = abs(audioData[dataIdx]).coerceIn(0f, 1f)
                        val lineLen = amplitude * (height * 0.72f)
                        val x = (i.toFloat() / drawLinesCount) * width

                        // Darken color of slices that are excluded by trim start/end points represent inactive parts
                        val currentFactor = i.toFloat() / drawLinesCount
                        val isInsideActiveRange = currentFactor in pad.startFactor..pad.endFactor
                        val waveColor = if (isInsideActiveRange) Color(0xFFFF5252) else Color(0xFF2B2C33)

                        drawLine(
                            color = waveColor,
                            start = Offset(x, midY - lineLen / 2),
                            end = Offset(x, midY + lineLen / 2),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }

                // Projection markers
                val startX = pad.startFactor * width
                val endX = pad.endFactor * width
                val loopStartX = pad.loopStartFactor * width

                // Highlight Active Audio Selection box
                drawRect(
                    color = Color(0xFFB3261E).copy(alpha = 0.08f),
                    topLeft = Offset(startX, 0f),
                    size = androidx.compose.ui.geometry.Size(endX - startX, height)
                )

                // Draw Loop Start Line (Blue dotted)
                drawLine(
                    color = Color(0xFF2196F3),
                    start = Offset(loopStartX, 0f),
                    end = Offset(loopStartX, height),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                )

                // Draw Start Point Handle (Glowing Orange)
                drawLine(
                    color = Color(0xFFB3261E),
                    start = Offset(startX, 0f),
                    end = Offset(startX, height),
                    strokeWidth = 2.5.dp.toPx()
                )
                drawCircle(
                    color = Color(0xFFB3261E),
                    radius = 8.dp.toPx(),
                    center = Offset(startX, 10.dp.toPx())
                )

                // Draw End Point Handle (Glowing Emerald)
                drawLine(
                    color = Color(0xFFFF5252),
                    start = Offset(endX, 0f),
                    end = Offset(endX, height),
                    strokeWidth = 2.5.dp.toPx()
                )
                drawCircle(
                    color = Color(0xFFFF5252),
                    radius = 8.dp.toPx(),
                    center = Offset(endX, height - 10.dp.toPx())
                )
            }

            // Text Overlays for Draggable points
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "START: ${(pad.startFactor * 100).toInt()}%",
                        color = Color(0xFFB3261E),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "LOOP: ${(pad.loopStartFactor * 100).toInt()}%",
                        color = Color(0xFF2196F3),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "END: ${(pad.endFactor * 100).toInt()}%",
                        color = Color(0xFFFF5252),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tactile playback parameter selectors
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // OneShot vs Gate button
            Button(
                onClick = { onUpdatePad(pad.copy(isOneShot = !pad.isOneShot)) },
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .testTag("param_oneshot"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (pad.isOneShot) Color(0xFFB3261E) else Color(0xFF2B2930)
                ),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Text(
                        text = if (pad.isOneShot) "ONE-SHOT" else "GATE PLAY",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Loop toggle
            Button(
                onClick = { onUpdatePad(pad.copy(isLooping = !pad.isLooping)) },
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .testTag("param_loop"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (pad.isLooping) Color(0xFF2196F3) else Color(0xFF2B2930)
                ),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Text(
                        text = "LOOP",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Reverse playback toggle
            Button(
                onClick = { onUpdatePad(pad.copy(isReverse = !pad.isReverse)) },
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .testTag("param_reverse"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (pad.isReverse) Color(0xFF49454F) else Color(0xFF2B2930)
                ),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.Reply,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Text(
                        text = "REVERSE",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
