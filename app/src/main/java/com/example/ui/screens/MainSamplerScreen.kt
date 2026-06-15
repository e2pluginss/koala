package com.example.ui.screens

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.SamplerViewModel
import com.example.ui.components.PadsGrid
import com.example.ui.components.PianoKeys
import com.example.ui.components.StepSequencer
import com.example.ui.components.XYPad
import com.example.ui.components.WaveformEditor
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainSamplerScreen(
    viewModel: SamplerViewModel = viewModel()
) {
    // 1. Collect reactive state objects from ViewModel flow
    val currentPads by viewModel.currentPads.collectAsState()
    val selectedPadId by viewModel.selectedPadId.collectAsState()
    val currentBank by viewModel.currentBank.collectAsState()

    val bpm by viewModel.bpm.collectAsState()
    val isMetronomeEnabled by viewModel.isMetronomeEnabled.collectAsState()
    val isRecordingSequencer by viewModel.isRecordingSequencer.collectAsState()
    val isPlayingSequencer by viewModel.isPlayingSequencer.collectAsState()
    val currentTick by viewModel.currentSequencerTick.collectAsState()
    val activePatternId by viewModel.activePatternId.collectAsState()
    val patterns by viewModel.patterns.collectAsState()

    val recordingPadIndex by viewModel.recordingPadIndex.collectAsState()
    val deleteModeActive by viewModel.deleteModeActive.collectAsState()

    val masterLevel by viewModel.masterLevel.collectAsState()
    val padLevels by viewModel.padLevels.collectAsState()

    val stutterActive by viewModel.stutterActive.collectAsState()
    val stutterMultiplier by viewModel.stutterMultiplier.collectAsState()

    val filterMode by viewModel.filterMode.collectAsState()
    val bitcrushValue by viewModel.bitcrushValue.collectAsState()
    val delayFeedback by viewModel.delayFeedback.collectAsState()
    val reverbMix by viewModel.reverbMix.collectAsState()

    // Workspace mode (For compact mobile phones)
    var mobileModeIndex by remember { mutableStateOf(0) } // 0 = Studio, 1 = Samurai Edit, 2 = Keys, 3 = Rhythm, 4 = FX

    // Workspace mode (For tablet split screens pane)
    var tabletModeIndex by remember { mutableStateOf(0) } // 0 = Samurai Edit, 1 = Keys, 2 = Rhythm, 3 = FX

    // 2. Adaptive Sizing determination (Using local configuration size constraints)
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 660

    // 3. Audio Record runtime permission wrapper setup
    val recordPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    // Layout Scaffold Core
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_scaffold_root"),
        topBar = {
            // Hardware-themed cockpit header control panel (BPM, VU peaks, CPU Metronome)
            Surface(
                tonalElevation = 6.dp,
                color = Color(0xFF0F0F11)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Vintage hardware Logo branding heading
                        Column {
                            Text(
                                text = "SAMURAI SAMPLER",
                                color = Color(0xFFB3261E),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp
                            )
                            Text(
                                text = "PRO EDITION MPC",
                                color = Color(0xFFCAC4D0),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        // Sound Level VU Peak metering strip
                        Row(
                            modifier = Modifier
                                .width(80.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF1B1B1F))
                                .border(0.5.dp, Color(0xFF2E2E35), RoundedCornerShape(3.dp))
                                .padding(horizontal = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val animateWidthFactor by animateFloatAsState(targetValue = masterLevel)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight(0.65f)
                                    .fillMaxWidth(animateWidthFactor)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (animateWidthFactor > 0.85f) Color(0xFFB3261E) else Color(0xFF4CAF50)
                                    )
                            )
                        }

                        // BPM Increment and dial controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.changeBpm(bpm - 1) },
                                modifier = Modifier.size(24.dp).testTag("action_bpm_dec")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Decrease Tempo",
                                    tint = Color(0xFFA0A5B5),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$bpm",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "TEMPO BPM",
                                    color = Color(0xFFCAC4D0),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            IconButton(
                                onClick = { viewModel.changeBpm(bpm + 1) },
                                modifier = Modifier.size(24.dp).testTag("action_bpm_inc")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Increase Tempo",
                                    tint = Color(0xFFA0A5B5),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = Color(0xFF26262B), thickness = 1.dp)
                }
            }
        },
        bottomBar = {
            // Display Bottom Navigation Row only on Compact phones
            if (!isTablet) {
                NavigationBar(
                    containerColor = Color(0xFF0F0F11),
                    tonalElevation = 8.dp
                ) {
                    val tabs = listOf(
                        Triple("STUDIO", Icons.Default.GridOn, Icons.Outlined.GridOn),
                        Triple("SAMURAI", Icons.Default.GraphicEq, Icons.Outlined.GraphicEq),
                        Triple("PITCH", Icons.Default.Piano, Icons.Outlined.Piano),
                        Triple("RHYTHM", Icons.Default.ListAlt, Icons.Outlined.ListAlt),
                        Triple("DSP FX", Icons.Default.Hearing, Icons.Outlined.Hearing)
                    )

                    tabs.forEachIndexed { idx, (label, filledIcon, outlinedIcon) ->
                        val selected = mobileModeIndex == idx
                        NavigationBarItem(
                            selected = selected,
                            onClick = { mobileModeIndex = idx },
                            modifier = Modifier.testTag("nav_item_$idx"),
                            icon = {
                                Icon(
                                    imageVector = if (selected) filledIcon else outlinedIcon,
                                    contentDescription = label
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                indicatorColor = Color(0xFFB3261E),
                                unselectedIconColor = Color(0xFFCAC4D0),
                                unselectedTextColor = Color(0xFFCAC4D0)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->

        // Global Alert Dialog Overlay for Microphones permission approvals
        if (!recordPermissionState.status.isGranted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1B1F)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color(0xFFB3261E),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Microphone Access Required",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "To record custom vocal loops or live audio directly onto any pad, please authorize microphone access approvals.",
                            fontSize = 11.sp,
                            color = Color(0xFFCAC4D0),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { recordPermissionState.launchPermissionRequest() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))
                        ) {
                            Text("GRANT APPROVAL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Adaptive canonical viewport selection
        if (isTablet) {
            // Canonical Tablet Desktop class: Dual-pane layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF121212))
                    .padding(innerPadding)
            ) {
                // Left view-pane: Standard playable Pads drum grid
                Box(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                ) {
                    PadsGrid(
                        pads = currentPads,
                        selectedPadId = selectedPadId,
                        currentBank = currentBank,
                        deleteModeActive = deleteModeActive,
                        recordingPadIndex = recordingPadIndex,
                        padLevels = padLevels,
                        onPadPress = { padId -> viewModel.padTriggered(padId) },
                        onPadRelease = { padId -> viewModel.padReleased(padId) },
                        onPadSelect = { padId -> viewModel.selectPad(padId) },
                        onBankSelect = { bankId -> viewModel.selectBank(bankId) },
                        onToggleDeleteMode = { viewModel.toggleDeleteMode() },
                        onStartRecording = { padId -> viewModel.startMicRecording(padId) },
                        onStopRecording = { viewModel.stopMicRecording() }
                    )
                }

                VerticalDivider(
                    color = Color(0xFF26262B),
                    modifier = Modifier.fillMaxHeight().width(1.dp)
                )

                // Right view-pane: Workspace selector + active widget
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Top tab selectors Row
                    TabRow(
                        selectedTabIndex = tabletModeIndex,
                        containerColor = Color(0xFF0F0F11),
                        contentColor = Color.White,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[tabletModeIndex]),
                                color = Color(0xFFB3261E)
                            )
                        }
                    ) {
                        val tabs = listOf("SAMURAI", "PITCH", "RHYTHM", "DSP FX")
                        tabs.forEachIndexed { idx, label ->
                            Tab(
                                selected = tabletModeIndex == idx,
                                onClick = { tabletModeIndex = idx },
                                modifier = Modifier.testTag("tab_tablet_$idx"),
                                text = {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (tabletModeIndex) {
                            0 -> WaveformEditor(
                                pad = viewModel.selectedPad,
                                onUpdatePad = { updated -> viewModel.updatePadParams(updated) },
                                onAutoChop = { count -> viewModel.autoChopSample(count) }
                            )
                            1 -> PianoKeys(
                                selectedPad = viewModel.selectedPad,
                                onPianoKeyPress = { semitones -> viewModel.triggerPianoKey(semitones) },
                                onPianoKeyRelease = { viewModel.releasePianoKey() }
                            )
                            2 -> StepSequencer(
                                pads = currentPads,
                                patterns = patterns,
                                activePatternId = activePatternId,
                                currentTick = currentTick,
                                isPlaying = isPlayingSequencer,
                                isRecording = isRecordingSequencer,
                                isMetronomeEnabled = isMetronomeEnabled,
                                onTogglePlay = { viewModel.togglePlaySequencer() },
                                onToggleRecord = { viewModel.toggleRecordSequencer() },
                                onToggleMetronome = { viewModel.toggleMetronome() },
                                onStepClick = { padId, tick -> viewModel.toggleStepNote(padId, tick) },
                                onClearPattern = { viewModel.clearActivePattern() },
                                onPatternSelect = { id -> viewModel.changeActivePattern(id) },
                                onBarsSelect = { bars -> viewModel.updatePatternLength(bars) }
                            )
                            3 -> XYPad(
                                stutterActive = stutterActive,
                                stutterMultiplier = stutterMultiplier,
                                filterMode = filterMode,
                                bitcrushValue = bitcrushValue,
                                delayFeedback = delayFeedback,
                                reverbMix = reverbMix,
                                onStutterChange = { active, mult -> viewModel.togglePerformanceStutter(active, mult) },
                                onFilterSweep = { cutoff, mode ->
                                    if (mode == 1) viewModel.updateLowPassFilter(cutoff)
                                    else viewModel.updateHighPassFilter(cutoff)
                                },
                                onFilterClear = { viewModel.clearFilter() },
                                onBitcrushSweep = { valY -> viewModel.updateBitcrush(valY) },
                                onDelaySweep = { valY -> viewModel.updateDelay(valY) },
                                onReverbSweep = { valY -> viewModel.updateReverb(valY) }
                            )
                        }
                    }
                }
            }
        } else {
            // Compact Mobile View: View active Tab solely
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (mobileModeIndex) {
                    0 -> PadsGrid(
                        pads = currentPads,
                        selectedPadId = selectedPadId,
                        currentBank = currentBank,
                        deleteModeActive = deleteModeActive,
                        recordingPadIndex = recordingPadIndex,
                        padLevels = padLevels,
                        onPadPress = { padId -> viewModel.padTriggered(padId) },
                        onPadRelease = { padId -> viewModel.padReleased(padId) },
                        onPadSelect = { padId -> viewModel.selectPad(padId) },
                        onBankSelect = { bankId -> viewModel.selectBank(bankId) },
                        onToggleDeleteMode = { viewModel.toggleDeleteMode() },
                        onStartRecording = { padId -> viewModel.startMicRecording(padId) },
                        onStopRecording = { viewModel.stopMicRecording() }
                    )
                    1 -> WaveformEditor(
                        pad = viewModel.selectedPad,
                        onUpdatePad = { updated -> viewModel.updatePadParams(updated) },
                        onAutoChop = { count -> viewModel.autoChopSample(count) }
                    )
                    2 -> PianoKeys(
                        selectedPad = viewModel.selectedPad,
                        onPianoKeyPress = { semitones -> viewModel.triggerPianoKey(semitones) },
                        onPianoKeyRelease = { viewModel.releasePianoKey() }
                    )
                    3 -> StepSequencer(
                        pads = currentPads,
                        patterns = patterns,
                        activePatternId = activePatternId,
                        currentTick = currentTick,
                        isPlaying = isPlayingSequencer,
                        isRecording = isRecordingSequencer,
                        isMetronomeEnabled = isMetronomeEnabled,
                        onTogglePlay = { viewModel.togglePlaySequencer() },
                        onToggleRecord = { viewModel.toggleRecordSequencer() },
                        onToggleMetronome = { viewModel.toggleMetronome() },
                        onStepClick = { padId, tick -> viewModel.toggleStepNote(padId, tick) },
                        onClearPattern = { viewModel.clearActivePattern() },
                        onPatternSelect = { id -> viewModel.changeActivePattern(id) },
                        onBarsSelect = { bars -> viewModel.updatePatternLength(bars) }
                    )
                    4 -> XYPad(
                        stutterActive = stutterActive,
                        stutterMultiplier = stutterMultiplier,
                        filterMode = filterMode,
                        bitcrushValue = bitcrushValue,
                        delayFeedback = delayFeedback,
                        reverbMix = reverbMix,
                        onStutterChange = { active, mult -> viewModel.togglePerformanceStutter(active, mult) },
                        onFilterSweep = { cutoff, mode ->
                            if (mode == 1) viewModel.updateLowPassFilter(cutoff)
                            else viewModel.updateHighPassFilter(cutoff)
                        },
                        onFilterClear = { viewModel.clearFilter() },
                        onBitcrushSweep = { valY -> viewModel.updateBitcrush(valY) },
                        onDelaySweep = { valY -> viewModel.updateDelay(valY) },
                        onReverbSweep = { valY -> viewModel.updateReverb(valY) }
                    )
                }
            }
        }
    }
}
