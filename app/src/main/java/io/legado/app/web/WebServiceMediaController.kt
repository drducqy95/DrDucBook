package io.legado.app.web

import io.legado.app.domain.model.MediaProtocol
import io.legado.app.domain.model.ResolvedBookMedia
import io.legado.app.domain.model.ResolvedMediaVariant
import io.legado.app.domain.usecase.ResolveBookMediaUseCase
import io.legado.app.domain.webservice.WebServiceMediaChapter
import io.legado.app.domain.webservice.WebServiceMediaSessionResponse
import io.legado.app.domain.webservice.WebServiceMediaTrack
import io.legado.app.domain.webservice.WebServiceMediaVariant
import okhttp3.Request
import okhttp3.Response
import splitties.init.appCtx
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import io.legado.app.help.http.okHttpClient

object WebServiceMediaController {
    private const val SESSION_TTL_MILLIS = 60 * 60 * 1000L
    private val sessions = ConcurrentHashMap<String, MediaSession>()

    suspend fun create(bookUrl: String, chapterIndex: Int?): WebServiceMediaSessionResponse {
        require(bookUrl.isNotBlank()) { "MEDIA_BOOK_REQUIRED" }
        val media = GlobalContext.get().get<ResolveBookMediaUseCase>().execute(bookUrl, chapterIndex).getOrThrow()
        val id = UUID.randomUUID().toString()
        val session = MediaSession(id, media, System.currentTimeMillis() + SESSION_TTL_MILLIS)
        sessions[id] = session
        trimExpired()
        return responseFor(session)
    }

    fun getSession(id: String): MediaSession? = sessions[id]?.let { session ->
        if (session.expiresAt <= System.currentTimeMillis()) {
            sessions.remove(id, session)
            null
        } else {
            session.touch()
            session
        }
    }

    fun responseFor(session: MediaSession): WebServiceMediaSessionResponse {
        val base = "/api/v2/media/sessions/${session.id}"
        return WebServiceMediaSessionResponse(
            sessionId = session.id,
            expiresAt = session.expiresAt,
            bookUrl = session.media.bookUrl,
            bookTitle = WebTextRepair.repair(session.media.bookTitle).orEmpty(),
            coverUrl = session.media.coverUrl,
            chapterIndex = session.media.chapterIndex,
            chapterCount = session.media.chapterCount,
            previousChapterIndex = session.media.previousChapterIndex,
            nextChapterIndex = session.media.nextChapterIndex,
            isVideo = session.media.isVideo,
            chapters = session.media.chapters.map {
                WebServiceMediaChapter(it.index, WebTextRepair.repair(it.title).orEmpty(), it.isOffline)
            },
            variants = session.media.media.variants.map { variant ->
                WebServiceMediaVariant(
                    id = variant.id,
                    title = WebTextRepair.repair(variant.title).orEmpty(),
                    contentKind = variant.contentKind.name,
                    protocol = variant.protocol.name,
                    mimeType = variant.mimeType,
                    playbackUrl = "$base/variants/${encode(variant.id)}",
                    externalPlayerRequired = variant.externalPlayerRequired,
                    drmUnsupported = variant.drmUnsupported,
                    durationMs = variant.durationMs,
                )
            },
            subtitles = session.media.media.subtitles.map { track ->
                WebServiceMediaTrack(track.id, WebTextRepair.repair(track.label).orEmpty(), track.language, track.mimeType, "$base/tracks/subtitle/${encode(track.id)}", track.isDefault)
            },
            audioTracks = session.media.media.audioTracks.map { track ->
                WebServiceMediaTrack(track.id, WebTextRepair.repair(track.label).orEmpty(), track.language, track.mimeType, "$base/tracks/audio/${encode(track.id)}", track.isDefault)
            },
        )
    }

    fun variant(session: MediaSession, id: String): ResolvedMediaVariant? =
        session.media.media.variants.firstOrNull { it.id == id }

    fun source(session: MediaSession, uri: String, headers: Map<String, String>): SourceTarget {
        val localFile = when {
            uri.startsWith("file:", ignoreCase = true) -> runCatching { File(URI(uri)) }.getOrNull()
            else -> File(uri).takeIf { it.isAbsolute }
        }
        if (localFile != null) return SourceTarget.Local(localFile, headers)
        return SourceTarget.Remote(uri, headers)
    }

