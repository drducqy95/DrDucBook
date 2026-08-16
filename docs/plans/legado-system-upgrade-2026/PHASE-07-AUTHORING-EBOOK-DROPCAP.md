# Phase 07 — Sáng tác, Ebook Editor không tuyến tính, preview/export và drop cap

## 1. Kết quả phải đạt

Sáng tác có bước tiền sáng tác đầy đủ; Ebook Editor clone sách qua dropdown với raw/cache/final, quản lý/chèn/reorder chapter, biên tập bằng block text/ảnh ở cả reflow và fixed layout, preview dùng cùng renderer với export; drop cap hiển thị giống nhau trong Editor, file xuất và Reader.

## 2. Phạm vi

### Trong phạm vi

- Home toolbar shortcuts cho Sáng tác và Ebook Editor; giữ lối vào Cá nhân.
- Tiền sáng tác: ý tưởng, điểm chính, thế giới, nhân vật, tình tiết, đại cương, timeline.
- AI suggestion có diff/apply; không tự ghi manuscript.
- Clone sách đã tải bằng dropdown searchable.
- Chọn raw, machine cache, user-edited hoặc final translation.
- Chapter insert/reorder/split/merge/delete.
- Block text/image; reflow và fixed-layout canvas.
- Drag/resize/z-order/align/snap/layer/lock/undo/redo.
- Live preview, validation và atomic export.
- EPUB3 fixed layout, reflow EPUB, HTML/PDF/TXT policy.
- Drop cap đúng Unicode và Reader support.

### Ngoài phạm vi

- Desktop publishing đầy đủ như InDesign.
- Collaborative realtime multi-user.
- Arbitrary JavaScript trong EPUB.
- Silent write-back vào sách nguồn.
- TXT giữ ảnh/layout; phải cảnh báo mất dữ liệu trình bày.

## 3. Sáng tác và tiền sáng tác

### P7.1 — Pre-writing model

Mỗi `WritingProject` có các section versioned:

- Premise/ý tưởng.
- Key points và mục tiêu tác phẩm.
- World bible: luật, địa lý, lịch sử, phe phái, vật phẩm.
- Character bible: hồ sơ, mục tiêu, xung đột, arc và quan hệ.
- Plot threads, foreshadowing và unresolved questions.
- Volume/arc/chapter outline.
- Timeline/event dependency.
- Style/tone/POV và constraints.

Mỗi section có `updatedAt`, revision và source `USER/AI_APPLIED/IMPORT` để consistency checker biết dữ liệu canonical.

### P7.2 — Pre-writing UI

- Library → workspace vẫn giữ unsaved-change guard hiện có.
- Thêm tab/rail `Tiền sáng tác`, `Đại cương`, `Bản thảo`, `Kiểm tra`.
- Phone dùng top tabs/sheets; tablet dùng navigation rail + master/detail.
- Character/world/plot dùng cards searchable; relation/timeline có list trước, graph là enhancement sau.
- AI output ở suggestion panel; action `Apply`, `Insert`, `Replace selected` và `Discard`.
- Apply tạo revision và invalidate summary/consistency phụ thuộc.

## 4. Clone sách và chapter editor

### P7.3 — Dropdown sách đã tải

- Bottom sheet clone hiện tại bỏ nhập `bookUrl` thủ công làm đường chính.
- Dropdown searchable lấy từ bookshelf/local books đã có chapter/content tải được.
- Row: cover, name, author, source, downloaded chapter count và translation availability.
- Chọn scope chapter và content variant:
  - Raw.
  - Machine draft.
  - User edited.
  - Final.
- Preview số chapter/content thiếu trước clone.
- Clone tạo project độc lập; không ghi ngược Book/Cache.

### P7.4 — Chapter operations

- Insert trước/sau/current index.
- Drag-drop reorder và move to index.
- Split tại cursor/block; merge với trước/sau.
- Multi-select delete/move/export.
- Auto renumber là tùy chọn; title user nhập không bị sửa nếu tắt.
- Mọi operation vào undo stack và autosave atomic.

## 5. Block document model

### P7.5 — Hai layout mode

- `REFLOW`: block có thứ tự logic, phù hợp EPUB reflow/accessibility.
- `FIXED_PAGE`: page canvas có width/height/unit và block geometry.
- Chuyển Reflow → Fixed tạo geometry mặc định nhưng giữ logical order.
- Chuyển Fixed → Reflow hiển thị preview thứ tự đọc và cảnh báo mất position/z-order; chỉ commit sau confirm.

