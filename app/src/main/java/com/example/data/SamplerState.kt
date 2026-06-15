package com.example.data

data class SamplerPad(
    val id: Int,
    val label: String,
    val audioData: FloatArray? = null,
    val sampleRate: Int = 44100,
    // Trim and Loop markers (as factors, 0.0f to 1.0f)
    val startFactor: Float = 0.0f,
    val endFactor: Float = 1.0f,
    val loopStartFactor: Float = 0.0f,
    val isLooping: Boolean = false,
    val isOneShot: Boolean = true, // true: plays to end, false: plays only while held
    val isReverse: Boolean = false,
    val volume: Float = 1.0f,
    val pan: Float = 0.0f, // -1.0 to 1.0 (left/right panning)
    val bankId: Int = 0, // 0 = Bank A, 1 = Bank B, 2 = Bank C, 3 = Bank D
    val color: Int = 0xFFE65100.toInt() // warm glowing orange accent
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SamplerPad) return false
        if (id != other.id) return false
        if (label != other.label) return false
        if (sampleRate != other.sampleRate) return false
        if (startFactor != other.startFactor) return false
        if (endFactor != other.endFactor) return false
        if (loopStartFactor != other.loopStartFactor) return false
        if (isLooping != other.isLooping) return false
        if (isOneShot != other.isOneShot) return false
        if (isReverse != other.isReverse) return false
        if (volume != other.volume) return false
        if (pan != other.pan) return false
        if (bankId != other.bankId) return false
        if (color != other.color) return false
        if (audioData == null && other.audioData == null) return true
        if (audioData == null || other.audioData == null) return false
        return audioData.size == other.audioData.size // performance safeguard
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + label.hashCode()
        result = 31 * result + sampleRate
        result = 31 * result + startFactor.hashCode()
        result = 31 * result + endFactor.hashCode()
        result = 31 * result + loopStartFactor.hashCode()
        result = 31 * result + isLooping.hashCode()
        result = 31 * result + isOneShot.hashCode()
        result = 31 * result + isReverse.hashCode()
        result = 31 * result + volume.hashCode()
        result = 31 * result + pan.hashCode()
        result = 31 * result + bankId
        result = 31 * result + color
        return result
    }
}

data class SequenceNote(
    val id: String,
    val padId: Int,
    val tick: Int,         // Step index inside the pattern (e.g. 0 to 63)
    val duration: Int = 1, // Number of ticks it remains active
    val pitchRatio: Float = 1.0f,
    val velocity: Float = 1.0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SequenceNote) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

data class SequencePattern(
    val id: Int,
    val name: String = "Pattern ${id + 1}",
    val notes: List<SequenceNote> = emptyList(),
    val lengthBars: Int = 1 // 1 bar = 16 ticks, 2 bars = 32 ticks, 4 bars = 64 ticks
)
