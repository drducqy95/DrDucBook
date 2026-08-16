# Phase 07 — Authoring, Ebook Editor & Drop Cap — Kế hoạch triển khai

Spec gốc: [../PHASE-07-AUTHORING-EBOOK-DROPCAP.md](../PHASE-07-AUTHORING-EBOOK-DROPCAP.md)
Wave: **4** | Phụ thuộc: P01 (AI suggestion), P03 (revision/clone variant)
Ước lượng: 5–7 ngày

---

## 1. Mục tiêu

Pre-writing workspace cho tiểu thuyết, clone sách đã tải làm manuscript source, block document
model cho Ebook Editor (reflow + fixed-layout), canvas/layer panel, chapter manager, preview,
validate/export EPUB3/PDF/HTML/TXT, và drop cap chính xác cho cả Reader và Editor.

---

## 2. Trạng thái hiện tại

### Đã có

| Artifact | Trạng thái | Ghi chú |
|---|:---:|---|
| Writing UI | PARTIAL | Contract, Screen (27 KB), ViewModel (14 KB) |
| Ebook Editor UI | PARTIAL | Contract, Screen (33.5 KB), ViewModel (16.5 KB) |
| Authoring library | PARTIAL | `AuthoringProjectLibrary.kt` (9.7 KB) |
| Domain | DONE | `AuthoringProject.kt`, `AuthoringProjectGateway.kt`, `AuthoringProjectUseCase.kt`, `ExportAuthoringProjectUseCase.kt` |
| Export | PARTIAL | `EbookExportModels.kt`, `EbookExportWriter.kt` |
| Drop cap (Reader) | PARTIAL | `DropCapColumn.kt`, `DropCapHtmlSupport.kt` |
| Navigation | DONE | `MainRouteWriting`, `MainRouteEbookEditor` đã đăng ký |

### Chưa có

| Artifact | Ưu tiên |
|---|:---:|
| `WritingPreproduction.kt` (domain model) | HIGH |
| `PreWritingWorkspace.kt` (UI) | HIGH |
| `CloneDownloadedBookUseCase.kt` | HIGH |
| `EbookDocument.kt`, `EbookBlock.kt`, `EbookBlockGeometry.kt` | **CRITICAL** |
| `EbookBlockCanvas.kt` | HIGH |
| `EbookLayerPanel.kt` | HIGH |
| `EbookChapterManagerSheet.kt` | HIGH |
| `EbookPreviewScreen.kt` | HIGH |
| `EbookLayoutRenderer.kt` (shared) | HIGH |
| `ValidateEbookProjectUseCase.kt` | HIGH |

---

## 3. Task chi tiết

### P07.T01 — Pre-writing model & workspace `[DONE]`

**Files:**
- `domain/model/WritingPreproduction.kt` `[NEW]`
- `ui/authoring/writing/PreWritingWorkspace.kt` `[NEW]`

**Domain model:**
```kotlin
@Stable data class WritingPreproduction(
    val premise: PreWritingSection,
    val keyPoints: PreWritingSection,
    val worldBible: PreWritingSection,
    val characterBible: PreWritingSection,
    val plotThreads: PreWritingSection,
    val outline: PreWritingSection,
    val timeline: PreWritingSection,
    val styleTone: PreWritingSection,
)
@Stable data class PreWritingSection(
    val content: String,
    val updatedAt: Long,
    val revision: Int,
    val source: SectionSource, // USER, AI_APPLIED, IMPORT
)
```

**Workspace UI:**
1. Tab/rail: Tiền sáng tác, Đại cương, Bản thảo, Kiểm tra
2. Character/world/plot cards: searchable, sortable
3. AI suggestion panel (sidebar hoặc sheet): nhận prompt → stream response → Apply / Insert / Replace selected / Discard
4. Apply = diff → preview → confirm
5. AI KHÔNG tự ghi manuscript — luôn qua user apply

**Tiêu chí pass:**
- Tạo nhân vật, thế giới, đề cương → lưu trong project
- AI gợi ý → Apply ghi đúng section → Discard không thay đổi
- Revision counter tăng mỗi lần save

---

### P07.T02 — Clone sách dropdown `[DONE]`

**File:** `domain/usecase/CloneDownloadedBookUseCase.kt` `[NEW]`

