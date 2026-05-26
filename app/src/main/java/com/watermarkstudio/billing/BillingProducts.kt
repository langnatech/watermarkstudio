package com.watermarkstudio.billing

object BillingProducts {
    const val WEEKLY = "com.watermark.pro.weekly"
    const val MONTHLY = "com.watermark.pro.monthly"
    const val YEARLY = "com.watermark.pro.yearly"

    /** Play Console product IDs — identifiers only, not prices. */
    val ALL = listOf(WEEKLY, MONTHLY, YEARLY)

    val PLAN_IDS = listOf("weekly", "monthly", "yearly")

    fun planIdToProductId(planId: String): String = when (planId) {
        "weekly" -> WEEKLY
        "monthly" -> MONTHLY
        "yearly" -> YEARLY
        else -> MONTHLY
    }
}
