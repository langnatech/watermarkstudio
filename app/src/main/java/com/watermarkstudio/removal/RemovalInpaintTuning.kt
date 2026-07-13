package com.watermarkstudio.removal

import com.watermarkstudio.util.RemovalRegion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Derives inpaint crop margin, PatchMatch iteration budget, and feather width
 * from the painted region geometry (not fixed globals).
 */
object RemovalInpaintTuning {

    data class RunConfig(
        val patchSize: Int,
        val emIterations: Int,
        val pmIterations: Int,
        val contextMarginPx: Int,
        val featherRadiusPx: Int,
    )

    fun resolve(
        region: RemovalRegion,
        maskedPixelCount: Int,
        quality: RemovalQuality,
        target: InpaintTarget,
        imageShortEdge: Int,
        previewScale: PreviewScaleContext? = null,
    ): RunConfig {
        val base = baseParameters(quality, target)
        val scaled = scaleForRegion(base, region, maskedPixelCount, target, previewScale)
        return RunConfig(
            patchSize = scaled.patchSize,
            emIterations = scaled.emIterations,
            pmIterations = scaled.pmIterations,
            contextMarginPx = contextMarginPx(region, target),
            featherRadiusPx = featherRadiusPx(region, imageShortEdge),
        )
    }

    internal fun contextMarginPx(region: RemovalRegion, target: InpaintTarget): Int {
        val span = max(region.width, region.height)
        val ratio =
            when (target) {
                InpaintTarget.IMAGE -> IMAGE_MARGIN_RATIO
                InpaintTarget.VIDEO -> VIDEO_MARGIN_RATIO
            }
        val (minMargin, maxMargin) =
            when (target) {
                InpaintTarget.IMAGE -> IMAGE_MARGIN_MIN_PX to IMAGE_MARGIN_MAX_PX
                InpaintTarget.VIDEO -> VIDEO_MARGIN_MIN_PX to VIDEO_MARGIN_MAX_PX
            }
        return (span * ratio).roundToInt().coerceIn(minMargin, maxMargin)
    }

    internal fun featherRadiusPx(region: RemovalRegion, imageShortEdge: Int): Int {
        val regionArea = region.width.toLong() * region.height
        val fromRegion = sqrt(regionArea.toDouble()) * FEATHER_RADIUS_SCALE
        val fromImage = imageShortEdge * FEATHER_IMAGE_EDGE_RATIO
        val estimate = fromRegion + fromImage
        val maxFeather = min(FEATHER_ABSOLUTE_MAX_PX, imageShortEdge / FEATHER_IMAGE_EDGE_DIVISOR)
        return estimate.roundToInt().coerceIn(FEATHER_MIN_PX, maxFeather.coerceAtLeast(FEATHER_MIN_PX))
    }

    private fun baseParameters(quality: RemovalQuality, target: InpaintTarget): PatchMatchParams =
        when (target) {
            InpaintTarget.IMAGE ->
                when (quality) {
                    RemovalQuality.STANDARD ->
                        PatchMatchParams(PATCH_SIZE_STANDARD, STANDARD_EM_ITERATIONS, STANDARD_PM_ITERATIONS)
                    RemovalQuality.ADVANCED ->
                        PatchMatchParams(PATCH_SIZE_ADVANCED, ADVANCED_EM_ITERATIONS, ADVANCED_PM_ITERATIONS)
                }
            InpaintTarget.VIDEO ->
                when (quality) {
                    RemovalQuality.STANDARD ->
                        PatchMatchParams(PATCH_SIZE_STANDARD, VIDEO_STANDARD_EM_ITERATIONS, VIDEO_STANDARD_PM_ITERATIONS)
                    RemovalQuality.ADVANCED ->
                        PatchMatchParams(PATCH_SIZE_STANDARD, VIDEO_ADVANCED_EM_ITERATIONS, VIDEO_ADVANCED_PM_ITERATIONS)
                }
        }

    private fun scaleForRegion(
        base: PatchMatchParams,
        region: RemovalRegion,
        maskedPixelCount: Int,
        target: InpaintTarget,
        previewScale: PreviewScaleContext?,
    ): PatchMatchParams {
        if (target == InpaintTarget.VIDEO) return base
        val areaScale = previewScale?.areaScale ?: 1f
        val regionAreaThreshold = (LARGE_REGION_AREA_PX * areaScale).toLong()
        val maskPixelThreshold = (LARGE_MASK_PIXELS * areaScale).roundToInt()
        val regionArea = region.width.toLong() * region.height
        val largeRegion = regionArea >= regionAreaThreshold || maskedPixelCount >= maskPixelThreshold
        if (!largeRegion) return base
        return base.copy(
            emIterations = (base.emIterations + LARGE_REGION_EXTRA_EM).coerceAtMost(MAX_EM_ITERATIONS),
            pmIterations = (base.pmIterations + LARGE_REGION_EXTRA_PM).coerceAtMost(MAX_PM_ITERATIONS),
        )
    }

    private data class PatchMatchParams(
        val patchSize: Int,
        val emIterations: Int,
        val pmIterations: Int,
    )

    private const val PATCH_SIZE_STANDARD = 7
    private const val PATCH_SIZE_ADVANCED = 9
    private const val STANDARD_EM_ITERATIONS = 1
    private const val STANDARD_PM_ITERATIONS = 3
    private const val ADVANCED_EM_ITERATIONS = 2
    private const val ADVANCED_PM_ITERATIONS = 8
    private const val VIDEO_STANDARD_EM_ITERATIONS = 1
    private const val VIDEO_STANDARD_PM_ITERATIONS = 2
    private const val VIDEO_ADVANCED_EM_ITERATIONS = 1
    private const val VIDEO_ADVANCED_PM_ITERATIONS = 6

    private const val IMAGE_MARGIN_RATIO = 0.5f
    private const val VIDEO_MARGIN_RATIO = 0.35f
    private const val IMAGE_MARGIN_MIN_PX = 48
    private const val IMAGE_MARGIN_MAX_PX = 160
    private const val VIDEO_MARGIN_MIN_PX = 32
    private const val VIDEO_MARGIN_MAX_PX = 128

    private const val FEATHER_RADIUS_SCALE = 0.018
    private const val FEATHER_IMAGE_EDGE_RATIO = 0.01f
    private const val FEATHER_IMAGE_EDGE_DIVISOR = 80
    private const val FEATHER_MIN_PX = 4
    private const val FEATHER_ABSOLUTE_MAX_PX = 40

    private const val LARGE_REGION_AREA_PX = 80_000L
    private const val LARGE_MASK_PIXELS = 12_000
    private const val LARGE_REGION_EXTRA_EM = 1
    private const val LARGE_REGION_EXTRA_PM = 2
    private const val MAX_EM_ITERATIONS = 4
    private const val MAX_PM_ITERATIONS = 12
}
