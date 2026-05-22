package com.watermarkstudio.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BillingManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onPremiumStatusChanged: (Boolean) -> Unit
) : PurchasesUpdatedListener {

    private val TAG = "BillingManager"

    private lateinit var billingClient: BillingClient

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()

    private val _purchaseCompletedEvent = MutableStateFlow<Boolean>(false)
    val purchaseCompletedEvent: StateFlow<Boolean> = _purchaseCompletedEvent.asStateFlow()

    private val _purchaseFlowFinishedEvent = MutableStateFlow<Boolean>(false)
    val purchaseFlowFinishedEvent: StateFlow<Boolean> = _purchaseFlowFinishedEvent.asStateFlow()

    // Map plan internal IDs to actual external Google Play product IDs
    val productIds = listOf(
        "com.watermark.pro.weekly",
        "com.watermark.pro.monthly",
        "com.watermark.pro.yearly"
    )

    init {
        initializeBillingClient()
    }

    private fun initializeBillingClient() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()
        
        connectToPlayStore()
    }

    fun connectToPlayStore() {
        if (billingClient.connectionState == BillingClient.ConnectionState.CONNECTED) {
            _isConnected.value = true
            return
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing Setup Succeeded.")
                    _isConnected.value = true
                    // Query active subscriptions to restore state
                    queryPurchases()
                    // Query product details
                    queryProductDetails()
                } else {
                    Log.e(TAG, "Billing Setup Failed. Response Code: ${billingResult.responseCode}, Message: ${billingResult.debugMessage}")
                    _isConnected.value = false
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing Service Disconnected. Retrying setup...")
                _isConnected.value = false
                scope.launch {
                    delay(3000)
                    connectToPlayStore()
                }
            }
        })
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        _purchaseFlowFinishedEvent.value = true
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User canceled billing flow.")
        } else {
            Log.e(TAG, "Purchases update failed: Err Code ${billingResult.responseCode}, Msg: ${billingResult.debugMessage}")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Check if purchase is acknowledged. If not, acknowledge it to avoid automatic Google refund
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "Purchase Acknowledged successfully.")
                        scope.launch(Dispatchers.Main) {
                            onPremiumStatusChanged(true)
                            _purchaseCompletedEvent.value = true
                        }
                    } else {
                        Log.e(TAG, "Error acknowledging purchase: ${billingResult.debugMessage}")
                    }
                }
            } else {
                Log.d(TAG, "Purchase already acknowledged previously.")
                scope.launch(Dispatchers.Main) {
                    onPremiumStatusChanged(true)
                }
            }
        }
    }

    fun resetPurchaseCompletedEvent() {
        _purchaseCompletedEvent.value = false
    }

    fun resetPurchaseFlowFinishedEvent() {
        _purchaseFlowFinishedEvent.value = false
    }

    fun queryPurchases() {
        if (!billingClient.isReady) {
            Log.w(TAG, "queryPurchases: BillingClient is not ready.")
            return
        }

        // Query Active Subscriptions
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                var hasActiveSubscription = false
                for (purchase in purchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        hasActiveSubscription = true
                        handlePurchase(purchase)
                    }
                }
                if (!hasActiveSubscription) {
                    Log.d(TAG, "No active subscriptions found.")
                    onPremiumStatusChanged(false)
                }
            } else {
                Log.e(TAG, "Failed querying purchases: ${billingResult.debugMessage}")
            }
        }
    }

    private fun queryProductDetails() {
        if (!billingClient.isReady) return

        val productList = productIds.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Product Details Query Completed: ${productDetailsList.size} items found.")
                _products.value = productDetailsList
            } else {
                Log.e(TAG, "Failed querying product details: ${billingResult.debugMessage}")
            }
        }
    }

    fun launchBillingFlow(activity: Activity, productId: String): Boolean {
        if (!billingClient.isReady) {
            connectToPlayStore()
            Log.w(TAG, "BillingClient is not ready. Attemping connection restart.")
            return false
        }

        val productDetails = _products.value.find { it.productId == productId }
        if (productDetails == null) {
            Log.e(TAG, "No ProductDetails found for $productId. Cannot launch purchase.")
            simulatePurchaseFallback(productId)
            return false
        }

        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: ""
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)
        Log.d(TAG, "Launched real billing flow for $productId. Response Code: ${billingResult.responseCode}")
        return billingResult.responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun simulatePurchaseFallback(productId: String) {
        Log.w(TAG, "ProductDetails not found for $productId. Cannot launch purchase flow.")
        scope.launch(Dispatchers.Main) {
            _purchaseCompletedEvent.value = false
        }
    }
}
