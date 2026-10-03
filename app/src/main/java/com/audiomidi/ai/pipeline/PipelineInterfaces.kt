package com.audiomidi.ai.pipeline

/**
 * Stage 1: Source separation.
 *
 * Takes a mixed audio input and produces multiple stems,
 * each containing (ideally) only one instrument group.
 *
 * Implementations:
 * - [com.audiomidi.ai.pipeline.impl.DemucsSeparator]
 * - [com.audiomidi.ai.pipeline.impl.SpleeterSeparator] (legacy)
 */
interface SourceSeparator {
    /** Model identifier (matches manifest.json entry). */
    val modelId: String

    /**
     * Load the model into memory. Called once before processing.
     */
    suspend fun load(modelPath: String)

    /**
     * @param input Mixed audio input
     * @return List of separated stems (e.g., vocals, drums, bass, piano, guitar, other)
     */
    suspend fun separate(input: AudioData): List<SeparatedStem>

    /**
     * Release native resources.
     */
    fun close()
}

/**
 * Stage 2: Per-stem MIDI transcription.
 *
 * Takes a single stem audio and produces MIDI notes.
 * Implementations:
 * - [com.audiomidi.ai.pipeline.impl.BasicPitchTranscriber]
 * - [com.audiomidi.ai.pipeline.impl.KongPianoTranscriber]
 * - [com.audiomidi.ai.pipeline.impl.MT3Transcriber] (end-to-end)
 */
interface Transcriber {
    val modelId: String

    suspend fun load(modelPath: String)

    /**
     * Transcribe a single stem into MIDI notes.
     */
    suspend fun transcribe(input: AudioData): List<MidiNote>

    fun close()
}

/**
 * Stage 3: Instrument classification.
 *
 * Identifies which General MIDI program best matches a stem's timbre.
 * Used to assign [MidiTrack.programNumber].
 *
 * Implementations:
 * - [com.audiomidi.ai.pipeline.impl.YamNetClassifier]
 * - [com.audiomidi.ai.pipeline.impl.OpenL3Classifier]
 */
interface InstrumentClassifier {
    val modelId: String

    suspend fun load(modelPath: String)

    /**
     * @param audio Stem audio
     * @return Top-1 instrument name and the General MIDI program number to use.
     */
    suspend fun classify(audio: AudioData): Classification

    fun close()
}

/**
 * Output of [InstrumentClassifier.classify].
 */
data class Classification(
    val instrumentName: String,
    val gmProgramNumber: Int,
    val confidence: Float
)

/**
 * Stage 4: Drum onset detection (separate interface because drum processing
 * doesn't yield pitched MIDI notes).
 */
interface DrumProcessor {
    val modelId: String

    suspend fun load(modelPath: String)

    /**
     * @return Onset times in ms, with type ("kick", "snare", "hihat", "tom", "clap", ...)
     *         The pitch field carries the GM drum key (35=kick, 38=snare, 42=hihat, ...)
     */
    suspend fun process(input: AudioData): List<MidiNote>

    fun close()
}

/**
 * Stage 5: MIDI writer/serializer.
 *
 * Takes a [MidiComposition] and serializes it to Standard MIDI File (SMF) bytes.
 * Implementation: pure Kotlin (no native deps), supports format 1 (multi-track).
 */
interface MidiWriter {
    suspend fun write(composition: MidiComposition): ByteArray
}
