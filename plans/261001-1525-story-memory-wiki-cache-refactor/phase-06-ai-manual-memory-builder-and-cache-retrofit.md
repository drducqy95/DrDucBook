# Phase 06: Tính Năng Xây Dựng Bộ Nhớ Dịch Chuyên Dụng (AI/Thủ Công) & Tái Tinh Chỉnh Cache Chương Cũ (Retrofit)

Status: ✅ Done
Dependencies: Phase 03, Phase 04, Phase 05

## 1. Mục tiêu
1. **Giải quyết triệt để Vấn đề 1 (Chất lượng output kém với các chương dịch trước):**
   Xây dựng tính năng **"Làm mới & Tái tinh chỉnh bản dịch theo Bộ nhớ dịch mới nhất" (Retrofit Cache with Current Memory)**. Khi người dùng đã tích lũy được bộ nhớ dịch phong phú (sau khi đọc 10, 20 hoặc 50 chương), họ có thể 1-chạm nâng cấp lại bản dịch của các chương đầu (Chương 0, 1, 2...) mà không làm mất các đoạn người dùng đã khóa hoặc sửa thủ công.
2. **Giải quyết Vấn đề 7 (Bổ sung tính năng xây dựng bộ nhớ dịch chuyên dụng):**
   - **Xây dựng tự động bằng AI chuyên dụng (Dedicated AI Memory Extractor):**
     Tạo Preset AI `AiTaskType.EXTRACT_STORY_MEMORY` riêng biệt (thay vì phụ thuộc vào `SUMMARIZE_CHAPTER` như trước). Cho phép người dùng:
     - Chọn một khoảng chương (ví dụ: 10 chương đầu, hoặc toàn bộ chương đã tải) để AI phân tích và tự động kiến tạo toàn bộ hồ sơ nhân vật, quan hệ và thiết lập thế giới.
     - Phân tích trực tiếp từ một đoạn văn bản tóm tắt / bách khoa / văn án do người dùng dán vào (Direct Text/Wiki Ingestion).
   - **Xây dựng thủ công nâng cao (Advanced Manual Builder):**
     Giao diện form trực quan cho phép thêm mới hồ sơ nhân vật, thiết lập thế giới, tạo quan hệ kết nối giữa các nhân vật chỉ bằng vài cú chạm.
     Hỗ trợ Import / Export file JSON theo định dạng chuẩn.

## 2. Phân tích nguyên nhân & Cơ chế Retrofit
- **Tại sao bản dịch cũ kém chất lượng bị kẹt?**
  - Khi chương 0 được dịch lần đầu: `storyContext.currentEntities.isEmpty()` -> AI không có tên khóa -> dịch thô, xưng hô sai.
  - Kết quả được ghi vào file đĩa: `00000-8d8ceae5743ffde0.vi.app_ai.nb` và `metadata`.
  - Mặc dù hiện tại Story Memory đã có 51 bản ghi phong phú, nhưng khi mở lại chương 0:
    Trình đọc thấy file `.nb` đã tồn tại trên đĩa nên đọc thẳng từ file cache cũ ra hiển thị!
- **Cơ chế Retrofit (Tái tinh chỉnh thông minh):**
  - Hệ thống kiểm tra: Nếu `storyMemoryRevision` của bản dịch trong cache khác với `storyMemoryRevision` hiện tại của sách -> Hiển thị thông báo hoặc huy hiệu: *"Đã có 51 mục bộ nhớ dịch mới, có thể làm mới chương này"*.
  - Bổ sung nút: **"Tái tinh chỉnh bản dịch theo Bộ nhớ dịch mới" (Refine with Current Memory)** trên menu đọc sách và trong màn hình quản lý Story Memory.
  - Khi bấm chạy: Hệ thống gọi pipeline dịch/tinh chỉnh với đầy đủ 51 thực thể đã khóa, cập nhật đè lên cache file `.nb` và ghi nhận một bản revision mới (`RevisionStatus.MACHINE_DRAFT`).
  - **Bảo toàn user-locked segments:** Trước khi overwrite, đọc `.revisions.json` và `.chunks.jsonl` hiện có, giữ nguyên các chunk có `locked = true` hoặc `status = USER_EDITED`.

