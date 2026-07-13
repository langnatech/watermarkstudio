package com.watermarkstudio.removal.ml

import android.content.Context
import com.watermarkstudio.R
import com.watermarkstudio.removal.InpaintTarget
import com.watermarkstudio.removal.PreviewScaleContext
import com.watermarkstudio.removal.RemovalQuality

/**
 * Runtime configuration for on-device LaMa inpaint (values from [R] resources).
 */
object OnDeviceInpaintConfig {

    fun isEnabled(context: Context): Boolean =
        context.resources.getBoolean(R.bool.ondevice_inpaint_enabled)

    fun enableForVideo(context: Context): Boolean =
        context.resources.getBoolean(R.bool.ondevice_inpaint_enable_for_video)

    fun enableForPreview(context: Context): Boolean =
        context.resources.getBoolean(R.bool.ondevice_inpaint_enable_for_preview)

    fun assetPath(context: Context): String =
        context.getString(R.string.ondevice_inpaint_asset_path)

    fun localFileName(context: Context): String =
        context.getString(R.string.ondevice_inpaint_local_file_name)

    fun inputImageName(context: Context): String =
        context.getString(R.string.ondevice_inpaint_input_image)

    fun inputMaskName(context: Context): String =
        context.getString(R.string.ondevice_inpaint_input_mask)

    fun inputSize(context: Context): Int =
        context.resources.getInteger(R.integer.ondevice_inpaint_input_size)

    fun minModelBytes(context: Context): Long =
        context.resources.getInteger(R.integer.ondevice_inpaint_min_model_bytes).toLong()

    fun maskThreshold(context: Context): Int =
        context.resources.getInteger(R.integer.ondevice_inpaint_mask_threshold)

    fun shouldAttempt(
        context: Context?,
        quality: RemovalQuality,
        target: InpaintTarget,
        previewScale: PreviewScaleContext?,
    ): Boolean {
        if (context == null) return false
        if (!isEnabled(context)) return false
        if (quality != RemovalQuality.ADVANCED) return false
        if (target == InpaintTarget.VIDEO && !enableForVideo(context)) return false
        if (previewScale != null && !enableForPreview(context)) return false
        return true
    }
}