### P7.6 — Block types và geometry

Block release đầu:

- Paragraph/text.
- Heading.
- Quote.
- Image + alt/caption.
- Divider/page break.

Fixed block fields:

- `x`, `y`, `width`, `height`, `rotation`, `zIndex`.
- Anchor/page ID.
- Padding, background và text style reference.
- Locked/hidden.
- Logical reading order tách khỏi z-order.

Editor controls:

- Drag, resize handles, rotate, duplicate, delete.
- Bring forward/back/front/back.
- Align left/center/right/top/middle/bottom.
- Distribute và snap grid/guides.
- Layer panel, lock/unlock và rename block.
- Keyboard/accessibility alternatives cho drag gestures.

### P7.7 — Undo/autosave

- Command history cho block/chapter operation; giới hạn memory và checkpoint disk.
- Text edit được debounce thành command hợp lý, không tạo command mỗi ký tự.
- Autosave write temporary + fsync/rename; crash mở recovery prompt.
- Asset store hash-based; xóa block không xóa asset còn reference.

## 6. Preview và export

### P7.8 — Shared renderer

- Tạo renderer contract dùng chung Editor canvas, Preview và Export model mapping.
- Preview phone/tablet/page size, font scale, light/dark và page navigation.
- Preview TOC, image/font loading, drop cap và fixed geometry.
- Không dùng HTML/CSS preview khác với exporter mà không có golden comparison.

### P7.9 — Validation

Trước export phải kiểm tra:

- Chapter/title/content rỗng.
- Missing/corrupt image/font.
- Duplicate ID và broken link.
- Invalid block geometry/out-of-page/negative size.
- Reading order thiếu hoặc trùng.
- Missing alt text warning.
- Unsupported output mapping.

### P7.10 — Output policy

- EPUB3 fixed-layout: preserve page geometry và viewport metadata.
- EPUB2/EPUB3 reflow: preserve logical order/style/assets.
- HTML/PDF: preserve fixed geometry theo page size.
- TXT: export logical text only, cảnh báo ảnh/layout bị bỏ.
- CBZ: render từng fixed page thành image; không dùng cho reflow text trừ khi user chọn rasterize.
- Export atomic, không ghi đè file thành công cũ nếu validation/write fail.

## 7. Drop cap thống nhất

### P7.11 — Data/style

- Drop cap là paragraph/block style, không chèn ký tự trang trí vào text.
- Fields tối thiểu: enabled, lines, characters/graphemes, margin và style class.
- Lấy grapheme cluster Unicode đầu tiên; xử lý opening quote/punctuation theo policy, không tách dấu tiếng Việt.
- Không áp dụng lên heading, paragraph rỗng, ảnh hoặc đoạn chỉ có punctuation.

### P7.12 — Reader/export

- `EbookExportWriter` sinh class/CSS chuẩn cho drop cap.
- `EpubReaderContentFormatter` nhận class của Editor và class EPUB phổ biến.
- `DropCapHtmlSupport` parse thành Reader column đúng.
- `DropCapColumn` đo size/baseline theo line height hiện tại, font scale và orientation.
- Editor preview, exported EPUB và Reader phải dùng cùng fixture/golden style.

## 8. File tác động

### File hiện có cần sửa

- `app/src/main/java/io/legado/app/domain/model/AuthoringProject.kt`
- `app/src/main/java/io/legado/app/domain/gateway/AuthoringProjectGateway.kt`
- `app/src/main/java/io/legado/app/data/repository/AuthoringProjectRepository.kt`
- `app/src/main/java/io/legado/app/domain/usecase/AuthoringProjectUseCase.kt`
- `app/src/main/java/io/legado/app/domain/usecase/ExportAuthoringProjectUseCase.kt`
- `app/src/main/java/io/legado/app/ui/authoring/AuthoringProjectLibrary.kt`
- `app/src/main/java/io/legado/app/ui/authoring/writing/WritingContract.kt`
- `app/src/main/java/io/legado/app/ui/authoring/writing/WritingViewModel.kt`
- `app/src/main/java/io/legado/app/ui/authoring/writing/WritingScreen.kt`
- `app/src/main/java/io/legado/app/ui/authoring/ebook/EbookEditorContract.kt`
- `app/src/main/java/io/legado/app/ui/authoring/ebook/EbookEditorViewModel.kt`
- `app/src/main/java/io/legado/app/ui/authoring/ebook/EbookEditorScreen.kt`
- `app/src/main/java/io/legado/app/service/export/EbookExportModels.kt`
- `app/src/main/java/io/legado/app/service/export/EbookExportWriter.kt`
- `app/src/main/java/io/legado/app/model/localBook/EpubReaderContentFormatter.kt`
- `app/src/main/java/io/legado/app/ui/book/read/page/provider/DropCapHtmlSupport.kt`
- `app/src/main/java/io/legado/app/ui/book/read/page/entities/column/DropCapColumn.kt`
- `app/src/main/java/io/legado/app/ui/main/home/HomeScreen.kt`
- `app/src/main/java/io/legado/app/ui/main/my/MyScreen.kt`
- `app/src/main/java/io/legado/app/ui/main/MainScreen.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavGraph.kt`
- `app/src/main/java/io/legado/app/di/appModule.kt`

