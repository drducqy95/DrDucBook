# Phase 01: Khóa Dịch UI với QT, Chuẩn Hóa Canonical Scope Key & Ưu Tiên Lưu Đè Cache AI

Status: ✅ Complete
Dependencies: Không có

## 1. Mục tiêu (Objective)
1. Đảm bảo dịch UI động mặc định bị khóa hoàn toàn vào bộ dịch offline Quick Translator (QT), không tự động gọi API AI tốn kém khi người dùng chỉ xem UI.
2. Chuẩn hóa quy tắc **Canonical Scope Key** cho tiêu đề chương và metadata giữa các thành phần (`TranslateChapterUseCase`, `TocViewModel`, `BookInfoViewModel`, `HomeViewModel`, `BookshelfViewModel`, `ReadBookViewModel`) để chấm dứt 100% tình trạng phân mảnh cache key.
3. Khi người dùng chạy dịch chương bằng AI (AI Cloud `PROVIDER_APP_AI` hoặc Local AI GGUF `PROVIDER_LOCAL_AI`), tự động lưu đè tiêu đề chương và metadata đã dịch bằng AI vào dynamic UI cache theo canonical scope key.
4. Khi UI truy vấn tiêu đề chương hoặc metadata, kiểm tra và ưu tiên hiển thị bản dịch từ cache AI (`PROVIDER_APP_AI` -> `PROVIDER_LOCAL_AI`) nếu đã tồn tại, thay thế hoàn toàn cho bản dịch QT.

## 2. Chi tiết yêu cầu kỹ thuật (Requirements)

### 2.1. Phân Tích Hiện Trạng & Root Cause Phân Mảnh Scope Key
- **Write Path** (`TranslateChapterUseCase.kt` line 678–682):
  - Gọi `saveAiChapterTitle(scopeKey = "chapter-title:${book.bookUrl}:${bookChapter.index}", originalText = ..., aiTitle = ...)`
  - Bên trong `saveAiChapterTitle`, tự động nối `":title"` -> Disk key: `"chapter-title:${book.bookUrl}:${bookChapter.index}:title"` với `provider = PROVIDER_APP_AI`.
- **Read Path 1 - Mục Lục TOC** (`TocViewModel.kt` line 331–336):
  - Gọi `executeChapterTitles(scopeKey = "toc:${book.bookUrl}:b$batchIndex", ...)`
  - `executeChapterTitles` hiện tại delegate thẳng sang `executeLines()`.
  - `executeLines()` tạo key: `"toc:${book.bookUrl}:b$batchIndex|qt-dictionary:...:line:$index"` với `provider = PROVIDER_QUICK_TRANSLATOR`.
  - **Hệ quả:** `executeChapterTitles` hoàn toàn bypass kiểm tra AI cache! 100% cache miss giữa TOC và bản dịch AI.
- **Read Path 2 - Thông Tin Sách & Trang Chủ** (`BookInfoViewModel.kt` line 1573, `HomeViewModel.kt` line 419):
  - `BookInfoViewModel` dùng scopeKey `"book:${book.bookUrl}"` -> disk key `"book:${book.bookUrl}:title"` (thiếu chapterIndex).
  - `HomeViewModel` dùng scopeKey `"home:${bookUrl}"` -> disk key `"home:${bookUrl}:title"` (sai prefix hoàn toàn).

### 2.2. Chuẩn hóa Canonical Scope Key trong `TranslateDynamicUiTextUseCase`
1. **Helper chuẩn hóa canonical key:**
   ```kotlin
   fun canonicalChapterTitleScopeKey(bookUrl: String, chapterIndex: Int): String =
       "chapter-title:$bookUrl:$chapterIndex"

   fun canonicalBookMetadataScopeKey(bookUrl: String, field: String): String =
       "book-metadata:$bookUrl:$field"
   ```
2. **Cập nhật `executeChapterTitle`:**
   - Nhận thêm: `chapterIndex: Int? = null`.
   - Kiểm tra cache AI theo thứ tự ưu tiên trước khi fallback QT:
     1. `translationCacheGateway.readDynamicUiTranslation(canonicalTitleScopeKey, originalText, targetLanguage, PROVIDER_APP_AI)`
     2. `translationCacheGateway.readDynamicUiTranslation(canonicalTitleScopeKey, originalText, targetLanguage, PROVIDER_LOCAL_AI)`
   - Nếu có cache AI (Cloud hoặc Local AI): trả về ngay sau khi chạy `restructureChapterNumbers().toTitleCase()`.
   - Nếu cả 2 đều không có cache: fallback dịch bằng QT offline thông qua `execute()`.
