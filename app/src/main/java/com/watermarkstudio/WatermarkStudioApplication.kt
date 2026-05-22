package com.watermarkstudio

import android.app.Application
import android.util.Log
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import androidx.work.Configuration
import androidx.work.WorkManager
import com.watermarkstudio.removal.OpenCvBootstrap

/**
 * Initializes EmojiCompat (bundled fonts) and WorkManager manually.
 * Startup Initializers are disabled in AndroidManifest to avoid cold-start crashes
 * on some devices (WorkDatabase / font provider failures).
 */
class WatermarkStudioApplication : Application() {

  override fun onCreate() {
    super.onCreate()
    initEmojiCompat()
    initWorkManager()
    OpenCvBootstrap.ensureLoaded(this)
  }

  private fun initEmojiCompat() {
    if (EmojiCompat.isConfigured()) return
    try {
      EmojiCompat.init(BundledEmojiCompatConfig(this))
    } catch (e: Exception) {
      Log.e(TAG, "EmojiCompat init failed", e)
    }
  }

  private fun initWorkManager() {
    try {
      WorkManager.initialize(
        this,
        Configuration.Builder().build(),
      )
    } catch (e: IllegalStateException) {
      // Already initialized (e.g. from a library).
      Log.d(TAG, "WorkManager already initialized")
    } catch (e: Exception) {
      Log.e(TAG, "WorkManager init failed", e)
    }
  }

  private companion object {
    const val TAG = "WatermarkStudioApp"
  }
}
