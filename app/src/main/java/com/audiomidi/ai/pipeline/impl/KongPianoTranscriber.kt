package com.audiomidi.ai.pipeline.impl

import android.content.Context
import com.audiomidi.ai.pipeline.AudioData
import com.audiomidi.ai.pipeline.MidiNote
import com.audiomidi.ai.pipeline.Transcriber
import timber.log.Timber
import java.io.File

/**
 * Bytedance Piano Transcription model — high-precision piano-only transcription.
 *
 * Better than Basic Pitch for piano stems specifically.
 *
 * **Status: Skeleton.**
 * ONNX session creation pending; see https://github.com/bytedance/piano_transcription
 */
class KongPianoTranscriber(
    private val context: Context,
    override val modelId: String
) : Transcriber {

    private var modelPath: String? = null

    override suspend fun load(modelPath: String) {
        val file = File(modelPath)
        if (!file.exists()) {
            throw java.io.FileNotFoundException("Piano model not found: $modelPath")
        }
        this.modelPath = modelPath
        Timber.i("KongPianoTranscriber: model path set to $modelPath (ONNX session TODO)")
    }

    override suspend fun transcribe(input: AudioData): List<MidiNote> {
        if (modelPath == null) throw IllegalStateException("Piano model not loaded")
        // TODO: actual inference
        Timber.w("KongPianoTranscriber: returning placeholder empty notes.")
        return emptyList()
    }

    override fun close() {
        // Nothing allocated yet
    }
}
