# Audio To MIDI AI

端侧多乐器音频转多轨 MIDI 的 Android 应用骨架。专为 **荣耀 Magic 6 (Snapdragon 8 Gen 3 + Hexagon NPU)** 设计，依赖 ONNX Runtime Android 的 NNAPI Execution Provider 自动路由到 NPU 加速。

> **状态**: v0.1.0 — 项目骨架完成。核心数据类、多源下载器、Pipeline 接口、Compose UI 都已就位；实际 ONNX 推理（Demucs/Basic Pitch/MT3）和音频 IO 为 TODO，需要在骨架上继续实现。

## 设计目标

- **离线优先**：完全本地处理，不上传用户音频
- **多乐器 MIDI 输出**：每条轨带 General MIDI program 号，避免"钢琴总谱"陷阱
- **流派感知**：Pop/Rock/Electronic/Classical/Jazz/Hip-hop/Folk/Metal 各有专属模型组合
- **可手动覆盖**：每个 pipeline 阶段的推荐模型可单独替换，不锁死
- **多源下载**：每个模型配置多个镜像（hf-mirror / HuggingFace / ModelScope / OpenI），自动 fallback + 断点续传 + SHA256 校验
- **NPU 友好**：模型走 INT8 量化，ONNX Runtime NNAPI EP 自动走 Hexagon NPU

## 项目结构

```
app/
├── build.gradle.kts                  ← 依赖配置（ONNX Runtime / OkHttp / Compose / Room）
└── src/main/
    ├── AndroidManifest.xml
    ├── assets/
    │   ├── manifest.json              ← 模型注册表（每个模型的多镜像 URL + SHA256）
    │   └── genre_presets.json         ← 流派→推荐模型映射表
    ├── java/com/audiomidi/ai/
    │   ├── AudioToMidiApp.kt          ← Application 类，装配所有单例
    │   ├── MainActivity.kt           ← Compose UI 入口
    │   ├── data/                      ← 数据类（不可变 model）
    │   │   ├── ModelAsset.kt
    │   │   ├── ModelSource.kt
    │   │   ├── ModelRole.kt
    │   │   ├── GenrePreset.kt
    │   │   ├── PipelineConfig.kt
    │   │   └── UserSettings.kt
    │   ├── model/                     ← 模型管理（下载/校验/注册表）
    │   │   ├── ModelManager.kt
    │   │   ├── Downloader.kt
    │   │   ├── ModelRegistry.kt
    │   │   └── DownloadState.kt
    │   ├── pipeline/                  ← 处理流水线（接口 + 实现）
    │   │   ├── AudioData.kt
    │   │   ├── PipelineInterfaces.kt  ← SourceSeparator / Transcriber / InstrumentClassifier / DrumProcessor / MidiWriter
    │   │   ├── PipelineExecutor.kt   ← 编排器，按 PipelineConfig 装配
    │   │   ├── PipelineService.kt     ← 前台服务（处理中长时跑）
    │   │   └── impl/
    │   │       ├── DemucsSeparator.kt
    │   │       ├── BasicPitchTranscriber.kt
    │   │       ├── KongPianoTranscriber.kt
    │   │       ├── YamNetClassifier.kt
    │   │       ├── LibrosaOnsetProcessor.kt
    │   │       └── KotlinMidiWriter.kt   ← 纯 Kotlin 标准 MIDI 文件写入器
    │   └── ui/
    │       ├── MainViewModel.kt
    │       └── theme/
    │           └── Theme.kt
    └── res/
        ├── values/                    ← strings.xml / colors.xml / themes.xml
        ├── xml/                        ← backup rules
        ├── drawable/                   ← launcher icon
        └── mipmap-anydpi-v26/         ← adaptive icon
```

## 构建与运行

### 环境要求

- **Android Studio**: Hedgehog (2023.1.1) 或更新
- **JDK**: 17+
- **Android SDK**: compileSdk 34（Android 14）
- **Gradle**: 8.7（项目自带 wrapper，会自动下载）

### 在 Android Studio 中打开

1. `File → Open → 选择仓库根目录`
2. 等待 Gradle sync 完成（首次会下载依赖，约 5-10 分钟）
3. 连接荣耀 Magic 6（开发者模式 + USB 调试）
4. 点击 ▶ Run

### 命令行构建

```bash
# Linux/macOS
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug

# 输出 APK
app/build/outputs/apk/debug/app-debug.apk
```

### 国内构建加速

`settings.gradle.kts` 已经预配了阿里云 Maven 镜像，Gradle sync 在国内网速下也能稳定完成。如果仍然慢，编辑 `gradle/wrapper/gradle-wrapper.properties`，把 `distributionUrl` 换成腾讯云镜像：

```
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip
```

## 模型下载

