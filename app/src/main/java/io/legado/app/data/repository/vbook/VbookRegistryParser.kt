package io.legado.app.data.repository.vbook

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.domain.model.VbookPluginKind
import io.legado.app.domain.model.VbookRegistryItem
import io.legado.app.domain.model.VbookRegistryMetadata
import io.legado.app.domain.model.VbookRegistryOrigin
import io.legado.app.domain.model.VbookRegistrySnapshot
import java.net.URI
import java.security.MessageDigest

internal object VbookRegistryParser {

    fun parse(
        json: String,
        fetchedAt: Long,
        origin: VbookRegistryOrigin,
    ): VbookRegistrySnapshot {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_REGISTRY_BYTES) {
            "VBook registry vượt giới hạn dung lượng"
        }
        val root = runCatching { JsonParser.parseString(json).asJsonObject }
            .getOrElse { throw IllegalArgumentException("VBook registry không phải JSON hợp lệ", it) }
        val data = root.get("data")
            ?.takeIf(JsonElement::isJsonArray)
            ?.asJsonArray
            ?: throw IllegalArgumentException("VBook registry thiếu mảng data")
        require(data.size() <= MAX_REGISTRY_ITEMS) {
            "VBook registry có quá nhiều phần tử"
        }
        val metadataObject = root.objectOrNull("metadata") ?: JsonObject()
        val metadata = VbookRegistryMetadata(
            id = metadataObject.string("id").orEmpty().bounded(MAX_SHORT_TEXT),
            slug = metadataObject.string("slug").orEmpty().bounded(MAX_SHORT_TEXT),
            name = metadataObject.string("name").orEmpty().bounded(MAX_SHORT_TEXT),
            author = metadataObject.string("author").orEmpty().bounded(MAX_SHORT_TEXT),
            description = metadataObject.string("description").orEmpty().bounded(MAX_DESCRIPTION),
            version = metadataObject.nonNegativeInt("version") ?: 0,
            generatedAt = metadataObject.string("generatedAt").orEmpty().bounded(MAX_SHORT_TEXT),
            declaredItemCount = metadataObject.nonNegativeInt("totalItems") ?: data.size(),
        )
        val byId = linkedMapOf<String, VbookRegistryItem>()
        var rejected = 0
        data.forEach { element ->
            val item = element.takeIf(JsonElement::isJsonObject)
                ?.asJsonObject
                ?.toRegistryItem()
            if (item == null) {
                rejected++
            } else {
                val previous = byId[item.pluginId]
                if (previous == null || item.version > previous.version) {
                    byId[item.pluginId] = item
                }
            }
        }
        require(byId.isNotEmpty()) { "VBook registry không có plugin hợp lệ" }
        return VbookRegistrySnapshot(
            metadata = metadata,
            items = byId.values.toList(),
            rejectedItemCount = rejected,
            fetchedAt = fetchedAt,
            origin = origin,
        )
    }

    private fun JsonObject.toRegistryItem(): VbookRegistryItem? {
        val name = firstString(NAME_KEYS)?.trim()?.bounded(MAX_SHORT_TEXT).orEmpty()
        val author = firstString(AUTHOR_KEYS)?.trim()?.bounded(MAX_SHORT_TEXT).orEmpty()
        val downloadUrl = firstString(DOWNLOAD_URL_KEYS)?.trim().orEmpty()
        val source = firstString(SOURCE_KEYS)
            ?.trim()
            ?.bounded(MAX_SOURCE_TEXT)
            .orEmpty()
            .ifBlank { downloadUrl.httpHost().orEmpty() }
            .bounded(MAX_SOURCE_TEXT)
        val declaredType = firstString(TYPE_KEYS)
            ?.trim()
            ?.lowercase()
            ?.bounded(MAX_SHORT_TEXT)
            .orEmpty()
        val version = firstNonNegativeInt(VERSION_KEYS) ?: 0
        if (
            name.isBlank() ||
            downloadUrl.isBlank() ||
            !downloadUrl.isHttpUrl() ||
            source.isBlank()
        ) {
            return null
        }
        val rawIcon = firstString(ICON_KEYS)?.trim().orEmpty()
        val icon = rawIcon.takeIf { it.isBlank() || it.isHttpUrl() }.orEmpty()
        return VbookRegistryItem(
            pluginId = stablePluginId(source, author, name),
            name = name,
            author = author,
            downloadUrl = downloadUrl,
            version = version,
            source = source,
            iconUrl = icon,
            description = firstString(DESCRIPTION_KEYS).orEmpty().bounded(MAX_DESCRIPTION),
            declaredType = declaredType,
            declaredKind = VbookPluginKind.fromDeclaredType(declaredType),
            locale = firstString(LOCALE_KEYS).orEmpty().trim().bounded(MAX_SHORT_TEXT).ifBlank { "und" },
        )
    }

    private fun JsonObject.objectOrNull(name: String): JsonObject? =
        get(name)?.takeIf(JsonElement::isJsonObject)?.asJsonObject

    private fun JsonObject.string(name: String): String? = runCatching {
        get(name)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
    }.getOrNull()

    private fun JsonObject.firstString(names: Array<String>): String? =
        names.firstNotNullOfOrNull { name -> primitiveString(name)?.takeIf(String::isNotBlank) }

    private fun JsonObject.primitiveString(name: String): String? = runCatching {
        get(name)
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
    }.getOrNull()

    private fun JsonObject.nonNegativeInt(name: String): Int? = runCatching {
        get(name)
            ?.takeIf { it.isJsonPrimitive }
            ?.asInt
            ?.takeIf { it >= 0 }
    }.getOrNull()

    private fun JsonObject.firstNonNegativeInt(names: Array<String>): Int? =
        names.firstNotNullOfOrNull { name -> nonNegativeInt(name) }

    private fun String.isHttpUrl(): Boolean = runCatching {
        val uri = URI(this)
        uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    private fun String.httpHost(): String? = runCatching {
        val uri = URI(this)
        uri.host?.takeIf { uri.scheme?.lowercase() in setOf("http", "https") }
    }.getOrNull()

    private fun String.bounded(maxLength: Int): String = take(maxLength)

    internal fun stablePluginId(source: String, author: String, name: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$source\u0000$author\u0000$name".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(24)
    }

    private const val MAX_REGISTRY_BYTES = 5 * 1024 * 1024
    private const val MAX_REGISTRY_ITEMS = 5_000
    private const val MAX_SHORT_TEXT = 512
    private const val MAX_SOURCE_TEXT = 2_048
    private const val MAX_DESCRIPTION = 8_192
    private val NAME_KEYS = arrayOf("name", "title", "displayName", "label")
    private val AUTHOR_KEYS = arrayOf("author", "owner", "creator", "provider", "developer")
    private val DOWNLOAD_URL_KEYS = arrayOf(
        "path",
        "url",
        "downloadUrl",
        "downloadURL",
        "download",
        "file",
        "src",
        "href",
        "link",
        "zip",
        "archive",
    )
    private val SOURCE_KEYS = arrayOf(
        "source",
        "baseUrl",
        "baseURL",
        "host",
        "domain",
        "site",
        "origin",
        "configUrl",
        "configURL",
        "id",
        "package",
    )
    private val TYPE_KEYS = arrayOf("type", "kind", "category", "sourceType", "contentType", "pluginType")
    private val VERSION_KEYS = arrayOf("version", "versionCode", "version_code", "build", "revision")
    private val ICON_KEYS = arrayOf("icon", "iconUrl", "iconURL", "logo", "avatar", "cover")
    private val DESCRIPTION_KEYS = arrayOf("description", "desc", "summary", "intro", "comment")
    private val LOCALE_KEYS = arrayOf("locale", "language", "lang")
}
