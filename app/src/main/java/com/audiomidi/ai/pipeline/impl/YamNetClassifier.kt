package com.audiomidi.ai.pipeline.impl

import android.content.Context
import com.audiomidi.ai.pipeline.AudioData
import com.audiomidi.ai.pipeline.Classification
import com.audiomidi.ai.pipeline.InstrumentClassifier
import timber.log.Timber
import java.io.File

/**
 * Google YamNet (AudioSet) — audio event classifier in 521 classes.
 *
 * Used to map an audio stem to the closest General MIDI program.
 *
 * **Status: Skeleton.**
 * TFLite session creation pending; see https://www.tensorflow.org/hub/tutorials/yamnet
 */
class YamNetClassifier(
    private val context: Context,
    override val modelId: String
) : InstrumentClassifier {

    private var modelPath: String? = null

    override suspend fun load(modelPath: String) {
        val file = File(modelPath)
        if (!file.exists()) {
            throw java.io.FileNotFoundException("YamNet model not found: $modelPath")
        }
        this.modelPath = modelPath
        Timber.i("YamNet: model path set to $modelPath (TFLite session TODO)")
    }

    override suspend fun classify(audio: AudioData): Classification {
        if (modelPath == null) throw IllegalStateException("YamNet not loaded")
        // TODO: actual inference
        // 1. Resample to 16 kHz mono
        // 2. Compute log-mel spectrogram (96 mel bins, 25ms window, 10ms hop)
        // 3. Run TFLite inference
        // 4. Pick top-1 class
        // 5. Map YamNet class name → General MIDI program via static table
        Timber.w("YamNetClassifier: returning placeholder classification (Acoustic Grand Piano).")
        return Classification(
            instrumentName = "Acoustic Grand Piano",
            gmProgramNumber = 0,
            confidence = 0.5f
        )
    }

    override fun close() {
        // Nothing allocated yet
    }

    companion object {
        /**
         * Mapping from YamNet class names to General MIDI program numbers.
         * This is a partial table; full table lives in assets/yamnet_to_gm.json
         * (to be added in a future commit).
         */
        val YAMNET_TO_GM: Map<String, Int> = mapOf(
            "Acoustic guitar" to 25,
            "Electric guitar" to 28,
            "Bass guitar" to 33,
            "Piano" to 0,
            "Synthesizer" to 82,
            "Strings" to 49,
            "Brass" to 62,
            "Organ" to 18,
            "Choir" to 53,
            "Drums" to 0  // drum channel handled separately
        )
    }
}
