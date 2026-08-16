package io.legado.app.help.vbook

import android.content.Context
import android.net.Uri
import io.legado.app.constant.BookSourceType
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.data.entities.rule.RowUi
import io.legado.app.domain.model.VbookCapabilityProfile
import io.legado.app.domain.model.VbookPluginKind
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

object VbookPluginImporter {

    suspend fun import(context: Context, uri: Uri): BookSource = withContext(Dispatchers.IO) {
        val pluginRoot = File(context.filesDir, "vbook_plugins").apply { mkdirs() }
        val staging = File(pluginRoot, "import_${UUID.randomUUID()}")
        check(staging.mkdirs()) { "Cannot create temporary VBook plugin directory" }
        try {
            context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
                unzipSafely(input, staging)
            } ?: throw IOException("Cannot read VBook plugin package")

            val extractedRoot = staging.walkTopDown()
                .maxDepth(4)
                .firstOrNull { it.isFile && it.name == MANIFEST_NAME }
                ?.parentFile
                ?: throw IOException("VBook package does not contain plugin.json")
            val manifest = validatePlugin(extractedRoot)
            val metadata = manifest.getJSONObject("metadata")
            val pluginId = stablePluginId(
                metadata.optString("source"),
                metadata.optString("author"),
                metadata.optString("name"),
            )
            val capabilityProfile = VbookPluginInspector.inspectInstalled(
                pluginDirectory = extractedRoot,
                pluginId = pluginId,
            )
            if (VbookPluginInspector.preferredBookSourceType(capabilityProfile) == null) {
                throw IOException("VBook plugin type ${metadata.optString("type")} is not a book source")
            }
            VbookPluginInspector.writeProfile(extractedRoot, capabilityProfile)
            installAtomically(pluginRoot, extractedRoot, pluginId)
            createBookSource(
                pluginId = pluginId,
                metadata = metadata,
                capabilityProfile = capabilityProfile,
                config = manifest.optJSONObject("config"),
            )
        } finally {
            staging.deleteRecursively()
        }
    }

    internal fun stablePluginId(source: String, author: String, name: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$source\u0000$author\u0000$name".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(24)
    }

    internal fun bookSourceTypeForPlugin(pluginType: String): Int? {
        return when (VbookPluginKind.fromDeclaredType(pluginType)) {
            VbookPluginKind.TEXT,
            VbookPluginKind.UNKNOWN -> BookSourceType.default
            VbookPluginKind.COMIC -> BookSourceType.image
            VbookPluginKind.AUDIOBOOK -> BookSourceType.audio
            VbookPluginKind.VIDEO -> BookSourceType.video
            VbookPluginKind.TTS,
            VbookPluginKind.TRANSLATOR -> null
        }
    }

    private fun unzipSafely(input: java.io.InputStream, target: File) {
        val canonicalTarget = target.canonicalFile
        var entryCount = 0
        var totalBytes = 0L
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entryCount++
                if (entryCount > MAX_ENTRIES) throw IOException("VBook package has too many files")
                val normalized = entry.name.replace('\\', '/')
                if (normalized.startsWith('/') || normalized.split('/').any { it == ".." }) {
                    throw SecurityException("Unsafe VBook package path: ${entry.name}")
                }
                val destination = File(canonicalTarget, normalized).canonicalFile
                if (
                    destination != canonicalTarget &&
                    !destination.path.startsWith(canonicalTarget.path + File.separator)
                ) {
                    throw SecurityException("VBook file escapes plugin directory")
                }
                if (entry.isDirectory) {
                    destination.mkdirs()
                } else {
                    destination.parentFile?.mkdirs()
                    destination.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var entryBytes = 0L
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            entryBytes += read
                            totalBytes += read
                            if (entryBytes > MAX_ENTRY_BYTES || totalBytes > MAX_TOTAL_BYTES) {
                                throw IOException("VBook package exceeds extracted size limit")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun validatePlugin(directory: File): JSONObject {
        val manifest = File(directory, MANIFEST_NAME)
        val root = try {
            JSONObject(manifest.readText(Charsets.UTF_8))
        } catch (error: Throwable) {
            throw IOException("plugin.json is not valid JSON", error)
        }
        val metadata = root.optJSONObject("metadata")
            ?: throw IOException("plugin.json is missing metadata")
        if (metadata.optString("name").isBlank() || metadata.optString("source").isBlank()) {
            throw IOException("VBook metadata is missing name or source")
        }
        val scripts = File(directory, "src")
        val scriptFiles = listJavaScriptFiles(scripts)
        if (!scripts.isDirectory || scriptFiles.isEmpty()) {
            throw IOException("VBook package does not contain src/*.js")
        }
        if (metadata.optBoolean("encrypt", false)) {
            val loader = VbookExecutor.ScriptLoader(directory)
            val declaredScripts = root.optJSONObject("script")
                ?.let { scriptObject ->
                    scriptObject.keys().asSequence()
                        .map { role -> scriptObject.optString(role).trim() }
                        .filter(String::isNotBlank)
                        .toList()
                }
                .orEmpty()
                .ifEmpty { scriptFiles.map { it.relativeScriptName(scripts) } }
            declaredScripts.forEach { scriptName ->
                if (loader.readScript(scriptName).isNullOrBlank()) {
                    throw IOException("Cannot decrypt VBook script $scriptName")
                }
            }
        }
        return root
    }

    private fun listJavaScriptFiles(scripts: File): List<File> {
        if (!scripts.isDirectory) return emptyList()
        val root = scripts.canonicalFile
        return scripts.walkTopDown()
            .filter { file -> file.isFile && file.extension.equals("js", true) }
            .mapNotNull { file ->
                val canonical = file.canonicalFile
                canonical.takeIf { it.path.startsWith(root.path + File.separator) }
            }
            .toList()
    }

    private fun File.relativeScriptName(root: File): String =
        canonicalFile.toRelativeString(root.canonicalFile).replace('\\', '/')

    private fun installAtomically(pluginRoot: File, extractedRoot: File, pluginId: String) {
        val target = File(pluginRoot, pluginId)
        val installing = File(pluginRoot, "${pluginId}_installing")
        val backup = File(pluginRoot, "${pluginId}_backup")
        installing.deleteRecursively()
        backup.deleteRecursively()
        extractedRoot.copyRecursively(installing, overwrite = true)
        validatePlugin(installing)
        var previousMoved = false
        try {
            if (target.exists()) {
                if (!target.renameTo(backup)) {
                    throw IOException("Cannot prepare old VBook plugin for replacement")
                }
                previousMoved = true
            }
            if (!installing.renameTo(target)) {
                installing.copyRecursively(target, overwrite = true)
                validatePlugin(target)
                installing.deleteRecursively()
            }
            backup.deleteRecursively()
        } catch (error: Throwable) {
            target.deleteRecursively()
            if (previousMoved && backup.exists()) backup.renameTo(target)
            throw error
        } finally {
            installing.deleteRecursively()
            if (backup.exists() && !target.exists()) backup.renameTo(target)
        }
    }

    internal fun reconcileInstalledSourceType(context: Context, source: BookSource): Boolean {
        val pluginId = source.bookSourceUrl
            .takeIf { it.startsWith(VbookPluginAdapter.SOURCE_PREFIX) }
            ?.removePrefix(VbookPluginAdapter.SOURCE_PREFIX)
            ?.takeIf { it.matches(Regex("[a-f0-9]{16,64}")) }
            ?: return false
        val pluginDirectory = File(context.filesDir, "vbook_plugins/$pluginId")
        if (!pluginDirectory.isDirectory) return false
        val manifest = runCatching {
            JSONObject(File(pluginDirectory, MANIFEST_NAME).readText(Charsets.UTF_8))
        }.getOrNull() ?: return false
        val metadata = manifest.optJSONObject("metadata") ?: return false
        var changed = applyManifestCompatibility(
            source = source,
            metadata = metadata,
            config = manifest.optJSONObject("config"),
        )
        val profile = runCatching {
            VbookPluginInspector.loadOrInspect(pluginDirectory, pluginId)
        }.getOrNull()
        val correctedType = profile?.let(VbookPluginInspector::preferredBookSourceType)
        if (correctedType != null && source.bookSourceType != correctedType) {
            source.bookSourceType = correctedType
            changed = true
        }
        return changed
    }

    private fun createBookSource(
        pluginId: String,
        metadata: JSONObject,
        capabilityProfile: VbookCapabilityProfile,
        config: JSONObject?,
    ): BookSource {
        val pluginType = metadata.optString("type")
        val type = VbookPluginInspector.preferredBookSourceType(capabilityProfile)
            ?: bookSourceTypeForPlugin(pluginType)
            ?: throw IOException("VBook plugin type $pluginType is not a book source")
        val description = metadata.optString("description")
        val author = metadata.optString("author")
        val version = metadata.optString("version")
        return BookSource(
            bookSourceUrl = VbookPluginAdapter.SOURCE_PREFIX + pluginId,
            bookSourceName = metadata.optString("name"),
            bookSourceGroup = "VBook",
            bookSourceType = type,
            bookUrlPattern = metadata.optString("regexp").takeIf(String::isNotBlank),
            enabled = true,
            enabledExplore = true,
            searchUrl = "vbook://search",
            exploreUrl = "vbook://home",
            ruleContent = ContentRule(content = "vbook"),
            bookSourceComment = buildString {
                if (description.isNotBlank()) append(description)
                if (author.isNotBlank()) append("\n\nVBook: ").append(author)
                if (version.isNotBlank()) append(" (v").append(version).append(')')
            }.trim(),
            lastUpdateTime = System.currentTimeMillis(),
        ).also { source ->
            applyManifestCompatibility(source, metadata, config)
        }
    }

    internal fun loginUiForConfig(config: JSONObject?): String? {
        if (config == null || config.length() == 0) return null
        val rows = config.keys().asSequence().mapNotNull { name ->
            val item = config.optJSONObject(name) ?: return@mapNotNull null
            val mode = item.optString("mode", "input").lowercase()
            val format = item.optString("format").lowercase()
            val type = when {
                mode == "toggle" -> RowUi.Type.toggle
                mode == "select" && format != "multiple" -> RowUi.Type.select
                isSecretConfig(name, item.optString("title"), format) -> RowUi.Type.password
                else -> RowUi.Type.text
            }
            val chars = when (type) {
                RowUi.Type.toggle -> arrayOf<String?>("false", "true")
                RowUi.Type.select -> item.optJSONArray("values")?.let { values ->
                    Array<String?>(values.length()) { index ->
                        values.opt(index)
                            ?.takeUnless { it == JSONObject.NULL }
                            ?.toString()
                    }
                }
                else -> null
            }
            val defaultValue = item.opt("default")
                ?.takeUnless { it == JSONObject.NULL }
                ?.toString()
                ?: if (type == RowUi.Type.toggle) "false" else ""
            RowUi(
                name = name,
                type = type,
                chars = chars,
                default = defaultValue,
                viewName = JSONObject.quote(item.optString("title", name)),
            )
        }.toList()
        return rows.takeIf(List<RowUi>::isNotEmpty)?.let(GSON::toJson)
    }

    private fun applyManifestCompatibility(
        source: BookSource,
        metadata: JSONObject,
        config: JSONObject?,
    ): Boolean {
        var changed = false
        val loginUi = loginUiForConfig(config)
        if (source.loginUi.isNullOrBlank() && loginUi != null) {
            source.loginUi = loginUi
            changed = true
        }
        if (source.loginUrl.isNullOrBlank()) {
            source.loginUrl = if (loginUi != null) {
                CONFIG_LOGIN_URL
            } else {
                httpSourceUrl(metadata.optString("source"))
            }
            changed = source.loginUrl != null || changed
        }
        if (source.header.isNullOrBlank()) {
            httpSourceUrl(metadata.optString("source"))?.let { referer ->
                source.header = JSONObject().put("Referer", referer).toString()
                changed = true
            }
        }
        return changed
    }

    private fun isSecretConfig(name: String, title: String, format: String): Boolean {
        val hint = "$name $title $format".lowercase()
        return listOf("password", "mật khẩu", "token", "secret", "api_key", "apikey")
            .any(hint::contains)
    }

    private fun httpSourceUrl(value: String): String? = runCatching {
        val uri = java.net.URI(value.trim())
        value.trim().takeIf {
            uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
        }
    }.getOrNull()

    private const val MANIFEST_NAME = "plugin.json"
    private const val CONFIG_LOGIN_URL = "@js:function login(){}"
    private const val MAX_ENTRIES = 2_000
    private const val MAX_ENTRY_BYTES = 20L * 1024L * 1024L
    private const val MAX_TOTAL_BYTES = 100L * 1024L * 1024L
}
