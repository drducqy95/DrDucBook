# Phase 05: Tính Năng Sao Chép Toàn Bộ Ngữ Cảnh Chương (Raw & Cache Từng Provider)

Status: ✅ Complete
Dependencies: Không có

## 1. Mục tiêu (Objective)
1. Cung cấp tính năng sao chép toàn bộ nội dung văn bản của chương hiện tại (hoặc bất kỳ chương nào được chỉ định) vào Clipboard hệ thống an toàn (giới hạn 100k ký tự, chống crash TransactionTooLarge).
2. Cho phép người dùng linh hoạt lựa chọn phiên bản muốn sao chép:
   - **Bản gốc (Raw)**: Tiếng Trung gốc đã qua bộ lọc clean/replace rules (`BookHelp.getContent`).
   - **Bản dịch theo các Provider đã có cache**:
     - AI Provider (`PROVIDER_APP_AI` - Gemini, OpenAI, Claude, Antigravity...)
     - Local AI (`PROVIDER_LOCAL_AI` - GGUF Offline)
     - Quick Translator (`PROVIDER_QUICK_TRANSLATOR` - QT)
     - NMT Offline (`PROVIDER_NMT`)
     - Google Translate (`PROVIDER_GOOGLE`)
     - ML Kit (`PROVIDER_ML_KIT`)
   - **Bản viết lại (Rewrite)** nếu có cache.
3. Thiết kế giao diện BottomSheet trực quan (`ChapterContextCopySheet`), hiển thị danh sách các phiên bản có sẵn kèm số ký tự, trạng thái (Máy dịch / Đã duyệt / Đã sửa tay), nút Copy 1 chạm và thông báo Toast xác nhận.
4. Tích hợp điểm truy cập thuận tiện trong Trình đọc sách Compose (`ReadBookScreen` & `ReadBookViewModel`):
   - Nút hành động trực tiếp trong `TranslationProgressSheet`.
   - Nút hành động trong Menu Trình đọc (`ReadBookMenuBar` / More menu).

## 2. Chi tiết yêu cầu kỹ thuật (Requirements)

### 2.1. Cập Nhật Kiến Trúc Trình Đọc Compose (Khắc phục Gap #9)
- **Lưu ý kiến trúc quan trọng:** `ReadBookActivity` đã được retire hoàn toàn khỏi dự án. Trình đọc hiện là màn hình thuần Jetpack Compose (`ReadBookScreen.kt`) chạy trong `MainActivity` qua Jetpack Navigation 3 (`MainRouteReadBook` -> `ReadBookRouteScreen` -> `ReadBookScreen`).
- Toàn bộ trạng thái UI và tương tác tuân theo chuẩn MVI/UDF của `ReadBookContract.kt` và `ReadBookViewModel.kt`.

### 2.2. Domain & UI State Model (Khắc phục Gap #10: Id-Based Dispatch, Không Dùng Lambda Trong State)
- Để đảm bảo tính `@Stable` tuyệt đối của Compose UiState, **KHÔNG** đặt lambda `contentFetcher: suspend () -> String` trong model. Thay vào đó, sử dụng định danh `id` để ViewModel dispatch:
  ```kotlin
  @Stable
  data class ChapterContextVersionUi(
      val id: String, // "raw", "provider:app_ai", "provider:local_ai", "provider:quick_translator", "rewrite"
      val title: String, // "Bản gốc (Chữ Hán)", "Bản dịch AI (Gemini)", "Bản dịch QT", "Bản viết lại"
      val provider: String?,
      val charCount: Int,
      val statusDescription: String?, // "Bản thảo máy", "Đã sửa tay", "Hoàn tất"
      val isAvailable: Boolean,
      val previewText: String = "",
  )

  @Stable
  data class ChapterContextCopyUiState(
      val chapterTitle: String = "",
      val versions: ImmutableList<ChapterContextVersionUi> = persistentListOf(),
      val isLoading: Boolean = false,
  )
  ```

### 2.3. Quy Trình Nạp Dữ Liệu & Sao Chép Trong `ReadBookViewModel.kt`
1. **Truy xuất các phiên bản khả dụng:**
   - Khi nhận Intent `ReadBookIntent.OpenChapterContextCopy`:
     - Lấy `curBook = ReadBook.book` và `curChapter = ReadBook.curTextChapter?.chapter` (nếu null thì báo lỗi).
     - Đọc nội dung raw: `BookHelp.getContent(curBook, curChapter)?.let(TranslationContentSanitizer::sanitize)`.
     - Đọc danh sách provider caches: Gọi `TranslationManager.listProviderCachesForChapter(curBook, curChapter, targetLanguage)`.
     - Đọc bản rewrite (nếu có cache).
     - Tổng hợp danh sách `ChapterContextVersionUi`:
       - `raw`: Kèm số ký tự chữ Hán.
       - Từng `provider`: Kèm số ký tự dịch, trạng thái stale / user edited.
       - `rewrite`: Kèm số ký tự viết lại.
     - Cập nhật `_uiState.update { it.copy(activeSheet = ReadBookSheet.ChapterContextCopy, chapterContextCopy = ...) }`.
