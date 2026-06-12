package com.watermarkstudio.removal

import android.graphics.Bitmap
import com.watermarkstudio.removal.native.RemovalNative
import com.watermarkstudio.util.RemovalRegion
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import kotlin.math.roundToInt

object PatchMatchInpainter {

    private const val TELEA_MIN_RADIUS = 3.0
    private const val TELEA_MAX_RADIUS = 12.0

    fun inpaint(
        bitmap: Bitmap,
        mask: Mat,
        region: RemovalRegion,
        quality: RemovalQuality,
        target: InpaintTarget = InpaintTarget.IMAGE,
    ): Bitmap {
        if (bitmap.width <= 0 || bitmap.height <= 0 || region.width <= 0 || region.height <= 0) {
            return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }
        if (Core.countNonZero(mask) == 0) {
            return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }

        return if (RemovalNative.isAvailable()) {
            runCatching { inpaintNative(bitmap, mask, region, quality, target) }
                .getOrElse { fallbackTelea(bitmap, mask) }
        } else {
            fallbackTelea(bitmap, mask)
        }
    }

    private fun inpaintNative(
        bitmap: Bitmap,
        mask: Mat,
        region: RemovalRegion,
        quality: RemovalQuality,
        target: InpaintTarget,
    ): Bitmap {
        val maskedPixels = Core.countNonZero(mask)
        val runConfig = RemovalInpaintTuning.resolve(region, maskedPixels, quality, target)
        val contextMargin = runConfig.contextMarginPx
        val cropLeft = (region.left - contextMargin).coerceAtLeast(0)
        val cropTop = (region.top - contextMargin).coerceAtLeast(0)
        val cropRight = (region.right + contextMargin).coerceAtMost(bitmap.width)
        val cropBottom = (region.bottom + contextMargin).coerceAtMost(bitmap.height)
        val cropWidth = cropRight - cropLeft
        val cropHeight = cropBottom - cropTop
        if (cropWidth <= 0 || cropHeight <= 0) return bitmap.copy(Bitmap.Config.ARGB_8888, true)

        val source = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val crop = Bitmap.createBitmap(source, cropLeft, cropTop, cropWidth, cropHeight)
        val imageBytes = bitmapToRgbaBytes(crop)
        val maskBytes = maskCropBytes(mask, cropLeft, cropTop, cropWidth, cropHeight)
        MaskedBackgroundPropagator.propagateIntoMask(imageBytes, maskBytes, cropWidth, cropHeight)
        RemovalNative.patchMatchInpaint(
            imageBytes,
            maskBytes,
            cropWidth,
            cropHeight,
            runConfig.patchSize,
            runConfig.emIterations,
            runConfig.pmIterations,
        )

        val repairedCrop = rgbaBytesToBitmap(imageBytes, cropWidth, cropHeight)
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        featherPaste(
            out,
            source,
            repairedCrop,
            maskBytes,
            cropLeft,
            cropTop,
            cropWidth,
            cropHeight,
            runConfig.featherRadiusPx,
        )
        crop.recycle()
        repairedCrop.recycle()
        if (source !== bitmap) source.recycle()
        return out
    }

    private fun fallbackTelea(bitmap: Bitmap, mask: Mat): Bitmap {
        val src = Mat()
        val dst = Mat()
        return try {
            val preprocessed = preprocessBitmapForInpaint(bitmap, mask)
            Utils.bitmapToMat(preprocessed, src)
            if (preprocessed !== bitmap) preprocessed.recycle()
            if (src.channels() == 4) {
                Imgproc.cvtColor(src, src, Imgproc.COLOR_RGBA2BGR)
            }
            val maskedPixels = Core.countNonZero(mask)
            val radius =
                MaskedBackgroundPropagator.inpaintRadiusForMaskArea(maskedPixels)
                    .coerceIn(TELEA_MIN_RADIUS, TELEA_MAX_RADIUS)
            Photo.inpaint(src, mask, dst, radius, Photo.INPAINT_NS)
            if (dst.channels() == 3) {
                Imgproc.cvtColor(dst, dst, Imgproc.COLOR_BGR2RGBA)
            }
            Bitmap.createBitmap(dst.cols(), dst.rows(), Bitmap.Config.ARGB_8888).also {
                Utils.matToBitmap(dst, it)
            }
        } finally {
            src.release()
            dst.release()
        }
    }

