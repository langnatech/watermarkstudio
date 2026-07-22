# 去水印修复管线（Kotlin + Native）

## 流程

1. 用户笔刷 → `WatermarkConfig.removalStrokes`（半径按**媒体短边**百分比）
2. `MaskGenerator` / `BrushStrokeGeometry` 栅格化为 **软 mask**（0–255；AA + 轻 dilate + 模糊；inpaint 核阈值 40）
3. **`MaskedBackgroundPropagator`**：8×8 网格 watermark tint（**双线性插值**）+ 边界/内部 alpha 反混合 + Laplacian 传播
4. `PatchMatchInpainter`：Pro/ADVANCED **图片**优先端侧 LaMa（`assets/ml/lama_fp32.onnx`）；失败或 STANDARD/视频/预览走 native PatchMatch（多尺度；源不足时 ROI 级 NS fallback）
5. **均值-方差色度对齐**后贴回：mask 不贴 crop 边界时优先 **OpenCV `seamlessClone`（Poisson）**，否则软 alpha 羽化

## 预览与导出对齐

- 预览最大边长：`RemovalExportLimits.PREVIEW_MAX_DIM`（720）
- `PreviewScaleContext` 按 `(preview/export)²` 缩放「大区域迭代」阈值，使预览与导出触发相同调参逻辑
- 图片预览/导出均使用 `InpaintTarget.IMAGE`；视频预览使用 `InpaintTarget.VIDEO`

## 质量档位（代码真值）

| 档位 | 图片 | 视频 |
|------|------|------|
| STANDARD | PatchMatch（大区域加成迭代） | 7 帧 ROI 时序中值 prefill + 轻量 PatchMatch |
| ADVANCED | 更高 PatchMatch 迭代 | **中值 + 光流融合** prefill + 略重 PatchMatch |

说明：ADVANCED **不是**「7 帧融合」；流式路径通过 **一帧延迟** 传入 `lookahead`；prefill 为中值与光流在 mask 核内按通道平均。

## 色块痕迹：根因与优化方案（已落地）

对齐主流去水印（半透明反混合 → 时序/邻域填洞 → PatchMatch/纹理合成 → 软边贴回）时，本仓库主要「色块」来源与对策：

| 优先级 | 根因 | 主流做法 | 本仓库改动 |
|--------|------|----------|------------|
| P0 | 金字塔 `upsampleIntoMasked` **最近邻**拷贝 | 双线性/双三次上采样 | C++ 改为双线性采样粗层 |
| P0 | 8×8 tint **硬切 cell** | 网格双线性插值 | `LocalTintGrid.tintAt` 双线性 |
| P1 | 光流在 curr 坐标上误用 prev→curr 位移 + 整数采样 | 反向 warp + 双线性 | Farneback(curr→neighbor) + 双线性取色 |
| P1 | 流式 ADVANCED 无 next 帧 | 双向时序 | `StreamingVideoRemovalEngine` 一帧延迟传 `lookahead` |
| P2 | 羽化偏小，接缝仍硬 | 略增 soft mask | `FEATHER_MIN` / scale 略增 |

明确不在上轮做的（过度设计）：深度学习 inpaint、全局单例 Banner 式架构、整帧非 ROI 修复。

### 验证建议

- 半透明 logo / 大块水印：修复区应无明显马赛克格与 8×8 色带
- 视频运动背景（ADVANCED）：边缘闪烁与色块应低于仅 prev 光流
- 回归：`MaskedBackgroundPropagatorTest`、`RemovalInpaintTuningTest`、`OpticalFlowRecoveryProcessorTest`

## 「完美」效果：预期边界与下一阶段路线图

**结论：当前经典管线无法保证对所有水印都「完美不可辨」**（重复平铺、强对比不透明 logo、大面积遮挡纹理、剧烈运动/模糊）。商用水印擦除产品多依赖深度学习；本应用定位是**本地区域修复**，应把目标定为：半透明/角标类常见水印「肉眼难察接缝与色块」，而不是 100% 还原被遮挡像素。

