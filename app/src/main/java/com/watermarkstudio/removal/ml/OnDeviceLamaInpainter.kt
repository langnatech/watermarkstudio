package com.watermarkstudio.removal.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.watermarkstudio.removal.PatchMatchInpainter
import com.watermarkstudio.util.RemovalRegion
import org.opencv.core.Mat
import java.io.File
import kotlin.math.max

/**
 * On-device LaMa inpaint (ONNX Runtime). Loads weights from APK assets; inference is offline.
 * Returns null on any failure so callers can fall back to PatchMatch.
 */
object OnDeviceLamaInpainter {

    private const val TAG = "OnDeviceLamaInpainter"

    @Volatile
    private var session: OrtSession? = null

    @Volatile
    private var environment: OrtEnvironment? = null

    @Volatile
    private var loadFailed = false

    fun ensureReady(context: Context): Boolean {
        if (session != null) return true
        if (loadFailed) return false
        if (!OnDeviceInpaintConfig.isEnabled(context)) {
            loadFailed = true
            return false
        }
        synchronized(this) {
            if (session != null) return true
            if (loadFailed) return false
            return try {
                val modelFile = materializeModelFile(context.applicationContext)
                val env = OrtEnvironment.getEnvironment()
                val options = OrtSession.SessionOptions()
                val loaded = env.createSession(modelFile.absolutePath, options)
                environment = env
                session = loaded
                Log.i(TAG, "Loaded on-device inpaint model from ${modelFile.absolutePath}")
                true
            } catch (e: Exception) {
                loadFailed = true
                Log.e(TAG, "Failed to load on-device inpaint model", e)
                false
            }
        }
    }

