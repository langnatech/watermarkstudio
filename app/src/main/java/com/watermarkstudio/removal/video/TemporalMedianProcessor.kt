package com.watermarkstudio.removal.video

import android.graphics.Bitmap
import android.util.Log
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.removal.mask.MaskGenerator
import com.watermarkstudio.removal.native.RemovalNative
import com.watermarkstudio.util.RemovalRegion

object TemporalMedianProcessor {
    private const val TAG = "TemporalMedianProcessor"

    fun apply(frames: List<Bitmap>, config: WatermarkConfig): List<Bitmap> {
        if (frames.isEmpty()) return frames
        val w = frames.first().width
        val h = frames.first().height
        val region = MaskGenerator.regionForConfig(w, h, config)
        if (region.width <= 0 || region.height <= 0) return frames

        return if (RemovalNative.ensureLoaded()) {
            applyNative(frames, w, h, region)
        } else {
            Log.w(TAG, "Using Kotlin temporal-median fallback (native library unavailable)")
            applyKotlinTemporalMedian(frames, region)
        }
    }

    private fun applyNative(
        frames: List<Bitmap>,
        w: Int,
        h: Int,
        region: RemovalRegion,
    ): List<Bitmap> {
        val n = frames.size
        val stride = w * h * 4
        val buffer = ByteArray(n * stride)
        for (i in frames.indices) {
            val pixels = IntArray(w * h)
            frames[i].getPixels(pixels, 0, w, 0, 0, w, h)
            val base = i * stride
            for (p in pixels.indices) {
                val c = pixels[p]
                buffer[base + p * 4] = ((c shr 16) and 0xFF).toByte()
                buffer[base + p * 4 + 1] = ((c shr 8) and 0xFF).toByte()
                buffer[base + p * 4 + 2] = (c and 0xFF).toByte()
                buffer[base + p * 4 + 3] = ((c shr 24) and 0xFF).toByte()
            }
        }

        RemovalNative.applyTemporalMedian(
            buffer,
            n,
            w,
            h,
            stride,
            region.left,
            region.top,
            region.width,
            region.height,
        )

        return bufferToOutputFrames(frames, buffer, w, h, stride)
    }

    /**
     * Per-pixel temporal median inside ROI (same semantics as native path).
     */
    private fun applyKotlinTemporalMedian(
        frames: List<Bitmap>,
        region: RemovalRegion,
    ): List<Bitmap> {
        val n = frames.size
        val roiPixels = region.width * region.height
        val stack = Array(n) { IntArray(roiPixels) }
        for (fi in frames.indices) {
            var idx = 0
            for (y in region.top until region.bottom) {
                for (x in region.left until region.right) {
                    stack[fi][idx++] = frames[fi].getPixel(x, y)
                }
            }
        }

        val medianArgb = IntArray(roiPixels)
        val rs = IntArray(n)
        val gs = IntArray(n)
        val bs = IntArray(n)
        for (idx in 0 until roiPixels) {
            for (fi in 0 until n) {
                val c = stack[fi][idx]
                rs[fi] = (c shr 16) and 0xFF
                gs[fi] = (c shr 8) and 0xFF
                bs[fi] = c and 0xFF
            }
            rs.sort(0, n)
            gs.sort(0, n)
            bs.sort(0, n)
            val mid = n / 2
            medianArgb[idx] = (0xFF shl 24) or (rs[mid] shl 16) or (gs[mid] shl 8) or bs[mid]
        }

        return frames.mapIndexed { _, original ->
            val out = original.copy(Bitmap.Config.ARGB_8888, true)
            var idx = 0
            for (y in region.top until region.bottom) {
                for (x in region.left until region.right) {
                    out.setPixel(x, y, medianArgb[idx++])
                }
            }
            original.recycle()
            out
        }
    }

    private fun bufferToOutputFrames(
        frames: List<Bitmap>,
        buffer: ByteArray,
        w: Int,
        h: Int,
        stride: Int,
    ): List<Bitmap> =
        frames.mapIndexed { i, original ->
            val out = original.copy(Bitmap.Config.ARGB_8888, true)
            val pixels = IntArray(w * h)
            val base = i * stride
            for (p in pixels.indices) {
                val r = buffer[base + p * 4].toInt() and 0xFF
                val g = buffer[base + p * 4 + 1].toInt() and 0xFF
                val b = buffer[base + p * 4 + 2].toInt() and 0xFF
                val a = buffer[base + p * 4 + 3].toInt() and 0xFF
                pixels[p] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            out.setPixels(pixels, 0, w, 0, 0, w, h)
            original.recycle()
            out
        }
}