## 3. Các bước thực hiện
1. **Thêm Task Type & Prompt Preset Chuyên Biệt:**
   - Trong `AiModels.kt`: Bổ sung `const val EXTRACT_STORY_MEMORY = "extract_story_memory"`.
   - Trong `AiPromptCatalog.kt`: Tạo template mặc định `DEFAULT_EXTRACT_STORY_MEMORY_PROMPT` với hướng dẫn trích xuất bách khoa toàn thư tu tiên/huyền huyễn chuẩn định dạng JSON `AiTranslationStoryAnalysis`.
2. **Cập nhật UseCase `TranslationStoryMemoryUseCase.kt`:**
   - Thêm hàm:
     `suspend fun analyzeTextContent(book: Book, rawText: String, preset: AiTaskPresetConfig): Result<AiTranslationStoryAnalysis>`
   - Thêm hàm:
     `suspend fun batchAnalyzeChapters(book: Book, chapterRange: IntRange, preset: AiTaskPresetConfig, onProgress: (Int, Int) -> Unit): Result<Int>`
   - Thêm hàm:
     `suspend fun retrofitChapterTranslations(book: Book, chapterIndices: List<Int>, provider: String, onProgress: (Int, Int) -> Unit): Result<Int>`
   - Trong `retrofitChapterTranslations()`:
     - Load latest `storyMemorySnapshot` và `storyContext`.
     - Cho mỗi chương: Đọc `revisions.json` để tìm locked/user-edited chunks → giữ nguyên.
     - Gọi pipeline dịch/tinh chỉnh cho các unlocked chunks.
     - Overwrite `.nb` file và cập nhật `.meta.json` với `storyMemoryRevision` mới.
3. **Xây dựng UI Dialog / Màn hình Xây Dựng Bộ Nhớ Dịch:**
   - Trong `BookStoryMemoryScreen.kt` & `StoryWikiScreen.kt`:
     - Thêm Action: **"Kiến tạo Bộ nhớ dịch bằng AI"**:
       - Lựa chọn nguồn phân tích: [Từ các chương sách đã lưu] hoặc [Dán văn bản / Wiki bên ngoài].
       - Chọn khoảng chương cần phân tích (từ chương X đến chương Y).
       - Hiển thị tiến trình phân tích trực quan theo thời gian thực (Progress bar).
     - Thêm Action: **"Làm mới bản dịch các chương cũ theo Bộ nhớ dịch"**:
       - Chọn danh sách các chương cần làm mới (mặc định chọn các chương đã lưu).
       - Chạy tiến trình nền cập nhật lại bản dịch với Story Memory mới nhất.
       - Hiển thị badge "Outdated" trên các chương có `storyMemoryRevision` cũ.

## 4. Files ảnh hưởng
- `app/src/main/java/io/legado/app/domain/model/AiModels.kt`
- `app/src/main/java/io/legado/app/domain/model/AiPromptCatalog.kt`
- `app/src/main/java/io/legado/app/domain/usecase/TranslationStoryMemoryUseCase.kt`
- `app/src/main/java/io/legado/app/domain/usecase/TranslateChapterUseCase.kt`
- `app/src/main/java/io/legado/app/data/repository/TranslationCacheRepositoryImpl.kt`
- `app/src/main/java/io/legado/app/ui/translation/memory/BookStoryMemoryContract.kt`
- `app/src/main/java/io/legado/app/ui/translation/memory/BookStoryMemoryViewModel.kt`
- `app/src/main/java/io/legado/app/ui/translation/memory/BookStoryMemoryScreen.kt`

## 5. Tiêu chuẩn nghiệm thu
- [ ] Chọn "Kiến tạo bằng AI" -> AI tự động phân tích và sinh ra danh sách đầy đủ Thực thể, Quan hệ, Thế giới quan từ các chương sách.
- [ ] Dán một đoạn văn bản tóm tắt bên ngoài -> AI trích xuất thành công và bổ sung vào Story Memory của truyện.
- [ ] Bấm "Làm mới bản dịch các chương cũ" cho Chương 0 của *Chủ Thần Đại Đạo*: Bản dịch chương 0 được cập nhật đồng bộ các thuật ngữ và tên nhân vật mới nhất.
- [ ] Các đoạn user đã khóa/sửa thủ công được bảo toàn nguyên vẹn sau khi retrofit.
- [ ] Badge "Outdated" hiển thị đúng trên các chương có storyMemoryRevision cũ.
