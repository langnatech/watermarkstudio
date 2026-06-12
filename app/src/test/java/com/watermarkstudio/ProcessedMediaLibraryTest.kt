package com.watermarkstudio

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.watermarkstudio.util.ProcessedMediaLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProcessedMediaLibraryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("watermark_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    @Test
    fun append_persistsDistinctUris() {
        val first = writableFileUri("a.jpg")
        val second = writableFileUri("b.jpg")
        ProcessedMediaLibrary.append(context, listOf(first))
        val merged = ProcessedMediaLibrary.append(context, listOf(second))
        assertEquals(listOf(first, second), merged)
        assertEquals(merged, ProcessedMediaLibrary.load(context))
    }

    @Test
    fun load_emptyWhenNothingSaved() {
        assertTrue(ProcessedMediaLibrary.load(context).isEmpty())
    }

    private fun writableFileUri(name: String): Uri {
        val file = File(context.cacheDir, name)
        file.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
        return Uri.fromFile(file)
    }
}
