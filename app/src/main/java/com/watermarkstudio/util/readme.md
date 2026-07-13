# util · AdMob 广告模块

## 展示成功率加固方案（已落地）

### 原则

- 继续 **UMP → 唯一 `initialize` → preload → show**，禁止同意前请求
- 广告位 ID 来自 `debug/release` 的 `admob.xml`，不硬编码
- 不把横幅提升到 Activity 全局
- 导出成功提示优先：插屏等待有上限（2.5s），超时放行业务并补 preload
- 不保证无填充等平台侧问题下的展示

### 插屏状态机

```text
showAd
  → 缓存有效且未过期? → presentAd (ready)
  → 否则丢弃过期缓存并 preload
  → 正在 load / 等 SDK? → 等待最多 2500ms
       → 期内 onAdLoaded → presentAd
       → 超时 → timeout_skip + onAdClosed + preload
  → presentAd → showed 清空 → dismiss/failed_show → preload 下一条
```

### InterstitialAdLoader

| 能力 | 行为 |
|------|------|
| 过期判定 | `loadedAtElapsedRealtime`；超过约 55 分钟视为过期（`expired`），丢弃后 preload |
| 短时等待展示 | 未就绪但正在加载/等 init 时等待最多 `SHOW_WAIT_MS=2500`；日志：`waiting` / `timeout_skip` |
| 快速重试 | load 失败：1s / 3s / 8s，最多 3 次 |
| 耗尽后恢复 | 快速重试耗尽后再隔 **30s** 恢复性 `loadAd` |
| 可观测日志 | `ready` / `expired` / `waiting` / `timeout_skip` / `showed` / `failed_show` |
| 同意安全 | SDK 未就绪只 `whenReady` 排队，不自行 `MobileAds.initialize` |

### AdMobBannerAd

- 等 `AdMobHelper.whenReady` 后再创建 `AdView` 并 load
- `ON_PAUSE` / `ON_RESUME` → `pause` / `resume`
- `AdListener`：失败时同一视图 **最多再 load 1 次**（延迟 2s）；记录 loaded / fail code
- 页面切换导致 `destroy` 仍接受（不提升为全局 Banner）

### 业务接入

- `MainActivity`：UMP 且 `canRequestAds` 后 init + 首次插屏 preload（唯一初始化入口）
- `EditorScreen`：非 Pro 进入页 `preload`；导出成功调用 `showAd`（等待逻辑在 Loader 内）

### 验证清单

- [ ] Debug 测试广告：冷启动 → 进编辑页见 `onAdLoaded` → 导出可见插屏
- [ ] 导出时仍在 loading：2.5s 内 load 完成应能补上展示（日志 `waiting` → `ready`）
- [ ] 无网导出：`timeout_skip`，Snackbar 仍出现；恢复网络后可再 preload
- [ ] 快速重试 3 次失败后约 30s 出现恢复性 load
- [ ] 横幅 init 后才有 AdView；失败日志后可见一次 retry
- [ ] Pro：不走插屏展示

### 预期效果

- 降低「广告刚 load 完、导出瞬间却跳过」的错失展示
- 降低「缓存过期首次 show 必失败」与「重试耗尽后一直空」的窗口
- 仍无法保证无填充时的展示
