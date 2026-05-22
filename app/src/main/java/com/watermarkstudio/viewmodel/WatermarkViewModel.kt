package com.watermarkstudio.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watermarkstudio.model.MediaItem
import com.watermarkstudio.model.MediaType
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.model.WatermarkType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import android.util.Log
import kotlinx.coroutines.isActive

data class UiState(
    val selectedMedia: List<MediaItem> = emptyList(),
    val watermarkConfigs: List<WatermarkConfig> = emptyList(),
    val isProcessing: Boolean = false,
    val processingProgress: Float = 0f,
    val processedMediaUris: List<Uri> = emptyList(),
    val errorMessage: String? = null,
    val maxVideoDurationSec: Int = 15,
    val isPremium: Boolean = false,
    val freeExportsUsedToday: Int = 0,
    val maxFreeExportsPerDay: Int = 3
)

class WatermarkViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private var billingManager: com.watermarkstudio.util.BillingManager? = null

    val billingProducts: StateFlow<List<com.android.billingclient.api.ProductDetails>>
        get() = billingManager?.products ?: MutableStateFlow(emptyList())

    val purchaseSuccessEvent: StateFlow<Boolean>
        get() = billingManager?.purchaseCompletedEvent ?: MutableStateFlow(false)

    val purchaseFlowFinishedEvent: StateFlow<Boolean>
        get() = billingManager?.purchaseFlowFinishedEvent ?: MutableStateFlow(false)

    fun initializeBilling(context: Context) {
        if (billingManager == null) {
            billingManager = com.watermarkstudio.util.BillingManager(context.applicationContext, viewModelScope) { isPremium ->
                setPremium(context.applicationContext, isPremium)
            }
        }
    }

    fun makePurchase(activity: android.app.Activity, planId: String): Boolean {
        val googleProductId = when (planId) {
            "weekly" -> "com.watermark.pro.weekly"
            "monthly" -> "com.watermark.pro.monthly"
            "yearly" -> "com.watermark.pro.yearly"
            else -> "com.watermark.pro.monthly"
        }
        return billingManager?.launchBillingFlow(activity, googleProductId) ?: false
    }

    fun resetPurchaseEvent() {
        billingManager?.resetPurchaseCompletedEvent()
    }

    fun resetPurchaseFlowFinishedEvent() {
        billingManager?.resetPurchaseFlowFinishedEvent()
    }

    fun restorePurchases() {
        billingManager?.queryPurchases()
    }

    fun setPremium(context: Context, status: Boolean) {
        _uiState.value = _uiState.value.copy(isPremium = status)
        try {
            val prefs = context.getSharedPreferences("watermark_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_premium", status).apply()
        } catch (e: Exception) {
            Log.e("WatermarkVM", "Failed to save premium status", e)
        }
    }

    fun checkAndResetDailyLimit(context: Context) {
        try {
            val prefs = context.getSharedPreferences("watermark_prefs", Context.MODE_PRIVATE)
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val todayStr = sdf.format(java.util.Date())
            val lastRecordedDate = prefs.getString("free_export_date", "")
            
            if (lastRecordedDate != todayStr) {
                prefs.edit()
                    .putString("free_export_date", todayStr)
                    .putInt("free_export_count", 0)
                    .apply()
                _uiState.value = _uiState.value.copy(freeExportsUsedToday = 0)
            } else {
                val count = prefs.getInt("free_export_count", 0)
                _uiState.value = _uiState.value.copy(freeExportsUsedToday = count)
            }
        } catch (e: Exception) {
            Log.e("WatermarkVM", "Error checking/resetting daily limits", e)
        }
    }

    fun consumeFreeExport(context: Context): Boolean {
        if (_uiState.value.isPremium) return true
        
        checkAndResetDailyLimit(context)
        val currentUsed = _uiState.value.freeExportsUsedToday
        if (currentUsed >= _uiState.value.maxFreeExportsPerDay) {
            return false
        }
        
        try {
            val prefs = context.getSharedPreferences("watermark_prefs", Context.MODE_PRIVATE)
            val nextUsed = currentUsed + 1
            prefs.edit().putInt("free_export_count", nextUsed).apply()
            _uiState.value = _uiState.value.copy(freeExportsUsedToday = nextUsed)
            return true
        } catch (e: Exception) {
            Log.e("WatermarkVM", "Failed to consume export limit", e)
            return false // fallback: block export on error to prevent abuse
        }
    }

    fun checkPremium(context: Context) {
        initializeBilling(context)
        checkAndResetDailyLimit(context)
        try {
            val prefs = context.getSharedPreferences("watermark_prefs", Context.MODE_PRIVATE)
            val status = prefs.getBoolean("is_premium", false)
            _uiState.value = _uiState.value.copy(isPremium = status)
            billingManager?.queryPurchases()
        } catch (e: Exception) {
            Log.e("WatermarkVM", "Failed to load premium status", e)
        }
    }

    fun setMaxVideoDuration(seconds: Int) {
        _uiState.value = _uiState.value.copy(maxVideoDurationSec = seconds)
    }

    fun addMedia(items: List<MediaItem>) {
        _uiState.value = _uiState.value.copy(
            selectedMedia = _uiState.value.selectedMedia + items
        )
    }

    fun addMediaUris(context: Context, uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val items = uris.map { uri ->
                val type = if (context.contentResolver.getType(uri)?.contains("video") == true) {
                    MediaType.VIDEO
                } else {
                    MediaType.IMAGE
                }

                var duration = 0L
                var size = 0L
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (sizeIndex != -1 && cursor.moveToFirst()) {
                            size = cursor.getLong(sizeIndex)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WatermarkVM", "Error querying size", e)
                }

                if (type == MediaType.VIDEO) {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        duration = durationStr?.toLong() ?: 0L
                    } catch (e: Exception) {
                        Log.e("WatermarkVM", "Error extracting duration", e)
                    } finally {
                        try {
                            retriever.release()
                        } catch (resEx: Exception) {
                            Log.e("WatermarkVM", "Error releasing retriever", resEx)
                        }
                    }
                }

                MediaItem(
                    uri = uri,
                    type = type,
                    name = uri.lastPathSegment ?: (if (type == MediaType.VIDEO) "video.mp4" else "image.jpg"),
                    size = size,
                    duration = duration
                )
            }

            _uiState.value = _uiState.value.copy(
                selectedMedia = _uiState.value.selectedMedia + items
            )
        }
    }

    fun removeMedia(item: MediaItem) {
        _uiState.value = _uiState.value.copy(
            selectedMedia = _uiState.value.selectedMedia - item
        )
    }

    fun addWatermark(config: WatermarkConfig) {
        _uiState.value = _uiState.value.copy(
            watermarkConfigs = _uiState.value.watermarkConfigs + config
        )
    }

    fun updateWatermark(index: Int, config: WatermarkConfig) {
        val updated = _uiState.value.watermarkConfigs.toMutableList()
        if (index in updated.indices) {
            updated[index] = config
            _uiState.value = _uiState.value.copy(watermarkConfigs = updated)
        }
    }

    fun removeWatermark(index: Int) {
        val updated = _uiState.value.watermarkConfigs.toMutableList()
        if (index in updated.indices) {
            updated.removeAt(index)
            _uiState.value = _uiState.value.copy(watermarkConfigs = updated)
        }
    }

    fun clearWatermarks() {
        _uiState.value = _uiState.value.copy(watermarkConfigs = emptyList())
    }

    fun processAll(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, processingProgress = 0f, errorMessage = null)
            val items = _uiState.value.selectedMedia
            val configs = _uiState.value.watermarkConfigs
            val processed = mutableListOf<Uri>()

            try {
                items.forEachIndexed { index, item ->
                    if (!isActive) return@launch
                    
                    try {
                        if (item.type == MediaType.IMAGE) {
                            com.watermarkstudio.util.MediaProcessor.processImage(context, item.uri, configs, _uiState.value.isPremium)?.let {
                                processed.add(it)
                            }
                        } else {
                            val maxDurMs = if (_uiState.value.maxVideoDurationSec > 0) {
                                _uiState.value.maxVideoDurationSec * 1000L
                            } else {
                                0L
                            }
                            com.watermarkstudio.util.MediaProcessor.processVideo(context, item.uri, configs, maxDurMs, _uiState.value.isPremium)?.let {
                                processed.add(it)
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e("WatermarkVM", "Error processing item: ${item.name}", t)
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            errorMessage = t.localizedMessage ?: "Failed to process ${item.name}",
                            processingProgress = (index + 1).toFloat() / items.size
                        )
                        return@launch
                    }

                    _uiState.value = _uiState.value.copy(processingProgress = (index + 1).toFloat() / items.size)
                }

                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    processingProgress = 1f,
                    processedMediaUris = (_uiState.value.processedMediaUris + processed).takeLast(20)
                )
            } catch (t: Throwable) {
                Log.e("WatermarkVM", "Batch processing failed", t)
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    errorMessage = "Processing failed: ${t.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }
}
