package com.drducbook.app.cloud

import io.legado.app.domain.model.CloudSnapshotDescriptor
import io.legado.app.domain.model.GoogleDriveSnapshotObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class GoogleDriveApiRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String? = null,
) {
    override fun toString(): String =
        "GoogleDriveApiRequest(method=$method, url=$url, headers=<redacted>, body=$body)"
}

object GoogleDriveAppDataContract {
    const val APP_DATA_SPACE = "appDataFolder"
    const val NAMESPACE = "drducbook"
    const val SNAPSHOT_MIME_TYPE = "application/octet-stream"
    const val HEAD_FILE_NAME = "drducbook-head.json"
    const val HEAD_MIME_TYPE = "application/json"

    val requiredScopes: Set<String> = setOf(CloudConsentScopes.googleDriveAppData)

    fun validateConsentScopes(scopes: Set<String>): Result<Unit> = runCatching {
        require(scopes == requiredScopes) {
            "Google Drive backup must request only drive.appdata"
        }
    }

    fun snapshotObject(descriptor: CloudSnapshotDescriptor): GoogleDriveSnapshotObject {
        CloudSyncClientContract.validateSnapshotDescriptor(descriptor).getOrThrow()
        val revision = CloudSyncClientContract.normalizeRevision(descriptor.revision)
        val snapshotId = CloudSyncClientContract.normalizeUuid(descriptor.snapshotId, "snapshotId")
        val fileName = "snapshot-$revision-$snapshotId.drducsnapshot"
        return GoogleDriveSnapshotObject(
            fileName = fileName,
            appDataPath = "snapshots/$revision/$snapshotId.drducsnapshot",
            revision = revision,
            snapshotId = snapshotId,
            sha256 = descriptor.contentSha256,
            sizeBytes = descriptor.contentSizeBytes,
        )
    }

    fun headObjectPath(target: String): String {
        val normalizedTarget = CloudSyncClientContract.normalizeRevision(target)
        return "heads/$normalizedTarget/$HEAD_FILE_NAME"
    }

    fun buildListRequest(
        accessToken: String,
        supabaseUserHash: String,
    ): GoogleDriveApiRequest {
        require(accessToken.isNotBlank()) { "Drive access token is required" }
        require(accountHashRegex.matches(supabaseUserHash)) { "Supabase user hash is invalid" }
        val query = "appProperties has { key='drducbookNamespace' and value='$NAMESPACE' } and " +
            "appProperties has { key='supabaseUserHash' and value='$supabaseUserHash' }"
        return GoogleDriveApiRequest(
            method = "GET",
            url = "https://www.googleapis.com/drive/v3/files?spaces=$APP_DATA_SPACE" +
                "&q=${urlEncode(query)}" +
                "&orderBy=${urlEncode("modifiedTime desc")}" +
                "&pageSize=1000" +
                "&fields=${urlEncode("files(id,name,modifiedTime,appProperties,size,md5Checksum),nextPageToken")}",
            headers = bearerHeaders(accessToken),
        )
    }

    fun buildDownloadRequest(
        accessToken: String,
        fileId: String,
    ): GoogleDriveApiRequest {
        require(accessToken.isNotBlank()) { "Drive access token is required" }
        require(fileIdRegex.matches(fileId)) { "Drive file ID is invalid" }
        return GoogleDriveApiRequest(
            method = "GET",
            url = "https://www.googleapis.com/drive/v3/files/${urlEncode(fileId)}?alt=media",
            headers = bearerHeaders(accessToken),
        )
    }

    fun buildStartResumableUploadRequest(
        accessToken: String,
        snapshot: GoogleDriveSnapshotObject,
        supabaseUserHash: String,
    ): GoogleDriveApiRequest {
        require(accessToken.isNotBlank()) { "Drive access token is required" }
        require(accountHashRegex.matches(supabaseUserHash)) { "Supabase user hash is invalid" }
        require(snapshot.sizeBytes >= 0) { "Snapshot size must not be negative" }
        val body = """
            {
              "name": "${snapshot.fileName}",
              "mimeType": "$SNAPSHOT_MIME_TYPE",
              "parents": ["$APP_DATA_SPACE"],
              "appProperties": {
                "drducbookNamespace": "$NAMESPACE",
                "objectPath": "${snapshot.appDataPath}",
                "revision": "${snapshot.revision}",
                "snapshotId": "${snapshot.snapshotId}",
                "sha256": "${snapshot.sha256}",
                "supabaseUserHash": "$supabaseUserHash"
              }
            }
        """.trimIndent()
        return GoogleDriveApiRequest(
            method = "POST",
            url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable",
            headers = bearerHeaders(accessToken) + mapOf(
                "content-type" to "application/json; charset=utf-8",
                "x-upload-content-type" to SNAPSHOT_MIME_TYPE,
                "x-upload-content-length" to snapshot.sizeBytes.toString(),
            ),
            body = body,
        )
    }

    fun containsClientSecret(request: GoogleDriveApiRequest): Boolean {
        val haystack = buildString {
            append(request.url).append('\n')
            request.body?.let(::append)
            request.headers.forEach { (key, value) ->
                append('\n').append(key).append(':').append(value)
            }
        }
        return secretPatterns.any { it.containsMatchIn(haystack) }
    }

    private fun bearerHeaders(accessToken: String): Map<String, String> =
        mapOf("authorization" to "Bearer $accessToken")

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private val accountHashRegex = Regex("^[a-f0-9]{64}$")
    private val fileIdRegex = Regex("^[A-Za-z0-9_-]{3,256}$")
    private val secretPatterns = listOf(
        Regex("refresh_token", RegexOption.IGNORE_CASE),
        Regex("client_secret", RegexOption.IGNORE_CASE),
        Regex("https://www.googleapis.com/auth/drive(?!\\.appdata)", RegexOption.IGNORE_CASE),
    )
}
