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
import com.watermarkstudio.billing.BillingProducts

enum class RestorePurchaseResult {
    SUCCESS,
    NONE,
    ERROR,
}

sealed class BillingUiEvent {
    data class ProductUnavailable(val productId: String) : BillingUiEvent()
    data object BillingNotReady : BillingUiEvent()
    data class QueryFailed(val message: String) : BillingUiEvent()
}

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

    val productIds: List<String> = BillingProducts.ALL

    private val _billingUiEvent = MutableStateFlow<BillingUiEvent?>(null)
    val billingUiEvent: StateFlow<BillingUiEvent?> = _billingUiEvent.asStateFlow()

    private val _restorePurchaseResult = MutableStateFlow<RestorePurchaseResult?>(null)
    val restorePurchaseResult: StateFlow<RestorePurchaseResult?> = _restorePurchaseResult.asStateFlow()

    private var restoreQueryInFlight = false

    fun clearBillingUiEvent() {
        _billingUiEvent.value = null
    }

    fun clearRestorePurchaseResult() {
        _restorePurchaseResult.value = null
    }

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

    fun queryPurchases(forRestore: Boolean = false) {
        if (!billingClient.isReady) {
            Log.w(TAG, "queryPurchases: BillingClient is not ready.")
            if (forRestore) {
                _restorePurchaseResult.value = RestorePurchaseResult.ERROR
            }
            return
        }
        restoreQueryInFlight = forRestore

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
                    if (restoreQueryInFlight) {
                        _restorePurchaseResult.value = RestorePurchaseResult.NONE
                        restoreQueryInFlight = false
                    }
                } else if (restoreQueryInFlight) {
                    _restorePurchaseResult.value = RestorePurchaseResult.SUCCESS
                    restoreQueryInFlight = false
                }
            } else {
                Log.e(TAG, "Failed querying purchases: ${billingResult.debugMessage}")
                if (restoreQueryInFlight) {
                    _restorePurchaseResult.value = RestorePurchaseResult.ERROR
                    restoreQueryInFlight = false
                }
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
            _billingUiEvent.value = BillingUiEvent.BillingNotReady
            return false
        }

        val productDetails = _products.value.find { it.productId == productId }
        if (productDetails == null) {
            Log.e(TAG, "No ProductDetails found for $productId. Cannot launch purchase.")
            reportProductUnavailable(productId)
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

    private fun reportProductUnavailable(productId: String) {
        Log.w(TAG, "ProductDetails not found for $productId. Configure SKU in Play Console.")
        scope.launch(Dispatchers.Main) {
            _purchaseCompletedEvent.value = false
            _billingUiEvent.value = BillingUiEvent.ProductUnavailable(productId)
        }
    }
}
