package com.watermarkstudio.removal.native

object RemovalNative {
    init {
        System.loadLibrary("removal_native")
    }

    fun ping(): Int = nativePing()

    fun applyTemporalMedian(
        framesRgba: ByteArray,
        nFrames: Int,
        frameWidth: Int,
        frameHeight: Int,
        frameStride: Int,
        roiLeft: Int,
        roiTop: Int,
        roiWidth: Int,
        roiHeight: Int,
    ) {
        nativeApplyTemporalMedian(
            framesRgba,
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

    private external fun nativePing(): Int

    private external fun nativeApplyTemporalMedian(
        framesRgba: ByteArray,
        nFrames: Int,
        frameWidth: Int,
        frameHeight: Int,
        frameStride: Int,
        roiLeft: Int,
        roiTop: Int,
        roiWidth: Int,
        roiHeight: Int,
    )
}
