package com.jpcottin.lenslate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LenslateApplicationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** What "Clear storage" leaves behind: a data directory without its cache directory. */
    @Test
    fun cacheDirectory_isRecreatedAfterItWasRemoved() {
        val cache = File(context.dataDir, "cache")
        cache.deleteRecursively()
        assertFalse(cache.exists())

        context.ensureCacheDirectory()

        assertTrue(cache.isDirectory)
    }
}
