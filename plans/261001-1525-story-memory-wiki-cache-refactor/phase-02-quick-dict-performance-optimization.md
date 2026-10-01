# Phase 02: Tối ưu Tốc độ Thêm Từ Điển QT & Làm Rõ Đích Đến trong Bộ Nhớ Dịch

Status: ✅ Complete
Dependencies: Phase 01

## 1. Mục tiêu
1. Xử lý triệt để tình trạng giật/lag/đơ vài giây khi mở form "Thêm từ điển QT" do thuật toán tìm kiếm đoạn gốc tiếng Trung chạy vòng lặp sliding window quét quadratic đệ quy toàn bộ chương.
2. Nâng cấp form thêm từ điển QT: khi tick "Lưu vào bộ nhớ dịch", người dùng được quyền lựa chọn chính xác từ này sẽ đi vào danh mục nào của Story Memory (Nhân vật, Thế lực, Địa danh, Pháp bảo, Công pháp, Cảnh giới, hay Thuật ngữ), kèm theo mô tả ngắn.

## 2. Phân tích nguyên nhân
- **Vấn đề hiệu năng chậm:**
  - Trong `QuickDictionarySelectionResolver.kt:126-134`:
    Khi `mappedSelection == null` (do người dùng bôi đen văn bản hoặc text map chưa sẵn sàng), code chạy:
    `translatedMatch = findTranslatedRange(..., searchWindow.expandedForFallback(source.length))`
    Hàm này duyệt qua từng ký tự của toàn bộ chương (có thể lên tới 5,000 - 10,000 ký tự), ở mỗi vị trí lặp thêm `minChars..maxChars` (10-12 lần), và tại mỗi bước gọi hàm `candidateTranslator(candidate)`.
    `candidateTranslator` thực hiện tra cứu toàn bộ cây Trie của QuickTranslator!
    Hàng chục ngàn lần tra cứu Trie chạy liên tục khiến main thread hoặc background worker bị nghẽn (CPU thrashing).
  - **Root cause cụ thể — `expandedForFallback()` tại line 327-333:**
    ```kotlin
    // BUG: quét toàn bộ chương từ vị trí 0 đến cuối
    fun expandedForFallback(sourceLength: Int) = SourceSearchWindow(
        start = 0,
        endInclusive = sourceLength - 1,
        ...
    )
    ```
- **Vấn đề phân loại mơ hồ:**
  - Trong `QuickDictionaryForm.kt:336`, chỉ có Checkbox: "Đồng thời lưu vào bộ nhớ dịch của truyện".
  - Code trong `TranslationStoryMemoryUseCase.kt:809-832` tự động ép kiểu: nếu QT Type là `NAME` thì lưu thành `entity` loại `"character"`, các trường hợp còn lại lưu thành `worldEntry` loại `"term"`.
  - Người dùng không có cách nào chỉ định một từ là "Địa danh", "Môn phái", hay "Pháp bảo", dẫn đến dữ liệu vào Story Memory bị phân loại sai hoặc thiếu thông tin.

## 3. Các bước thực hiện
1. **Tối ưu hóa `QuickDictionarySelectionResolver.kt`:**
   - **Fix `expandedForFallback()` — Giới hạn bán kính tìm kiếm nghiêm ngặt:**
     ```kotlin
     // SAU: giới hạn bán kính ± SEARCH_RADIUS_FALLBACK chars quanh vị trí ước lượng
     fun expandedForFallback(sourceLength: Int) = SourceSearchWindow(
         start = (approximateStart - SEARCH_RADIUS_FALLBACK).coerceAtLeast(0),
         endInclusive = (approximateEnd + SEARCH_RADIUS_FALLBACK).coerceAtMost(sourceLength - 1),
         ...
     )
     private const val SEARCH_RADIUS_FALLBACK = 320
     ```
   - **Tối ưu Han-Việt Fast Matcher:** So khớp phát âm Hán Việt trước khi gọi bộ dịch VietPhrase. Vì phát âm Hán Việt là 1-1 theo ký tự Hán, việc tìm kiếm chuỗi âm Hán Việt có độ phức tạp O(N) cực nhanh, loại bỏ 95% các lần gọi translator không cần thiết.
   - **Cache kết quả dịch trung gian:** Lưu cache LRU cho các cụm từ ngắn được dịch thử trong quá trình resolve.
