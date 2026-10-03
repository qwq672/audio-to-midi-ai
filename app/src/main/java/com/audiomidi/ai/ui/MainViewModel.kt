package com.audiomidi.ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiomidi.ai.AudioToMidiApp
import com.audiomidi.ai.data.GenrePreset
import com.audiomidi.ai.data.ModelAsset
import com.audiomidi.ai.data.ModelRole
import com.audiomidi.ai.data.PipelineConfig
import com.audiomidi.ai.model.DownloadState
import com.audiomidi.ai.model.ModelStatus
import com.audiomidi.ai.pipeline.AudioData
import com.audiomidi.ai.pipeline.PipelineStage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * UI state for the main screen.
 */
data class MainUiState(
    val selectedGenreId: String = "pop",
    val currentConfig: PipelineConfig? = null,
    val availablePresets: List<GenrePreset> = emptyList(),
    val availableModels: List<ModelAsset> = emptyList(),
    val modelStatuses: Map<String, ModelStatus> = emptyMap(),
    val currentPipelineStage: PipelineStage? = null,
    val inputAudioPath: String? = null,
    val outputMidiPath: String? = null,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Main screen ViewModel. Bridges UI ↔ app singletons (modelManager,
 * pipelineExecutor). Manages user-selected genre + override models.
 */
class MainViewModel : ViewModel() {

    private lateinit var app: AudioToMidiApp

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun bind(app: AudioToMidiApp) {
        this.app = app
        refreshState()
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

    private fun refreshState() {
        val appReady = ::app.isInitialized
        val presetsReady = appReady && app::presets.isInitialized
        val registryReady = appReady && app::registry.isInitialized
        _uiState.update {
            it.copy(
                availablePresets = if (presetsReady) app.presets.presets else emptyList(),
                availableModels = if (registryReady) app.registry.all() else emptyList(),
                currentConfig = it.currentConfig ?: defaultConfigFor(it.selectedGenreId)
            )
        }
    }

    fun selectGenre(genreId: String) {
        val preset = app.presets.byId(genreId) ?: return
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
            it.copy(
                selectedGenreId = genreId,
                currentConfig = config
            )
        }
        // Trigger auto-download of recommended models if needed.
        if (app.settings.autoDownloadOnGenreSelect) {
            viewModelScope.launch {
                preset.models.values.forEach { modelId ->
                    val asset = app.registry.byId(modelId)
                    if (asset != null && !app.modelManager.isAvailable(asset)) {
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

    fun setInputAudio(path: String) {
        _uiState.update { it.copy(inputAudioPath = path) }
    }

    /**
     * Trigger the full pipeline. In production this would delegate to a
     * foreground service; here it just calls the executor directly.
     */
    fun startProcessing() {
        val config = _uiState.value.currentConfig ?: return
        val inputPath = _uiState.value.inputAudioPath ?: return

        _uiState.update {
            it.copy(isProcessing = true, errorMessage = null, outputMidiPath = null)
        }
        viewModelScope.launch {
            // TODO: load audio from inputPath into AudioData
            // For now, placeholder empty audio to show the pipeline wiring.
            val audio = AudioData(sampleRate = 44100, channels = 2, samples = FloatArray(0))
            val result = app.pipelineExecutor.run(audio, config)
            result.onSuccess { (midiBytes, _) ->
                // TODO: write midiBytes to a file in Downloads/AudioToMidi/
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        outputMidiPath = "Downloads/AudioToMidi/output.mid"
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isProcessing = false, errorMessage = e.message)
                }
            }
        }
    }

    private fun updateModelStatus(modelId: String, state: DownloadState) {
        val asset = app.registry.byId(modelId) ?: return
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
        if (!::app.isInitialized || !app::presets.isInitialized) return null
        val preset = app.presets.byId(genreId) ?: return null
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

// Helper extension to make MutableStateFlow updates terser
private inline fun <T> MutableStateFlow<T>.update(block: (T) -> T) {
    value = block(value)
}
