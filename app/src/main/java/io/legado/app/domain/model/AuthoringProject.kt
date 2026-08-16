package io.legado.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class AuthoringProjectKind {
    WRITING,
    EBOOK_EDITOR,
}

@Serializable
enum class AuthoringTextAlignment {
    START,
    JUSTIFY,
    CENTER,
}

@Serializable
data class AuthoringStyle(
    val fontFamily: String = "Serif",
    val fontSizeSp: Int = 18,
    val lineHeightPercent: Int = 150,
    val paragraphIndentEm: Float = 1.5f,
    val alignment: AuthoringTextAlignment = AuthoringTextAlignment.JUSTIFY,
    val dropCap: Boolean = true,
)

@Serializable
data class AuthoringChapter(
    val id: String,
    val title: String,
    val content: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class AuthoringProject(
    val id: String,
    val kind: AuthoringProjectKind,
    val title: String,
    val author: String = "",
    val description: String = "",
    val language: String = "vi",
    val coverPath: String? = null,
    val sourceBookUrl: String? = null,
    val sourceOrigin: String? = null,
    val preproduction: WritingPreproduction = WritingPreproduction(),
    val writingWorkflow: WritingWorkflow = WritingWorkflow(),
    val document: EbookDocument? = null,
    val chapters: List<AuthoringChapter> = emptyList(),
    val style: AuthoringStyle = AuthoringStyle(),
    val createdAt: Long,
    val updatedAt: Long,
)
