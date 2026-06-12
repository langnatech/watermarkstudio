# 视频去水印 · 帧源 / 编码稳定性

## 修复语义

视频去水印不再使用整块矩形 ROI 作为修复区域。编辑器会把用户笔刷记录为
`WatermarkConfig.removalStrokes`（半径 `brushRadiusPct`，按媒体宽度百分比），
`MaskGenerator` 按当前帧分辨率栅格化为 8-bit mask：

- 流式/批处理视频路径跳过光流与中值，直接对 mask 像素做 PatchMatch（静态水印场景更快、更稳）；
- `FrameInpaintBlender` 在整段视频处理期间缓存 mask，对同一 mask 调用 `PatchMatchInpainter`（`InpaintTarget.VIDEO` 使用更少迭代）；
- ROI 仅作为性能裁剪包围盒，不代表整块区域都会被覆盖。

## 问题

三星 Exynos 上 `MediaMetadataRetriever.getFrameAtTime` 与自建 `MediaCodec`+`ImageReader` 会走 `c2.exynos.h264.*`，在 Java 捕获前 **SIGSEGV**。

## 帧源（`VideoFrameSourceFactory`）

| 设备 | 行为 |
|------|------|
| **Samsung Exynos**（`s5e*` / `SOC_MODEL` / `HARDWARE`） | **仅** `FfmpegVideoFrameSource`：导出前将 URI **复制到 cache**（`input_src.mp4`），分批 FFmpeg 抽 PNG（避免 `saf:N.mp4` 句柄过期）；失败 **不回退** Retriever / 批处理 |
| 其它 | `RetrieverVideoFrameSource`；`preferMediaCodec=true` 时才尝试应用内 MediaCodec |

## 编码（`VideoAvcCodecSelector`）

Exynos 导出优先 `c2.android.avc.encoder` / 非 HW 的 AVC 编码器，**跳过** 名称含 `exynos` 的编码器。

用于：`IncrementalVideoEncoder`、`SlideshowVideoEncoder`、`VideoExportMuxer`。

## 其它入口

- **预览**：`VideoFrameExtractor.loadPreviewFrame` → Exynos 走 FFmpeg 单帧。
- **批处理回退**：`VideoFrameExtractor.extract` → 统一 `VideoFrameSourceFactory`。
- **导出入口**：`VideoRemovalEngine` 在 Exynos 且无 FFmpeg 时直接失败。

## 真机验收（≥ 1.0.14 / versionCode 15）

1. logcat：`Opening FfmpegVideoFrameSource`、`VideoAvcCodecSelector: Using AVC encoder: c2.android.avc.encoder`（或同类非 exynos 名）。
2. 导出过程中**不应**再出现 `allocate(c2.exynos.h264.decoder)`（读元数据除外）。
3. 同视频去水印导出无 SIGSEGV。

若仍崩溃：抓 tombstone，区分 `libffmpeg` / `libopencv` / `exynos` encoder。