2. **Xử lý sao chép (Id-based copy execution):**
   - Khi nhận Intent `ReadBookIntent.CopyChapterContext(versionId: String)`:
     - Dựa vào `versionId`:
       - `"raw"`: Lấy nội dung từ `BookHelp.getContent(curBook, curChapter)`.
       - `"provider:$p"`: Lấy nội dung từ `TranslationManager.getCachedTranslation(curBook, curChapter, provider = p, targetLanguage)`.
       - `"rewrite"`: Lấy nội dung rewrite từ cache.
     - Kiểm tra nếu nội dung rỗng -> Báo Toast "Không có dữ liệu".
     - Nếu có nội dung -> Gọi extension an toàn: `context.sendToClip(content)`.
     - `sendToClip` tự động kiểm tra giới hạn 100,000 ký tự (tránh `TransactionTooLargeException`) và hiển thị Toast "Đã sao chép nội dung chương ([Tiêu đề phiên bản])".

### 2.4. Xây Dựng Giao Diện `ChapterContextCopySheet.kt`
- Sử dụng widget chuẩn của dự án: `AppModalBottomSheet` (hỗ trợ cả Material 3 Expressive và Miuix).
- Header: Tiêu đề chương + Tên sách.
- Body: Danh sách `NormalCard` cho từng phiên bản:
  - Icon phân biệt (📄 Bản gốc, 🤖 AI Cloud, 🧠 Local AI, ⚡ Quick Translator, ✍️ Rewrite).
  - Tên phiên bản, số lượng ký tự (`x.xxx ký tự`), tag trạng thái.
  - Nút Action Icon "Sao chép" (Copy) bên phải.
- Khi người dùng chạm nút Copy: Gửi Intent `CopyChapterContext(version.id)`.

### 2.5. Tích Hợp Lối Vào Trong UI
1. **Trong `TranslationProgressSheet.kt`:** Thêm nút hành động "Sao chép ngữ cảnh" cạnh thanh tiến độ dịch.
2. **Trong `ReadBookMenuBar.kt`:** Thêm mục "Sao chép ngữ cảnh chương" trong menu tác vụ mở rộng.

## 3. Files to Create/Modify
- `app/src/main/java/io/legado/app/domain/model/ChapterContextVersion.kt`: Khai báo model phiên bản ngữ cảnh chương.
- `app/src/main/java/io/legado/app/ui/book/read/sheet/ChapterContextCopySheet.kt`: BottomSheet hiển thị và kích hoạt sao chép phiên bản.
- `app/src/main/java/io/legado/app/ui/book/read/ReadBookContract.kt`: Thêm `ReadBookSheet.ChapterContextCopy`, `ReadBookIntent.OpenChapterContextCopy`, `ReadBookIntent.CopyChapterContext`, và `ChapterContextCopyUiState`.
- `app/src/main/java/io/legado/app/ui/book/read/ReadBookViewModel.kt`: Nạp danh sách phiên bản và xử lý intent sao chép.
- `app/src/main/java/io/legado/app/ui/book/read/ReadBookScreen.kt`: Mount `ChapterContextCopySheet`.
- `app/src/main/java/io/legado/app/ui/book/read/sheet/TranslationProgressSheet.kt`: Nút mở sheet sao chép.
- `app/src/main/java/io/legado/app/ui/book/read/ReadBookMenuBar.kt`: Tùy chọn menu sao chép ngữ cảnh.
- `app/src/main/res/values/strings.xml` & `values-vi/strings.xml`: Chuỗi hiển thị.

## 4. Test Criteria
- [ ] Unit Test: `listProviderCachesForChapter` trả về đầy đủ danh sách các provider đang có cache trên đĩa.
- [ ] UI Verification: Mở sheet sao chép -> hiển thị chính xác các card phiên bản khả dụng kèm số lượng ký tự.
- [ ] Clipboard Verification: Bấm sao chép bản Raw -> Clipboard nhận đúng toàn văn tiếng Trung gốc.
- [ ] Clipboard Verification: Bấm sao chép bản AI -> Clipboard nhận đúng toàn văn bản dịch AI.
- [ ] Buffer Safety Verification: Khi sao chép chương có trên 100,000 ký tự, `sendToClip` cắt tỉa an toàn mà không làm sập ứng dụng.
