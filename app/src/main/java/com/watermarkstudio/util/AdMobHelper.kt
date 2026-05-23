package com.watermarkstudio.util

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdMobHelper {
    private const val TAG = "AdMobHelper"
    
    // Ad Unit IDs are loaded from strings.xml.
    // IMPORTANT: Replace values in strings.xml with REAL production IDs before release.

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

    fun isInitialized(): Boolean = initialized
}

@Composable
fun AdMobBannerAd(
    modifier: Modifier = Modifier,
    adUnitId: String? = null
) {
    val context = LocalContext.current
    val resolvedAdUnitId = adUnitId ?: context.getString(com.watermarkstudio.R.string.admob_banner_id)
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                setAdUnitId(resolvedAdUnitId)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                val adRequest = AdRequest.Builder().build()
                loadAd(adRequest)
            }
        },
        onRelease = { adView ->
            adView.destroy()
        },
        update = { adView ->
            // AdView properties update logic if needed
        }
    )
}

object InterstitialAdLoader {
    private const val TAG = "InterstitialAdLoader"
    private var mInterstitialAd: InterstitialAd? = null
    private var isAdLoading = false

    fun loadAd(context: Context, adUnitId: String = context.getString(com.watermarkstudio.R.string.admob_interstitial_id)) {
        if (mInterstitialAd != null || isAdLoading) return
        if (!AdMobHelper.isInitialized()) {
            AdMobHelper.initialize(context) { loadAd(context, adUnitId) }
            return
        }

        isAdLoading = true
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "Interstitial ad failed loaded: ${adError.message}")
                    mInterstitialAd = null
                    isAdLoading = false
                }

                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    Log.d(TAG, "Interstitial ad fully loaded.")
                    mInterstitialAd = interstitialAd
                    isAdLoading = false
                }
            }
        )
    }

    fun showAd(activity: android.app.Activity, onAdClosed: () -> Unit) {
        val ad = mInterstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Interstitial ad dismissed fullscreen.")
                    mInterstitialAd = null
                    // Reload for next usage using applicationContext to avoid Activity leak
                    loadAd(activity.applicationContext)
                    onAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "Interstitial ad error showing content: ${adError.message}")
                    mInterstitialAd = null
                    onAdClosed()
                }
            }
            ad.show(activity)
        } else {
            Log.d(TAG, "Interstitial ad is not ready yet. Skipping to success callback.")
            onAdClosed()
        }
    }
}
