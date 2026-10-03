package com.audiomidi.ai.ui

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
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
import com.audiomidi.ai.util.MidiOutput
import com.audiomidi.ai.util.MidiStorageWriter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Persistent UI state for the wizard.
 */
data class WizardUiState(
    val currentStep: WizardStep = WizardStep.HOME,
    val isReady: Boolean = false,
    val isLoading: Boolean = true,

    val settings: UserSettings = UserSettings(),

    val availablePresets: List<GenrePreset> = emptyList(),
    val selectedGenreId: String = "pop",

    val availableModels: List<ModelAsset> = emptyList(),
    val currentConfig: PipelineConfig? = null,
    val modelStatuses: Map<String, ModelStatus> = emptyMap(),

    val selectedAudioUris: List<Uri> = emptyList(),
    val conversionCount: Int = 1,
    val migrateMetadata: Boolean = true,

    val isProcessing: Boolean = false,
    val processingProgress: Float = 0f,
    val currentPipelineStage: PipelineStage? = null,
    val processingJob: Job? = null,

    // Real URIs from MediaStore, shareable with other apps
    val outputMidiFiles: List<MidiOutput> = emptyList(),
    val errorMessage: String? = null
)

class MainViewModel : ViewModel() {

    private var appRef: AudioToMidiApp? = null

    private val _uiState = MutableStateFlow(WizardUiState())
    val uiState: StateFlow<WizardUiState> = _uiState.asStateFlow()

    fun bind(app: AudioToMidiApp) {
        this.appRef = app
        viewModelScope.launch {
            app.isReady.collect { ready ->
                _uiState.update { it.copy(isReady = ready, isLoading = !ready) }
                if (ready) refreshAfterLoad()
            }
        }
        viewModelScope.launch {
            app.modelManager.downloadEvents.collect { (modelId, state) ->
                updateModelStatus(modelId, state)
            }
        }
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

    fun downloadModel(modelId: String) {
        val app = appRef ?: return
        val asset = app.registry?.byId(modelId) ?: return
        if (app.modelManager.isAvailable(asset)) return
        if (asset.bundled) {
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

    fun retryDownload(modelId: String) = downloadModel(modelId)

    fun cancelDownload(modelId: String) {
        // TODO: implement via Downloader.cancelDownload(modelId)
    }

    fun deleteModel(modelId: String) {
        val app = appRef ?: return
        val asset = app.registry?.byId(modelId) ?: return
        if (app.modelManager.delete(asset)) {
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
                outputMidiFiles = emptyList(),
                processingProgress = 0f
            )
        }
        val job = viewModelScope.launch {
            val outputs = mutableListOf<MidiOutput>()
            for ((idx, uri) in uris.withIndex()) {
                // TODO: decode audio from uri to AudioData
                val audio = AudioData(sampleRate = 44100, channels = 2, samples = FloatArray(0))
                val result = app.pipelineExecutor.run(audio, config)
                if (result.isFailure) {
                    val err = result.exceptionOrNull()?.message ?: "Unknown error"
                    _uiState.update { it.copy(errorMessage = err) }
                    break
                }
                val (midiBytes, _) = result.getOrThrow()

                // Actually write MIDI bytes to user-accessible storage
                // (Download/AudioToMidi/<source>_<idx>.mid)
                val sourceName = uri.lastPathSegment?.substringBeforeLast('.') ?: "audio"
                val outName = "${sourceName}_${idx + 1}.mid"
                val output = MidiStorageWriter.write(app, outName, midiBytes)
                if (output == null) {
                    _uiState.update {
                        it.copy(errorMessage = "Failed to write MIDI file: $outName")
                    }
                    break
                }
                outputs.add(output)
            }
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingProgress = 1f,
                    outputMidiFiles = outputs,
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

    // ---------- MIDI file actions (listen / share) ----------

    /**
     * Open MIDI file with an external app via ACTION_VIEW intent.
     * Falls back from audio/midi to audio/x-midi MIME type if no app handles it.
     */
    fun listenToMidi(output: MidiOutput) {
        val app = appRef ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(output.uri, "audio/midi")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "用…打开 ${output.displayName}")
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        try {
            app.startActivity(chooser)
        } catch (e: Exception) {
            // Try fallback MIME type
            val fallback = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(output.uri, "audio/x-midi")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                app.startActivity(Intent.createChooser(fallback, "用…打开").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e2: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "没有应用能打开 MIDI 文件: ${e2.message}")
                }
            }
        }
    }

    /**
     * Share MIDI file via system share sheet (ACTION_SEND with EXTRA_STREAM).
     */
    fun shareMidi(output: MidiOutput) {
        val app = appRef ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/midi"
            putExtra(Intent.EXTRA_STREAM, output.uri)
            putExtra(Intent.EXTRA_SUBJECT, output.displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "分享 ${output.displayName}")
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        try {
            app.startActivity(chooser)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(errorMessage = "没有应用可以分享: ${e.message}")
            }
        }
    }

    /**
     * Open the system file manager at the Downloads location.
     */
    fun openInFileManager() {
        val app = appRef ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            app.startActivity(Intent.createChooser(intent, "在文件管理器中查看").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            _uiState.update {
                it.copy(errorMessage = "无法打开文件管理器: ${e.message}")
            }
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
