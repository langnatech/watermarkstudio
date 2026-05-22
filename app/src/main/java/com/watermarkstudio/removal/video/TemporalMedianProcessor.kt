package com.watermarkstudio.removal.video

import android.graphics.Bitmap
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.removal.mask.MaskGenerator
import com.watermarkstudio.removal.native.RemovalNative
import java.nio.ByteBuffer

object TemporalMedianProcessor {

    fun apply(frames: List<Bitmap>, config: WatermarkConfig): List<Bitmap> {
        if (frames.isEmpty()) return frames
        val w = frames.first().width
        val h = frames.first().height
        val region = MaskGenerator.regionForConfig(w, h, config)
        if (region.width <= 0 || region.height <= 0) return frames

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

        return frames.mapIndexed { i, original ->
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
}
