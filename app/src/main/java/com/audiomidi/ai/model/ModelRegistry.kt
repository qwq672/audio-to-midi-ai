package com.audiomidi.ai.model

import android.content.Context
import com.audiomidi.ai.data.GenrePreset
import com.audiomidi.ai.data.ModelAsset
import com.audiomidi.ai.data.ModelRole
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJson
import timber.log.Timber
import java.io.IOException

/**
 * Loads and parses the bundled manifest.json (in app/src/main/assets/).
 *
 * Provides:
 * - Lookup by model id
 * - List by genre tag
 * - List by role (for "show all available models in this role" UI)
 * - List by "is default" (for first-launch auto-download)
 */
class ModelRegistry(
    private val assets: List<ModelAsset>,
    val manifestVersion: String
) {
    private val byId: Map<String, ModelAsset> = assets.associateBy { it.id }

    fun all(): List<ModelAsset> = assets
    fun byId(id: String): ModelAsset? = byId[id]
    fun byRole(role: ModelRole): List<ModelAsset> = assets.filter { it.role == role }
    fun byGenre(genreId: String): List<ModelAsset> = assets.filter { genreId in it.genreTags }
    fun defaults(): List<ModelAsset> = assets.filter { it.isDefault }
    fun bundled(): List<ModelAsset> = assets.filter { it.bundled }
    fun downloadable(): List<ModelAsset> = assets.filter { !it.bundled }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Empty registry used before async load completes. */
        fun placeholder(): ModelRegistry = ModelRegistry(emptyList(), "0-placeholder")

        suspend fun load(context: Context): ModelRegistry = kotlinx.coroutines.withContext(
            kotlinx.coroutines.Dispatchers.IO
        ) {
            try {
                val raw = context.assets.open("manifest.json").bufferedReader().use { it.readText() }
                val obj = json.decodeFromString<JsonObject>(raw)
                val version = obj["manifestVersion"]?.toString()?.trim('"') ?: "0"
                val modelsArray = obj["models"]
                val models: List<ModelAsset> = if (modelsArray != null) {
                    json.decodeFromJson(modelsArray)
                } else {
                    emptyList()
                }
                ModelRegistry(models, version)
            } catch (e: IOException) {
                Timber.e(e, "Failed to load manifest.json from assets")
                placeholder()
            }
        }
    }
}

/**
 * Lightweight wrapper for parsing genre_presets.json.
 */
class GenrePresetRegistry(
    val presets: List<GenrePreset>
) {
    fun byId(id: String): GenrePreset? = presets.firstOrNull { it.id == id }
    fun defaultPreset(): GenrePreset =
        presets.firstOrNull { it.id == "pop" } ?: presets.first()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun placeholder(): GenrePresetRegistry = GenrePresetRegistry(emptyList())

        suspend fun load(context: Context): GenrePresetRegistry = kotlinx.coroutines.withContext(
            kotlinx.coroutines.Dispatchers.IO
        ) {
            try {
                val raw = context.assets.open("genre_presets.json").bufferedReader().use { it.readText() }
                val obj = json.decodeFromString<JsonObject>(raw)
                val presetsArray = obj["presets"]
                val list: List<GenrePreset> = if (presetsArray != null) {
                    json.decodeFromJson(presetsArray)
                } else {
                    emptyList()
                }
                GenrePresetRegistry(list)
            } catch (e: IOException) {
                Timber.e(e, "Failed to load genre_presets.json")
                placeholder()
            }
        }
    }
}

/**
 * Auto-detects the best download region by probing TTFB of common sources.
 * Used when [com.audiomidi.ai.data.DownloadRegion.AUTO] is selected.
 */
class RegionDetector(
    private val downloader: Downloader
) {
    /**
     * Probes a representative set of sources. Returns CHINA if hf-mirror
     * is significantly faster than huggingface.co, else GLOBAL.
     */
    suspend fun detect(): com.audiomidi.ai.data.DownloadRegion {
        val candidates = listOf(
            "https://hf-mirror.com" to "china",
            "https://huggingface.co" to "global",
            "https://www.modelscope.cn" to "china"
        )
        val results = candidates.map { (url, region) ->
            region to downloader.probeTtfb(url)
        }.sortedBy { it.second }

        val best = results.first()
        val second = results.getOrNull(1)
        return if (best.second == Long.MAX_VALUE) {
            com.audiomidi.ai.data.DownloadRegion.CHINA
        } else if (second != null && second.second != Long.MAX_VALUE
            && best.second * 3 < second.second
        ) {
            if (best.first == "china") com.audiomidi.ai.data.DownloadRegion.CHINA
            else com.audiomidi.ai.data.DownloadRegion.GLOBAL
        } else {
            com.audiomidi.ai.data.DownloadRegion.CHINA
        }
    }
}
