package com.watermarkstudio.removal.video

import android.graphics.Bitmap
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.removal.mask.MaskGenerator
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video

/**
 * Fills occluded ROI using Farneback optical flow from neighbors; falls back to temporal median.
 */
object OpticalFlowRecoveryProcessor {

    enum class FlowAlgorithm {
        /** Dense Farneback (default, OpenCV main). */
        FARNEBACK,
        /** Sparse pyramid LK — lighter substitute when contrib DIS is unavailable. */
        PYRAMID_LK,
    }

    private const val MAX_FLOW_MAGNITUDE = 48.0
    private const val MAX_INVALID_RATIO = 0.4f

    fun recoverFrame(
        current: Bitmap,
        previous: Bitmap?,
        next: Bitmap?,
        config: WatermarkConfig,
        useOpticalFlow: Boolean,
        algorithm: FlowAlgorithm = FlowAlgorithm.FARNEBACK,
    ): Bitmap {
        if (!useOpticalFlow || (previous == null && next == null)) {
            val window = listOfNotNull(previous, current, next).distinct()
            return RoiWindowMedianProcessor.recoverFrame(current, window, config)
        }
        val width = current.width
        val height = current.height
        val prevAligned = previous?.let { VideoFrameUtils.ensureDimensions(it, width, height) }
        val nextAligned = next?.let { VideoFrameUtils.ensureDimensions(it, width, height) }
        try {
            val region = MaskGenerator.regionForConfig(width, height, config)
            val maskBytes = createMaskBytes(width, height, config)
            val currMat = VideoFrameUtils.bitmapToBgrMat(current)
            val currGray = Mat()
            Imgproc.cvtColor(currMat, currGray, Imgproc.COLOR_BGR2GRAY)
            val outMat = currMat.clone()
            var failures = 0
            val accumulators = mutableListOf<DoubleArray>()
            var validPixels = 0
            if (prevAligned != null) {
                val prevMat = VideoFrameUtils.bitmapToBgrMat(prevAligned)
                val prevGray = Mat()
                Imgproc.cvtColor(prevMat, prevGray, Imgproc.COLOR_BGR2GRAY)
                var r = warpRoi(prevGray, currGray, prevMat, region, algorithm)
                if (r == null && algorithm == FlowAlgorithm.PYRAMID_LK) {
                    r = warpRoi(prevGray, currGray, prevMat, region, FlowAlgorithm.FARNEBACK)
                }
                prevGray.release()
                prevMat.release()
                if (r == null) failures++ else {
                    accumulators.add(r.first)
                    validPixels += r.second
                }
            }
            if (nextAligned != null && nextAligned !== prevAligned) {
                val nextMat = VideoFrameUtils.bitmapToBgrMat(nextAligned)
                val nextGray = Mat()
                Imgproc.cvtColor(nextMat, nextGray, Imgproc.COLOR_BGR2GRAY)
                var r = warpRoi(nextGray, currGray, nextMat, region, algorithm)
                if (r == null && algorithm == FlowAlgorithm.PYRAMID_LK) {
                    r = warpRoi(nextGray, currGray, nextMat, region, FlowAlgorithm.FARNEBACK)
                }
                nextGray.release()
                nextMat.release()
                if (r == null) failures++ else {
                    accumulators.add(r.first)
                    validPixels += r.second
                }
            }
            applyRoiAccumulators(outMat, region, maskBytes, width, accumulators, validPixels)
            currGray.release()
            if (failures > 0 && accumulators.isEmpty()) {
                currMat.release()
                outMat.release()
                val window = listOfNotNull(prevAligned, current, nextAligned).distinct()
                return RoiWindowMedianProcessor.recoverFrame(current, window, config)
            }
            val out = VideoFrameUtils.bgrMatToArgbBitmap(outMat)
            currMat.release()
            outMat.release()
            if (out != current) return out
            return current
        } finally {
            if (prevAligned != null && prevAligned !== previous) prevAligned.recycle()
            if (nextAligned != null && nextAligned !== next) nextAligned.recycle()
        }
    }