### 已完成（降低色块）

双线性金字塔上采样、tint 双线性、反向光流+双线性、流式 lookahead、羽化略增。

### 下一阶段（按收益 / 成本）

| 阶段 | 优化项 | 作用 | 代价 |
|------|--------|------|------|
| **A · 经典算法榨干（已落地）** | ① soft mask ② 贴回均值-方差对齐 ③ ADVANCED 中值+光流融合 ④ ADVANCED 提高 patch/迭代 + 金字塔底边 128 ⑤ 时序 **向量中值** | 边缘糊块、色偏、通道伪色、纹理合成不足 | 中：CPU 略升 |
| **B · 交互与 mask 质量（已落地）** | ① 画笔/橡皮 ② 边缘收缩 ③ 导出后可再修 ④ **智能选区**（加选/减选 flood-fill） | 用户 mask 更准 → 效果跃升往往大于算法 | 低～中：UI |
| **B+ · 贴回接缝（已落地）** | 均值-方差后优先 `seamlessClone`，失败回退羽化 | 减轻修复块与周围的硬色差接缝 | 低：OpenCV 已有 |
| **C · 端侧离线 ML（已落地 · 交付 B）** | **仅手机本地推理**（ONNX Runtime + LaMa assets）；Pro/ADVANCED 图片导出；失败回退 PatchMatch | 接近主流「完美感」且隐私/离网可用 | 高：APK +~198MB；视频/预览默认关闭 |

**推荐落地顺序**：A / B / B+ / C（端侧离线，交付 B 内置）已完成主路径。

### 阶段 C 方案（离网端侧，禁止在线推理 API）

**硬约束**

1. **推理全程离网**：不去调用云端 inpaint / Generative API。
2. **不占位**：模型缺失、加载失败、推理异常 → 直接回退 `PatchMatchInpainter` 经典路径。
3. **交付 B**：`lama_fp32.onnx` 置于 `assets/ml/`（约 198MB，`aapt` 不压缩 `.onnx`）；首次使用拷贝到 `filesDir/ml/` 再加载。

**已实现**

| 项 | 实现 |
|----|------|
| 模型 | Carve/LaMa-ONNX `lama_fp32.onnx`（Apache-2.0 系原版 LaMa 转换） |
| 运行时 | `onnxruntime-android` **≥1.22.0**（`libonnxruntime4j_jni.so` 16 KB 对齐；勿用 1.20.x） |
| 配置 | `res/values/ondevice_inpaint.xml` → `OnDeviceInpaintConfig` |
| 推理 | `OnDeviceLamaInpainter`：ROI crop → 512×512 → `image`/`mask` → 贴回 `pasteRepaired` |
| 门控 | 仅 `RemovalQuality.ADVANCED` + `InpaintTarget.IMAGE`；预览/视频默认 `false` |
| 入口 | `PatchMatchInpainter.inpaint(..., context)`；`ImageRemovalEngine` 传入 Context |
| 拉取 | Gradle `:app:downloadOnDeviceInpaintModel`（`preBuild` 依赖；可用 `-PondeviceInpaintModelUrl=`） |

**管线**

```
MaskGenerator
  → [ADVANCED 图片且模型就绪] OnDeviceLamaInpainter
       成功 → pasteRepaired（均值-方差 + seamlessClone/羽化）
       失败 → PatchMatch / NS（现有路径）
  → [STANDARD / 视频 / 预览] PatchMatch（现有路径）
```

**未默认开启（可在 `ondevice_inpaint.xml` 打开）**

- `ondevice_inpaint_enable_for_video`：逐帧 ML 成本过高
- `ondevice_inpaint_enable_for_preview`：编辑器预览保持轻量

**合规**

- 权重不进 Git（`.gitignore` 忽略 `*.onnx`），构建前由下载任务写入 assets。
- `firebase-ai` 保持注释，**不作为去水印路径**。

