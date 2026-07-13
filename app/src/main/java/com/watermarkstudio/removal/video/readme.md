# 视频去水印 · 帧源 / 时序修复 / 编码稳定性

## 修复语义

用户笔刷 → `WatermarkConfig.removalStrokes`（半径 `brushRadiusPct`，按**媒体短边**百分比）。

### 导出管线（流式主路径）

`StreamingVideoRemovalEngine`（**一帧延迟**，便于 ADVANCED 传入下一帧 `lookahead`）：

1. 取出待处理帧 + 下一帧（末帧 lookahead=null）
2. **`TemporalPrefillProcessor`**（7 帧滑动窗口）
   - **STANDARD**：`RoiWindowMedianProcessor` **向量中值**填充 mask 核像素
   - **ADVANCED**：时序中值 + 光流（prev/next）在 mask 核内通道平均融合；光流失败时仍可用中值分量
3. **`FrameInpaintBlender.refineFrame`**：`PatchMatchInpainter`（`InpaintTarget.VIDEO`）纹理 refine
4. `IncrementalVideoEncoder` → 混音导出

批处理回退（流式失败时）同样接入时序 prefill；内存估算超过 **256MB** 时拒绝 batch。

失败帧超过 `max(3, 5%×已处理帧)` 时中止导出。

色块相关质量优化见上级 [`readme.md`](../readme.md)。

## 帧源（`VideoFrameSourceFactory`）

| 设备 | 行为 |
|------|------|
| **Samsung Exynos** | **仅** `FfmpegVideoFrameSource`；失败不回退 Retriever / 批处理 |
| 其它 | `RetrieverVideoFrameSource` |

## 编码（`VideoAvcCodecSelector`）

Exynos 导出优先非 `exynos` 的 AVC 编码器。

## 真机验收

1. 静态角标视频：闪烁明显减轻（对比逐帧独立 PatchMatch）
2. ADVANCED 运动背景优于 STANDARD（应能用到 next 帧光流）
3. Exynos：FFmpeg 流式路径无 SIGSEGV
4. 大块半透明水印：无明显马赛克色块 / 8×8 色带