3. **Rewrite hoàn toàn `executeChapterTitles` (Khắc phục Gap #1 & #2):**
   - Signature:
     ```kotlin
     suspend fun executeChapterTitles(
         scopeKey: String,
         originalLines: List<String>,
         book: Book? = null,
         chapterIndices: List<Int>? = null,
         contextText: String = originalLines.joinToString("\n"),
         forceRetranslate: Boolean = false,
     ): Result<List<String>>
     ```
   - **Quy trình thực thi chi tiết (Per-Chapter AI Check + Batch QT Fallback):**
     - Bước 1: Khởi tạo mảng kết quả `results = arrayOfNulls<String>(originalLines.size)`.
     - Bước 2: Duyệt qua từng dòng. Nếu có `book` và `chapterIndices` tương ứng:
       - Kiểm tra cache AI (cả `PROVIDER_APP_AI` và `PROVIDER_LOCAL_AI`) theo canonical key `canonicalChapterTitleScopeKey(book.bookUrl, chapterIndices[i]) + ":title"`.
       - Nếu trúng cache AI: chuẩn hóa `restructureChapterNumbers().toTitleCase()`, gán vào `results[i]`.
       - Nếu dòng trống hoặc không chứa CJK: gán trực tiếp `results[i] = line`.
     - Bước 3: Thu thập các vị trí còn trống (`missingIndices`). Nếu `missingIndices` không rỗng:
       - Gộp các dòng bị miss để dịch batch bằng QT thông qua `executeLines(scopeKey, missingLines, book, contextText, forceRetranslate)`.
       - Điền kết quả dịch QT vào các vị trí tương ứng trong `results`.
     - Bước 4: Trả về danh sách đầy đủ, đảm bảo giữ nguyên 100% thứ tự chương ban đầu.
4. **Cập nhật `executeBookName` và `executeAuthorName`:**
   - Sử dụng canonical key `canonicalBookMetadataScopeKey(book.bookUrl, "name")` và `"author"`.
   - Kiểm tra cache AI trước (`PROVIDER_APP_AI`, `PROVIDER_LOCAL_AI`), nếu không có thì dịch bằng QT và định dạng Title Case.

### 2.3. Cập nhật `saveAiChapterTitle` & Ghi Đè Cache Khi Dịch AI
- Trong `TranslateDynamicUiTextUseCase.kt`:
  - `saveAiChapterTitle` hỗ trợ nhận rõ ràng `bookUrl: String, chapterIndex: Int` hoặc canonical scope key:
    ```kotlin
    suspend fun saveAiChapterTitle(
        bookUrl: String,
        chapterIndex: Int,
        originalText: String,
        aiTitle: String,
        provider: String = TranslationConstants.PROVIDER_APP_AI,
    )
    ```
  - Lưu đồng thời vào canonical scope key `canonicalChapterTitleScopeKey(bookUrl, chapterIndex) + ":title"`.
- Trong `TranslateChapterUseCase.kt` (lines 677–683):
  - Khi hoàn thành chunk/chương có `aiTitle` (từ timeline hoặc translation pipeline):
  - Gọi `translateDynamicUiTextUseCase.saveAiChapterTitle(book.bookUrl, bookChapter.index, bookChapter.title, aiTitle, provider)`.

### 2.4. Đồng bộ hóa TẤT CẢ Call Sites UI (Khắc phục Gap #3)
1. **`TocViewModel.kt` (line 328–345):**
   - Truyền danh sách `batch.map { it.index }` vào tham số `chapterIndices` của `executeChapterTitles`.
   - Nhờ đó, TOC lập tức hiển thị tiêu đề AI cho các chương đã dịch AI, trong khi các chương chưa dịch AI vẫn có bản dịch QT tức thì.
2. **`BookInfoViewModel.kt` (line 1573):**
   - Đổi scopeKey tra cứu tiêu đề chương mới nhất sang canonical key: `canonicalChapterTitleScopeKey(book.bookUrl, latestChapter.index)`.
3. **`HomeViewModel.kt` (line 419):**
   - Đổi scopeKey tra cứu tiêu đề chương đang đọc / mới nhất sang canonical key có chứa `chapterIndex`.
4. **`BookshelfViewModel.kt` & `ReadBookViewModel.kt`:**
   - Đảm bảo hiển thị nhất quán tiêu đề canonical đã được ưu tiên AI cache.

## 3. Files to Create/Modify
- `app/src/main/java/io/legado/app/domain/usecase/TranslateDynamicUiTextUseCase.kt`: Thêm canonical scope key helpers, rewrite `executeChapterTitles` với kiểm tra AI cache (`PROVIDER_APP_AI` & `PROVIDER_LOCAL_AI`) trước khi gom batch QT, cập nhật `executeChapterTitle`, `executeBookName`, `executeAuthorName`, `saveAiChapterTitle`.
- `app/src/main/java/io/legado/app/domain/usecase/TranslateChapterUseCase.kt`: Gọi lưu canonical AI title với provider tương ứng (`PROVIDER_APP_AI` hoặc `PROVIDER_LOCAL_AI`).
- `app/src/main/java/io/legado/app/ui/book/toc/TocViewModel.kt`: Truyền `chapterIndices` vào `executeChapterTitles`.
- `app/src/main/java/io/legado/app/ui/book/info/BookInfoViewModel.kt`: Đổi scopeKey sang canonical key có `chapterIndex`.
- `app/src/main/java/io/legado/app/ui/main/bookshelf/HomeViewModel.kt`: Đổi scopeKey sang canonical key có `chapterIndex`.

## 4. Test Criteria
- [x] Unit Test: Khi chưa có AI cache, `executeChapterTitle` và `executeChapterTitles` dịch 100% bằng QT.
- [x] Unit Test: Khi có `PROVIDER_APP_AI` cache, `executeChapterTitle` và `executeChapterTitles` trả về ngay bản dịch AI mà không gọi QT.
- [x] Unit Test: Khi có `PROVIDER_LOCAL_AI` cache, `executeChapterTitle` và `executeChapterTitles` trả về bản dịch Local AI thay thế QT.
- [x] Unit Test: `executeChapterTitles` với batch hỗn hợp (vừa có chương đã dịch AI, vừa có chương chưa dịch) kết hợp chính xác và đúng thứ tự ban đầu.
- [x] Unit Test: `executeBookName` và `executeAuthorName` giữ Title Case và tái cấu trúc số chương chuẩn mực.
