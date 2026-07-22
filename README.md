# Watermark Studio

Android 应用：批量添加文字/Logo 水印，以及基于**本地区域模糊混合**的图章消除（非 AI）。支持 Google Play 订阅与 AdMob 变现。

## 构建与运行

**环境**：Android Studio，JDK 11+，minSdk 24。

```powershell
.\gradlew.bat :app:installDebug
```

单元测试：

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

## Release 签名与 AAB

1. 复制 `keystore.properties.example` → `keystore.properties`，填入 `my-upload-key.jks` 密码。
2. 构建：`.\gradlew.bat bundleRelease`
3. 输出：`app/build/outputs/bundle/release/app-release.aab`

配置不完整时会在 Gradle 配置阶段报错，避免 `signReleaseBundle` NPE。

## 发布前生产检查清单

| 项 | 位置 | 说明 |
|----|------|------|
| AdMob 正式 ID | `app/src/release/res/values/admob.xml` | 替换为 AdMob 控制台中的 App ID / 横幅 / 插屏单元 ID；debug 使用测试 ID |
| AdMob 应用验证 | `docs/app-ads.txt` → GitHub Pages | 发布者 `pub-8798068561135489`；Play「网站」须与可访问的 `…/app-ads.txt` 域名一致，见 `docs/readme.md` |
| Play 订阅 SKU | Play Console + `BillingProducts.kt` | `com.watermark.pro.weekly/monthly/yearly` 必须与控制台一致 |
| 隐私政策 / 条款 URL | `values/strings.xml` | 已指向 GitHub Pages；部署见 `docs/readme.md` |
| UMP 同意 | `UmpConsentHelper.kt` | 在 AdMob 初始化前请求；EEA 投放广告前必测 |
| 免费导出次数 | `res/values/integers.xml` `free_exports_per_day` | 默认 3，与添加/去水印共用 |
| ProGuard | `app/proguard-rules.pro` | 已保留 `com.watermarkstudio.*` 模型与 ViewModel |
| 去水印算法说明 | 商店文案 | 本地 OpenCV/时序恢复；Pro 光流+保留原声；勿宣传全 AI 修复 |
| keystore | 勿提交仓库 | `keystore.properties`、`*.jks` 已在 `.gitignore` |
| 16 KB 页面 | NDK r28 + OpenCV **4.12+** + ONNX Runtime **≥1.22.0** | `libonnxruntime4j_jni.so` 在 1.20 为 4KB 对齐；自研 `removal_native` 见 `CMakeLists.txt`；FFmpeg 使用 `ffmpeg-kit-16kb` |

## 核心业务流程

### 添加水印

1. 首页 → **进入水印工作台**（或快捷：文字 / Logo / 多层）
2. 选择图片/视频（Photo Picker，无需冷启动 READ_MEDIA 权限）
3. 编辑水印层（Layers 面板可切换/删除多层）；文字层可设置**内容、字体（无衬线/衬线/等宽/粗体）、颜色、字号（sp）、不透明度**；导出与预览共用 [`TextWatermarkRenderer`](app/src/main/java/com/watermarkstudio/util/TextWatermarkRenderer.kt) + [`WatermarkOutlinedText`](app/src/main/java/com/watermarkstudio/ui/components/WatermarkTextCompose.kt)（主流样式：高不透明度亮色填充 + 细黑描边，默认白字 88% 不透明）；TEXT 坐标为**媒体内容区**左上角百分比（[`WatermarkContentGeometry`](app/src/main/java/com/watermarkstudio/ui/components/WatermarkContentGeometry.kt) 对齐 `ContentScale.Fit` 留白，与导出像素一致）；视频文字叠加使用 Media3 **左上角锚点**（与图片 `drawOnCanvas` 一致）；相册写入统一经 [`MediaStoreSaveHelper`](app/src/main/java/com/watermarkstudio/util/MediaStoreSaveHelper.kt)；批量导出单项失败时支持 **PARTIAL** 结果；**在预览图上拖动**水印/消除区域定位（替代纯滑块）

**稳定性（第二轮）**：订阅查询空列表不再覆盖本地 Pro；[`RemovalNative`](app/src/main/java/com/watermarkstudio/removal/native/RemovalNative.kt) 安全加载 + Kotlin 时序中值回退；[`MediaCodecStreamDecoder.open`](app/src/main/java/com/watermarkstudio/removal/video/MediaCodecStreamDecoder.kt) 构造失败释放 extractor；视频帧 YUV 直转 RGB（无 JPEG 往返）。
4. **执行批量转换并导出** → 写入系统相册；成功后在非 Pro 环境可能展示插屏广告
5. **媒体库** Tab 查看本会话及历史导出（`ProcessedMediaLibrary` 持久化 MediaStore URI；文件保存在 `Pictures/WatermarkStudio` 与 `Movies/WatermarkStudio`）

