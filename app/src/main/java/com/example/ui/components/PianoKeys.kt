package com.example.ui.components

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SamplerPad

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PianoKeys(
    selectedPad: SamplerPad?,
    onPianoKeyPress: (Int) -> Unit,
    onPianoKeyRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSemitonePlaying by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(12.dp)
    ) {
        // Keyboard Instructions Header
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            Text(
                text = "CHROMATIC KEYBOARD",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (selectedPad != null) {
                    "Playing: \"${selectedPad.label}\" stretched across keys"
                } else {
                    "Select a loaded pad to play it chromatically"
                },
                color = if (selectedPad != null) Color(0xFFB3261E) else Color(0xFF6B6E7D),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Layout the chromatic absolute white and black piano keys
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0F0F11))
                .border(1.dp, Color(0xFF36343B), RoundedCornerShape(8.dp))
                .testTag("chromatic_keyboard_workspace")
        ) {
            // White Keys mapping: Semitone offsets from center C (C3 -> C4 -> E4)
            val whiteKeys = listOf(
                Pair(0, "C"), Pair(2, "D"), Pair(4, "E"),
                Pair(5, "F"), Pair(7, "G"), Pair(9, "A"),
                Pair(11, "B"), Pair(12, "C2"), Pair(14, "D2")
            )

            // Black keys mapping: Semitone offsets, positioned relatively to overlap White keys
            // Pair(semitone, leftPercentBias)
            val blackKeys = listOf(
                Pair(1, 0.111f), // C#
                Pair(3, 0.222f), // D#
                Pair(6, 0.444f), // F#
                Pair(8, 0.556f), // G#
                Pair(10, 0.667f), // A#
                Pair(13, 0.889f) // C2#
            )

            // 1. Draw White Keys laying in Row
            Row(modifier = Modifier.fillMaxSize()) {
                whiteKeys.forEach { (semitone, noteName) ->
                    val isKeyPressing = activeSemitonePlaying == semitone
                    val keyColor = if (isKeyPressing) Color(0xFFFF5252) else Color(0xFFF0F1F5)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(width = 0.5.dp, color = Color(0xFF36343B))
                            .background(keyColor)
                            .testTag("piano_white_$semitone")
                            .pointerInteropFilter { motionEvent ->
                                when (motionEvent.action) {
                                    MotionEvent.ACTION_DOWN -> {
                                        if (selectedPad != null) {
                                            activeSemitonePlaying = semitone
                                            onPianoKeyPress(semitone)
                                        }
                                        true
                                    }
                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                        activeSemitonePlaying = null
                                        onPianoKeyRelease()
                                        true
                                    }
                                    else -> false
                                }
                            },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = noteName,
                            color = if (isKeyPressing) Color.White else Color(0xFF4C4F5C),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }
            }

            // 2. Draw Black Keys overlapping correctly above White Keys
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val containerWidth = maxWidth
                val keyHeightFactor = 0.58f // Black keys take up 58% of keyboard height

                blackKeys.forEach { (semitone, bias) ->
                    val isKeyPressing = activeSemitonePlaying == semitone
                    val keyColor = if (isKeyPressing) Color(0xFFB3261E) else Color(0xFF1F1F24)

                    // Position black key mathematically relative to proportional width bias
                    val leftOffset = containerWidth * bias - 15.dp

                    Box(
                        modifier = Modifier
                            .offset(x = leftOffset)
                            .width(30.dp)
                            .fillMaxHeight(keyHeightFactor)
                            .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                            .background(keyColor)
                            .testTag("piano_black_$semitone")
                            .border(0.5.dp, Color(0xFF32323A), RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                            .pointerInteropFilter { motionEvent ->
                                when (motionEvent.action) {
                                    MotionEvent.ACTION_DOWN -> {
                                        if (selectedPad != null) {
                                            activeSemitonePlaying = semitone
                                            onPianoKeyPress(semitone)
                                        }
                                        true
                                    }
                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                        activeSemitonePlaying = null
                                        onPianoKeyRelease()
                                        true
                                    }
                                    else -> false
                                }
                            },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = "#",
                            color = Color(0xFF7A7F8E),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
