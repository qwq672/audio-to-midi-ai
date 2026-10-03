package com.audiomidi.ai.data

import kotlinx.serialization.Serializable

/**
 * A genre preset. Each genre defines a recommended combination of models
 * (one per pipeline stage). Users can override any entry in the UI; the
 * saved override creates a custom preset.
 *
 * @property id Genre identifier: "pop", "rock", "electronic", "classical", etc.
 * @property displayName Localized name. The UI also looks up strings.xml
 *                        for an i18n version ("genre_<id>").
 * @property emoji Decorative emoji for the genre picker.
 * @property description Short description of what the preset is optimized for.
 * @property models Map of ModelRole -> modelId. Each role picks one model.
 *                  Stages without a recommended model are absent from the map
 *                  (handled as "skip" in the pipeline).
 * @property maxInstruments Hard cap on number of MIDI tracks in the output.
 *                          Prevents hallucination of dozens of instruments.
 * @property confidenceThreshold YamNet classification confidence required to
 *                                keep an instrument. Lower = more permissive.
 * @property minNoteDurationMs Minimum note duration; shorter notes are filtered
 *                              out as likely false positives.
 */
@Serializable
data class GenrePreset(
    val id: String,
    val displayName: String,
    val emoji: String,
    val description: String,
    val models: Map<String, String>,
    val maxInstruments: Int = 12,
    val confidenceThreshold: Float = 0.6f,
    val minNoteDurationMs: Int = 2000
) {
    init {
        require(id.matches(Regex("^[a-z0-9_]+$"))) {
            "genre id must be snake_case [a-z0-9_], got: $id"
        }
        require(maxInstruments in 2..30) {
            "maxInstruments must be 2..30, got $maxInstruments"
        }
        require(confidenceThreshold in 0.1f..0.95f) {
            "confidenceThreshold must be 0.1..0.95, got $confidenceThreshold"
        }
    }

    /**
     * Convert string-keyed map to ModelRole-keyed map for runtime use.
     */
    fun modelMap(): Map<ModelRole, String> {
        return models.mapNotNull { (roleStr, modelId) ->
            runCatching { ModelRole.valueOf(roleStr) }.getOrNull()?.let { it to modelId }
        }.toMap()
    }
}
