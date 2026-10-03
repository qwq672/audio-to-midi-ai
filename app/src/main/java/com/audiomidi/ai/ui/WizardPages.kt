package com.audiomidi.ai.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.audiomidi.ai.data.DownloadRegion
import com.audiomidi.ai.data.GenrePreset
import com.audiomidi.ai.data.ModelAsset
import com.audiomidi.ai.data.ModelRole
import com.audiomidi.ai.model.DownloadState
import com.audiomidi.ai.model.ModelStatus
import com.audiomidi.ai.pipeline.PipelineStage

// Common spacing constants
private val xs = 4.dp
private val sm = 8.dp
private val md = 12.dp
private val lg = 16.dp
private val xl = 20.dp
private val xxl = 24.dp

// ============================================================
// Top step indicator — modern pill style with chevrons
// ============================================================

@Composable
fun StepIndicator(currentStep: WizardStep) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = lg, vertical = sm)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WizardStep.values().forEachIndexed { idx, step ->
                val isActive = step == currentStep
                val isDone = step.ordinal < currentStep.ordinal
                val bg = when {
                    isActive -> MaterialTheme.colorScheme.primary
                    isDone -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val fg = when {
                    isActive -> MaterialTheme.colorScheme.onPrimary
                    isDone -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = bg,
                        shape = CircleShape,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isDone) {
                                Icon(Icons.Default.Check,
                                    contentDescription = null,
                                    tint = fg,
                                    modifier = Modifier.size(16.dp))
                            } else {
                                Text("${step.position}",
                                     color = fg,
                                     style = MaterialTheme.typography.labelSmall,
                                     fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Spacer(Modifier.width(sm))
                    Text(
                        stepLabel(step),
                        color = if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (idx < WizardStep.values().size - 1) {
                        Spacer(Modifier.width(sm))
                        Icon(Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(16.dp))
                    }
                }
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

private fun stepFullLabel(step: WizardStep): String = when (step) {
    WizardStep.HOME -> "首页 · 总设置"
    WizardStep.GENRE_SELECT -> "第 1 步 · 选择流派"
    WizardStep.MODEL_SELECT -> "第 2 步 · 选择模型"
    WizardStep.AUDIO_SELECT -> "第 3 步 · 选择音频"
    WizardStep.CONFIRM -> "第 4 步 · 确认"
    WizardStep.PROCESSING -> "第 5 步 · 处理中"
    WizardStep.COMPLETE -> "第 6 步 · 完成"
}

// ============================================================
// Bottom navigation
// ============================================================

@Composable
fun WizardBottomBar(
    state: WizardUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCancel: () -> Unit,
    onStart: () -> Unit,
    onRestart: () -> Unit
) {
    Surface(
        shadowElevation = 12.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = lg, vertical = md),
            horizontalArrangement = Arrangement.spacedBy(md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (state.currentStep) {
                WizardStep.HOME -> {
                    Button(onClick = onNext, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(sm))
                        Text("开始向导")
                        Spacer(Modifier.width(xs))
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                    }
                }
                WizardStep.GENRE_SELECT,
                WizardStep.MODEL_SELECT,
                WizardStep.AUDIO_SELECT -> {
                    OutlinedButton(onClick = onPrev, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(xs))
                        Text("上一步")
                    }
                    Button(onClick = onNext, modifier = Modifier.weight(1f),
                        enabled = canAdvanceFrom(state.currentStep, state),
                        shape = RoundedCornerShape(12.dp)) {
                        Text("下一步")
                        Spacer(Modifier.width(xs))
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                    }
                }
                WizardStep.CONFIRM -> {
                    OutlinedButton(onClick = onPrev, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(xs))
                        Text("上一步")
                    }
                    Button(onClick = onStart, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(sm))
                        Text("开始转换")
                    }
                }
                WizardStep.PROCESSING -> {
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null)
                        Spacer(Modifier.width(sm))
                        Text("取消转换")
                    }
                }
                WizardStep.COMPLETE -> {
                    OutlinedButton(onClick = onRestart, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(sm))
                        Text("再来一次")
                    }
                    Button(onClick = { }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(sm))
                        Text("导出 / 分享")
                    }
                }
            }
        }
    }
}

