package io.legado.app.help.media

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URI
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.ceil

object MediaDownloadTransferPolicy {

    data class HlsByteRange(
        val offset: Long,
        val length: Long,
    ) {
        val endInclusive: Long
            get() = offset + length - 1L

        fun toHttpRangeHeader(): String = "bytes=$offset-$endInclusive"
    }

    data class HlsDownloadSegment(
        val url: String,
        val keyUrl: String?,
        val iv: ByteArray?,
        val byteRange: HlsByteRange? = null,
        val discontinuityBefore: Boolean = false,
        val initMap: Boolean = false,
    )

    data class HlsVariant(
        val url: String,
        val bandwidth: Long?,
        val width: Int?,
        val height: Int?,
    )

    data class DashDownloadSegment(
        val url: String,
        val byteRange: HlsByteRange? = null,
        val initSegment: Boolean = false,
    )

    data class DashDownloadPlan(
        val segments: List<DashDownloadSegment>,
        val mimeType: String?,
        val extension: String,
    )

    data class DirectTransferPlan(
        val append: Boolean,
        val initialBytes: Long,
    )

    fun directTransferPlan(existingBytes: Long, responseCode: Int): DirectTransferPlan {
        val append = existingBytes > 0L && responseCode == 206
        return DirectTransferPlan(
            append = append,
            initialBytes = if (append) existingBytes else 0L,
        )
    }

    fun contentRangeTotal(value: String?): Long? = value
        ?.substringAfterLast('/', missingDelimiterValue = "")
        ?.toLongOrNull()

    fun resumeIdentityChanged(
        existingBytes: Long,
        storedEtag: String?,
        responseEtag: String?,
        storedLastModified: String?,
        responseLastModified: String?,
        storedContentLength: Long,
        responseContentLength: Long,
    ): Boolean {
        if (existingBytes <= 0L) return false
        val etagChanged = !storedEtag.isNullOrBlank() && !responseEtag.isNullOrBlank() &&
            storedEtag != responseEtag
        val lastModifiedChanged = !storedLastModified.isNullOrBlank() &&
            !responseLastModified.isNullOrBlank() && storedLastModified != responseLastModified
        val lengthChanged = storedContentLength > 0L && responseContentLength > 0L &&
            storedContentLength != responseContentLength
        return etagChanged || lastModifiedChanged || lengthChanged
    }

    fun parseHlsSegments(baseUrl: String, lines: List<String>): List<String> {
        val result = mutableListOf<String>()
        lines.forEach { line ->
            if (line.startsWith("#EXT-X-MAP")) {
                Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.getOrNull(1)?.let {
                    result += URI(baseUrl).resolve(it).toString()
                }
            } else if (!line.startsWith('#')) {
                result += URI(baseUrl).resolve(line).toString()
            }
        }
        return result
    }

