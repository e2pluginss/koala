package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import com.example.data.SamplerPad
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

class SamplerEngine {

    companion object {
        private const val TAG = "SamplerEngine"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING_PLAY = AudioFormat.ENCODING_PCM_FLOAT
        private const val ENCODING_REC = AudioFormat.ENCODING_PCM_16BIT
    }

    // Audio Track for streaming high fidelity float output
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var isRecording = false
    private var playbackThread: Thread? = null
    private var recordingThread: Thread? = null

    // Native audio voices currently playing
    private val activeVoices = mutableListOf<ActiveVoice>()

    // Local level measurements for visually pulsing meters
    private val _masterLevel = MutableStateFlow(0f)
    val masterLevel: StateFlow<Float> = _masterLevel.asStateFlow()

    private val _padLevelActivity = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val padLevelActivity: StateFlow<Map<Int, Float>> = _padLevelActivity.asStateFlow()

    // Recording target & temporary storage
    private var recordingPadId: Int = -1
    private val recordingBuffer = mutableListOf<Float>()

    // Global performance FX states
    var isStutterActive = false
    var stutterSpeedFactor = 4 // 4 = 1/4 note, 8 = 1/8 note, 16 = 1/16 note, etc.
    var stutterStepCount = 0
    private var stutterBuffer: FloatArray? = null
    private var stutterWriteIndex = 0

    var isReverseLoopActive = false

    var delayFeedback = 0.0f // 0.0f to 0.9f
    var delayValueInMs = 250f
    private val delayBuffer = FloatArray(44100 * 2) // Max 2 seconds delay
    private var delayWriteIndex = 0

    var reverbMix = 0.0f // 0.0f to 0.8f
    private val combBuffer1 = FloatArray(1600)
    private val combBuffer2 = FloatArray(1900)
    private val combBuffer3 = FloatArray(2200)
    private var combIndex1 = 0
    private var combIndex2 = 0
    private var combIndex3 = 0

    var filterType = 0 // 0 = None, 1 = LowPass, 2 = HighPass
    var filterCutoff = 1.0f // 0.1f to 1.0f
    private var filterStateY1 = 0.0f

    var bitcrushMix = 0.0f // 0.0f to 1.0f crush factor
    var bitcrushBits = 4.0f // 2.0 to 16.0 bits

    // Sub-voice tracking model
    class ActiveVoice(
        val padId: Int,
        var currentIndex: Double,
        val pitchRatio: Float,
        val velocity: Float,
        val isOneShot: Boolean,
        val isLooping: Boolean,
        val reverse: Boolean,
        val startFrame: Int,
        val endFrame: Int,
        val loopStartFrame: Int,
        val audioData: FloatArray
    ) {
        var isFinished = false
    }

    init {
        startEngine()
    }

    fun startEngine() {
        if (isPlaying) return
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_OUT,
                ENCODING_PLAY
            )
            // Use slightly larger buffer to prevent occasional audio glitches
            val bufferSize = (minBufferSize * 2)

