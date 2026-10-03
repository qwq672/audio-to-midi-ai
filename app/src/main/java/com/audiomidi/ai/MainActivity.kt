package com.audiomidi.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.audiomidi.ai.data.GenrePreset
import com.audiomidi.ai.data.ModelAsset
import com.audiomidi.ai.data.ModelRole
import com.audiomidi.ai.model.DownloadState
import com.audiomidi.ai.pipeline.PipelineStage
import com.audiomidi.ai.ui.MainViewModel
import com.audiomidi.ai.ui.theme.AudioToMidiTheme
import kotlinx.coroutines.flow.collectLatest

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio → Multi-Track MIDI") },
                actions = {
                    IconButton(onClick = { /* Open settings */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Genre selector
            Section(title = "1. 选择流派") {
                GenrePicker(
                    presets = uiState.availablePresets,
                    selectedId = uiState.selectedGenreId,
                    onSelect = viewModel::selectGenre
                )
            }

            // 2. Recommended models (with override option)
            Section(title = "2. 推荐模型（可手动覆盖）") {
                RecommendedModelsList(
                    state = uiState,
                    onOverride = viewModel::overrideModel
                )
            }

            // 3. Input file
            Section(title = "3. 选择音频文件") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { /* TODO: launch SAF picker */ }) {
                        Icon(Icons.Default.AudioFile, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("选择音频")
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = uiState.inputAudioPath ?: "未选择",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // 4. Pipeline status
            uiState.currentPipelineStage?.let { stage ->
                Section(title = "4. 处理进度") {
                    PipelineStatusView(stage)
                }
            }

            // 5. Run button
            Button(
                onClick = viewModel::startProcessing,
                enabled = uiState.currentConfig != null && uiState.inputAudioPath != null && !uiState.isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (uiState.isProcessing) "处理中…" else "开始转换")
            }

            uiState.errorMessage?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(err, modifier = Modifier.padding(12.dp))
                }
            }

            uiState.outputMidiPath?.let { path ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("MIDI 已生成", fontWeight = FontWeight.SemiBold)
                        Text(path, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun GenrePicker(
    presets: List<GenrePreset>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    if (presets.isEmpty()) {
        Text("正在加载预设…", style = MaterialTheme.typography.bodySmall)
        return
    }
    LazyColumn(
        modifier = Modifier.height(280.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(presets) { preset ->
            val selected = preset.id == selectedId
            Card(
                onClick = { onSelect(preset.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(preset.emoji, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(preset.displayName, fontWeight = FontWeight.Medium)
                        Text(
                            preset.description,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendedModelsList(
    state: com.audiomidi.ai.ui.MainUiState,
    onOverride: (ModelRole, String?) -> Unit
) {
    val config = state.currentConfig ?: return
    LazyColumn(
        modifier = Modifier.height(280.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(config.models.entries.toList()) { (roleStr, modelId) ->
            val role = runCatching { ModelRole.valueOf(roleStr) }.getOrNull() ?: return@items
            val asset = state.availableModels.firstOrNull { it.id == modelId }
            val status = state.modelStatuses[modelId]
            val isAvailable = status?.isAvailable == true || asset?.bundled == true
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isAvailable) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            asset?.displayName ?: modelId,
                            fontWeight = FontWeight.Medium
                        )
                        Text(role.name, style = MaterialTheme.typography.bodySmall)
                        status?.let { DownloadStatusText(it) }
                    }
                    OutlinedButton(
                        onClick = { /* TODO: open model override picker */ }
                    ) {
                        Text("更换")
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadStatusText(status: com.audiomidi.ai.model.ModelStatus) {
    when (val st = status.state) {
        is DownloadState.Idle -> Text("待下载", style = MaterialTheme.typography.bodySmall)
        is DownloadState.Downloading -> {
            val pct = if (st.totalBytes > 0) (st.bytesDownloaded * 100 / st.totalBytes) else 0
            Text("下载中 $pct%  ${st.bytesPerSecond / 1024} KB/s",
                style = MaterialTheme.typography.bodySmall)
        }
        is DownloadState.Verifying -> Text("校验中…", style = MaterialTheme.typography.bodySmall)
        is DownloadState.Completed -> Text("已就绪", style = MaterialTheme.typography.bodySmall)
        is DownloadState.Failed -> Text("失败：${st.errors.size} 个源", style = MaterialTheme.typography.bodySmall)
        is DownloadState.Cancelled -> Text("已取消", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PipelineStatusView(stage: PipelineStage) {
    val text = when (stage) {
        is PipelineStage.Loading -> "加载模型 ${stage.modelId} ${(stage.progress * 100).toInt()}%"
        is PipelineStage.Separating -> "分离音轨 ${(stage.progress * 100).toInt()}% ${stage.currentStem ?: ""}"
        is PipelineStage.Transcribing -> "转录 ${stage.stem} ${(stage.progress * 100).toInt()}%"
        is PipelineStage.DrumProcessing -> "鼓轨处理 ${(stage.progress * 100).toInt()}%"
        is PipelineStage.Classifying -> "识别乐器 ${stage.stem}"
        is PipelineStage.Merging -> "合并 MIDI ${(stage.progress * 100).toInt()}%"
        is PipelineStage.Done -> "完成，${stage.trackCount} 个音轨"
        is PipelineStage.Failed -> "失败（${stage.stage}）：${stage.error}"
    }
    Card {
        Text(text, modifier = Modifier.padding(12.dp))
    }
}
