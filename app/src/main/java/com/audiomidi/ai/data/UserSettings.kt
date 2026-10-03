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
    // NNAPI EP on Snapdragon NPU drivers can SIGSEGV on certain ONNX ops
    // (e.g., dynamic-shape convolutions in Basic Pitch). Default OFF for
    // stability. Users can opt in via Settings if they want to try NPU.
    val preferNpuAcceleration: Boolean = false,
    val outputDir: String = "Downloads/AudioToMidi",
    val lastUsedPresetId: String = "folk",
    val customPresets: List<PipelineConfig> = emptyList()
)
