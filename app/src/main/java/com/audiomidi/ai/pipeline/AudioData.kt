package com.audiomidi.ai.pipeline

/**
 * Audio sample data (mono or stereo float PCM).
 *
 * @property sampleRate e.g., 44100
 * @property channels 1 = mono, 2 = stereo
 * @property samples Flat float array. Length = numFrames * channels.
 *                   Interleaved order: [L0, R0, L1, R1, ...] for stereo.
 */
data class AudioData(
    val sampleRate: Int,
    val channels: Int,
    val samples: FloatArray
) {
    val numFrames: Int get() = if (channels == 0) 0 else samples.size / channels
    val durationSeconds: Double get() = if (sampleRate == 0) 0.0 else numFrames.toDouble() / sampleRate

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioData) return false
        return sampleRate == other.sampleRate &&
            channels == other.channels &&
            samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int {
        var result = sampleRate
        result = 31 * result + channels
        result = 31 * result + samples.contentHashCode()
        return result
    }
}

/**
 * A single separated stem from source separation.
 *
 * @property label "vocals", "drums", "bass", "piano", "guitar", "other"
 * @property audio The audio data for this stem
 * @property sourceInstrument Default guess of instrument for this stem
 *                            (used as fallback if classifier fails).
 */
data class SeparatedStem(
    val label: String,
    val audio: AudioData,
    val sourceInstrument: String
)

/**
 * A single transcribed MIDI note.
 *
 * @property pitch MIDI note number 0..127
 * @property startMs Onset time in milliseconds from start of audio
 * @property endMs Offset time in milliseconds
 * @property velocity 0..127
 */
data class MidiNote(
    val pitch: Int,
    val startMs: Long,
    val endMs: Long,
    val velocity: Int = 80
) {
    init {
        require(pitch in 0..127) { "pitch out of range: $pitch" }
        require(endMs >= startMs) { "endMs ($endMs) < startMs ($startMs)" }
        require(velocity in 0..127) { "velocity out of range: $velocity" }
    }

    val durationMs: Long get() = endMs - startMs
}

/**
 * A MIDI track containing notes, with metadata for the instrument on this track.
 *
 * @property programNumber General MIDI program number 0..127
 * @property instrumentName Display name (e.g., "Acoustic Grand Piano")
 * @property notes Notes on this track, sorted by startMs.
 * @property channel MIDI channel 0..15 (9 is reserved for drums in GM).
 */
data class MidiTrack(
    val programNumber: Int,
    val instrumentName: String,
    val channel: Int,
    val notes: List<MidiNote>
) {
    init {
        require(programNumber in 0..127) { "programNumber out of range: $programNumber" }
        require(channel in 0..15) { "channel out of range: $channel" }
    }
}

/**
 * Final output: a complete multi-track MIDI file (in memory, not yet serialized).
 */
data class MidiComposition(
    val tracks: List<MidiTrack>,
    val ticksPerQuarter: Int = 480,
    val microsPerQuarter: Int = 500000 // 120 BPM default
)
