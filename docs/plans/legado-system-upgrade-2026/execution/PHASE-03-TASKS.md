# Phase 03 — LocalAI, ML Kit & Translation — Kế hoạch triển khai

Spec gốc: [../PHASE-03-LOCALAI-MLKIT-TRANSLATION.md](../PHASE-03-LOCALAI-MLKIT-TRANSLATION.md)
Wave: **2 (ML Kit/revision) + 3 (manga)**
Phụ thuộc: Phase 01 (AI route)
Ước lượng: 5–7 ngày

---

## 1. Mục tiêu

Hoàn thiện LocalAI GGUF configuration, nối UI ML Kit model manager, enforce prerequisite
policy dịch, xây dựng translation revision model (machine/user-edited/final), và triển khai
pipeline dịch truyện tranh (OCR → translate → overlay).

---

## 2. Trạng thái hiện tại

### Đã có

| Artifact | Trạng thái | Ghi chú |
|---|:---:|---|
| LocalAI runtime | PARTIAL | `LocalGgufHandler`, `LocalAiEngineGateway/Repository` có |
| `LocalAiModels.kt` (5.9 KB) | DONE | Domain models |
| ML Kit UI | PARTIAL | `MlKitModels{Contract,ViewModel,Screen}` (16 KB total) |
| ML Kit gateway | DONE | `MlKitTranslationGateway.kt` |
| Translation cache | PARTIAL | `TranslationCache.kt` có nhưng thiếu revision fields |
| Translation pipeline | DONE | `TranslateChapterUseCase.kt` (91.4 KB!) |
| Chunk pipeline | DONE | `ContentChunker`, `AiTranslationChunkPipeline` |
| `MainRouteSettingsMlKitModels` | DONE | Route đã đăng ký |

### Chưa có

| Artifact | Ưu tiên |
|---|:---:|
| `TranslationRevision.kt` (domain model) | **CRITICAL** |
| `TranslationRevision{Contract,ViewModel,Screen}.kt` (UI) | HIGH |
| `MangaTranslationModels.kt` (domain) | HIGH |
| `TranslateMangaPageUseCase.kt` | HIGH |
| `MangaOcrRepository.kt` | HIGH |
| `MangaTranslationCacheRepository.kt` | MEDIUM |
| `MangaTranslationOverlay.kt` (Reader) | HIGH |
| `MangaTranslationEditorSheet.kt` | MEDIUM |

---

## 3. Phạm vi điều chỉnh

- **Wave 2 scope**: LocalAI completion (P3.1–P3.2), ML Kit prerequisite (P3.3–P3.4), Translation revision (P3.5–P3.6)
- **Wave 3 scope**: Manga OCR/translation/overlay (P3.7–P3.9)
- TranslateChapterUseCase đã rất lớn (91 KB) → revision logic thêm vào cẩn thận, surgical changes

---

## 4. Task chi tiết

### Wave 2 Tasks

#### P03.T01 — LocalAI runtime completion (P3.1–P3.2) `[AUTOMATED_DONE / DEVICE_PARTIAL]`

**Files:**
- `domain/gateway/LocalAiEngineGateway.kt` `[MODIFY]`
- `data/repository/LocalAiEngineRepository.kt` `[MODIFY]`
- `data/repository/ai/LocalGgufHandler.kt` `[MODIFY]`

**Yêu cầu:**
1. Import SAF: check `.gguf`, magic bytes, free storage, SHA-256 checksum
2. Import atomic: file `.importing` → rename sau validate
3. Model list: search, select, test, unload, delete
4. Probe bắt buộc: inspect → load → generate token → unload/warm
5. Native mutex/queue; cancel → native cancel, không hỏng handle kế tiếp
6. ABI không hỗ trợ → thông báo rõ, không cho profile ready
7. Hiển thị: file size, SHA-256, context, thread/batch/gpu, RAM estimate

**Tiêu chí pass:**
- Import GGUF valid → probe → ready
- Import invalid magic → reject + thông báo
- Insufficient storage → reject + thông báo
- Cancel import → staging cleanup
- Native cancel không leak handle

---

#### P03.T02 — ML Kit prerequisite policy (P3.4) `[AUTOMATED_DONE / DEVICE_PARTIAL]`

**Files:**
- `data/repository/MlKitTranslationRepository.kt` `[MODIFY]`
- `ui/config/translation/mlkit/MlKitModelsViewModel.kt` `[MODIFY]`

