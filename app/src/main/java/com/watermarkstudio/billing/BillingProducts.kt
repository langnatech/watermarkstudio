package com.watermarkstudio.billing

object BillingProducts {
    const val WEEKLY = "com.watermark.pro.weekly"
    const val MONTHLY = "com.watermark.pro.monthly"
    const val YEARLY = "com.watermark.pro.yearly"

    val ALL = listOf(WEEKLY, MONTHLY, YEARLY)

    fun planIdToProductId(planId: String): String = when (planId) {
        "weekly" -> WEEKLY
        "monthly" -> MONTHLY
        "yearly" -> YEARLY
        else -> MONTHLY
    }
}
