package com.audiomidi.ai.data

import kotlinx.serialization.Serializable

/**
 * User's download region preference.
 */
@Serializable
enum class DownloadRegion {
    /** Auto-detect by speed testing each mirror's TTFB on first launch. */
    AUTO,

    /** China: prefer hf-mirror → ModelScope → OpenI → HuggingFace. */
    CHINA,

    /** Global: prefer HuggingFace → hf-mirror → ModelScope. */
    GLOBAL
}

/**
 * Persistent user settings.
 */
@Serializable
data class UserSettings(
    val downloadRegion: DownloadRegion = DownloadRegion.AUTO,
    val maxParallelDownloads: Int = 3,
    val autoDownloadOnGenreSelect: Boolean = true,
    val preferNpuAcceleration: Boolean = true,
    val outputDir: String = "Downloads/AudioToMidi",
    val lastUsedPresetId: String = "pop",
    val customPresets: List<PipelineConfig> = emptyList()
)
