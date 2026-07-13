# 端侧去水印模型（内置 assets）

- **文件**：`lama_fp32.onnx`（Carve/LaMa-ONNX，约 198MB）
- **来源**：https://huggingface.co/Carve/LaMa-ONNX
- **用途**：Pro / ADVANCED 图片 ROI inpaint；推理在设备本地完成，不调用云端 API
- **输入**：`image` `[1,3,512,512]` float32 ∈ [0,1]；`mask` `[1,1,512,512]` float32 ∈ {0,1}
- **交付方式 B**：打进 APK/AAB assets；运行时拷贝到私有目录再由 ONNX Runtime 加载

若本地缺失该文件，可执行：

```powershell
curl.exe -L -o app/src/main/assets/ml/lama_fp32.onnx "https://hf-mirror.com/Carve/LaMa-ONNX/resolve/main/lama_fp32.onnx"
```

或使用 Gradle 任务 `:app:downloadOnDeviceInpaintModel`。