    suspend fun openRemote(target: SourceTarget.Remote, range: String?): Response = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(target.uri)
        target.headers.forEach { (key, value) ->
            if (key.isNotBlank() && key.lowercase() !in HOP_BY_HOP_HEADERS && value.isNotBlank()) builder.header(key, value)
        }
        if (!range.isNullOrBlank()) builder.header("Range", range)
        okHttpClient.newCall(builder.get().build()).execute()
    }

    suspend fun openManifest(
        session: MediaSession,
        variant: ResolvedMediaVariant,
        accessToken: String? = null,
    ): ManifestResult? {
        if (variant.protocol != MediaProtocol.HLS && variant.protocol != MediaProtocol.DASH) return null
        val response = openRemote(source(session, variant.uri, variant.headers) as SourceTarget.Remote, null)
        val body = response.body.string()
        response.close()
        val rewritten = rewriteManifest(session, variant.uri, variant.protocol, variant.headers, body, accessToken)
        val mime = if (variant.protocol == MediaProtocol.HLS) "application/vnd.apple.mpegurl" else "application/dash+xml"
        return ManifestResult(rewritten, mime)
    }

    suspend fun openNestedManifest(session: MediaSession, resource: ResourceTarget): ManifestResult? {
        if (!resource.uri.looksLikePlaylist()) return null
        val response = openRemote(SourceTarget.Remote(resource.uri, resource.headers), null)
        val body = response.body.string()
        val contentType = response.header("Content-Type").orEmpty()
        response.close()
        if (!contentType.contains("mpegurl", true) && !resource.uri.looksLikePlaylist()) return null
        return ManifestResult(
            rewriteManifest(session, resource.uri, MediaProtocol.HLS, resource.headers, body, resource.accessToken),
            "application/vnd.apple.mpegurl",
        )
    }

    private fun rewriteManifest(
        session: MediaSession,
        baseUri: String,
        protocol: MediaProtocol,
        headers: Map<String, String>,
        body: String,
        accessToken: String?,
    ): String {
        val base = "/api/v2/media/sessions/${session.id}/resources/"
        val rewrite = { raw: String ->
            val absolute = runCatching { URI(baseUri).resolve(raw).toString() }.getOrDefault(raw)
            val token = UUID.randomUUID().toString()
            session.resources[token] = ResourceTarget(absolute, headers, accessToken)
            appendAccessToken(base + token, accessToken)
        }
        var result = body.replace(Regex("URI=\\\"([^\\\"]+)\\\"")) { match -> "URI=\\\"${rewrite(match.groupValues[1])}\\\"" }
        if (protocol == MediaProtocol.HLS) {
            result = result.lines().joinToString("\n") { line ->
                if (line.isBlank() || line.startsWith('#')) line else rewrite(line.trim())
            }
        } else {
            result = result.replace(Regex("(media|initialization)=\\\"([^\\\"]+)\\\"")) { match -> "${match.groupValues[1]}=\\\"${rewrite(match.groupValues[2])}\\\"" }
        }
        return result
    }

    fun resource(session: MediaSession, id: String): ResourceTarget? = session.resources[id]

    private fun trimExpired() {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { it.value.expiresAt <= now }
        if (sessions.size > 20) sessions.keys.firstOrNull()?.let(sessions::remove)
    }

    private fun encode(value: String) = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun appendAccessToken(path: String, accessToken: String?): String =
        accessToken?.takeIf(String::isNotBlank)?.let {
            "$path?access_token=${encode(it)}"
        } ?: path

    class MediaSession(val id: String, val media: ResolvedBookMedia, initialExpiresAt: Long) {
        @Volatile var expiresAt: Long = initialExpiresAt
        val resources = ConcurrentHashMap<String, ResourceTarget>()

        fun touch() {
            expiresAt = System.currentTimeMillis() + SESSION_TTL_MILLIS
        }
    }

    sealed interface SourceTarget {
        val headers: Map<String, String>
        data class Remote(val uri: String, override val headers: Map<String, String>) : SourceTarget
        data class Local(val file: File, override val headers: Map<String, String>) : SourceTarget
    }

    data class ResourceTarget(
        val uri: String,
        val headers: Map<String, String>,
        val accessToken: String? = null,
    )
    data class ManifestResult(val body: String, val mimeType: String)

    private fun String.looksLikePlaylist(): Boolean =
        lowercase().substringBefore('?').endsWith(".m3u8") ||
            lowercase().substringBefore('?').endsWith(".m3u")

    private val HOP_BY_HOP_HEADERS = setOf("host", "content-length", "connection", "transfer-encoding")
}
