# Phase 01: Công cụ Tạo & Truy vấn Bộ nhớ Dịch Cốt truyện & Chuẩn hóa Hiển thị Số chương Timeline

## 1. Mục tiêu
1. Bổ sung các công cụ Agent/Chatbot để tự động tạo, truy vấn và đồng bộ bộ nhớ dịch cốt truyện (`create_story_memory`, `get_story_memory`, `retrofit_story_translations`).
2. **Sửa đổi hiển thị ở Bộ nhớ dịch & Timeline:** Viết **đúng số chương thực tế và tiêu đề chương** (truy xuất từ `BookChapter.title` hoặc phân tích số chương tự nhiên như "Chương 458: ...", "Chương 12: ...", "Tiết tử: ...") thay vì hiển thị theo số thứ tự cứng nhắc 1, 2, 3, 4 theo index.

---

## 2. Thiết kế chi tiết các công cụ

### 2.1. `create_story_memory` (Tạo bộ nhớ dịch)
- **Tên đăng ký:** `create_story_memory` (alias: `analyze_story_memory`).
- **Đầu vào (Arguments):**
  - `bookUrl` (String, tùy chọn): URL của cuốn sách trên kệ.
  - `bookName` (String, tùy chọn): Tên sách (dùng để tìm kiếm sách trên kệ nếu không có `bookUrl`).
  - `chapterIndex` (Int, tùy chọn): Chỉ số chương cần phân tích (nếu lấy từ sách).
  - `chapterCount` (Int, mặc định 1): Số lượng chương cần phân tích liên tiếp.
  - `content` (String, tùy chọn): Đoạn văn bản thô trực tiếp cần phân tích bóc tách ký ức (nếu người dùng paste trực tiếp vào chat).
  - `chapterTitle` (String, tùy chọn): Tiêu đề chương tương ứng.
- **Quy trình xử lý:**
  1. Nếu có `content`: Lấy tiêu đề chương thực tế hoặc định dạng lại tiêu đề qua `formatVietnameseChapterTitle`, gọi `translationStoryMemoryUseCase.analyzeTextContent(bookUrl, resolvedTitle, chapterIndex ?: 0, content)`.
  2. Nếu không có `content` nhưng có `chapterIndex`: Lấy `BookChapter` từ Room DB (`bookChapterDao`), lấy tiêu đề chương thực tế (chứa số chương chuẩn của truyện), đọc nội dung từ cache/nguồn, sau đó gọi `analyzeChapter`.
  3. Trả về kết quả JSON thống kê số thực thể (`entities`), thế giới quan (`worldBuilding`), quan hệ (`relationships`) và mốc thời gian (`timelines`) vừa được trích xuất và lưu vào DB.
- **Rủi ro:** `AgentActionRisk.WRITE`.
- **Nhóm quyền:** `ChatbotToolCategory.DICTIONARY_AND_MEMORY`.

### 2.2. `get_story_memory` (Truy vấn bộ nhớ cốt truyện)
- **Tên đăng ký:** `get_story_memory` (alias: `list_story_memories`).
- **Đầu vào:**
  - `bookUrl` (String, tùy chọn) hoặc `bookName` (String, tùy chọn).
  - `filter` (String, tùy chọn: "all", "entities", "world", "relationships", "timeline", mặc định "all").
  - `limit` (Int, mặc định 50).
- **Quy trình xử lý:**
  1. Xác định cuốn sách theo `bookUrl` hoặc `bookName`.
  2. Gọi `translationStoryMemoryUseCase.loadSnapshotWithSeriesInheritance(book.bookUrl)`.
  3. Đảm bảo các timeline record trả về mang tiêu đề chương thực tế đã chuẩn hóa.
  4. Trả về cấu trúc JSON sạch, dễ đọc cho AI.
- **Rủi ro:** `AgentActionRisk.READ` (An toàn, tự duyệt).

### 2.3. `retrofit_story_translations` (Cập nhật đồng bộ bản dịch cũ)
- **Tên đăng ký:** `retrofit_story_translations`.
- **Đầu vào:**
  - `bookUrl` (String, tùy chọn) hoặc `bookName` (String, tùy chọn).
  - `dryRun` (Boolean, mặc định false): Chỉ kiểm tra số lượng chương bị ảnh hưởng mà không ghi đè.
- **Quy trình xử lý:**
  1. Gọi `translationStoryMemoryUseCase.retrofitChapterTranslations(book.bookUrl)`.
  2. Bắn sự kiện `EventBus.REFRESH_BOOK_CONTENT` để làm mới Reader nếu đang đọc cuốn sách này.
  3. Trả về số chương đã được chuẩn hóa lại thuật ngữ.
- **Rủi ro:** `AgentActionRisk.WRITE`.
- **Nhóm quyền:** `ChatbotToolCategory.DICTIONARY_AND_MEMORY`.

---

## 3. Sửa đổi Chuẩn hóa Hiển thị Số chương & Bản dịch Tiêu đề Timeline trong Bộ nhớ dịch

### 3.1. Hiện trạng vấn đề & Phân tích nguyên nhân gốc rễ
1. **Lọt tiêu đề thô tiếng CJK (Raw Title Leakage):**
   - Trong `BookStoryMemoryViewModel.kt:251`:
     ```kotlin
     // Hiện tại:
     title = timeline.chapterTitle.ifBlank { "Chương ${timeline.chapterIndex + 1}" }
     ```
   - Khi `timeline.chapterTitle` chứa chuỗi CJK gốc (ví dụ: `第458章 消费任务已完成`), ViewModel gán trực tiếp vào `StoryMemoryItemUi.title` mà **không hề dịch**, khiến người đọc nhìn thấy tiêu đề tiếng Trung thô ráp ngay trên UI Bộ nhớ dịch.
