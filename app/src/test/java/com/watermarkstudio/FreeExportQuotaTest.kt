package com.watermarkstudio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.watermarkstudio.model.WatermarkType
import com.watermarkstudio.util.ProcessedMediaLibrary
import com.watermarkstudio.viewmodel.WatermarkViewModel
import java.io.File
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
    fun resetEditorSession_keepsProcessedLibraryUris() {
        val file = File(context.cacheDir, "wm_test.jpg")
        file.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        val uri = android.net.Uri.fromFile(file)
        ProcessedMediaLibrary.replaceAll(context, listOf(uri))
        viewModel.refreshProcessedLibrary(context)
        viewModel.resetEditorSession(WatermarkType.TEXT)
        assertEquals(listOf(uri), viewModel.uiState.value.processedMediaUris)
        assertEquals(0L, viewModel.uiState.value.exportSuccessBatchId)
    }
}
