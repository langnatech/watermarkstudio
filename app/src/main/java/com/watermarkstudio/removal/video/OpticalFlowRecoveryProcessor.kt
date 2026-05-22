package com.watermarkstudio.removal.video

import android.graphics.Bitmap
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.removal.mask.MaskGenerator
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video

/**
 * Fills occluded ROI using Farneback optical flow from neighbors; falls back to temporal median.
 */
object OpticalFlowRecoveryProcessor {

    private const val MAX_FLOW_MAGNITUDE = 48.0
    private const val MAX_INVALID_RATIO = 0.4f

    fun recover(
        frames: List<Bitmap>,
        config: WatermarkConfig,
        useOpticalFlow: Boolean,
    ): List<Bitmap> {
        if (frames.isEmpty()) return emptyList()
        if (!useOpticalFlow || frames.size < 2) {
            return TemporalMedianProcessor.apply(frames, config)
        }
        val width = frames.first().width
        val height = frames.first().height
        val region = MaskGenerator.regionForConfig(width, height, config)
        val mats = frames.map { b ->
            val m = Mat()
            Utils.bitmapToMat(b, m)
            m
        }
        val grays = mats.map { m ->
            val g = Mat()
            Imgproc.cvtColor(m, g, Imgproc.COLOR_BGR2GRAY)
            g
        }
        var flowFailures = 0
        val outputs = mutableListOf<Bitmap>()
        for (i in mats.indices) {
            val accumulators = mutableListOf<DoubleArray>()
            var validPixels = 0
            if (i > 0) {
                val r = warpRoi(grays[i - 1], grays[i], mats[i - 1], region)
                if (r == null) flowFailures++ else {
                    accumulators.add(r.first)
                    validPixels += r.second
                }
            }
            if (i < mats.size - 1) {
                val r = warpRoi(grays[i + 1], grays[i], mats[i + 1], region)
                if (r == null) flowFailures++ else {
                    accumulators.add(r.first)
                    validPixels += r.second
                }
            }
            val outMat = mats[i].clone()
            if (accumulators.isNotEmpty() && validPixels > 0) {
                val roiW = region.width
                val roiH = region.height
                var idx = 0
                for (y in region.top until region.bottom) {
                    for (x in region.left until region.right) {
                        var b = 0.0
                        var g = 0.0
                        var r = 0.0
                        var n = 0
                        for (acc in accumulators) {
                            val base = idx * 3
                            if (acc[base] >= 0) {
                                b += acc[base]
                                g += acc[base + 1]
                                r += acc[base + 2]
                                n++
                            }
                        }
                        if (n > 0) {
                            outMat.put(y, x, b / n, g / n, r / n)
                        }
                        idx++
                    }
                }
            } else {
                flowFailures++
            }
            val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(outMat, out)
            outMat.release()
            outputs.add(out)
        }
        grays.forEach { it.release() }
        mats.forEach { it.release() }
        return if (flowFailures > frames.size / 2) {
            outputs.forEach { it.recycle() }
            TemporalMedianProcessor.apply(frames, config)
        } else {
            frames.forEach { if (!it.isRecycled) it.recycle() }
            outputs
        }
    }

    /** Returns packed BGR per ROI pixel (-1 = invalid) and count of valid pixels. */
    private fun warpRoi(
        prevGray: Mat,
        currGray: Mat,
        prevBgr: Mat,
        region: com.watermarkstudio.util.RemovalRegion,
    ): Pair<DoubleArray, Int>? {
        val flow = Mat()
        return try {
            Video.calcOpticalFlowFarneback(
                prevGray,
                currGray,
                flow,
                0.5,
                3,
                15,
                3,
                5,
                1.2,
                0,
            )
            val roiPixels = region.width * region.height
            val packed = DoubleArray(roiPixels * 3) { -1.0 }
            var invalid = 0
            var total = 0
            var idx = 0
            for (y in region.top until region.bottom) {
                for (x in region.left until region.right) {
                    total++
                    val fx = flow.get(y, x)[0]
                    val fy = flow.get(y, x)[1]
                    if (kotlin.math.abs(fx) > MAX_FLOW_MAGNITUDE || kotlin.math.abs(fy) > MAX_FLOW_MAGNITUDE) {
                        invalid++
                        idx++
                        continue
                    }
                    val sx = (x + fx).toInt()
                    val sy = (y + fy).toInt()
                    if (sx in 0 until prevBgr.cols() && sy in 0 until prevBgr.rows()) {
                        val px = prevBgr.get(sy, sx)
                        packed[idx * 3] = px[0]
                        packed[idx * 3 + 1] = px[1]
                        packed[idx * 3 + 2] = px[2]
                    } else {
                        invalid++
                    }
                    idx++
                }
            }
            if (total == 0 || invalid.toFloat() / total > MAX_INVALID_RATIO) {
                null
            } else {
                Pair(packed, total - invalid)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            flow.release()
        }
    }
}
