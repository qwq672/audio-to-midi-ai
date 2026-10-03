package com.audiomidi.ai.ui

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
import com.audiomidi.ai.data.DownloadRegion
import com.audiomidi.ai.data.GenrePreset
import com.audiomidi.ai.data.ModelAsset
import com.audiomidi.ai.data.ModelRole
import com.audiomidi.ai.model.DownloadState
import com.audiomidi.ai.model.ModelStatus
import com.audiomidi.ai.pipeline.PipelineStage
import android.net.Uri

/**
 * Top step indicator showing where the user is in the wizard.
 */
@Composable
fun StepIndicator(currentStep: WizardStep) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        WizardStep.values().forEach { step ->
            val isActive = step == currentStep
            val isDone = step.ordinal < currentStep.ordinal
            val color = when {
                isActive -> MaterialTheme.colorScheme.primary
                isDone -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = color,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isDone) {
                                Icon(Icons.Default.Check, contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text(
                                    "${step.position}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isActive)
                                        MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Text(
                    stepLabel(step),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (step != WizardStep.values().last()) {
                HorizontalDivider(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    color = if (isDone) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

private fun stepLabel(step: WizardStep): String = when (step) {
    WizardStep.HOME -> "首页"
    WizardStep.GENRE_SELECT -> "流派"
    WizardStep.MODEL_SELECT -> "模型"
    WizardStep.AUDIO_SELECT -> "音频"
    WizardStep.CONFIRM -> "确认"
    WizardStep.PROCESSING -> "处理"
    WizardStep.COMPLETE -> "完成"
}

/**
 * Bottom navigation bar with Back / Next buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardBottomBar(
    state: WizardUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCancel: () -> Unit,
    onStart: () -> Unit,
    onRestart: () -> Unit
) {
    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (state.currentStep) {
                WizardStep.HOME -> {
                    Button(onClick = onNext, modifier = Modifier.weight(1f)) {
                        Text("开始向导 →")
                    }
                }
                WizardStep.GENRE_SELECT,
                WizardStep.MODEL_SELECT,
                WizardStep.AUDIO_SELECT -> {
                    OutlinedButton(onClick = onPrev, modifier = Modifier.weight(1f)) {
                        Text("← 上一步")
                    }
                    Button(onClick = onNext, modifier = Modifier.weight(1f),
                        enabled = canAdvanceFrom(state.currentStep, state)) {
                        Text("下一步 →")
                    }
                }
                WizardStep.CONFIRM -> {
                    OutlinedButton(onClick = onPrev, modifier = Modifier.weight(1f)) {
                        Text("← 上一步")
                    }
                    Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("开始转换")
                    }
                }
                WizardStep.PROCESSING -> {
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("取消")
                    }
                }
                WizardStep.COMPLETE -> {
                    OutlinedButton(onClick = onRestart, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("再来一次")
                    }
                    Button(onClick = { /* TODO: export / share */ }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("导出 / 分享")
                    }
                }
            }
        }
    }
}

private fun canAdvanceFrom(step: WizardStep, state: WizardUiState): Boolean = when (step) {
    WizardStep.GENRE_SELECT -> state.selectedGenreId.isNotBlank()
    WizardStep.MODEL_SELECT -> state.currentConfig != null
    WizardStep.AUDIO_SELECT -> state.selectedAudioUris.isNotEmpty()
    else -> true
}

// ============================================================
// PAGE 1: HOME (Settings)
// ============================================================