private fun canAdvanceFrom(step: WizardStep, state: WizardUiState): Boolean = when (step) {
    WizardStep.GENRE_SELECT -> state.selectedGenreId.isNotBlank() && state.isReady
    WizardStep.MODEL_SELECT -> state.currentConfig != null
    WizardStep.AUDIO_SELECT -> state.selectedAudioUris.isNotEmpty()
    else -> true
}

// ============================================================
// PAGE 1: HOME (Settings) — hero + cards
// ============================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomePage(
    state: WizardUiState,
    onRegionChange: (DownloadRegion) -> Unit,
    onMaxParallelChange: (Int) -> Unit,
    onNpuToggle: (Boolean) -> Unit,
    onAutoDownloadToggle: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(lg)) {
        // Hero header
        Card(
            shape = RoundedCornerShape(xxl),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(xl)) {
                Text("Audio To MIDI AI",
                     style = MaterialTheme.typography.headlineLarge,
                     color = MaterialTheme.colorScheme.onPrimaryContainer,
                     fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(xs))
                Text("端侧多乐器音频转多轨 MIDI",
                     style = MaterialTheme.typography.bodyLarge,
                     color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
                Spacer(Modifier.height(md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Smartphone, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(xs))
                    Text("目标：Honor Magic 6 · Snapdragon 8 Gen 3 · Hexagon NPU",
                         style = MaterialTheme.typography.labelSmall,
                         color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                }
            }
        }

        // Settings: Download region
        SettingsCard(
            icon = Icons.Default.Public,
            title = "下载地区",
            subtitle = "决定从哪个镜像优先下载模型"
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(sm)) {
                DownloadRegion.values().forEach { region ->
                    FilterChip(
                        selected = state.settings.downloadRegion == region,
                        onClick = { onRegionChange(region) },
                        label = { Text(regionLabel(region)) },
                        shape = RoundedCornerShape(50)
                    )
                }
            }
            Spacer(Modifier.height(xs))
            Text(
                "国内用户推荐选「国内」(hf-mirror 优先)，自动模式会先测速",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Settings: NPU
        SettingsCard(
            icon = Icons.Default.Memory,
            title = "硬件加速",
            subtitle = "在骁龙 8 Gen 3 上路由到 Hexagon NPU"
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("启用 NNAPI Execution Provider",
                         style = MaterialTheme.typography.bodyMedium)
                    Text("关闭后走 CPU，速度慢但兼容性好",
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.settings.preferNpuAcceleration,
                    onCheckedChange = onNpuToggle
                )
            }
        }

        // Settings: Download strategy
        SettingsCard(
            icon = Icons.Default.Download,
            title = "下载策略",
            subtitle = "并行下载数量与自动下载"
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("并行下载数", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { onMaxParallelChange(state.settings.maxParallelDownloads - 1) }) {
                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = "减少")
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ) {
                    Text("${state.settings.maxParallelDownloads}",
                         modifier = Modifier.padding(horizontal = md, vertical = xs),
                         style = MaterialTheme.typography.titleLarge,
                         color = MaterialTheme.colorScheme.onPrimaryContainer,
                         fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { onMaxParallelChange(state.settings.maxParallelDownloads + 1) }) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = "增加")
                }
            }
            HorizontalDivider(Modifier.padding(vertical = md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("切换流派时自动下载推荐模型",
                         style = MaterialTheme.typography.bodyMedium)
                    Text("关闭后每次手动确认才下载",
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.settings.autoDownloadOnGenreSelect,
                    onCheckedChange = onAutoDownloadToggle
                )
            }
        }

        Card(
            shape = RoundedCornerShape(xxl),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Row(Modifier.padding(lg), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(md))
                Column(Modifier.weight(1f)) {
                    Text("v0.1.0 skeleton",
                         style = MaterialTheme.typography.labelMedium,
                         color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text("音频解码 + MIDI 合成器待实现",
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(xxl),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(md))
                Column(Modifier.weight(1f)) {
                    Text(title,
                         style = MaterialTheme.typography.titleMedium,
                         fontWeight = FontWeight.SemiBold)
                    Text(subtitle,
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(md))
            content()
        }
    }
}

private fun regionLabel(region: DownloadRegion): String = when (region) {
    DownloadRegion.AUTO -> "自动检测"
    DownloadRegion.CHINA -> "国内优先"
    DownloadRegion.GLOBAL -> "海外优先"
}

// ============================================================
// PAGE 2: GENRE SELECT
// ============================================================

@Composable
fun GenreSelectPage(state: WizardUiState, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(lg)) {
        // Page header
        Column {
            Text("选择歌曲流派", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("不同流派有不同的模型组合，后续可在「模型选择」页手动覆盖",
                 style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (state.isLoading || !state.isReady) {
            Card(shape = RoundedCornerShape(xxl), modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(xl).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(md))
                    Text("正在加载流派预设…",
                         style = MaterialTheme.typography.bodyMedium,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (state.availablePresets.isEmpty()) {
            Card(
                shape = RoundedCornerShape(xxl),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(Modifier.padding(lg)) {
                    Text("没有可用的预设",
                         style = MaterialTheme.typography.titleMedium,
                         color = MaterialTheme.colorScheme.onErrorContainer)
                    Text("请检查 assets/genre_presets.json 是否被打入 APK",
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(md),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(state.availablePresets) { preset ->
                    GenreCard(
                        preset = preset,
                        selected = preset.id == state.selectedGenreId,
                        onClick = { onSelect(preset.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GenreCard(preset: GenrePreset, selected: Boolean, onClick: () -> Unit) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                         else MaterialTheme.colorScheme.surface
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outlineVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(xxl),
        color = containerColor,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        tonalElevation = if (selected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(preset.emoji,
                         style = MaterialTheme.typography.headlineLarge)
                }
            }
            Spacer(Modifier.width(lg))
            Column(Modifier.weight(1f)) {
                Text(preset.displayName,
                     style = MaterialTheme.typography.titleLarge,
                     fontWeight = FontWeight.SemiBold,
                     color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                             else MaterialTheme.colorScheme.onSurface)
                Text(preset.description,
                     style = MaterialTheme.typography.bodySmall,
                     color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                             else MaterialTheme.colorScheme.onSurfaceVariant,
                     maxLines = 2,
                     overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(xs))
                AssistChip(
                    onClick = {},
                    label = { Text("最多 ${preset.maxInstruments} 轨") },
                    shape = RoundedCornerShape(50)
                )
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle,
                    contentDescription = "已选",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp))
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
        Column {
            Text("请先在上一页选择流派",
                 style = MaterialTheme.typography.titleMedium,
                 color = MaterialTheme.colorScheme.error)
        }
        return
    }

    var expandedRole: ModelRole? by remember { mutableStateOf(null) }

    Column(verticalArrangement = Arrangement.spacedBy(md)) {
        Column {
            Text("推荐模型配置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("带 ✓ 的是该流派的推荐模型，可点击「更换」手动覆盖",
                 style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(md),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(config.models.entries.toList()) { (roleStr, modelId) ->
                val role = runCatching { ModelRole.valueOf(roleStr) }.getOrNull() ?: return@items
                val asset = state.availableModels.firstOrNull { it.id == modelId }
                val status = state.modelStatuses[modelId]
                val isAvailable = status?.isAvailable == true || asset?.bundled == true
                val isExpanded = expandedRole == role
                ModelRoleCard(
                    role = role,
                    modelId = modelId,
                    asset = asset,
                    status = status,
                    isAvailable = isAvailable,
                    isExpanded = isExpanded,
                    onExpandToggle = { expandedRole = if (isExpanded) null else role },
                    onOverride = { newId ->
                        onOverride(role, newId)
                        expandedRole = null
                    },
                    alternatives = state.availableModels.filter { it.role == role }
                )
            }
        }
    }
}

@Composable
private fun ModelRoleCard(
    role: ModelRole,
    modelId: String,
    asset: ModelAsset?,
    status: ModelStatus?,
    isAvailable: Boolean,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onOverride: (String?) -> Unit,
    alternatives: List<ModelAsset>
) {
    Card(
        shape = RoundedCornerShape(xxl),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (isAvailable) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isAvailable) Icons.Default.Check else Icons.Default.Downloading,
                            contentDescription = null,
                            tint = if (isAvailable) MaterialTheme.colorScheme.onTertiaryContainer
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(md))
                Column(Modifier.weight(1f)) {
                    Text(asset?.displayName ?: modelId,
                         style = MaterialTheme.typography.titleMedium,
                         fontWeight = FontWeight.SemiBold)
                    Text(roleLabel(role),
                         style = MaterialTheme.typography.labelSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                    status?.let { DownloadStatusBadge(it) }
                }
                TextButton(onClick = onExpandToggle) {
                    Text(if (isExpanded) "收起" else "更换")
                    Spacer(Modifier.width(xs))
                    Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                         contentDescription = null)
                }
            }

            if (isExpanded) {
                HorizontalDivider(Modifier.padding(vertical = md))
                alternatives.forEach { alt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOverride(alt.id) }
                            .padding(vertical = xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = alt.id == modelId,
                            onClick = { onOverride(alt.id) }
                        )
                        Spacer(Modifier.width(sm))
                        Column(Modifier.weight(1f)) {
                            Text(alt.displayName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${alt.sizeBytes / 1_000_000} MB · ${alt.genreTags.joinToString(", ") }",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(xs))
                TextButton(onClick = { onOverride(null) }) {
                    Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(xs))
                    Text("跳过此阶段")
                }
            }
        }
    }
}

@Composable
private fun DownloadStatusBadge(status: ModelStatus) {
    val (color, text) = when (val st = status.state) {
        is DownloadState.Idle -> MaterialTheme.colorScheme.surfaceVariant to "待下载"
        is DownloadState.Downloading -> {
            val pct = if (st.totalBytes > 0) (st.bytesDownloaded * 100 / st.totalBytes) else 0
            MaterialTheme.colorScheme.primaryContainer to "下载中 $pct%"
        }
        is DownloadState.Verifying -> MaterialTheme.colorScheme.tertiaryContainer to "校验中"
        is DownloadState.Completed -> MaterialTheme.colorScheme.secondaryContainer to "已就绪"
        is DownloadState.Failed -> MaterialTheme.colorScheme.errorContainer to "失败"
        is DownloadState.Cancelled -> MaterialTheme.colorScheme.surfaceVariant to "已取消"
    }
    Surface(color = color, shape = RoundedCornerShape(50)) {
        Text(text,
             modifier = Modifier.padding(horizontal = sm, vertical = xs / 2),
             style = MaterialTheme.typography.labelSmall)
    }
}

private fun roleLabel(role: ModelRole): String = when (role) {
    ModelRole.SOURCE_SEPARATOR -> "音源分离"
    ModelRole.VOCAL_TRANSCRIBER -> "人声转录"
    ModelRole.BASS_TRANSCRIBER -> "贝斯转录"
    ModelRole.PIANO_TRANSCRIBER -> "钢琴转录"
    ModelRole.GUITAR_TRANSCRIBER -> "吉他转录"
    ModelRole.DRUM_PROCESSOR -> "鼓轨处理"
    ModelRole.OTHER_TRANSCRIBER -> "其他乐器转录"
    ModelRole.END_TO_END_TRANSCRIBER -> "端到端多乐器转录"
    ModelRole.INSTRUMENT_CLASSIFIER -> "音色识别"
    ModelRole.SECONDARY_SEPARATOR -> "二次分离"
    ModelRole.DRUM_TYPE_CLASSIFIER -> "鼓类型识别"
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
    Column(verticalArrangement = Arrangement.spacedBy(lg)) {
        Column {
            Text("选择音频文件", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("可以一次选多个文件，会按顺序处理",
                 style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Pick button — big prominent
        Card(
            shape = RoundedCornerShape(xxl),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(xl).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(md))
                Text("从文件管理器选择音频",
                     style = MaterialTheme.typography.titleMedium,
                     color = MaterialTheme.colorScheme.onPrimaryContainer,
                     fontWeight = FontWeight.SemiBold)
                Text("支持 MP3 / M4A / WAV / FLAC 等格式",
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
                Spacer(Modifier.height(md))
                Button(onClick = onPickFiles, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Spacer(Modifier.width(sm))
                    Text("选择音频文件")
                }
            }
        }

        // Selected files list
        if (state.selectedAudioUris.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("已选 ${state.selectedAudioUris.size} 个文件",
                         style = MaterialTheme.typography.titleSmall,
                         modifier = Modifier.weight(1f))
                    Text("点击 ✕ 移除",
                         style = MaterialTheme.typography.labelSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.selectedAudioUris.forEach { uri ->
                    SelectedFileChip(uri = uri, onRemove = { onRemoveUri(uri) })
                }
            }
        }

        // Conversion count + metadata toggle
        Card(shape = RoundedCornerShape(xxl)) {
            Column(Modifier.padding(lg), verticalArrangement = Arrangement.spacedBy(md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Repeat, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(md))
                    Column(Modifier.weight(1f)) {
                        Text("转换次数",
                             style = MaterialTheme.typography.titleMedium,
                             fontWeight = FontWeight.SemiBold)
                        Text("对同一音频多次转换可对比不同效果",
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onConversionCountChange(state.conversionCount - 1) }) {
                        Icon(Icons.Default.RemoveCircleOutline,
                            contentDescription = "减少",
                            modifier = Modifier.size(32.dp))
                    }
                    Text("${state.conversionCount}",
                         style = MaterialTheme.typography.displaySmall,
                         fontWeight = FontWeight.Bold,
                         modifier = Modifier.padding(horizontal = lg))
                    IconButton(onClick = { onConversionCountChange(state.conversionCount + 1) }) {
                        Icon(Icons.Default.AddCircleOutline,
                            contentDescription = "增加",
                            modifier = Modifier.size(32.dp))
                    }
                }
            }
        }

        // Metadata migration toggle
        Card(shape = RoundedCornerShape(xxl)) {
            Row(
                Modifier.padding(lg).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Label, contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(md))
                Column(Modifier.weight(1f)) {
                    Text("迁移音频元数据到 MIDI",
                         style = MaterialTheme.typography.titleMedium,
                         fontWeight = FontWeight.SemiBold)
                    Text("把 ID3 标签（标题/作者/专辑/年份）写入 MIDI 文件",
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.migrateMetadata,
                    onCheckedChange = onMigrateMetadataToggle
                )
            }
        }
    }
}

@Composable
private fun SelectedFileChip(uri: Uri, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = md, vertical = sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(sm))
            Text(
                uri.lastPathSegment ?: uri.toString(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close,
                    contentDescription = "移除",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp))
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
    Column(verticalArrangement = Arrangement.spacedBy(lg)) {
        Column {
            Text("确认转换配置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("下面是即将应用的设置，请确认后开始",
                 style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Genre preset summary
        SummaryCard(
            icon = Icons.Default.Category,
            title = "流派预设",
            primary = state.selectedGenreId.uppercase(),
            secondary = "使用模型数：${config?.models?.size ?: 0} 个"
        ) {
            config?.let {
                Column(verticalArrangement = Arrangement.spacedBy(xs)) {
                    SummaryRow("最大乐器轨", "${it.maxInstruments}")
                    SummaryRow("置信度阈值", "${it.confidenceThreshold}")
                    SummaryRow("最短音符时长", "${it.minNoteDurationMs} ms")
                }
            }
        }

        SummaryCard(
            icon = Icons.Default.QueueMusic,
            title = "音频文件",
            primary = "${state.selectedAudioUris.size} 个文件",
            secondary = null
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(xs)) {
                state.selectedAudioUris.take(3).forEach { uri ->
                    Text("• ${uri.lastPathSegment ?: uri}",
                         style = MaterialTheme.typography.bodySmall,
                         maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (state.selectedAudioUris.size > 3) {
                    Text("…共 ${state.selectedAudioUris.size} 个",
                         style = MaterialTheme.typography.labelSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        SummaryCard(
            icon = Icons.Default.Settings,
            title = "转换设置",
            primary = "转换次数：${state.conversionCount}",
            secondary = null
        ) {
            SummaryRow("元数据迁移",
                if (state.migrateMetadata) "开" else "关")
        }

        // Estimate
        Card(
            shape = RoundedCornerShape(xxl),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Row(Modifier.padding(lg), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(md))
                Column {
                    val perSong = (state.currentConfig?.models?.size ?: 1) * 60
                    val total = perSong * state.selectedAudioUris.size * state.conversionCount
                    Text("预估时间 ≈ ${total / 60} 分钟",
                         style = MaterialTheme.typography.titleMedium,
                         color = MaterialTheme.colorScheme.onTertiaryContainer,
                         fontWeight = FontWeight.SemiBold)
                    Text("基于经验估算，实际取决于音频长度和模型规模",
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f))
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    primary: String,
    secondary: String?,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(xxl),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(md))
                Column(Modifier.weight(1f)) {
                    Text(title,
                         style = MaterialTheme.typography.labelMedium,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(primary,
                         style = MaterialTheme.typography.titleMedium,
                         fontWeight = FontWeight.SemiBold)
                    secondary?.let {
                        Text(it,
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(md))
            HorizontalDivider()
            Spacer(Modifier.height(md))
            content()
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
             fontWeight = FontWeight.Medium)
    }
}

// ============================================================
// PAGE 6: PROCESSING
// ============================================================

@Composable
fun ProcessingPage(state: WizardUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(lg))

        // Animated rotating circle
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp))
            }
        }

        Text("正在转换…",
             style = MaterialTheme.typography.headlineMedium,
             fontWeight = FontWeight.Bold)

        val stage = state.currentPipelineStage
        val stageText = when (stage) {
            is PipelineStage.Loading -> "加载模型 ${stage.modelId} · ${(stage.progress * 100).toInt()}%"
            is PipelineStage.Separating -> "分离音轨 · ${(stage.progress * 100).toInt()}%${stage.currentStem?.let { " · $it" } ?: ""}"
            is PipelineStage.Transcribing -> "转录 ${stage.stem} · ${(stage.progress * 100).toInt()}%"
            is PipelineStage.DrumProcessing -> "鼓轨处理 · ${(stage.progress * 100).toInt()}%"
            is PipelineStage.Classifying -> "识别乐器 · ${stage.stem}"
            is PipelineStage.Merging -> "合并 MIDI · ${(stage.progress * 100).toInt()}%"
            is PipelineStage.Done -> "完成 · ${stage.trackCount} 个音轨"
            is PipelineStage.Failed -> "失败（${stage.stage}）"
            null -> "等待开始…"
        }

        Text(stageText,
             style = MaterialTheme.typography.bodyLarge,
             color = MaterialTheme.colorScheme.onSurfaceVariant)

        LinearProgressIndicator(
            progress = { stageProgress(stage) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )

        state.errorMessage?.let {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(md))
                    Text(it,
                         style = MaterialTheme.typography.bodyMedium,
                         color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        Text("转换会在后台运行，点击底部「取消转换」中断",
             style = MaterialTheme.typography.bodySmall,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Column(verticalArrangement = Arrangement.spacedBy(lg)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(lg))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(56.dp))
                }
            }
            Spacer(Modifier.height(md))
            Text("转换完成",
                 style = MaterialTheme.typography.headlineLarge,
                 fontWeight = FontWeight.Bold)
            if (state.outputMidiPaths.isNotEmpty()) {
                Text("共生成 ${state.outputMidiPaths.size} 个 MIDI 文件",
                     style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (state.outputMidiPaths.isEmpty()) {
            Card(
                shape = RoundedCornerShape(xxl),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(Modifier.padding(lg), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(md))
                    Text("没有生成 MIDI 文件（可能由于错误或取消）",
                         style = MaterialTheme.typography.bodyMedium,
                         color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(md)) {
                state.outputMidiPaths.forEach { path ->
                    Card(
                        shape = RoundedCornerShape(xxl),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(lg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.AudioFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                            Spacer(Modifier.width(md))
                            Column(Modifier.weight(1f)) {
                                Text(path.substringAfterLast('/'),
                                     style = MaterialTheme.typography.titleSmall,
                                     fontWeight = FontWeight.SemiBold,
                                     maxLines = 1,
                                     overflow = TextOverflow.Ellipsis)
                                Text(path,
                                     style = MaterialTheme.typography.labelSmall,
                                     color = MaterialTheme.colorScheme.onSurfaceVariant,
                                     maxLines = 1,
                                     overflow = TextOverflow.Ellipsis)
                            }
                            Button(onClick = { onListen(path) },
                                shape = RoundedCornerShape(50)) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(xs))
                                Text("试听")
                            }
                        }
                    }
                }
            }
        }

        state.errorMessage?.let {
            Card(
                shape = RoundedCornerShape(xxl),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(Modifier.padding(lg), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(md))
                    Text(it,
                         style = MaterialTheme.typography.bodyMedium,
                         color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}
