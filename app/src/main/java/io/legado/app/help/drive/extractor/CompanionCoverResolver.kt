package io.legado.app.help.drive.extractor

object CompanionCoverResolver {

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")

    /**
     * Resolves companion cover image for a book filename from a list of files in the same folder.
     * Returns the matching image's full path or relative name if found.
     */
    fun resolveCompanionCover(
        bookFilename: String,
        allFilesInFolder: List<String>
    ): String? {
        val baseName = bookFilename.substringBeforeLast(".")
        val imageFiles = allFilesInFolder.filter { file ->
            val ext = file.substringAfterLast(".", "").lowercase()
            ext in imageExtensions
        }

        // 1. Exact base name match: [book_name].jpg / [book_name].png
        for (img in imageFiles) {
            val imgBase = img.substringBeforeLast("/").substringAfterLast("/").ifEmpty { img.substringBeforeLast(".") }
            if (img.substringBeforeLast(".").endsWith(baseName, ignoreCase = true) ||
                img.substringAfterLast("/").substringBeforeLast(".").equals(baseName, ignoreCase = true)) {
                return img
            }
        }

        // 2. Generic cover: cover.jpg, folder.jpg, poster.jpg
        val genericCover = imageFiles.firstOrNull { img ->
            val name = img.substringAfterLast("/").lowercase()
            name.startsWith("cover.") || name.startsWith("folder.") || name.startsWith("poster.")
        }

        return genericCover
    }
}
