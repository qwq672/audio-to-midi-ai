package com.audiomidi.ai.model

import com.audiomidi.ai.data.ModelAsset
import com.audiomidi.ai.data.ModelSource

/**
 * State of a download.
 */
sealed class DownloadState {
    /** Not started. */
    object Idle : DownloadState()

    /** Downloading from [sourceUrl], [bytesDownloaded] of [totalBytes]. */
    data class Downloading(
        val sourceUrl: String,
        val sourceType: String,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long = 0
    ) : DownloadState()

    /** Verifying integrity (SHA256). */
    data class Verifying(val bytesDownloaded: Long) : DownloadState()

    /** Download finished successfully, file is at [filePath]. */
    data class Completed(val filePath: String) : DownloadState()

    /** All sources failed. [errors] lists failures per source. */
    data class Failed(val errors: List<SourceError>) : DownloadState()

    /** User cancelled. */
    object Cancelled : DownloadState()
}

/**
 * Failure record for a single source.
 */
data class SourceError(
    val source: ModelSource,
    val reason: String,
    val cause: Throwable? = null
)

/**
 * Per-source result used internally for fallback decisions.
 */
data class DownloadAttempt(
    val source: ModelSource,
    val succeeded: Boolean,
    val bytesDownloaded: Long,
    val durationMs: Long,
    val error: String?
)

/**
 * Result of a full download chain (one model, multiple sources).
 */
data class DownloadResult(
    val assetId: String,
    val finalState: DownloadState,
    val totalElapsedMs: Long,
    val attempts: List<DownloadAttempt>
)

/**
 * Snapshot of a model's availability status for UI display.
 */
data class ModelStatus(
    val asset: ModelAsset,
    val isAvailable: Boolean,
    val localPath: String?,
    val state: DownloadState,
    val lastUpdated: Long = 0L
)