2. **Số thứ tự cứng nhắc 1, 2, 3, 4 làm sai lệch chương sách:**
   - Khi `timeline.chapterTitle` rỗng, hệ thống fallback thành `"Chương ${timeline.chapterIndex + 1}"`.
   - Nếu một cuốn sách bắt đầu từ chương 458 (hoặc timeline được ghi nhận ở các chương xa), người dùng lại thấy hiển thị "Chương 1", "Chương 2", "Chương 3"... Đây là số thứ tự index cứng nhắc, gây nhầm lẫn nghiêm trọng với diễn biến truyện thực tế.
3. **Khiếm khuyết trong `formatVietnameseChapterTitle` hiện tại:**
   - Trong `TranslationStoryMemoryUseCase.kt:809`:
     ```kotlin
     fun formatVietnameseChapterTitle(bookUrl: String, chapterIndex: Int, title: String): String {
         if (title.isBlank()) return "Chương ${chapterIndex + 1}"
         if (!title.containsCjk()) return title.restructureChapterNumbers().toTitleCase()
         val translated = quickTranslationGateway.translate(title)
         return translated.restructureChapterNumbers().toTitleCase()
     }
     ```
   - Nếu `title` truyền vào rỗng, hàm lập tức trả về `"Chương ${chapterIndex + 1}"` mà **không hề tra cứu mục lục thực tế từ Room DB** (`appDb.bookChapterDao.getChapter(bookUrl, chapterIndex)?.title`).

---

### 3.2. Giải pháp thực thi triệt để

1. **Nâng cấp `formatVietnameseChapterTitle` trong `TranslationStoryMemoryUseCase.kt`:**
   - Tiếp nhận `bookUrl`, `chapterIndex`, `title`.
   - **Bước 1 (Truy xuất tiêu đề mục lục thực tế nếu rỗng):**
     Nếu `title.isBlank()`:
     Truy vấn `appDb.bookChapterDao.getChapter(bookUrl, chapterIndex)?.title` từ cơ sở dữ liệu để lấy tên chương gốc thực tế của sách.
   - **Bước 2 (Dịch hoàn toàn sang tiếng Việt - Không để Raw CJK):**
     Nếu tiêu đề (dù lấy từ tham số hay từ DB) chứa ký tự CJK (`containsCjk()`):
     Gọi `quickTranslationGateway.translate(rawTitle)` để dịch offline nhanh (dưới 1ms) sang tiếng Việt theo đúng từ điển thuật ngữ.
   - **Bước 3 (Chuẩn hóa cấu trúc số chương tự nhiên & TitleCase):**
     Áp dụng `restructureChapterNumbers().toTitleCase()`.
     Chuyển đổi các định dạng như "Đệ 458 Chương", "Thứ 458 Chương" thành `"Chương 458: ..."`. Giữ nguyên các định dạng tự nhiên như "Tiết tử: ...", "Ngoại truyện: ...", "Hồi 12: ...".
   - **Bước 4 (Fallback an toàn):**
     Chỉ khi cả tiêu đề tham số và tiêu đề trong DB đều rỗng thì mới fallback về `"Chương ${chapterIndex + 1}"`.

2. **Áp dụng chuẩn hóa đồng bộ trong `BookStoryMemoryViewModel.kt`:**
   - Khi chuyển đổi `snapshot.timelines` sang danh sách `StoryMemoryItemUi`:
     ```kotlin
     val displayTitle = storyMemoryUseCase.formatVietnameseChapterTitle(
         bookUrl = bookUrl,
         chapterIndex = timeline.chapterIndex,
         title = timeline.chapterTitle,
     )
     add(
         StoryMemoryItemUi(
             id = TranslationStoryMemoryUseCase.timelineKey(timeline.chapterIndex),
             kind = AiTranslationStoryMemoryKind.TIMELINE,
             title = displayTitle, // Luôn hiển thị bản dịch tiếng Việt, đúng số chương thực tế
             subtitle = details.ifBlank { timeline.summary },
             chapterIndex = timeline.chapterIndex.takeIf { it >= 0 },
         )
     )
     ```

3. **Đồng bộ hóa trong snapshot và lưu trữ:**
   - Trong `TranslationStoryMemoryUseCase.buildWikiSnapshot`:
     Đảm bảo `timeline.chapterTitle` khi lưu hoặc build wiki record đều đi qua `formatVietnameseChapterTitle`.

---

## 4. Tiêu chí kiểm thử
- **Kiểm thử Bản dịch tiêu đề (Không để raw):**
  - Timeline có tiêu đề CJK `第458章 消费任务已完成` -> Hiển thị trên UI Bộ nhớ dịch là `"Chương 458: Nhiệm Vụ Tiêu Phí Đã Hoàn Thành"`. Tuyệt đối không còn bất kỳ chữ Hán thô nào trên giao diện.
- **Kiểm thử Số chương tự nhiên (Không thứ tự cứng nhắc 1, 2, 3, 4):**
  - Timeline của chương mục lục số 458 nhưng `chapterTitle` trong snapshot bị rỗng -> Hệ thống tra cứu Room DB và hiển thị đúng `"Chương 458: ..."` thay vì `"Chương 1"`.
- **Kiểm thử công cụ:**
  - Gọi `create_story_memory` trên một chương cụ thể -> Tiêu đề timeline được dịch sang tiếng Việt và lưu trữ đúng định dạng chuẩn.
  - Gọi `get_story_memory` -> Trả về JSON với tiêu đề chương đã được dịch và chuẩn hóa số chương.
