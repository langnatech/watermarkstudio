package com.watermarkstudio.removal.video

import android.graphics.Bitmap
import android.media.Image

object VideoFrameUtils {

    fun bitmapToYuv420(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        val yuv = ByteArray(width * height * 3 / 2)
        var yIndex = 0
        var uvIndex = width * height
        for (j in 0 until height) {
            for (i in 0 until width) {
                val c = argb[j * width + i]
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return yuv
    }

    /** Converts [Image] YUV_420_888 to ARGB bitmap, respecting plane row/pixel stride. */
    fun imageYuv420888ToBitmap(image: Image): Bitmap? {
        val nv21 = yuv420888ToNv21(image) ?: return null
        return nv21ToBitmap(nv21, image.width, image.height)
    }

    /** Direct NV21 → ARGB (avoids lossy JPEG round-trip). */
    fun nv21ToBitmap(nv21: ByteArray, width: Int, height: Int): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val expected = width * height + width * height / 2
        if (nv21.size < expected) return null
        val argb = IntArray(width * height)
        var yp = 0
        for (j in 0 until height) {
            var uvp = width * height + (j shr 1) * width
            var u = 0
            var v = 0
            for (i in 0 until width) {
                val y = nv21[yp].toInt() and 0xFF
                if ((i and 1) == 0) {
                    v = (nv21[uvp++].toInt() and 0xFF) - 128
                    u = (nv21[uvp++].toInt() and 0xFF) - 128
                }
                val y1192 = 1192 * (y - 16)
                var r = (y1192 + 1634 * v) ushr 10
                var g = (y1192 - 833 * v - 400 * u) ushr 10
                var b = (y1192 + 2066 * u) ushr 10
                r = r.coerceIn(0, 255)
                g = g.coerceIn(0, 255)
                b = b.coerceIn(0, 255)
                argb[yp] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                yp++
            }
        }
        return Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun yuv420888ToNv21(image: Image): ByteArray? {
        val planes = image.planes
        if (planes.size < 3) return null
        val width = image.width
        val height = image.height
        val nv21 = ByteArray(width * height + width * height / 2)
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        var outputOffset = 0
        for (row in 0 until height) {
            var inputOffset = row * yRowStride
            for (col in 0 until width) {
                nv21[outputOffset++] = yBuffer.get(inputOffset)
                inputOffset += yPixelStride
            }
        }
        val chromaHeight = height / 2
        val chromaWidth = width / 2
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride
        for (row in 0 until chromaHeight) {
            var uInput = row * uRowStride
            var vInput = row * vRowStride
            for (col in 0 until chromaWidth) {
                nv21[outputOffset++] = vBuffer.get(vInput)
                nv21[outputOffset++] = uBuffer.get(uInput)
                uInput += uPixelStride
                vInput += vPixelStride
            }
        }
        return nv21
    }

    fun downscaleIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val maxSide = maxOf(w, h)
        if (maxSide <= maxDim) return bitmap
        val scale = maxDim.toFloat() / maxSide
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, nw, nh, true)
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }
}
