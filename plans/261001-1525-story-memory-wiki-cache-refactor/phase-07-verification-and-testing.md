# Phase 07: Kiểm Thử Toàn Diện, Build Debug APK & Xác Minh Trên Thiết Bị Thực

Status: ✅ Done
Dependencies: Phase 01, Phase 02, Phase 02b, Phase 03, Phase 04, Phase 05, Phase 06

## 1. Mục tiêu
1. Viết và hoàn thiện các Unit test cho toàn bộ logic mới:
   - Test logic bảng Relationship (Entity 1 - Entity 2 - Type - Description).
   - Test tối ưu hóa hiệu năng QuickDictionarySelectionResolver (không còn đệ quy/sliding window chậm).
   - Test liên thông đồng bộ 2 chiều giữa Story Memory và Quick Dictionary.
   - Test trích xuất Story Memory bằng AI và cơ chế retrofit cache.
   - Test UI dropdown/compaction hoạt động đúng trên form QT.
2. Biên dịch Kotlin (`.\gradlew.bat :app:compileAppDebugKotlin`) và Assemble APK (`.\gradlew.bat :app:assembleAppDebug`).
3. Cài đặt trực tiếp lên thiết bị Huawei Nova HBN-LX9 (`2FK0224429001286`) và kiểm thử trực tiếp trên sách *Chủ Thần Đại Đạo*.

## 2. Test Cases Chi Tiết
1. **`StoryRelationshipModelTest.kt`:**
   - Kiểm tra ánh xạ draft -> relationship không bị nhầm lẫn giữa text gốc/bản dịch và chủ thể/đối tượng.
   - Kiểm tra hiển thị tiêu đề và nhãn quan hệ.
   - Kiểm tra round-trip: `toRelationship().toDraft().toRelationship()` phải idempotent.
2. **`QuickDictionarySelectionResolverPerformanceTest.kt`:**
   - Kiểm tra thời gian giải mã vị trí khi bôi đen text tiếng Việt không vượt quá 50ms cho đoạn văn dài 10,000 ký tự.
   - Kiểm tra không gọi candidateTranslator khi đã tìm thấy khớp Hán-Việt chính xác.
   - Kiểm tra `expandedForFallback()` KHÔNG trả về window quét toàn bộ chương (`start != 0 || endInclusive != sourceLength-1`).
3. **`StoryMemorySyncBridgeTest.kt`:**
   - Kiểm tra khi thêm một Entity nhân vật trong Story Memory, QuickDictionaryGateway tự động có bản ghi tương ứng.
   - Kiểm tra khi sửa một từ trong QuickDictionary có tick lưu memory, bản ghi trong Story Memory được cập nhật đồng bộ.
4. **`StoryMemoryDossierTest.kt`:**
   - Kiểm tra parse và serialize các trường mở rộng của hồ sơ nhân vật (cảnh giới, môn phái, pháp bảo liên kết).
   - Kiểm tra backward compat: entity CŨ không có `metadata` field → `characterProfile()` trả về `null` an toàn.
   - Kiểm tra round-trip: `withCharacterProfile(profile).characterProfile()` phải giữ nguyên dữ liệu.
5. **`CacheRetrofitTest.kt`:**
   - Kiểm tra cơ chế retrofit phát hiện cache cũ có revision khác với memory hiện tại và thực hiện cập nhật đè cache.
   - Kiểm tra locked/user-edited chunks KHÔNG bị overwrite trong quá trình retrofit.
6. **`MemoryDictionaryGetterTest.kt`:**
   - Kiểm tra `memoryDictionary` getter trả về cả entity dictionary VÀ world building terms.
   - Kiểm tra entity có `raw` rỗng hoặc `target` rỗng bị loại trừ.
7. **`StoryMemoryCategoryTest.kt`:**
   - Kiểm tra mỗi `StoryMemoryCategory` map đúng sang entity type / world category.
   - Kiểm tra `addQuickDictionaryEntry()` với category "ARTIFACT" tạo World Entry có category "weapon".

## 3. Quy trình thực thi
1. Chạy unit tests:
   ```bash
   .\gradlew.bat test --tests "io.legado.app.ui.quickdict.*" --tests "io.legado.app.domain.usecase.TranslationStoryMemoryUseCaseTest" --tests "io.legado.app.domain.model.AiTranslationStoryMemoryTest"
   ```
2. Biên dịch Kotlin debug: `.\gradlew.bat :app:compileAppDebugKotlin`
3. Đóng gói APK: `.\gradlew.bat :app:assembleAppDebug`
4. Deploy lên Huawei: `adb -s 2FK0224429001286 install -r app/build/outputs/apk/app/debug/app-app-debug.apk`
5. Kiểm chứng trực tiếp trên ứng dụng:
   - Mở sách *Chủ Thần Đại Đạo*
   - Thêm từ điển QT (verify dropdown UI, memory category selector)
   - Mở Story Memory (verify relationship editor 4 trường, không còn TranslationCaseControls)
   - Mở Story Wiki (verify book-first navigation, BackHandler)
   - Dịch lại chương 0 bằng QT (verify world building terms được áp dụng)
