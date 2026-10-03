package io.legado.app.help.drive

import io.legado.app.domain.model.OpdsCatalog
import io.legado.app.ui.drive.DriveCatalogItem

object OpdsDriveAdapter {

    fun toDriveCatalogItems(
        catalog: OpdsCatalog,
        importedPaths: Set<String> = emptySet(),
    ): List<DriveCatalogItem> {
        return catalog.entries.map { entry ->
            val isDir = entry.isNavigation
            val itemPath = if (isDir) {
                entry.navigationUrl ?: entry.id
            } else {
                entry.acquisitionUrl ?: entry.id
            }
            val format = entry.acquisitionFormat.orEmpty()
            val fileName = if (isDir) entry.title else {
                val ext = format.lowercase()
                if (ext.isNotBlank() && !entry.title.endsWith(".$ext", ignoreCase = true)) {
                    "${entry.title}.$ext"
                } else entry.title
            }

            DriveCatalogItem(
                name = fileName,
                path = itemPath,
                isDir = isDir,
                title = entry.title,
                author = entry.author,
                intro = entry.summary ?: entry.content,
                coverUrl = entry.coverUrl ?: entry.thumbnailUrl,
                format = format,
                isImported = importedPaths.contains(itemPath) || importedPaths.contains(fileName),
            )
        }.sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
    }
}
