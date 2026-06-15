package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SamplerPad
import com.example.data.SequenceNote
import com.example.data.SequencePattern

@Composable
fun StepSequencer(
    pads: List<SamplerPad>,
    patterns: List<SequencePattern>,
    activePatternId: Int,
    currentTick: Int,
    isPlaying: Boolean,
    isRecording: Boolean,
    isMetronomeEnabled: Boolean,
    onTogglePlay: () -> Unit,
    onToggleRecord: () -> Unit,
    onToggleMetronome: () -> Unit,
    onStepClick: (Int, Int) -> Unit, // padId, tick
    onClearPattern: () -> Unit,
    onPatternSelect: (Int) -> Unit,
    onBarsSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val activePattern = patterns.getOrElse(activePatternId) { SequencePattern(activePatternId) }
    val totalSteps = activePattern.lengthBars * 16

    val gridScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(12.dp)
    ) {
        // Transport Sequencer Controls (Play, Rec, BPM)
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play-Stop Button
                val playColor by animateColorAsState(targetValue = if (isPlaying) Color(0xFF4CAF50) else Color(0xFF26262B))
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).background(playColor).testTag("seq_play")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = "Play/Stop",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Record Button
                val recColor by animateColorAsState(targetValue = if (isRecording) Color(0xFFD32F2F) else Color(0xFF26262B))
                IconButton(
                    onClick = onToggleRecord,
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).background(recColor).testTag("seq_rec")
                ) {
                    Icon(
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = "Record",
                        tint = if (isRecording) Color.White else Color(0xFFD32F2F),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Metronome Button
                val metroColor by animateColorAsState(targetValue = if (isMetronomeEnabled) Color(0xFF2196F3) else Color(0xFF26262B))
                IconButton(
                    onClick = onToggleMetronome,
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).background(metroColor).testTag("seq_metronome")
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Metronome",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Loop Bar Size select (1 Bar, 2 Bars, 4 Bars)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("BARS:", color = Color(0xFFCAC4D0), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                listOf(1, 2, 4).forEach { bars ->
                    val isActive = activePattern.lengthBars == bars
                    Button(
                        onClick = { onBarsSelect(bars) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isActive) Color(0xFFB3261E) else Color(0xFF2B2930)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp).testTag("seq_bars_$bars"),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(text = "$bars", fontSize = 10.sp, color = Color.White)
                    }
                }
            }

            // Clear Pattern
            Button(
                onClick = onClearPattern,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2930)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp).testTag("seq_clear"),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(text = "CLEAR ALL", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // Patterns Selection Row (1-8)
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PATTERNS:",
                color = Color(0xFFCAC4D0),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (patIdx in 0 until 8) {
                    val active = activePatternId == patIdx
                    Box(
                        modifier = Modifier
                            .testTag("pattern_bank_$patIdx")
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (active) Color(0xFFB3261E) else Color(0xFF2B2930))
                            .clickable { onPatternSelect(patIdx) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${patIdx + 1}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Horizontal Scrollable Note Edit Step Matrix
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF000000))
                .border(1.dp, Color(0xFF36343B), RoundedCornerShape(6.dp))
                .horizontalScroll(gridScrollState)
        ) {
            Column {
                // Header displaying Beat tick index indicators
                Row(modifier = Modifier.height(26.dp)) {
                    // Margin padding for aligning rows text
                    Spacer(modifier = Modifier.width(44.dp))

                    for (step in 0 until totalSteps) {
                        val isBeatStart = step % 4 == 0
                        val isPlayingStep = isPlaying && currentTick == step

                        Box(
                            modifier = Modifier
                                .width(34.dp)
                                .fillMaxHeight()
                                .background(
                                    when {
                                        isPlayingStep -> Color(0xFFB3261E).copy(alpha = 0.2f)
                                        isBeatStart -> Color(0xFF1B1B22)
                                        else -> Color.Transparent
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${step + 1}",
                                color = if (isPlayingStep) Color(0xFFB3261E) else Color(0xFF565863),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 16 rows corresponding to sampler pads
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    for (padId in 0 until 16) {
                        val pad = pads.getOrNull(padId)
                        val glowColor = if (pad != null) Color(pad.color) else Color(0xFFFF5722)

                        Row(modifier = Modifier.height(28.dp)) {
                            // Row Label (e.g. A1, A2...)
                            Box(
                                modifier = Modifier
                                    .width(44.dp)
                                    .fillMaxHeight()
                                    .background(Color(0xFF131317)),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "${"ABCD"[pad?.bankId ?: 0]}${padId + 1}",
                                    color = glowColor.copy(alpha = 0.85f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }

                            // 16 columns (sixteenth note buttons)
                            for (step in 0 until totalSteps) {
                                val hasNote = activePattern.notes.any { it.padId == padId && it.tick == step }
                                val isPlayingStep = isPlaying && currentTick == step
                                val isBeatStart = step % 4 == 0

                                val cellColor = when {
                                    hasNote -> glowColor
                                    isPlayingStep -> Color(0xFFB3261E).copy(alpha = 0.08f)
                                    isBeatStart -> Color(0xFF1C1C22)
                                    else -> Color(0xFF141418)
                                }

                                Box(
                                    modifier = Modifier
                                        .testTag("step_grid_${padId}_$step")
                                        .width(34.dp)
                                        .fillMaxHeight()
                                        .border(width = 0.5.dp, color = Color(0xFF36343B))
                                        .background(cellColor)
                                        .clickable { onStepClick(padId, step) }
                                ) {
                                    // Soft indicator overlay showing active step playhead
                                    if (isPlayingStep) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .width(2.dp)
                                                .background(Color(0xFFB3261E))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
