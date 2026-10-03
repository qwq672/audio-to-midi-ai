package com.audiomidi.ai.pipeline.impl

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OnnxTensor
import android.content.Context
import com.audiomidi.ai.pipeline.AudioData
import com.audiomidi.ai.pipeline.MidiNote
import com.audiomidi.ai.pipeline.Transcriber
import timber.log.Timber
import java.nio.FloatBuffer

/**
 * Spotify Basic Pitch ONNX transcriber.
 *
 * Reference implementation tested in Python (see scripts/demo_audio_to_midi.py):
 *   - Input:  mono audio at 22050 Hz, length 43844 samples (~2 sec/chunk)
 *   - Output: outputs[1] of shape [1, 172, 88] — per-frame per-pitch probability
 *   - Decode: threshold at 0.4, find contiguous runs of frames per pitch,
 *             emit MidiNote(pitch = 21 + idx, startMs, endMs, velocity)
 *
 * The Kotlin port uses the same I/O contract verified in Python.
 */
class BasicPitchTranscriber(
    private val context: Context,
    override val modelId: String
) : Transcriber {

    private var session: OrtSession? = null
    private var inputName: String = ""

    override suspend fun load(modelPath: String) {
        val env = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply {
            addNnapi()
            setIntraOpNumThreads(4)
        }
        val modelFile = java.io.File(modelPath)
        if (!modelFile.exists()) {
            throw java.io.FileNotFoundException("Basic Pitch model not found: $modelPath")
        }
        session = env.createSession(modelFile.absolutePath, options)
        inputName = session!!.inputNames.first()
        Timber.i("Basic Pitch loaded: ${modelFile.length()} bytes, input name: $inputName")
    }

    override suspend fun transcribe(input: AudioData): List<MidiNote> {
        val s = session ?: throw IllegalStateException("Basic Pitch not loaded")
        if (input.samples.isEmpty()) {
            Timber.w("Empty audio input, returning no notes")
            return emptyList()
        }

        // 1. Mix stereo down to mono if needed
        val mono = if (input.channels == 2) {
            val outSize = input.samples.size / 2
            FloatArray(outSize) { i ->
                (input.samples[i * 2] + input.samples[i * 2 + 1]) * 0.5f
            }
        } else {
            input.samples
        }

        // 2. Linear-resample to 22050 Hz if source rate differs
        val resampled = if (input.sampleRate != TARGET_SAMPLE_RATE) {
            linearResample(mono, input.sampleRate, TARGET_SAMPLE_RATE)
        } else {
            mono
        }

        Timber.i("BasicPitch transcribe: ${mono.size} samples mono -> ${resampled.size} @ ${TARGET_SAMPLE_RATE}Hz, ${resampled.size / TARGET_SAMPLE_RATE}.1f sec")

        // 3. Chunk into INPUT_LEN-sample segments, zero-pad last chunk
        val nChunks = (resampled.size + INPUT_LEN - 1) / INPUT_LEN
        val allNotes = mutableListOf<MidiNote>()
        val env = OrtEnvironment.getEnvironment()

        for (chunkIdx in 0 until nChunks) {
            val start = chunkIdx * INPUT_LEN
            val chunk = FloatArray(INPUT_LEN) { i ->
                val srcIdx = start + i
                if (srcIdx < resampled.size) resampled[srcIdx] else 0f
            }

            // 4. Build ONNX Tensor [1, INPUT_LEN, 1]
            // onnxruntime-android 1.17.0 doesn't have createTensor(env, FloatArray, LongArray)
            // overload — must wrap FloatArray in FloatBuffer first.
            val floatBuffer = FloatBuffer.wrap(chunk)
            val inputTensor = OnnxTensor.createTensor(
                env, floatBuffer, longArrayOf(1, INPUT_LEN.toLong(), 1)
            )

            val result = try {
                s.run(mapOf(inputName to inputTensor))
            } finally {
                inputTensor.close()
            }

            try {
                // 5. outputs[1] is the note probability tensor [1, 172, 88]
                val noteTensor = result.get(1)
                try {
                    @Suppress("UNCHECKED_CAST")
                    val value = noteTensor.value as Array<Array<FloatArray>>  // [1][172][88]
                    val probs = value[0]  // [172][88]
                    decodeNotesFromChunk(probs, chunkIdx, allNotes)
                } finally {
                    noteTensor.close()
                }
            } finally {
                result.close()
            }

            if (chunkIdx % 10 == 0) {
                Timber.i("  chunk ${chunkIdx + 1}/$nChunks done, notes so far: ${allNotes.size}")
            }
        }

        Timber.i("BasicPitch transcribe done: ${allNotes.size} notes total")
        return allNotes
    }

    /**
     * Scan probs[172][88] for contiguous frame runs above THRESHOLD per pitch,
     * emit MidiNote for each run.
     */
    private fun decodeNotesFromChunk(
        probs: Array<FloatArray>,
        chunkIdx: Int,
        out: MutableList<MidiNote>
    ) {
        // frame_ms = (INPUT_LEN * 1000 / SR) / N_FRAMES ≈ 11.6 ms
        val frameMs = (INPUT_LEN * 1000.0f / TARGET_SAMPLE_RATE) / N_FRAMES

        for (pitchIdx in 0 until N_PITCHES) {
            var i = 0
            while (i < N_FRAMES) {
                if (probs[i][pitchIdx] > THRESHOLD) {
                    // Find end of contiguous run
                    var j = i
                    var peak = 0f
                    while (j < N_FRAMES && probs[j][pitchIdx] > THRESHOLD) {
                        if (probs[j][pitchIdx] > peak) peak = probs[j][pitchIdx]
                        j++
                    }
                    val startMs = ((chunkIdx * N_FRAMES + i) * frameMs).toLong()
                    val endMs = ((chunkIdx * N_FRAMES + j - 1) * frameMs).toLong()
                    val velocity = (60 + peak * 60).toInt().coerceIn(0, 127)
                    val midiPitch = MIDI_OFFSET + pitchIdx
                    out.add(MidiNote(midiPitch, startMs, endMs, velocity))
                    i = j
                } else {
                    i++
                }
            }
        }
    }

    override fun close() {
        session?.close()
        session = null
    }

    /**
     * Simple linear interpolation resampler. Good enough for Basic Pitch which
     * is robust to minor sample rate mismatches. For higher quality use
     * a proper resampling library (e.g., soxr via JNI).
     */
    private fun linearResample(
        input: FloatArray,
        inRate: Int,
        outRate: Int
    ): FloatArray {
        if (inRate == outRate) return input
        val ratio = outRate.toFloat() / inRate.toFloat()
        val outLen = (input.size * ratio).toInt()
        return FloatArray(outLen) { i ->
            val srcIdx = i / ratio
            val srcLo = srcIdx.toInt()
            val srcHi = (srcLo + 1).coerceAtMost(input.size - 1)
            val frac = srcIdx - srcLo
            if (srcLo >= input.size) 0f
            else input[srcLo] * (1f - frac) + input[srcHi] * frac
        }
    }

    companion object {
        // === Model I/O contract (verified in Python) ===
        const val TARGET_SAMPLE_RATE = 22050
        const val INPUT_LEN = 43844
        const val N_FRAMES = 172
        const val N_PITCHES = 88
        const val MIDI_OFFSET = 21   // MIDI 21 = A0, lowest piano key
        const val NOTE_OUTPUT_INDEX = 1  // outputs[1] is the working pitch prob
        const val THRESHOLD = 0.4f
    }
}
