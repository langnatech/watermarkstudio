package com.watermarkstudio

import androidx.test.core.app.ApplicationProvider
import com.watermarkstudio.removal.InpaintTarget
import com.watermarkstudio.removal.PreviewScaleContext
import com.watermarkstudio.removal.RemovalQuality
import com.watermarkstudio.removal.ml.OnDeviceInpaintConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OnDeviceInpaintConfigTest {

    @Test
    fun shouldAttempt_advancedImageExport_whenEnabled() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(
            OnDeviceInpaintConfig.shouldAttempt(
                context = context,
                quality = RemovalQuality.ADVANCED,
                target = InpaintTarget.IMAGE,
                previewScale = null,
            ),
        )
    }

    @Test
    fun shouldAttempt_falseForStandardOrVideoOrPreviewByDefault() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertFalse(
            OnDeviceInpaintConfig.shouldAttempt(
                context = context,
                quality = RemovalQuality.STANDARD,
                target = InpaintTarget.IMAGE,
                previewScale = null,
            ),
        )
        assertFalse(
            OnDeviceInpaintConfig.shouldAttempt(
                context = context,
                quality = RemovalQuality.ADVANCED,
                target = InpaintTarget.VIDEO,
                previewScale = null,
            ),
        )
        assertFalse(
            OnDeviceInpaintConfig.shouldAttempt(
                context = context,
                quality = RemovalQuality.ADVANCED,
                target = InpaintTarget.IMAGE,
                previewScale = PreviewScaleContext(exportMaxDim = 1920, previewMaxDim = 720),
            ),
        )
        assertFalse(
            OnDeviceInpaintConfig.shouldAttempt(
                context = null,
                quality = RemovalQuality.ADVANCED,
                target = InpaintTarget.IMAGE,
                previewScale = null,
            ),
        )
    }
}
