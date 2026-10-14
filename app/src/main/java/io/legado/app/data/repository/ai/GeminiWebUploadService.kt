package io.legado.app.data.repository.ai

import io.legado.app.constant.AppLog
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Service tải file / hình ảnh lên hạ tầng Google Gemini Web (Google Resumable Upload).
 * Hỗ trợ Manga OCR, dịch tranh ảnh và tài liệu scan qua Gemini Web.
 * Giao thức reverse-engineered từ Google Bard/Gemini Web client:
 * 1. POST https://content-push.googleapis.com/upload/ với lệnh 'start' để lấy Upload-URL
 * 2. POST binary payload với lệnh 'upload, finalize' để nhận Google File ID (w_...)
 */
object GeminiWebUploadService {

    private const val UPLOAD_ENDPOINT = "https://content-push.googleapis.com/upload/"
    private const val BARD_STORAGE_AUTH = "Basic c2F2ZXM6cyNMdGhlNmxzd2F2b0RsN3J1d1U="
    private const val BARD_TENANT_ID = "bard-storage"
    private const val DEFAULT_PUSH_ID = "feeds/mcudyrk2a4khkz"

    /**
     * Upload ảnh nhị phân lên Google Gemini Web storage và nhận về File ID.
     * @param imageData Mảng byte của file ảnh (PNG, JPEG, WEBP)
     * @param mimeType MIME type của ảnh (mặc định image/jpeg)
     * @param fileName Tên file đại diện (mặc định input_file_0.jpg)
     * @param session Phiên đăng nhập Gemini Web (chứa cookieHeader và pushId)
     * @return Result chứa File ID dạng chuỗi (ví dụ: w_xxxxxxxxxxxx)
     */
    suspend fun uploadImage(
        imageData: ByteArray,
        mimeType: String = "image/jpeg",
        fileName: String = "input_file_0.jpg",
        session: GeminiWebSession,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val pushId = session.pushId.ifBlank { DEFAULT_PUSH_ID }
            val initHeaders = mutableMapOf(
                "X-Goog-Upload-Command" to "start",
                "X-Goog-Upload-Header-Content-Length" to imageData.size.toString(),
                "X-Goog-Upload-Header-Content-Type" to mimeType,
                "X-Tenant-Id" to BARD_TENANT_ID,
                "Push-Id" to pushId,
                "Authorization" to BARD_STORAGE_AUTH,
                "Content-Type" to "application/x-www-form-urlencoded;charset=utf-8",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
            )
            if (session.cookieHeader.isNotBlank()) {
                initHeaders["Cookie"] = session.cookieHeader
            }

            // Bước 1: Gửi lệnh start để nhận session upload URL
            val initBody = "File name: $fileName".toRequestBody("application/x-www-form-urlencoded;charset=utf-8".toMediaType())
            val initResponse = aiOkHttpClient.newCallResponse {
                url(UPLOAD_ENDPOINT)
                post(initBody)
                addHeaders(initHeaders)
            }

            val uploadUrl = initResponse.header("X-Goog-Upload-Url")
                ?: initResponse.header("X-Goog-Upload-URL")
            val uploadStatus = initResponse.header("X-Goog-Upload-Status")
            initResponse.close()

            if (uploadUrl.isNullOrBlank() || uploadStatus != "active") {
                throw Exception("Google Resumable Upload khởi tạo thất bại (status=$uploadStatus, header missing).")
            }

            // Bước 2: Upload binary dữ liệu ảnh lên uploadUrl
            val uploadHeaders = mutableMapOf(
                "X-Goog-Upload-Command" to "upload, finalize",
                "X-Goog-Upload-Offset" to "0",
                "Content-Type" to mimeType,
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
            )
            if (session.cookieHeader.isNotBlank()) {
                uploadHeaders["Cookie"] = session.cookieHeader
            }

            val uploadBody = imageData.toRequestBody(mimeType.toMediaType())
            val uploadResponse = aiOkHttpClient.newCallResponse {
                url(uploadUrl)
                post(uploadBody)
                addHeaders(uploadHeaders)
            }

            if (!uploadResponse.isSuccessful) {
                val code = uploadResponse.code
                uploadResponse.close()
                throw Exception("Google Gemini Web Upload thất bại với mã HTTP $code")
            }

            val responseBodyString = uploadResponse.body.string().trim()
            uploadResponse.close()

            if (responseBodyString.isBlank()) {
                throw Exception("Google Gemini Web không trả về File ID sau khi upload.")
            }

            AppLog.put("Gemini Web Upload: Tải ảnh thành công, nhận File ID: $responseBodyString")
            responseBodyString
        }
    }
}
