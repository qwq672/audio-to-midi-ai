package com.audiomidi.ai

import android.app.Application
import com.audiomidi.ai.data.DownloadRegion
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Application entry point. Owns singletons shared across the app.
 *
 * [registry] and [presets] are nullable (rather than lateinit) because they're
 * loaded asynchronously from assets. Callers can either:
 * - null-check before use, or
 * - observe [isReady] (a StateFlow that emits true once both are loaded).
 */
class AudioToMidiApp : Application() {

    var settings: UserSettings = UserSettings()
        private set

    var registry: ModelRegistry? = null
        private set

    var presets: GenrePresetRegistry? = null
        private set

    lateinit var modelManager: ModelManager
        private set

    lateinit var pipelineExecutor: PipelineExecutor
        private set

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Default settings — real app should load from DataStore.
        settings = UserSettings()

        // Construct managers (ModelManager needs settings for region/priority).
        modelManager = ModelManager(this, settings)

        // Wire up pipeline executor with factory closures, using a placeholder
        // registry that will be replaced after async load completes.
        pipelineExecutor = PipelineExecutor(
            modelManager = modelManager,
            registry = ModelRegistry.placeholder(),
            separatorFactory = { modelId ->
                when (modelId) {
                    "demucs_6s", "demucs_4s", "demucs_4s_ft" -> DemucsSeparator(this, modelId)
                    "spleeter_4s" -> DemucsSeparator(this, modelId)
                    else -> throw IllegalArgumentException("Unknown separator: $modelId")
                }
            },
            transcriberFactory = { modelId ->
                when (modelId) {
                    "basic_pitch" -> BasicPitchTranscriber(this, modelId)
                    "bytedance_piano" -> KongPianoTranscriber(this, modelId)
                    "mt3" -> BasicPitchTranscriber(this, modelId)
                    else -> throw IllegalArgumentException("Unknown transcriber: $modelId")
                }
            },
            classifierFactory = { modelId ->
                when (modelId) {
                    "yamnet" -> YamNetClassifier(this, modelId)
                    "openl3_classifier" -> YamNetClassifier(this, modelId)
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

        // Load registries from assets, asynchronously.
        appScope.launch {
            val loadedRegistry = ModelRegistry.load(this@AudioToMidiApp)
            val loadedPresets = GenrePresetRegistry.load(this@AudioToMidiApp)
            registry = loadedRegistry
            presets = loadedPresets
            // Critical: update the executor's registry too, otherwise run()
            // still uses the empty placeholder and throws "Unknown model id".
            pipelineExecutor.registry = loadedRegistry
            _isReady.value = true
            Timber.i("Loaded ${loadedRegistry.all().size} models and ${loadedPresets.presets.size} presets")

            // Auto-detect region on first launch.
            if (settings.downloadRegion == DownloadRegion.AUTO) {
                val detector = RegionDetector(Downloader())
                val detected = detector.detect()
                Timber.i("Auto-detected region: $detected")
                // TODO: persist via DataStore
            }
        }

        Timber.i("AudioToMidiApp initialized")
    }
}
