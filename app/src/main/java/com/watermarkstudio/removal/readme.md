# 去水印修复管线（Kotlin + Native）

## 流程

1. 用户笔刷 → `WatermarkConfig.removalStrokes`（半径按**媒体短边**百分比）
2. `MaskGenerator` / `BrushStrokeGeometry` 栅格化为 8-bit mask（255 = 待修复）
3. **`MaskedBackgroundPropagator`**：8×8 网格局部 watermark tint + 边界/内部 alpha 反混合 + Laplacian 传播
4. `PatchMatchInpainter` 按 `RemovalRegion` 裁剪 ROI + context margin，调用 native `patchMatchInpaint`（返回状态码；源像素不足时 ROI 级 NS fallback）
5. 分辨率自适应羽化混合贴回原图

## 预览与导出对齐

- 预览最大边长：`RemovalExportLimits.PREVIEW_MAX_DIM`（720）
- `PreviewScaleContext` 按 `(preview/export)²` 缩放「大区域迭代」阈值，使预览与导出触发相同调参逻辑
- 图片预览/导出均使用 `InpaintTarget.IMAGE`；视频预览使用 `InpaintTarget.VIDEO`

## 质量档位

| 档位 | 图片 | 视频 |
|------|------|------|
| STANDARD | PatchMatch + 更多迭代（大区域加成） | 7 帧时序中值 prefill + 轻量 PatchMatch refine |
| ADVANCED | 更高 PatchMatch 迭代 | 时序融合 + 光流 prefill + PatchMatch refine |

## 相关文件

| 文件 | 职责 |
|------|------|
| `mask/BrushStrokeGeometry.kt` | 笔刷直径（短边 %） |
| `mask/MaskGenerator.kt` | 笔刷 Path → mask Mat |
| `MaskedBackgroundPropagator.kt` | 半透明预处理 |
| `PatchMatchInpainter.kt` | 裁剪、native、ROI NS 回退、羽化 |
| `RemovalInpaintTuning.kt` | 自适应 margin / 迭代 / 羽化 |
| `PreviewScaleContext.kt` | 预览尺度阈值缩放 |
| `preview/RemovalPreviewHelper.kt` | 编辑器内预览 |
| `native/RemovalNative.kt` + `cpp/patchmatch_inpaint.cpp` | PatchMatch 多尺度 |

视频路径见 [`video/readme.md`](video/readme.md)。
