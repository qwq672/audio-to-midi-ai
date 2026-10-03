package com.audiomidi.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audiomidi.ai.ui.*
import com.audiomidi.ai.ui.theme.AudioToMidiTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as AudioToMidiApp
        viewModel.bind(app)

        setContent {
            AudioToMidiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.setSelectedAudioUris(uris)
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(topBarTitle(state.currentStep)) },
                    navigationIcon = {
                        if (state.currentStep != WizardStep.HOME &&
                            state.currentStep != WizardStep.PROCESSING &&
                            state.currentStep != WizardStep.COMPLETE) {
                            IconButton(onClick = viewModel::prevStep) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一步")
                            }
                        }
                    }
                )
                StepIndicator(state.currentStep)
            }
        },
        bottomBar = {
            WizardBottomBar(
                state = state,
                onPrev = viewModel::prevStep,
                onNext = viewModel::nextStep,
                onCancel = viewModel::cancelProcessing,
                onStart = {
                    viewModel.goToStep(WizardStep.PROCESSING)
                    viewModel.startProcessing()
                },
                onRestart = {
                    viewModel.goToStep(WizardStep.HOME)
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Smooth fade between steps
            AnimatedContent(
                targetState = state.currentStep,
                transitionSpec = {
                    fadeIn(initialAlpha = 0.3f) togetherWith fadeOut(targetAlpha = 0.3f)
                },
                label = "page-transition"
            ) { step ->
                when (step) {
                    WizardStep.HOME -> HomePage(
                        state = state,
                        onRegionChange = viewModel::setDownloadRegion,
                        onMaxParallelChange = viewModel::setMaxParallelDownloads,
                        onNpuToggle = viewModel::setNpuPreferred,
                        onAutoDownloadToggle = viewModel::setAutoDownloadOnGenreSelect
                    )
                    WizardStep.GENRE_SELECT -> GenreSelectPage(
                        state = state,
                        onSelect = { genreId -> viewModel.selectGenre(genreId) }
                    )
                    WizardStep.MODEL_SELECT -> ModelSelectPage(
                        state = state,
                        onOverride = viewModel::overrideModel,
                        onShowAlternatives = { }
                    )
                    WizardStep.AUDIO_SELECT -> AudioSelectPage(
                        state = state,
                        onPickFiles = {
                            audioPickerLauncher.launch(arrayOf("audio/*"))
                        },
                        onRemoveUri = viewModel::removeAudioUri,
                        onConversionCountChange = viewModel::setConversionCount,
                        onMigrateMetadataToggle = viewModel::setMigrateMetadata
                    )
                    WizardStep.CONFIRM -> ConfirmPage(state)
                    WizardStep.PROCESSING -> ProcessingPage(state)
                    WizardStep.COMPLETE -> CompletePage(
                        state = state,
                        onListen = { /* TODO: open MIDI player */ }
                    )
                }
            }
        }
    }
}

private fun topBarTitle(step: WizardStep): String = when (step) {
    WizardStep.HOME -> "Audio To MIDI AI"
    WizardStep.GENRE_SELECT -> "选择流派"
    WizardStep.MODEL_SELECT -> "选择模型"
    WizardStep.AUDIO_SELECT -> "选择音频"
    WizardStep.CONFIRM -> "确认"
    WizardStep.PROCESSING -> "处理中"
    WizardStep.COMPLETE -> "完成"
}
