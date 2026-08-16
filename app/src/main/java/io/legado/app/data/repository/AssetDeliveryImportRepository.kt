package io.legado.app.data.repository

import android.content.Context
import android.net.Uri
import io.legado.app.domain.gateway.AssetDeliveryDownloadedFile
import io.legado.app.domain.gateway.AssetDeliveryImportGateway
import io.legado.app.domain.gateway.LocalAiEngineGateway
import io.legado.app.domain.gateway.QuickDictionaryGateway
import io.legado.app.domain.model.AssetDeliveryArtifact
import io.legado.app.domain.model.AssetDeliveryArtifactKind
import io.legado.app.domain.model.ExternalAssetCatalog
import io.legado.app.domain.model.QuickDictionaryScope
import io.legado.app.domain.model.QuickDictionaryType
import io.legado.app.model.translation.HachimiOnnxModelImporter
import io.legado.app.model.tts.LocalTtsModelImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/** Routes verified asset downloads through the same importers as manual file imports. */
class AssetDeliveryImportRepository(
    private val context: Context,
    private val quickDictionaryGateway: QuickDictionaryGateway,
    private val localAiEngineGateway: LocalAiEngineGateway,
) : AssetDeliveryImportGateway {

    override suspend fun importArtifact(
        artifact: AssetDeliveryArtifact,
        downloadedFile: AssetDeliveryDownloadedFile,
    ): String = withContext(Dispatchers.IO) {
        val source = File(downloadedFile.path)
        require(source.isFile && source.length() > 0L) {
            "Không tìm thấy tệp gói đã tải"
        }
        when (artifact.kind) {
            AssetDeliveryArtifactKind.TTS -> {
                LocalTtsModelImporter.import(context, Uri.fromFile(source))
                "Đã nhập model TTS: ${artifact.displayName}"
            }

            AssetDeliveryArtifactKind.LOCAL_AI -> {
                localAiEngineGateway.importModel(Uri.fromFile(source).toString())
                    .getOrThrow()
                "Đã nhập model AI cục bộ: ${artifact.displayName}"
            }

            AssetDeliveryArtifactKind.TRANSLATION -> when (artifact.id) {
                ExternalAssetCatalog.hachimiOnnxAssetId -> {
                    HachimiOnnxModelImporter.import(context, Uri.fromFile(source))
                    "Đã nhập model Hachimi NMT"
                }

                ExternalAssetCatalog.quickTranslationCleanAssetId -> {
                    importQuickDictionary(source, artifact.displayName)
                    "Đã nhập gói từ điển QT"
                }

                else -> throw IOException("Chưa có bộ nhập cho gói dịch ${artifact.id}")
            }
        }
    }

    private suspend fun importQuickDictionary(source: File, displayName: String) {
        val extracted = File.createTempFile("asset-qt-", ".txt", context.cacheDir)
        try {
            extractDictionaryText(source, extracted)
            quickDictionaryGateway.importPack(
                localPath = extracted.absolutePath,
                displayName = displayName,
                type = QuickDictionaryType.VIETPHRASE,
                scope = QuickDictionaryScope.GLOBAL,
                scopeKey = "",
            )
        } finally {
            extracted.delete()
        }
    }

    private fun extractDictionaryText(source: File, target: File) {
        try {
            ZipFile(source).use { zip ->
                val entries = zip.entries().asSequence()
                    .filter { !it.isDirectory }
                    .filter { entry ->
                        val name = entry.name.lowercase()
                        name.endsWith(".txt") || name.endsWith(".dict") ||
                            name.endsWith(".tsv") || name.endsWith(".csv") ||
                            name.endsWith(".qt")
                    }
                    .sortedBy { it.name.lowercase() }
                    .toList()
                if (entries.isEmpty()) {
                    throw IOException("Gói từ điển QT không chứa tệp dữ liệu")
                }
                target.outputStream().buffered().use { output ->
                    entries.forEachIndexed { index, entry ->
                        val normalized = entry.name.replace('\\', '/')
                        require(!normalized.startsWith('/') &&
                            normalized.split('/').none { it == ".." }) {
                            "Đường dẫn không an toàn trong gói từ điển QT"
                        }
                        zip.getInputStream(entry).use { input -> input.copyTo(output) }
                        if (index < entries.lastIndex) output.write('\n'.code)
                    }
                }
            }
        } catch (_: java.util.zip.ZipException) {
            source.copyTo(target, overwrite = true)
        }
    }
}
