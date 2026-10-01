# Phase 03: Liên Thông Đồng Bộ Bộ Nhớ Dịch Giữa QT, NMT và AI

Status: ✅ Complete
Dependencies: Phase 01, Phase 02

## 1. Mục tiêu
Đảm bảo bộ nhớ dịch (Story Memory) thực sự được **sử dụng liên thông đúng theo thiết kế 2 chiều** giữa tất cả các provider (QT, NMT, AI):
1. Khi có Story Memory (do AI trích xuất hoặc người dùng thêm thủ công), QT và NMT phải áp dụng chuẩn xác toàn bộ danh từ riêng, thuật ngữ, và đại từ xưng hô từ quan hệ.
2. Khi người dùng chỉnh sửa trong Story Memory, các từ điển của QT/NMT phải được tự động đồng bộ (và ngược lại).
3. Đưa thông tin quan hệ xưng hô (Pronouns & Addressing từ `AiTranslationStoryRelationship`) vào bộ quy tắc thay thế xưng hô của QT/NMT thay vì chỉ ném vào prompt AI.

## 2. Phân tích nguyên nhân
- **Chỉ AI Refiner sinh dữ liệu:**
  Hiện tại chỉ có Stage 3 của AI Refiner là gọi `persistRefinerResult()`. QT và NMT chạy hoàn toàn thụ động, không đóng góp thực thể vào bộ nhớ.
- **QT và NMT nhận từ điển dạng phẳng, bỏ qua quan hệ xưng hô:**
  Trong `TranslateChapterUseCase.kt:575`, code gộp:
  `val dictionaries = mergeDictionaryTerms(primaryTerms = storyContext.memoryDictionary + bookDictionary.pairs, fallbackTerms = scopedQuickTerms)`
  - Tuy nhiên, `storyContext.currentRelationships` (ví dụ: "Triệu Kỳ là chủ nhân, Trí não là hệ thống") và `pronouns_addressing` chỉ được đóng gói vào `AiTranslationContextPack` gửi cho AI prompt (`AiTranslationRefinePipeline.kt:143`).
  - Trong khi đó, QT (`quickTranslationGateway.translate`) chỉ nhận `dictionaries` thô dạng `DictPair`. QT hoàn toàn không biết mối quan hệ này để đổi xưng hô (ví dụ: đổi "ta/ngươi" thành "chủ nhân/ngài" hoặc "sư phụ/đồ nhi").
  - Tương tự với NMT (`nmtTranslationGateway.translate`), NMT chỉ nhận danh sách cặp từ thay thế sau giải mã (post-replace), không có ngữ cảnh quan hệ.
- **⚠️ BUG ẨN — `memoryDictionary` getter bỏ sót World Building terms:**
  Tại `AiTranslationStoryContext.kt:290-291`:
  ```kotlin
  val memoryDictionary: List<DictPair>
      get() = entityDictionary
  ```
  Getter này **chỉ trả về dictionary từ entities** (tên nhân vật), **hoàn toàn bỏ qua** World Building terms (pháp bảo, công pháp, cảnh giới, địa danh...). Đây là nguyên nhân khiến QT/NMT không nhận được các thuật ngữ thế giới quan.
- **Sự phân mảnh giữa 2 kho lưu trữ:**
  - Story Memory lưu ở Room DB `ai_memory` (hoặc memory snapshot).
  - Quick Dictionary lưu ở Room DB `quick_dictionary_entries` và file `translation_dictionary.json`.
  - Hai bên không có cơ chế Reactive Sync (khi user sửa 1 từ ở Story Memory UI, file `translation_dictionary.json` không được cập nhật tương ứng; và khi sửa từ điển QT, chỉ khi tick lưu memory thì mới copy sang một nửa).

## 3. Các bước thực hiện
1. **Fix `memoryDictionary` getter — Gộp cả World Building terms:**
   ```kotlin
   // AiTranslationStoryContext.kt
   val memoryDictionary: List<DictPair>
       get() = entityDictionary + worldBuildingDictionary

   // Thêm computed property
   private val worldBuildingDictionary: List<DictPair>
       get() = currentWorldBuilding
           .filter { it.raw.isNotBlank() && it.target.isNotBlank() }
           .map { DictPair(raw = it.raw, target = it.target) }
   ```
