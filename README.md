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
| AdMob 正式 ID | `app/src/release/res/values/admob.xml` | 替换 Google 测试 ID；debug 使用 `app/src/debug/res/values/admob.xml` 测试 ID |
| Play 订阅 SKU | Play Console + `BillingProducts.kt` | `com.watermark.pro.weekly/monthly/yearly` 必须与控制台一致 |
| 隐私政策 / 条款 URL | `values/strings.xml` | 已指向 GitHub Pages；部署见 `docs/readme.md` |
| UMP 同意 | `UmpConsentHelper.kt` | 在 AdMob 初始化前请求；EEA 投放广告前必测 |
| 免费导出次数 | `res/values/integers.xml` `free_exports_per_day` | 默认 3，与添加/去水印共用 |
| ProGuard | `app/proguard-rules.pro` | 已保留 `com.watermarkstudio.*` 模型与 ViewModel |
| 去水印算法说明 | 商店文案 | 本地 OpenCV/时序恢复；Pro 光流+保留原声；勿宣传全 AI 修复 |
| keystore | 勿提交仓库 | `keystore.properties`、`*.jks` 已在 `.gitignore` |

## 核心业务流程

### 添加水印

1. 首页 → **进入水印工作台**（或快捷：文字 / Logo / 多层）
2. 选择图片/视频（Photo Picker，无需冷启动 READ_MEDIA 权限）
3. 编辑水印层（Layers 面板可切换/删除多层）；文字层可设置**内容、字体（无衬线/衬线/等宽/粗体）、颜色、字号（sp）、不透明度**；导出与预览共用 [`TextWatermarkRenderer`](app/src/main/java/com/watermarkstudio/util/TextWatermarkRenderer.kt)（TEXT 坐标为左上角语义，与拖动预览一致）；相册写入统一经 [`MediaStoreSaveHelper`](app/src/main/java/com/watermarkstudio/util/MediaStoreSaveHelper.kt)；批量导出单项失败时支持 **PARTIAL** 结果；**在预览图上拖动**水印/消除区域定位（替代纯滑块）
4. **执行批量转换并导出** → 写入系统相册；成功后在非 Pro 环境可能展示插屏广告
5. **媒体库** Tab 查看已处理 URI

### 去水印（免费额度，无需订阅）

1. 首页 → **消除水印** → **进入去水印工作台**
2. 支持**图片与视频**（模拟器/低内存设备视频会提示 `error_remove_video_not_supported`）
3. 绿色预览框与处理区域使用同一 `RemovalRegion` 比例
4. 导出成功后扣减每日免费次数（`res/values/integers.xml`）；失败不扣减
5. 免费用户受每日导出次数、分辨率与算法档位限制；Pro 更高分辨率并默认 **ADVANCED** 算法

### 订阅

- 购买 / 恢复购买 / **管理订阅**（跳转 Play 订阅管理页）
- 商品未配置时 Toast 提示 `billing_product_unavailable`

## 去水印算法（阶段二：OpenCV + 光流 + 原声保留）

质量档位由 [`RemovalQuality`](app/src/main/java/com/watermarkstudio/removal/RemovalQuality.kt) 决定：**Pro → ADVANCED**，免费 → **STANDARD**。

| 媒体 | STANDARD（免费） | ADVANCED（Pro） |
|------|------------------|-----------------|
| **图片** | `Photo.inpaint` TELEA | NS inpaint + 羽化 mask + `Photo.seamlessClone` |
| **视频** | Retriever 抽帧 → JNI 时序中值 → H.264（**无音频**） | MediaCodec 解码（失败回退 Retriever）→ Farneback 光流填充（失败回退中值）→ 逐帧 NS + seamlessClone → H.264 + **拷贝源 AAC 音轨** |
| **预览** | 与处理共用 [`RemovalRegion`](app/src/main/java/com/watermarkstudio/util/RemovalRegion.kt) | 同左 |

- Kotlin 入口：[`RemovalPipeline`](app/src/main/java/com/watermarkstudio/removal/RemovalPipeline.kt) → [`VideoRemovalEngine`](app/src/main/java/com/watermarkstudio/removal/video/VideoRemovalEngine.kt)
- Native：`app/src/main/cpp/`（`removal_native`，时序中值）
- 依赖：`org.opencv:opencv:4.9.0`（BSD，无 contrib/DIS）
- Pro 导出失败时：ADVANCED 视频 mux 失败会回退 **无音频** slideshow 编码

