package io.legado.app.help.vbook

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.constant.BookSourceType
import io.legado.app.domain.model.VbookCapability
import io.legado.app.domain.model.VbookCapabilityEvidence
import io.legado.app.domain.model.VbookCapabilityEvidenceKind
import io.legado.app.domain.model.VbookCapabilityProfile
import io.legado.app.domain.model.VbookPluginKind
import io.legado.app.utils.GSON
import java.io.File
import java.io.IOException

object VbookPluginInspector {

    fun inspectInstalled(
        pluginDirectory: File,
        pluginId: String,
        inspectedAt: Long = System.currentTimeMillis(),
    ): VbookCapabilityProfile {
        require(PLUGIN_ID.matches(pluginId)) { "Mã plugin VBook không hợp lệ" }
        val manifestFile = File(pluginDirectory, MANIFEST_FILE)
        if (!manifestFile.isFile || manifestFile.length() > MAX_MANIFEST_BYTES) {
            throw IOException("Không tìm thấy manifest VBook hợp lệ")
        }
        val scriptsRoot = File(pluginDirectory, "src")
        val loader = VbookExecutor.ScriptLoader(pluginDirectory)
        val scripts = listJavaScriptFiles(scriptsRoot)
            .associate { file ->
                val scriptName = file.relativeScriptName(scriptsRoot)
                scriptName to if (file.length() <= MAX_SCRIPT_BYTES) {
                    runCatching { loader.readScript(scriptName).orEmpty() }.getOrDefault("")
                } else {
                    ""
                }
            }
        return inspect(
            manifestJson = manifestFile.readText(Charsets.UTF_8),
            scripts = scripts,
            pluginId = pluginId,
            inspectedAt = inspectedAt,
        )
    }

    internal fun inspect(
        manifestJson: String,
        scripts: Map<String, String>,
        pluginId: String,
        inspectedAt: Long,
    ): VbookCapabilityProfile {
        val root = runCatching { JsonParser.parseString(manifestJson).asJsonObject }
            .getOrElse { throw IllegalArgumentException("Manifest VBook không hợp lệ", it) }
        val metadata = root.objectOrNull("metadata")
            ?: throw IllegalArgumentException("Manifest VBook thiếu metadata")
        val declaredKind = VbookPluginKind.fromDeclaredType(metadata.string("type").orEmpty())
        val version = metadata.nonNegativeInt("version") ?: 0
        val roleObject = root.objectOrNull("script")
        val roles = buildSet {
            roleObject?.entrySet()?.forEach { (role, value) ->
                if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                    add(role.trim().lowercase())
                }
            }
        }
        val evidence = mutableListOf<VbookCapabilityEvidence>()
        val capabilities = linkedSetOf<VbookCapability>()
        fun add(
            capability: VbookCapability,
            kind: VbookCapabilityEvidenceKind,
            detail: String,
        ) {
            capabilities += capability
            evidence += VbookCapabilityEvidence(capability, kind, detail)
        }

        val roleCapabilities = mapOf(
            "home" to VbookCapability.EXPLORE,
            "gen" to VbookCapability.EXPLORE,
            "genre" to VbookCapability.EXPLORE,
            "search" to VbookCapability.SEARCH,
            "detail" to VbookCapability.DETAIL,
            "toc" to VbookCapability.EPISODE_LIST,
            "page" to VbookCapability.EPISODE_LIST,
            "track" to VbookCapability.MEDIA_TRACK,
            "voice" to VbookCapability.TTS_VOICE_LIST,
            "tts" to VbookCapability.TTS_SYNTHESIS,
        )
        roles.forEach { role ->
            roleCapabilities[role]?.let { capability ->
                add(capability, VbookCapabilityEvidenceKind.MANIFEST_ROLE, "script.$role")
            }
        }
        if ("chap" in roles) {
            when (declaredKind) {
                VbookPluginKind.TEXT ->
                    add(
                        VbookCapability.TEXT_CONTENT,
                        VbookCapabilityEvidenceKind.DECLARED_TYPE,
                        "type + script.chap",
                    )

                VbookPluginKind.COMIC ->
                    add(
                        VbookCapability.IMAGE_CONTENT,
                        VbookCapabilityEvidenceKind.DECLARED_TYPE,
                        "type + script.chap",
                    )

                VbookPluginKind.AUDIOBOOK ->
                    add(
                        VbookCapability.AUDIO_CONTENT,
                        VbookCapabilityEvidenceKind.DECLARED_TYPE,
                        "type + script.chap",
                    )

                VbookPluginKind.VIDEO ->
                    add(
                        VbookCapability.VIDEO_CONTENT,
                        VbookCapabilityEvidenceKind.DECLARED_TYPE,
                        "type + script.chap",
                    )

                else -> Unit
            }
        }
        if ("track" in roles) {
            when (declaredKind) {
                VbookPluginKind.VIDEO ->
                    add(
                        VbookCapability.VIDEO_CONTENT,
                        VbookCapabilityEvidenceKind.MANIFEST_ROLE,
                        "video + script.track",
                    )

                VbookPluginKind.AUDIOBOOK ->
                    add(
                        VbookCapability.AUDIO_CONTENT,
                        VbookCapabilityEvidenceKind.MANIFEST_ROLE,
                        "audiobook + script.track",
                    )

                else -> Unit
            }
        }