            val audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val audioFormat = android.media.AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_OUT)
                .setEncoding(ENCODING_PLAY)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            isPlaying = true
            audioTrack?.play()

            playbackThread = Thread {
                audioLoop()
            }.apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
            Log.d(TAG, "Audio Sampler Engine Started Successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Audio Sampler Engine", e)
        }
    }

    fun stopEngine() {
        isPlaying = false
        playbackThread?.join()
        playbackThread = null

        audioTrack?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {
                // ignore
            }
        }
        audioTrack = null
        Log.d(TAG, "Audio Sampler Engine Stopped.")
    }

    fun triggerPad(
        pad: SamplerPad,
        pitchRatio: Float = 1.0f,
        velocity: Float = 1.0f
    ) {
        val data = pad.audioData ?: return
        if (data.isEmpty()) return

        val totalFrames = data.size
        val startFrame = (pad.startFactor * totalFrames).toInt().coerceIn(0, totalFrames - 1)
        val endFrame = (pad.endFactor * totalFrames).toInt().coerceIn(1, totalFrames)
        val loopStartFrame = (pad.loopStartFactor * totalFrames).toInt().coerceIn(0, totalFrames - 1)

        val voice = ActiveVoice(
            padId = pad.id,
            currentIndex = if (pad.isReverse) (endFrame - 1).toDouble() else startFrame.toDouble(),
            pitchRatio = pitchRatio,
            velocity = velocity * pad.volume,
            isOneShot = pad.isOneShot,
            isLooping = pad.isLooping,
            reverse = pad.isReverse,
            startFrame = startFrame,
            endFrame = endFrame,
            loopStartFrame = loopStartFrame,
            audioData = data
        )

        // Monophonic voice cutoff if playing same pad, otherwise polyphonic!
        synchronized(activeVoices) {
            activeVoices.removeAll { it.padId == pad.id }
            activeVoices.add(voice)
        }
    }

    fun stopPad(padId: Int) {
        synchronized(activeVoices) {
            val voice = activeVoices.find { it.padId == padId }
            if (voice != null && !voice.isOneShot) {
                activeVoices.remove(voice)
            }
        }
    }

    fun stopAll() {
        synchronized(activeVoices) {
            activeVoices.clear()
        }
    }

    // Audio Synthesis and Real-Time DSP Mix Loop
    private fun audioLoop() {
        val blockLength = 512
        val floatBuffer = FloatArray(blockLength)
        val padLevels = mutableMapOf<Int, Float>()

        while (isPlaying) {
            val track = audioTrack ?: break
            // 1. Core Mixing (Polyphony)
            synchronized(activeVoices) {
                for (i in 0 until blockLength) {
                    var sum = 0.0f
                    val iterator = activeVoices.iterator()
                    while (iterator.hasNext()) {
                        val voice = iterator.next()
                        if (voice.isFinished) {
                            iterator.remove()
                            continue
                        }

                        val idx = voice.currentIndex
                        val roundedIdx = idx.toInt()

                        // Check boundaries
                        if (voice.reverse) {
                            if (roundedIdx < voice.startFrame) {
                                if (voice.isLooping) {
                                    voice.currentIndex = (voice.endFrame - 1).toDouble()
                                } else {
                                    voice.isFinished = true
                                    continue
                                }
                            }
                        } else {
                            if (roundedIdx >= voice.endFrame) {
                                if (voice.isLooping) {
                                    voice.currentIndex = voice.loopStartFrame.toDouble()
                                } else {
                                    voice.isFinished = true
                                    continue
                                }
                            }
                        }

                        // Linear interpolation of raw sample values for premium high fidelity quality
                        val fIdx = voice.currentIndex.toFloat()
                        val lower = voice.currentIndex.toInt()
                        var upper = if (voice.reverse) lower - 1 else lower + 1

                        if (lower in voice.audioData.indices) {
                            val lowerSample = voice.audioData[lower]
                            val upperSample = if (upper in voice.audioData.indices) voice.audioData[upper] else 0.0f
                            val frac = (fIdx - lower.toFloat())

                            val sample = lowerSample + frac * (upperSample - lowerSample)
                            val amplified = sample * voice.velocity
                            sum += amplified

                            // Track pad indicators
                            padLevels[voice.padId] = (padLevels[voice.padId] ?: 0f) + Math.abs(amplified)
                        } else {
                            voice.isFinished = true
                        }

                        // Progress the sample pointer
                        val delta = voice.pitchRatio
                        if (voice.reverse) {
                            voice.currentIndex -= delta
                        } else {
                            voice.currentIndex += delta
                        }
                    }
                    floatBuffer[i] = sum
                }
            }

            // 2. Real-Time Performance DSP Effects Layer

            // A. Stutter Repeater Effect
            if (isStutterActive) {
                val stutterLen = (44100 / stutterSpeedFactor).coerceAtLeast(1024)
                if (stutterBuffer == null || stutterBuffer?.size != stutterLen) {
                    stutterBuffer = FloatArray(stutterLen)
                    stutterWriteIndex = 0
                }
                val sBuf = stutterBuffer!!

                for (i in 0 until blockLength) {
                    val input = floatBuffer[i]
                    // Write to buffer
                    sBuf[stutterWriteIndex] = input
                    // Read repeating slice
                    floatBuffer[i] = sBuf[stutterWriteIndex % stutterLen]
                    stutterWriteIndex = (stutterWriteIndex + 1) % stutterLen
                }
            } else {
                stutterBuffer = null
            }

            // B. Filter Effect (Sweepable LP/HP SVF style)
            if (filterType > 0) {
                val alpha = filterCutoff.coerceIn(0.01f, 1.0f)
                for (i in 0 until blockLength) {
                    val input = floatBuffer[i]
                    if (filterType == 1) { // Low Pass
                        filterStateY1 = filterStateY1 + alpha * (input - filterStateY1)
                        floatBuffer[i] = filterStateY1
                    } else if (filterType == 2) { // High Pass
                        filterStateY1 = filterStateY1 + alpha * (input - filterStateY1)
                        floatBuffer[i] = input - filterStateY1
                    }
                }
            }

            // C. Bitcrusher Effect
            if (bitcrushMix > 0.0f) {
                val stepCount = 2.0f.pow(bitcrushBits)
                for (i in 0 until blockLength) {
                    val raw = floatBuffer[i]
                    // Squeeze and quantize amplitudes
                    val crushed = (Math.round(raw * stepCount) / stepCount)
                    floatBuffer[i] = raw * (1.0f - bitcrushMix) + crushed * bitcrushMix
                }
            }

            // D. Delay/Feedback Echo Loop
            if (delayFeedback > 0.01f) {
                val delaySamples = ((delayValueInMs / 1000f) * SAMPLE_RATE).toInt().coerceIn(128, delayBuffer.size - 1)
                for (i in 0 until blockLength) {
                    val readIndex = (delayWriteIndex - delaySamples + delayBuffer.size) % delayBuffer.size
                    val echo = delayBuffer[readIndex]
                    val input = floatBuffer[i]
                    val output = input + echo * delayFeedback
                    delayBuffer[delayWriteIndex] = output
                    delayWriteIndex = (delayWriteIndex + 1) % delayBuffer.size
                    floatBuffer[i] = output
                }
            }

            // E. Reverb / Deep Ambient Space
            if (reverbMix > 0.01f) {
                for (i in 0 until blockLength) {
                    val input = floatBuffer[i]

                    // Multi-comb filter mixing for reverb simulation
                    combIndex1 = (combIndex1 + 1) % combBuffer1.size
                    val c1 = combBuffer1[combIndex1]
                    combBuffer1[combIndex1] = input + c1 * 0.7f

                    combIndex2 = (combIndex2 + 1) % combBuffer2.size
                    val c2 = combBuffer2[combIndex2]
                    combBuffer2[combIndex2] = input + c2 * 0.65f

                    combIndex3 = (combIndex3 + 1) % combBuffer3.size
                    val c3 = combBuffer3[combIndex3]
                    combBuffer3[combIndex3] = input + c3 * 0.6f

                    val wet = (c1 + c2 + c3) * 0.33f
                    floatBuffer[i] = input * (1.0f - reverbMix) + wet * reverbMix
                }
            }

            // F. Visual Levels Extraction
            var maxAmp = 0f
            for (i in 0 until blockLength) {
                val absVal = Math.abs(floatBuffer[i])
                if (absVal > maxAmp) maxAmp = absVal
            }
            _masterLevel.value = maxAmp.coerceIn(0f, 1f)

            // Normalize and scale pad levels down
            val scaledPads = padLevels.mapValues { (_, levelSum) ->
                (levelSum / blockLength * 12.0f).coerceIn(0f, 1f)
            }
            _padLevelActivity.value = scaledPads
            padLevels.clear()

            // Soft-clipping compression to prevent harsh speaker cracks
            for (i in 0 until blockLength) {
                val s = floatBuffer[i]
                floatBuffer[i] = when {
                    s > 1.0f -> 1.0f
                    s < -1.0f -> -1.0f
                    else -> s
                }
            }

            // 3. Play stream
            track.write(floatBuffer, 0, blockLength, AudioTrack.WRITE_NON_BLOCKING)
        }
    }

    // Micro Recording Pipeline
    @SuppressLint("MissingPermission")
    fun startRecording(padId: Int) {
        if (isRecording) return
        recordingPadId = padId
        recordingBuffer.clear()
        isRecording = true

        recordingThread = Thread {
            val minBufSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_IN,
                ENCODING_REC
            )
            val recFormat = android.media.AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_IN)
                .setEncoding(ENCODING_REC)
                .build()

            val record = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(recFormat)
                .setBufferSizeInBytes(minBufSize * 2)
                .build()

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord could not be initialized")
                isRecording = false
                return@Thread
            }

            try {
                record.startRecording()
                val shortBuffer = ShortArray(1024)
                while (isRecording) {
                    val read = record.read(shortBuffer, 0, shortBuffer.size)
                    if (read > 0) {
                        synchronized(recordingBuffer) {
                            for (j in 0 until read) {
                                // Convert 16-bit short to normalized float
                                val value = shortBuffer[j] / 32768.0f
                                recordingBuffer.add(value)
                            }
                        }
                    }
                }
                record.stop()
                record.release()
            } catch (e: Exception) {
                Log.e(TAG, "Recording thread exception", e)
            }
        }.apply {
            start()
        }
    }

    fun stopRecording(): FloatArray? {
        if (!isRecording) return null
        isRecording = false
        recordingThread?.join()
        recordingThread = null

        val finalWave = synchronized(recordingBuffer) {
            recordingBuffer.toFloatArray()
        }
        recordingBuffer.clear()

        if (finalWave.isEmpty()) return null

        // Transient auto-trim helper to skip silent microphone buffer gap
        var firstActiveIndex = 0
        val threshold = 0.012f
        for (idx in finalWave.indices) {
            if (Math.abs(finalWave[idx]) > threshold) {
                firstActiveIndex = idx
                break
            }
        }

        // Return trimmed wave or original if no threshold is met
        return if (firstActiveIndex > 0 && firstActiveIndex < finalWave.size - 128) {
            finalWave.copyOfRange(firstActiveIndex, finalWave.size)
        } else {
            finalWave
        }
    }

    // Programmatic Lo-Fi Synthesizers for Initial Sample Packs
    fun generateSyntheticKit(bankId: Int): List<SamplerPad> {
        val list = mutableListOf<SamplerPad>()
        val defaultColor = when (bankId) {
            0 -> 0xFFE65100.toInt() // Orange LED
            1 -> 0xFF2E7D32.toInt() // Emerald Green LED
            2 -> 0xFF1565C0.toInt() // Sapphire Blue LED
            else -> 0xFF8E24AA.toInt() // Violet Purple LED
        }

        for (padId in 0..15) {
            val idx = bankId * 16 + padId
            val name: String
            val soundData: FloatArray?

            when (padId) {
                0 -> {
                    name = "Sub Kick 808"
                    soundData = buildDeepSynthKick()
                }
                1 -> {
                    name = "Crisp Snare"
                    soundData = buildSnareNoise()
                }
                2 -> {
                    name = "Tapped HiHat"
                    soundData = buildHatNoise(false)
                }
                3 -> {
                    name = "Lofi HiHat"
                    soundData = buildHatNoise(true)
                }
                4 -> {
                    name = "Jazz Chord"
                    soundData = buildLofiChord(220f, 1.2f) // Electric piano lofi A-minor 9
                }
                5 -> {
                    name = "Vocal Pad"
                    soundData = buildFormantVowel(330f, 1.0f) // Vocal format ahh/ooh
                }
                6 -> {
                    name = "Cosmic Bass"
                    soundData = buildWaveBass(110f, 0.8f) // Deep filter triangle wave
                }
                7 -> {
                    name = "Retro Beep"
                    soundData = buildRetroBeep(440f)
                }
                8 -> {
                    name = "Lofi Chord II"
                    soundData = buildLofiChord(261.63f, 1.2f) // EP C-Major 9
                }
                9 -> {
                    name = "Chop FX A"
                    soundData = buildWaveBass(165f, 1.2f)
                }
                10 -> {
                    name = "Chop FX B"
                    soundData = buildFormantVowel(220f, 1.2f)
                }
                11 -> {
                    name = "Bell Bell"
                    soundData = buildRetroBeep(880f)
                }
                else -> {
                    // Empty pads ready for user creation
                    name = "Empty Pad ${padId + 1}"
                    soundData = null
                }
            }

            list.add(
                SamplerPad(
                    id = padId,
                    label = name,
                    audioData = soundData,
                    sampleRate = SAMPLE_RATE,
                    bankId = bankId,
                    color = defaultColor,
                    isOneShot = true
                )
            )
        }
        return list
    }

    private fun buildDeepSynthKick(): FloatArray {
        val totalFrames = (SAMPLE_RATE * 0.4).toInt() // 400ms duration
        val arr = FloatArray(totalFrames)
        for (i in 0 until totalFrames) {
            val time = i.toFloat() / SAMPLE_RATE
            // Pitch sweeps rapidly from 150Hz down to 40Hz
            val freq = 45f + 110f * Math.exp(-60.0 * time).toFloat()
            val phase = 2.0 * PI * freq * time
            // Exponential volume envelope with snappy click punch
            val env = Math.exp(-7.0 * time).toFloat()
            val click = 0.35f * Math.exp(-250.0 * time).toFloat() * Random.nextFloat()
            arr[i] = (sin(phase).toFloat() * env) + click
        }
        return arr
    }

    private fun buildSnareNoise(): FloatArray {
        val totalFrames = (SAMPLE_RATE * 0.25).toInt() // 250ms duration
        val arr = FloatArray(totalFrames)
        var lpState = 0f
        for (i in 0 until totalFrames) {
            val time = i.toFloat() / SAMPLE_RATE
            // White noise base
            val rawNoise = Random.nextFloat() * 2f - 1f
            // Filter output slightly for crispy wooden punch
            lpState = lpState + 0.35f * (rawNoise - lpState)
            val noiseEnv = Math.exp(-12.0 * time).toFloat()
            // Drum body component
            val bodyFreq = 180f
            val bodyEnv = Math.exp(-28.0 * time).toFloat()
            val body = sin(2.0 * PI * bodyFreq * time).toFloat() * bodyEnv * 0.4f

            arr[i] = (lpState * noiseEnv * 0.7f) + body
        }
        return arr
    }

    private fun buildHatNoise(isOpen: Boolean): FloatArray {
        val duration = if (isOpen) 0.35f else 0.05f
        val totalFrames = (SAMPLE_RATE * duration).toInt()
        val arr = FloatArray(totalFrames)
        var hpState = 0f
        for (i in 0 until totalFrames) {
            val time = i.toFloat() / SAMPLE_RATE
            val rawNoise = Random.nextFloat() * 2f - 1f
            // Fast Highpass cascade structure to get high metallic resonance
            hpState = rawNoise - (hpState + 0.05f * (rawNoise - hpState))
            val env = Math.exp((-if (isOpen) 9.0 else 40.0) * time).toFloat()
            arr[i] = hpState * env * 0.45f
        }
        return arr
    }

    private fun buildLofiChord(rootFreq: Float, durationSec: Float): FloatArray {
        val totalFrames = (SAMPLE_RATE * durationSec).toInt()
        val arr = FloatArray(totalFrames)
        // Synthesize an elegant Minor 9th chord: Root, Minor 3rd, Perfect 5th, Minor 7th, Major 9th
        val ratios = floatArrayOf(
            1.00f, // Root
            1.20f, // Minor 3rd (e.g. C to Eb)
            1.50f, // Perfect 5th
            1.80f, // Minor 7th
            2.25f  // Major 9th
        )
        for (i in 0 until totalFrames) {
            val time = i.toFloat() / SAMPLE_RATE
            var sum = 0f
            for (j in ratios.indices) {
                val f = rootFreq * ratios[j]
                // Combine sine wave and some nice warm triangle harmonics
                val wave = sin(2.0 * PI * f * time).toFloat() * 0.7f +
                           sin(4.0 * PI * f * time).toFloat() * 0.2f
                sum += wave
            }
            val env = Math.exp(-1.5 * time).toFloat() * (1f - Math.exp(-40.0 * time).toFloat()) // Attack envelope fade-in
            arr[i] = (sum / ratios.size) * env * 0.5f
        }
        return arr
    }

    private fun buildWaveBass(rootFreq: Float, durationSec: Float): FloatArray {
        val totalFrames = (SAMPLE_RATE * durationSec).toInt()
        val arr = FloatArray(totalFrames)
        for (i in 0 until totalFrames) {
            val time = i.toFloat() / SAMPLE_RATE
            // Triangle-driven rich sub bass
            val angle = 2.0 * PI * rootFreq * time
            val tri = (Math.abs((angle % (2.0 * PI)) / PI - 1.0) * 2.0 - 1.0).toFloat()
            val env = Math.exp(-2.5 * time).toFloat()
            arr[i] = tri * env * 0.6f
        }
        return arr
    }

    private fun buildFormantVowel(rootFreq: Float, durationSec: Float): FloatArray {
        val totalFrames = (SAMPLE_RATE * durationSec).toInt()
        val arr = FloatArray(totalFrames)
        for (i in 0 until totalFrames) {
            val time = i.toFloat() / SAMPLE_RATE
            // Rich fundamental pulse train
            var sum = 0f
            for (harm in 1..4) {
                sum += sin(2.0 * PI * (rootFreq * harm) * time).toFloat() / harm
            }
            // Formant sweep (Simulating vocal "Ah" to "Oh" modulation)
            val formantCutoff = 800f + 600f * sin(2.0 * PI * 0.5f * time).toFloat()
            val env = Math.exp(-1.8 * time).toFloat()
            arr[i] = sum * env * 0.35f
        }
        return arr
    }

    private fun buildRetroBeep(freq: Float): FloatArray {
        val totalFrames = (SAMPLE_RATE * 0.3f).toInt()
        val arr = FloatArray(totalFrames)
        for (i in 0 until totalFrames) {
            val time = i.toFloat() / SAMPLE_RATE
            // Retro 8-bit pulse square wave
            val s = sin(2.0 * PI * freq * time).toFloat()
            val square = if (s > 0f) 0.5f else -0.5f
            val env = Math.exp(-4.5 * time).toFloat()
            arr[i] = square * env * 0.3f
        }
        return arr
    }
}
