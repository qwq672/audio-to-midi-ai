package com.audiomidi.ai.pipeline.impl

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.audiomidi.ai.pipeline.AudioData
import com.audiomidi.ai.pipeline.MidiNote
import com.audiomidi.ai.pipeline.Transcriber
import timber.log.Timber

/**
 * Spotify's Basic Pitch model — lightweight CNN for monophonic & some polyphonic
 * pitch transcription. Works on vocals, bass, guitar, melodic stems.
 *
 * **Status: Skeleton.** Real inference TODO; see file header doc.
 */
class BasicPitchTranscriber(
    private val context: Context,
    override val modelId: String
) : Transcriber {

    private var session: OrtSession? = null

    override suspend fun load(modelPath: String) {
        val env = OrtEnvironment.getEnvironment()  // local non-null val
        val options = OrtSession.SessionOptions().apply {
            addNnapi()
            setIntraOpNumThreads(4)
        }
        val modelFile = java.io.File(modelPath)
        if (!modelFile.exists()) {
            throw java.io.FileNotFoundException("Basic Pitch model not found: $modelPath")
        }
        session = OrtSession(env, modelFile.absolutePath, options)
        Timber.i("Basic Pitch loaded: ${modelFile.length()} bytes")
    }

    override suspend fun transcribe(input: AudioData): List<MidiNote> {
        val s = session ?: throw IllegalStateException("Basic Pitch not loaded")

        // ----- TODO: Real inference -----
        // 1. Resample input to 22050 Hz
        // 2. Normalize to peak 1.0
        // 3. Build input tensor of shape [1, num_frames, 264] (n_mels) — OR
        //    [1, audio_length] depending on the ONNX export variant.
        // 4. Run inference → 3 outputs: note, onset, frame probabilities.
        // 5. Peak-pick note output above threshold (e.g. 0.5) → emit MidiNotes.

        Timber.w("BasicPitchTranscriber: returning placeholder empty notes.")
        return emptyList()
    }

    override fun close() {
        session?.close()
        session = null
    }
}
