package com.watermarkstudio.removal.native

import android.util.Log

object RemovalNative {
    private const val TAG = "RemovalNative"

    @Volatile
    private var nativeAvailable: Boolean? = null

    /** Loads libremoval_native once; returns false instead of crashing when unavailable. */
    fun ensureLoaded(): Boolean {
        nativeAvailable?.let { return it }
        return synchronized(this) {
            nativeAvailable?.let { return it }
            nativeAvailable =
                try {
                    System.loadLibrary("removal_native")
                    true
                } catch (e: UnsatisfiedLinkError) {
                    Log.w(TAG, "Native removal library not available", e)
                    false
                } catch (e: LinkageError) {
                    Log.w(TAG, "Native removal library linkage error", e)
                    false
                }
            nativeAvailable == true
        }
    }

    fun isAvailable(): Boolean = ensureLoaded()

    fun ping(): Int {
        if (!ensureLoaded()) return -1
        return nativePing()
    }

    fun applyTemporalMedian(
        framesRgba: ByteArray,
        mask: ByteArray,
        nFrames: Int,
        frameWidth: Int,
        frameHeight: Int,
        frameStride: Int,
        roiLeft: Int,
        roiTop: Int,
        roiWidth: Int,
        roiHeight: Int,
    ) {
        check(ensureLoaded()) { "removal_native is not loaded on this device" }
        nativeApplyTemporalMedian(
            framesRgba,
            mask,
            nFrames,
            frameWidth,
            frameHeight,
            frameStride,
            roiLeft,
            roiTop,
            roiWidth,
            roiHeight,
        )
    }

    const val PATCH_MATCH_OK = 0
    const val PATCH_MATCH_INSUFFICIENT_SOURCES = 1
    const val PATCH_MATCH_INVALID = 2

    fun patchMatchInpaint(
        imageRgba: ByteArray,
        mask: ByteArray,
        width: Int,
        height: Int,
        patchSize: Int,
        emIterations: Int,
        pmIterations: Int,
    ): Int {
        check(ensureLoaded()) { "removal_native is not loaded on this device" }
        return nativePatchMatchInpaint(
            imageRgba,
            mask,
            width,
            height,
            patchSize,
            emIterations,
            pmIterations,
        )
    }

    private external fun nativePing(): Int

    private external fun nativeApplyTemporalMedian(
        framesRgba: ByteArray,
        mask: ByteArray,
        nFrames: Int,
        frameWidth: Int,
        frameHeight: Int,
        frameStride: Int,
        roiLeft: Int,
        roiTop: Int,
        roiWidth: Int,
        roiHeight: Int,
    )

    private external fun nativePatchMatchInpaint(
        imageRgba: ByteArray,
        mask: ByteArray,
        width: Int,
        height: Int,
        patchSize: Int,
        emIterations: Int,
        pmIterations: Int,
    ): Int
}
