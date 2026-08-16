package io.legado.app.service.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import io.legado.app.domain.model.EbookBlock
import io.legado.app.domain.model.EbookDividerBlock
import io.legado.app.domain.model.EbookDividerType
import io.legado.app.domain.model.EbookDocumentChapter
import io.legado.app.domain.model.EbookHeadingBlock
import io.legado.app.domain.model.EbookImageBlock
import io.legado.app.domain.model.EbookPageBreakBlock
import io.legado.app.domain.model.EbookParagraphBlock
import io.legado.app.domain.model.blockPlainText
import io.legado.app.utils.FileDoc
import io.legado.app.utils.createFileIfNotExist
import io.legado.app.utils.delete
import io.legado.app.utils.find
import io.legado.app.utils.openOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.Writer
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

class EbookExportWriter(
    private val outputDirectory: FileDoc,
    private val charset: Charset,
    private val imageOptimization: EbookExportImageOptimization = EbookExportImageOptimization.ORIGINAL,
    private val onProgress: (Int, Int) -> Unit = { _, _ -> },
) {
    fun write(payload: EbookExportPayload, format: EbookExportFormat, fileName: String): FileDoc {
        outputDirectory.asFile()?.let { directory ->
            directory.mkdirs()
            val target = File(directory, fileName)
            val temporary = File(directory, ".$fileName.tmp")
            FileOutputStream(temporary).buffered().use { output ->
                writePayload(payload, format, output)
            }
            require(temporary.isFile && temporary.length() > 0L) { "Export produced an empty file" }
            runCatching {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }.getOrElse {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            return FileDoc.fromFile(target)
        }
        outputDirectory.find(fileName)?.delete()
        val target = outputDirectory.createFileIfNotExist(fileName)
        target.openOutputStream().getOrThrow().buffered().use { output ->
            writePayload(payload, format, output)
        }
        return target
    }

    private fun writePayload(payload: EbookExportPayload, format: EbookExportFormat, output: OutputStream) {
        when (format) {
            EbookExportFormat.TXT -> writeTxt(payload, output)
            EbookExportFormat.HTML -> writeHtml(payload, output)
            EbookExportFormat.EPUB3 -> writeEpub3(payload, output)
            EbookExportFormat.PDF -> writePdf(payload, output)
            EbookExportFormat.CBZ -> writeCbz(payload, output)
            EbookExportFormat.EPUB2 -> error("EPUB2 uses the legacy writer")
        }
    }

    private fun writeTxt(payload: EbookExportPayload, output: OutputStream) {
        output.bufferedWriter(charset).use { writer ->
            writer.line(payload.title)
            writer.line("${payload.labels.author}: ${payload.author}")
            writer.line()
            writer.line(payload.labels.introduction)
            writer.line(payload.intro)
            payload.chapters.forEachIndexed { index, chapter ->
                writer.line()
                writer.line()
                writer.line(chapter.title)
                writer.line()
                writer.line(chapter.plainText)
                onProgress(index + 1, payload.chapters.size)
            }
        }
    }

    private fun writeHtml(payload: EbookExportPayload, output: OutputStream) {
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.append("<!doctype html><html lang=\"")
                .append(xml(payload.language)).append("\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>").append(xml(payload.title)).append("</title>")
                .append("<style>")
                .append(STYLE).append(payload.layoutCss)
                .append("</style></head><body><main>")
                .append("<section class=\"intro\"><h1>").append(xml(payload.title))
                .append("</h1><p><b>").append(xml(payload.labels.author)).append(":</b> ")
                .append(xml(payload.author))
                .append("</p><h2>").append(xml(payload.labels.introduction)).append("</h2><p>")
                .append(xml(normalizeLineBreaks(payload.intro)).replace("\n", "<br>"))
                .append("</p></section>")
            payload.chapters.forEachIndexed { index, chapter ->
                writer.append("<article id=\"chapter-").append(index.toString()).append("\"><h2>")
                    .append(xml(chapter.title)).append("</h2>")
                    .append(embedHtmlImages(chapter, payload.imageOptimization))
                    .append("</article>")
                onProgress(index + 1, payload.chapters.size)
            }
            writer.append("</main></body></html>")
        }
    }

    private fun writeEpub3(payload: EbookExportPayload, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            val mime = "application/epub+zip".toByteArray()
            val crc = CRC32().apply { update(mime) }
            zip.putNextEntry(zipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mime.size.toLong()
                compressedSize = mime.size.toLong()
                this.crc = crc.value
            })
            zip.write(mime)
            zip.closeEntry()
            zip.textEntry("META-INF/container.xml", CONTAINER_XML)
            zip.textEntry("OEBPS/styles.css", STYLE + payload.layoutCss)
            zip.textEntry("OEBPS/intro.xhtml", xhtmlPage(payload.language, payload.title, buildString {
                append("<h1>").append(xml(payload.title)).append("</h1>")
                append("<p><b>").append(xml(payload.labels.author)).append(":</b> ")
                    .append(xml(payload.author)).append("</p>")
                append("<h2>").append(xml(payload.labels.introduction)).append("</h2><p>")
                append(xml(normalizeLineBreaks(payload.intro)).replace("\n", "<br/>")).append("</p>")
            }, styleHref = "styles.css", viewport = payload.viewport()))
            val imageNames = linkedSetOf<String>()
            val imageNamesBySource = linkedMapOf<String, String>()
            var coverImageName: String? = null
            payload.cover?.takeIf { it.isFile }?.let { cover ->
                val name = buildCoverImageName(cover)
                coverImageName = name
                zip.fileEntry("OEBPS/Images/$name", cover)
                imageNames += name
            }
            payload.chapters.forEachIndexed { index, chapter ->
                val body = epubHtmlImages(
                    chapter,
                    zip,
                    imageNames,
                    imageNamesBySource,
                    payload.imageOptimization,
                )
                zip.textEntry(
                    "OEBPS/Text/chapter_$index.xhtml",
                    xhtmlPage(
                        payload.language,
                        chapter.title,
                        "<h2>${xml(chapter.title)}</h2>$body",
                        styleHref = "../styles.css",
                        viewport = payload.viewport(),
                    ),
                )
                onProgress(index + 1, payload.chapters.size)
            }
            zip.textEntry("OEBPS/nav.xhtml", buildNavigation(payload))
            zip.textEntry("OEBPS/content.opf", buildPackage(payload, imageNames, coverImageName))
        }
    }

    private fun writePdf(payload: EbookExportPayload, output: OutputStream) {
        val document = PdfDocument()
        try {
            var pageNumber = 1
            pageNumber = appendPdfText(
                document,
                pageNumber,
                payload.title,
                "${payload.labels.author}: ${payload.author}\n\n" +
                    "${payload.labels.introduction}\n${payload.intro}",
            )
            payload.chapters.forEachIndexed { index, chapter ->
                val fixedChapter = chapter.documentChapter
                pageNumber = if (
                    payload.layoutMode == "FIXED_PAGE" &&
                    fixedChapter != null &&
                    payload.viewportWidth != null &&
                    payload.viewportHeight != null
                ) {
                    appendFixedPdfChapter(
                        document = document,
                        firstPage = pageNumber,
                        chapter = fixedChapter,
                        viewportWidth = payload.viewportWidth,
                        viewportHeight = payload.viewportHeight,
                    )
                } else {
                    appendPdfText(document, pageNumber, chapter.title, chapter.plainText)
                }
                onProgress(index + 1, payload.chapters.size)
            }
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    private fun writeCbz(payload: EbookExportPayload, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            var page = 1
            page = appendCbzTextPages(
                zip,
                page,
                payload.title,
                "${payload.labels.author}: ${payload.author}\n\n" +
                    "${payload.labels.introduction}\n${payload.intro}",
            )
            payload.chapters.forEachIndexed { index, chapter ->
                if (chapter.images.isNotEmpty()) {
                    chapter.images.forEach { image ->
                        if (image.file.isFile) {
                            zip.fileEntryOptimized(
                                "${page.toString().padStart(6, '0')}-${safeName(image.fileName)}",
                                image.file,
                                payload.imageOptimization,
                            )
                            page++
                        }
                    }
                } else {
                    page = appendCbzTextPages(zip, page, chapter.title, chapter.plainText)
                }
                onProgress(index + 1, payload.chapters.size)
            }
        }
    }

    private fun appendPdfText(
        document: PdfDocument,
        firstPage: Int,
        title: String,
        content: String,
    ): Int {
        var pageNumber = firstPage
        paginateText(title, content) { layout ->
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            )
            page.canvas.drawColor(Color.WHITE)
            page.canvas.save()
            page.canvas.translate(PAGE_MARGIN.toFloat(), PAGE_MARGIN.toFloat())
            layout.draw(page.canvas)
            page.canvas.restore()
            document.finishPage(page)
            pageNumber++
        }
        return pageNumber
    }

    private fun appendFixedPdfChapter(
        document: PdfDocument,
        firstPage: Int,
        chapter: EbookDocumentChapter,
        viewportWidth: Float,
        viewportHeight: Float,
    ): Int {
        val width = viewportWidth.roundToInt().coerceAtLeast(1)
        val height = viewportHeight.roundToInt().coerceAtLeast(1)
        val maxPage = chapter.blocks.maxOfOrNull { it.geometry?.page ?: 0 }?.coerceAtLeast(0) ?: 0
        var pageNumber = firstPage
        for (fixedPage in 0..maxPage) {
            val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
            page.canvas.drawColor(Color.WHITE)
            chapter.blocks
                .asSequence()
                .filter { it.geometry?.page == fixedPage && it.geometry?.isHidden != true }
                .sortedBy { it.geometry?.zIndex ?: it.readingOrder }
                .forEach { block -> drawFixedBlock(page.canvas, block) }
            document.finishPage(page)
            pageNumber++
        }
        return pageNumber
    }

    private fun drawFixedBlock(canvas: Canvas, block: EbookBlock) {
        val geometry = block.geometry ?: return
        val bounds = RectF(
            geometry.x,
            geometry.y,
            geometry.x + geometry.width,
            geometry.y + geometry.height,
        )
        canvas.save()
        canvas.rotate(geometry.rotation, bounds.centerX(), bounds.centerY())
        geometry.backgroundColor?.let { value ->
            runCatching { Color.parseColor(value) }.getOrNull()?.let { color ->
                canvas.drawRect(bounds, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
            }
        }
        val content = RectF(
            bounds.left + geometry.padding.left,
            bounds.top + geometry.padding.top,
            bounds.right - geometry.padding.right,
            bounds.bottom - geometry.padding.bottom,
        )
        canvas.clipRect(content)
        when (block) {
            is EbookImageBlock -> drawFixedImage(canvas, block, content)
            is EbookDividerBlock -> if (block.type != EbookDividerType.SPACE) {
                canvas.drawLine(
                    content.left,
                    content.centerY(),
                    content.right,
                    content.centerY(),
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        strokeWidth = if (block.type == EbookDividerType.ORNAMENT) 3f else 1f
                    },
                )
            }
            is EbookPageBreakBlock -> Unit
            else -> drawFixedText(canvas, block, content)
        }
        canvas.restore()
    }

    private fun drawFixedImage(canvas: Canvas, block: EbookImageBlock, bounds: RectF) {
        val path = block.uri.removePrefix("file://")
        val bitmap = BitmapFactory.decodeFile(path) ?: return
        try {
            canvas.drawBitmap(bitmap, null, bounds, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawFixedText(canvas: Canvas, block: EbookBlock, bounds: RectF) {
        val text = blockPlainText(block).trim()
        if (text.isEmpty() || bounds.width() < 1f || bounds.height() < 1f) return
        val paragraphStyle = (block as? EbookParagraphBlock)?.style
        val style = when {
            paragraphStyle?.let { it.bold && it.italic } == true -> Typeface.BOLD_ITALIC
            paragraphStyle?.bold == true -> Typeface.BOLD
            paragraphStyle?.italic == true -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = when (block) {
                is EbookHeadingBlock -> (34f - block.level.coerceIn(1, 6) * 2f).coerceAtLeast(20f)
                else -> 18f
            }
            typeface = Typeface.create(Typeface.SERIF, style)
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, bounds.width().roundToInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(2f, 1.1f)
            .setIncludePad(false)
            .build()
        canvas.save()
        canvas.translate(bounds.left, bounds.top)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun appendCbzTextPages(
        zip: ZipOutputStream,
        firstPage: Int,
        title: String,
        content: String,
    ): Int {
        var pageNumber = firstPage
        paginateText(title, content) { layout ->
            val bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.RGB_565)
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                canvas.save()
                canvas.translate(PAGE_MARGIN.toFloat(), PAGE_MARGIN.toFloat())
                layout.draw(canvas)
                canvas.restore()
                val bytes = ByteArrayOutputStream().use { buffer ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, buffer)
                    buffer.toByteArray()
                }
                zip.fileEntry("${pageNumber.toString().padStart(6, '0')}.jpg", bytes)
            } finally {
                bitmap.recycle()
            }
            pageNumber++
        }
        return pageNumber
    }

    private fun paginateText(title: String, content: String, render: (StaticLayout) -> Unit) {
        val fullText = "$title\n\n$content".trim()
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 38f
        }
        val width = PAGE_WIDTH - PAGE_MARGIN * 2
        val height = PAGE_HEIGHT - PAGE_MARGIN * 2
        var offset = 0
        while (offset < fullText.length) {
            val candidateEnd = (offset + TEXT_WINDOW).coerceAtMost(fullText.length)
            val candidate = StaticLayout.Builder.obtain(fullText, offset, candidateEnd, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(8f, 1.15f)
                .setIncludePad(false)
                .build()
            var lastLine = candidate.lineCount - 1
            while (lastLine > 0 && candidate.getLineBottom(lastLine) > height) lastLine--
            val end = candidate.getLineEnd(lastLine).coerceAtLeast(offset + 1)
            val pageLayout = StaticLayout.Builder.obtain(fullText, offset, end, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(8f, 1.15f)
                .setIncludePad(false)
                .build()
            render(pageLayout)
            offset = end
        }
    }

    private fun embedHtmlImages(
        chapter: EbookExportChapter,
        optimization: EbookExportImageOptimization,
    ): String {
        var html = normalizeHtml(chapter.html)
        chapter.images.forEach { image ->
            if (!image.file.isFile) return@forEach
            withOptimizedImage(image.file, optimization) { optimized ->
                val mime = imageMime(optimized.name)
                val data = Base64.encodeToString(optimized.readBytes(), Base64.NO_WRAP)
                html = replaceImageReferences(
                    html = html,
                    image = image,
                    replacement = "data:$mime;base64,$data",
                )
            }
        }
        return html
    }

    private fun epubHtmlImages(
        chapter: EbookExportChapter,
        zip: ZipOutputStream,
        written: MutableSet<String>,
        namesBySource: MutableMap<String, String>,
        optimization: EbookExportImageOptimization,
    ): String {
        var html = normalizeHtml(chapter.html)
        chapter.images.forEach { image ->
            if (!image.file.isFile) return@forEach
            val name = namesBySource.getOrPut(image.source) {
                uniqueImageName(image.fileName, image.file, written)
            }
            if (written.add(name)) {
                zip.fileEntryOptimized("OEBPS/Images/$name", image.file, optimization)
            }
            html = replaceImageReferences(
                html = html,
                image = image,
                replacement = "../Images/$name",
            )
        }
        return html
    }

    private fun replaceImageReferences(
        html: String,
        image: EbookExportImage,
        replacement: String,
    ): String {
        return imageReferenceCandidates(image).fold(html) { current, source ->
            current.replace(source, replacement)
        }
    }

    private fun imageReferenceCandidates(image: EbookExportImage): List<String> {
        return (listOf(image.source) + image.aliases)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .sortedByDescending(String::length)
    }

    private fun normalizeHtml(content: String): String {
        val trimmed = normalizeLineBreaks(content).trim()
        return if (Regex("<[^>]+>").containsMatchIn(trimmed)) {
            trimmed
        } else {
            trimmed.split(Regex("\\n{2,}"))
                .joinToString("") { "<p>${xml(it).replace("\n", "<br/>")}</p>" }
        }
    }

    private fun buildNavigation(payload: EbookExportPayload): String = xhtmlPage(
        payload.language,
        payload.labels.tableOfContents,
        buildString {
            append("<nav epub:type=\"toc\" id=\"toc\"><h1>")
                .append(xml(payload.labels.tableOfContents)).append("</h1><ol>")
            append("<li><a href=\"intro.xhtml\">")
                .append(xml(payload.labels.introduction)).append("</a></li>")
            payload.chapters.forEachIndexed { index, chapter ->
                append("<li><a href=\"Text/chapter_").append(index).append(".xhtml\">")
                    .append(xml(chapter.title)).append("</a></li>")
            }
            append("</ol></nav>")
        },
        extraNamespace = " xmlns:epub=\"http://www.idpf.org/2007/ops\"",
        styleHref = "styles.css",
    )

    private fun buildPackage(
        payload: EbookExportPayload,
        imageNames: Set<String>,
        coverImageName: String?,
    ): String {
        val metadataDate = payload.metadataDate
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: DEFAULT_METADATA_DATE
        val id = payload.identifier
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: "urn:uuid:${UUID.nameUUIDFromBytes("${payload.title}\u0000${payload.author}".toByteArray())}"
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            append("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"book-id\">")
            append("<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">")
            append("<dc:identifier id=\"book-id\">").append(xml(id)).append("</dc:identifier>")
            append("<dc:title>").append(xml(payload.title)).append("</dc:title>")
            append("<dc:creator>").append(xml(payload.author)).append("</dc:creator>")
            append("<dc:language>").append(xml(payload.language)).append("</dc:language>")
            append("<dc:description>").append(xml(payload.description.ifBlank { payload.intro })).append("</dc:description>")
            append("<dc:publisher>").append(xml(payload.publisher)).append("</dc:publisher>")
            append("<dc:date>").append(xml(metadataDate)).append("</dc:date>")
            payload.subjects.map(String::trim).filter(String::isNotBlank).distinct().forEach { subject ->
                append("<dc:subject>").append(xml(subject)).append("</dc:subject>")
            }
            append("<meta property=\"dcterms:modified\">").append(xml(metadataDate)).append("</meta>")
            if (payload.layoutMode == "FIXED_PAGE") {
                append("<meta property=\"rendition:layout\">pre-paginated</meta>")
                append("<meta property=\"rendition:orientation\">auto</meta>")
                append("<meta property=\"rendition:spread\">auto</meta>")
            }
            append("</metadata>")
            append("<manifest><item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>")
            append("<item id=\"css\" href=\"styles.css\" media-type=\"text/css\"/>")
            append("<item id=\"intro\" href=\"intro.xhtml\" media-type=\"application/xhtml+xml\"/>")
            payload.chapters.indices.forEach { index ->
                append("<item id=\"chapter_").append(index).append("\" href=\"Text/chapter_")
                    .append(index).append(".xhtml\" media-type=\"application/xhtml+xml\"/>")
            }
            imageNames.forEachIndexed { index, name ->
                append("<item id=\"image_").append(index).append("\" href=\"Images/")
                    .append(xml(name)).append("\" media-type=\"").append(imageMime(name)).append("\"")
                if (name == coverImageName) append(" properties=\"cover-image\"")
                append("/>")
            }
            coverImageName?.let { cover ->
                val coverIndex = imageNames.indexOf(cover)
                if (coverIndex >= 0) {
                    append("<meta name=\"cover\" content=\"image_")
                        .append(coverIndex)
                        .append("\"/>")
                }
            }
            append("</manifest><spine><itemref idref=\"intro\"/>")
            payload.chapters.indices.forEach { index ->
                append("<itemref idref=\"chapter_").append(index).append("\"/>")
            }
            append("</spine></package>")
        }
    }

    private fun xhtmlPage(
        language: String,
        title: String,
        body: String,
        extraNamespace: String = "",
        styleHref: String,
        viewport: Pair<Float, Float>? = null,
    ): String = """<?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml"$extraNamespace lang="${xml(language)}">
        <head><title>${xml(title)}</title>${viewport?.let { "<meta name=\"viewport\" content=\"width=${it.first},height=${it.second}\"/>" }.orEmpty()}<link rel="stylesheet" type="text/css" href="${xml(styleHref)}"/></head>
        <body>$body</body></html>""".trimIndent()

    private fun EbookExportPayload.viewport(): Pair<Float, Float>? {
        val width = viewportWidth ?: return null
        val height = viewportHeight ?: return null
        return width to height
    }

    private fun ZipOutputStream.textEntry(name: String, text: String) =
        fileEntry(name, text.toByteArray(Charsets.UTF_8))

    private fun ZipOutputStream.fileEntry(name: String, bytes: ByteArray) {
        putNextEntry(zipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun ZipOutputStream.fileEntry(name: String, file: File) {
        putNextEntry(zipEntry(name))
        file.inputStream().buffered().use { input ->
            input.copyTo(this)
        }
        closeEntry()
    }

    private fun ZipOutputStream.fileEntryOptimized(
        name: String,
        file: File,
        optimization: EbookExportImageOptimization,
    ) {
        withOptimizedImage(file, optimization) { optimized -> fileEntry(name, optimized) }
    }

    private inline fun withOptimizedImage(
        file: File,
        optimization: EbookExportImageOptimization,
        block: (File) -> Unit,
    ) {
        if (optimization == EbookExportImageOptimization.ORIGINAL ||
            file.extension.lowercase() !in setOf("jpg", "jpeg", "png")
        ) {
            block(file)
            return
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        if (largest <= optimization.maxEdge || largest <= 0) {
            block(file)
            return
        }
        var sample = 1
        while (largest / sample > optimization.maxEdge) sample *= 2
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: run {
            block(file)
            return
        }
        val temporary = File.createTempFile("export-image-", ".${file.extension}")
        try {
            val format = if (file.extension.equals("png", ignoreCase = true)) {
                Bitmap.CompressFormat.PNG
            } else {
                Bitmap.CompressFormat.JPEG
            }
            temporary.outputStream().use { output ->
                check(bitmap.compress(format, optimization.quality, output)) {
                    "Cannot optimize export image"
                }
            }
            block(temporary)
        } finally {
            bitmap.recycle()
            temporary.delete()
        }
    }

    private fun zipEntry(name: String) = ZipEntry(name).apply { time = ZIP_ENTRY_TIME_MS }

    private fun Writer.line(value: String = "") {
        append(normalizeLineBreaks(value))
        append('\n')
    }

    private fun normalizeLineBreaks(value: String): String = value
        .replace("\r\n", "\n")
        .replace('\r', '\n')

    private fun buildCoverImageName(file: File): String {
        val extension = safeName(file.extension.lowercase()).ifBlank { "jpg" }
        return "cover.$extension"
    }

    private fun uniqueImageName(fileName: String, file: File, written: Set<String>): String {
        val safe = safeName(fileName).ifBlank { "image" }
        if (safe !in written) return safe
        val dot = safe.lastIndexOf('.')
        val base = if (dot > 0) safe.substring(0, dot) else safe
        val extension = if (dot > 0) safe.substring(dot) else ""
        val crc = file.crc32Hex()
        var suffix = 1
        var candidate = "${base}_${crc}$extension"
        while (candidate in written) {
            suffix++
            candidate = "${base}_${crc}_$suffix$extension"
        }
        return candidate
    }

    private fun File.crc32Hex(): String {
        val crc = CRC32()
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                crc.update(buffer, 0, read)
            }
        }
        return crc.value.toString(16)
    }

    private fun safeName(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun imageMime(name: String): String = when (name.substringAfterLast('.', "jpg").lowercase()) {
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        else -> "image/jpeg"
    }

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    companion object {
        private const val PAGE_WIDTH = 1240
        private const val PAGE_HEIGHT = 1754
        private const val PAGE_MARGIN = 96
        private const val TEXT_WINDOW = 8_000
        private const val ZIP_ENTRY_TIME_MS = 0L
        private const val DEFAULT_METADATA_DATE = "1970-01-01T00:00:00Z"
        private const val CONTAINER_XML = """<?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
            <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
            </container>"""
        private const val STYLE = """
            body{font-family:serif;line-height:1.65;margin:5%;max-width:52rem}img{max-width:100%;height:auto}
            h1,h2{line-height:1.25;page-break-after:avoid}article{break-before:page}.intro{break-after:page}
        """
    }
}