**Yêu cầu:**
1. Dropdown searchable: bookshelf/local books đã có chapter
2. Chọn scope chapters: all, range, individual
3. Content variant dropdown: RAW, MACHINE_DRAFT, USER_EDITED, FINAL (từ P03 revision)
4. Clone tạo project mới (WritingProject hoặc EbookProject)
5. Project KHÔNG link trực tiếp Book entity — copy content
6. Không ghi ngược Book/Cache khi sửa project

**Tiêu chí pass:**
- Clone sách 100 chương → project có 100 chapter
- Clone variant FINAL → chỉ lấy content final
- Sửa project → Book/Cache không đổi

---

### P07.T03 — Block document model `[DONE]`

**Files:**
- `domain/model/EbookDocument.kt` `[NEW]`
- `domain/model/EbookBlock.kt` `[NEW]`
- `domain/model/EbookBlockGeometry.kt` `[NEW]`

**EbookDocument:**
```kotlin
@Stable data class EbookDocument(
    val layoutMode: LayoutMode, // REFLOW, FIXED_PAGE
    val pageSize: PageSize?, // null = REFLOW
    val chapters: ImmutableList<EbookChapter>,
    val metadata: EbookMetadata,
)
```

**EbookBlock (sealed interface):**
```
Paragraph — text, style (bold/italic/underline/strikethrough)
Heading — text, level (1–6)
Quote — text, attribution
ImageBlock — uri, alt, caption, originalWidth/Height
Divider — type (line/ornament/space)
PageBreak
CodeBlock — text, language
ListBlock — items, ordered/unordered
```

**EbookBlockGeometry (fixed-layout only):**
```kotlin
@Stable data class EbookBlockGeometry(
    val x: Float, val y: Float,
    val width: Float, val height: Float,
    val rotation: Float = 0f,
    val zIndex: Int = 0,
    val anchor: PageAnchor, // page number/id
    val padding: Padding = Padding.ZERO,
    val background: Background? = null,
    val isLocked: Boolean = false,
    val isHidden: Boolean = false,
)
```

**Logical reading order tách khỏi z-order** (per-block `readingOrder: Int`)

---

### P07.T04 — Ebook Editor integration `[PARTIAL → DONE]`

**Files:**
- `ui/authoring/ebook/EbookEditorContract.kt` `[MODIFY]`
- `ui/authoring/ebook/EbookEditorViewModel.kt` `[MODIFY]`
- `ui/authoring/ebook/EbookEditorScreen.kt` `[MODIFY]`

**Yêu cầu:**
1. Integrate block model vào existing editor
2. Undo/redo command history (debounce text edits 500ms)
3. Autosave: staging file + fsync + atomic rename; interval 30s hoặc trước nav
4. Mode toggle: REFLOW (text editor) / FIXED_PAGE (canvas)
5. Block palette: insert paragraph/heading/image/quote/divider/page-break/code/list
6. AI suggestion: apply block hoặc generate content cho block selected

---

### P07.T05 — Block canvas (fixed-layout) `[DONE]`

**File:** `ui/authoring/ebook/EbookBlockCanvas.kt` `[NEW]`

**Yêu cầu:**
1. Canvas cho FIXED_PAGE mode
2. Block rendering theo geometry (x, y, width, height, rotation)
3. Drag: move block, snap grid (configurable interval)
4. Resize handles: 8 corner/edge handles
5. Rotate handle: free rotation
6. Selection: single click, multi-select (Ctrl+click or lasso)
7. Actions: duplicate, delete, bring forward/back, align (left/center/right/top/bottom), distribute
8. Keyboard alternatives: arrow move, Tab cycle, Delete remove
9. Accessibility: TalkBack labels cho mỗi block + action
10. Zoom/pan canvas

---

### P07.T06 — Layer panel `[DONE]`

**File:** `ui/authoring/ebook/EbookLayerPanel.kt` `[NEW]`

**Yêu cầu:**
1. Layer list: thumbnail, block type icon, name/excerpt
2. Drag reorder → update z-order
3. Lock/unlock, show/hide per block
4. Double-tap → rename
5. Reading order column (different from z-order)

---

### P07.T07 — Chapter manager `[DONE]`

**File:** `ui/authoring/ebook/EbookChapterManagerSheet.kt` `[NEW]`

