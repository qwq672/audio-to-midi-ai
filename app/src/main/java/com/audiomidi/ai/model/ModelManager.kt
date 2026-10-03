package com.audiomidi.ai.model

import android.content.Context
import com.audiomidi.ai.data.DownloadRegion
import com.audiomidi.ai.data.ModelAsset
import com.audiomidi.ai.data.ModelSource
import com.audiomidi.ai.data.UserSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.security.MessageDigest

/**
 * Central manager for model files on the local filesystem.
 *
 * Responsibilities:
 * - Track which models are already downloaded
 * - Download missing models, trying multiple mirror sources with fallback
 * - Verify SHA256 integrity of downloaded files
 * - Provide paths for pipeline components to load models
 * - Allow user to delete models to free storage
 *
 * Storage layout:
 *   /data/data/com.audiomidi.ai/files/models/<model_id>.<ext>
 *   /data/data/com.audiomidi.ai/files/models/.partial/<model_id>.<ext>.partial
 *
 * @property appContext Application context for file access
 * @property settings   User settings (region, parallel downloads)
 */
class ModelManager(
    private val appContext: Context,
    private val settings: UserSettings
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val _downloadEvents = MutableSharedFlow<Pair<String, DownloadState>>(
        extraBufferCapacity = 64
    )
    val downloadEvents: SharedFlow<Pair<String, DownloadState>> = _downloadEvents.asSharedFlow()

    private val modelsDir: File = File(appContext.filesDir, "models").apply {
        if (!exists()) mkdirs()
    }
    private val partialDir: File = File(modelsDir, ".partial").apply {
        if (!exists()) mkdirs()
    }

    private val downloader = Downloader(
        maxParallel = settings.maxParallelDownloads
    )

    /**
     * Where a model file lives on disk. May not exist yet.
     */
    fun localPathFor(asset: ModelAsset): File {
        val ext = when (asset.format) {
            "ONNX" -> "onnx"
            "TFLITE" -> "tflite"
            else -> "bin"
        }
        return File(modelsDir, "${asset.id}.$ext")
    }

    /**
     * Whether a model is fully downloaded AND (if applicable) SHA256-verified.
     */
    fun isAvailable(asset: ModelAsset): Boolean {
        if (asset.bundled) return true
        val path = localPathFor(asset)
        if (!path.exists()) return false
        if (path.length() != asset.sizeBytes) return false
        // Skip SHA check if no hash stored (TODO_VERIFY_AT_RUNTIME placeholder).
        if (asset.sha256.isBlank() || asset.sha256 == "TODO_VERIFY_AT_RUNTIME") return true
        return verifySha256(path, asset.sha256)
    }

    /**
     * Sort [asset.sources] by region preference + priority. Returns an ordered list to try.
     */
    fun orderedSourcesFor(asset: ModelAsset): List<ModelSource> {
        val region = settings.downloadRegion
        // 1. Filter by region match (or "auto" / "global" fallback)
        val regionFiltered = asset.sources.filter { source ->
            when (region) {
                DownloadRegion.AUTO -> true // keep all
                DownloadRegion.CHINA -> source.region == "china" || source.region == "global"
                DownloadRegion.GLOBAL -> source.region == "global" || source.region == "china"
            }
        }
        // 2. Sort: preferred region first (within priority), then by priority asc.
        val regionRank: (ModelSource) -> Int = { src ->
            when (region) {
                DownloadRegion.CHINA -> if (src.region == "china") 0 else 1
                DownloadRegion.GLOBAL -> if (src.region == "global") 0 else 1
                DownloadRegion.AUTO -> 1
            }
        }
        return regionFiltered.sortedWith(
            compareBy(regionRank, { it.priority })
        )
    }

    /**
     * Download a model, trying each source in order. Emits progress via [downloadEvents].
     *
     * Supports resuming partial downloads via HTTP Range header.
     */
    suspend fun ensureDownloaded(asset: ModelAsset) {
        if (isAvailable(asset)) {
            _downloadEvents.emit(asset.id to DownloadState.Completed(localPathFor(asset).absolutePath))
            return
        }
        if (asset.bundled) {
            // Bundled assets don't need downloading; their path is in assets/.
            _downloadEvents.emit(asset.id to DownloadState.Completed("assets/${asset.id}"))
            return
        }

        val sources = orderedSourcesFor(asset)
        if (sources.isEmpty()) {
            _downloadEvents.emit(asset.id to DownloadState.Failed(
                listOf(SourceError(
                    source = ModelSource("", "global", 1, "none"),
                    reason = "No download sources configured for model ${asset.id}"
                ))
            ))
            return
        }

        val errors = mutableListOf<SourceError>()
        for (source in sources) {
            val target = localPathFor(asset)
            val partial = File(partialDir, "${asset.id}.${source.type}.partial")
            try {
                downloader.downloadWithResume(
                    url = source.url,
                    targetFile = target,
                    partialFile = partial,
                    expectedSize = asset.sizeBytes
                ) { downloaded, total, bps ->
                    // Emit progress
                    val state = DownloadState.Downloading(
                        sourceUrl = source.url,
                        sourceType = source.type,
                        bytesDownloaded = downloaded,
                        totalBytes = total,
                        bytesPerSecond = bps
                    )
                    // Fire and forget; if no subscriber, that's fine.
                    kotlinx.coroutines.runBlocking {
                        _downloadEvents.emit(asset.id to state)
                    }
                }

                // Download complete, verify SHA256
                _downloadEvents.emit(asset.id to DownloadState.Verifying(target.length()))
                if (asset.sha256.isNotBlank() && asset.sha256 != "TODO_VERIFY_AT_RUNTIME") {
                    if (!verifySha256(target, asset.sha256)) {
                        target.delete()
                        errors.add(SourceError(
                            source = source,
                            reason = "SHA256 verification failed"
                        ))
                        continue // try next source
                    }
                }
                _downloadEvents.emit(asset.id to DownloadState.Completed(target.absolutePath))
                return
            } catch (t: Throwable) {
                Timber.w(t, "Download from ${source.url} failed")
                errors.add(SourceError(source = source, reason = t.message ?: "Unknown error", cause = t))
                // continue to next source
            }
        }
        _downloadEvents.emit(asset.id to DownloadState.Failed(errors))
    }

    /**
     * Delete a model from local storage.
     */
    fun delete(asset: ModelAsset): Boolean {
        val path = localPathFor(asset)
        return path.delete().also { ok ->
            if (ok) Timber.i("Deleted model ${asset.id} at ${path.absolutePath}")
            else Timber.w("Failed to delete model ${asset.id}")
        }
    }

    /**
     * Used by RegionDetector: probe each source's TTFB to pick fastest.
     */
    suspend fun probeSourceLatency(source: ModelSource): Long {
        return downloader.probeTtfb(source.url)
    }

    private fun verifySha256(file: File, expected: String): Boolean {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = fis.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        val actual = md.digest().joinToString("") { "%02x".format(it) }
        val ok = actual.equals(expected, ignoreCase = true)
        if (!ok) Timber.w("SHA mismatch for ${file.name}: expected=$expected actual=$actual")
        return ok
    }
}