    private fun preprocessBitmapForInpaint(bitmap: Bitmap, mask: Mat): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val rgba = bitmapToRgbaBytes(bitmap)
        val maskBytes = ByteArray(width * height).also { mask.get(0, 0, it) }
        MaskedBackgroundPropagator.propagateIntoMask(rgba, maskBytes, width, height)
        return rgbaBytesToBitmap(rgba, width, height)
    }

    private fun bitmapToRgbaBytes(bitmap: Bitmap): ByteArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val out = ByteArray(pixels.size * 4)
        pixels.forEachIndexed { index, color ->
            val base = index * 4
            out[base] = ((color shr 16) and 0xFF).toByte()
            out[base + 1] = ((color shr 8) and 0xFF).toByte()
            out[base + 2] = (color and 0xFF).toByte()
            out[base + 3] = ((color shr 24) and 0xFF).toByte()
        }
        return out
    }

    private fun rgbaBytesToBitmap(bytes: ByteArray, width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        for (index in pixels.indices) {
            val base = index * 4
            val r = bytes[base].toInt() and 0xFF
            val g = bytes[base + 1].toInt() and 0xFF
            val b = bytes[base + 2].toInt() and 0xFF
            val a = bytes[base + 3].toInt() and 0xFF
            pixels[index] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    private fun maskCropBytes(mask: Mat, left: Int, top: Int, width: Int, height: Int): ByteArray {
        val crop = Mat(mask, Rect(left, top, width, height))
        return try {
            ByteArray(width * height).also { crop.get(0, 0, it) }
        } finally {
            crop.release()
        }
    }

    private fun featherPaste(
        out: Bitmap,
        original: Bitmap,
        repairedCrop: Bitmap,
        maskBytes: ByteArray,
        cropLeft: Int,
        cropTop: Int,
        cropWidth: Int,
        cropHeight: Int,
        featherRadiusPx: Int,
    ) {
        val maskMat = Mat(cropHeight, cropWidth, org.opencv.core.CvType.CV_8UC1)
        val feather = Mat()
        try {
            maskMat.put(0, 0, maskBytes)
            val kernel = featherRadiusPx * 2 + 1
            Imgproc.GaussianBlur(
                maskMat,
                feather,
                Size(kernel.toDouble(), kernel.toDouble()),
                0.0,
            )
            val alphaBytes = ByteArray(cropWidth * cropHeight)
            feather.get(0, 0, alphaBytes)
            val pixelCount = cropWidth * cropHeight
            val basePixels = IntArray(pixelCount)
            val repairedPixels = IntArray(pixelCount)
            original.getPixels(basePixels, 0, cropWidth, cropLeft, cropTop, cropWidth, cropHeight)
            repairedCrop.getPixels(repairedPixels, 0, cropWidth, 0, 0, cropWidth, cropHeight)
            for (index in 0 until pixelCount) {
                val alpha = alphaBytes[index].toInt() and 0xFF
                if (alpha == 0) continue
                basePixels[index] = lerpArgb(basePixels[index], repairedPixels[index], alpha / 255f)
            }
            out.setPixels(basePixels, 0, cropWidth, cropLeft, cropTop, cropWidth, cropHeight)
        } finally {
            maskMat.release()
            feather.release()
        }
    }

    private fun lerpArgb(base: Int, overlay: Int, alpha: Float): Int {
        val inv = 1f - alpha
        val a = ((base ushr 24) * inv + (overlay ushr 24) * alpha).roundToInt().coerceIn(0, 255)
        val r = (((base ushr 16) and 0xFF) * inv + ((overlay ushr 16) and 0xFF) * alpha).roundToInt().coerceIn(0, 255)
        val g = (((base ushr 8) and 0xFF) * inv + ((overlay ushr 8) and 0xFF) * alpha).roundToInt().coerceIn(0, 255)
        val b = ((base and 0xFF) * inv + (overlay and 0xFF) * alpha).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
