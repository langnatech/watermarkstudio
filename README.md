<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/f621e555-b526-4722-82f5-d29c38bdc37a

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device

## 发布 Release 包（AAB/APK）

Release 构建需要有效的上传密钥。`signReleaseBundle` 失败并出现 `NullPointerException` 时，通常是以下原因之一：

1. **Keystore 文件缺失**：默认路径为项目根目录 `my-upload-key.jks`
2. **密码未配置**：`storePassword` / `keyPassword` 为空时，签名阶段会 NPE

### 配置步骤

1. 若尚无上传密钥，在项目根目录生成（请自行记录密码）。

   Windows 上若提示找不到 `keytool`，请使用 Android Studio 自带的 JDK（不要直接写 `keytool`）：

   ```powershell
   cd D:\watermark-studio
   & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -storetype PKCS12 -keystore my-upload-key.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```

   **注意**：若 `my-upload-key.jks` 已存在，`keytool` 会先要求输入**原密钥库密码**才能继续；输错会报 `keystore password was incorrect`。此时不要重复生成，应直接配置 `keystore.properties`。仅当忘记旧密码且应用尚未上架时，可先删除旧文件再重新生成：

   ```powershell
   Remove-Item .\my-upload-key.jks
   # 再执行上面的 keytool -genkeypair 命令
   ```

   若希望当前终端会话里可直接用 `keytool`，可先执行：

   ```powershell
   $env:Path = "C:\Program Files\Android\Android Studio\jbr\bin;" + $env:Path
   ```

2. 复制 `keystore.properties.example` 为 `keystore.properties`，填入真实密码（该文件已加入 `.gitignore`，勿提交）：

   ```properties
   storeFile=my-upload-key.jks
   storePassword=你的密钥库密码
   keyAlias=upload
   keyPassword=你的密钥密码
   ```

   也可使用环境变量：`STORE_PASSWORD`、`KEY_PASSWORD`（可选 `KEY_ALIAS`、`KEYSTORE_PATH`）。

3. 构建 Release Bundle：

   ```powershell
   .\gradlew.bat bundleRelease
   ```

   输出路径：`app/build/outputs/bundle/release/app-release.aab`

配置不完整时，构建会在任务图就绪阶段报错并列出缺失项，而不是在 `signReleaseBundle` 阶段抛出无信息的 NPE。

## Google Play 隐私权政策 URL（中英双语 · GitHub Pages）

上架时 Play 控制台要求填写**可公开访问的 HTTPS 隐私政策链接**（本应用含广告与订阅，必须提供）。

| 文件 | 说明 |
|------|------|
| `docs/privacy-policy.html` | 中英双语隐私政策（章节左右对照） |
| `docs/terms-of-service.html` | 中英双语服务条款 |
| `docs/readme.md` | **GitHub Pages 部署步骤**（Settings → Pages → `/docs`） |

部署后 Play 控制台 URL 示例：

`https://<GitHub用户名>.github.io/<仓库名>/privacy-policy.html`

同步更新 `app/src/main/res/values/strings.xml` 中的 `privacy_policy_url`、`terms_of_service_url`。发布前将 HTML 内联系邮箱改为真实支持邮箱。
