package com.watermarkstudio.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watermarkstudio.R
import com.watermarkstudio.billing.BillingProducts
import com.watermarkstudio.util.BillingUiEvent
import com.watermarkstudio.util.RestorePurchaseResult
import com.watermarkstudio.model.ExportStatus
import com.watermarkstudio.model.MediaItem
import com.watermarkstudio.model.MediaType
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.model.WatermarkType
import com.watermarkstudio.removal.RemovalInputValidator
import com.watermarkstudio.util.ProcessedMediaLibrary
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
    val maxFreeExportsPerDay: Int = 3,
    /** Non-zero after a successful batch export; UI handles once then clears via [WatermarkViewModel.clearExportSuccessEvent]. */
    val exportSuccessBatchId: Long = 0L,
    val exportStatus: ExportStatus = ExportStatus.IDLE,
    val exportStatusMessage: String? = null,
    val processingItemIndex: Int = 0,
    val processingItemTotal: Int = 0,
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

    val billingProductsQueryComplete: StateFlow<Boolean>
        get() = billingManager?.productsQueryComplete ?: MutableStateFlow(false)

    fun refreshSubscriptionProducts() {
        billingManager?.refreshProductDetails()
    }

    val purchaseSuccessEvent: StateFlow<Boolean>
        get() = billingManager?.purchaseCompletedEvent ?: MutableStateFlow(false)

    val purchaseFlowFinishedEvent: StateFlow<Boolean>
        get() = billingManager?.purchaseFlowFinishedEvent ?: MutableStateFlow(false)

    val billingUiEvent: StateFlow<BillingUiEvent?>
        get() = billingManager?.billingUiEvent ?: MutableStateFlow(null)

    val restorePurchaseResult: StateFlow<RestorePurchaseResult?>
        get() = billingManager?.restorePurchaseResult ?: MutableStateFlow(null)

    fun initializeBilling(context: Context) {
        if (billingManager == null) {
            billingManager = com.watermarkstudio.util.BillingManager(context.applicationContext, viewModelScope) { isPremium ->
                setPremium(context.applicationContext, isPremium)
            }
        }
    }

    fun makePurchase(activity: android.app.Activity, planId: String): Boolean {
        return billingManager?.launchBillingFlow(activity, BillingProducts.planIdToProductId(planId)) ?: false
    }

    fun clearBillingUiEvent() {
        billingManager?.clearBillingUiEvent()
    }

    fun clearRestorePurchaseResult() {
        billingManager?.clearRestorePurchaseResult()
    }

    fun resetPurchaseEvent() {
        billingManager?.resetPurchaseCompletedEvent()
    }

    fun resetPurchaseFlowFinishedEvent() {
        billingManager?.resetPurchaseFlowFinishedEvent()
    }

    fun restorePurchases() {
        billingManager?.queryPurchases(forRestore = true)
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

    fun refreshMaxFreeExports(context: Context) {
        val max = context.resources.getInteger(R.integer.free_exports_per_day)
        _uiState.value = _uiState.value.copy(maxFreeExportsPerDay = max)
    }

    /** Returns whether a free user may start another export (does not consume quota). */
    fun canStartExport(context: Context): Boolean {
        if (_uiState.value.isPremium) return true
        checkAndResetDailyLimit(context)
        refreshMaxFreeExports(context)
        return _uiState.value.freeExportsUsedToday < _uiState.value.maxFreeExportsPerDay
    }

    /** Consumes one daily export after a successful batch; call only from [processAll]. */
    private fun commitFreeExport(context: Context): Boolean {
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
            Log.e("WatermarkVM", "Failed to commit export limit", e)
            return false
        }
    }

    fun clearExportSuccessEvent() {
        _uiState.value = _uiState.value.copy(exportSuccessBatchId = 0L)
    }

    fun clearExportStatus() {
        _uiState.value =
            _uiState.value.copy(
                exportStatus = ExportStatus.IDLE,
                exportStatusMessage = null,
            )
    }

    fun checkPremium(context: Context) {
        initializeBilling(context)
        refreshMaxFreeExports(context)
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

    private var pendingImageWatermarkFocus = false

    fun setPendingImageWatermarkFocus(enabled: Boolean) {
        pendingImageWatermarkFocus = enabled
    }

    fun consumePendingImageWatermarkFocus(): Boolean {
        val pending = pendingImageWatermarkFocus
        pendingImageWatermarkFocus = false
        return pending
    }

    /** Resets editor media/watermarks when entering add or remove workflow. */
    fun resetEditorSession(mode: WatermarkType) {
        val defaultConfig =
            if (mode == WatermarkType.REMOVE) {
                WatermarkConfig(
                    type = WatermarkType.REMOVE,
                    opacity = 1f,
                    brushRadiusPct = WatermarkConfig.DEFAULT_BRUSH_RADIUS_PCT,
                )
            } else {
                WatermarkConfig(WatermarkType.TEXT, text = "")
            }
        _uiState.value = _uiState.value.copy(
            selectedMedia = emptyList(),
            watermarkConfigs = listOf(defaultConfig),
            isProcessing = false,
            processingProgress = 0f,
            errorMessage = null,
            maxVideoDurationSec = 15,
            exportSuccessBatchId = 0L,
            exportStatus = ExportStatus.IDLE,
            exportStatusMessage = null,
            processingItemIndex = 0,
            processingItemTotal = 0,
        )
    }

    private var pendingMultilayer = false

    fun setPendingMultilayer(enabled: Boolean) {
        pendingMultilayer = enabled
    }

    fun consumePendingMultilayer(): Boolean {
        val pending = pendingMultilayer
        pendingMultilayer = false
        return pending
    }

    private var pendingLibraryTab = false

    fun setPendingLibraryTab(enabled: Boolean = true) {
        pendingLibraryTab = enabled
    }

    fun consumePendingLibraryTab(): Boolean {
        val pending = pendingLibraryTab
        pendingLibraryTab = false
        return pending
    }

    /** Loads persisted export URIs (survives app restart). Call from Home / after export. */
    fun refreshProcessedLibrary(context: Context) {
        val uris = ProcessedMediaLibrary.load(context)
        _uiState.value = _uiState.value.copy(processedMediaUris = uris)
    }

    private fun resolveMediaType(context: Context, uri: Uri): MediaType {
        val mime = context.contentResolver.getType(uri)
        if (mime != null) {
            if (mime.startsWith("video/")) return MediaType.VIDEO
            if (mime.startsWith("image/")) return MediaType.IMAGE
        }

        val path = uri.lastPathSegment?.lowercase().orEmpty()
        val videoExtensions = listOf(".mp4", ".mkv", ".webm", ".mov", ".3gp", ".avi")
        if (videoExtensions.any { path.endsWith(it) }) {
            return MediaType.VIDEO
        }

        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val retrieverMime = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE,
            )
            when {
                retrieverMime?.startsWith("video/") == true -> MediaType.VIDEO
                retrieverMime?.startsWith("image/") == true -> MediaType.IMAGE
                else -> {
                    Log.w("WatermarkVM", "Unknown MIME for $uri, defaulting to IMAGE")
                    MediaType.IMAGE
                }
            }
        } catch (e: Exception) {
            Log.w("WatermarkVM", "Could not detect media type for $uri, defaulting to IMAGE", e)
            MediaType.IMAGE
        } finally {
            try {
                retriever.release()
            } catch (releaseEx: Exception) {
                Log.e("WatermarkVM", "Error releasing retriever", releaseEx)
            }
        }
    }

    fun addMedia(items: List<MediaItem>) {
        _uiState.value = _uiState.value.copy(
            selectedMedia = _uiState.value.selectedMedia + items
        )
    }

    fun addMediaUris(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val items = uris.map { uri ->
                val type = resolveMediaType(context, uri)

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
                        val durationStr = retriever.extractMetadata(
                            android.media.MediaMetadataRetriever.METADATA_KEY_DURATION,
                        )
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
                    duration = duration,
                )
            }

            _uiState.value = _uiState.value.copy(
                selectedMedia = _uiState.value.selectedMedia + items,
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
            val items = _uiState.value.selectedMedia
            val configs = _uiState.value.watermarkConfigs
            _uiState.value =
                _uiState.value.copy(
                    isProcessing = true,
                    processingProgress = 0f,
                    errorMessage = null,
                    exportStatus = ExportStatus.PROCESSING,
                    exportStatusMessage = null,
                    processingItemIndex = 0,
                    processingItemTotal = items.size,
                    exportSuccessBatchId = 0L,
                )
            if (items.isEmpty()) {
                _uiState.value =
                    _uiState.value.copy(
                        isProcessing = false,
                        exportStatus = ExportStatus.FAILED,
                        exportStatusMessage = context.getString(R.string.export_error_no_media),
                        errorMessage = context.getString(R.string.export_error_no_media),
                    )
                return@launch
            }
            if (configs.isEmpty()) {
                _uiState.value =
                    _uiState.value.copy(
                        isProcessing = false,
                        exportStatus = ExportStatus.FAILED,
                        exportStatusMessage = context.getString(R.string.export_error_no_config),
                        errorMessage = context.getString(R.string.export_error_no_config),
                    )
                return@launch
            }
            val removeOnly = configs.all { it.type == WatermarkType.REMOVE }
            val removeConfig = configs.firstOrNull { it.type == WatermarkType.REMOVE }
            if (removeOnly && removeConfig != null && !RemovalInputValidator.hasPaintedMask(removeConfig)) {
                val message = context.getString(R.string.export_error_no_removal_strokes)
                _uiState.value =
                    _uiState.value.copy(
                        isProcessing = false,
                        exportStatus = ExportStatus.FAILED,
                        exportStatusMessage = message,
                        errorMessage = message,
                    )
                return@launch
            }
            val exportConfigs = watermarkConfigsForExport(configs)
            val ignoredRemoveLayers = !removeOnly && exportConfigs.size < configs.size

            val processed = mutableListOf<Uri>()
            var failedCount = 0

            try {
                items.forEachIndexed { index, item ->
                    if (!isActive) return@launch

                    _uiState.value =
                        _uiState.value.copy(
                            processingItemIndex = index + 1,
                            processingItemTotal = items.size,
                        )

                    try {
                        val maxDurMs =
                            if (_uiState.value.maxVideoDurationSec > 0) {
                                _uiState.value.maxVideoDurationSec * 1000L
                            } else {
                                0L
                            }
                        val outputUri =
                            if (removeOnly && removeConfig != null) {
                                if (item.type == MediaType.VIDEO &&
                                    !com.watermarkstudio.removal.RemovalCapability.supportsVideoRemoval(context)
                                ) {
                                    null
                                } else {
                                val itemBase = index.toFloat() / items.size
                                val itemSpan = 1f / items.size.coerceAtLeast(1)
                                com.watermarkstudio.removal.RemovalPipeline.processItem(
                                    context,
                                    item,
                                    removeConfig,
                                    maxDurMs,
                                    _uiState.value.isPremium,
                                    progress =
                                        com.watermarkstudio.removal.RemovalProgress { sub ->
                                            val progressValue =
                                                (itemBase + itemSpan * sub.coerceIn(0f, 1f))
                                                    .coerceIn(0f, 1f)
                                            viewModelScope.launch(Dispatchers.Main.immediate) {
                                                _uiState.value =
                                                    _uiState.value.copy(
                                                        processingProgress = progressValue,
                                                    )
                                            }
                                        },
                                )
                                }
                            } else if (item.type == MediaType.IMAGE) {
                                com.watermarkstudio.util.MediaProcessor.processImage(
                                    context,
                                    item.uri,
                                    exportConfigs,
                                    _uiState.value.isPremium,
                                )
                            } else {
                                com.watermarkstudio.util.MediaProcessor.processVideo(
                                    context,
                                    item.uri,
                                    exportConfigs,
                                    maxDurMs,
                                    _uiState.value.isPremium,
                                )
                            }
                        if (outputUri != null) {
                            processed.add(outputUri)
                        } else {
                            failedCount++
                            Log.e("WatermarkVM", "Processing returned null for ${item.name}")
                        }
                    } catch (t: Throwable) {
                        failedCount++
                        Log.e("WatermarkVM", "Error processing item: ${item.name}", t)
                    }

                    _uiState.value = _uiState.value.copy(processingProgress = (index + 1).toFloat() / items.size)
                }

                val (exportStatus, baseStatusMessage, baseErrorMessage) =
                    when {
                        processed.isEmpty() ->
                            Triple(
                                ExportStatus.FAILED,
                                context.getString(R.string.export_status_failed_desc),
                                context.getString(R.string.export_status_failed_desc),
                            )
                        failedCount > 0 ->
                            Triple(
                                ExportStatus.PARTIAL,
                                context.getString(
                                    R.string.export_status_partial_desc,
                                    processed.size,
                                    items.size,
                                ),
                                context.getString(
                                    R.string.export_status_partial_desc,
                                    processed.size,
                                    items.size,
                                ),
                            )
                        else ->
                            Triple(
                                ExportStatus.SUCCESS,
                                context.getString(
                                    R.string.export_status_success_desc,
                                    processed.size,
                                ),
                                null,
                            )
                    }
                val removeLayersNote =
                    if (ignoredRemoveLayers && processed.isNotEmpty()) {
                        context.getString(R.string.export_warning_remove_layers_ignored)
                    } else {
                        null
                    }
                val statusMessage =
                    if (removeLayersNote != null) {
                        "$baseStatusMessage $removeLayersNote"
                    } else {
                        baseStatusMessage
                    }
                val errorMessage = baseErrorMessage

                val successBatchId =
                    if (exportStatus == ExportStatus.SUCCESS) {
                        if (!_uiState.value.isPremium) {
                            commitFreeExport(context)
                        }
                        System.currentTimeMillis()
                    } else {
                        0L
                    }

                val libraryUris =
                    if (processed.isNotEmpty()) {
                        setPendingLibraryTab(true)
                        ProcessedMediaLibrary.append(context, processed)
                    } else {
                        _uiState.value.processedMediaUris
                    }
                _uiState.value =
                    _uiState.value.copy(
                        isProcessing = false,
                        processingProgress = 1f,
                        processedMediaUris = libraryUris,
                        errorMessage = errorMessage,
                        exportSuccessBatchId = successBatchId,
                        exportStatus = exportStatus,
                        exportStatusMessage = statusMessage,
                    )
            } catch (t: Throwable) {
                Log.e("WatermarkVM", "Batch processing failed", t)
                val msg =
                    context.getString(
                        R.string.export_error_unknown,
                        t.localizedMessage ?: context.getString(R.string.export_error_unknown_fallback),
                    )
                _uiState.value =
                    _uiState.value.copy(
                        isProcessing = false,
                        exportStatus = ExportStatus.FAILED,
                        exportStatusMessage = msg,
                        errorMessage = msg,
                    )
            }
        }
    }

    private fun watermarkConfigsForExport(configs: List<WatermarkConfig>): List<WatermarkConfig> =
        configs.filter { it.type != WatermarkType.REMOVE }
}
