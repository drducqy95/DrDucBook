package io.legado.app.ui.book.info

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.drducbook.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.constant.AppConst
import io.legado.app.ui.config.otherConfig.OtherConfig
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.text.AppText
import java.io.File

/** Opens the user's mail client and walks through split Send-to-Kindle parts in order. */
class KindleSendActivity : BaseComposeActivity() {

    private val parts: List<KindlePart> by lazy {
        val uris = intent.getStringArrayListExtra(EXTRA_PART_URIS).orEmpty()
        val names = intent.getStringArrayListExtra(EXTRA_PART_FILE_NAMES).orEmpty()
        val mimes = intent.getStringArrayListExtra(EXTRA_PART_MIME_TYPES).orEmpty()
        if (uris.isNotEmpty() && uris.size == names.size && uris.size == mimes.size) {
            uris.indices.map { index -> KindlePart(uris[index], names[index], mimes[index]) }
        } else {
            listOf(
                KindlePart(
                    uri = intent.getStringExtra(EXTRA_URI).orEmpty(),
                    fileName = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty(),
                    mimeType = intent.getStringExtra(EXTRA_MIME_TYPE).orEmpty(),
                )
            )
        }
    }

    @Composable
    override fun Content() {
        if (parts.isEmpty() || parts.any { it.uri.isBlank() || !isSupportedFileName(it.fileName) }) {
            LaunchedEffect(Unit) {
                showMessage("Export file is no longer available or unsupported")
                finish()
            }
            return
        }
        var email by rememberSaveable { mutableStateOf(OtherConfig.kindleEmail.trim()) }
        var currentIndex by rememberSaveable { mutableStateOf(0) }
        var awaitingMail by rememberSaveable { mutableStateOf(false) }
        var showContinuePrompt by rememberSaveable { mutableStateOf(false) }
        var error by rememberSaveable { mutableStateOf<String?>(null) }

        val mailLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            awaitingMail = false
            if (currentIndex >= parts.lastIndex) {
                finish()
            } else if (result.resultCode == Activity.RESULT_OK) {
                currentIndex++
            } else {
                // Mail clients often return CANCELED even after the message was sent.
                // Ask before advancing so a part is never silently skipped.
                showContinuePrompt = true
            }
        }

        val address = email.trim()
        LaunchedEffect(address, currentIndex, awaitingMail, showContinuePrompt) {
            if (EMAIL_REGEX.matches(address) && !awaitingMail && !showContinuePrompt) {
                awaitingMail = true
                launchEmail(parts[currentIndex], address, mailLauncher)
            }
        }

        if (!EMAIL_REGEX.matches(address)) {
            AlertDialog(
                onDismissRequest = { finish() },
                title = { AppText(getString(R.string.send_to_kindle)) },
                text = {
                    AppTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            error = null
                        },
                        label = getString(R.string.kindle_receiving_email),
                        supportingText = error?.let { message -> { AppText(message) } },
                        isError = error != null,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                },
                dismissButton = {
                    TextButton(onClick = { finish() }) { AppText("Cancel") }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val candidate = email.trim()
                            if (!EMAIL_REGEX.matches(candidate)) {
                                error = "Enter a valid Kindle email address"
                            } else {
                                OtherConfig.kindleEmail = candidate
                                email = candidate
                            }
                        },
                    ) { AppText("Start sending") }
                },
            )
        } else if (showContinuePrompt) {
            val nextPart = currentIndex + 2
            AlertDialog(
                onDismissRequest = { finish() },
                title = { AppText("Continue Send-to-Kindle") },
                text = {
                    AppText(
                        "The mail app did not report a successful result. Continue with part " +
                            "$nextPart/${parts.size}? Confirm in the mail app before continuing.",
                    )
                },
                dismissButton = {
                    TextButton(onClick = { finish() }) { AppText("Finish") }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showContinuePrompt = false
                            currentIndex++
                        },
                    ) { AppText("Continue") }
                },
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                AppText("Preparing part ${currentIndex + 1}/${parts.size} for Send-to-Kindle…")
                AppText("The mail app still requires your confirmation before sending.")
            }
        }
    }

    private fun launchEmail(
        part: KindlePart,
        address: String,
        launcher: androidx.activity.result.ActivityResultLauncher<Intent>,
    ) {
        runCatching {
            val parsed = Uri.parse(part.uri)
            val shareUri = if (parsed.scheme.equals("file", ignoreCase = true)) {
                FileProvider.getUriForFile(this, AppConst.authority, File(requireNotNull(parsed.path)))
            } else {
                parsed
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = part.mimeType.ifBlank { mimeFor(part.fileName) }
                putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
                putExtra(Intent.EXTRA_SUBJECT, part.fileName.ifBlank { "DrDucBook export" })
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri(part.fileName, shareUri)
            }
            launcher.launch(Intent.createChooser(send, getString(R.string.send_to_kindle)))
        }.onFailure {
            showMessage(it.localizedMessage ?: "No email application is available")
            finish()
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun mimeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "epub" -> "application/epub+zip"
        "pdf" -> "application/pdf"
        "mobi" -> "application/x-mobipocket-ebook"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    private data class KindlePart(
        val uri: String,
        val fileName: String,
        val mimeType: String,
    )

    companion object {
        const val EXTRA_URI = "kindle.uri"
        const val EXTRA_FILE_NAME = "kindle.fileName"
        const val EXTRA_MIME_TYPE = "kindle.mimeType"
        const val EXTRA_PART_URIS = "kindle.partUris"
        const val EXTRA_PART_FILE_NAMES = "kindle.partFileNames"
        const val EXTRA_PART_MIME_TYPES = "kindle.partMimeTypes"
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun isSupportedFileName(fileName: String): Boolean =
            when (fileName.substringAfterLast('.', "").lowercase()) {
                "epub", "pdf", "txt", "html", "htm", "rtf",
                "jpg", "jpeg", "png", "gif", "bmp" -> true
                else -> false
            }
    }
}
