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

### 4. 填写 Play 控制台与应用

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

- 去水印（阶段二）：
  - **STANDARD**：图片 TELEA；视频时序中值 + 无音频导出。
  - **ADVANCED（Pro）**：图片 NS + seamlessClone；视频 MediaCodec 解码 → Farneback 光流（失败回退中值）→ 逐帧融合 → **保留原 AAC 音轨**。
  - 勿在商店文案中写「全 AI 修复」；处理在设备本地完成。
  - **阶段三**：`VideoRemovalLimits` 时长/帧数封顶；ADVANCED 导出失败时用 Media3 `RemovalVideoRemuxer` 合并原声。
- Release AdMob ID：`app/src/release/res/values/admob.xml`。
- Play 订阅 SKU 与 `BillingProducts.kt` 一致；法律页 URL 与本 Pages 部署地址一致。

### 去水印 QA 要点

| 检查项 | 期望 |
|--------|------|
| Pro 视频导出 | `MediaMetadataRetriever` 可读 duration；有音轨时听感正常 |
| 免费视频 | 无原声、带应用角标 |
| 运动背景 | ADVANCED 优于 STANDARD；极端运动可回退中值 |
| 模拟器 | `error_remove_video_not_supported` |