        val scriptText = scripts.values.joinToString("\n")
            .take(MAX_COMBINED_SCRIPT_CHARS)
            .lowercase()
        if (
            "track" in roles ||
            declaredKind in setOf(VbookPluginKind.VIDEO, VbookPluginKind.AUDIOBOOK)
        ) {
            addScriptHints(scriptText, ::add)
        }
        if (VbookCapability.MEDIA_TRACK in capabilities) {
            if (
                capabilities.any {
                    it in setOf(
                        VbookCapability.HLS,
                        VbookCapability.DASH,
                        VbookCapability.DIRECT_VIDEO,
                    )
                }
            ) {
                add(
                    VbookCapability.VIDEO_CONTENT,
                    VbookCapabilityEvidenceKind.SCRIPT_HINT,
                    "script.track + video media hint",
                )
            } else if (VbookCapability.DIRECT_AUDIO in capabilities) {
                add(
                    VbookCapability.AUDIO_CONTENT,
                    VbookCapabilityEvidenceKind.SCRIPT_HINT,
                    "script.track + audio media hint",
                )
            }
        }
        if (
            capabilities.any {
                it in setOf(
                    VbookCapability.HLS,
                    VbookCapability.DASH,
                    VbookCapability.DIRECT_AUDIO,
                    VbookCapability.DIRECT_VIDEO,
                )
            }
        ) {
            add(
                VbookCapability.DOWNLOAD,
                VbookCapabilityEvidenceKind.SCRIPT_HINT,
                "direct/segmented media candidate",
            )
            add(
                VbookCapability.EXPORT,
                VbookCapabilityEvidenceKind.SCRIPT_HINT,
                "downloadable media candidate",
            )
        }
        return VbookCapabilityProfile(
            pluginId = pluginId,
            pluginVersion = version,
            declaredKind = declaredKind,
            scriptRoles = roles,
            capabilities = capabilities,
            evidence = evidence.distinct(),
            inspectedAt = inspectedAt,
        )
    }

    fun mergeRuntimeResult(
        profile: VbookCapabilityProfile,
        role: String,
        resultJson: String,
        inspectedAt: Long = System.currentTimeMillis(),
    ): VbookCapabilityProfile {
        if (resultJson.isBlank() || resultJson.length > MAX_RUNTIME_RESULT_CHARS) return profile
        val additions = linkedSetOf<VbookCapability>()
        val evidence = mutableListOf<VbookCapabilityEvidence>()
        fun add(capability: VbookCapability, detail: String) {
            additions += capability
            evidence += VbookCapabilityEvidence(
                capability = capability,
                kind = VbookCapabilityEvidenceKind.RUNTIME_RESULT,
                detail = "$role: $detail",
            )
        }
        addRuntimeHints(resultJson.lowercase(), ::add)
        if (additions.isEmpty() || additions.all(profile.capabilities::contains)) return profile
        if (
            additions.any {
                it in setOf(
                    VbookCapability.HLS,
                    VbookCapability.DASH,
                    VbookCapability.DIRECT_AUDIO,
                    VbookCapability.DIRECT_VIDEO,
                )
            }
        ) {
            add(VbookCapability.DOWNLOAD, "media URL verified")
            add(VbookCapability.EXPORT, "media URL verified")
        }
        return profile.copy(
            capabilities = profile.capabilities + additions,
            evidence = (profile.evidence + evidence).distinct(),
            inspectedAt = inspectedAt,
        )
    }

    fun preferredBookSourceType(profile: VbookCapabilityProfile): Int? {
        return when {
            profile.declaredKind in setOf(VbookPluginKind.TTS, VbookPluginKind.TRANSLATOR) -> null
            VbookCapability.VIDEO_CONTENT in profile.capabilities -> BookSourceType.video
            VbookCapability.AUDIO_CONTENT in profile.capabilities -> BookSourceType.audio
            VbookCapability.IMAGE_CONTENT in profile.capabilities -> BookSourceType.image
            else -> BookSourceType.default
        }
    }

    fun writeProfile(pluginDirectory: File, profile: VbookCapabilityProfile) {
        val target = File(pluginDirectory, PROFILE_FILE)
        val temp = File(pluginDirectory, "$PROFILE_FILE.tmp")
        temp.writeText(GSON.toJson(profile), Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    fun readProfile(pluginDirectory: File): VbookCapabilityProfile? {
        val file = File(pluginDirectory, PROFILE_FILE)
        if (!file.isFile || file.length() > MAX_PROFILE_BYTES) return null
        return runCatching {
            GSON.fromJson(file.readText(Charsets.UTF_8), VbookCapabilityProfile::class.java)
        }.getOrNull()?.takeIf { profile ->
            PLUGIN_ID.matches(profile.pluginId) &&
                profile.pluginVersion >= 0
        }
    }

    fun loadOrInspect(
        pluginDirectory: File,
        pluginId: String,
        inspectedAt: Long = System.currentTimeMillis(),
    ): VbookCapabilityProfile {
        readProfile(pluginDirectory)?.takeIf { it.pluginId == pluginId }?.let { return it }
        return inspectInstalled(pluginDirectory, pluginId, inspectedAt).also {
            writeProfile(pluginDirectory, it)
        }
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

    private fun addScriptHints(
        value: String,
        add: (VbookCapability, VbookCapabilityEvidenceKind, String) -> Unit,
    ) {
        fun hint(capability: VbookCapability, token: String) {
            if (token in value) {
                add(capability, VbookCapabilityEvidenceKind.SCRIPT_HINT, token)
            }
        }
        hint(VbookCapability.HLS, ".m3u8")
        hint(VbookCapability.HLS, "application/vnd.apple.mpegurl")
        hint(VbookCapability.DASH, ".mpd")
        hint(VbookCapability.DASH, "application/dash+xml")
        hint(VbookCapability.DIRECT_VIDEO, ".mp4")
        hint(VbookCapability.DIRECT_AUDIO, ".mp3")
        hint(VbookCapability.DIRECT_AUDIO, ".m4a")
        hint(VbookCapability.CUSTOM_HEADERS, "headers")
        hint(VbookCapability.REFERER, "referer")
        hint(VbookCapability.COOKIES, "cookie")
        if (
            listOf("subtitle", "caption", ".vtt", ".srt").any(value::contains)
        ) {
            add(
                VbookCapability.SUBTITLES,
                VbookCapabilityEvidenceKind.SCRIPT_HINT,
                "subtitle/caption",
            )
        }
        if (listOf("audio_track", "audiotrack", "audio track").any(value::contains)) {
            add(
                VbookCapability.AUDIO_TRACKS,
                VbookCapabilityEvidenceKind.SCRIPT_HINT,
                "audio track",
            )
        }
        if ("iframe" in value) {
            add(
                VbookCapability.EXTERNAL_PLAYER,
                VbookCapabilityEvidenceKind.SCRIPT_HINT,
                "iframe",
            )
        }
    }

    private fun addRuntimeHints(
        value: String,
        add: (VbookCapability, String) -> Unit,
    ) {
        if ("<img" in value || imageRuntimeRegex.containsMatchIn(value)) {
            add(VbookCapability.IMAGE_CONTENT, "chapter image result")
        }
        if (".m3u8" in value) add(VbookCapability.HLS, ".m3u8")
        if (".mpd" in value) add(VbookCapability.DASH, ".mpd")
        if (".mp4" in value) add(VbookCapability.DIRECT_VIDEO, ".mp4")
        if (".mp3" in value || ".m4a" in value) {
            add(VbookCapability.DIRECT_AUDIO, "audio URL")
        }
        if ("\"headers\"" in value) add(VbookCapability.CUSTOM_HEADERS, "headers object")
        if ("referer" in value) add(VbookCapability.REFERER, "Referer header")
        if ("cookie" in value) add(VbookCapability.COOKIES, "cookie data")
        if (
            listOf("subtitle", "caption", ".vtt", ".srt").any(value::contains)
        ) {
            add(VbookCapability.SUBTITLES, "subtitle result")
        }
        if ("\"type\":\"iframe\"" in value.replace(" ", "")) {
            add(VbookCapability.EXTERNAL_PLAYER, "iframe result")
        }
    }

    private fun JsonObject.objectOrNull(name: String): JsonObject? =
        get(name)?.takeIf(JsonElement::isJsonObject)?.asJsonObject

    private fun JsonObject.string(name: String): String? = runCatching {
        get(name)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
    }.getOrNull()

    private fun JsonObject.nonNegativeInt(name: String): Int? = runCatching {
        get(name)?.takeIf { it.isJsonPrimitive }?.asInt?.takeIf { it >= 0 }
    }.getOrNull()

    private const val MANIFEST_FILE = "plugin.json"
    private const val PROFILE_FILE = "capabilities.json"
    private const val MAX_MANIFEST_BYTES = 1024L * 1024L
    private const val MAX_PROFILE_BYTES = 1024L * 1024L
    private const val MAX_SCRIPT_BYTES = 2L * 1024L * 1024L
    private const val MAX_COMBINED_SCRIPT_CHARS = 8 * 1024 * 1024
    private const val MAX_RUNTIME_RESULT_CHARS = 1024 * 1024
    private val imageRuntimeRegex = Regex("(?i)\\.(?:jpe?g|png|webp|gif|avif)(?:[?#\\\"]|$)")
    private val PLUGIN_ID = Regex("[a-f0-9]{16,64}")
}