首次启动应用会：
1. 探测 `hf-mirror.com`、`huggingface.co`、`modelscope.cn` 三个源的 TTFB
2. 自动选择最快的源作为主源
3. 根据"Pop/Rock"默认流派，下载所需模型（Demucs 6s INT8 ~80MB + Bytedance Piano INT8 ~8MB）

用户切换流派时，按需下载该流派推荐模型（不阻塞其他操作）。

所有模型缓存于 `/data/data/com.audiomidi.ai/files/models/`，用户可在"模型管理"页面手动删除释放存储。

## 镜像优先级（用户可在设置中切换）

| 用户地区 | 优先级 |
|---|---|
| **国内**（自动检测或手动选） | hf-mirror → ModelScope → OpenI → HuggingFace |
| **海外** | HuggingFace → hf-mirror → ModelScope |
| **自动** | 同时探测 TTFB，选最快的 |

## 流派预设

`assets/genre_presets.json` 定义了 8 个内置流派：

| 流派 | 主分离器 | 转录器 | 鼓处理 | 分类器 | maxInstr |
|---|---|---|---|---|---|
| Pop/Rock | Demucs 6s | Basic Pitch + Kong Piano | librosa onset | YamNet | 12 |
| Rock/Metal | Demucs 4s_ft | 同上 | librosa onset | YamNet | 14 |
| Electronic | Demucs 6s | Basic Pitch + Kong Piano | librosa onset | OpenL3 | 10 |
| Classical | —（跳过分轨） | MT3 端到端 | — | YamNet | 15 |
| Jazz | Demucs 6s | Basic Pitch + Kong Piano | librosa onset | YamNet | 10 |
| Hip-hop | Demucs 6s | Basic Pitch | librosa onset | YamNet | 8 |
| Folk | Demucs 4s | Basic Pitch | — | YamNet | 6 |
| Auto | 自动选择 | 自动选择 | 自动选择 | 自动选择 | 12 |

每个流派都可以手动覆盖任意阶段使用的模型，覆盖后可"另存为自定义预设"。

## 防幻觉机制

- **max_instruments 硬上限**：默认 12，古典流派允许 15，避免一首歌出几十轨
- **置信度阈值**：YamNet 置信度 < 0.6 的分类结果丢弃
- **最短音符时长**：< 2 秒的音符直接过滤（多半是误识别）
- **同源合并**：多次识别到同一 GM program 的音符并入同一条轨

## 已知限制（v0.1.0）

### 模型推理（未实现）
- `DemucsSeparator`、`BasicPitchTranscriber`、`KongPianoTranscriber`、`YamNetClassifier` 都是骨架实现
- ONNX session 创建逻辑已写好，但实际推理循环（音频块化、tensor 构造、输出解码）需要继续实现
- 参考各文件头部的 `// TODO` 注释

### 音频 IO（未实现）
- `MainViewModel.setInputAudio()` 目前只保存路径，未实际解码
- 需要集成 `MediaExtractor` + PCM 解码 + 重采样
- 推荐用 [TarsosDSP](https://github.com/JorenSix/TarsosDSP) 或自己写 PCM 解码

### UI（部分完成）
- 流派选择器、模型列表、进度展示已实现
- "更换"按钮未接（需要做模型选择对话框）
- 设置页未实现（地区/并行下载数等设置）

### 自定义预设持久化（未实现）
- `UserSettings.customPresets` 字段定义了，但未用 Room/DataStore 持久化
- App 重启后自定义预设会丢失

## 后续开发路线图

**v0.2**：Basic Pitch 真实推理打通
- 输入音频解码（MediaExtractor + PCM）
- Basic Pitch ONNX 输入/输出张量构造
- 输出 MIDI 验证可用（用钢琴独奏音频测试）

**v0.3**：Demucs 6s 真实推理
- 分块处理 + overlap-add
- 6 个分轨输出验证可用

**v0.4**：YamNet 分类
- 音色 → GM program 映射表完善（assets/yamnet_to_gm.json）
- 置信度阈值过滤

**v0.5**：所有内置流派预设可跑通端到端

**v0.6**：自定义预设持久化 + 设置页

**v0.7**：MT3 端到端流程（古典流派）

**v0.8**：A/B 对比模式 + 自定义预设 UI

## 安全提示

- **不要把 GitHub PAT 或其他密钥提交进仓库**
- `.gitignore` 已配置为忽略 `*.env`、`secrets.properties`
- 模型文件 (`*.onnx`、`*.tflite`) 也被忽略，避免仓库膨胀
- 默认下载的 Basic Pitch 和 YamNet 应放在 `app/src/main/assets/`，文件名匹配 `.gitignore` 的白名单

## License

TBD（建议 Apache 2.0，与上游 Demucs / Basic Pitch / MT3 兼容）
