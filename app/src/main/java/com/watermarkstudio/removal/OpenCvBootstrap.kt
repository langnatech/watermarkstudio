package com.watermarkstudio.removal

import android.content.Context
import android.util.Log
import org.opencv.android.OpenCVLoader

object OpenCvBootstrap {
    private const val TAG = "OpenCvBootstrap"

    @Volatile
    private var loaded = false

    fun ensureLoaded(context: Context): Boolean {
        if (loaded) return true
        synchronized(this) {
            if (loaded) return true
            val ok =
                try {
                    OpenCVLoader.initLocal() || OpenCVLoader.initDebug()
                } catch (e: Exception) {
                    Log.e(TAG, "OpenCV init failed", e)
                    false
                }
            loaded = ok
            if (!ok) {
                Log.e(TAG, "OpenCV native libraries not available on this device")
            }
            return ok
        }
    }
}
