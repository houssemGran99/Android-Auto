package com.autoflow.platform.files

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SafFolderTest {
    @Test
    fun fileNamesMustBePlainNames() {
        assertEquals("log.txt", SafFolder.validFileName("  log.txt "))
        assertNull(SafFolder.validFileName("../secret"))
        assertNull(SafFolder.validFileName("a/b.txt"))
        assertNull(SafFolder.validFileName(".."))
        assertNull(SafFolder.validFileName(" "))
    }

    @Test
    fun mimeTypeFollowsExtension() {
        assertEquals("text/plain", SafFolder.mimeTypeFor("notes.TXT"))
        assertEquals("text/csv", SafFolder.mimeTypeFor("data.csv"))
        assertEquals("application/octet-stream", SafFolder.mimeTypeFor("README"))
    }

    @Test
    fun onlyContentUrisAreAccepted() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        assertNull(SafFolder.parse(context, "/sdcard/Download"))
        assertNull(SafFolder.parse(context, ""))
    }
}
