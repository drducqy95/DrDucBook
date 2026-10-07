# Phase 03: Hoàn Thiện Pipeline Dịch Chương Bằng ML Kit (Từ Điển Bộ Nhớ Dịch, Từ Điển QT Dự Án & Sửa Đổi Xưng Hô Trung Tính)

Status: ✅ Complete
Dependencies: Không có

## 1. Mục tiêu (Objective)
1. Nâng cấp toàn diện bộ dịch chương offline Google ML Kit (`PROVIDER_ML_KIT`) trong `TranslateChapterUseCase.kt`:
   - Tự động áp dụng **Từ điển Bộ nhớ dịch (Story Translation Memory)**: nhận diện và ép các thực thể, thuật ngữ đã lưu trong bộ nhớ dịch vào văn bản dịch ML Kit.
   - Tự động áp dụng **Từ điển QT riêng của dự án (Project QuickDictionary & Book Terms)**: các từ vựng thuộc phạm vi Dự án của cuốn sách đang đọc.
   - Sửa đổi xưng hô sang **trung tính / phong cách tiểu thuyết** (`MlKitPronounNeutralizer`): chuyển đổi các đại từ nhân xưng thô của dịch máy (anh ấy -> hắn, cô ấy -> nàng, họ -> bọn họ...) theo chế độ xưng hô được cấu hình (`QuickTranslationPronounMode`).

## 2. Chi tiết yêu cầu kỹ thuật (Requirements)

### 2.1. Phân Tích & Xác Định Vị Trí Hook Trong Pipeline (Khắc phục Gap #6)
- **Hiện trạng trong `TranslateChapterUseCase.kt`:**
  - Tại dòng 1308–1319, nhánh `PROVIDER_ML_KIT` gọi:
    ```kotlin
    translateWithMlKitPreservingLayout(
        chunk = chunk,
        sourceContent = sourceContent,
        targetLanguage = targetLanguage,
        dictionaries = mergeDictionaryTerms(primaryTerms = dictSnapshot, fallbackTerms = quickTranslatorTerms),
        quickPhonetics = quickPhonetics,
    )
    ```
    Nhánh này **hoàn toàn bỏ quên** tham số `pronounMode = quickPronounMode` (trong khi nhánh QT dòng 1334 lại có).
  - Tại dòng 1915–1958, `translateWithMlKitPreservingLayout` chia văn bản theo đoạn văn và chỉ gọi `repairMlKitResidualCjk` khi có chữ Hán sót lại. Không có cơ chế ép từ điển hay trung tính hóa xưng hô cho phần văn bản ML Kit đã dịch ra tiếng Việt.
- **Vị trí Hook Chính Xác:**
  1. Cập nhật signature của `translateWithMlKitPreservingLayout`:
     ```kotlin
     private suspend fun translateWithMlKitPreservingLayout(
         chunk: TextChunk,
         sourceContent: String,
         targetLanguage: String,
         dictionaries: List<DictPair>,
         quickPhonetics: List<DictPair>,
         pronounMode: QuickTranslationPronounMode = QuickTranslationPronounMode.AUTO,
         storyMemorySnapshot: List<StoryMemoryEntity> = emptyList(),
     ): String
     ```
  2. Tại vòng lặp từng đoạn văn (lines 1936–1957) và nhánh fallback (lines 1924–1935):
     ```kotlin
     // 1. Dịch thô bằng ML Kit
     val translated = mlKitTranslationGateway.translate(...)
     // 2. Vá các tàn dư CJK còn sót lại (nếu có)
     val repaired = repairMlKitResidualCjk(...)
     // 3. ÉP TỪ ĐIỂN BỘ NHỚ DỊCH & QT DỰ ÁN (Hook Point 1)
     val enforced = enforceDictionariesForMlKit(
         sourceCjk = paragraph,
         translatedVi = repaired,
         dictionaries = dictionaries,
         canonicalMemory = storyMemorySnapshot,
     )
     // 4. TRUNG TÍNH HÓA XƯNG HÔ THEO PRONOUN MODE (Hook Point 2)
     val neutralized = MlKitPronounNeutralizer.neutralize(
         text = enforced,
         mode = pronounMode,
     )
     add(neutralized)
     ```

### 2.2. Thuật Toán Terminology Enforcer Cho ML Kit (Khắc phục Gap #7)
Xây dựng thuật toán `enforceDictionariesForMlKit`:
1. **Sắp xếp ưu tiên:** Sắp xếp danh sách `dictionaries` theo độ dài cụm CJK nguồn giảm dần (Greedy Longest Match), đảm bảo cụm dài (ví dụ: `漩涡鸣人`) được xử lý trước cụm con (`鸣人`).
2. **Quy trình khớp ngữ cảnh an toàn (Tránh false positive):**
   - Với mỗi cặp `(sourceCjkTerm, targetTerm)`:
     - Kiểm tra nếu `sourceCjk` đoạn gốc có chứa `sourceCjkTerm`:
       - *Trường hợp A:* Nếu `translatedVi` đã chứa sẵn `targetTerm` -> Bỏ qua, ML Kit hoặc vá lỗi đã dịch đúng.
       - *Trường hợp B:* Tìm các biến thể mà ML Kit thường dịch sai:
         - Phiên âm Hán-Việt cơ bản từ `quickTranslationGateway.hanViet(sourceCjkTerm)` (ví dụ: "Xoáy Ốc Minh Nhân").
         - Tra cứu cache dịch term đơn lẻ của ML Kit (được cache theo chunk để không gọi thừa API).
         - Nếu phát hiện biến thể này trong `translatedVi`, thay thế chính xác bằng `targetTerm`.
   - Với `canonicalMemory`:
     - Duyệt qua từng thực thể, kiểm tra các `aliases` tiếng Việt của thực thể đó. Nếu xuất hiện alias cũ/lệch thì thay thế bằng `canonicalTarget` của thực thể.
