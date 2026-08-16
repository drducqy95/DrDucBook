package io.legado.app.help

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.utils.FileDoc
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.listFileDocs
import java.io.File

private const val bundledVietnameseFontAssetDir = "font/vietnamese"
private val bundledVietnameseFonts = listOf(
    "BeVietnamPro-Regular.ttf",
    "Literata-Variable.ttf",
    "NotoSans-Variable.ttf",
    "NotoSerif-Variable.ttf",
)

private val supportedFontExtension = Regex("(?i)\\.(ttf|otf)$")

fun loadFontFiles(context: Context, folderUri: Uri?): List<FileDoc> {
    val fontRegex = Regex("(?i).*\\.[ot]tf")
    val bundledFontDir = ensureBundledVietnameseFonts(context)
    val bundledFonts = bundledFontDir.listFileDocs { it.name.matches(fontRegex) }
    val selectedFonts = mutableListOf<FileDoc>()
    if (folderUri != null) {
        try {
            if (folderUri.isContentScheme()) {
                val documentFile = DocumentFile.fromTreeUri(context, folderUri)
                selectedFonts += documentFile?.listFileDocs { it.name.matches(fontRegex) }
                    ?: emptyList()
            } else {
                val path = folderUri.path ?: folderUri.toString()
                val file = File(path)
                if (file.exists()) {
                    selectedFonts += file.listFileDocs { it.name.matches(fontRegex) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    } else {
        selectedFonts += bundledFonts
    }
    return (selectedFonts + bundledFonts)
        .distinctBy { it.name.lowercase() }
        .sortedBy { it.name.lowercase() }
}

/** Copies one SAF font into app-managed storage so it remains available after a restart. */
fun importFontFile(context: Context, uri: Uri): Result<FileDoc> = runCatching {
    val document = DocumentFile.fromSingleUri(context, uri)
    val originalName = document?.name
        ?.trim()
        ?.takeIf { it.matches(Regex("(?i).+\\.(ttf|otf)$")) }
        ?: error("Only TTF and OTF font files are supported")
    val safeName = originalName
        .replace(Regex("[^\\p{L}\\p{N}._ -]"), "_")
        .takeLast(120)
        .ifBlank { "ImportedFont.ttf" }
    val fileName = if (safeName in bundledVietnameseFonts) "Imported-$safeName" else safeName
    require(fileName.contains(supportedFontExtension)) {
        "Only TTF and OTF font files are supported"
    }

    val fontDir = managedFontDir(context)
    val target = File(fontDir, fileName)
    val temporary = File(fontDir, ".$fileName.importing")
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            temporary.outputStream().buffered().use(input::copyTo)
        } ?: error("Không thể mở tệp phông chữ đã chọn")
        require(temporary.length() > 0L) { "The selected font file is empty" }
        Typeface.Builder(temporary).build()
        if (target.exists() && !target.delete()) error("Không thể thay thế phông chữ đã nhập")
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        FileDoc.fromFile(target.absolutePath)
    } finally {
        temporary.delete()
    }
}

private fun ensureBundledVietnameseFonts(context: Context): File {
    val fontDir = managedFontDir(context)
    bundledVietnameseFonts.forEach { fileName ->
        runCatching {
            context.assets.open("$bundledVietnameseFontAssetDir/$fileName").use { input ->
                val target = File(fontDir, fileName)
                val expectedSize = input.available().toLong()
                if (target.isFile && target.length() == expectedSize) return@use
                val temporary = File(fontDir, "$fileName.tmp")
                temporary.outputStream().buffered().use { output -> input.copyTo(output) }
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
            }
        }
    }
    return fontDir
}

private fun managedFontDir(context: Context): File {
    val root = context.getExternalFilesDir(null) ?: context.filesDir
    return File(root, "font").apply { mkdirs() }
}