@Composable
fun HomePage(
    state: WizardUiState,
    onRegionChange: (DownloadRegion) -> Unit,
    onMaxParallelChange: (Int) -> Unit,
    onNpuToggle: (Boolean) -> Unit,
    onAutoDownloadToggle: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("总设置", style = MaterialTheme.typography.headlineSmall)

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("下载地区", style = MaterialTheme.typography.titleSmall)
                Text("决定从哪个镜像优先下载模型。国内用户推荐 hf-mirror。",
                     style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DownloadRegion.values().forEach { region ->
                        FilterChip(
                            selected = state.settings.downloadRegion == region,
                            onClick = { onRegionChange(region) },
                            label = { Text(regionLabel(region)) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("硬件加速", style = MaterialTheme.typography.titleSmall)
                Switch(
                    checked = state.settings.preferNpuAcceleration,
                    onCheckedChange = onNpuToggle
                )
                Text("启用后，ONNX Runtime 走 NNAPI Execution Provider，" +
                     "在骁龙 8 Gen 3 上自动路由到 Hexagon NPU。",
                     style = MaterialTheme.typography.bodySmall)
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("下载策略", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("并行下载数", modifier = Modifier.weight(1f))
                    IconButton(onClick = { onMaxParallelChange(state.settings.maxParallelDownloads - 1) }) {
                        Icon(Icons.Default.Remove, contentDescription = "减少")
                    }
                    Text("${state.settings.maxParallelDownloads}",
                         style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { onMaxParallelChange(state.settings.maxParallelDownloads + 1) }) {
                        Icon(Icons.Default.Add, contentDescription = "增加")
                    }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("切换流派时自动下载推荐模型")
                        Text("如果关闭，每次手动确认才下载",
                             style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = state.settings.autoDownloadOnGenreSelect,
                        onCheckedChange = onAutoDownloadToggle
                    )
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("关于", style = MaterialTheme.typography.titleSmall)
                Text("Audio → MIDI AI  v0.1.0",
                     style = MaterialTheme.typography.bodySmall)
                Text("端侧多乐器音频转多轨 MIDI",
                     style = MaterialTheme.typography.bodySmall)
                Text("目标设备：荣耀 Magic 6 (Snapdragon 8 Gen 3 + Hexagon NPU)",
                     style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun regionLabel(region: DownloadRegion): String = when (region) {
    DownloadRegion.AUTO -> "自动"
    DownloadRegion.CHINA -> "国内"
    DownloadRegion.GLOBAL -> "海外"
}

// ============================================================
// PAGE 2: GENRE SELECT
// ============================================================

@Composable
fun GenreSelectPage(state: WizardUiState, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("选择流派", style = MaterialTheme.typography.headlineSmall)
        Text("不同流派有不同的模型组合。可以后续在「模型选择」页手动覆盖。",
             style = MaterialTheme.typography.bodySmall)
        if (state.isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.availablePresets.isEmpty()) {
            Text("没有可用的预设。请检查 assets/genre_presets.json 是否被打入 APK。",
                 color = MaterialTheme.colorScheme.error)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxHeight()
            ) {
                items(state.availablePresets) { preset ->
                    val selected = preset.id == state.selectedGenreId
                    Card(
                        onClick = { onSelect(preset.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(preset.emoji, style = MaterialTheme.typography.headlineLarge)
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(preset.displayName, fontWeight = FontWeight.SemiBold)
                                Text(preset.description,
                                     style = MaterialTheme.typography.bodySmall,
                                     maxLines = 2)
                                Text("最多 ${preset.maxInstruments} 轨",
                                     style = MaterialTheme.typography.labelSmall,
                                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (selected) {
                                Icon(Icons.Default.Check, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// PAGE 3: MODEL SELECT
// ============================================================

@Composable
fun ModelSelectPage(
    state: WizardUiState,
    onOverride: (ModelRole, String?) -> Unit,
    onShowAlternatives: (ModelRole) -> Unit
) {
    val config = state.currentConfig
    if (config == null) {
        Text("请先在上一页选择流派", color = MaterialTheme.colorScheme.error)
        return
    }

    var expandedRole: ModelRole? by remember { mutableStateOf(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("推荐模型", style = MaterialTheme.typography.headlineSmall)
        Text("带 ✓ 标记的是该流派推荐配置，可点击「更换」手动覆盖。",
             style = MaterialTheme.typography.bodySmall)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(config.models.entries.toList()) { (roleStr, modelId) ->
                val role = runCatching { ModelRole.valueOf(roleStr) }.getOrNull() ?: return@items
                val asset = state.availableModels.firstOrNull { it.id == modelId }
                val status = state.modelStatuses[modelId]
                val isAvailable = status?.isAvailable == true || asset?.bundled == true
                val isExpanded = expandedRole == role

                Card(shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isAvailable) Icons.Default.Star else Icons.Default.Downloading,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(asset?.displayName ?: modelId, fontWeight = FontWeight.Medium)
                                Text(role.name, style = MaterialTheme.typography.bodySmall)
                                status?.let { DownloadStatusText(it) }
                            }
                            TextButton(onClick = { expandedRole = if (isExpanded) null else role }) {
                                Text(if (isExpanded) "收起" else "更换")
                            }
                        }
                        if (isExpanded) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            val alternatives = state.availableModels.filter { it.role == role }
                            alternatives.forEach { alt ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = alt.id == modelId,
                                        onClick = {
                                            onOverride(role, alt.id)
                                            expandedRole = null
                                        }
                                    )
                                    Text(alt.displayName, modifier = Modifier.weight(1f))
                                    Text("${alt.sizeBytes / 1_000_000} MB",
                                         style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Row {
                                TextButton(onClick = {
                                    onOverride(role, null)
                                    expandedRole = null
                                }) {
                                    Text("跳过此阶段")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadStatusText(status: ModelStatus) {
    when (val st = status.state) {
        is DownloadState.Idle -> Text("待下载", style = MaterialTheme.typography.bodySmall)
        is DownloadState.Downloading -> {
            val pct = if (st.totalBytes > 0) (st.bytesDownloaded * 100 / st.totalBytes) else 0
            Text("下载中 $pct%  ${st.bytesPerSecond / 1024} KB/s",
                 style = MaterialTheme.typography.bodySmall)
        }
        is DownloadState.Verifying -> Text("校验中…", style = MaterialTheme.typography.bodySmall)
        is DownloadState.Completed -> Text("已就绪", style = MaterialTheme.typography.bodySmall)
        is DownloadState.Failed -> Text("失败：${st.errors.size} 个源",
                                         style = MaterialTheme.typography.bodySmall,
                                         color = MaterialTheme.colorScheme.error)
        is DownloadState.Cancelled -> Text("已取消", style = MaterialTheme.typography.bodySmall)
    }
}

// ============================================================
// PAGE 4: AUDIO SELECT
// ============================================================

@Composable
fun AudioSelectPage(
    state: WizardUiState,
    onPickFiles: () -> Unit,
    onRemoveUri: (Uri) -> Unit,
    onConversionCountChange: (Int) -> Unit,
    onMigrateMetadataToggle: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("选择音频文件", style = MaterialTheme.typography.headlineSmall)

        Button(onClick = onPickFiles, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.AudioFile, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("从文件管理器选择一个或多个音频")
        }

        if (state.selectedAudioUris.isNotEmpty()) {
            Text("已选 ${state.selectedAudioUris.size} 个文件",
                 style = MaterialTheme.typography.bodySmall)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.heightIn(max = 200.dp)
            ) {
                items(state.selectedAudioUris) { uri ->
                    Card {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                uri.lastPathSegment ?: uri.toString(),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                            IconButton(onClick = { onRemoveUri(uri) }) {
                                Icon(Icons.Default.Close, contentDescription = "移除")
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("转换次数", style = MaterialTheme.typography.titleSmall)
                Text("对同一音频多次转换可对比不同模型的效果（如质量 vs 速度）",
                     style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        onConversionCountChange(state.conversionCount - 1)
                    }) { Icon(Icons.Default.Remove, contentDescription = null) }
                    Text("${state.conversionCount}",
                         style = MaterialTheme.typography.headlineMedium)
                    IconButton(onClick = {
                        onConversionCountChange(state.conversionCount + 1)
                    }) { Icon(Icons.Default.Add, contentDescription = null) }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("元数据迁移", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.migrateMetadata,
                        onCheckedChange = onMigrateMetadataToggle
                    )
                    Text("将音频的 ID3 标签（标题/作者/专辑/年份）写入 MIDI 文件")
                }
                Text("勾选后，MIDI 文件的 track 0 会包含文本 meta 事件，" +
                     "可在支持 MIDI 文本查看的播放器中看到。",
                     style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ============================================================
// PAGE 5: CONFIRM
// ============================================================

@Composable
fun ConfirmPage(state: WizardUiState) {
    val config = state.currentConfig
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("确认转换", style = MaterialTheme.typography.headlineSmall)

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("流派预设：${state.selectedGenreId}", fontWeight = FontWeight.Medium)
                config?.let {
                    Text("使用模型数：${it.models.size}")
                    Text("最大乐器轨：${it.maxInstruments}")
                    Text("置信度阈值：${it.confidenceThreshold}")
                    Text("最短音符时长：${it.minNoteDurationMs} ms")
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("音频文件：${state.selectedAudioUris.size} 个", fontWeight = FontWeight.Medium)
                state.selectedAudioUris.take(3).forEach { uri ->
                    Text("• ${uri.lastPathSegment ?: uri}",
                         style = MaterialTheme.typography.bodySmall)
                }
                if (state.selectedAudioUris.size > 3) {
                    Text("…共 ${state.selectedAudioUris.size} 个",
                         style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("转换次数：${state.conversionCount}", fontWeight = FontWeight.Medium)
                Text("元数据迁移：${if (state.migrateMetadata) "开" else "关"}",
                     fontWeight = FontWeight.Medium)
            }
        }

        Card(colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )) {
            Column(Modifier.padding(16.dp)) {
                Text("预估时间", style = MaterialTheme.typography.titleSmall)
                val perSong = (state.currentConfig?.models?.size ?: 1) * 60 // ~1 min per model
                val total = perSong * state.selectedAudioUris.size * state.conversionCount
                Text("≈ ${total / 60} 分钟（基于经验和模型数量估算）",
                     style = MaterialTheme.typography.bodyMedium)
                Text("转换会在后台运行，可中途取消。完成后可在「完成」页试听。",
                     style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ============================================================
// PAGE 6: PROCESSING
// ============================================================

@Composable
fun ProcessingPage(state: WizardUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("正在转换…", style = MaterialTheme.typography.headlineSmall)

        val stage = state.currentPipelineStage
        val stageText = when (stage) {
            is PipelineStage.Loading -> "加载模型 ${stage.modelId} ${(stage.progress * 100).toInt()}%"
            is PipelineStage.Separating -> "分离音轨 ${(stage.progress * 100).toInt()}% ${stage.currentStem ?: ""}"
            is PipelineStage.Transcribing -> "转录 ${stage.stem} ${(stage.progress * 100).toInt()}%"
            is PipelineStage.DrumProcessing -> "鼓轨处理 ${(stage.progress * 100).toInt()}%"
            is PipelineStage.Classifying -> "识别乐器 ${stage.stem}"
            is PipelineStage.Merging -> "合并 MIDI ${(stage.progress * 100).toInt()}%"
            is PipelineStage.Done -> "完成：${stage.trackCount} 个音轨"
            is PipelineStage.Failed -> "失败（${stage.stage}）：${stage.error}"
            null -> "等待开始…"
        }

        Text(stageText, style = MaterialTheme.typography.bodyLarge)
        LinearProgressIndicator(
            progress = { stageProgress(stage) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )

        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun stageProgress(stage: PipelineStage?): Float = when (stage) {
    is PipelineStage.Loading -> stage.progress
    is PipelineStage.Separating -> stage.progress
    is PipelineStage.Transcribing -> stage.progress
    is PipelineStage.DrumProcessing -> stage.progress
    is PipelineStage.Merging -> stage.progress
    is PipelineStage.Done -> 1f
    else -> 0f
}

// ============================================================
// PAGE 7: COMPLETE
// ============================================================

@Composable
fun CompletePage(state: WizardUiState, onListen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("转换完成", style = MaterialTheme.typography.headlineSmall)
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        if (state.outputMidiPaths.isEmpty()) {
            Text("没有生成 MIDI 文件（可能由于错误或取消）",
                 color = MaterialTheme.colorScheme.error)
        } else {
            Text("共生成 ${state.outputMidiPaths.size} 个 MIDI 文件：",
                 fontWeight = FontWeight.Medium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.outputMidiPaths) { path ->
                    Card {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(path, modifier = Modifier.weight(1f),
                                 style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { onListen(path) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("试听")
                            }
                        }
                    }
                }
            }
        }

        state.errorMessage?.let {
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )) {
                Text(it, modifier = Modifier.padding(12.dp))
            }
        }
    }
}
