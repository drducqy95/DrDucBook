# Phase 03 — LocalAI, Google ML Kit, cache dịch và dịch truyện tranh

## 1. Kết quả phải đạt

Người dùng có thể cấu hình và test LocalAI GGUF, quản lý gói ngôn ngữ ML Kit từ UI, không thể dịch ML Kit khi thiếu model, chốt bản dịch chương để chống ghi đè và dùng pipeline OCR/dịch/overlay cho truyện tranh.

## 2. Phạm vi

### Trong phạm vi

- Local GGUF import/inspect/load/generate/unload và cấu hình Router.
- UI quản lý model ML Kit: download/delete/batch/progress/error.
- Chặn dịch khi thiếu source/target language pack.
- Chuẩn hóa lỗi AI provider trong dịch chương.
- Translation revision: machine draft, user edited, final.
- UI so sánh raw/cache/final và thao tác chốt/mở khóa.
- Reader ưu tiên final translation.
- Manga OCR, block normalization, translation overlay và cache.
- Manual correction và export ảnh/CBZ/PDF dịch.

### Ngoài phạm vi

- Tự động tải ML Kit model không xin phép.
- Cloud OCR ngoài registry đã hỗ trợ.
- Inpainting AI chất lượng cao ở release đầu; phase đầu dùng background estimation không phá ảnh gốc.
- Huấn luyện hoặc quantize model GGUF trên thiết bị.

## 3. LocalAI/GGUF

### P3.1 — Hoàn thiện runtime packaging

- Kiểm tra `legado_local_ai` được đóng gói theo ABI thực sự hỗ trợ.
- Nếu native library thiếu, UI báo ABI/runtime cụ thể; không cho profile ready.
- Import qua SAF, kiểm tra `.gguf`, magic bytes, free storage và checksum với model đã pin.
- Lưu model trong external app files, import atomic bằng file `.importing`.
- Hiển thị file size, SHA-256, context, thread/batch/gpu profile và RAM estimate.

### P3.2 — LocalAI configuration và test

- Tile LocalAI ở Router mở model manager/popup riêng.
- Danh sách model có search, select, test, unload và delete.
- Probe bắt buộc: inspect → load → generate token → unload hoặc giữ warm theo policy.
- Translation dùng prompt/budget LocalAI hiện có; chat dùng generic chat template tương thích model metadata.
- Một native context dùng mutex/queue; cancel phải gọi native cancel và không làm hỏng handle kế tiếp.

## 4. Google ML Kit

### P3.3 — Nối UI model manager

- Đăng ký `MlKitModelsViewModel` trong Koin.
- Thêm `MainRouteSettingsMlKitModels` và entry Navigation 3.
- Thêm mục `Gói ngôn ngữ ML Kit` trong Translation Settings.
- Hiển thị installed/not installed/downloading/deleting/error và dung lượng ước tính.
- Download/delete từng model, batch selected, download-all selected và cancel.

### P3.4 — Prerequisite policy

- Trước `MlKitTranslationGateway.translate`, resolve mã ngôn ngữ nguồn/đích.
- Kiểm tra cả hai model trong model manager.
- Nếu thiếu, throw typed error chứa danh sách model thiếu.
- UI mở sheet giải thích dung lượng và nút tải; chỉ retry khi download pass.
- Mất mạng trong lúc tải giữ state retryable; không xóa model đã tải thành công trước đó.
- Dịch offline phải pass sau khi tắt mạng.

## 5. Cache dịch và chốt bản dịch

### P3.5 — Revision model

Mỗi chapter translation có:

- `status`: `MACHINE_DRAFT`, `USER_EDITED`, `FINAL`.
- Raw content hash, dictionary revision, provider/model/prompt revision.
- `createdAt`, `updatedAt`, `finalizedAt` và actor.
- Parent revision để diff/rollback.

Policy:

- Background translation chỉ được thay `MACHINE_DRAFT` cùng chapter/hash.
- `USER_EDITED` và `FINAL` không bị retry/provider fallback ghi đè.
- Raw content đổi làm revision cũ `STALE`; final vẫn xem được nhưng Reader cảnh báo nếu hash khác.
- Mở khóa final tạo revision editable mới, không sửa row lịch sử.

### P3.6 — UI/Reader

- Chapter action mở màn so sánh Raw/Machine/Final.
- Cho sửa draft, save user edit, finalize, unlock, diff và restore.
- Reader chọn ưu tiên `FINAL > USER_EDITED > MACHINE_DRAFT > RAW` theo chế độ user.
- Ebook clone lấy đúng raw/cache/final, không trộn revision giữa chapter.

## 6. Dịch truyện tranh

### P3.7 — Pipeline dữ liệu

1. Tạo image hash và load tiled/subsampled.
2. OCR ML Kit theo script Latin/Japanese/Chinese/Korean.
3. Chuẩn hóa text block, bounding polygon, confidence, orientation và reading order.
4. Nhóm block thành bubble/region; cho user sửa order.
5. Dịch qua ML Kit hoặc AI Route.
6. Ước lượng background bằng sampling vùng biên/blur-fill.
7. Fit font, line break, alignment và render overlay.
8. Cache OCR/translation/layout theo image hash + OCR/model/prompt version.

### P3.8 — Reader UX và chỉnh sửa

