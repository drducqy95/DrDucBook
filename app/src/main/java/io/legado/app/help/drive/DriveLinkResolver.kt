package io.legado.app.help.drive

import io.legado.app.domain.model.DriveSourceType
import io.legado.app.domain.model.ManagedDriveSource
import io.legado.app.utils.MD5Utils
import java.net.URI

data class ResolvedDriveLink(
    val type: DriveSourceType,
    val targetId: String,
    val normalizedUrl: String,
    val suggestedName: String,
    val isFolder: Boolean
)

object DriveLinkResolver {

    private val gDriveFolderRegex = Regex("""/drive/(?:u/\d+/)?folders/([a-zA-Z0-9_-]+)""")
    private val gDriveFileRegex = Regex("""/(?:file/d/|open\?id=|uc\?id=)([a-zA-Z0-9_-]+)""")
    private val oneDriveRegex = Regex("""(?:1drv\.ms|onedrive\.live\.com)""")
    private val dropboxRegex = Regex("""dropbox\.com/(?:s|scl)/(?:fi|fo)/([a-zA-Z0-9_-]+)""")

    fun resolve(rawUrl: String): Result<ResolvedDriveLink> = runCatching {
        val trimmed = rawUrl.trim()
        require(trimmed.isNotBlank()) { "URL cannot be blank" }

        val uri = URI.create(trimmed)
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path.orEmpty()

        when {
            host.contains("drive.google.com") || host.contains("docs.google.com") -> {
                val folderMatch = gDriveFolderRegex.find(path)
                if (folderMatch != null) {
                    val folderId = folderMatch.groupValues[1]
                    return@runCatching ResolvedDriveLink(
                        type = DriveSourceType.GOOGLE_DRIVE_PUBLIC,
                        targetId = folderId,
                        normalizedUrl = "https://drive.google.com/drive/folders/$folderId",
                        suggestedName = "Google Drive ($folderId)",
                        isFolder = true
                    )
                }

                val fileMatch = gDriveFileRegex.find(trimmed)
                if (fileMatch != null) {
                    val fileId = fileMatch.groupValues[1]
                    return@runCatching ResolvedDriveLink(
                        type = DriveSourceType.GOOGLE_DRIVE_PUBLIC,
                        targetId = fileId,
                        normalizedUrl = "https://drive.google.com" + "/file/d/$fileId/view",
                        suggestedName = "Google Drive File ($fileId)",
                        isFolder = false
                    )
                }

                throw IllegalArgumentException("Unsupported Google Drive link format")
            }

            oneDriveRegex.containsMatchIn(host) -> {
                ResolvedDriveLink(
                    type = DriveSourceType.ONEDRIVE_PUBLIC,
                    targetId = trimmed,
                    normalizedUrl = trimmed,
                    suggestedName = "OneDrive Cloud",
                    isFolder = true
                )
            }

            dropboxRegex.containsMatchIn(trimmed) -> {
                val isFolder = trimmed.contains("/fo/")
                ResolvedDriveLink(
                    type = DriveSourceType.DROPBOX_PUBLIC,
                    targetId = trimmed,
                    normalizedUrl = trimmed,
                    suggestedName = if (isFolder) "Dropbox Folder" else "Dropbox File",
                    isFolder = isFolder
                )
            }

            uri.scheme in listOf("http", "https") -> {
                val isDir = path.endsWith("/") || path.isBlank()
                val segment = path.trimEnd('/').split('/').lastOrNull()?.takeIf { it.isNotBlank() } ?: host
                ResolvedDriveLink(
                    type = DriveSourceType.HTTP_INDEX,
                    targetId = trimmed,
                    normalizedUrl = trimmed,
                    suggestedName = "HTTP ($segment)",
                    isFolder = isDir
                )
            }

            else -> throw IllegalArgumentException("Unrecognized cloud storage URL: $rawUrl")
        }
    }

    fun createManagedSource(
        resolved: ResolvedDriveLink,
        customName: String = ""
    ): ManagedDriveSource {
        val name = customName.trim().ifEmpty { resolved.suggestedName }
        val id = MD5Utils.md5Encode("${resolved.type}_${resolved.targetId}")
        return ManagedDriveSource(
            id = id,
            name = name,
            type = resolved.type,
            rootFolderId = if (resolved.type == DriveSourceType.GOOGLE_DRIVE_PUBLIC && resolved.isFolder) resolved.targetId else "",
            publicUrl = resolved.normalizedUrl,
        )
    }
}
