# Phase 02: Đồng Bộ Dữ Liệu Dịch Tiêu Đề & Metadata Khi Xuất Ebook

Status: ✅ Complete
Dependencies: `phase-01-ui-qt-ai-cache-integration.md`

## 1. Mục tiêu (Objective)
1. Khi xuất sách dạng bản dịch (`ContentSource.Translation`), toàn bộ tiêu đề chương trong file xuất (EPUB/TXT/HTML/PDF) phải sử dụng **bản dịch tiêu đề tiếng Việt** (ưu tiên bản AI đã lưu trong cache theo Canonical Scope Key từ Phase 1, fallback sang bản QT offline) thay vì để sót chữ Hán thô ráp.
2. Đồng bộ các thông tin metadata của ebook (Tên sách, Tác giả, Lời giới thiệu, Tên file xuất) sử dụng bản dịch chuẩn ngữ pháp tiếng Việt.
3. Đảm bảo an toàn luồng (coroutine thread safety) trong Android Service và xử lý tối ưu các trường hợp không có CJK (Latin/tiếng Việt sẵn có).

## 2. Chi tiết yêu cầu kỹ thuật (Requirements)

### 2.1. Nâng cấp & Inject Dependency vào `ExportBookService.kt`
- Hiện tại trong `ExportBookService.kt`:
  - `TranslateDynamicUiTextUseCase` **chưa được inject** (0 reference trong toàn bộ file).
  - Có khai báo `private val translationCacheRepository: TranslationCacheGateway by inject()` tại dòng 131 nhưng chưa được tận dụng.
- **Giải pháp Injection:**
  - Thêm Koin injection vào `ExportBookService`:
    ```kotlin
    private val translateDynamicUiTextUseCase: TranslateDynamicUiTextUseCase by inject()
    ```

### 2.2. Xử lý Tiêu Đề Chương & Metadata khi `source == ContentSource.Translation`
1. **Tiêu đề từng chương (`EbookExportChapter.title`):**
   - Trong vòng lặp xuất chương của `exportModernFormat` (chạy trên background coroutine):
   - Lấy `rawTitle = chapter.getDisplayTitle(contentProcessor.getTitleReplaceRules(), useReplace = useReplace).replace("\uD83D\uDD12", "")`.
   - **Xử lý Non-CJK Edge Case (Khắc phục Gap #5):**
     - Nếu `!rawTitle.containsCjk()`: Giữ nguyên `rawTitle`, không cần qua bộ dịch.
     - Nếu có CJK: Gọi `translateDynamicUiTextUseCase.executeChapterTitle(scopeKey = canonicalChapterTitleScopeKey(book.bookUrl, chapter.index), originalText = rawTitle, book = book, chapterIndex = chapter.index)`.
     - Kết quả nhận về được ưu tiên từ AI cache (`PROVIDER_APP_AI` / `PROVIDER_LOCAL_AI`), fallback sang QT, và được chuẩn hóa `restructureChapterNumbers().toTitleCase()`.
2. **Metadata cuốn sách trong `EbookExportPayload` (Khắc phục Gap #5):**
   - `title`:
     - Nếu `book.name.containsCjk()`: gọi `translateDynamicUiTextUseCase.executeBookName(canonicalBookMetadataScopeKey(book.bookUrl, "name"), book.name, book).getOrElse { book.name }`.
     - Nếu không có CJK: dùng trực tiếp `book.name`.
   - `author`:
     - Nếu `book.getRealAuthor().containsCjk()`: gọi `translateDynamicUiTextUseCase.executeAuthorName(canonicalBookMetadataScopeKey(book.bookUrl, "author"), book.getRealAuthor(), book).getOrElse { book.getRealAuthor() }`.
     - Nếu không có CJK: dùng trực tiếp `book.getRealAuthor()`.
   - `intro` & `description`:
     - Giới thiệu truyện: nếu có CJK thì dịch qua `translateDynamicUiTextUseCase.execute(canonicalBookMetadataScopeKey(book.bookUrl, "intro"), book.getDisplayIntro(), book)`.
   - `language`: Luôn gán là `targetLanguage` (mặc định `"vi"`).
3. **Tên tệp xuất (`fileName`):**
   - Đặt tên file xuất thân thiện: `val exportFileName = "${translatedTitle.sanitizeFileName()} - ${translatedAuthor.sanitizeFileName()}"`.
   - Giúp các thiết bị đọc sách (Kindle, Kobo, Boox) hiển thị tên sách tiếng Việt chuẩn mực mà không bị lỗi font hoặc để lộ chữ Hán.

### 2.3. Xử Lý Concurrency & Service Lifecycle (Khắc phục Gap #4)
- `ExportBookService` là Foreground Service chạy dài hạn. Việc dịch tiêu đề diễn ra trong coroutine scope của Service.
- Sử dụng `runCatching` bọc quanh từng lời gọi dịch tiêu đề chương: nếu xảy ra ngoại lệ không mong muốn, fallback ngay về `rawTitle` để đảm bảo **quá trình xuất ebook không bao giờ bị crash hoặc abort giữa chừng**.
- Tận dụng `TranslateDynamicUiTextUseCase.memoryCache` để tránh tra cứu disk I/O lặp lại cho các tiêu đề trùng lặp.

### 2.4. Đồng bộ với `WebServiceExportController.kt`
- Kiểm tra các endpoint xuất sách từ xa trên web interface cục bộ (Ktor web service) để đảm bảo payload trả về cũng dùng tiêu đề và metadata tiếng Việt đồng bộ.

## 3. Files to Create/Modify
- `app/src/main/java/io/legado/app/service/ExportBookService.kt`: Inject `TranslateDynamicUiTextUseCase`, xử lý dịch tiêu đề chương và metadata trong `exportModernFormat`.
- `app/src/main/java/io/legado/app/web/WebServiceExportController.kt`: Đồng bộ nếu có xuất bản dịch.

## 4. Test Criteria
- [x] Unit Test: Khi xuất sách với `ContentSource.Translation`, `EbookExportChapter.title` chứa tiêu đề tiếng Việt đã dịch, 0% ký tự CJK còn sót.
- [x] Unit Test: Khi chương đã có AI title cache -> Ebook xuất ra hiển thị chính xác tiêu đề AI đó.
- [x] Edge Case Test: Khi sách hoặc tiêu đề chương không chứa CJK (đã là tiếng Việt hoặc tiếng Anh), quá trình xuất diễn ra bình thường mà không gọi dịch dư thừa.
- [x] Edge Case Test: Khi gặp lỗi dịch đột xuất trong 1 chương, fallback an toàn về display title mà không dừng tiến trình xuất toàn bộ cuốn sách.