- Toggle `Chế độ dịch` ở toolbar Reader.
- Hiển thị progress theo trang: OCR, translating, rendering.
- Prefetch giới hạn một trang kế tiếp, dừng khi memory pressure.
- Overlay dùng cùng transform matrix với ảnh khi zoom/pan.
- Tap block mở editor raw/translated/font/color/box/order.
- Toggle Original/Translated và xóa cache trang.
- Original image luôn bất biến.

### P3.9 — Export

- Render bản dịch ra file mới, không ghi đè ảnh nguồn.
- Export selected pages hoặc chapter thành image set, CBZ hoặc PDF.
- Manifest ghi source hash, model/prompt và thời điểm export.

## 7. File tác động

### File hiện có cần sửa

- `app/src/main/java/io/legado/app/data/repository/LocalAiEngineRepository.kt`
- `app/src/main/java/io/legado/app/data/repository/ai/LocalGgufHandler.kt`
- `app/src/main/java/io/legado/app/domain/gateway/LocalAiEngineGateway.kt`
- `app/src/main/java/io/legado/app/domain/model/LocalAiModels.kt`
- `app/src/main/java/io/legado/app/domain/usecase/TranslateChapterUseCase.kt`
- `app/src/main/java/io/legado/app/data/repository/MlKitTranslationRepository.kt`
- `app/src/main/java/io/legado/app/domain/gateway/MlKitTranslationGateway.kt`
- `app/src/main/java/io/legado/app/ui/config/translation/mlkit/MlKitModelsContract.kt`
- `app/src/main/java/io/legado/app/ui/config/translation/mlkit/MlKitModelsViewModel.kt`
- `app/src/main/java/io/legado/app/ui/config/translation/mlkit/MlKitModelsScreen.kt`
- `app/src/main/java/io/legado/app/ui/config/translation/TranslationConfigScreen.kt`
- `app/src/main/java/io/legado/app/data/entities/TranslationCache.kt`
- `app/src/main/java/io/legado/app/data/repository/TranslationCacheRepositoryImpl.kt`
- `app/src/main/java/io/legado/app/domain/gateway/TranslationCacheGateway.kt`
- `app/src/main/java/io/legado/app/ui/book/read/ReadBookContract.kt`
- `app/src/main/java/io/legado/app/ui/book/read/ReadBookViewModel.kt`
- `app/src/main/java/io/legado/app/ui/book/read/ReadBookScreen.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavKey.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavGraph.kt`
- `app/src/main/java/io/legado/app/di/appModule.kt`
- `app/src/main/java/io/legado/app/data/AppDatabase.kt`
- `app/build.gradle.kts`

### File tạo mới dự kiến

- `app/src/main/java/io/legado/app/domain/model/TranslationRevision.kt`
- `app/src/main/java/io/legado/app/ui/translation/revision/TranslationRevisionContract.kt`
- `app/src/main/java/io/legado/app/ui/translation/revision/TranslationRevisionViewModel.kt`
- `app/src/main/java/io/legado/app/ui/translation/revision/TranslationRevisionScreen.kt`
- `app/src/main/java/io/legado/app/domain/manga/MangaTranslationModels.kt`
- `app/src/main/java/io/legado/app/domain/usecase/TranslateMangaPageUseCase.kt`
- `app/src/main/java/io/legado/app/data/repository/MangaOcrRepository.kt`
- `app/src/main/java/io/legado/app/data/repository/MangaTranslationCacheRepository.kt`
- `app/src/main/java/io/legado/app/ui/book/read/manga/MangaTranslationOverlay.kt`
- `app/src/main/java/io/legado/app/ui/book/read/manga/MangaTranslationEditorSheet.kt`

## 8. Test bắt buộc phải pass

### Test hiện có

```powershell
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.LocalAiModelsTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.LocalAiTranslationPromptTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.TranslationCacheRepositoryImplTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.AiTranslationLayoutProtocolTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.AiTranslationChunkPipelineTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.ui.book.read.ReaderTranslationModePolicyTest"
```

### Test mới

- GGUF invalid magic, insufficient storage, cancel import, unsupported ABI và native load failure.
- Local native probe/cancel/reload không leak handle.
- ML Kit thiếu source/target model trả đúng typed error.
- Download batch partial success và retry.
- Offline translation sau download.
- Final revision không bị background overwrite.
- Unlock tạo revision mới và restore đúng.
- Reader precedence raw/machine/edited/final.
- OCR reading order ngang/dọc, low confidence và rotated block.
- Overlay transform đúng khi zoom/pan/rotation.
- Image hash invalidation và export không thay đổi file nguồn.

### Instrumentation/build

```powershell
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain
.\gradlew.bat :app:connectedAppDebugAndroidTest --no-daemon --console=plain
```

Nox gate:

1. Import/test một GGUF hợp lệ hoặc xác nhận thông báo ABI chính xác.
2. Mở ML Kit model manager, tải cặp ngôn ngữ, tắt mạng và dịch.
3. Thử dịch trước khi tải phải bị chặn bằng CTA tải model.
4. Chốt một chapter, chạy dịch lại và xác nhận final không đổi.
5. Bật manga translate, zoom/pan, sửa một block và export file mới.

## 9. Điều kiện đóng phase

- LocalAI có cấu hình, test và lỗi actionable.
- ML Kit model manager truy cập được và prerequisite được enforce.
- Final translation bất biến trước automation.
- Manga overlay chính xác, chỉnh sửa được và không phá ảnh nguồn.
