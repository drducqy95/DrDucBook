package io.legado.app.data.repository

import android.content.Context
import io.legado.app.domain.gateway.StoryImageStorageGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class StoryImageStorageRepository(
    context: Context,
) : StoryImageStorageGateway {

    private val rootDirectory = File(context.filesDir, "story_wiki_images")

    override suspend fun save(
        bookUrl: String,
        subjectKey: String,
        bytes: ByteArray,
        mimeType: String,
    ): String = withContext(Dispatchers.IO) {
        require(bytes.isNotEmpty()) { "Generated image is empty" }
        val directory = File(rootDirectory, bookUrl.sha256().take(24)).apply {
            check(exists() || mkdirs()) { "Cannot create story image directory" }
        }
        val extension = when (mimeType.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            else -> "png"
        }
        val target = File(directory, "${subjectKey.sha256().take(32)}.$extension")
        target.writeBytes(bytes)
        target.absolutePath
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
