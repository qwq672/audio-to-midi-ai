package com.audiomidi.ai.data

import kotlinx.serialization.Serializable

/**
 * A single download source for a model. Multiple sources per model form
 * the multi-mirror fallback chain.
 *
 * @property url Direct download URL.
 * @property region "china" | "global" | "auto". Used by ModelManager to pick mirrors.
 * @property priority 1 = highest priority. Lower numbers tried first within the user's region.
 * @property type "hf-mirror" | "huggingface" | "modelscope" | "openi" | "github" | "gitee" | "internal"
 */
@Serializable
data class ModelSource(
    val url: String,
    val region: String,
    val priority: Int,
    val type: String
) {
    init {
        require(region in setOf("china", "global", "auto")) {
            "region must be 'china', 'global', or 'auto', got: $region"
        }
        require(priority >= 1) { "priority must be >= 1, got: $priority" }
    }
}
