# Phase 03: Text Format Analyzers, Companion Covers & Typography Cover Generator

Status: ⬜ Pending
Dependencies: Phase 02

## Objective
Xử lý các định dạng văn bản thuần (`txt`, `md`, `html`) và các tình huống sách không có ảnh bìa nhúng bằng cách phân tích dòng mở đầu, ghép cặp ảnh bìa đi kèm trong thư mục (Companion Cover) và tự động tạo bìa chữ nghệ thuật (Typography Cover).

## Requirements

### Functional
- [ ] `TextRemoteExtractor`:
  - **HTML**: Range request 4KB đầu $\rightarrow$ trích xuất `<title>`, `<meta name="author">`, `<meta name="description">`, `og:image`, và thẻ `<img src="...">` đầu tiên.
  - **MD (Markdown)**: Range request 4KB đầu $\rightarrow$ trích xuất YAML frontmatter (`--- title: ... author: ... cover: ... ---`) hoặc tiêu đề `# Tên sách` và ảnh markdown `![cover](url)`.
  - **TXT**: Range request 2KB đầu $\rightarrow$ áp dụng heuristics regex tách tiêu đề, tác giả, mô tả từ các dòng đầu (`Tên truyện: ...`, `Tác giả: ...`, `Giới thiệu: ...`).
- [ ] `CompanionCoverResolver`:
  - Quét danh sách file trong cùng thư mục.
  - Tự động ghép cặp file ảnh đi kèm với file sách:
    - Cùng tên: `[tensach].jpg`, `[tensach].png`, `[tensach].jpeg`.
    - Tên chuẩn: `cover.jpg`, `folder.jpg`, `poster.jpg`.
- [ ] `TypographyCoverGenerator`:
  - Khi sách không có ảnh bìa từ Drive hoặc companion: tự động sinh ảnh bìa nghệ thuật chất lượng cao (dựa trên thuật toán `BookCover.kt` và hash bảng màu).
  - Hiển thị tên sách in hoa đậm nét, tên tác giả và huy hiệu định dạng (`TXT`, `MD`, `HTML`, `PRC`).

### Non-Functional
- [ ] Sinh ảnh bìa trong < 5ms không gây giật lag danh sách cuộn.
- [ ] Tránh trích xuất nhầm ảnh icon hoặc ảnh banner quảng cáo trong file HTML.

## Implementation Steps
1. [ ] Viết `TextRemoteExtractor.kt` cho HTML, Markdown và TXT.
2. [ ] Viết `CompanionCoverResolver.kt` liên kết file sách với file ảnh trong thư mục.
3. [ ] Cập nhật hoặc tích hợp với `BookCover.kt` để tạo ảnh bìa nghệ thuật cho `DriveBookUi`.

## Files to Create/Modify
- `app/src/main/java/io/legado/app/help/drive/extractor/TextRemoteExtractor.kt` [NEW]
- `app/src/main/java/io/legado/app/help/drive/extractor/CompanionCoverResolver.kt` [NEW]
- `app/src/main/java/io/legado/app/model/BookCover.kt` [MODIFY - hỗ trợ sinh bìa DriveBookUi]

## Test Criteria
- [ ] File HTML mẫu trích xuất đúng title, author và og:image.
- [ ] File Markdown mẫu với YAML frontmatter trích xuất đúng metadata.
- [ ] File TXT đi kèm file `[name].jpg` tự động nhận file ảnh làm bìa.
