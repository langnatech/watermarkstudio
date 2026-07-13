package com.watermarkstudio.removal

import android.content.Context
import android.graphics.Bitmap
import com.watermarkstudio.removal.mask.MaskGenerator
import com.watermarkstudio.removal.ml.OnDeviceInpaintConfig
import com.watermarkstudio.removal.ml.OnDeviceLamaInpainter
import com.watermarkstudio.removal.native.RemovalNative
import com.watermarkstudio.util.RemovalRegion
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
object PatchMatchInpainter {

    private const val TELEA_MIN_RADIUS = 3.0
    private const val NS_MAX_RADIUS = 24.0

    fun inpaint(
        bitmap: Bitmap,
        mask: Mat,
        region: RemovalRegion,
        quality: RemovalQuality,
        target: InpaintTarget = InpaintTarget.IMAGE,
        previewScale: PreviewScaleContext? = null,
        context: Context? = null,
    ): Bitmap {
        if (bitmap.width <= 0 || bitmap.height <= 0 || region.width <= 0 || region.height <= 0) {
            return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }
        if (Core.countNonZero(mask) == 0) {
            return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }

        if (OnDeviceInpaintConfig.shouldAttempt(context, quality, target, previewScale)) {
            val maskedPixels = Core.countNonZero(mask)
            val imageShortEdge = min(bitmap.width, bitmap.height)
            val runConfig =
                RemovalInpaintTuning.resolve(
                    region,
                    maskedPixels,
                    quality,
                    target,
                    imageShortEdge,
                    previewScale,
                )
            val ml =
                runCatching {
                    OnDeviceLamaInpainter.inpaint(
                        context = context!!,
                        bitmap = bitmap,
                        mask = mask,
                        region = region,
                        contextMarginPx = runConfig.contextMarginPx,
                        featherRadiusPx = runConfig.featherRadiusPx,
                    )
                }.getOrNull()
            if (ml != null) return ml
        }

        return if (RemovalNative.isAvailable()) {
            runCatching { inpaintNative(bitmap, mask, region, quality, target, previewScale) }
                .getOrElse { fallbackNsOnCrop(bitmap, mask, region) }
        } else {
            fallbackNsOnCrop(bitmap, mask, region)
        }
    }

    /** Exposed for [OnDeviceLamaInpainter] ROI paste; keeps a single paste implementation. */
    internal fun maskCropBytesForMl(mask: Mat, left: Int, top: Int, width: Int, height: Int): ByteArray =
        maskCropBytes(mask, left, top, width, height)

    internal fun pasteRepairedForMl(
        out: Bitmap,
        original: Bitmap,
        repairedCrop: Bitmap,
        softMaskBytes: ByteArray,
        cropLeft: Int,
        cropTop: Int,
        cropWidth: Int,
        cropHeight: Int,
        featherRadiusPx: Int,
    ) {
        pasteRepaired(
            out,
            original,
            repairedCrop,
            softMaskBytes,
            cropLeft,
            cropTop,
            cropWidth,
            cropHeight,
            featherRadiusPx,
        )
    }