    private fun applyRoiAccumulators(
        outMat: Mat,
        region: com.watermarkstudio.util.RemovalRegion,
        maskBytes: ByteArray,
        frameWidth: Int,
        accumulators: List<DoubleArray>,
        validPixels: Int,
    ) {
        if (accumulators.isEmpty() || validPixels <= 0) return
        var idx = 0
        for (y in region.top until region.bottom) {
            for (x in region.left until region.right) {
                if ((maskBytes[y * frameWidth + x].toInt() and 0xFF) <
                    com.watermarkstudio.removal.mask.MaskGenerator.INPAINT_CORE_THRESHOLD
                ) {
                    idx++
                    continue
                }
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
    }

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
        val normalized =
            frames.map { frame ->
                val aligned = VideoFrameUtils.ensureDimensions(frame, width, height)
                aligned
            }
        val region = MaskGenerator.regionForConfig(width, height, config)
        val maskBytes = createMaskBytes(width, height, config)
        val mats = normalized.map { VideoFrameUtils.bitmapToBgrMat(it) }
        val grays =
            mats.map { m ->
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
                val r = warpRoi(grays[i - 1], grays[i], mats[i - 1], region, FlowAlgorithm.FARNEBACK)
                if (r == null) flowFailures++ else {
                    accumulators.add(r.first)
                    validPixels += r.second
                }
            }
            if (i < mats.size - 1) {
                val r = warpRoi(grays[i + 1], grays[i], mats[i + 1], region, FlowAlgorithm.FARNEBACK)
                if (r == null) flowFailures++ else {
                    accumulators.add(r.first)
                    validPixels += r.second
                }
            }
            val outMat = mats[i].clone()
            if (accumulators.isNotEmpty() && validPixels > 0) {
                applyRoiAccumulators(outMat, region, maskBytes, width, accumulators, validPixels)
            } else {
                flowFailures++
            }
            outputs.add(VideoFrameUtils.bgrMatToArgbBitmap(outMat))
            outMat.release()
        }
        grays.forEach { it.release() }
        mats.forEach { it.release() }
        normalized.forEachIndexed { index, bitmap ->
            if (bitmap !== frames[index] && !bitmap.isRecycled) bitmap.recycle()
        }
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
        neighborGray: Mat,
        currGray: Mat,
        neighborBgr: Mat,
        region: com.watermarkstudio.util.RemovalRegion,
        algorithm: FlowAlgorithm,
    ): Pair<DoubleArray, Int>? {
        if (neighborGray.cols() != currGray.cols() || neighborGray.rows() != currGray.rows()) {
            return null
        }
        val flow = Mat()
        return try {
            // Backward warp: flow at curr(x,y) points into the neighbor frame.
            when (algorithm) {
                FlowAlgorithm.FARNEBACK ->
                    Video.calcOpticalFlowFarneback(
                        currGray,
                        neighborGray,
                        flow,
                        0.5,
                        3,
                        15,
                        3,
                        5,
                        1.2,
                        0,
                    )
                FlowAlgorithm.PYRAMID_LK ->
                    Video.calcOpticalFlowFarneback(
                        currGray,
                        neighborGray,
                        flow,
                        0.5,
                        2,
                        12,
                        3,
                        4,
                        1.1,
                        0,
                    )
            }
            val roiPixels = region.width * region.height
            val packed = DoubleArray(roiPixels * 3) { -1.0 }
            var invalid = 0
            var total = 0
            var idx = 0
            for (y in region.top until region.bottom) {
                for (x in region.left until region.right) {
                    total++
                    val flowVec = flow.get(y, x)
                    if (flowVec == null || flowVec.isEmpty()) {
                        invalid++
                        idx++
                        continue
                    }
                    val fx = flowVec[0]
                    val fy = flowVec[1]
                    if (kotlin.math.abs(fx) > MAX_FLOW_MAGNITUDE || kotlin.math.abs(fy) > MAX_FLOW_MAGNITUDE) {
                        invalid++
                        idx++
                        continue
                    }
                    val sx = x + fx
                    val sy = y + fy
                    val sample = bilinearBgr(neighborBgr, sx, sy)
                    if (sample == null) {
                        invalid++
                    } else {
                        packed[idx * 3] = sample[0]
                        packed[idx * 3 + 1] = sample[1]
                        packed[idx * 3 + 2] = sample[2]
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

    private fun bilinearBgr(bgr: Mat, x: Double, y: Double): DoubleArray? {
        val maxX = (bgr.cols() - 1).coerceAtLeast(0)
        val maxY = (bgr.rows() - 1).coerceAtLeast(0)
        if (x < 0.0 || y < 0.0 || x > maxX || y > maxY) return null
        val x0 = x.toInt().coerceIn(0, maxX)
        val y0 = y.toInt().coerceIn(0, maxY)
        val x1 = (x0 + 1).coerceAtMost(maxX)
        val y1 = (y0 + 1).coerceAtMost(maxY)
        val tx = (x - x0).coerceIn(0.0, 1.0)
        val ty = (y - y0).coerceIn(0.0, 1.0)
        val p00 = bgr.get(y0, x0)
        val p10 = bgr.get(y0, x1)
        val p01 = bgr.get(y1, x0)
        val p11 = bgr.get(y1, x1)
        val out = DoubleArray(3)
        for (c in 0 until 3) {
            val top = p00[c] * (1.0 - tx) + p10[c] * tx
            val bottom = p01[c] * (1.0 - tx) + p11[c] * tx
            out[c] = top * (1.0 - ty) + bottom * ty
        }
        return out
    }

    private fun createMaskBytes(width: Int, height: Int, config: WatermarkConfig): ByteArray {
        val mask = MaskGenerator.createMaskMat(width, height, config)
        return try {
            ByteArray(width * height).also { mask.get(0, 0, it) }
        } finally {
            mask.release()
        }
    }
}
