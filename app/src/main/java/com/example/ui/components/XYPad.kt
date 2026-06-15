package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun XYPad(
    stutterActive: Boolean,
    stutterMultiplier: Int,
    filterMode: Int,
    bitcrushValue: Float,
    delayFeedback: Float,
    reverbMix: Float,
    onStutterChange: (Boolean, Int) -> Unit,
    onFilterSweep: (Float, Int) -> Unit, // cutoff, mode
    onFilterClear: () -> Unit,
    onBitcrushSweep: (Float) -> Unit,
    onDelaySweep: (Float) -> Unit,
    onReverbSweep: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var touchX by remember { mutableStateOf(0.5f) }
    var touchY by remember { mutableStateOf(0.0f) }
    var isTouching by remember { mutableStateOf(false) }

    // State for Y-Axis mode selection (to let users toggle effect)
    var yAxisMode by remember { mutableStateOf(0) } // 0 = Delay, 1 = Bitcrusher, 2 = Reverb

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(12.dp)
    ) {
        // FX Header Controls
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "PERFORMANCE FX ENGINE",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Hold & sweep anywhere below to trigger real-time DSP",
                    color = Color(0xFFCAC4D0),
                    fontSize = 11.sp
                )
            }

            // FX clear helper
            Button(
                onClick = {
                    touchX = 0.5f
                    touchY = 0f
                    onFilterClear()
                    onBitcrushSweep(0f)
                    onDelaySweep(0f)
                    onReverbSweep(0f)
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2930)),
                modifier = Modifier.height(26.dp).testTag("action_reset_fx"),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("RESET SYSTEM", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // Active parameter description overlays
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val modes = listOf("DELAY FEEDBACK", "LOFI CRUSHER", "SPACE REVERB")
            modes.forEachIndexed { idx, label ->
                val active = yAxisMode == idx
                Button(
                    onClick = { yAxisMode = idx },
                    modifier = Modifier.weight(1f).height(32.dp).testTag("fx_y_mode_$idx"),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (active) Color(0xFFB3261E) else Color(0xFF2B2930)
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(text = label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Giant XY Touch Canvas Pad
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF000000))
                .border(2.dp, if (isTouching) Color(0xFFFF5252) else Color(0xFF36343B), RoundedCornerShape(8.dp))
                .testTag("xy_touch_surface")
                .pointerInput(yAxisMode) {
                    detectTapGestures(
                        onPress = { offset ->
                            isTouching = true
                            val width = size.width.toFloat()
                            val height = size.height.toFloat()

                            touchX = (offset.x / width).coerceIn(0f, 1f)
                            touchY = (1f - (offset.y / height)).coerceIn(0f, 1f) // invert so bottom = 0.0f, top = 1.0f

                            // Trigger FX
                            applyFXSweeps(touchX, touchY, yAxisMode, onFilterSweep, onBitcrushSweep, onDelaySweep, onReverbSweep)

                            try {
                                awaitRelease()
                            } finally {
                                isTouching = false
                                // Gently release filter and crush back to zero, preserving Delay Feedback
                                onFilterClear()
                                onBitcrushSweep(0f)
                            }
                        }
                    )
                }
                .pointerInput(yAxisMode) {
                    detectDragGestures(
                        onDragStart = { isTouching = true },
                        onDragEnd = {
                            isTouching = false
                            onFilterClear()
                            onBitcrushSweep(0f)
                        },
                        onDragCancel = {
                            isTouching = false
                            onFilterClear()
                            onBitcrushSweep(0f)
                        },
                        onDrag = { change, _ ->
                            val width = size.width.toFloat()
                            val height = size.height.toFloat()

                            touchX = (change.position.x / width).coerceIn(0f, 1f)
                            touchY = (1f - (change.position.y / height)).coerceIn(0f, 1f)

                            applyFXSweeps(touchX, touchY, yAxisMode, onFilterSweep, onBitcrushSweep, onDelaySweep, onReverbSweep)
                        }
                    )
                }
        ) {
            // Draw grid lines & pulsing targets
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                // Grid divisions
                val lineEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                val linesCount = 8

                for (i in 1 until linesCount) {
                    val x = (i.toFloat() / linesCount) * width
                    val y = (i.toFloat() / linesCount) * height

                    // Vertical lines
                    drawLine(
                        color = Color(0xFF212128),
                        start = Offset(x, 0f),
                        end = Offset(x, height),
                        strokeWidth = 1.dp.toPx()
                    )
                    // Horizontal lines
                    drawLine(
                        color = Color(0xFF212128),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Mid lines divisions
                drawLine(
                    color = Color(0xFF33333F),
                    start = Offset(width / 2f, 0f),
                    end = Offset(width / 2f, height),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = lineEffect
                )
                drawLine(
                    color = Color(0xFF33333F),
                    start = Offset(0f, height / 2f),
                    end = Offset(width, height / 2f),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = lineEffect
                )

                // Drag indicator Crosshair overlay
                if (isTouching || touchY > 0.01f) {
                    val pxX = touchX * width
                    val pxY = (1f - touchY) * height // invert back

                    // Pulsing sweep glow radar circle
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFF5252).copy(alpha = 0.22f), Color.Transparent),
                            center = Offset(pxX, pxY),
                            radius = 64.dp.toPx()
                        ),
                        radius = 64.dp.toPx(),
                        center = Offset(pxX, pxY)
                    )

                    // Target Crosshairs lines
                    drawLine(
                        color = Color(0xFFFF5252).copy(alpha = 0.6f),
                        start = Offset(0f, pxY),
                        end = Offset(width, pxY),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    drawLine(
                        color = Color(0xFFFF5252).copy(alpha = 0.6f),
                        start = Offset(pxX, 0f),
                        end = Offset(pxX, height),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    // Node core
                    drawCircle(
                        color = Color(0xFFFF5252),
                        radius = 6.dp.toPx(),
                        center = Offset(pxX, pxY)
                    )
                }
            }

            // Parameter text value displays
            Box(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                contentAlignment = Alignment.TopStart
            ) {
                Column {
                    val filterName = when {
                        !isTouching -> "FILTER: BYPASSED"
                        touchX < 0.45f -> "LOWPASS FILTER: ${(20000 * (touchX * 2.2f).coerceIn(0.01f, 1f)).toInt()}Hz"
                        touchX > 0.55f -> "HIGHPASS FILTER: ${(18000 * ((touchX - 0.5f) * 2f).coerceIn(0.01f, 1f)).toInt()}Hz"
                        else -> "FILTER: HIGHPASS SWEEP BAND"
                    }
                    Text(
                        text = filterName,
                        color = if (isTouching) Color(0xFFFF5252) else Color(0xFF656975),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    val yValName = when (yAxisMode) {
                        0 -> "DELAY FEEDBACK: ${(touchY * 100).toInt()}%"
                        1 -> "CRUSHER LEVEL: ${(touchY * 100).toInt()}%"
                        else -> "REVERB SPACE DAMP: ${(touchY * 100).toInt()}%"
                    }
                    Text(
                        text = yValName,
                        color = if (isTouching) Color(0xFF2196F3) else Color(0xFF656975),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Multi-multiplier Stutter repeater triggers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val stutters = listOf(
                Pair("STUTTER x4", 4),
                Pair("STUTTER x8", 8),
                Pair("STUTTER x16", 16)
            )

            stutters.forEach { (label, mult) ->
                val active = stutterActive && stutterMultiplier == mult
                Button(
                    onClick = {
                        if (active) {
                            onStutterChange(false, 4)
                        } else {
                            onStutterChange(true, mult)
                        }
                    },
                    modifier = Modifier.weight(1f).height(36.dp).testTag("fx_stutter_$mult"),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (active) Color(0xFFB3261E) else Color(0xFF2B2930)
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(text = label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun applyFXSweeps(
    x: Float,
    y: Float,
    mode: Int,
    onFilterSweep: (Float, Int) -> Unit,
    onBitcrushSweep: (Float) -> Unit,
    onDelaySweep: (Float) -> Unit,
    onReverbSweep: (Float) -> Unit
) {
    // 1. Evaluate X-axis sweep: Filters
    // x = 0.5 is centers. < 0.45 is Lowpass sweeps down. > 0.55 is Highpass sweeps up.
    when {
        x < 0.45f -> {
            // Lowpass: center value X ranges 0.0f to 0.45f. Scale to 0.05f to 0.8f cutoff
            val cutoff = 0.05f + (x / 0.45f) * 0.75f
            onFilterSweep(cutoff, 1)
        }
        x > 0.55f -> {
            // Highpass: scale X ranging 0.55f to 1.0f into 0.02f to 0.6f cutoff factor
            val cutoff = 0.02f + ((x - 0.55f) / 0.45f) * 0.58f
            onFilterSweep(cutoff, 2)
        }
        else -> {
            onFilterSweep(1.0f, 0)
        }
    }

    // 2. Evaluate Y-axis: Delay, Bitcrush, Reverb depending on Active tab mode
    when (mode) {
        0 -> { // Delay feedback: scale bottom Y to Feedback multiplier (0.0 to 0.85)
            val feedback = y * 0.85f
            onDelaySweep(feedback)
            onBitcrushSweep(0.0f)
            onReverbSweep(0.0f)
        }
        1 -> { // Bitcrusher mix: scale Y to (0.0 to 0.95)
            val crunch = y * 0.95f
            onBitcrushSweep(crunch)
            onDelaySweep(0.0f)
            onReverbSweep(0.0f)
        }
        2 -> { // Reverb Mix: scale Y to (0.0 to 0.8)
            val wet = y * 0.8f
            onReverbSweep(wet)
            onDelaySweep(0.0f)
            onBitcrushSweep(0.0f)
        }
    }
}
