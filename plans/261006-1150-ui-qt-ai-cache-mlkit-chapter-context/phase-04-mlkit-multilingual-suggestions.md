# Phase 04: Đề Xuất Bản Dịch Bằng ML Kit Đa Ngôn Ngữ (Trung-Nhật, Trung-Anh, Trung-Hàn) Trong Quản Lý Từ Điển & Bộ Nhớ Dịch

Status: ✅ Complete
Dependencies: Không có

## 1. Mục tiêu (Objective)
1. Bổ sung tính năng đề xuất bản dịch bằng Google ML Kit cho các cụm từ muốn chuyển ngữ sang ngoại ngữ khác tiếng Việt:
   - **Trung - Nhật (`zh -> ja`)**: Tên nhân vật, thuật ngữ anime/manga/tiểu thuyết Nhật Bản (ví dụ: 漩涡鸣人 = Uzumaki Naruto, 宇智波佐助 = Uchiha Sasuke, 白眼 = Byakugan).
   - **Trung - Anh (`zh -> en`)**: Tên nhân vật, địa danh phương Tây (ví dụ: 凯撒 = Caesar, 亚历山大 = Alexander).
   - **Trung - Hàn (`zh -> ko`)**: Tên idol, diễn viên, nhân vật manhwa Hàn Quốc (ví dụ: 陈然竣 = Choi Yeon-jun, 金泰亨 = Kim Tae-hyung).
   - **Trung - Việt (`zh -> vi`)**: Dịch máy offline sang tiếng Việt.
2. Tự động chọn và sử dụng gói ngôn ngữ ML Kit đã tải sẵn trên máy theo cặp ngôn ngữ được chọn (`mlKitTranslationGateway.translate(text, targetLanguage, sourceLanguage = "zh")`).
3. Tích hợp tính năng đề xuất này đồng bộ tại 3 vị trí:
   - **Chỉnh sửa từ điển** (`QuickDictionaryManagerScreen` - `DictionaryEditorSheet`).
   - **Chỉnh sửa bộ nhớ dịch** (`BookStoryMemoryScreen` - `StoryMemoryEditorDialog` & `CharacterDossierSheet`).
   - **Thêm từ điển QT** (`QuickDictionaryForm` trong trình đọc `QuickDictionarySheet` & `QuickDictionaryEditorSheet`).

## 2. Chi tiết yêu cầu kỹ thuật (Requirements)

### 2.1. Nâng Cấp Domain UseCase `TranslateChapterUseCase.executeSuggestion` (Khắc phục Gap #8)
- **Signature Hiện Tại:**
  ```kotlin
  suspend fun executeSuggestion(
      text: String,
      provider: String,
      book: Book? = null,
      previousContext: String = "",
      nextContext: String = "",
      targetLanguage: String = TranslationConstants.TARGET_VIETNAMESE,
  ): Result<String>
  ```
- **Nâng Cấp Signature & Logic Điều Hướng:**
  ```kotlin
  suspend fun executeSuggestion(
      text: String,
      provider: String,
      book: Book? = null,
      previousContext: String = "",
      nextContext: String = "",
      targetLanguage: String = TranslationConstants.TARGET_VIETNAMESE,
      sourceLanguage: String? = null,
  ): Result<String>
  ```
- **Xử lý đặc thù theo ngôn ngữ đích (Language Target Isolation):**
  - Khi `provider == TranslationConstants.PROVIDER_ML_KIT`:
    - Xác định `resolvedSourceLang = sourceLanguage ?: inferMlKitSourceLanguageHint(source, targetLanguage) ?: "zh"`.
    - Gọi:
      ```kotlin
      val raw = mlKitTranslationGateway.translate(
          text = source,
          targetLanguage = targetLanguage,
          sourceLanguage = resolvedSourceLang,
      )
      ```
    - **Quan trọng:**
      - Nếu `targetLanguage == TranslationConstants.TARGET_VIETNAMESE`: Áp dụng sửa chữa tàn dư CJK (`repairMlKitResidualCjk`) và hậu xử lý tiếng Việt (`postProcessTranslation`).
      - Nếu `targetLanguage != "vi"` (ví dụ: `"ja"`, `"en"`, `"ko"`):
        - **BỎ QUA** `repairMlKitResidualCjk` (tránh chèn âm Hán-Việt vào bản dịch tiếng Anh/Nhật/Hàn).
        - **BỎ QUA** `VietnameseTranslationPostProcessor` (tránh làm sai lệch chữ hoa/thường hay ngữ pháp của tiếng Anh/Nhật).
        - Chỉ chuẩn hóa khoảng trắng và dấu câu cơ bản.

