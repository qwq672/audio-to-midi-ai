package com.audiomidi.ai.data

import kotlinx.serialization.Serializable

/**
 * Runtime configuration of the transcription pipeline.
 *
 * This is constructed by combining a GenrePreset with user overrides,
 * and stored persistently as a custom preset when modified.
 *
 * @property name Display name (for custom presets).
 * @property baseGenreId Genre this preset was derived from.
 * @property models Map of ModelRole -> modelId, possibly with user overrides
 *                  on top of the genre's default.
 * @property maxInstruments Confidence threshold etc. inherit from genre but
 *                          can be tweaked.
 * @property isCustom If true, this is a user-saved preset (otherwise system-defined).
 */
@Serializable
data class PipelineConfig(
    val name: String,
    val baseGenreId: String,
    val models: Map<String, String>,
    val maxInstruments: Int = 12,
    val confidenceThreshold: Float = 0.6f,
    val minNoteDurationMs: Int = 2000,
    val isCustom: Boolean = false
) {
    /**
     * Apply an override: replace the model for a single role.
     */
    fun withOverride(role: ModelRole, modelId: String?): PipelineConfig {
        val newModels = models.toMutableMap()
        if (modelId == null) {
            newModels.remove(role.name)
        } else {
            newModels[role.name] = modelId
        }
        return copy(models = newModels, isCustom = true)
    }
}
