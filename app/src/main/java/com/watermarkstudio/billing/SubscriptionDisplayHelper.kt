package com.watermarkstudio.billing

import com.android.billingclient.api.ProductDetails

/**
 * Maps [ProductDetails] from Play Billing into UI-ready subscription rows.
 * Prices and product titles come from Play Console — not from local currency strings.
 */
object SubscriptionDisplayHelper {

    data class PeriodLabels(
        val week: String,
        val month: String,
        val year: String,
    )

    data class PlanMarketingCopy(
        val planId: String,
        val fallbackTitle: String,
        val fallbackDescription: String,
        val tag: String,
        val isPopular: Boolean = false,
    )

    fun resolveTitle(details: ProductDetails?, fallbackTitle: String): String {
        val fromPlay = details?.name?.trim().orEmpty()
        return fromPlay.ifBlank { fallbackTitle }
    }

    fun resolveDescription(details: ProductDetails?, fallbackDescription: String): String {
        val fromPlay = details?.description?.trim().orEmpty()
        return fromPlay.ifBlank { fallbackDescription }
    }

    /**
     * e.g. "$6.99 / month" using [ProductDetails.SubscriptionOfferDetails.pricingPhases].
     */
    fun resolvePriceLine(
        details: ProductDetails?,
        periodLabels: PeriodLabels,
    ): String? {
        if (details == null) return null
        val phase = details.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull()
            ?: return null
        val periodLabel = labelForBillingPeriod(phase.billingPeriod, periodLabels)
        return "${phase.formattedPrice} / $periodLabel"
    }

    fun labelForBillingPeriod(billingPeriod: String?, labels: PeriodLabels): String {
        return when {
            billingPeriod.equals("P1W", ignoreCase = true) -> labels.week
            billingPeriod.equals("P1Y", ignoreCase = true) -> labels.year
            billingPeriod.equals("P1M", ignoreCase = true) -> labels.month
            billingPeriod?.contains('W', ignoreCase = true) == true -> labels.week
            billingPeriod?.contains('Y', ignoreCase = true) == true -> labels.year
            else -> labels.month
        }
    }

    fun offerTokenForPurchase(details: ProductDetails): String? {
        return details.subscriptionOfferDetails?.firstOrNull()?.offerToken
    }

    fun buildPlanRows(
        productDetailsList: List<ProductDetails>,
        marketingCopies: List<PlanMarketingCopy>,
        periodLabels: PeriodLabels,
    ): List<SubscriptionPlanRow> {
        return marketingCopies.map { copy ->
            val details = productDetailsList.find { it.productId == BillingProducts.planIdToProductId(copy.planId) }
            SubscriptionPlanRow(
                planId = copy.planId,
                productId = BillingProducts.planIdToProductId(copy.planId),
                title = resolveTitle(details, copy.fallbackTitle),
                description = resolveDescription(details, copy.fallbackDescription),
                priceText = resolvePriceLine(details, periodLabels),
                tag = copy.tag,
                isPopular = copy.isPopular,
                isPurchasable = details != null && !offerTokenForPurchase(details).isNullOrBlank(),
            )
        }
    }
}

data class SubscriptionPlanRow(
    val planId: String,
    val productId: String,
    val title: String,
    val description: String,
    val priceText: String?,
    val tag: String,
    val isPopular: Boolean,
    val isPurchasable: Boolean,
)