3. **Bảo vệ ranh giới từ (Word Boundaries):** Sử dụng regex ranh giới từ (`\b`) hoặc khoảng trắng khi thay thế cụm từ Latin/tiếng Việt để không vô tình thay thế vào giữa một từ khác.

### 2.3. Xây Dựng Bộ Chuẩn Hóa Xưng Hô Trung Tính (`MlKitPronounNeutralizer`)
Tạo object `MlKitPronounNeutralizer.kt` trong `domain/model/`:
```kotlin
object MlKitPronounNeutralizer {
    fun neutralize(
        text: String,
        mode: QuickTranslationPronounMode = QuickTranslationPronounMode.AUTO,
    ): String
}
```
- **Quy tắc chuyển đổi cụ thể theo từng `mode`:**
  - **`ANCIENT` (Cổ trang / Tiên hiệp / Huyền huyễn / Kiếm hiệp) hoặc `AUTO`:**
    - `(?i)\banh ấy\b` -> `hắn` (hoặc `y`)
    - `(?i)\bcô ấy\b` -> `nàng`
    - `(?i)\bông ấy\b` -> `ông ta` (hoặc `lão`)
    - `(?i)\bbà ấy\b` -> `bà ta`
    - `(?i)\bhọ\b` (ở đầu câu hoặc sau động từ chỉ nhóm người) -> `bọn họ`
    - `(?i)\bcác anh ấy\b / (?i)\bcác cô ấy\b` -> `bọn họ`
  - **`MODERN` (Đô thị / Hiện đại):**
    - `(?i)\banh ấy\b` -> `anh / hắn`
    - `(?i)\bcô ấy\b` -> `cô / nàng`
  - **`WESTERN` (Phương Tây):**
    - `(?i)\banh ấy\b` -> `chàng / hắn`
    - `(?i)\bcô ấy\b` -> `nàng / cô`
  - **`OFF`:** Giữ nguyên 100% bản dịch thô của ML Kit.
- **Bảo toàn ngữ cảnh hội thoại:** Không thay đổi đại từ nằm trong các câu thoại trực tiếp nếu có dấu ngoặc kép hoặc gạch đầu dòng, tôn trọng ngữ cảnh giao tiếp của nhân vật.

### 2.4. Tích Hợp Vào Caller Dispatcher
- Trong `TranslateChapterUseCase.kt` nhánh `PROVIDER_ML_KIT` (dòng 1308–1319):
  - Truyền `pronounMode = quickPronounMode`.
  - Lấy danh sách entity active từ `storyContextProvider().activeMemoryEntities` truyền vào `storyMemorySnapshot`.

## 3. Files to Create/Modify
- `app/src/main/java/io/legado/app/domain/model/MlKitPronounNeutralizer.kt`: Bộ chuẩn hóa đại từ nhân xưng trung tính cho bản dịch ML Kit.
- `app/src/main/java/io/legado/app/domain/usecase/TranslateChapterUseCase.kt`:
  - Thêm phương thức private `enforceDictionariesForMlKit`.
  - Cập nhật `translateWithMlKitPreservingLayout` nhận `pronounMode` & `storyMemorySnapshot`.
  - Hook `enforceDictionariesForMlKit` và `MlKitPronounNeutralizer.neutralize` sau khi sửa chữa tàn dư CJK.
- `app/src/test/java/io/legado/app/domain/model/MlKitPronounNeutralizerTest.kt`: Unit tests cho bộ xưng hô trung tính.
- `app/src/test/java/io/legado/app/domain/usecase/MlKitDictionaryEnforcerTest.kt`: Unit tests cho terminology enforcer.

## 4. Test Criteria
- [ ] Unit Test: Khi có thuật ngữ từ điển dự án (ví dụ `漩涡鸣人` -> `Uzumaki Naruto`), bản dịch ML Kit bắt buộc hiển thị `Uzumaki Naruto`.
- [ ] Unit Test: Khi có thực thể trong Story Translation Memory, bản dịch ML Kit bắt buộc sử dụng target chuẩn của thực thể thay vì dịch máy thô.
- [ ] Unit Test: "Anh ấy nhìn cô ấy và nói với họ" được chuyển đổi trung tính thành "Hắn nhìn nàng và nói với bọn họ" trong chế độ ANCIENT.
- [ ] Unit Test: Chế độ OFF giữ nguyên đại từ gốc của ML Kit.
- [ ] Unit Test: Đoạn văn không có từ điển hoặc không có CJK được giữ nguyên vẹn mà không làm biến dạng cấu trúc.