### 去水印（免费额度，无需订阅）

1. 首页 → **消除水印** → **进入去水印工作台**
2. **视频预览**：首帧 + [`RemovalPreviewHelper`](app/src/main/java/com/watermarkstudio/removal/preview/RemovalPreviewHelper.kt)（720px、`InpaintTarget.VIDEO`、与导出同调参）；笔刷 `brushRadiusPct` 为**媒体短边**的 0.5–8%（默认 2.5%）；笔画/粗细变化后约 400ms debounce 再跑预览
3. 支持**图片与视频**（模拟器/低内存设备视频会提示 `error_remove_video_not_supported`）
4. 预览笔迹与导出 mask 使用同一 `WatermarkConfig.removalStrokes` 百分比坐标
5. 导出成功后扣减每日免费次数（`res/values/integers.xml`）；失败不扣减
6. 免费用户受每日导出次数、分辨率与算法档位限制；Pro 更高分辨率并默认 **ADVANCED** 算法

### 订阅

- 购买 / 恢复购买 / **管理订阅**（跳转 Play 订阅管理页）
- **价格与商品名称**：由 Play Billing `queryProductDetailsAsync` 拉取（`SubscriptionDisplayHelper`），不在应用内写死货币金额；SKU 仅保留在 `BillingProducts.kt`
- 商品未配置时 Toast 提示 `billing_product_unavailable`；价格区显示 `plan_price_unavailable`

## 去水印算法（阶段四：笔刷 Mask + Native PatchMatch）

质量档位由 [`RemovalQuality`](app/src/main/java/com/watermarkstudio/removal/RemovalQuality.kt) 决定：**Pro → ADVANCED**，免费 → **STANDARD**。

| 媒体 | STANDARD（免费） | ADVANCED（Pro） |
|------|------------------|-----------------|
| **图片** | 笔刷 mask → [`MaskedBackgroundPropagator`](app/src/main/java/com/watermarkstudio/removal/MaskedBackgroundPropagator.kt) 半透明预处理 → native PatchMatch（不可用时 OpenCV NS 回退） | 同路径，更多 PatchMatch 迭代 |
| **视频** | 7 帧时序中值 prefill + PatchMatch refine → H.264 + 原声 | 时序融合 + 光流 prefill + PatchMatch refine + 原声 |
| **预览** | 720px + `PreviewScaleContext` 与导出参数对齐 | 同左 |

- Kotlin 入口：[`RemovalPipeline`](app/src/main/java/com/watermarkstudio/removal/RemovalPipeline.kt) → [`VideoRemovalEngine`](app/src/main/java/com/watermarkstudio/removal/video/VideoRemovalEngine.kt)
- Mask：[`MaskGenerator`](app/src/main/java/com/watermarkstudio/removal/mask/MaskGenerator.kt) 将 `WatermarkConfig.removalStrokes` 栅格化为 8-bit 像素级 mask；[`RemovalRegion`](app/src/main/java/com/watermarkstudio/util/RemovalRegion.kt) 仅按笔画包围盒裁剪 PatchMatch；[`RemovalInputValidator`](app/src/main/java/com/watermarkstudio/removal/RemovalInputValidator.kt) 在引擎层与导出前校验 strokes 非空
- Native：`app/src/main/cpp/`（`removal_native`，时序中值 + 多尺度 PatchMatch）
- 依赖：`org.opencv:opencv:4.12.0`（BSD，无 contrib/DIS；**4.12+** 含 Google Play **16 KB** 页面对齐的 `libopencv_java4.so`）
- Pro 导出失败时：ADVANCED 视频 mux 失败会回退 **无音频** slideshow 编码

## 去水印阶段三（稳定性 + Media3 音轨复用）

| 项 | 实现 |
|----|------|
| 时长硬封顶 | 免费 **15s** / Pro **300s（5min）** |
| 帧率 | 免费：15s 内 **60fps 全长**（900 帧）；Pro：**永不因时长降帧**，保持源 fps（≤60） |
| 帧数 | 免费最多 **900** 帧；Pro `maxFrames = 时长×fps`（流式逐帧，内存恒定） |
| MediaCodec YUV | [`VideoFrameUtils.imageYuv420888ToBitmap`](app/src/main/java/com/watermarkstudio/removal/video/VideoFrameUtils.kt) 按 stride 转换 |
| 音视频对齐 | 音频截断至 `frameCount × frameDuration` |
| 导出回退链 | `VideoExportMuxer` → Media3 / FFmpeg remux → 无音频 slideshow |

## 去水印阶段 3b（流式 + 增强光流 + 预览）