**Yêu cầu:**
1. Trước `translate()`: resolve source/target language code
2. Check cả hai model trong model manager
3. Thiếu model → throw typed error chứa danh sách model thiếu
4. UI mở sheet: dung lượng + nút tải; retry chỉ khi download pass
5. Mất mạng khi tải → state retryable; không xóa model đã tải
6. Offline translation phải pass sau download thành công

**Tiêu chí pass:**
- Dịch khi thiếu model → error + CTA tải
- Tải xong → retry dịch → thành công
- Tắt mạng sau tải → dịch offline thành công
- Download partial failure → retry, model OK không xóa

---

#### P03.T03 — Translation revision model (P3.5) `[AUTOMATED_DONE / DEVICE_PARTIAL]`

**File:** `app/src/main/java/io/legado/app/domain/model/TranslationRevision.kt` `[NEW]`

**Schema:**
```kotlin
enum class RevisionStatus { MACHINE_DRAFT, USER_EDITED, FINAL, STALE }

@Stable
data class TranslationRevision(
    val status: RevisionStatus,
    val rawContentHash: String,
    val dictionaryRevision: String?,
    val providerModelPromptRevision: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val finalizedAt: Long?,
    val actor: String, // "system", "user", "ai:provider:model"
    val parentRevisionId: Long?,
)
```

**Policy rules:**
1. Background translation CHỈ thay `MACHINE_DRAFT` cùng chapter/hash
2. `USER_EDITED` và `FINAL` KHÔNG bị retry/provider fallback ghi đè
3. Raw content đổi → revision cũ `STALE`; final vẫn xem được nhưng Reader cảnh báo
4. Mở khóa final → tạo revision editable mới, KHÔNG sửa row lịch sử

---

#### P03.T04 — Translation cache revision integration `[AUTOMATED_DONE / DEVICE_PARTIAL]`

**Files:**
- `data/entities/TranslationCache.kt` `[MODIFY]`
- `domain/gateway/TranslationCacheGateway.kt` `[MODIFY]`
- `data/repository/TranslationCacheRepositoryImpl.kt` `[MODIFY]`

**Yêu cầu:**
1. Thêm revision fields vào `TranslationCache` entity: `status`, `finalizedAt`, `actor`, `parentRevisionId`
2. Room migration (additive columns, default `MACHINE_DRAFT`)
3. Gateway: `finalizeChapter()`, `unlockChapter()`, `getRevisionHistory()`
4. Repository enforce policy: background không ghi đè USER_EDITED/FINAL
5. Chunk checkpoint: successful chunks remain readable when a sibling fails; retry only missing,
   failed, or dictionary-stale chunks
6. AI cache identity ignores combo/model/prompt revision; non-AI providers remain isolated by
   provider/revision and dictionary changes invalidate only overlapping chunks

---

#### P03.T05 — Translation revision UI (P3.6) `[AUTOMATED_DONE / DEVICE_PARTIAL]`

**Files:**
- `ui/translation/revision/TranslationRevisionContract.kt` `[NEW]`
- `ui/translation/revision/TranslationRevisionViewModel.kt` `[NEW]`
- `ui/translation/revision/TranslationRevisionScreen.kt` `[NEW]`

**Yêu cầu:**
1. So sánh Raw / Machine / Final side-by-side hoặc toggle
2. Sửa draft → save user edit
3. Finalize → status FINAL
4. Unlock → tạo revision mới
5. Diff view (changed text highlight)
6. Restore to previous revision

**Navigation:** Thêm `MainRouteTranslationRevision(bookUrl, chapterIndex)` vào `MainNavKey.kt`

---

#### P03.T06 — Reader revision precedence `[AUTOMATED_DONE / DEVICE_PARTIAL]`

**Files:**
- `ui/book/read/ReadBookViewModel.kt` `[MODIFY]` (relevant parts only)

**Yêu cầu:**
1. Precedence: `FINAL > USER_EDITED > MACHINE_DRAFT > RAW`
2. User chọn chế độ hiển thị
3. Cảnh báo nếu raw hash khác revision đã chốt (content nguồn đã thay đổi)

---

### Wave 3 Tasks (Manga)

#### P03.T07 — Manga pipeline domain (P3.7) `[AUTOMATED_DONE / DEVICE_PARTIAL]`

**File:** `app/src/main/java/io/legado/app/domain/manga/MangaTranslationModels.kt` `[NEW]`

**Models:**
```
MangaTextBlock — text, boundingPolygon, confidence, orientation, readingOrder, script
MangaBubbleRegion — blocks grouped, user-adjusted order
MangaTranslationResult — original block + translated text + font/color/alignment
MangaOverlayPage — image hash, blocks, overlay bitmap, cache key
MangaExportManifest — source hash, model/prompt, export timestamp
```

