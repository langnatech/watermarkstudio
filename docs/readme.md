# 法律文档 · GitHub Pages 托管

本目录为 **GitHub Pages** 站点根目录（仓库 Settings → Pages → Branch: `main` → Folder: **`/docs`**）。

## 页面一览

| 文件 | 说明 |
|------|------|
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
