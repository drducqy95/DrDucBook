# Phase 04: Hạ Tầng Đính Kèm Ảnh/Tài Liệu (Resumable Upload) Hỗ Trợ Dịch Manga/OCR

> **Thuộc kế hoạch:** `PLN-261014-1845` — Gemini Web Advanced Enhancements  
> **Trạng thái:** 🟡 Sẵn sàng triển khai  
> **Các file tác động chính:**
> - `app/src/main/java/io/legado/app/data/repository/ai/GeminiWebUploadService.kt`
> - `app/src/main/java/io/legado/app/data/repository/ai/GeminiWebHandler.kt`

---

## 1. Mục Đích & Tiềm Năng Ứng Dụng

1. **Khả năng Đa Phương Thức (Multimodal) của Gemini Web:**
   - Gemini Web (đặc biệt là các model Flash và Pro) có khả năng đọc hiểu hình ảnh, biểu bảng, văn bản viết tay, trang truyện tranh (Manga/Manhua) với độ chính xác hàng đầu thế giới.
   - Hiện nay các API thương mại tính phí khá đắt cho token hình ảnh (Vision tokens). Tận dụng được giao thức upload của Gemini Web sẽ giúp người dùng DrDucBook có thể:
     - Dịch trực tiếp trang truyện tranh (Manga/Manhua translation).
     - OCR và dịch các đoạn trích từ ảnh chụp sách in hoặc tài liệu scan PDF.
2. **Quy trình Reverse-Engineered Tải Ảnh Lên Google:**
   - Trong `gemini-web-to-api` (`gemini_upload.go`), cơ chế upload file sử dụng giao thức chuẩn **Google Resumable Upload**:
     - **Endpoint:** `POST https://content-push.googleapis.com/upload`
     - **Authorization:** `Basic c2F2ZXM6cyNMdGhlNmxzd2F2b0RsN3J1d1U=` (hằng số công khai của Google Bard/Gemini web client).
     - **Push-ID:** Bóc tách từ HTML (`"qKIAYe":"..."`, mặc định `feeds/mcudyrk2a4khkz`).
     - **X-Tenant-Id:** `bard-storage`.
     - **Quy trình 3 bước:**
       1. `OPTIONS` khởi tạo kết nối.
       2. `POST` với header `X-Goog-Upload-Command: start` $\rightarrow$ Nhận URL upload tạm thời trong header `X-Goog-Upload-Url`.
       3. `POST` gửi binary data của ảnh lên URL tạm thời với header `X-Goog-Upload-Command: upload, finalize` $\rightarrow$ Google trả về File ID dạng chuỗi (ví dụ: `w_123456...`).
     - Sau khi có File ID, chỉ cần gắn vào mảng `inner[0]` của request `StreamGenerate`:
       `messageContent = [prompt, 0, null, [[ [fileId], fileName ]], null, null, 0]`

---

## 2. Thiết Kế Module `GeminiWebUploadService` Trên Android

```kotlin
class GeminiWebUploadService(
    private val okHttpClient: okhttp3.OkHttpClient
) {
    suspend fun uploadImage(
        imageData: ByteArray,
        mimeType: String = "image/jpeg",
        filename: String = "image.jpg",
        session: GeminiWebSession
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // Bước 1: Gửi lệnh start để lấy upload URL
            // Bước 2: Gửi binary data và nhận File ID
            // Trả về file ID thành công
        }
    }
}
```

---

## 3. Các Bước Triển Khai

1. Tạo file mới `GeminiWebUploadService.kt` trong package `io.legado.app.data.repository.ai`.
2. Hỗ trợ truyền mảng `imageBytes: ByteArray?` trong `AiGenerateRequest`.
3. Trong `GeminiWebHandler.kt`: Nếu request có đính kèm ảnh, tự động gọi `GeminiWebUploadService` để lấy File ID và gắn vào cấu trúc `inner[0]` của payload `f.req`.

---

## 4. Tiêu Chí Nghiệm Thu
- [ ] Gửi thành công 1 file ảnh (JPG/PNG < 5MB) lên Google Gemini Web và nhận về File ID hợp lệ.
- [ ] Gemini Web phản hồi phân tích/dịch chính xác nội dung trong ảnh.
- [ ] Xử lý lỗi mượt mà nếu upload gặp sự cố (timeout, file quá lớn, lỗi mạng).
