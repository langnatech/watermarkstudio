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
            applyRoiAccumulators(outMat, region, accumulators, validPixels)
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
        accumulators: List<DoubleArray>,
        validPixels: Int,
    ) {
        if (accumulators.isEmpty() || validPixels <= 0) return
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
                applyRoiAccumulators(outMat, region, accumulators, validPixels)
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
        prevGray: Mat,
        currGray: Mat,
        prevBgr: Mat,
        region: com.watermarkstudio.util.RemovalRegion,
        algorithm: FlowAlgorithm,
    ): Pair<DoubleArray, Int>? {
        if (prevGray.cols() != currGray.cols() || prevGray.rows() != currGray.rows()) {
            return null
        }
        val flow = Mat()
        return try {
            when (algorithm) {
                FlowAlgorithm.FARNEBACK ->
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
                FlowAlgorithm.PYRAMID_LK ->
                    Video.calcOpticalFlowFarneback(
                        prevGray,
                        currGray,
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
