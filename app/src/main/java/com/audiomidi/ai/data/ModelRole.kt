package com.audiomidi.ai.data

import kotlinx.serialization.Serializable

/**
 * Role of a model in the pipeline. Each role maps to a pipeline stage.
 */
@Serializable
enum class ModelRole {
    /** Source separator (e.g., Demucs, Spleeter). */
    SOURCE_SEPARATOR,

    /** Vocal transcription model. */
    VOCAL_TRANSCRIBER,

    /** Bass transcription model. */
    BASS_TRANSCRIBER,

    /** Piano transcription model. */
    PIANO_TRANSCRIBER,

    /** Guitar transcription model. */
    GUITAR_TRANSCRIBER,

    /** Drum onset processor. */
    DRUM_PROCESSOR,

    /** Other/harmonic instruments transcriber. */
    OTHER_TRANSCRIBER,

    /** End-to-end multi-instrument transcriber (e.g., MT3). */
    END_TO_END_TRANSCRIBER,

    /** Instrument/timbre classifier (e.g., YamNet, OpenL3). */
    INSTRUMENT_CLASSIFIER,

    /** Secondary source separator for the "other" stem. */
    SECONDARY_SEPARATOR,

    /** Drum type classifier (909/808/live). */
    DRUM_TYPE_CLASSIFIER
}
