# 法律文档 · GitHub Pages 托管

本目录为 **GitHub Pages** 站点根目录（仓库 Settings → Pages → Branch: `main` → Folder: **`/docs`**）。

## 页面一览

| 文件 | 说明 |
|------|------|
| `feature-graphic-1024x500.png` | Play 置顶大图（1024×500，PNG） |
| `feature-graphic-1024x500.jpg` | Play 置顶大图（1024×500，JPEG） |
| `index.html` | 首页导航 |
| `privacy-policy.html` | **中英双语**隐私政策（填入 Play 控制台） |
| `terms-of-service.html` | **中英双语**服务条款（应用内链接） |
| `app-ads.txt` | **AdMob 应用验证**（IAB app-ads.txt，发布者 ID 与 AdMob 控制台一致） |
| `assets/legal.css` | 共享样式（宽屏左右对照中英文） |
| `.nojekyll` | 禁用 Jekyll，避免静态资源被错误处理 |

## 部署到 GitHub Pages

### 1. 创建仓库并推送

在项目根目录（`D:\watermark-studio`）执行：

```powershell
git init
git add .
git reset keystore.properties
git reset -- "*.jks"
git reset .env
git commit -m "Add bilingual legal docs for GitHub Pages"
git branch -M main
git remote add origin https://github.com/<你的用户名>/<仓库名>.git
git push -u origin main
```

> `keystore.properties`、`*.jks`、`.env` 已在 `.gitignore` 中，勿提交。

### 2. 开启 Pages

1. 打开 GitHub 仓库 → **Settings** → **Pages**
2. **Build and deployment** → Source: **Deploy from a branch**
3. Branch: **`main`**，Folder: **`/docs`**
4. 点击 **Save**，等待 1～5 分钟

### 3. 访问地址

```
https://<GitHub用户名>.github.io/<仓库名>/privacy-policy.html
https://<GitHub用户名>.github.io/<仓库名>/terms-of-service.html
```

示例：用户 `liang`，仓库 `watermark-studio`：

```
https://liang.github.io/watermark-studio/privacy-policy.html
```

### 4. AdMob `app-ads.txt` 验证

AdMob 控制台「设置新应用 → 验证应用」会抓取 **Google Play 商店详情里填写的开发者网站域名** 下的 `app-ads.txt`。

本仓库已在 `docs/app-ads.txt` 写入发布者行（与 AdMob 控制台一致）：

```text
google.com, pub-8798068561135489, DIRECT, f08c47fec0942fa0
```

推送到 `main` 并等待 GitHub Pages 更新后，应可通过以下地址访问（**404 则 AdMob 无法验证**）：

```text
https://langnatech.github.io/watermarkstudio/app-ads.txt
```

**域名必须与 Play 一致（常见踩坑）**

| Play 商店「网站」字段示例 | `app-ads.txt` 必须可访问的 URL |
|---------------------------|--------------------------------|
| `https://langnatech.github.io/watermarkstudio` | `https://langnatech.github.io/watermarkstudio/app-ads.txt`（本仓库 `docs/` 部署） |
| 仅填 `https://langnatech.github.io` | `https://langnatech.github.io/app-ads.txt`（需在 **`langnatech.github.io` 用户/组织站点仓库根目录**单独放置，不能只用本项目的 `/docs`） |

建议在 Play 控制台将**网站**设为 `https://langnatech.github.io/watermarkstudio`（与隐私政策同站），部署后浏览器打开上述 `app-ads.txt` 确认非 404，再在 AdMob 点击「检查是否更新」。爬取可能需要数小时。

### 5. 填写 Play 控制台与应用

- **Google Play** → 应用内容 → 隐私权政策 → 填入 `privacy-policy.html` 的完整 URL
- 修改 `app/src/main/res/values/strings.xml`：

```xml
<string name="privacy_policy_url">https://你的用户名.github.io/仓库名/privacy-policy.html</string>
<string name="terms_of_service_url">https://你的用户名.github.io/仓库名/terms-of-service.html</string>
```