## 去水印阶段三（稳定性 + Media3 音轨复用）

| 项 | 实现 |
|----|------|
| 时长硬封顶 | [`VideoRemovalLimits`](app/src/main/java/com/watermarkstudio/removal/video/VideoRemovalLimits.kt)：免费 15s / Pro 300s，与加水印一致 |
| 帧数上限 | 最多 480 帧，超长自动降 FPS，降低 OOM |
| MediaCodec YUV | [`VideoFrameUtils.imageYuv420888ToBitmap`](app/src/main/java/com/watermarkstudio/removal/video/VideoFrameUtils.kt) 按 stride 转换 |
| 音视频对齐 | 音频截断至 `frameCount × frameDuration` |
| 导出回退链 | `VideoExportMuxer` → Media3 / FFmpeg remux → 无音频 slideshow |

## 去水印阶段 3b（流式 + 增强光流 + 预览）

| 项 | 实现 |
|----|------|
| 流式管线 | [`StreamingVideoRemovalEngine`](app/src/main/java/com/watermarkstudio/removal/video/StreamingVideoRemovalEngine.kt)：MediaCodec（ADVANCED）或 Retriever 逐帧 → 邻帧处理 → [`IncrementalVideoEncoder`](app/src/main/java/com/watermarkstudio/removal/video/IncrementalVideoEncoder.kt)，内存仅保留 prev/curr/next |
| STANDARD 流式恢复 | [`RoiWindowMedianProcessor`](app/src/main/java/com/watermarkstudio/removal/video/RoiWindowMedianProcessor.kt) 滑动窗口 ROI 中值 |
| ADVANCED 光流 | 标准 Farneback + **轻量 Farneback**（`PYRAMID_LK` 档位，更小金字塔窗口，主库替代 contrib DIS） |
| Pro 原声 | 流式无声 MP4 → [`RemovalVideoRemuxer`](app/src/main/java/com/watermarkstudio/removal/video/RemovalVideoRemuxer.kt) |
| 低分预览 | [`RemovalPreviewHelper`](app/src/main/java/com/watermarkstudio/removal/preview/RemovalPreviewHelper.kt) 图片去水印预览（Editor） |
| 批处理回退 | 流式失败时回退阶段二全帧缓冲路径 |

## 去水印阶段 3c（MediaCodec 流式源 + FFmpeg remux）

| 项 | 实现 |
|----|------|
| MediaCodec 流式解码 | [`MediaCodecVideoFrameSource`](app/src/main/java/com/watermarkstudio/removal/video/MediaCodecVideoFrameSource.kt) + [`MediaCodecStreamDecoder`](app/src/main/java/com/watermarkstudio/removal/video/MediaCodecStreamDecoder.kt)；批处理与流式共用解码会话 |
| 帧源选择 | [`VideoFrameSourceFactory`](app/src/main/java/com/watermarkstudio/removal/video/VideoFrameSourceFactory.kt)：ADVANCED 优先 MediaCodec，失败回退 Retriever |
| 原声导出链 | Media3 [`RemovalVideoRemuxer`](app/src/main/java/com/watermarkstudio/removal/video/RemovalVideoRemuxer.kt) → [`FfmpegRemuxHelper`](app/src/main/java/com/watermarkstudio/removal/video/FfmpegRemuxHelper.kt) → 无声拷贝 |
| 依赖 | `com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1`（**LGPL 3.0**，增大 APK；用于 exotic 音轨/容器） |

**仍未实现**：opencv-contrib DIS、完整 PatchMatch。

### 真机 QA 矩阵（去水印）

| 场景 | STANDARD | ADVANCED |
|------|----------|----------|
| 静态角标图片 | TELEA 修复 | NS + 无缝边缘 |
| 轻微平移背景视频 | 时序中值 | 光流填充 + 原声 |
| 运动剧烈视频 | 可能残留 | 光流失败回退中值 |
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
| 去水印图片 | TELEA、扣额度 | NS + seamlessClone |
| 去水印视频 | 中值、无原声 | 光流 + 保留原声（真机） |
| 去水印 + 模拟器 | 视频提示不支持 | 同左 |
| 额度耗尽 | 升级对话框 | — |
| 恢复购买 | 成功/无订阅/失败 Toast | — |
| 冷启动 | 无 WorkManager/Emoji 崩溃 | — |
| Release 包 | 各屏无混淆崩溃 | — |
