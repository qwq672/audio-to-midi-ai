package com.audiomidi.ai.pipeline

import com.audiomidi.ai.data.PipelineConfig
import com.audiomidi.ai.model.ModelManager
import com.audiomidi.ai.model.ModelRegistry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Stage of the pipeline currently executing. Used for progress UI.
 */
sealed class PipelineStage {
    /** Loading required models. */
    data class Loading(val modelId: String, val progress: Float) : PipelineStage()

    /** Source separation in progress. */
    data class Separating(val progress: Float, val currentStem: String? = null) : PipelineStage()

    /** Per-stem transcription. */
    data class Transcribing(val stem: String, val progress: Float) : PipelineStage()

    /** Drum onset detection. */
    data class DrumProcessing(val progress: Float) : PipelineStage()

    /** Classifying each stem to GM program. */
    data class Classifying(val stem: String) : PipelineStage()

    /** Merging into final multi-track MIDI. */
    data class Merging(val progress: Float) : PipelineStage()

    /** Finished, MIDI file written to [outputPath]. */
    data class Done(val outputPath: String, val trackCount: Int) : PipelineStage()

    /** Failed at some stage. */
    data class Failed(val stage: String, val error: String) : PipelineStage()
}

/**
 * Orchestrates the full audio → MIDI pipeline.
 *
 * Responsibilities:
 * 1. Resolve [PipelineConfig] into concrete model instances
 * 2. Ensure all required models are downloaded
 * 3. Execute stages in order
 * 4. Apply hallucination suppression (max instruments, min duration)
 * 5. Write the final multi-track MIDI file
 *
 * The executor is intentionally model-agnostic: it only knows the interfaces
 * defined in PipelineInterfaces.kt. New model implementations can be dropped
 * in without changing the executor.
 */
