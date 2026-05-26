package com.watermarkstudio

import android.app.Application
import android.util.Log
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import com.watermarkstudio.removal.OpenCvBootstrap

/**
 * Initializes EmojiCompat (bundled fonts) and OpenCV.
 * Startup Initializers are disabled in AndroidManifest to avoid cold-start crashes
 * on some devices (font provider failures).
 */
class WatermarkStudioApplication : Application() {

  override fun onCreate() {
    super.onCreate()
    initEmojiCompat()
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

  private companion object {
    const val TAG = "WatermarkStudioApp"
  }
}
