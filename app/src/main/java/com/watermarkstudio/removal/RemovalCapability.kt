package com.watermarkstudio.removal

import android.app.ActivityManager
import android.content.Context
import android.os.Build
object RemovalCapability {

    fun supportsVideoRemoval(context: Context): Boolean {
        if (isEmulator()) return false
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val lowRam = am.isLowRamDevice
        return !lowRam && !isEmulator()
    }

    private fun isEmulator(): Boolean {
        val brand = Build.BRAND ?: ""
        val device = Build.DEVICE ?: ""
        val model = Build.MODEL ?: ""
        val hardware = Build.HARDWARE ?: ""
        val product = Build.PRODUCT ?: ""
        val fingerprint = Build.FINGERPRINT ?: ""
        return brand.startsWith("generic", ignoreCase = true) ||
            device.startsWith("generic", ignoreCase = true) ||
            model.contains("google_sdk", ignoreCase = true) ||
            model.contains("Emulator", ignoreCase = true) ||
            model.contains("Android SDK built for x86", ignoreCase = true) ||
            hardware.contains("goldfish", ignoreCase = true) ||
            hardware.contains("ranchu", ignoreCase = true) ||
            product.contains("sdk", ignoreCase = true) ||
            product.contains("google_sdk", ignoreCase = true) ||
            fingerprint.startsWith("generic", ignoreCase = true)
    }
}
