# 去水印修复管线（Kotlin + Native）

## 流程

1. 用户笔刷 → `WatermarkConfig.removalStrokes`
2. `MaskGenerator` 栅格化为 8-bit mask（255 = 待修复）
3. **`MaskedBackgroundPropagator`**（P3/P4）：mask 边界 alpha 反混合 + 内部 Laplacian 传播，削弱半透明水印混合色，再交给 PatchMatch
4. `PatchMatchInpainter` 按 `RemovalRegion` 裁剪 ROI + context margin，调用 native `patchMatchInpaint`；失败或未加载 `.so` 时用 OpenCV **INPAINT_NS**（半径按 mask 面积估算）
5. 羽化混合贴回原图

## 半透明水印（P3）

常见叠加：`observed = (1 − α)·background + α·watermark`。

若直接把混合像素交给 PatchMatch，会把水印色调当作待匹配纹理，导致有色/半透明角标去不干净。

`MaskedBackgroundPropagator`：

- **P4 边界反混合**：从 mask 边界估计水印色调，对 `observed = (1−α)·bg + α·wm` 做反解（仅边界像素）
- **P3 Laplacian 传播**：从边界向内部 Gauss-Seidel 平均（4 邻域，双向扫描）
- 迭代次数 = `depth × 4 + 1`（上限 64）
- 仅改写 mask 内 RGB

## Native PatchMatch（P4 NNF 上采样）

`patchmatch_inpaint.cpp` 多尺度金字塔：

- 粗层完成后将 **NNF（Match 场）** 按分辨率比例上采样到细层，作为 `initializeMatches` 的种子
- 细层继续 PatchMatch 迭代 + vote，比仅上采样颜色收敛更快

## 质量档位与自适应调参（P5）

`RemovalInpaintTuning.resolve` 按 **ROI 尺寸 / mask 像素数** 推导：

| 参数 | 规则 |
|------|------|
| `contextMarginPx` | `max(regionW, regionH) × 比例`（图片 50%，视频 35%），夹在 48–160 / 32–128 |
| `pmIterations` | 图片大区域（≥80k ROI 或 ≥12k mask 像素）+2 次 PM、+1 次 EM |
| `featherRadiusPx` | `√(region 面积) × 1.2%`，3–8px |

视频路径不增加迭代，避免导出过慢。

## 半透明内部反混合（P5）

边界估计水印色调后，Laplacian 传播得到 **局部背景估计**，再对 `depth > 0` 的 mask 内部像素做 alpha 反混合（渐变背景场景）。

## 质量档位

基线迭代见 `RemovalInpaintTuning`：`RemovalQuality` × `InpaintTarget`（图片多于视频）。

## 相关文件

| 文件 | 职责 |
|------|------|
| `mask/MaskGenerator.kt` | 笔刷 Path → mask Mat |
| `MaskedBackgroundPropagator.kt` | 半透明预处理 |
| `PatchMatchInpainter.kt` | 裁剪、native 调用、TELEA/NS 回退、羽化 |
| `RemovalInputValidator.kt` | strokes 非空校验 |
| `native/RemovalNative.kt` + `cpp/patchmatch_inpaint.cpp` | PatchMatch 多尺度 |

视频路径见 [`video/readme.md`](video/readme.md)。
