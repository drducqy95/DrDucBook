package io.legado.app.ui.vbook.importer

import android.app.Application
import android.content.Intent
import io.legado.app.ui.book.source.health.SourceHealthActivity
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class VbookImportActivityTest {

    @Test
    fun vbookImportActivityIsDeclaredAndResolvableInManifest() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, VbookImportActivity::class.java)
        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        assertNotNull("VbookImportActivity must be registered in AndroidManifest.xml", resolveInfo)
        assertTrue(
            "Resolved activity should be VbookImportActivity",
            resolveInfo?.activityInfo?.name == VbookImportActivity::class.java.name,
        )
    }

    @Test
    fun sourceHealthActivityIsDeclaredAndResolvableInManifest() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, SourceHealthActivity::class.java)
        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        assertNotNull("SourceHealthActivity must be registered in AndroidManifest.xml", resolveInfo)
        assertTrue(
            "Resolved activity should be SourceHealthActivity",
            resolveInfo?.activityInfo?.name == SourceHealthActivity::class.java.name,
        )
    }
}