## 发布前必改

将 `privacy-policy.html`、`terms-of-service.html` 中的 **`support@watermarkstudio.app`** 改为你真实、可回复的支持邮箱。

## 页面结构说明

- 每个章节标题为 **中英并列**（如 `1. Information We Collect / 我们收集哪些信息`）
- 正文为 **左右双语卡片**（桌面端并排，手机端上下排列）
- 顶部导航可跳转 `#zh` / `#en` 锚点

## 应用生产验收（与根目录 README 同步）

- 去水印（阶段四）：
  - **STANDARD**：笔刷 mask + `MaskedBackgroundPropagator`（边界/内部 alpha 反混合 + Laplacian）+ `RemovalInpaintTuning` 自适应 ROI 参数 + native PatchMatch（NNF 上采样）；视频时序恢复仅写回 mask 像素并保留原声。
  - **ADVANCED（Pro）**：更多 PatchMatch 迭代；视频 MediaCodec 解码 → Farneback 光流（失败回退中值）→ PatchMatch → **保留原 AAC 音轨**。
  - 勿在商店文案中写「全 AI 修复」；处理在设备本地完成。
  - **阶段三**：`VideoRemovalLimits` 时长/帧数封顶；ADVANCED 导出失败时用 Media3 `RemovalVideoRemuxer` 合并原声。
  - **阶段 3b**：视频流式逐帧处理；Pro 光流可选 Pyramid LK；图片区 inpaint 低分预览。
  - **阶段 3c**：ADVANCED 流式优先 MediaCodec 解码；Pro 原声导出 Media3 → FFmpeg-kit（LGPL）回退。
  - **编辑器**：去水印模式使用预览笔刷涂抹 mask（`brushRadiusPct` 为媒体宽度 0.5–8%）；笔画变化后约 400ms debounce 再跑 PatchMatch 预览；[`RemovalRegion`](../app/src/main/java/com/watermarkstudio/util/RemovalRegion.kt) 仅按笔画包围盒裁剪；已移除旧版绿框矩形 ROI；[`RemovalInputValidator`](../app/src/main/java/com/watermarkstudio/removal/RemovalInputValidator.kt) 校验 strokes 非空；导出时全屏进度遮罩 + 结果横幅（成功/部分成功/失败）。
- Release AdMob ID：`app/src/release/res/values/admob.xml`。
- Play 订阅 SKU 与 `BillingProducts.kt` 一致（`com.watermark.pro.weekly/monthly/yearly`）；**展示价与商品名**从 Play 拉取，见 `billing/SubscriptionDisplayHelper.kt`；法律页 URL 与本 Pages 部署地址一致。

### Release 稳定性（2026-05）

| 现象 | 处理 |
|------|------|
| 启动崩溃 `Failed to create WorkDatabase` / `WorkManagerInitializer` | AdMob 会传递依赖 WorkManager；应用显式对齐 `work-runtime-ktx` 到版本目录中的新版，Manifest 禁用启动期 `WorkManagerInitializer`（应用不调度 Worker），`proguard-rules.pro` 保留 WorkManager/Room 数据库类以防库侧懒加载 |
| Release 导出视频 `SIGSEGV` / OpenCV 断言 | Exynos：FFmpeg 软件抽帧 + 本地 `input_src.mp4`；**1.0.16+** 按真实片长封顶帧数，末批 seek 超 EOF 时视为结束（不再 `FFmpeg batch produced no frames`） |

### 去水印 QA 要点

| 检查项 | 期望 |
|--------|------|
| Pro 视频导出 | `MediaMetadataRetriever` 可读 duration；有音轨时听感正常 |
| 免费视频 | 无原声 |
| 运动背景 | ADVANCED 优于 STANDARD；极端运动可回退中值 |
| 模拟器 | `error_remove_video_not_supported` |
| **Release 包** | 选 8s 内 H.264 视频 → 去水印 → 批量导出，无 native 崩溃 |
