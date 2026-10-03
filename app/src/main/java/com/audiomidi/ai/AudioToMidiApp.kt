package com.audiomidi.ai

import android.app.Application
import com.audiomidi.ai.data.UserSettings
import com.audiomidi.ai.model.Downloader
import com.audiomidi.ai.model.GenrePresetRegistry
import com.audiomidi.ai.model.ModelManager
import com.audiomidi.ai.model.ModelRegistry
import com.audiomidi.ai.model.RegionDetector
import com.audiomidi.ai.pipeline.PipelineExecutor
import com.audiomidi.ai.pipeline.impl.BasicPitchTranscriber
import com.audiomidi.ai.pipeline.impl.DemucsSeparator
import com.audiomidi.ai.pipeline.impl.KongPianoTranscriber
import com.audiomidi.ai.pipeline.impl.KotlinMidiWriter
import com.audiomidi.ai.pipeline.impl.LibrosaOnsetProcessor
import com.audiomidi.ai.pipeline.impl.YamNetClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Application entry point. Owns singletons shared across the app:
 * - UserSettings (loaded from DataStore, defaults for first launch)
 * - ModelManager (multi-source downloader + verifier)
 * - ModelRegistry & GenrePresetRegistry (parsed from assets/ JSON)
 * - PipelineExecutor (assembled from factory closures)
 *
 * The factory pattern decouples the executor from specific model implementations:
 * adding a new model class only requires a new entry in the factory closures below.
 */
class AudioToMidiApp : Application() {

    lateinit var settings: UserSettings
        private set

    lateinit var registry: ModelRegistry
        private set

    lateinit var presets: GenrePresetRegistry
        private set

    lateinit var modelManager: ModelManager
        private set

    lateinit var pipelineExecutor: PipelineExecutor
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Default settings — real app should load from DataStore.
        settings = UserSettings()

        // Construct managers (ModelManager needs settings for region/priority).
        // RegionDetector will run on first launch if settings.downloadRegion == AUTO.
        modelManager = ModelManager(this, settings)

        // Load registries from assets.
        appScope.launch {
            registry = ModelRegistry.load(this@AudioToMidiApp)
            presets = GenrePresetRegistry.load(this@AudioToMidiApp)
            Timber.i("Loaded ${registry.all().size} models and ${presets.presets.size} presets")

            // Auto-detect region on first launch.
            if (settings.downloadRegion == com.audiomidi.ai.data.DownloadRegion.AUTO) {
                val detector = RegionDetector(Downloader())
                val detected = detector.detect()
                Timber.i("Auto-detected region: $detected")
                // Note: in production, persist this in DataStore and update settings.
            }
        }

        // Wire up pipeline executor with factory closures.
        pipelineExecutor = PipelineExecutor(
            modelManager = modelManager,
            registry = ModelRegistry(emptyList(), "0").also {
                // Registry loaded asynchronously above; pipelineExecutor uses a placeholder
                // until reload. Real app should reassign after async load completes.
            },
            separatorFactory = { modelId ->
                when (modelId) {
                    "demucs_6s", "demucs_4s", "demucs_4s_ft" -> DemucsSeparator(this, modelId)
                    "spleeter_4s" -> DemucsSeparator(this, modelId) // Spleeter stub uses Demucs interface
                    else -> throw IllegalArgumentException("Unknown separator: $modelId")
                }
            },
            transcriberFactory = { modelId ->
                when (modelId) {
                    "basic_pitch" -> BasicPitchTranscriber(this, modelId)
                    "bytedance_piano" -> KongPianoTranscriber(this, modelId)
                    "mt3" -> BasicPitchTranscriber(this, modelId) // MT3 stub uses BasicPitch interface
                    else -> throw IllegalArgumentException("Unknown transcriber: $modelId")
                }
            },
            classifierFactory = { modelId ->
                when (modelId) {
                    "yamnet" -> YamNetClassifier(this, modelId)
                    "openl3_classifier" -> YamNetClassifier(this, modelId) // OpenL3 stub uses YamNet interface
                    else -> throw IllegalArgumentException("Unknown classifier: $modelId")
                }
            },
            drumProcessorFactory = { modelId ->
                when (modelId) {
                    "librosa_onset" -> LibrosaOnsetProcessor(modelId)
                    else -> throw IllegalArgumentException("Unknown drum processor: $modelId")
                }
            },
            midiWriter = KotlinMidiWriter()
        )

        Timber.i("AudioToMidiApp initialized")
    }
}