**Yêu cầu:**
1. `AppModalBottomSheet`
2. Chapter list: drag reorder, multi-select
3. Actions: insert (after selected), delete (confirm nếu có content), split at cursor, merge adjacent
4. Auto renumber option (toggle)
5. Chapter metadata: title, subtitle, page break before (toggle)

---

### P07.T08 — Preview screen `[DONE]`

**File:** `ui/authoring/ebook/EbookPreviewScreen.kt` `[NEW]`

**Yêu cầu:**
1. Phone/tablet/page size selector
2. Font scale slider
3. Light/dark mode toggle
4. TOC navigation
5. Image/font loading verification
6. Drop cap preview
7. Fixed-layout geometry render

**Navigation:** `MainRouteEbookPreview(projectId: String)`

---

### P07.T09 — Shared layout renderer `[DONE]`

**File:** `service/export/EbookLayoutRenderer.kt` `[NEW]`

**Yêu cầu:**
1. Shared rendering engine cho:
   - Editor canvas (interactive, editable)
   - Preview screen (read-only)
   - Export (file output)
2. Golden comparison contract: đầu vào giống nhau → đầu ra giống nhau
3. Fixed-layout: viewport metadata, geometry rendering
4. Reflow: logical order, pagination

---

### P07.T10 — Validate use case `[DONE]`

**File:** `domain/usecase/ValidateEbookProjectUseCase.kt` `[NEW]`

**Checks:**
1. Empty chapter (0 blocks)
2. Empty content (blocks nhưng toàn blank)
3. Missing/corrupt image (check file existence + probe)
4. Missing font (check custom font existence)
5. Duplicate block/chapter ID
6. Broken internal link
7. Invalid geometry (negative/zero width/height, out of page bounds)
8. Missing alt text cho image
9. Orphan resource (file không dùng)

**Output:** `ImmutableList<ValidationIssue>` với severity (ERROR, WARNING, INFO)

---

### P07.T11 — Export enhancement `[DONE]`

**File:** `service/export/EbookExportWriter.kt` `[MODIFY]`

**Yêu cầu:**
1. EPUB3: fixed-layout viewport metadata (`rendition:layout pre-paginated`)
2. EPUB2/3 reflow: logical reading order
3. HTML: preserve geometry (absolute positioning)
4. PDF: fixed-layout geometry, pagination
5. TXT: cảnh báo mất ảnh/layout trước export
6. All: atomic file write (temp → verify → rename)
7. Drop cap CSS class output

---

### P07.T12 — Drop cap unification `[DONE]`

**Files:**
- `ui/book/read/page/provider/DropCapHtmlSupport.kt` `[MODIFY]`
- `ui/book/read/page/entities/column/DropCapColumn.kt` `[MODIFY]`

**Yêu cầu (HTML Support):**
1. Parse CSS class EPUB phổ biến: `dropcap`, `drop-cap`, `initial`, `lettrine` + Editor custom class
2. Grapheme cluster Unicode đầu tiên (xử lý Vietnamese dấu, CJK, emoji, opening quote `"'«「`)
3. Không áp lên: heading, empty paragraph, ảnh, punctuation-only paragraph

**Yêu cầu (Column):**
1. Đo size/baseline theo line height * drop-lines (2–4 configurable)
2. Font scale, orientation (portrait vs landscape)
3. Wrap text around drop cap area
4. User setting: on/off, lines count, font style

---

## 5. Test bắt buộc

### Unit tests mới

| Test case | File | Priority |
|---|---|:---:|
| Clone scope + variant | `CloneDownloadedBookUseCaseTest.kt` `[NEW]` | HIGH |
| Clone không ghi ngược Book | same | CRITICAL |
| Block geometry valid/invalid | `EbookBlockGeometryTest.kt` `[NEW]` | HIGH |
| Geometry out of bounds | same | HIGH |
| Reading order ≠ z-order | same | MEDIUM |
| Undo/redo correctness | `EbookEditorViewModelTest.kt` | HIGH |
| Autosave atomic | same | HIGH |
| Validate: empty/corrupt/duplicate/broken | `ValidateEbookProjectUseCaseTest.kt` `[NEW]` | HIGH |
| Export EPUB3 fixed-layout metadata | `EbookExportWriterTest.kt` | HIGH |
| Export TXT warning | same | MEDIUM |
| Drop cap grapheme cluster | `DropCapRoundTripTest.kt` `[NEW]` | HIGH |
| Drop cap skip heading/empty/image | same | HIGH |
| Drop cap Vietnamese dấu | same | HIGH |