---

#### P03.T08 — Manga OCR + translation use case (P3.7) `[AUTOMATED_DONE / DEVICE_PARTIAL]`

**Files:**
- `domain/usecase/TranslateMangaPageUseCase.kt` `[NEW]`
- `data/repository/MangaOcrRepository.kt` `[NEW]`
- `data/repository/MangaTranslationCacheRepository.kt` `[NEW]`

**Pipeline 8 bước:**
1. Image hash + load tiled/subsampled
2. OCR ML Kit (Latin/Japanese/Chinese/Korean)
3. Normalize text blocks (bounding polygon, confidence, orientation, reading order)
4. Group blocks → bubble/region; user sửa order
5. Translate qua ML Kit hoặc AI Route
6. Background estimation (sampling vùng biên/blur-fill)
7. Fit font, line break, alignment → render overlay
8. Cache theo image hash + OCR/model/prompt version
9. Page retry keeps successful bubble checkpoints. For `app_ai`, changing combo/model/prompt does
   not create a new cache identity; dictionary dependencies are evaluated per bubble. Other
   providers remain isolated by provider/revision.

---

#### P03.T09 — Manga Reader overlay (P3.8) `[AUTOMATED_DONE / DEVICE_PARTIAL]`

**Files:**
- `ui/book/read/manga/MangaTranslationOverlay.kt` `[NEW]`
- `ui/book/read/manga/MangaTranslationEditorSheet.kt` `[NEW]`

**Yêu cầu:**
1. Toggle `Chế độ dịch` ở Reader toolbar
2. Progress per page: OCR → translating → rendering
3. Prefetch 1 trang kế tiếp, stop khi memory pressure
4. Overlay cùng transform matrix với ảnh (zoom/pan)
5. Tap block → editor: raw/translated/font/color/box/order
6. Toggle Original/Translated; xóa cache trang
7. Original image LUÔN bất biến

---

#### P03.T10 — Manga export (P3.9) `[AUTOMATED_DONE / DEVICE_PARTIAL]`

**Yêu cầu:**
1. Render dịch → file mới (KHÔNG ghi đè ảnh nguồn)
2. Export: image set, CBZ, PDF
3. Manifest: source hash, model/prompt, timestamp

---

## 5. Test bắt buộc

### Unit tests hiện có (phải pass)

```powershell
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.LocalAiModelsTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.LocalAiTranslationPromptTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.TranslationCacheRepositoryImplTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.AiTranslationLayoutProtocolTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.AiTranslationChunkPipelineTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.ui.book.read.ReaderTranslationModePolicyTest"
```

### Unit tests mới

| Test case | Priority |
|---|:---:|
| GGUF invalid magic → reject | HIGH |
| GGUF insufficient storage → reject | HIGH |
| Cancel import → cleanup staging | HIGH |
| Unsupported ABI → not ready | HIGH |
| Native load/cancel/reload no handle leak | HIGH |
| ML Kit thiếu model → typed error | CRITICAL |
| Download batch partial success + retry | HIGH |
| Offline translation sau download | CRITICAL |
| FINAL revision bất biến trước background | CRITICAL |
| Unlock tạo revision mới | HIGH |
| Reader precedence raw/machine/edited/final | CRITICAL |
| OCR reading order ngang/dọc | HIGH |
| Low confidence block handling | MEDIUM |
| Overlay transform zoom/pan/rotation | HIGH |
| Image hash invalidation | MEDIUM |
| Export không thay đổi file nguồn | CRITICAL |

### LDPlayer smoke test (`127.0.0.1:5555`)

| # | Kịch bản |
|:---:|---|
| 1 | Import/test GGUF hợp lệ hoặc confirm ABI message |
| 2 | ML Kit model manager: tải cặp ngôn ngữ, tắt mạng, dịch |
| 3 | Dịch trước tải → chặn + CTA tải model |
| 4 | Chốt chapter → dịch lại → final không đổi |
| 5 | Manga translate: bật, zoom/pan, sửa block, export |

---

## 6. Điều kiện đóng phase

- [x] LocalAI có cấu hình, test và lỗi actionable ở automated gate
- [x] ML Kit model manager và prerequisite được enforce ở automated gate
- [x] Final translation bất biến trước automation ở unit/integration gate
- [x] Manga overlay, editor và export không ghi đè ảnh nguồn ở automated gate
- [ ] Import/generate model LocalAI thật trên LDPlayer
- [ ] ML Kit offline, revision Reader và manga happy path trên nội dung thật
- [ ] Đạt gate cold/warm, peak memory và soak trên LDPlayer
