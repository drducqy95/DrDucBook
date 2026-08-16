package io.legado.app.help.vbook

import android.content.Context
import org.json.JSONObject
import java.io.File

object VbookPluginAliasStore {

    fun resolve(context: Context, pluginId: String): String {
        if (!PLUGIN_ID.matches(pluginId)) return pluginId
        return readAliases(context)
            .optString(pluginId)
            .takeIf { PLUGIN_ID.matches(it) }
            ?: pluginId
    }

    fun record(context: Context, aliasPluginId: String, installedPluginId: String) {
        if (
            aliasPluginId == installedPluginId ||
            !PLUGIN_ID.matches(aliasPluginId) ||
            !PLUGIN_ID.matches(installedPluginId)
        ) {
            return
        }
        val aliases = readAliases(context)
        if (aliases.optString(aliasPluginId) == installedPluginId) return
        aliases.put(aliasPluginId, installedPluginId)
        writeAliases(context, aliases)
    }

    internal fun readAliases(context: Context): JSONObject {
        val file = aliasFile(context)
        if (!file.isFile || file.length() > MAX_ALIAS_BYTES) return JSONObject()
        return runCatching { JSONObject(file.readText(Charsets.UTF_8)) }
            .getOrDefault(JSONObject())
    }

    private fun writeAliases(context: Context, aliases: JSONObject) {
        val file = aliasFile(context)
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(aliases.toString(), Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }

    private fun aliasFile(context: Context): File =
        File(context.filesDir, "vbook_plugins/aliases.json")

    private const val MAX_ALIAS_BYTES = 512L * 1024L
    private val PLUGIN_ID = Regex("[a-f0-9]{16,64}")
}