### File tạo mới dự kiến

- `app/src/main/java/io/legado/app/domain/model/WritingPreproduction.kt`
- `app/src/main/java/io/legado/app/domain/model/EbookDocument.kt`
- `app/src/main/java/io/legado/app/domain/model/EbookBlock.kt`
- `app/src/main/java/io/legado/app/domain/model/EbookBlockGeometry.kt`
- `app/src/main/java/io/legado/app/domain/usecase/CloneDownloadedBookUseCase.kt`
- `app/src/main/java/io/legado/app/domain/usecase/ValidateEbookProjectUseCase.kt`
- `app/src/main/java/io/legado/app/ui/authoring/writing/PreWritingWorkspace.kt`
- `app/src/main/java/io/legado/app/ui/authoring/ebook/EbookBlockCanvas.kt`
- `app/src/main/java/io/legado/app/ui/authoring/ebook/EbookLayerPanel.kt`
- `app/src/main/java/io/legado/app/ui/authoring/ebook/EbookPreviewScreen.kt`
- `app/src/main/java/io/legado/app/ui/authoring/ebook/EbookChapterManagerSheet.kt`
- `app/src/main/java/io/legado/app/service/export/EbookLayoutRenderer.kt`
- `app/src/test/java/io/legado/app/domain/usecase/CloneDownloadedBookUseCaseTest.kt`
- `app/src/test/java/io/legado/app/domain/model/EbookBlockGeometryTest.kt`
- `app/src/test/java/io/legado/app/model/localBook/DropCapRoundTripTest.kt`

## 9. Test bắt buộc phải pass

### Test hiện có

```powershell
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.service.export.EbookExportScopeTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.model.localBook.EpubReaderContentFormatterTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.ui.book.read.page.provider.DropCapHtmlSupportTest"
```

### Test mới bắt buộc

- Pre-writing CRUD/revision/invalidation và AI suggestion apply-only.
- Clone raw/machine/edited/final; missing cache và mixed chapter availability.
- Insert/reorder/split/merge chapter và undo/redo.
- Geometry clamp, resize, rotate, z-order, align, distribute và reading order.
- Reflow ↔ fixed conversion warning/data preservation.
- Autosave atomic, crash recovery và asset reference counting.
- Validator missing asset/font/link/geometry/alt text.
- Golden render Editor Preview ↔ EPUB/PDF/HTML.
- Drop cap Unicode tiếng Việt, CJK, emoji grapheme, quote/punctuation và font scaling.
- TXT warning và logical order.

### Instrumentation/Nox

1. Home shortcut mở Sáng tác và Ebook Editor bằng một lần nhấn.
2. Tạo pre-writing world/character/plot/outline, nhận AI suggestion và kiểm tra chưa apply thì dữ liệu không đổi.
3. Clone bookshelf book qua dropdown bằng raw và final translation.
4. Insert/reorder chapter, thêm text/image block, drag/resize/z-order.
5. Preview phone/tablet và export EPUB3 fixed/PDF/HTML/TXT.
6. Mở EPUB export trong Reader, kiểm tra TOC, ảnh, layout và drop cap.
7. Kill app giữa autosave và kiểm tra recovery.

### Gate build

```powershell
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain
.\gradlew.bat :app:connectedAppDebugAndroidTest --no-daemon --console=plain
```

Phải chạy và giữ pass `EbookExportWriterInstrumentedTest` sau khi đổi exporter.

## 10. Điều kiện đóng phase

- Pre-writing là bước sử dụng được, không chỉ placeholder.
- Clone dropdown và raw/cache/final đúng từng chapter.
- Fixed block editor thực sự di chuyển/resize/z-order, không chỉ reorder tuyến tính.
- Preview/export chia sẻ document contract và golden test.
- Drop cap đúng trong Editor, export và Reader.
