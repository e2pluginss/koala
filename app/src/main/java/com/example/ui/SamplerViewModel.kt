package com.example.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SamplerEngine
import com.example.data.SamplerPad
import com.example.data.SequenceNote
import com.example.data.SequencePattern
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.PI

class SamplerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "SamplerViewModel"
    }

    private val engine = SamplerEngine()

    // 4 Banks (0 = A, 1 = B, 2 = C, 3 = D). Each bank has 16 pads.
    private val padsMap = mutableStateMapOf<Int, List<SamplerPad>>()

    private val _currentBank = MutableStateFlow(0)
    val currentBank: StateFlow<Int> = _currentBank.asStateFlow()

    private val _selectedPadId = MutableStateFlow(0)
    val selectedPadId: StateFlow<Int> = _selectedPadId.asStateFlow()

    // Active pads representing current selected bank (for reactive Composable streams)
    private val _currentPads = MutableStateFlow<List<SamplerPad>>(emptyList())
    val currentPads: StateFlow<List<SamplerPad>> = _currentPads.asStateFlow()

    // Sequencer States
    private val _bpm = MutableStateFlow(120)
    val bpm: StateFlow<Int> = _bpm.asStateFlow()

    private val _isMetronomeEnabled = MutableStateFlow(false)
    val isMetronomeEnabled: StateFlow<Boolean> = _isMetronomeEnabled.asStateFlow()

    private val _isRecordingSequencer = MutableStateFlow(false)
    val isRecordingSequencer: StateFlow<Boolean> = _isRecordingSequencer.asStateFlow()

    private val _isPlayingSequencer = MutableStateFlow(false)
    val isPlayingSequencer: StateFlow<Boolean> = _isPlayingSequencer.asStateFlow()

    private val _activePatternId = MutableStateFlow(0)
    val activePatternId: StateFlow<Int> = _activePatternId.asStateFlow()

    private val _patterns = MutableStateFlow<List<SequencePattern>>(emptyList())
    val patterns: StateFlow<List<SequencePattern>> = _patterns.asStateFlow()

    // Current playing tick index of the loop (e.g. 0 to 15, or 0 to 63)
    private val _currentSequencerTick = MutableStateFlow(0)
    val currentSequencerTick: StateFlow<Int> = _currentSequencerTick.asStateFlow()

    // Mic recording states
    private val _recordingPadIndex = MutableStateFlow<Int?>(null)
    val recordingPadIndex: StateFlow<Int?> = _recordingPadIndex.asStateFlow()

    private val _deleteModeActive = MutableStateFlow(false)
    val deleteModeActive: StateFlow<Boolean> = _deleteModeActive.asStateFlow()

    // Keyboard mode states
    private val _keyboardBasePitch = MutableStateFlow(1.0f) // center rate is 1.0 (normal play)
    val keyboardBasePitch: StateFlow<Float> = _keyboardBasePitch.asStateFlow()

    // Master levels
    val masterLevel: StateFlow<Float> = engine.masterLevel
    val padLevels: StateFlow<Map<Int, Float>> = engine.padLevelActivity

    // Performance FX States (Mapped to UI variables and Engine binds)
    private val _stutterActive = MutableStateFlow(false)
    val stutterActive: StateFlow<Boolean> = _stutterActive.asStateFlow()

    private val _stutterMultiplier = MutableStateFlow(4)
    val stutterMultiplier: StateFlow<Int> = _stutterMultiplier.asStateFlow()

    private val _lowPassFilterCutoff = MutableStateFlow(1.0f)
    val lowPassFilterCutoff: StateFlow<Float> = _lowPassFilterCutoff.asStateFlow()

    private val _highPassFilterCutoff = MutableStateFlow(1.0f)
    val highPassFilterCutoff: StateFlow<Float> = _highPassFilterCutoff.asStateFlow()

    private val _filterMode = MutableStateFlow(0) // 0 = Off, 1 = LowPass, 2 = HighPass
    val filterMode: StateFlow<Int> = _filterMode.asStateFlow()

    private val _bitcrushValue = MutableStateFlow(0.0f)
    val bitcrushValue: StateFlow<Float> = _bitcrushValue.asStateFlow()

    private val _delayFeedback = MutableStateFlow(0.0f)
    val delayFeedback: StateFlow<Float> = _delayFeedback.asStateFlow()

    private val _reverbMix = MutableStateFlow(0.0f)
    val reverbMix: StateFlow<Float> = _reverbMix.asStateFlow()

    // Coroutine job running the sequencer clock ticks
    private var sequencerJob: Job? = null

    // Helper beep wave data for metronome tick
    private val metronomeLeadBeep = buildMetronomeClick(800f, 0.04f)
    private val metronomeBeatBeep = buildMetronomeClick(500f, 0.03f)

    init {
        // Prepare initial synthetic kits for all banks
        for (bank in 0..3) {
            padsMap[bank] = engine.generateSyntheticKit(bank)
        }
        updateCurrentPadsStream()

        // Prepare 8 initial empty patterns
        val rawPatterns = List(8) { id ->
            SequencePattern(id = id, lengthBars = 1)
        }
        _patterns.value = rawPatterns
    }

    private fun updateCurrentPadsStream() {
        _currentPads.value = padsMap[currentBank.value] ?: emptyList()
    }

    // Engine playback trig helpers
    fun padTriggered(padId: Int, velocity: Float = 1.0f) {
        val selectedBankIndex = currentBank.value
        val bankPads = padsMap[selectedBankIndex] ?: return
        val pad = bankPads.getOrNull(padId) ?: return

        if (_deleteModeActive.value) {
            // Remove/Clear sample
            val updatedPad = pad.copy(audioData = null, label = "Empty Pad ${padId + 1}")
            val list = bankPads.toMutableList()
            list[padId] = updatedPad
            padsMap[selectedBankIndex] = list
            updateCurrentPadsStream()
            _deleteModeActive.value = false
            return
        }

        // Play visual trigger signal
        engine.triggerPad(pad, pitchRatio = 1.0f, velocity = velocity)

        // Capture trigger parameters in real-time when Sequencer recording is active
        if (_isRecordingSequencer.value && _isPlayingSequencer.value) {
            recordNoteEvent(padId, _currentSequencerTick.value, velocity, 1.0f)
        }
    }

    fun padReleased(padId: Int) {
        engine.stopPad(padId)
    }

    fun selectPad(padId: Int) {
        _selectedPadId.value = padId
    }

    fun selectBank(bankId: Int) {
        if (bankId in 0..3) {
            _currentBank.value = bankId
            updateCurrentPadsStream()
        }
    }

    val selectedPad: SamplerPad?
        get() = currentPads.value.getOrNull(selectedPadId.value)

    fun updatePadParams(updated: SamplerPad) {
        val bank = currentBank.value
        val list = padsMap[bank]?.toMutableList() ?: return
        val idx = updated.id
        if (idx in list.indices) {
            list[idx] = updated
            padsMap[bank] = list
            updateCurrentPadsStream()
        }
    }

    fun changeBpm(newBpm: Int) {
        _bpm.value = newBpm.coerceIn(40, 240)
    }

    fun toggleMetronome() {
        _isMetronomeEnabled.value = !_isMetronomeEnabled.value
    }

    fun toggleDeleteMode() {
        _deleteModeActive.value = !_deleteModeActive.value
    }

    // Chromatic Keyboard/Pitch triggers
    fun triggerPianoKey(semitones: Int, velocity: Float = 0.85f) {
        val pad = selectedPad ?: return
        // Pitch shift ratio based on equal temperament: 2^(semitones / 12)
        val ratio = 2.0f.pow(semitones / 12.0f)
        engine.triggerPad(pad, pitchRatio = ratio, velocity = velocity)

        // Capture playing notes in sequencer
        if (_isRecordingSequencer.value && _isPlayingSequencer.value) {
            recordNoteEvent(pad.id, _currentSequencerTick.value, velocity, ratio)
        }
    }

    fun releasePianoKey() {
        selectedPad?.let { engine.stopPad(it.id) }
    }

    // Waveform Slicing / Auto-Chop
    fun autoChopSample(slicesCount: Int) {
        val pad = selectedPad ?: return
        val rawData = pad.audioData ?: return
        if (rawData.isEmpty()) return

        val segmentLen = 1.0f / slicesCount
        val bank = currentBank.value
        val padsList = padsMap[bank]?.toMutableList() ?: return

        var targetPadIndex = 0
        for (i in 0 until slicesCount) {
            // Find next empty or available pad to layout sliced chops in current bank
            while (targetPadIndex < 16 && targetPadIndex == pad.id) {
                targetPadIndex++
            }
            if (targetPadIndex >= 16) break

            // Make chopped clone representing segment slices
            val chopPad = SamplerPad(
                id = targetPadIndex,
                label = "${pad.label} S${i + 1}",
                audioData = rawData,
                sampleRate = pad.sampleRate,
                startFactor = i * segmentLen,
                endFactor = (i + 1) * segmentLen,
                loopStartFactor = i * segmentLen,
                bankId = bank,
                color = pad.color,
                isOneShot = pad.isOneShot
            )

            padsList[targetPadIndex] = chopPad
            targetPadIndex++
        }

        padsMap[bank] = padsList
        updateCurrentPadsStream()
    }

    // Microphone Recording
    fun startMicRecording(padId: Int) {
        _recordingPadIndex.value = padId
        engine.startRecording(padId)
    }

    fun stopMicRecording() {
        val targetIndex = _recordingPadIndex.value ?: return
        _recordingPadIndex.value = null

        val recordedData = engine.stopRecording() ?: return

        // Update target pad with newly recorded sound waveform
        val bank = currentBank.value
        val padsList = padsMap[bank]?.toMutableList() ?: return
        val currentPad = padsList[targetIndex]

        val updatedPad = currentPad.copy(
            audioData = recordedData,
            label = "Rec Sound ${targetIndex + 1}",
            startFactor = 0.0f,
            endFactor = 1.0f
        )
        padsList[targetIndex] = updatedPad
        padsMap[bank] = padsList
        updateCurrentPadsStream()
    }

    // Performance DSP FX Binds
    fun togglePerformanceStutter(active: Boolean, multiplier: Int = 4) {
        _stutterActive.value = active
        _stutterMultiplier.value = multiplier
        engine.isStutterActive = active
        engine.stutterSpeedFactor = multiplier
    }

    fun updateLowPassFilter(cutoff: Float) {
        _lowPassFilterCutoff.value = cutoff
        _filterMode.value = 1
        engine.filterType = 1
        engine.filterCutoff = cutoff
    }

    fun updateHighPassFilter(cutoff: Float) {
        _highPassFilterCutoff.value = cutoff
        _filterMode.value = 2
        engine.filterType = 2
        engine.filterCutoff = cutoff
    }

    fun clearFilter() {
        _filterMode.value = 0
        engine.filterType = 0
    }

    fun updateBitcrush(mix: Float) {
        _bitcrushValue.value = mix
        engine.bitcrushMix = mix
        // Quantize progressively from 12 bits down to 3 bits
        engine.bitcrushBits = 3f + (1f - mix) * 9f
    }

    fun updateDelay(feedback: Float, delayTimeMs: Float = 250f) {
        _delayFeedback.value = feedback
        engine.delayFeedback = feedback
        engine.delayValueInMs = delayTimeMs
    }

    fun updateReverb(mix: Float) {
        _reverbMix.value = mix
        engine.reverbMix = mix
    }

    // Loops, Step Sequencer, Arranger Operations
    fun togglePlaySequencer() {
        if (_isPlayingSequencer.value) {
            stopSequencer()
        } else {
            startSequencer()
        }
    }

    fun toggleRecordSequencer() {
        if (_isRecordingSequencer.value) {
            _isRecordingSequencer.value = false
        } else {
            _isRecordingSequencer.value = true
            if (!_isPlayingSequencer.value) {
                startSequencer()
            }
        }
    }

    private fun startSequencer() {
        _isPlayingSequencer.value = true
        _currentSequencerTick.value = 0

        sequencerJob?.cancel()
        sequencerJob = viewModelScope.launch {
            while (isActive && _isPlayingSequencer.value) {
                val currentTick = _currentSequencerTick.value
                val activePattern = _patterns.value[activePatternId.value]
                val maxTicks = activePattern.lengthBars * 16

                // 1. Play sequenced events scheduled on this sixteenth notes tick
                val notesToPlay = activePattern.notes.filter { it.tick == currentTick }
                notesToPlay.forEach { note ->
                    val bankList = padsMap[currentBank.value] ?: return@forEach
                    val pad = bankList.getOrNull(note.padId)
                    if (pad != null && pad.audioData != null) {
                        engine.triggerPad(pad, pitchRatio = note.pitchRatio, velocity = note.velocity)
                    }
                }

                // 2. Play metronome beep on beat boundaries
                if (_isMetronomeEnabled.value) {
                    val beatIndex = currentTick % 4
                    if (beatIndex == 0) {
                        // Strong click
                        engine.triggerPad(metronomeLeadBeep)
                    } else {
                        // Weak click
                        engine.triggerPad(metronomeBeatBeep)
                    }
                }

                // Calculate sixteenth note interval
                val tempoBpm = _bpm.value
                val tickMs = (60000 / tempoBpm) / 4
                delay(tickMs.toLong())

                // Increment loop step
                _currentSequencerTick.value = (currentTick + 1) % maxTicks
            }
        }
    }

    private fun stopSequencer() {
        _isPlayingSequencer.value = false
        _isRecordingSequencer.value = false
        sequencerJob?.cancel()
        sequencerJob = null
        _currentSequencerTick.value = 0
    }

    fun changeActivePattern(id: Int) {
        if (id in 0..7) {
            _activePatternId.value = id
            _currentSequencerTick.value = 0
        }
    }

    fun clearActivePattern() {
        val activeId = activePatternId.value
        val updated = _patterns.value.toMutableList()
        updated[activeId] = SequencePattern(id = activeId, lengthBars = updated[activeId].lengthBars)
        _patterns.value = updated
    }

    fun updatePatternLength(bars: Int) {
        val activeId = activePatternId.value
        val updated = _patterns.value.toMutableList()
        val currentPattern = updated[activeId]
        val validatedBars = bars.coerceIn(1, 4)

        // Trim any notes exceeding boundary limit
        val maxTicks = validatedBars * 16
        val trimmedNotes = currentPattern.notes.filter { it.tick < maxTicks }

        updated[activeId] = currentPattern.copy(lengthBars = validatedBars, notes = trimmedNotes)
        _patterns.value = updated
    }

    // Toggle note in matrix Step grid directly
    fun toggleStepNote(padId: Int, tick: Int) {
        val activeId = activePatternId.value
        val patternList = _patterns.value.toMutableList()
        val currentPattern = patternList[activeId]

        val existingNote = currentPattern.notes.find { it.padId == padId && it.tick == tick }
        val updatedNotes = currentPattern.notes.toMutableList()

        if (existingNote != null) {
            updatedNotes.remove(existingNote)
        } else {
            val newNote = SequenceNote(
                id = UUID.randomUUID().toString(),
                padId = padId,
                tick = tick,
                duration = 1,
                pitchRatio = 1.0f,
                velocity = 0.85f
            )
            updatedNotes.add(newNote)
        }

        patternList[activeId] = currentPattern.copy(notes = updatedNotes)
        _patterns.value = patternList
    }

    private fun recordNoteEvent(padId: Int, tick: Int, velocity: Float, pitchRatio: Float) {
        val activeId = activePatternId.value
        val patternList = _patterns.value.toMutableList()
        val currentPattern = patternList[activeId]

        // Keep it clean: eliminate old note on same tick/pad to avoid overlapping duplicates
        val updatedNotes = currentPattern.notes.toMutableList()
        updatedNotes.removeAll { it.padId == padId && it.tick == tick }

        val newNote = SequenceNote(
            id = UUID.randomUUID().toString(),
            padId = padId,
            tick = tick,
            duration = 1,
            pitchRatio = pitchRatio,
            velocity = velocity
        )
        updatedNotes.add(newNote)

        patternList[activeId] = currentPattern.copy(notes = updatedNotes)
        _patterns.value = patternList
    }

    override fun onCleared() {
        super.onCleared()
        stopSequencer()
        engine.stopEngine()
    }

    // Support generator for standard high precision metronome beep
    private fun buildMetronomeClick(beepFreq: Float, durationSec: Float): SamplerPad {
        val sampleRate = 44100
        val frames = (sampleRate * durationSec).toInt()
        val arr = FloatArray(frames)
        for (i in 0 until frames) {
            val time = i.toFloat() / sampleRate
            arr[i] = sin(2.0 * PI * beepFreq * time).toFloat() * Math.exp(-40.0 * time).toFloat()
        }
        return SamplerPad(
            id = -99,
            label = "CLK",
            audioData = arr,
            sampleRate = sampleRate,
            volume = 0.5f,
            isOneShot = true
        )
    }
}
