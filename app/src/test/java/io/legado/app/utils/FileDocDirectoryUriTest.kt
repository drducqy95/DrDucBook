package io.legado.app.utils

import android.app.Application
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class FileDocDirectoryUriTest {

    @Test
    fun contentTreePathRemainsAContentUri() {
        val path = "content://com.android.externalstorage.documents/tree/primary%3ADownload"

        assertEquals(Uri.parse(path), directoryUriFromPath(path))
    }

    @Test
    fun localDirectoryPathBecomesAFileUri() {
        val directory = File("build/test-output/ebook export")

        assertEquals(Uri.fromFile(directory), directoryUriFromPath(directory.path))
    }
}
