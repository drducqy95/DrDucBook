package io.legado.app.utils

import androidx.documentfile.provider.DocumentFile

@Suppress("MemberVisibilityCanBePrivate")
object DocumentUtils {

    fun exists(root: DocumentFile, fileName: String, vararg subDirs: String): Boolean {
        val parent = getDirDocument(root, *subDirs) ?: return false
        return parent.findFile(fileName)?.exists() ?: false
    }

    fun delete(root: DocumentFile, fileName: String, vararg subDirs: String) {
        val parent: DocumentFile? = createFolderIfNotExist(root, *subDirs)
        parent?.findFile(fileName)?.delete()
    }

    fun createFileIfNotExist(
        root: DocumentFile,
        fileName: String,
        vararg subDirs: String
    ): DocumentFile? {
        val parent: DocumentFile? = createFolderIfNotExist(root, *subDirs)
        return parent?.findFile(fileName) ?: parent?.createFile(mimeType(fileName), fileName)
    }

    fun createFolderIfNotExist(root: DocumentFile, vararg subDirs: String): DocumentFile? {
        var parent: DocumentFile? = root
        for (subDirName in subDirs) {
            val subDir = parent?.findFile(subDirName)
                ?: parent?.createDirectory(subDirName)
            parent = subDir
        }
        return parent
    }

    fun getDirDocument(root: DocumentFile, vararg subDirs: String): DocumentFile? {
        var parent = root
        for (subDirName in subDirs) {
            val subDir = parent.findFile(subDirName)
            parent = subDir ?: return null
        }
        return parent
    }

    private fun mimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "epub" -> "application/epub+zip"
            "pdf" -> "application/pdf"
            "html", "htm" -> "text/html"
            "txt" -> "text/plain"
            "cbz" -> "application/vnd.comicbook+zip"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

}
