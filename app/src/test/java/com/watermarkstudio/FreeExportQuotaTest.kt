package com.watermarkstudio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.watermarkstudio.model.WatermarkType
import com.watermarkstudio.viewmodel.WatermarkViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FreeExportQuotaTest {

    private lateinit var context: Context
    private lateinit var viewModel: WatermarkViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        viewModel = WatermarkViewModel()
        context.getSharedPreferences("watermark_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        viewModel.checkPremium(context)
    }

    @Test
    fun canStartExport_whenUnderDailyLimit() {
        assertTrue(viewModel.canStartExport(context))
    }

    @Test
    fun canStartExport_false_afterMaxCommits() {
        viewModel.refreshMaxFreeExports(context)
        val max = viewModel.uiState.value.maxFreeExportsPerDay
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        context.getSharedPreferences("watermark_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("free_export_date", today)
            .putInt("free_export_count", max)
            .apply()
        viewModel.checkAndResetDailyLimit(context)
        assertFalse(viewModel.canStartExport(context))
    }

    @Test
    fun resetEditorSession_clearsProcessedUris() {
        viewModel.resetEditorSession(WatermarkType.TEXT)
        assertTrue(viewModel.uiState.value.processedMediaUris.isEmpty())
        assertEquals(0L, viewModel.uiState.value.exportSuccessBatchId)
    }
}
