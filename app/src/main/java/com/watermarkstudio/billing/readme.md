# Play 订阅（Billing）

## PBL 9 迁移（当前）

- 依赖：`billing-ktx` **9.1.0**（`gradle/libs.versions.toml`）
- `enablePendingPurchases(PendingPurchasesParams)` 替代无参 API
- `queryProductDetailsAsync` 回调使用 `QueryProductDetailsResult`（`productDetailsList` + `unfetchedProductList`）
- `enableAutoServiceReconnection()` 启用断线自动重连
- `BillingResult.onPurchasesUpdatedSubResponseCode`：余额不足 / 优惠无资格等子码映射到 `BillingUiEvent`

参考：[迁移到 PBL 9](https://developer.android.com/google/play/billing/migrate-gpblv9?hl=zh-cn)

## 职责划分

| 来源 | 内容 |
|------|------|
| **Play Console** | 商品 ID、基础方案价格、`ProductDetails.name` / `description`、`formattedPrice`、`billingPeriod` |
| **`BillingProducts.kt`** | 仅 SKU 常量与 `planId` 映射（`weekly` / `monthly` / `yearly`） |
| **`strings.xml`** | 营销文案 fallback（标题/描述/角标）；**不含**货币金额 |
| **`SubscriptionDisplayHelper`** | 将 `ProductDetails` 转为 UI 行；价格行格式：`{formattedPrice} / {周期}` |

## 数据流

1. `BillingManager` 连接 Play → `queryProductDetailsAsync(BillingProducts.ALL)`
2. `SubscriptionScreen` 进入时 `refreshSubscriptionProducts()`
3. `SubscriptionDisplayHelper.buildPlanRows()` 合并 Play 数据与本地营销 fallback
4. 购买：`launchBillingFlow` + `offerToken`（来自 Play 返回的 `subscriptionOfferDetails`）

## 全球定价

价格在 Console 按国家配置；应用只展示 Play 返回的 `formattedPrice`，勿在代码或 strings 中写死 `$` / `¥`。

## 自检 Logcat

`BillingManager`: `Product Details Query Completed: 3 items found`  
每条 SKU 会打印 `name`, `price`, `period`。