    private fun inpaintNative(
        bitmap: Bitmap,
        mask: Mat,
        region: RemovalRegion,
        quality: RemovalQuality,
        target: InpaintTarget,
        previewScale: PreviewScaleContext?,
    ): Bitmap {
        val maskedPixels = Core.countNonZero(mask)
        val imageShortEdge = min(bitmap.width, bitmap.height)
        val runConfig =
            RemovalInpaintTuning.resolve(
                region,
                maskedPixels,
                quality,
                target,
                imageShortEdge,
                previewScale,
            )
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
        val softMaskBytes = maskCropBytes(mask, cropLeft, cropTop, cropWidth, cropHeight)
        val hardMaskBytes = MaskGenerator.toInpaintCoreBytes(softMaskBytes)
        MaskedBackgroundPropagator.propagateIntoMask(imageBytes, hardMaskBytes, cropWidth, cropHeight)
        val nativeResult =
            RemovalNative.patchMatchInpaint(
                imageBytes,
                hardMaskBytes,
                cropWidth,
                cropHeight,
                runConfig.patchSize,
                runConfig.emIterations,
                runConfig.pmIterations,
            )
        if (nativeResult != RemovalNative.PATCH_MATCH_OK) {
            crop.recycle()
            if (source !== bitmap) source.recycle()
            return fallbackNsOnCrop(bitmap, mask, region)
        }

        val repairedCrop = rgbaBytesToBitmap(imageBytes, cropWidth, cropHeight)
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        pasteRepaired(
            out,
            source,
            repairedCrop,
            softMaskBytes,
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

    private fun fallbackNsOnCrop(bitmap: Bitmap, mask: Mat, region: RemovalRegion): Bitmap {
        val maskedPixels = Core.countNonZero(mask)
        val imageShortEdge = min(bitmap.width, bitmap.height)
        val runConfig =
            RemovalInpaintTuning.resolve(
                region,
                maskedPixels,
                RemovalQuality.STANDARD,
                InpaintTarget.IMAGE,
                imageShortEdge,
            )
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
        val softMaskBytes = maskCropBytes(mask, cropLeft, cropTop, cropWidth, cropHeight)
        val hardMaskBytes = MaskGenerator.toInpaintCoreBytes(softMaskBytes)
        val maskCrop = Mat(cropHeight, cropWidth, CvType.CV_8UC1)
        maskCrop.put(0, 0, hardMaskBytes)
        val src = Mat()
        val dst = Mat()
        return try {
            val preprocessed = preprocessBitmapForInpaint(crop, hardMaskBytes)
            Utils.bitmapToMat(preprocessed, src)
            if (preprocessed !== crop) preprocessed.recycle()
            if (src.channels() == 4) {
                Imgproc.cvtColor(src, src, Imgproc.COLOR_RGBA2BGR)
            }
            val cropMaskedPixels = hardMaskBytes.count { (it.toInt() and 0xFF) != 0 }
            val cropShortEdge = min(cropWidth, cropHeight)
            val radius =
                MaskedBackgroundPropagator.inpaintRadiusForMaskArea(cropMaskedPixels)
                    .coerceIn(TELEA_MIN_RADIUS, min(NS_MAX_RADIUS, cropShortEdge / 20.0))
            Photo.inpaint(src, maskCrop, dst, radius, Photo.INPAINT_NS)
            if (dst.channels() == 3) {
                Imgproc.cvtColor(dst, dst, Imgproc.COLOR_BGR2RGBA)
            }
            val repairedCrop = Bitmap.createBitmap(cropWidth, cropHeight, Bitmap.Config.ARGB_8888).also {
                Utils.matToBitmap(dst, it)
            }
            val out = source.copy(Bitmap.Config.ARGB_8888, true)
            pasteRepaired(
                out,
                source,
                repairedCrop,
                softMaskBytes,
                cropLeft,
                cropTop,
                cropWidth,
                cropHeight,
                runConfig.featherRadiusPx,
            )
            repairedCrop.recycle()
            out
        } finally {
            src.release()
            dst.release()
            maskCrop.release()
            crop.recycle()
            if (source !== bitmap) source.recycle()
        }
    }

    private fun preprocessBitmapForInpaint(bitmap: Bitmap, hardMaskBytes: ByteArray): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val rgba = bitmapToRgbaBytes(bitmap)
        MaskedBackgroundPropagator.propagateIntoMask(rgba, hardMaskBytes, width, height)
        return rgbaBytesToBitmap(rgba, width, height)
    }

    private fun bitmapToRgbaBytes(bitmap: Bitmap): ByteArray {
        val pixelCount = bitmap.width * bitmap.height
        val rgba = ByteArray(pixelCount * 4)
        val pixels = IntArray(pixelCount)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (index in pixels.indices) {
            val color = pixels[index]
            val base = index * 4
            rgba[base] = ((color shr 16) and 0xFF).toByte()
            rgba[base + 1] = ((color shr 8) and 0xFF).toByte()
            rgba[base + 2] = (color and 0xFF).toByte()
            rgba[base + 3] = ((color shr 24) and 0xFF).toByte()
        }
        return rgba
    }

    private fun rgbaBytesToBitmap(bytes: ByteArray, width: Int, height: Int): Bitmap {
        val pixelCount = width * height
        val pixels = IntArray(pixelCount)
        for (index in 0 until pixelCount) {
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

    /**
     * Prefer OpenCV Poisson [Photo.seamlessClone] when the mask stays clear of the crop border;
     * otherwise fall back to mean–variance + soft alpha feather.
     */
    private fun pasteRepaired(
        out: Bitmap,
        original: Bitmap,
        repairedCrop: Bitmap,
        softMaskBytes: ByteArray,
        cropLeft: Int,
        cropTop: Int,
        cropWidth: Int,
        cropHeight: Int,
        featherRadiusPx: Int,
    ) {
        val maskMat = Mat(cropHeight, cropWidth, CvType.CV_8UC1)
        val feather = Mat()
        try {
            maskMat.put(0, 0, softMaskBytes)
            val extraBlur = max(1, featherRadiusPx / 2) * 2 + 1
            Imgproc.GaussianBlur(
                maskMat,
                feather,
                Size(extraBlur.toDouble(), extraBlur.toDouble()),
                0.0,
            )
            val alphaBytes = ByteArray(cropWidth * cropHeight)
            feather.get(0, 0, alphaBytes)
            val pixelCount = cropWidth * cropHeight
            val basePixels = IntArray(pixelCount)
            val repairedPixels = IntArray(pixelCount)
            original.getPixels(basePixels, 0, cropWidth, cropLeft, cropTop, cropWidth, cropHeight)
            repairedCrop.getPixels(repairedPixels, 0, cropWidth, 0, 0, cropWidth, cropHeight)
            alignRepairedColorStatistics(basePixels, repairedPixels, alphaBytes)

            val seamlessOk =
                trySeamlessPaste(
                    out,
                    basePixels,
                    repairedPixels,
                    alphaBytes,
                    cropLeft,
                    cropTop,
                    cropWidth,
                    cropHeight,
                )
            if (seamlessOk) return

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

    private fun trySeamlessPaste(
        out: Bitmap,
        basePixels: IntArray,
        repairedPixels: IntArray,
        alphaBytes: ByteArray,
        cropLeft: Int,
        cropTop: Int,
        cropWidth: Int,
        cropHeight: Int,
    ): Boolean {
        if (cropWidth < 24 || cropHeight < 24) return false
        val border = 2
        var sumX = 0L
        var sumY = 0L
        var count = 0
        val binary = ByteArray(cropWidth * cropHeight)
        for (y in 0 until cropHeight) {
            for (x in 0 until cropWidth) {
                val index = y * cropWidth + x
                val a = alphaBytes[index].toInt() and 0xFF
                if (a < 128) continue
                if (x < border || y < border || x >= cropWidth - border || y >= cropHeight - border) {
                    return false
                }
                binary[index] = 255.toByte()
                sumX += x
                sumY += y
                count++
            }
        }
        if (count < 64) return false

        val center = Point((sumX / count).toDouble(), (sumY / count).toDouble())
        val srcArgb = Bitmap.createBitmap(cropWidth, cropHeight, Bitmap.Config.ARGB_8888)
        val dstArgb = Bitmap.createBitmap(cropWidth, cropHeight, Bitmap.Config.ARGB_8888)
        srcArgb.setPixels(repairedPixels, 0, cropWidth, 0, 0, cropWidth, cropHeight)
        dstArgb.setPixels(basePixels, 0, cropWidth, 0, 0, cropWidth, cropHeight)

        val srcRgba = Mat()
        val dstRgba = Mat()
        val srcBgr = Mat()
        val dstBgr = Mat()
        val maskMat = Mat(cropHeight, cropWidth, CvType.CV_8UC1)
        val blend = Mat()
        return try {
            Utils.bitmapToMat(srcArgb, srcRgba)
            Utils.bitmapToMat(dstArgb, dstRgba)
            Imgproc.cvtColor(srcRgba, srcBgr, Imgproc.COLOR_RGBA2BGR)
            Imgproc.cvtColor(dstRgba, dstBgr, Imgproc.COLOR_RGBA2BGR)
            maskMat.put(0, 0, binary)
            Photo.seamlessClone(srcBgr, dstBgr, maskMat, center, blend, Photo.NORMAL_CLONE)
            if (blend.empty() || blend.width() != cropWidth || blend.height() != cropHeight) {
                return false
            }
            val blendRgba = Mat()
            try {
                Imgproc.cvtColor(blend, blendRgba, Imgproc.COLOR_BGR2RGBA)
                val blended = Bitmap.createBitmap(cropWidth, cropHeight, Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(blendRgba, blended)
                val blendedPixels = IntArray(cropWidth * cropHeight)
                blended.getPixels(blendedPixels, 0, cropWidth, 0, 0, cropWidth, cropHeight)
                // Keep soft exterior from feather path: only trust seamless where alpha was strong.
                for (index in blendedPixels.indices) {
                    val alpha = alphaBytes[index].toInt() and 0xFF
                    when {
                        alpha == 0 -> Unit
                        alpha >= 240 -> basePixels[index] = blendedPixels[index]
                        else ->
                            basePixels[index] =
                                lerpArgb(basePixels[index], blendedPixels[index], alpha / 255f)
                    }
                }
                out.setPixels(basePixels, 0, cropWidth, cropLeft, cropTop, cropWidth, cropHeight)
                blended.recycle()
                true
            } finally {
                blendRgba.release()
            }
        } catch (_: Throwable) {
            false
        } finally {
            srcRgba.release()
            dstRgba.release()
            srcBgr.release()
            dstBgr.release()
            maskMat.release()
            blend.release()
            srcArgb.recycle()
            dstArgb.recycle()
        }
    }

    /**
     * Mean–variance match repaired interior to surrounding unmasked context to reduce color-block seams.
     */
    private fun alignRepairedColorStatistics(
        originalPixels: IntArray,
        repairedPixels: IntArray,
        alphaBytes: ByteArray,
    ) {
        var ctxCount = 0
        var repCount = 0
        var ctxR = 0.0
        var ctxG = 0.0
        var ctxB = 0.0
        var repR = 0.0
        var repG = 0.0
        var repB = 0.0
        for (i in alphaBytes.indices) {
            val a = alphaBytes[i].toInt() and 0xFF
            when {
                a == 0 -> {
                    val c = originalPixels[i]
                    ctxR += (c ushr 16) and 0xFF
                    ctxG += (c ushr 8) and 0xFF
                    ctxB += c and 0xFF
                    ctxCount++
                }
                a >= 200 -> {
                    val c = repairedPixels[i]
                    repR += (c ushr 16) and 0xFF
                    repG += (c ushr 8) and 0xFF
                    repB += c and 0xFF
                    repCount++
                }
            }
        }
        if (ctxCount < 16 || repCount < 16) return
        ctxR /= ctxCount
        ctxG /= ctxCount
        ctxB /= ctxCount
        repR /= repCount
        repG /= repCount
        repB /= repCount

        var ctxVarR = 0.0
        var ctxVarG = 0.0
        var ctxVarB = 0.0
        var repVarR = 0.0
        var repVarG = 0.0
        var repVarB = 0.0
        for (i in alphaBytes.indices) {
            val a = alphaBytes[i].toInt() and 0xFF
            when {
                a == 0 -> {
                    val c = originalPixels[i]
                    val dr = ((c ushr 16) and 0xFF) - ctxR
                    val dg = ((c ushr 8) and 0xFF) - ctxG
                    val db = (c and 0xFF) - ctxB
                    ctxVarR += dr * dr
                    ctxVarG += dg * dg
                    ctxVarB += db * db
                }
                a >= 200 -> {
                    val c = repairedPixels[i]
                    val dr = ((c ushr 16) and 0xFF) - repR
                    val dg = ((c ushr 8) and 0xFF) - repG
                    val db = (c and 0xFF) - repB
                    repVarR += dr * dr
                    repVarG += dg * dg
                    repVarB += db * db
                }
            }
        }
        ctxVarR /= ctxCount
        ctxVarG /= ctxCount
        ctxVarB /= ctxCount
        repVarR /= repCount
        repVarG /= repCount
        repVarB /= repCount

        val scaleR = sqrt(ctxVarR / max(repVarR, 1.0)).coerceIn(0.75, 1.35)
        val scaleG = sqrt(ctxVarG / max(repVarG, 1.0)).coerceIn(0.75, 1.35)
        val scaleB = sqrt(ctxVarB / max(repVarB, 1.0)).coerceIn(0.75, 1.35)

        for (i in alphaBytes.indices) {
            val a = alphaBytes[i].toInt() and 0xFF
            if (a == 0) continue
            val c = repairedPixels[i]
            val r = ((((c ushr 16) and 0xFF) - repR) * scaleR + ctxR).roundToInt().coerceIn(0, 255)
            val g = ((((c ushr 8) and 0xFF) - repG) * scaleG + ctxG).roundToInt().coerceIn(0, 255)
            val b = (((c and 0xFF) - repB) * scaleB + ctxB).roundToInt().coerceIn(0, 255)
            val alpha = (c ushr 24) and 0xFF
            repairedPixels[i] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
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
