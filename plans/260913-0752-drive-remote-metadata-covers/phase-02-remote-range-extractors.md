# Phase 02: Zero-Download Range Extractors (EPUB, MOBI/AZW3/PRC, DOCX, PDF)

Status: ⬜ Pending
Dependencies: Phase 01

## Objective
Xây dựng các bộ trích xuất dữ liệu từ xa (Remote Range Extractors) sử dụng HTTP Range Request, chỉ đọc 4KB - 64KB đầu hoặc cuối của file để phân tích metadata (tiêu đề, tác giả, tóm tắt) và ảnh bìa nhúng bên trong mà không tải toàn bộ file.

## Requirements

### Functional
- [ ] `RemoteRangeExtractor`: Cung cấp hàm đọc byte range qua HTTP/WebDAV (`readHead`, `readTail`, `readRange`).
- [ ] `EpubRemoteExtractor`:
  - Đọc 64KB cuối của file EPUB để tìm End of Central Directory (EOCD) và Central Directory của ZIP.
  - Phân tích vị trí của `content.opf`.
  - Range request đọc `content.opf` để lấy metadata (`title`, `creator`, `description`, `cover` item).
  - Xác định byte offset và độ dài của file ảnh bìa nhúng $\rightarrow$ tạo stream Range request ảnh bìa cho Coil.
- [ ] `MobiRemoteExtractor`:
  - Hỗ trợ `mobi`, `azw`, `azw3`, `prc`.
  - Đọc 8KB đầu tiên để phân tích PalmDOC Header, Record Table và EXTH Header.
  - Trích xuất: tag 100 (Author), tag 103 (Description), tag 503 (Title), tag 201 (Cover Record Number).
  - Tìm offset của record ảnh bìa $\rightarrow$ Range request tải ảnh bìa.
- [ ] `DocxRemoteExtractor`:
  - Đọc Central Directory của ZIP $\rightarrow$ phân tích `docProps/core.xml` (title, author, subject).
  - Trích xuất ảnh thumbnail nhúng (`docProps/thumbnail.jpeg` hoặc ảnh đầu tiên trong `word/media/`).
- [ ] `PdfRemoteExtractor`:
  - Trích xuất thông tin Info Dictionary (Title, Author, Subject) từ 16KB cuối/đầu.
  - Dùng Google Drive `thumbnailLink` làm nguồn ưu tiên, fallback stream trang 1.

### Non-Functional
- [ ] Mỗi lần trích xuất tiêu thụ tối đa < 100KB mạng.
- [ ] Xử lý lỗi timeout hoặc server không hỗ trợ Range (fallback mềm).

## Implementation Steps
1. [ ] Tạo package `io.legado.app.help.drive.extractor`.
2. [ ] Viết `RemoteRangeExtractor.kt` với OkHttp / Cronet request builder có header `Range: bytes=...`.
3. [ ] Viết `EpubRemoteExtractor.kt`.
4. [ ] Viết `MobiRemoteExtractor.kt`.
5. [ ] Viết `DocxRemoteExtractor.kt`.
6. [ ] Viết `PdfRemoteExtractor.kt`.

## Files to Create/Modify
- `app/src/main/java/io/legado/app/help/drive/extractor/RemoteRangeExtractor.kt` [NEW]
- `app/src/main/java/io/legado/app/help/drive/extractor/EpubRemoteExtractor.kt` [NEW]
- `app/src/main/java/io/legado/app/help/drive/extractor/MobiRemoteExtractor.kt` [NEW]
- `app/src/main/java/io/legado/app/help/drive/extractor/DocxRemoteExtractor.kt` [NEW]
- `app/src/main/java/io/legado/app/help/drive/extractor/PdfRemoteExtractor.kt` [NEW]

## Test Criteria
- [ ] `EpubRemoteExtractorTest` trích xuất thành công title, author, cover href từ file EPUB mẫu mà chỉ đọc < 80KB.
- [ ] `MobiRemoteExtractorTest` đọc đúng EXTH tag 100, 103, 201 từ file MOBI/PRC/AZW3 mẫu.
- [ ] `DocxRemoteExtractorTest` đọc đúng title, author từ file DOCX mẫu.
