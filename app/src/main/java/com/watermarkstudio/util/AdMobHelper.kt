package com.watermarkstudio.util

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdMobHelper {
    private const val TAG = "AdMobHelper"

    private var initialized = false
    private var initInProgress = false
    private val onReadyCallbacks = mutableListOf<() -> Unit>()

    fun initialize(context: Context, onReady: (() -> Unit)? = null) {
        if (initialized) {
            onReady?.invoke()
            return
        }
        onReady?.let { onReadyCallbacks.add(it) }
        if (initInProgress) return

        initInProgress = true
        val appContext = context.applicationContext
        try {
            MobileAds.initialize(appContext) { status ->
                Log.d(TAG, "AdMob initialization state completed: $status")
                initialized = true
                initInProgress = false
                val callbacks = onReadyCallbacks.toList()
                onReadyCallbacks.clear()
                callbacks.forEach { it() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed initializing AdMob", e)
            initInProgress = false
            onReadyCallbacks.clear()
        }
    }

    /**
     * 仅等待初始化完成，不主动触发 [initialize]。
     * 初始化仍由 MainActivity 在 UMP 同意后再调用，避免同意前发广告请求。
     */
    fun whenReady(onReady: () -> Unit): () -> Unit {
        if (initialized) {
            onReady()
            return {}
        }
        onReadyCallbacks.add(onReady)
        return { onReadyCallbacks.remove(onReady) }
    }

    fun isInitialized(): Boolean = initialized
}

@Composable
fun AdMobBannerAd(
    modifier: Modifier = Modifier,
    adUnitId: String? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val resolvedAdUnitId = adUnitId ?: context.getString(com.watermarkstudio.R.string.admob_banner_id)
    var sdkReady by remember { mutableStateOf(AdMobHelper.isInitialized()) }
    val adViewHolder = remember { AdViewHolder() }

    DisposableEffect(Unit) {
        if (AdMobHelper.isInitialized()) {
            sdkReady = true
            onDispose { }
        } else {
            val cancel = AdMobHelper.whenReady { sdkReady = true }
            onDispose { cancel() }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val adView = adViewHolder.adView ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                Lifecycle.Event.ON_RESUME -> adView.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (!sdkReady) {
        Spacer(
            modifier = modifier
                .fillMaxWidth()
                .height(50.dp)
        )
        return
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        factory = { viewContext ->
            AdView(viewContext).apply {
                setAdSize(AdSize.BANNER)
                setAdUnitId(resolvedAdUnitId)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                var retryUsed = false
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        Log.d(BANNER_TAG, "Banner ad loaded.")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.e(
                            BANNER_TAG,
                            "Banner failed to load: code=${error.code} domain=${error.domain} message=${error.message}"
                        )
                        if (retryUsed) return
                        retryUsed = true
                        val retry = Runnable {
                            adViewHolder.retryRunnable = null
                            if (adViewHolder.adView !== this@apply) return@Runnable
                            Log.d(BANNER_TAG, "Banner retrying load once.")
                            loadAd(AdRequest.Builder().build())
                        }
                        adViewHolder.retryRunnable = retry
                        postDelayed(retry, BANNER_RETRY_DELAY_MS)
                    }
                }
                loadAd(AdRequest.Builder().build())
                adViewHolder.adView = this
            }
        },
        onRelease = { adView ->
            adViewHolder.retryRunnable?.let { adView.removeCallbacks(it) }
            adViewHolder.retryRunnable = null
            if (adViewHolder.adView === adView) {
                adViewHolder.adView = null
            }
            adView.destroy()
        }
    )
}

private const val BANNER_TAG = "AdMobBannerAd"
private const val BANNER_RETRY_DELAY_MS = 2_000L

private class AdViewHolder {
    var adView: AdView? = null
    var retryRunnable: Runnable? = null
}

/**
 * 插屏广告：预加载与展示分离。
 * 展示前尽量使用有效缓存；未就绪且正在加载时短时等待，超时放行业务并补 preload。
 */
object InterstitialAdLoader {
    private const val TAG = "InterstitialAdLoader"
    private const val MAX_LOAD_RETRY = 3
    private const val SHOW_WAIT_MS = 2_500L
    private const val AD_EXPIRY_MS = 55L * 60L * 1_000L
    private const val RECOVERY_DELAY_MS = 30_000L

    private val retryDelaysMs = longArrayOf(1_000L, 3_000L, 8_000L)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var interstitialAd: InterstitialAd? = null
    private var loadedAtElapsedRealtime: Long = 0L
    private var isAdLoading = false
    private var waitingForInit = false
    private var loadRetryAttempt = 0
    private var pendingRetry: Runnable? = null

    private var pendingShowActivity: Activity? = null
    private var pendingShowOnClosed: (() -> Unit)? = null
    private var pendingShowTimeout: Runnable? = null

    fun isAdReady(): Boolean {
        if (interstitialAd == null) return false
        return !isExpired()
    }

    fun preload(context: Context) {
        loadAd(context.applicationContext)
    }

    fun loadAd(
        context: Context,
        adUnitId: String = context.getString(com.watermarkstudio.R.string.admob_interstitial_id),
    ) {
        val appContext = context.applicationContext
        discardExpiredCache(appContext, triggerPreload = false)
        if (interstitialAd != null || isAdLoading) return
        if (!AdMobHelper.isInitialized()) {
            // 不在此处 initialize：须等 MainActivity 完成 UMP 后再由官方路径初始化
            if (waitingForInit) return
            waitingForInit = true
            AdMobHelper.whenReady {
                waitingForInit = false
                loadAd(appContext, adUnitId)
            }
            return
        }

        cancelPendingRetry()
        isAdLoading = true
        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            appContext,
            adUnitId,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(
                        TAG,
                        "Interstitial ad failed to load: code=${adError.code} domain=${adError.domain} message=${adError.message}"
                    )
                    interstitialAd = null
                    loadedAtElapsedRealtime = 0L
                    isAdLoading = false
                    scheduleLoadRetry(appContext, adUnitId)
                }

                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial ad fully loaded.")
                    interstitialAd = ad
                    loadedAtElapsedRealtime = SystemClock.elapsedRealtime()
                    isAdLoading = false
                    loadRetryAttempt = 0
                    tryFulfillPendingShow()
                }
            }
        )
    }

    fun showAd(activity: Activity, onAdClosed: () -> Unit) {
        val appContext = activity.applicationContext
        discardExpiredCache(appContext, triggerPreload = true)

        val readyAd = interstitialAd
        if (readyAd != null && !isExpired()) {
            Log.d(TAG, "ready: showing interstitial")
            presentAd(activity, readyAd, onAdClosed)
            return
        }

        if (!isAdLoading && !waitingForInit) {
            preload(appContext)
        }

        // 预加载已启动或仍在等 SDK：短时等待 load 完成再展示
        if (isAdLoading || waitingForInit) {
            Log.d(TAG, "waiting: interstitial not ready, wait up to ${SHOW_WAIT_MS}ms")
            schedulePendingShow(activity, onAdClosed)
            return
        }

        val loadedDuringPreload = interstitialAd
        if (loadedDuringPreload != null && !isExpired()) {
            Log.d(TAG, "ready: showing interstitial after synchronous load path")
            presentAd(activity, loadedDuringPreload, onAdClosed)
            return
        }

        Log.w(TAG, "timeout_skip: interstitial not ready and not loading")
        preload(appContext)
        onAdClosed()
    }

    private fun presentAd(activity: Activity, ad: InterstitialAd, onAdClosed: () -> Unit) {
        clearPendingShow(/* invokeCallback = */ false)
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "showed: interstitial fullscreen content")
                interstitialAd = null
                loadedAtElapsedRealtime = 0L
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial ad dismissed fullscreen.")
                interstitialAd = null
                loadedAtElapsedRealtime = 0L
                preload(activity.applicationContext)
                onAdClosed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(
                    TAG,
                    "failed_show: code=${adError.code} domain=${adError.domain} message=${adError.message}"
                )
                interstitialAd = null
                loadedAtElapsedRealtime = 0L
                preload(activity.applicationContext)
                onAdClosed()
            }
        }
        ad.show(activity)
    }

    private fun schedulePendingShow(activity: Activity, onAdClosed: () -> Unit) {
        val previousClosed = pendingShowOnClosed
        clearPendingShow(invokeCallback = false)
        previousClosed?.invoke()

        pendingShowActivity = activity
        pendingShowOnClosed = onAdClosed
        val timeout = Runnable {
            pendingShowTimeout = null
            val cb = pendingShowOnClosed
            pendingShowActivity = null
            pendingShowOnClosed = null
            Log.w(TAG, "timeout_skip: wait exceeded ${SHOW_WAIT_MS}ms")
            preload(activity.applicationContext)
            cb?.invoke()
        }
        pendingShowTimeout = timeout
        mainHandler.postDelayed(timeout, SHOW_WAIT_MS)
    }

    private fun tryFulfillPendingShow() {
        val activity = pendingShowActivity ?: return
        val ad = interstitialAd ?: return
        if (isExpired()) return
        val onClosed = pendingShowOnClosed ?: return
        clearPendingShow(invokeCallback = false)
        Log.d(TAG, "ready: fulfilling pending show after wait")
        presentAd(activity, ad, onClosed)
    }

    private fun clearPendingShow(invokeCallback: Boolean) {
        pendingShowTimeout?.let { mainHandler.removeCallbacks(it) }
        pendingShowTimeout = null
        val cb = pendingShowOnClosed
        pendingShowOnClosed = null
        pendingShowActivity = null
        if (invokeCallback) {
            cb?.invoke()
        }
    }

    private fun isExpired(): Boolean {
        if (interstitialAd == null) return false
        return SystemClock.elapsedRealtime() - loadedAtElapsedRealtime > AD_EXPIRY_MS
    }

    private fun discardExpiredCache(appContext: Context, triggerPreload: Boolean) {
        if (!isExpired()) return
        Log.w(TAG, "expired: discarding cached interstitial")
        interstitialAd = null
        loadedAtElapsedRealtime = 0L
        if (triggerPreload) {
            preload(appContext)
        }
    }

    private fun scheduleLoadRetry(appContext: Context, adUnitId: String) {
        if (loadRetryAttempt >= MAX_LOAD_RETRY) {
            Log.w(
                TAG,
                "Interstitial load retry exhausted (max=$MAX_LOAD_RETRY); scheduling recovery in ${RECOVERY_DELAY_MS}ms"
            )
            loadRetryAttempt = 0
            cancelPendingRetry()
            val recovery = Runnable {
                pendingRetry = null
                loadAd(appContext, adUnitId)
            }
            pendingRetry = recovery
            mainHandler.postDelayed(recovery, RECOVERY_DELAY_MS)
            return
        }
        val delayMs = retryDelaysMs[loadRetryAttempt.coerceAtMost(retryDelaysMs.lastIndex)]
        loadRetryAttempt += 1
        Log.d(TAG, "Scheduling interstitial reload in ${delayMs}ms (attempt=$loadRetryAttempt).")
        cancelPendingRetry()
        val retry = Runnable {
            pendingRetry = null
            loadAd(appContext, adUnitId)
        }
        pendingRetry = retry
        mainHandler.postDelayed(retry, delayMs)
    }

    private fun cancelPendingRetry() {
        pendingRetry?.let { mainHandler.removeCallbacks(it) }
        pendingRetry = null
    }
}