### 2.2. Định Nghĩa Model & Presets Các Cặp Ngôn Ngữ ML Kit
Tạo model trong `domain/model/MlKitModels.kt`:
```kotlin
@Stable
data class MlKitLanguagePair(
    val id: String,
    val sourceLang: String,
    val targetLang: String,
    val labelRes: Int, // R.string.lang_pair_zh_ja, ...
    val shortTag: String, // "JA", "EN", "KO", "VI"
) {
    companion object {
        val PRESETS = persistentListOf(
            MlKitLanguagePair("zh-ja", "zh", "ja", R.string.mlkit_pair_zh_ja, "JA"),
            MlKitLanguagePair("zh-en", "zh", "en", R.string.mlkit_pair_zh_en, "EN"),
            MlKitLanguagePair("zh-ko", "zh", "ko", R.string.mlkit_pair_zh_ko, "KO"),
            MlKitLanguagePair("zh-vi", "zh", "vi", R.string.mlkit_pair_zh_vi, "VI"),
        )
    }
}
```

### 2.3. Tích Hợp UI Đồng Bộ Tại 3 Vị Trí
1. **Thêm Từ Điển QT (`QuickDictionaryForm.kt`):**
   - Bổ sung hàng chip chọn cặp ngôn ngữ: `[JA: Trung-Nhật]`, `[EN: Trung-Anh]`, `[KO: Trung-Hàn]`, `[VI: Trung-Việt]`.
   - Khi bấm chọn một cặp: ViewModel kích hoạt `executeSuggestion(..., provider = PROVIDER_ML_KIT, targetLanguage = pair.targetLang, sourceLanguage = pair.sourceLang)`.
   - Kết quả trả về hiển thị dưới dạng Action Chip đề xuất. Bấm vào chip -> tự động điền vào ô `target`.
2. **Chỉnh Sửa Từ Điển QT (`QuickDictionaryManagerScreen.kt` - `DictionaryEditorSheet`):**
   - Thêm nút/chip chọn cặp ngôn ngữ ML Kit tương tự.
   - Bấm vào đề xuất -> cập nhật ô `target` của từ điển.
3. **Chỉnh Sửa Bộ Nhớ Dịch (`BookStoryMemoryScreen.kt` - `StoryMemoryEditorDialog` & `CharacterDossierSheet.kt`):**
   - Hỗ trợ đề xuất ML Kit đa ngữ cho tên nhân vật và biệt danh, đặc biệt hữu ích cho các tác phẩm đồng nhân anime hoặc bối cảnh thế giới hiện đại/phương Tây.

## 3. Files to Create/Modify
- `app/src/main/java/io/legado/app/domain/model/MlKitModels.kt`: Khai báo `MlKitLanguagePair` presets.
- `app/src/main/java/io/legado/app/domain/usecase/TranslateChapterUseCase.kt`: Thêm tham số `sourceLanguage` vào `executeSuggestion`, cách ly hậu xử lý tiếng Việt khi target != "vi".
- `app/src/main/java/io/legado/app/ui/quickdict/QuickDictionaryForm.kt`: Thêm bộ chọn cặp ngôn ngữ và chip đề xuất.
- `app/src/main/java/io/legado/app/ui/quickdict/QuickDictionaryEditorViewModel.kt`: Xử lý Intent đề xuất ML Kit đa ngữ.
- `app/src/main/java/io/legado/app/ui/config/translation/dictionary/QuickDictionaryManagerScreen.kt`: Thêm chọn cặp ngôn ngữ ML Kit trong `DictionaryEditorSheet`.
- `app/src/main/java/io/legado/app/ui/config/translation/dictionary/QuickDictionaryManagerViewModel.kt`: Handler cho đề xuất ML Kit.
- `app/src/main/java/io/legado/app/ui/translation/memory/BookStoryMemoryScreen.kt`: Thêm đề xuất ML Kit trong dialog/sheet.
- `app/src/main/java/io/legado/app/ui/translation/memory/BookStoryMemoryViewModel.kt`: Handler cho đề xuất ML Kit.
- `app/src/main/res/values/strings.xml` & `values-vi/strings.xml`: Chuỗi hiển thị tên các cặp ngôn ngữ.

## 4. Test Criteria
- [ ] Unit Test: `executeSuggestion` với `provider = PROVIDER_ML_KIT` và `targetLanguage = "ja"` trả về bản dịch tiếng Nhật, không chạy bộ lọc tiếng Việt.
- [ ] Unit Test: `executeSuggestion` với `targetLanguage = "en"` trả về bản dịch tiếng Anh (ví dụ "Caesar" cho "凯撒").
- [ ] Unit Test: Nếu chưa tải gói ML Kit tương ứng, trả về `MlKitMissingLanguageModelException` thông báo rõ ràng mã ngôn ngữ cần tải.
- [ ] UI Verification: Bấm chọn cặp Trung - Nhật -> đề xuất hiển thị ngay trên UI form từ điển.