class PipelineExecutor(
    private val modelManager: ModelManager,
    private val registry: ModelRegistry,
    private val separatorFactory: (String) -> SourceSeparator,
    private val transcriberFactory: (String) -> Transcriber,
    private val classifierFactory: (String) -> InstrumentClassifier,
    private val drumProcessorFactory: (String) -> DrumProcessor,
    private val midiWriter: MidiWriter
) {
    private val _stages = MutableSharedFlow<PipelineStage>(extraBufferCapacity = 32)
    val stages: SharedFlow<PipelineStage> = _stages.asSharedFlow()

    /**
     * Run the full pipeline on [inputAudio] using [config].
     *
     * @return Pair of (output MIDI bytes, output file path) on success.
     */
    suspend fun run(inputAudio: AudioData, config: PipelineConfig): Result<Pair<ByteArray, String>> {
        return runCatching {
            val modelMap = config.models

            // ---- Stage 1: ensure all models are downloaded ----
            modelMap.values.forEach { modelId ->
                val asset = registry.byId(modelId) ?: error("Unknown model id: $modelId")
                _stages.emit(PipelineStage.Loading(modelId, 0f))
                modelManager.ensureDownloaded(asset)
                _stages.emit(PipelineStage.Loading(modelId, 1f))
            }

            // ---- Stage 2 (optional): source separation ----
            val separatorModelId = modelMap["SOURCE_SEPARATOR"]
            val stems: List<SeparatedStem> = if (separatorModelId != null) {
                val sep = separatorFactory(separatorModelId)
                val asset = registry.byId(separatorModelId)!!
                sep.load(modelManager.localPathFor(asset).absolutePath)
                _stages.emit(PipelineStage.Separating(0f))
                try {
                    val result = sep.separate(inputAudio)
                    _stages.emit(PipelineStage.Separating(1f))
                    result
                } finally { sep.close() }
            } else {
                // No separation; treat input as a single "other" stem
                listOf(SeparatedStem("other", inputAudio, "Ensemble"))
            }

            // ---- Stage 3: per-stem transcription + classification ----
            val tracks = mutableListOf<MidiTrack>()

            // If end-to-end transcriber is configured, skip per-stem transcription
            val endToEndId = modelMap["END_TO_END_TRANSCRIBER"]
            if (endToEndId != null) {
                val transcriber = transcriberFactory(endToEndId)
                val asset = registry.byId(endToEndId)!!
                transcriber.load(modelManager.localPathFor(asset).absolutePath)
                _stages.emit(PipelineStage.Transcribing("end-to-end", 0f))
                val notes = transcriber.transcribe(inputAudio)
                _stages.emit(PipelineStage.Transcribing("end-to-end", 1f))
                transcriber.close()
                // Group by instrument tag (model emits notes with hint on instrument).
                val grouped = groupByInstrumentHint(notes)
                tracks.addAll(grouped)
            } else {
                for (stem in stems) {
                    val role = when (stem.label) {
                        "vocals" -> "VOCAL_TRANSCRIBER"
                        "bass" -> "BASS_TRANSCRIBER"
                        "piano" -> "PIANO_TRANSCRIBER"
                        "guitar" -> "GUITAR_TRANSCRIBER"
                        "other" -> "OTHER_TRANSCRIBER"
                        "drums" -> "DRUM_PROCESSOR"
                        else -> "OTHER_TRANSCRIBER"
                    }
                    val modelId = modelMap[role] ?: continue

                    if (role == "DRUM_PROCESSOR") {
                        val drumProc = drumProcessorFactory(modelId)
                        val asset = registry.byId(modelId)!!
                        drumProc.load(modelManager.localPathFor(asset).absolutePath)
                        _stages.emit(PipelineStage.DrumProcessing(0f))
                        val drumNotes = drumProc.process(stem.audio)
                        _stages.emit(PipelineStage.DrumProcessing(1f))
                        drumProc.close()
                        tracks.add(MidiTrack(
                            programNumber = 0, // drum channel
                            instrumentName = "Drums",
                            channel = 9, // GM drum channel
                            notes = drumNotes
                        ))
                    } else {
                        val transcriber = transcriberFactory(modelId)
                        val asset = registry.byId(modelId)!!
                        transcriber.load(modelManager.localPathFor(asset).absolutePath)
                        _stages.emit(PipelineStage.Transcribing(stem.label, 0f))
                        val notes = transcriber.transcribe(stem.audio)
                        _stages.emit(PipelineStage.Transcribing(stem.label, 1f))
                        transcriber.close()

                        // Classify stem to assign GM program number
                        val classifierId = modelMap["INSTRUMENT_CLASSIFIER"]
                        val classification = if (classifierId != null) {
                            val clf = classifierFactory(classifierId)
                            val clfAsset = registry.byId(classifierId)!!
                            clf.load(modelManager.localPathFor(clfAsset).absolutePath)
                            _stages.emit(PipelineStage.Classifying(stem.label))
                            val c = clf.classify(stem.audio)
                            clf.close()
                            c
                        } else {
                            Classification(stem.sourceInstrument, defaultGmProgram(stem.label), 0f)
                        }

                        tracks.add(MidiTrack(
                            programNumber = classification.gmProgramNumber,
                            instrumentName = classification.instrumentName,
                            channel = tracks.size.coerceAtMost(15).let { if (it == 9) 10 else it },
                            notes = notes
                        ))
                    }

                    if (tracks.size >= config.maxInstruments) {
                        // Enforce max-instruments to prevent hallucination of dozens of tracks
                        break
                    }
                }
            }

            // ---- Stage 4: hallucination suppression ----
            val filteredTracks = tracks.map { track ->
                val filteredNotes = track.notes.filter { it.durationMs >= config.minNoteDurationMs }
                track.copy(notes = filteredNotes)
            }.filter { it.notes.isNotEmpty() }

            // ---- Stage 5: write MIDI ----
            _stages.emit(PipelineStage.Merging(0f))
            val composition = MidiComposition(filteredTracks)
            val midiBytes = midiWriter.write(composition)
            _stages.emit(PipelineStage.Merging(1f))

            _stages.emit(PipelineStage.Done("<in-memory>", filteredTracks.size))
            midiBytes to "<in-memory>"
        }.onFailure { e ->
            _stages.emit(PipelineStage.Failed("executor", e.message ?: "Unknown"))
        }
    }

    /**
     * Heuristic: group notes from end-to-end transcription by instrument hint.
     * Used by MT3-style models that emit per-note instrument tags.
     */
    private fun groupByInstrumentHint(notes: List<MidiNote>): List<MidiTrack> {
        // Placeholder heuristic: each unique pitch range becomes one track.
        // Real implementation depends on MT3 tokenization.
        return listOf(MidiTrack(
            programNumber = 0,
            instrumentName = "Auto-detected",
            channel = 0,
            notes = notes
        ))
    }

    /**
     * Default GM program when no classifier is available.
     */
    private fun defaultGmProgram(stemLabel: String): Int = when (stemLabel) {
        "vocals" -> 53 // Voice "Aahs"
        "bass" -> 33 // Electric Bass (finger)
        "piano" -> 0 // Acoustic Grand Piano
        "guitar" -> 25 // Acoustic Guitar (steel)
        "other" -> 49 // String Ensemble 1
        else -> 0
    }
}