### 阶段 B 实现要点

1. **`RemovalStroke.isEraser`**：按笔画顺序绘制；橡皮用黑色覆盖已涂区域。
2. **`RemovalBrushTool`**：`PAINT` / `ERASER` / `SMART_SELECT`；`maskErodePx` 做整体边缘收缩。
3. **`RemovalSmartSelect`**：点击预览种子像素，颜色容差 flood-fill，横条行程转为涂抹笔画；同次填充共享 `batchId`，撤销一次清整批；取色使用**原图 base preview**（非 inpaint 结果）；容差可调；`smartSelectSubtract` 时生成橡皮笔画（智能减选）。
4. **`RemovalBrushOverlay` / `RemovalBrushControls`**：三工具切换；智能模式为加选/减选 + 容差滑条；橡皮预览暖色。
5. **`RemovalInputValidator`**：至少一笔非橡皮有效涂抹才可导出。

### 阶段 B+ 实现要点

1. **`PatchMatchInpainter.pasteRepaired`**：色度对齐后，若 mask 核距 crop 边界 ≥2px 且面积足够，调用 `Photo.seamlessClone(NORMAL_CLONE)`；边缘软区仍按 alpha 与底图混合；任一条件不满足或 OpenCV 失败则回退软羽化。

### 阶段 A 实现要点

1. **`MaskGenerator`**：保留抗锯齿描边；去掉 `THRESH_BINARY`+硬 dilate；改为轻微灰度 dilate + GaussianBlur，输出 0–255 软 mask。
2. **`PatchMatchInpainter`**：传播/PatchMatch/NS 使用 `>=40` 的硬核；贴回前均值-方差对齐（见 B+）。
3. **`TemporalPrefillProcessor` ADVANCED**：先中值再光流，mask 内按通道平均融合（同源则跳过）。
4. **`RemovalInpaintTuning`**：图片 ADVANCED `patchSize=9`、`pmIterations=8`；视频 ADVANCED `pmIterations=6`；native `kMaxPyramidBase=128`。
5. **`TemporalVectorMedian`**：ROI 时序填充选用窗口内真实样本（向量中值），避免 R/G/B 分通道中值产生假色块。

### 场景期望（务实）

| 水印类型 | 可达效果 |
|----------|----------|
| 半透明角标 / 淡 logo | 高：反混合+PatchMatch 优势场景 |
| 不透明小 logo、纹理背景充足 | 中高：依赖 mask 紧、迭代够 |
| 平铺满屏水印、文字压在复杂纹理上 | 中低：经典法易留痕迹 |
| 视频强运动 + 大遮挡 | 中：光流失败时回退中值/单帧 |

## 相关文件

| 文件 | 职责 |
|------|------|
| `mask/BrushStrokeGeometry.kt` | 笔刷直径（短边 %） |
| `mask/MaskGenerator.kt` | 笔刷 Path → soft mask（含橡皮/收缩） |
| `mask/RemovalSmartSelect.kt` | 智能选区 flood-fill → 笔画 |
| `MaskedBackgroundPropagator.kt` | 半透明预处理 |
| `PatchMatchInpainter.kt` | 裁剪、native、ROI NS 回退、羽化；ADVANCED 优先端侧 LaMa |
| `ml/OnDeviceLamaInpainter.kt` | assets LaMa ONNX 离线推理 |
| `ml/OnDeviceInpaintConfig.kt` | 端侧 ML 开关与张量/路径配置 |
| `RemovalInpaintTuning.kt` | 自适应 margin / 迭代 / 羽化 |
| `PreviewScaleContext.kt` | 预览尺度阈值缩放 |
| `preview/RemovalPreviewHelper.kt` | 编辑器内预览 |
| `native/RemovalNative.kt` + `cpp/patchmatch_inpaint.cpp` | PatchMatch 多尺度 |

视频路径见 [`video/readme.md`](video/readme.md)。
