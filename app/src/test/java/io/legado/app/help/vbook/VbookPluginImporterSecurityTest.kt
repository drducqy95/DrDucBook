package io.legado.app.help.vbook

import android.app.Application
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class VbookPluginImporterSecurityTest {

    @Test
    fun importRejectsZipEntriesOutsidePluginDirectory() {
        val application: Application = RuntimeEnvironment.getApplication()
        val zipFile = File(application.cacheDir, "evil-vbook.zip")
        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("../plugin.json"))
            zip.write("""{"metadata":{"name":"Bad","source":"https://bad.example"}}""".toByteArray())
            zip.closeEntry()
        }

        assertThrows(SecurityException::class.java) {
            runBlocking {
                VbookPluginImporter.import(application, Uri.fromFile(zipFile))
            }
        }
    }
}
