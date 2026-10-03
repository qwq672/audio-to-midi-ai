package com.audiomidi.ai.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiomidi.ai.AudioToMidiApp
import com.audiomidi.ai.data.DownloadRegion
import com.audiomidi.ai.data.GenrePreset
import com.audiomidi.ai.data.ModelAsset
import com.audiomidi.ai.data.ModelRole
import com.audiomidi.ai.data.PipelineConfig
import com.audiomidi.ai.data.UserSettings
import com.audiomidi.ai.model.DownloadState
import com.audiomidi.ai.model.ModelStatus
import com.audiomidi.ai.pipeline.AudioData
import com.audiomidi.ai.pipeline.PipelineStage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Persistent UI state for the wizard.
 */
data class WizardUiState(
    val currentStep: WizardStep = WizardStep.HOME,
    val isReady: Boolean = false,
    val isLoading: Boolean = true,

    // Home / Settings
    val settings: UserSettings = UserSettings(),

    // Genre selection
    val availablePresets: List<GenrePreset> = emptyList(),
    val selectedGenreId: String = "pop",

    // Model selection
    val availableModels: List<ModelAsset> = emptyList(),
    val currentConfig: PipelineConfig? = null,
    val modelStatuses: Map<String, ModelStatus> = emptyMap(),

    // Audio selection
    val selectedAudioUris: List<Uri> = emptyList(),
    val conversionCount: Int = 1,
    val migrateMetadata: Boolean = true,

    // Processing
    val isProcessing: Boolean = false,
    val processingProgress: Float = 0f,
    val currentPipelineStage: PipelineStage? = null,
    val processingJob: Job? = null,

    // Complete
    val outputMidiPaths: List<String> = emptyList(),
    val errorMessage: String? = null
)

class MainViewModel : ViewModel() {

    private var appRef: AudioToMidiApp? = null

    private val _uiState = MutableStateFlow(WizardUiState())
    val uiState: StateFlow<WizardUiState> = _uiState.asStateFlow()

    fun bind(app: AudioToMidiApp) {
        this.appRef = app
        // Observe readiness — refresh UI when presets/registry become available.
        viewModelScope.launch {
            app.isReady.collect { ready ->
                _uiState.update { it.copy(isReady = ready, isLoading = !ready) }
                if (ready) refreshAfterLoad()
            }
        }
        // Subscribe to download events
        viewModelScope.launch {
            app.modelManager.downloadEvents.collect { (modelId, state) ->
                updateModelStatus(modelId, state)
            }
        }
        // Subscribe to pipeline stages
        viewModelScope.launch {
            app.pipelineExecutor.stages.collect { stage ->
                _uiState.update { it.copy(currentPipelineStage = stage) }
            }
        }
    }

    private fun refreshAfterLoad() {
        val app = appRef ?: return
        val r = app.registry
        val p = app.presets
        _uiState.update {
            it.copy(
                availablePresets = p?.presets ?: emptyList(),
                availableModels = r?.all() ?: emptyList(),
                currentConfig = it.currentConfig ?: defaultConfigFor(it.selectedGenreId)
            )
        }
    }

    // ---------- Step navigation ----------

    fun goToStep(step: WizardStep) {
        _uiState.update { it.copy(currentStep = step, errorMessage = null) }
    }

    fun nextStep() {
        val cur = _uiState.value.currentStep
        cur.next?.let { goToStep(it) }
    }

    fun prevStep() {
        val cur = _uiState.value.currentStep
        cur.prev?.let { goToStep(it) }
    }

    // ---------- Settings ----------

    fun updateSettings(settings: UserSettings) {
        _uiState.update { it.copy(settings = settings) }
        // In production, persist via DataStore
    }

    fun setDownloadRegion(region: DownloadRegion) {
        _uiState.update { it.copy(settings = it.settings.copy(downloadRegion = region)) }
    }

    fun setMaxParallelDownloads(n: Int) {
        _uiState.update { it.copy(settings = it.settings.copy(maxParallelDownloads = n.coerceIn(1, 6))) }
    }

