package io.legado.app.utils

import android.app.Application
import android.content.Context
import android.content.Intent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class FilteredOpenDocumentContractTest {

    @Before
    fun setUpAppContext() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
    }

    @Test
    fun `uses primary mime instead of scanning every file type`() {
        val mimeTypes = arrayOf(
            "application/zip",
            "application/x-zip-compressed",
            "application/octet-stream",
        )
        val context: Context = RuntimeEnvironment.getApplication()

        val intent = FilteredOpenDocumentContract("application/zip")
            .createIntent(context, mimeTypes)

        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertEquals("application/zip", intent.type)
        assertEquals(true, intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false))
        assertArrayEquals(mimeTypes, intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
    }

    @Test
    fun `uses one shot content action for files copied during import`() {
        val context: Context = RuntimeEnvironment.getApplication()

        val intent = FilteredOpenDocumentContract(
            primaryMimeType = "application/zip",
            persistableAccess = false,
        ).createIntent(context, arrayOf("application/zip"))

        assertEquals(Intent.ACTION_GET_CONTENT, intent.action)
        assertEquals("application/zip", intent.type)
        assertEquals(true, intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false))
    }
}
