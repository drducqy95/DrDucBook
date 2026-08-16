package io.legado.app.help.vbook

import android.app.Application
import io.legado.app.constant.BookSourceType
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class VbookPluginImporterTest {

    @Test
    fun stablePluginId_isDeterministicAndMetadataSensitive() {
        val first = VbookPluginImporter.stablePluginId("https://example.com", "author", "name")
        val second = VbookPluginImporter.stablePluginId("https://example.com", "author", "name")
        val different = VbookPluginImporter.stablePluginId("https://example.com", "other", "name")

        assertEquals(24, first.length)
        assertEquals(first, second)
        assertNotEquals(first, different)
    }

    @Test
    fun bookSourceTypeForPlugin_mapsSupportedContentTypes() {
        assertEquals(BookSourceType.default, VbookPluginImporter.bookSourceTypeForPlugin("novel"))
        assertEquals(BookSourceType.default, VbookPluginImporter.bookSourceTypeForPlugin("CHINESE_NOVEL"))
        assertEquals(BookSourceType.image, VbookPluginImporter.bookSourceTypeForPlugin("comic"))
        assertEquals(BookSourceType.image, VbookPluginImporter.bookSourceTypeForPlugin("truyen-tranh"))
        assertEquals(BookSourceType.audio, VbookPluginImporter.bookSourceTypeForPlugin("audiobook"))
        assertEquals(BookSourceType.video, VbookPluginImporter.bookSourceTypeForPlugin(" video "))
    }

    @Test
    fun bookSourceTypeForPlugin_rejectsNonBookPlugins() {
        assertNull(VbookPluginImporter.bookSourceTypeForPlugin("tts"))
        assertNull(VbookPluginImporter.bookSourceTypeForPlugin("translate"))
    }

    @Test
    fun pluginConfig_isConvertedToLoginFieldsWithoutChangingVariableNames() {
        val loginUi = VbookPluginImporter.loginUiForConfig(
            JSONObject(
                """
                {
                  "USER_EMAIL": {"title": "Email đăng nhập", "mode": "input"},
                  "USER_PASSWORD": {"title": "Mật khẩu", "mode": "input"},
                  "CONFIG_GENDER": {
                    "title": "Giới tính",
                    "mode": "select",
                    "values": ["-1=Tất cả", "1=Nam", "0=Nữ"],
                    "default": "-1"
                  }
                }
                """.trimIndent(),
            ),
        )

        val rows = JSONArray(loginUi)
        assertEquals("USER_EMAIL", rows.getJSONObject(0).getString("name"))
        assertEquals("text", rows.getJSONObject(0).getString("type"))
        assertEquals("USER_PASSWORD", rows.getJSONObject(1).getString("name"))
        assertEquals("password", rows.getJSONObject(1).getString("type"))
        assertEquals("CONFIG_GENDER", rows.getJSONObject(2).getString("name"))
        assertEquals("select", rows.getJSONObject(2).getString("type"))
        assertEquals("-1", rows.getJSONObject(2).getString("default"))
        assertEquals("-1=Tất cả", rows.getJSONObject(2).getJSONArray("chars").getString(0))
    }
}
