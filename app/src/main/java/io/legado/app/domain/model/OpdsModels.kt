package io.legado.app.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

object OpdsMimeTypes {
    const val NAVIGATION = "application/atom+xml;profile=opds-catalog;kind=navigation"
    const val ACQUISITION = "application/atom+xml;profile=opds-catalog;kind=acquisition"
    const val SEARCH = "application/opensearchdescription+xml"
    const val ATOM_XML = "application/atom+xml"

    const val REL_SELF = "self"
    const val REL_START = "start"
    const val REL_UP = "up"
    const val REL_NEXT = "next"
    const val REL_SUBSECTION = "subsection"
    const val REL_SEARCH = "search"
    const val REL_ACQUISITION = "http://opds-spec.org/acquisition"
    const val REL_IMAGE = "http://opds-spec.org/image"
    const val REL_THUMBNAIL = "http://opds-spec.org/image/thumbnail"

    const val MIME_EPUB = "application/epub+zip"
    const val MIME_PDF = "application/pdf"
    const val MIME_TXT = "text/plain"
    const val MIME_MOBI = "application/x-mobipocket-ebook"
    const val MIME_CBZ = "application/vnd.comicbook+zip"
    const val MIME_CBR = "application/vnd.comicbook-rar"
    const val MIME_JPEG = "image/jpeg"
    const val MIME_PNG = "image/png"
    const val MIME_WEBP = "image/webp"
}

@Serializable
@Immutable
data class OpdsCatalog(
    val id: String,
    val title: String,
    val updated: String = "",
    val selfUrl: String = "",
    val links: List<OpdsLink> = emptyList(),
    val entries: List<OpdsEntry> = emptyList(),
)

@Serializable
@Immutable
data class OpdsEntry(
    val id: String,
    val title: String,
    val updated: String = "",
    val author: String? = null,
    val summary: String? = null,
    val content: String? = null,
    val coverUrl: String? = null,
    val thumbnailUrl: String? = null,
    val links: List<OpdsLink> = emptyList(),
    val categories: List<String> = emptyList(),
) {
    /** True if this entry represents a subfolder/category catalog navigation link */
    val isNavigation: Boolean
        get() = links.any { it.isNavigationLink }

    /** True if this entry represents a downloadable book */
    val isAcquisition: Boolean
        get() = links.any { it.isAcquisitionLink }

    val navigationUrl: String?
        get() = links.firstOrNull { it.isNavigationLink }?.href

    val acquisitionUrl: String?
        get() = links.firstOrNull { it.isAcquisitionLink }?.href

    val acquisitionFormat: String?
        get() = links.firstOrNull { it.isAcquisitionLink }?.let { link ->
            when {
                link.type.contains("epub", ignoreCase = true) -> "EPUB"
                link.type.contains("pdf", ignoreCase = true) -> "PDF"
                link.type.contains("text/plain", ignoreCase = true) -> "TXT"
                link.type.contains("mobi", ignoreCase = true) -> "MOBI"
                link.type.contains("comic", ignoreCase = true) -> "CBZ"
                else -> link.type.substringAfterLast("/").substringBefore("+").uppercase()
            }
        }
}

@Serializable
@Immutable
data class OpdsLink(
    val href: String,
    val rel: String = "",
    val type: String = "",
    val title: String? = null,
    val length: Long? = null,
) {
    val isNavigationLink: Boolean
        get() = rel == OpdsMimeTypes.REL_SUBSECTION ||
                type.contains("kind=navigation", ignoreCase = true) ||
                (type.contains("application/atom+xml", ignoreCase = true) && !isAcquisitionLink)

    val isAcquisitionLink: Boolean
        get() = rel.startsWith(OpdsMimeTypes.REL_ACQUISITION) ||
                type.contains("epub", ignoreCase = true) ||
                type.contains("pdf", ignoreCase = true) ||
                type.contains("text/plain", ignoreCase = true) ||
                type.contains("mobipocket", ignoreCase = true) ||
                type.contains("comicbook", ignoreCase = true)

    val isCoverImage: Boolean
        get() = rel == OpdsMimeTypes.REL_IMAGE || rel == OpdsMimeTypes.REL_THUMBNAIL

    val isSearch: Boolean
        get() = rel == OpdsMimeTypes.REL_SEARCH ||
                type.contains("opensearchdescription", ignoreCase = true)
}
