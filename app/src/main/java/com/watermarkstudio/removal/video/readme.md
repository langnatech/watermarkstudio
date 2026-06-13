# 视频去水印 · 帧源 / 时序修复 / 编码稳定性

## 修复语义

用户笔刷 → `WatermarkConfig.removalStrokes`（半径 `brushRadiusPct`，按**媒体短边**百分比）。

### 导出管线（流式主路径）

`StreamingVideoRemovalEngine`：

1. 解码单帧
2. **`TemporalPrefillProcessor`**（7 帧滑动窗口）
   - **STANDARD**：`RoiWindowMedianProcessor` 时序中值填充 mask 像素
   - **ADVANCED**：`OpticalFlowRecoveryProcessor` 光流 warp 邻帧（失败回退中值）
3. **`FrameInpaintBlender.refineFrame`**：`PatchMatchInpainter`（`InpaintTarget.VIDEO`）纹理 refine
4. `IncrementalVideoEncoder` → 混音导出

批处理回退（流式失败时）同样接入时序 prefill；内存估算超过 **256MB** 时拒绝 batch。

失败帧超过 `max(3, 5%×已处理帧)` 时中止导出。

## 帧源（`VideoFrameSourceFactory`）

| 设备 | 行为 |
|------|------|
| **Samsung Exynos** | **仅** `FfmpegVideoFrameSource`；失败不回退 Retriever / 批处理 |
| 其它 | `RetrieverVideoFrameSource` |

## 编码（`VideoAvcCodecSelector`）

Exynos 导出优先非 `exynos` 的 AVC 编码器。

## 真机验收

1. 静态角标视频：闪烁明显减轻（对比逐帧独立 PatchMatch）
2. ADVANCED 运动背景优于 STANDARD
3. Exynos：FFmpeg 流式路径无 SIGSEGV
