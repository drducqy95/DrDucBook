package io.legado.app.help.vbook

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VbookPluginInstrumentedTest {

    @Test
    fun importsEncryptedPluginAndExecutesHomeScript() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val pluginPath = InstrumentationRegistry.getArguments().getString("pluginPath").orEmpty()
        val pluginFile = File(pluginPath)

        assertTrue("Missing VBook fixture: $pluginPath", pluginFile.isFile)
        val source = VbookPluginImporter.import(context, Uri.fromFile(pluginFile))

        assertTrue(source.bookSourceUrl.startsWith(VbookPluginAdapter.SOURCE_PREFIX))
        assertTrue(VbookPluginAdapter.canHandle(source))
        val kinds = VbookPluginAdapter.exploreKinds(source)
        assertTrue("The VBook home script returned no categories", kinds.isNotEmpty())
        assertTrue(kinds.all { it.title.isNotBlank() && it.url.orEmpty().startsWith("vbook://discover") })

        val pluginId = source.bookSourceUrl.orEmpty().removePrefix(VbookPluginAdapter.SOURCE_PREFIX)
        val installed = File(context.filesDir, "vbook_plugins/$pluginId")
        assertEquals(true, File(installed, "plugin.json").isFile)
    }
}