| 项 | 实现 |
|----|------|
| 流式管线 | [`StreamingVideoRemovalEngine`](app/src/main/java/com/watermarkstudio/removal/video/StreamingVideoRemovalEngine.kt)：MediaCodec（ADVANCED）或 Retriever 逐帧 → 邻帧处理 → [`IncrementalVideoEncoder`](app/src/main/java/com/watermarkstudio/removal/video/IncrementalVideoEncoder.kt)，内存仅保留 prev/curr/next |
| 流式视频修复 | [`TemporalPrefillProcessor`](app/src/main/java/com/watermarkstudio/removal/video/TemporalPrefillProcessor.kt) 7 帧窗口 → [`FrameInpaintBlender.refineFrame`](app/src/main/java/com/watermarkstudio/removal/video/FrameInpaintBlender.kt) PatchMatch refine |
| Pro 原声 | 流式无声 MP4 → [`RemovalVideoRemuxer`](app/src/main/java/com/watermarkstudio/removal/video/RemovalVideoRemuxer.kt) |
| 低分预览 | [`RemovalPreviewHelper`](app/src/main/java/com/watermarkstudio/removal/preview/RemovalPreviewHelper.kt) 图片去水印预览（Editor） |
| 批处理回退 | 流式失败时回退阶段二全帧缓冲路径 |

## 去水印阶段 3c（MediaCodec 流式源 + FFmpeg remux）

| 项 | 实现 |
|----|------|
| MediaCodec 流式解码 | [`MediaCodecVideoFrameSource`](app/src/main/java/com/watermarkstudio/removal/video/MediaCodecVideoFrameSource.kt) + [`MediaCodecStreamDecoder`](app/src/main/java/com/watermarkstudio/removal/video/MediaCodecStreamDecoder.kt)；批处理与流式共用解码会话 |
| 帧源选择 | Exynos：**仅** FFmpeg 分批抽帧（失败即失败）；其它设备：Retriever。编码：[`VideoAvcCodecSelector`](app/src/main/java/com/watermarkstudio/removal/video/VideoAvcCodecSelector.kt) 避开 Samsung HW AVC |
| 原声导出链 | Media3 [`RemovalVideoRemuxer`](app/src/main/java/com/watermarkstudio/removal/video/RemovalVideoRemuxer.kt)（**主线程** 调用 Transformer）→ [`FfmpegRemuxHelper`](app/src/main/java/com/watermarkstudio/removal/video/FfmpegRemuxHelper.kt) → 无声拷贝 |
| 导出稳定性 | 编码前 [`VideoFrameUtils.prepareForVideoEncode`](app/src/main/java/com/watermarkstudio/removal/video/VideoFrameUtils.kt)（ARGB_8888 + 偶数宽高）；OpenCV 逐帧 `Throwable` 兜底 |
| 依赖 | `com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1`（**LGPL 3.0**，增大 APK；用于 exotic 音轨/容器） |

**仍未实现**：opencv-contrib DIS、AI 大区域语义修复模型。

**P3–P5（质量）**：边界/内部 alpha 反混合、Laplacian 传播、native **NNF 上采样**、[`RemovalInpaintTuning`](app/src/main/java/com/watermarkstudio/removal/RemovalInpaintTuning.kt) 按 ROI 自适应 margin/迭代/羽化；详见 [`removal/readme.md`](app/src/main/java/com/watermarkstudio/removal/readme.md)。

### 真机 QA 矩阵（去水印）

| 场景 | STANDARD | ADVANCED |
|------|----------|----------|
| 静态角标图片 | 笔刷 mask + PatchMatch | 笔刷 mask + 更多 PatchMatch 迭代 |
| 轻微平移背景视频 | 时序中值 + PatchMatch | 光流 prefill + PatchMatch + 原声 |
| 运动剧烈视频 | 可能残留 | 光流回退中值 + PatchMatch |
| 无音轨视频 | 仅视频轨 | 仅视频轨 |
| 模拟器 | 视频禁用提示 | 同左 |

## Google Play 法律文档

| 文件 | 说明 |
|------|------|
| `docs/privacy-policy.html` | 隐私政策（中英） |
| `docs/terms-of-service.html` | 服务条款（中英） |
| `docs/readme.md` | GitHub Pages 部署 |

## 真机 QA 矩阵（发布前）

| 场景 | 非 Pro | Pro |
|------|--------|-----|
| 添加水印全流程 | 额度、720p/1024 | 1080/2500 |
| 去水印图片 | 笔刷 mask + PatchMatch、扣额度 | 更多 PatchMatch 迭代 |
| 去水印视频 | mask 中值 + PatchMatch + 原声 | 光流 + PatchMatch + 保留原声（真机） |
| 去水印 + 模拟器 | 视频提示不支持 | 同左 |
| 额度耗尽 | 升级对话框 | — |
| 恢复购买 | 成功/无订阅/失败 Toast | — |
| 冷启动 | WorkManager Startup 已禁用；无 WorkDatabase/Emoji 崩溃 | — |
| Release 包 | 各屏无混淆崩溃 | — |