2. **Bổ sung `StoryMemoryCategory` enum và fields trong `QuickDictionaryContract.kt`:**
   ```kotlin
   // Thêm enum phân loại bộ nhớ dịch
   enum class StoryMemoryCategory(val entityType: String?, val worldCategory: String?) {
       CHARACTER("character", null),
       FACTION(null, "faction"),
       LOCATION(null, "location"),
       ARTIFACT(null, "weapon"),
       TECHNIQUE(null, "technique"),
       REALM(null, "rank"),
       TERM(null, "term"),
   }

   // Thêm vào QuickDictionaryUiState
   val memoryCategory: StoryMemoryCategory = StoryMemoryCategory.TERM,
   val memoryDescription: String = "",

   // Thêm Intent
   data class SetMemoryCategory(val category: StoryMemoryCategory) : QuickDictionaryEditorIntent
   data class SetMemoryDescription(val value: String) : QuickDictionaryEditorIntent
   ```
3. **Nâng cấp `QuickDictionaryForm.kt`:**
   - Khi `saveToTranslationMemory == true`:
     - Hiển thị bộ chọn **Loại Bộ nhớ dịch (Story Memory Category):**
       - 👤 **Nhân vật (Character)**: Tự động điền type=character, category=character.
       - 🏰 **Thế lực / Môn phái (Faction)**: Lưu vào World building (category="faction").
       - 🗺️ **Địa danh / Vùng đất (Location)**: Lưu vào World building (category="location").
       - ⚔️ **Pháp bảo / Khí vật (Artifact/Weapon)**: Lưu vào World building (category="weapon"/"equipment").
       - 📜 **Công pháp / Chiêu thức (Technique)**: Lưu vào World building (category="technique").
       - 🌟 **Đẳng cấp / Cảnh giới (Realm/Rank)**: Lưu vào World building (category="rank").
       - 💡 **Thuật ngữ chung (Term)**: Lưu vào World building (category="term").
     - Bổ sung ô nhập **"Mô tả / Ghi chú ngữ cảnh"** (`memoryDescription`) (tùy chọn) để khi lưu vào Story Memory, AI có thể hiểu bối cảnh của thuật ngữ.
4. **Cập nhật `QuickDictionaryEditorViewModel.kt`:**
   - Thêm xử lý `SetMemoryCategory` và `SetMemoryDescription` intent trong `onIntent()`.
   - Cập nhật hàm `save()`: Truyền `memoryCategory` và `memoryDescription` từ form vào `addQuickDictionaryEntry()`.
5. **Cập nhật `TranslationStoryMemoryUseCase.kt`:**
   - Mở rộng signature `addQuickDictionaryEntry(book, raw, target, type, memoryCategory, description)`.
   - Sử dụng `memoryCategory` thay vì ép kiểu mặc định từ QT Type.

## 4. Files ảnh hưởng
- `app/src/main/java/io/legado/app/ui/quickdict/QuickDictionarySelectionResolver.kt`
- `app/src/main/java/io/legado/app/ui/quickdict/QuickDictionaryContract.kt`
- `app/src/main/java/io/legado/app/ui/quickdict/QuickDictionaryForm.kt`
- `app/src/main/java/io/legado/app/ui/quickdict/QuickDictionaryEditorViewModel.kt`
- `app/src/main/java/io/legado/app/domain/usecase/TranslationStoryMemoryUseCase.kt`
- `app/src/main/res/values/strings.xml` & `app/src/main/res/values-vi/strings.xml`

## 5. Tiêu chuẩn nghiệm thu
- [ ] Mở sheet "Thêm từ điển QT" từ trang đọc sách mượt mà tức thì (<200ms), không còn hiện tượng treo/đơ ứng dụng.
- [ ] Tick vào "Lưu vào bộ nhớ dịch của truyện" hiện ra bộ chọn phân loại rõ ràng (Nhân vật, Môn phái, Pháp bảo, Công pháp...) và ô mô tả.
- [ ] Chọn "Nhân vật" → Sau khi lưu, kiểm tra trong Story Memory xuất hiện Entity loại character.
- [ ] Chọn "Pháp bảo" → Sau khi lưu, kiểm tra trong Story Memory xuất hiện World Entry category="weapon".
- [ ] Mô tả ngữ cảnh (nếu nhập) được lưu đúng vào description của entity/world entry tương ứng.
