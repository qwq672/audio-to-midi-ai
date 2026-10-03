package com.audiomidi.ai.data

import kotlinx.serialization.Serializable

/**
 * A model entry in the registry. Each entry may have multiple download sources
 * (multi-mirror fallback) and metadata describing how to use the model.
 *
 * @property id Stable identifier used in genre presets and pipeline configs.
 *              Example: "demucs_6s", "basic_pitch", "mt3".
 * @property displayName Human-readable name shown in UI.
 * @property role Pipeline stage this model serves.
 * @property format "ONNX" | "TFLITE" | "NONE" (NONE for built-in code like librosa onset).
 * @property sizeBytes Expected file size in bytes (for progress display & space check).
 * @property sha256 SHA-256 of the file, used for integrity verification after download.
 *                  May be empty for "internal" models.
 * @property isDefault If true, downloaded automatically on first launch alongside the
 *                     default genre preset.
 * @property genreTags Genres where this model is relevant: ["pop", "rock", "electronic", ...].
 *                      Used to suggest downloads when user switches genre.
 * @property sources Ordered fallback chain. ModelManager tries each in priority order
 *                   (after region filtering).
 * @property bundled If true, the file is bundled inside the APK (no download needed).
 * @property minAppVersion Minimum app version required to use this model.
 */
@Serializable
data class ModelAsset(
    val id: String,
    val displayName: String,
    val role: ModelRole,
    val format: String,
    val sizeBytes: Long,
    val sha256: String = "",
    val isDefault: Boolean = false,
    val genreTags: List<String> = emptyList(),
    val sources: List<ModelSource> = emptyList(),
    val bundled: Boolean = false,
    val minAppVersion: String = "0.1.0"
) {
    init {
        require(id.matches(Regex("^[a-z0-9_]+$"))) {
            "id must be snake_case [a-z0-9_], got: $id"
        }
        require(format in setOf("ONNX", "TFLITE", "NONE")) {
            "format must be ONNX, TFLITE, or NONE, got: $format"
        }
        require(bundled || sources.isNotEmpty()) {
            "Model $id must either be bundled or have at least one source URL"
        }
    }
}
