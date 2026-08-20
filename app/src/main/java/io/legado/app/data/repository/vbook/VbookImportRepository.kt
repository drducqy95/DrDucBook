package io.legado.app.data.repository.vbook

import android.content.Context
import android.net.Uri
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.constant.AppConst
import io.legado.app.data.dao.BookSourceDao
import io.legado.app.data.repository.validateInternetFetchUrl
import io.legado.app.domain.gateway.VbookImportGateway
import io.legado.app.domain.model.ImportClassification
import io.legado.app.domain.model.VbookImportAction
import io.legado.app.domain.model.VbookImportPreview
import io.legado.app.domain.model.VbookImportPreviewItem
import io.legado.app.domain.model.VbookPluginKind
import io.legado.app.domain.model.VbookRegistryOrigin
import io.legado.app.domain.model.inferredVbookCapabilities
import io.legado.app.help.http.await
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.vbook.VbookPluginAdapter
import io.legado.app.help.vbook.VbookPluginAliasStore
import io.legado.app.help.vbook.VbookPluginImporter
import io.legado.app.help.vbook.VbookPluginInspector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

class VbookImportRepository(
    private val context: Context,
    private val bookSourceDao: BookSourceDao,
    private val client: OkHttpClient = okHttpClient,
    private val now: () -> Long = System::currentTimeMillis,
    private val validateUrl: (String) -> String = { value ->
        validateInternetFetchUrl(value).getOrThrow()
    },
) : VbookImportGateway {

    override suspend fun preview(input: String): VbookImportPreview = withContext(Dispatchers.IO) {
        val raw = readRegistryInput(input)
        val parsed = runCatching { JsonParser.parseString(raw.content) }
            .getOrElse { throw IllegalArgumentException("VBook registry is not valid JSON", it) }
        val classification: ImportClassification
        val normalizedJson = when {
            parsed.isJsonObject && parsed.asJsonObject.get("data")?.isJsonArray == true -> {
                classification = ImportClassification.REGISTRY
                raw.content
            }
            parsed.isJsonArray -> {
                classification = ImportClassification.COMPATIBLE_ARRAY
                JsonObject().apply {
                    add("metadata", JsonObject())
                    add("data", parsed.asJsonArray)
                }.toString()
            }
            else -> throw IllegalArgumentException(
                "JSON must contain a data array or be a compatible plugin array"
            )
        }
        val snapshot = VbookRegistryParser.parse(
            json = normalizedJson,
            fetchedAt = now(),
            origin = raw.origin,
        )
        VbookImportPreview(
            classification = classification,
            sourceLabel = raw.label,
            items = snapshot.items.map { item -> toPreviewItem(item) },
            rejectedItemCount = snapshot.rejectedItemCount,
        )
    }

    override suspend fun install(item: VbookImportPreviewItem): String = withContext(Dispatchers.IO) {
        require(item.compatible) { item.compatibilityMessage ?: "Unsupported VBook plugin type" }
        val validatedUrl = validateUrl(item.downloadUrl)
        val temp = File(context.cacheDir, "vbook_${UUID.randomUUID()}.zip")
        try {
            val request = Request.Builder().url(validatedUrl).get().build()
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Cannot download VBook plugin: HTTP ${response.code}")
                }
                validateUrl(response.request.url.toString())
                response.body.byteStream().use { input ->
                    temp.outputStream().buffered().use { output ->
                        copyBounded(input, output, MAX_PLUGIN_DOWNLOAD_BYTES)
                    }
                }
            }
            inspectArchivePluginId(temp)
            val source = VbookPluginImporter.import(context, Uri.fromFile(temp))
            source.bookSourceUrl
                .removePrefix(VbookPluginAdapter.SOURCE_PREFIX)
                .takeIf { it != source.bookSourceUrl }
                ?.let { installedPluginId ->
                    VbookPluginAliasStore.record(context, item.pluginId, installedPluginId)
                }
            bookSourceDao.getBookSource(source.bookSourceUrl)?.let { existing ->
                source.bookSourceGroup = existing.bookSourceGroup
                source.customOrder = existing.customOrder
                source.enabled = existing.enabled
                source.enabledExplore = existing.enabledExplore
                source.enabledCookieJar = existing.enabledCookieJar
            }
            bookSourceDao.insert(source)
            source.bookSourceName
        } finally {
            temp.delete()
        }
    }

    private fun toPreviewItem(item: io.legado.app.domain.model.VbookRegistryItem): VbookImportPreviewItem {
        val installedPluginId = VbookPluginAliasStore.resolve(context, item.pluginId)
        val sourceUrl = VbookPluginAdapter.SOURCE_PREFIX + installedPluginId
        val existing = bookSourceDao.getBookSource(sourceUrl)
        val pluginDirectory = File(context.filesDir, "vbook_plugins/$installedPluginId")
        val installedVersion = VbookPluginInspector.readProfile(pluginDirectory)?.pluginVersion
        val action = when {
            existing == null -> VbookImportAction.INSTALL
            !pluginDirectory.isDirectory -> VbookImportAction.DUPLICATE_URL_WARNING
            installedVersion == item.version -> VbookImportAction.SKIP_SAME
            installedVersion != null && installedVersion > item.version ->
                VbookImportAction.DOWNGRADE_WARNING
            else -> VbookImportAction.UPDATE
        }
        val compatible = item.declaredKind !in setOf(
            VbookPluginKind.TTS,
            VbookPluginKind.TRANSLATOR,
        )
        return VbookImportPreviewItem(
            pluginId = item.pluginId,
            name = item.name,
            author = item.author,
            version = item.version,
            description = item.description,
            iconUrl = item.iconUrl,
            downloadUrl = item.downloadUrl,
            declaredKind = item.declaredKind,
            capabilities = inferredVbookCapabilities(item.declaredKind),
            action = action,
            compatible = compatible,
            compatibilityMessage = if (compatible) null else {
                "This extension is not a compatible book source"
            },
        )
    }

    private suspend fun readRegistryInput(input: String): RawRegistryInput {
        return if (input.startsWith("http://", true) || input.startsWith("https://", true)) {
            val requestUrl = input.removeSuffix("#requestWithoutUA")
            val validatedUrl = validateUrl(requestUrl)
            val request = Request.Builder().url(validatedUrl).get().apply {
                if (input.endsWith("#requestWithoutUA", true)) {
                    header(AppConst.UA_NAME, "null")
                }
            }.build()
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Cannot load VBook registry: HTTP ${response.code}")
                }
                val finalUrl = validateUrl(response.request.url.toString())
                RawRegistryInput(
                    content = response.body.byteStream().use {
                        readUtf8Bounded(it, MAX_REGISTRY_BYTES)
                    },
                    label = finalUrl,
                    origin = VbookRegistryOrigin.NETWORK,
                )
            }
        } else {
            val uri = Uri.parse(input)
            require(uri.scheme in setOf("content", "file")) {
                "Choose a JSON file or enter an HTTPS registry URL"
            }
            val content = context.contentResolver.openInputStream(uri)?.use {
                readUtf8Bounded(it, MAX_REGISTRY_BYTES)
            } ?: throw IOException("Cannot read selected VBook registry file")
            RawRegistryInput(content, uri.lastPathSegment ?: "registry.json", VbookRegistryOrigin.CACHE_VALIDATED)
        }
    }

    private fun inspectArchivePluginId(file: File): String {
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            var entries = 0
            while (entry != null) {
                entries++
                require(entries <= MAX_ARCHIVE_ENTRIES) { "VBook archive has too many files" }
                val name = entry.name.replace('\\', '/')
                require(!name.startsWith('/') && name.split('/').none { it == ".." }) {
                    "Unsafe VBook archive path"
                }
                if (!entry.isDirectory && name.substringAfterLast('/') == "plugin.json") {
                    val manifest = JSONObject(readUtf8Bounded(zip, MAX_MANIFEST_BYTES))
                    val metadata = manifest.optJSONObject("metadata")
                        ?: throw IOException("VBook plugin manifest is missing metadata")
                    return VbookPluginImporter.stablePluginId(
                        metadata.optString("source"),
                        metadata.optString("author"),
                        metadata.optString("name"),
                    )
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        throw IOException("VBook archive does not contain plugin.json")
    }

    private suspend fun copyBounded(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        maxBytes: Long,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            coroutineContext.ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw IOException("VBook download exceeds size limit")
            output.write(buffer, 0, read)
        }
    }

    private fun readUtf8Bounded(input: java.io.InputStream, maxBytes: Long): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw IOException("VBook input exceeds size limit")
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private data class RawRegistryInput(
        val content: String,
        val label: String,
        val origin: VbookRegistryOrigin,
    )

    private companion object {
        const val MAX_REGISTRY_BYTES = 5L * 1024L * 1024L
        const val MAX_PLUGIN_DOWNLOAD_BYTES = 30L * 1024L * 1024L
        const val MAX_MANIFEST_BYTES = 1024L * 1024L
        const val MAX_ARCHIVE_ENTRIES = 2_000
    }
}