### Nox smoke test

| # | Kịch bản |
|:---:|---|
| 1 | Tạo writing project → tiền sáng tác → nhân vật/thế giới |
| 2 | AI gợi ý → apply → diff preview → confirm |
| 3 | Clone sách 50 chương → project đúng số chapter |
| 4 | Ebook editor: reflow mode → insert block types → undo/redo |
| 5 | Fixed-layout: drag/resize/rotate block → snap grid |
| 6 | Chapter manager: insert/delete/reorder/split/merge |
| 7 | Preview: phone/tablet size, font scale, TOC |
| 8 | Export EPUB3 → mở bằng reader → check drop cap |
| 9 | Reader: chapter đầu có drop cap → đúng kí tự → đúng kích thước |

---

## 6. Điều kiện đóng phase

- [x] Pre-writing/đại cương/bản thảo/kiểm tra hoạt động end-to-end
- [x] AI không tự ghi manuscript
- [x] Clone sách không ảnh hưởng data nguồn
- [x] Block model hỗ trợ reflow và fixed-layout
- [x] Canvas/layer/chapter manager hoạt động
- [x] Export EPUB3/PDF/HTML/TXT đúng format
- [x] Drop cap đúng grapheme, không áp lên phần tử không phù hợp

---

## 7. Trạng thái thực thi 2026-07-27

| Task | Trạng thái | Bằng chứng |
|---|---|---|
| P07.T01 | DONE | Có `WritingPreproduction`, workspace Tiền sáng tác/Đại cương/Bản thảo/Kiểm tra, revision/source và AI Apply/Discard tường minh. |
| P07.T02 | DONE | Clone searchable, scope all/range/list, RAW/MACHINE_DRAFT/USER_EDITED/FINAL, copy độc lập và chặn VBook theo mã mở khóa. |
| P07.T03 | DONE | Block document tương thích ngược, đủ block type, reflow/fixed-page, geometry, z-order và reading order độc lập. |
| P07.T04 | DONE | Undo/redo, debounce 500 ms, autosave 30 giây, palette block, AI suggestion chỉ ghi sau Apply. |
| P07.T05 | DONE | Canvas có zoom/pan bằng scroll, snap grid, group selection bằng long-press, group drag, 8 resize handle, rotate, align/distribute, keyboard move/tab/delete và semantics. |
| P07.T06 | DONE | Có thumbnail/type, chọn/đổi tên, lock/show-hide, z-order, reading-order và kéo-thả reorder trực tiếp. |
| P07.T07 | DONE | Có insert-after-selected, delete confirm, multi-select/xóa nhóm, kéo-thả reorder, split tại block chọn, merge, subtitle, page-break và auto-renumber. |
| P07.T08 | DONE | Preview route có phone/tablet/page, font scale, light/dark, TOC và fixed geometry. |
| P07.T09 | DONE | `EbookLayoutRenderer` tạo block order/HTML/plain text/CSS dùng chung cho preview và export; editor dùng cùng document/geometry contract. |
| P07.T10 | DONE | Kiểm tra empty, missing/duplicate ID, image/alt, font, internal link, orphan resource, geometry/page/out-of-bounds. |
| P07.T11 | DONE | EPUB3/HTML giữ geometry, PDF vẽ block theo page/x/y/size/rotation/z-index, filesystem write atomic và TXT fixed-layout có cảnh báo mất bố cục. |
| P07.T12 | DONE | Hỗ trợ dropcap/drop-cap/initial/lettrine/legado-dropcap, grapheme Unicode và bỏ qua phần tử không phù hợp. |

Xác minh: compile toàn app pass; focused unit tests Phase 07 pass; instrumentation `fixedLayoutPdfKeepsDocumentPageBoundaries` pass 1/1 trên LDPlayer alias `emulator-5554`; APK x86_64 build/install pass trên `127.0.0.1:5555`. Smoke test tạo project/chapter, chuyển FIXED_PAGE, chèn Paragraph, mở Layers/Chapter manager, xóa project pass; compact top/bottom bar không còn che nội dung hay vỡ chữ; không có `AndroidRuntime` crash. Cold start đo 10–14 giây, chuyển theo dõi hiệu năng sang Phase 08.