    fun parseHlsMasterVariants(baseUrl: String, lines: List<String>): List<HlsVariant> {
        val variants = mutableListOf<HlsVariant>()
        var pendingStreamInf: String? = null
        lines.forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#EXT-X-STREAM-INF:") -> pendingStreamInf = line
                pendingStreamInf != null && line.isNotBlank() && !line.startsWith('#') -> {
                    val streamInf = pendingStreamInf.orEmpty()
                    val resolution = attribute(streamInf, "RESOLUTION")
                    val width = resolution?.substringBefore('x')?.toIntOrNull()
                    val height = resolution?.substringAfter('x', missingDelimiterValue = "")?.toIntOrNull()
                    variants += HlsVariant(
                        url = URI(baseUrl).resolve(line).toString(),
                        bandwidth = attribute(streamInf, "BANDWIDTH")?.toLongOrNull(),
                        width = width,
                        height = height,
                    )
                    pendingStreamInf = null
                }
                line.isNotBlank() && !line.startsWith('#') -> pendingStreamInf = null
            }
        }
        return variants
    }

    fun selectBestHlsVariant(baseUrl: String, lines: List<String>): HlsVariant? =
        parseHlsMasterVariants(baseUrl, lines).maxWithOrNull(
            compareBy<HlsVariant>(
                { it.bandwidth ?: -1L },
                { it.height ?: -1 },
                { it.width ?: -1 },
            )
        )

    fun parseDashDownloadPlan(manifestUrl: String, manifestXml: String): DashDownloadPlan {
        val mpd = parseXmlRoot(manifestXml)
        val mpdDurationMs = parseXsDurationMillis(mpd.getAttribute("mediaPresentationDuration"))
        val mpdBase = resolveDashBase(manifestUrl, mpd)
        val candidates = mutableListOf<DashRepresentationCandidate>()
        val periods = mpd.childElements("Period").ifEmpty { listOf(mpd) }
        periods.forEach { period ->
            val periodBase = resolveDashBase(mpdBase.url, period)
            period.childElements("AdaptationSet").forEach { adaptation ->
                val adaptationBase = resolveDashBase(periodBase.url, adaptation)
                val adaptationMime = adaptation.getAttribute("mimeType").ifBlank {
                    adaptation.getAttribute("contentType")
                }
                val adaptationTemplate = adaptation.childElements("SegmentTemplate").firstOrNull()
                val adaptationList = adaptation.childElements("SegmentList").firstOrNull()
                adaptation.childElements("Representation").forEach { representation ->
                    val representationBase = resolveDashBase(adaptationBase.url, representation)
                    val representationMime = representation.getAttribute("mimeType").ifBlank { adaptationMime }
                    val representationId = representation.getAttribute("id").ifBlank { null }
                    val bandwidth = representation.getAttribute("bandwidth").toLongOrNull()
                    val template = representation.childElements("SegmentTemplate").firstOrNull()
                        ?: adaptationTemplate
                    val list = representation.childElements("SegmentList").firstOrNull()
                        ?: adaptationList
                    val segments = when {
                        template != null -> buildDashTemplateSegments(
                            baseUrl = representationBase.url,
                            representationId = representationId,
                            bandwidth = bandwidth,
                            template = template,
                            mpdDurationMs = mpdDurationMs,
                        )
                        list != null -> buildDashListSegments(representationBase.url, list)
                        representationBase.explicit -> listOf(DashDownloadSegment(representationBase.url))
                        adaptationBase.explicit -> listOf(DashDownloadSegment(adaptationBase.url))
                        mpdBase.explicit -> listOf(DashDownloadSegment(mpdBase.url))
                        else -> emptyList()
                    }
                    if (segments.isNotEmpty()) {
                        candidates += DashRepresentationCandidate(
                            segments = segments,
                            mimeType = representationMime.ifBlank { null },
                            bandwidth = bandwidth,
                            width = representation.getAttribute("width").toIntOrNull()
                                ?: adaptation.getAttribute("width").toIntOrNull(),
                            height = representation.getAttribute("height").toIntOrNull()
                                ?: adaptation.getAttribute("height").toIntOrNull(),
                        )
                    }
                }
            }
        }
        val selected = candidates.maxWithOrNull(
            compareBy<DashRepresentationCandidate>(
                { it.mimeScore },
                { it.bandwidth ?: -1L },
                { it.height ?: -1 },
                { it.width ?: -1 },
            )
        ) ?: error("DASH manifest has no downloadable representation")
        return DashDownloadPlan(
            segments = selected.segments,
            mimeType = selected.mimeType,
            extension = extensionForDash(selected.mimeType, selected.segments),
        )
    }

    fun parseHlsDownloadSegments(baseUrl: String, lines: List<String>): List<HlsDownloadSegment> {
        val result = mutableListOf<HlsDownloadSegment>()
        var sequence = lines.firstOrNull { it.startsWith("#EXT-X-MEDIA-SEQUENCE:") }
            ?.substringAfter(':')?.trim()?.toLongOrNull() ?: 0L
        var keyUrl: String? = null
        var explicitIv: ByteArray? = null
        var pendingByteRange: PendingByteRange? = null
        var discontinuityBeforeNextMedia = false
        val byteRangeEndByUrl = mutableMapOf<String, Long>()
        lines.forEach { line ->
            when {
                line.startsWith("#EXT-X-KEY:") -> {
                    val method = attribute(line, "METHOD").orEmpty()
                    if (method.equals("NONE", ignoreCase = true)) {
                        keyUrl = null
                        explicitIv = null
                    } else if (method.equals("AES-128", ignoreCase = true)) {
                        keyUrl = attribute(line, "URI")?.let { URI(baseUrl).resolve(it).toString() }
                        explicitIv = attribute(line, "IV")?.let(::parseIv)
                    }
                }
                line.startsWith("#EXT-X-BYTERANGE:") -> {
                    pendingByteRange = parsePendingByteRange(line.substringAfter(':').trim())
                }
                line.startsWith("#EXT-X-DISCONTINUITY") -> {
                    discontinuityBeforeNextMedia = true
                }
                line.startsWith("#EXT-X-MAP") -> attribute(line, "URI")?.let { uri ->
                    val url = URI(baseUrl).resolve(uri).toString()
                    val mapByteRange = parsePendingByteRange(attribute(line, "BYTERANGE"))
                    result += HlsDownloadSegment(
                        url = url,
                        keyUrl = keyUrl,
                        iv = explicitIv ?: keyUrl?.let { sequenceIv(sequence) },
                        byteRange = mapByteRange?.toByteRange(url, byteRangeEndByUrl),
                        initMap = true,
                    )
                }
                !line.startsWith('#') -> {
                    val url = URI(baseUrl).resolve(line).toString()
                    result += HlsDownloadSegment(
                        url = url,
                        keyUrl = keyUrl,
                        iv = explicitIv ?: keyUrl?.let { sequenceIv(sequence) },
                        byteRange = pendingByteRange?.toByteRange(url, byteRangeEndByUrl),
                        discontinuityBefore = discontinuityBeforeNextMedia,
                    )
                    pendingByteRange = null
                    discontinuityBeforeNextMedia = false
                    sequence++
                }
            }
        }
        return result
    }

    private fun parseXmlRoot(xml: String): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        return factory.newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))
            .documentElement
    }

    private fun buildDashListSegments(baseUrl: String, list: Element): List<DashDownloadSegment> {
        val result = mutableListOf<DashDownloadSegment>()
        list.childElements("Initialization").firstOrNull()?.let { init ->
            init.getAttribute("sourceURL").takeIf(String::isNotBlank)?.let { source ->
                result += DashDownloadSegment(
                    url = URI(baseUrl).resolve(source).toString(),
                    byteRange = parseDashRange(init.getAttribute("range")),
                    initSegment = true,
                )
            }
        }
        list.childElements("SegmentURL").forEach { segment ->
            segment.getAttribute("media").takeIf(String::isNotBlank)?.let { media ->
                result += DashDownloadSegment(
                    url = URI(baseUrl).resolve(media).toString(),
                    byteRange = parseDashRange(segment.getAttribute("mediaRange")),
                )
            }
        }
        return result
    }

    private fun buildDashTemplateSegments(
        baseUrl: String,
        representationId: String?,
        bandwidth: Long?,
        template: Element,
        mpdDurationMs: Long?,
    ): List<DashDownloadSegment> {
        val media = template.getAttribute("media").takeIf(String::isNotBlank) ?: return emptyList()
        val startNumber = template.getAttribute("startNumber").toLongOrNull() ?: 1L
        val result = mutableListOf<DashDownloadSegment>()
        template.getAttribute("initialization").takeIf(String::isNotBlank)?.let { init ->
            result += DashDownloadSegment(
                url = URI(baseUrl).resolve(
                    substituteDashTemplate(init, representationId, startNumber, bandwidth, null)
                ).toString(),
                initSegment = true,
            )
        }
        val timeline = template.childElements("SegmentTimeline").firstOrNull()
        if (timeline != null) {
            var number = startNumber
            var time: Long? = null
            timeline.childElements("S").forEach { s ->
                val duration = s.getAttribute("d").toLongOrNull() ?: return@forEach
                val repeatCount = s.getAttribute("r").toIntOrNull()?.coerceAtLeast(0) ?: 0
                time = s.getAttribute("t").toLongOrNull() ?: time
                repeat(repeatCount + 1) {
                    result += DashDownloadSegment(
                        url = URI(baseUrl).resolve(
                            substituteDashTemplate(media, representationId, number, bandwidth, time)
                        ).toString(),
                    )
                    number++
                    time = time?.plus(duration)
                }
            }
            return result
        }
        val duration = template.getAttribute("duration").toLongOrNull() ?: return result
        val timescale = template.getAttribute("timescale").toLongOrNull()?.takeIf { it > 0L } ?: 1L
        val segmentDurationMs = duration * 1_000.0 / timescale
        val count = mpdDurationMs
            ?.takeIf { it > 0L && segmentDurationMs > 0.0 }
            ?.let { ceil(it / segmentDurationMs).toInt().coerceAtLeast(1) }
            ?: 1
        repeat(count) { offset ->
            val number = startNumber + offset
            result += DashDownloadSegment(
                url = URI(baseUrl).resolve(
                    substituteDashTemplate(media, representationId, number, bandwidth, null)
                ).toString(),
            )
        }
        return result
    }

    private fun substituteDashTemplate(
        value: String,
        representationId: String?,
        number: Long,
        bandwidth: Long?,
        time: Long?,
    ): String {
        var result = value
        Regex("""\${'$'}Number%0(\d+)d\${'$'}""").findAll(result).forEach { match ->
            val width = match.groupValues[1].toIntOrNull()?.coerceIn(1, 18) ?: 1
            result = result.replace(match.value, number.toString().padStart(width, '0'))
        }
        return result
            .replace("${'$'}RepresentationID${'$'}", representationId.orEmpty())
            .replace("${'$'}Number${'$'}", number.toString())
            .replace("${'$'}Bandwidth${'$'}", bandwidth?.toString().orEmpty())
            .replace("${'$'}Time${'$'}", time?.toString().orEmpty())
    }

    private fun parseDashRange(value: String?): HlsByteRange? {
        if (value.isNullOrBlank()) return null
        val start = value.substringBefore('-').trim().toLongOrNull() ?: return null
        val end = value.substringAfter('-', missingDelimiterValue = "").trim().toLongOrNull() ?: return null
        if (end < start) return null
        return HlsByteRange(offset = start, length = end - start + 1L)
    }

    private fun resolveDashBase(parentUrl: String, element: Element): DashResolvedBase {
        val base = element.childElements("BaseURL").firstOrNull()
            ?.textContent
            ?.trim()
            ?.takeIf(String::isNotBlank)
        return if (base == null) {
            DashResolvedBase(parentUrl, explicit = false)
        } else {
            DashResolvedBase(URI(parentUrl).resolve(base).toString(), explicit = true)
        }
    }

    private fun Element.childElements(name: String): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node.nodeType == Node.ELEMENT_NODE && node.nodeNameMatches(name)) {
                result += node as Element
            }
        }
        return result
    }

    private fun Node.nodeNameMatches(name: String): Boolean {
        val local = localName ?: nodeName.substringAfter(':')
        return local == name
    }

    private fun parseXsDurationMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val match = Regex("""P(?:([0-9.]+)D)?(?:T(?:([0-9.]+)H)?(?:([0-9.]+)M)?(?:([0-9.]+)S)?)?""")
            .matchEntire(value.trim())
            ?: return null
        val days = match.groupValues[1].toDoubleOrNull() ?: 0.0
        val hours = match.groupValues[2].toDoubleOrNull() ?: 0.0
        val minutes = match.groupValues[3].toDoubleOrNull() ?: 0.0
        val seconds = match.groupValues[4].toDoubleOrNull() ?: 0.0
        return ((days * 86_400.0 + hours * 3_600.0 + minutes * 60.0 + seconds) * 1_000.0).toLong()
    }

    private fun extensionForDash(mimeType: String?, segments: List<DashDownloadSegment>): String {
        val firstPath = segments.firstOrNull()?.url.orEmpty().substringBefore('?').lowercase()
        return when {
            mimeType?.contains("audio", ignoreCase = true) == true -> "m4a"
            firstPath.endsWith(".m4a") -> "m4a"
            firstPath.endsWith(".webm") -> "webm"
            else -> "mp4"
        }
    }

    private fun attribute(line: String, name: String): String? =
        Regex("(?:^|,)${Regex.escape(name)}=(?:\"([^\"]*)\"|([^,]*))")
            .find(line.substringAfter(':'))
            ?.let { it.groupValues[1].ifBlank { it.groupValues[2] }.trim() }

    private fun parsePendingByteRange(value: String?): PendingByteRange? {
        if (value.isNullOrBlank()) return null
        val normalized = value.trim().trim('"')
        val length = normalized.substringBefore('@').trim().toLongOrNull() ?: return null
        if (length <= 0L) return null
        val offset = normalized.substringAfter('@', missingDelimiterValue = "")
            .trim()
            .takeIf(String::isNotBlank)
            ?.toLongOrNull()
        return PendingByteRange(length = length, offset = offset)
    }

    private fun parseIv(value: String): ByteArray {
        val hex = value.removePrefix("0x").removePrefix("0X").padStart(32, '0').takeLast(32)
        return ByteArray(16) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }

    private fun sequenceIv(sequence: Long): ByteArray = ByteArray(16).also { bytes ->
        var value = sequence
        for (index in 15 downTo 0) {
            bytes[index] = (value and 0xff).toByte()
            value = value ushr 8
        }
    }

    private data class PendingByteRange(
        val length: Long,
        val offset: Long?,
    ) {
        fun toByteRange(url: String, rangeEndByUrl: MutableMap<String, Long>): HlsByteRange {
            val resolvedOffset = offset ?: rangeEndByUrl[url] ?: 0L
            rangeEndByUrl[url] = resolvedOffset + length
            return HlsByteRange(
                offset = resolvedOffset,
                length = length,
            )
        }
    }

    private data class DashResolvedBase(
        val url: String,
        val explicit: Boolean,
    )

    private data class DashRepresentationCandidate(
        val segments: List<DashDownloadSegment>,
        val mimeType: String?,
        val bandwidth: Long?,
        val width: Int?,
        val height: Int?,
    ) {
        val mimeScore: Int
            get() = when {
                mimeType?.contains("video", ignoreCase = true) == true -> 2
                mimeType?.contains("audio", ignoreCase = true) == true -> 1
                else -> 0
            }
    }
}
