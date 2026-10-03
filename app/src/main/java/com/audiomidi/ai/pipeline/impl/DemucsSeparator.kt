package com.audiomidi.ai.pipeline.impl

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.audiomidi.ai.pipeline.AudioData
import com.audiomidi.ai.pipeline.SeparatedStem
import com.audiomidi.ai.pipeline.SourceSeparator
import timber.log.Timber

/**
 * Demucs v4 source separator using ONNX Runtime on Android.
 *
 * Supports variants:
 *  - htdemucs      (4-stem)
 *  - htdemucs_ft   (4-stem, fine-tuned)
 *  - htdemucs_6s   (6-stem: vocals, drums, bass, piano, guitar, other)
 *
 * **Status: Skeleton.** Real model I/O is TODO. See file header in earlier
 * commits for the design notes on chunking + overlap-add.
 */
class DemucsSeparator(
    private val context: Context,
    override val modelId: String
) : SourceSeparator {

    private var session: OrtSession? = null
    private var env: OrtEnvironment? = null
    private var numStems: Int = 4

    override suspend fun load(modelPath: String) {
        try {
            env = OrtEnvironment.getEnvironment()

            val options = OrtSession.SessionOptions().apply {
                // NNAPI EP routes to Hexagon NPU on Snapdragon 8 Gen 3.
                addNnapi()
                setIntraOpNumThreads(4)
                setInterOpNumThreads(2)
            }

            val modelFile = java.io.File(modelPath)
            if (!modelFile.exists()) {
                throw java.io.FileNotFoundException("Model file not found: $modelPath")
            }

            session = OrtSession(env, modelFile.absolutePath, options)
            numStems = when (modelId) {
                "demucs_6s" -> 6
                "demucs_4s", "demucs_4s_ft" -> 4
                else -> 4
            }
            Timber.i("Demucs loaded: $modelId, ${modelFile.length()} bytes, $numStems stems expected")
        } catch (t: Throwable) {
            Timber.e(t, "Failed to load Demucs model")
            throw t
        }
    }

    override suspend fun separate(input: AudioData): List<SeparatedStem> {
        val s = session ?: throw IllegalStateException("Demucs not loaded")

        // ----- TODO: Real inference pipeline -----
        // 1. Resample input to 44.1 kHz (Demucs native rate)
        // 2. Convert to mono if needed (Demucs v4 expects stereo)
        // 3. Chunking with 8s segments, 1s overlap
        // 4. For each chunk: build ONNX Tensor [1, channels, length], run session.run(...)
        // 5. Read output tensor of shape [stems, channels, length]
        // 6. Overlap-add per-chunk outputs
        // 7. Return as List<SeparatedStem>

        val stemLabels = if (numStems == 6) {
            listOf("vocals", "drums", "bass", "piano", "guitar", "other")
        } else {
            listOf("vocals", "drums", "bass", "other")
        }

        // --- Placeholder: return empty stems (no audio data) ---
        Timber.w("DemucsSeparator: returning placeholder empty stems. Real inference not yet implemented.")
        return stemLabels.map { label ->
            SeparatedStem(
                label = label,
                audio = AudioData(input.sampleRate, input.channels, FloatArray(0)),
                sourceInstrument = defaultInstrumentFor(label)
            )
        }
    }

    override fun close() {
        session?.close()
        session = null
        // env is shared/global, do not close
    }

    private fun defaultInstrumentFor(stem: String): String = when (stem) {
        "vocals" -> "Voice \"Aahs\""
        "drums" -> "Drums"
        "bass" -> "Electric Bass"
        "piano" -> "Acoustic Grand Piano"
        "guitar" -> "Acoustic Guitar"
        "other" -> "String Ensemble"
        else -> "Unknown"
    }
}