    /**
     * Runs LaMa on the ROI crop and pastes back with the shared soft-mask path.
     * @return repaired full-frame bitmap, or null to trigger classical fallback.
     */
    fun inpaint(
        context: Context,
        bitmap: Bitmap,
        mask: Mat,
        region: RemovalRegion,
        contextMarginPx: Int,
        featherRadiusPx: Int,
    ): Bitmap? {
        if (!ensureReady(context)) return null
        val activeSession = session ?: return null
        val env = environment ?: return null
        val inputSize = OnDeviceInpaintConfig.inputSize(context)
        if (inputSize <= 0) return null
        if (bitmap.width <= 0 || bitmap.height <= 0 || region.width <= 0 || region.height <= 0) {
            return null
        }

        val cropLeft = (region.left - contextMarginPx).coerceAtLeast(0)
        val cropTop = (region.top - contextMarginPx).coerceAtLeast(0)
        val cropRight = (region.right + contextMarginPx).coerceAtMost(bitmap.width)
        val cropBottom = (region.bottom + contextMarginPx).coerceAtMost(bitmap.height)
        val cropWidth = cropRight - cropLeft
        val cropHeight = cropBottom - cropTop
        if (cropWidth <= 0 || cropHeight <= 0) return null

        val source = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val crop = Bitmap.createBitmap(source, cropLeft, cropTop, cropWidth, cropHeight)
        val softMaskBytes = PatchMatchInpainter.maskCropBytesForMl(mask, cropLeft, cropTop, cropWidth, cropHeight)
        val scaledImage = Bitmap.createScaledBitmap(crop, inputSize, inputSize, true)
        val maskBitmap = softMaskToBitmap(softMaskBytes, cropWidth, cropHeight)
        val scaledMask = Bitmap.createScaledBitmap(maskBitmap, inputSize, inputSize, false)
        maskBitmap.recycle()

        var imageTensor: OnnxTensor? = null
        var maskTensor: OnnxTensor? = null
        var results: OrtSession.Result? = null
        return try {
            val imageNchw = bitmapToNchwFloat01(scaledImage, inputSize)
            val maskNchw = maskToNchwBinary(scaledMask, inputSize, OnDeviceInpaintConfig.maskThreshold(context))
            scaledImage.recycle()
            scaledMask.recycle()

            val imageBuf =
                java.nio.ByteBuffer
                    .allocateDirect(imageNchw.size * 4)
                    .order(java.nio.ByteOrder.nativeOrder())
                    .asFloatBuffer()
            imageBuf.put(imageNchw)
            imageBuf.rewind()
            val maskBuf =
                java.nio.ByteBuffer
                    .allocateDirect(maskNchw.size * 4)
                    .order(java.nio.ByteOrder.nativeOrder())
                    .asFloatBuffer()
            maskBuf.put(maskNchw)
            maskBuf.rewind()
            imageTensor =
                OnnxTensor.createTensor(
                    env,
                    imageBuf,
                    longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong()),
                )
            maskTensor =
                OnnxTensor.createTensor(
                    env,
                    maskBuf,
                    longArrayOf(1, 1, inputSize.toLong(), inputSize.toLong()),
                )
            val inputs =
                mapOf(
                    OnDeviceInpaintConfig.inputImageName(context) to imageTensor,
                    OnDeviceInpaintConfig.inputMaskName(context) to maskTensor,
                )
            results = activeSession.run(inputs)
            val repairedScaled = outputToBitmap(results, inputSize) ?: return null
            val repairedCrop =
                Bitmap.createScaledBitmap(repairedScaled, cropWidth, cropHeight, true)
            if (repairedScaled !== repairedCrop) repairedScaled.recycle()

            val out = source.copy(Bitmap.Config.ARGB_8888, true)
            PatchMatchInpainter.pasteRepairedForMl(
                out,
                source,
                repairedCrop,
                softMaskBytes,
                cropLeft,
                cropTop,
                cropWidth,
                cropHeight,
                featherRadiusPx,
            )
            repairedCrop.recycle()
            out
        } catch (e: Exception) {
            Log.e(TAG, "On-device inpaint failed", e)
            null
        } finally {
            results?.close()
            imageTensor?.close()
            maskTensor?.close()
            crop.recycle()
            if (source !== bitmap) source.recycle()
        }
    }

    private fun materializeModelFile(context: Context): File {
        val dir = File(context.filesDir, "ml")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Cannot create model directory: ${dir.absolutePath}")
        }
        val dest = File(dir, OnDeviceInpaintConfig.localFileName(context))
        val minBytes = OnDeviceInpaintConfig.minModelBytes(context)
        if (dest.exists() && dest.length() >= minBytes) {
            return dest
        }
        if (dest.exists()) dest.delete()
        val assetPath = OnDeviceInpaintConfig.assetPath(context)
        context.assets.open(assetPath).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        if (dest.length() < minBytes) {
            dest.delete()
            throw IllegalStateException(
                "On-device model too small (${dest.length()} < $minBytes); asset may be missing or truncated",
            )
        }
        return dest
    }

    private fun softMaskToBitmap(softMaskBytes: ByteArray, width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        for (i in softMaskBytes.indices) {
            val v = softMaskBytes[i].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    private fun bitmapToNchwFloat01(bitmap: Bitmap, size: Int): FloatArray {
        val hw = size * size
        val out = FloatArray(3 * hw)
        val pixels = IntArray(hw)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        for (i in 0 until hw) {
            val c = pixels[i]
            out[i] = ((c ushr 16) and 0xFF) / 255f
            out[hw + i] = ((c ushr 8) and 0xFF) / 255f
            out[2 * hw + i] = (c and 0xFF) / 255f
        }
        return out
    }

    private fun maskToNchwBinary(bitmap: Bitmap, size: Int, threshold: Int): FloatArray {
        val hw = size * size
        val out = FloatArray(hw)
        val pixels = IntArray(hw)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        val thr = threshold.coerceIn(0, 255)
        for (i in 0 until hw) {
            val v = pixels[i] and 0xFF
            out[i] = if (v >= thr) 1f else 0f
        }
        return out
    }

    private fun outputToBitmap(results: OrtSession.Result, size: Int): Bitmap? {
        val tensor = results.get(0) as? OnnxTensor ?: return null
        val hw = size * size
        val expected = 3 * hw
        val buffer = tensor.floatBuffer
        if (buffer.remaining() < expected) return null
        val planar = FloatArray(expected)
        buffer.get(planar)
        var maxAbs = 0f
        for (v in planar) maxAbs = max(maxAbs, kotlin.math.abs(v))
        val scale = if (maxAbs <= 1.5f) 255f else 1f
        val pixels = IntArray(hw)
        for (i in 0 until hw) {
            val r = (planar[i] * scale).toInt().coerceIn(0, 255)
            val g = (planar[hw + i] * scale).toInt().coerceIn(0, 255)
            val b = (planar[2 * hw + i] * scale).toInt().coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }
}