2. **Đồng bộ hóa 2 chiều tự động (Bidirectional Sync Bridge):**
   - Xây dựng UseCase: `SyncStoryMemoryWithQuickDictionaryUseCase`:
     - Khi một Entity hoặc World Entry được tạo/chỉnh sửa trong Story Memory: Tự động upsert một bản ghi tương ứng trong `QuickDictionaryGateway` (scope = `PROJECT`, gắn với bookUrl hiện tại), với phân loại chuẩn (Entity -> `NAME`, World Entry -> `VIETPHRASE`/`TERM`).
     - Ngược lại, khi thêm từ trong Quick Dictionary có tick lưu Memory: Tạo đầy đủ bản ghi có liên kết `sourceKey`.
   - Đăng ký DI trong `di/appModule.kt`: `singleOf(::SyncStoryMemoryWithQuickDictionaryUseCase)`.
3. **Khai thác Mạng lưới Quan hệ (Relationship-driven Pronoun Adaptation) cho QT:**
   - Trong `QuickTranslationGateway`:
     - Bổ sung cơ chế tiêm quan hệ: chuyển đổi `storyContext.currentRelationships` thành các cặp thay thế có điều kiện (conditional replace rules) cho đoạn văn có chứa cả 2 thực thể.
     - Ví dụ: Trong đoạn có cả "Triệu Kỳ" và "智脑", tự động kích hoạt ánh xạ xưng hô "chủ nhân / phó não". Trong đoạn có cả "Hàn Lập" và "Mặc đại phu", ánh xạ xưng hô tương ứng "sư phụ / đồ nhi".
4. **Cập nhật NMT Dictionary Pipeline:**
   - Đảm bảo các thuật ngữ từ `storyContext.memoryDictionary` (đã fix bao gồm cả World Building) được ưu tiên tuyệt đối (highest priority) trong bảng so khớp token của NMT, ngăn chặn việc NMT dịch tách từ hoặc dịch lặp (như lỗi "phó não thông minh Lượng tử Trí năng Phó não" đã ghi nhận trong cache thực tế của chương 0).
5. **Invalidate Cache thông minh khi Bộ nhớ dịch thay đổi:**
   - Khi người dùng chỉnh sửa một Entity / Thuật ngữ trong Story Memory, hệ thống phải cập nhật `dictionaryRevision` và đánh dấu các cache chương bị ảnh hưởng là `STALE` để trình đọc nhận biết cần hiển thị bản dịch mới.

## 4. Files ảnh hưởng
- `app/src/main/java/io/legado/app/domain/model/AiTranslationStoryMemory.kt` (fix `memoryDictionary` getter)
- `app/src/main/java/io/legado/app/domain/usecase/TranslateChapterUseCase.kt`
- `app/src/main/java/io/legado/app/domain/usecase/TranslationStoryMemoryUseCase.kt`
- `app/src/main/java/io/legado/app/domain/usecase/SyncStoryMemoryWithQuickDictionaryUseCase.kt` (MỚI)
- `app/src/main/java/io/legado/app/di/appModule.kt` (đăng ký DI)
- `app/src/main/java/io/legado/app/domain/gateway/QuickTranslationGateway.kt`
- `app/src/main/java/io/legado/app/data/repository/QuickTranslationGatewayImpl.kt`
- `app/src/main/java/io/legado/app/data/repository/NmtTranslationRepositoryImpl.kt`

## 5. Tiêu chuẩn nghiệm thu
- [ ] Thêm hoặc sửa một tên nhân vật trong Story Memory (ví dụ "Triệu Kỳ" -> "Triệu Kì") -> Dịch lại bằng QT hoặc NMT áp dụng ngay lập tức tên mới.
- [ ] Dịch bằng QT một đoạn có quan hệ nhân vật đã xác định -> Xưng hô tuân theo quan hệ được trích xuất trong Story Memory.
- [ ] Kiểm tra tính toàn vẹn của cả 2 bảng `ai_memory` và `quick_dictionary_entries` luôn đồng nhất đối với cùng một đầu sách.
- [ ] Dịch bằng QT/NMT một đoạn có chứa thuật ngữ World Building (tên pháp bảo, công pháp) → Thuật ngữ được dịch đúng theo bộ nhớ dịch (trước đây bị bỏ qua do bug getter).
- [ ] Verify `memoryDictionary` getter trả về cả entity names VÀ world building terms.
