package com.watermarkstudio

import androidx.test.core.app.ApplicationProvider
import com.watermarkstudio.removal.RemovalQuality
import com.watermarkstudio.removal.RemovalQualityResolver
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RemovalQualityResolverTest {

    @Test
    fun freeUser_getsStandard() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals(RemovalQuality.STANDARD, RemovalQualityResolver.resolve(false, context))
    }

    @Test
    fun premiumUser_getsAdvanced() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals(RemovalQuality.ADVANCED, RemovalQualityResolver.resolve(true, context))
    }
}