    fun setNpuPreferred(enabled: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(preferNpuAcceleration = enabled)) }
    }

    fun setAutoDownloadOnGenreSelect(enabled: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(autoDownloadOnGenreSelect = enabled)) }
    }

    // ---------- Genre selection ----------

    fun selectGenre(genreId: String) {
        val app = appRef ?: return
        val preset = app.presets?.byId(genreId) ?: return
        val config = PipelineConfig(
            name = preset.displayName,
            baseGenreId = genreId,
            models = preset.models,
            maxInstruments = preset.maxInstruments,
            confidenceThreshold = preset.confidenceThreshold,
            minNoteDurationMs = preset.minNoteDurationMs,
            isCustom = false
        )
        _uiState.update {
            it.copy(selectedGenreId = genreId, currentConfig = config)
        }
        if (app.settings.autoDownloadOnGenreSelect) {
            viewModelScope.launch {
                preset.models.values.forEach { modelId ->
                    val asset = app.registry?.byId(modelId) ?: return@forEach
                    if (!app.modelManager.isAvailable(asset)) {
                        app.modelManager.ensureDownloaded(asset)
                    }
                }
            }
        }
    }

    fun overrideModel(role: ModelRole, modelId: String?) {
        val current = _uiState.value.currentConfig ?: return
        val updated = current.withOverride(role, modelId)
        _uiState.update { it.copy(currentConfig = updated) }
    }

    // ---------- Manual model management ----------

    /**
     * Manually trigger download of a specific model. Used by the Download
     * button shown next to each model in ModelSelectPage.
     */
    fun downloadModel(modelId: String) {
        val app = appRef ?: return
        val asset = app.registry?.byId(modelId) ?: return
        if (app.modelManager.isAvailable(asset)) return  // already there
        if (asset.bundled) {
            // Bundled assets shouldn't need downloading — surface as error
            _uiState.update {
                it.copy(errorMessage = "${asset.displayName} marked as bundled but file not found. " +
                    "Check app/src/main/assets/ contains ${asset.id}.onnx")
            }
            return
        }
        viewModelScope.launch {
            app.modelManager.ensureDownloaded(asset)
        }
    }

    /**
     * Retry a failed download — same as downloadModel since ensureDownloaded
     * clears the failure state internally.
     */
    fun retryDownload(modelId: String) = downloadModel(modelId)

    /**
     * Cancel any in-progress download for a model. Currently a stub —
     * requires OkHttp Call.cancel() plumbing.
     */
    fun cancelDownload(modelId: String) {
        // TODO: implement via Downloader.cancelDownload(modelId)
    }

    /**
     * Delete a model file from local storage to free space.
     */
    fun deleteModel(modelId: String) {
        val app = appRef ?: return
        val asset = app.registry?.byId(modelId) ?: return
        if (app.modelManager.delete(asset)) {
            // Remove from statuses too
            _uiState.update { current ->
                current.copy(modelStatuses = current.modelStatuses - modelId)
            }
        }
    }

    // ---------- Audio selection ----------

    fun setSelectedAudioUris(uris: List<Uri>) {
        _uiState.update { it.copy(selectedAudioUris = uris) }
    }

    fun addAudioUri(uri: Uri) {
        _uiState.update {
            if (uri in it.selectedAudioUris) it
            else it.copy(selectedAudioUris = it.selectedAudioUris + uri)
        }
    }

    fun removeAudioUri(uri: Uri) {
        _uiState.update {
            it.copy(selectedAudioUris = it.selectedAudioUris - uri)
        }
    }

    fun setConversionCount(n: Int) {
        _uiState.update { it.copy(conversionCount = n.coerceAtLeast(1)) }
    }

    fun setMigrateMetadata(enabled: Boolean) {
        _uiState.update { it.copy(migrateMetadata = enabled) }
    }

    // ---------- Processing ----------

    fun startProcessing() {
        val app = appRef ?: return
        val config = _uiState.value.currentConfig ?: return
        val uris = _uiState.value.selectedAudioUris
        if (uris.isEmpty()) return

        _uiState.update {
            it.copy(
                isProcessing = true,
                errorMessage = null,
                outputMidiPaths = emptyList(),
                processingProgress = 0f
            )
        }
        val job = viewModelScope.launch {
            val outputs = mutableListOf<String>()
            for (uri in uris) {
                // TODO: decode audio from uri to AudioData
                // For now: placeholder empty audio to show the pipeline wiring
                val audio = AudioData(sampleRate = 44100, channels = 2, samples = FloatArray(0))
                val result = app.pipelineExecutor.run(audio, config)
                if (result.isFailure) {
                    val err = result.exceptionOrNull()?.message ?: "Unknown error"
                    _uiState.update { it.copy(errorMessage = err) }
                    break
                }
                val (_, _) = result.getOrThrow()
                // TODO: write midiBytes to Downloads/AudioToMidi/<uri_last_segment>.mid
                //       if migrateMetadata is true, also embed ID3 tag info as text events
                val outName = "output_${System.currentTimeMillis()}.mid"
                outputs.add("Downloads/AudioToMidi/$outName")
            }
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingProgress = 1f,
                    outputMidiPaths = outputs,
                    currentStep = WizardStep.COMPLETE
                )
            }
        }
        _uiState.update { it.copy(processingJob = job) }
    }

    fun cancelProcessing() {
        _uiState.value.processingJob?.cancel()
        _uiState.update {
            it.copy(
                isProcessing = false,
                processingJob = null,
                currentStep = WizardStep.AUDIO_SELECT,
                errorMessage = "用户取消"
            )
        }
    }

    // ---------- Helpers ----------

    private fun updateModelStatus(modelId: String, state: DownloadState) {
        val app = appRef ?: return
        val asset = app.registry?.byId(modelId) ?: return
        val status = ModelStatus(
            asset = asset,
            isAvailable = state is DownloadState.Completed,
            localPath = (state as? DownloadState.Completed)?.filePath,
            state = state
        )
        _uiState.update { current ->
            current.copy(modelStatuses = current.modelStatuses + (modelId to status))
        }
    }

    private fun defaultConfigFor(genreId: String): PipelineConfig? {
        val app = appRef ?: return null
        val preset = app.presets?.byId(genreId) ?: return null
        return PipelineConfig(
            name = preset.displayName,
            baseGenreId = genreId,
            models = preset.models,
            maxInstruments = preset.maxInstruments,
            confidenceThreshold = preset.confidenceThreshold,
            minNoteDurationMs = preset.minNoteDurationMs
        )
    }
}

private inline fun <T> MutableStateFlow<T>.update(block: (T) -> T) {
    value = block(value)
}
