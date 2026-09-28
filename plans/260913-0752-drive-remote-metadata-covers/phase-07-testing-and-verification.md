# Phase 07: Automated Testing, Bandwidth Benchmarks & Device Verification

Status: ⬜ Pending
Dependencies: Phase 06

## Objective
Kiểm thử toàn diện toàn bộ các tính năng mới:
1. Trích xuất ảnh bìa và metadata từ xa cho 10 định dạng sách.
2. Kiểm tra tên hiển thị "DrDucBook WebDAV" trên Notification và Server.
3. Kiểm thử tải danh sách tài khoản Admin, hiển thị ngày đăng ký, lần đăng nhập gần nhất, bộ lọc và sắp xếp.
4. Đo lường băng thông mạng thực tế (Zero-Download Verification) và kiểm chứng trên máy ảo Android (LDPlayer/emulator-5554).

## Requirements

### Functional
- [ ] Unit tests:
  - `EpubRemoteExtractorTest`: kiểm tra đọc Central Directory và trích xuất OPF.
  - `MobiRemoteExtractorTest`: kiểm tra đọc EXTH tags cho MOBI, AZW3, PRC.
  - `TextRemoteExtractorTest`: kiểm tra đọc HTML, Markdown, TXT.
  - `DocxRemoteExtractorTest`: kiểm tra đọc core.xml và thumbnail.
  - `CompanionCoverResolverTest`: kiểm tra ghép cặp file ảnh cùng tên.
  - `RemoteBookMetadataRepositoryTest`: kiểm tra bộ đệm 2 tầng (RAM + Room).
  - `AccountAdminFilterSortTest`: kiểm tra logic sắp xếp và lọc danh sách tài khoản admin.
- [ ] Device Verification trên máy ảo:
  - Khởi động WebDAV service và kiểm tra notification "DrDucBook WebDAV".
  - Duyệt link Drive chứa thư mục hỗn hợp nhiều định dạng: kiểm tra ảnh bìa, tác giả, mô tả ở cả 3 chế độ xem (Catalog, Grid, File).
  - Vào màn hình Quản trị tài khoản Admin: kiểm tra hiển thị ngày đăng ký, lần đăng nhập gần nhất, thử nghiệm các chế độ sắp xếp và bộ lọc.

### Non-Functional
- [ ] Đo lường băng thông: khi duyệt thư mục 50 sách, tổng lưu lượng mạng trích xuất bìa/metadata không vượt quá 2MB.
- [ ] Độ mượt: cuộn danh sách đạt 60 FPS ổn định không giật khung hình.

## Implementation Steps
1. [ ] Viết các file Unit Test trong `app/src/test/java/io/legado/app/help/drive/extractor/` và `app/src/test/java/io/legado/app/ui/account/`.
2. [ ] Chạy test suite: `.\gradlew.bat test`.
3. [ ] Build APK debug, cài đặt lên `emulator-5554`.
4. [ ] Mở ứng dụng, kiểm thử duyệt link Google Drive công khai và màn hình Admin, chụp ảnh màn hình nghiệm thu.
5. [ ] Cập nhật `project_progress.json` và tạo `walkthrough.md`.

## Files to Create/Modify
- `app/src/test/java/io/legado/app/help/drive/extractor/EpubRemoteExtractorTest.kt` [NEW]
- `app/src/test/java/io/legado/app/help/drive/extractor/MobiRemoteExtractorTest.kt` [NEW]
- `app/src/test/java/io/legado/app/help/drive/extractor/TextRemoteExtractorTest.kt` [NEW]
- `app/src/test/java/io/legado/app/help/drive/extractor/CompanionCoverResolverTest.kt` [NEW]
- `app/src/test/java/io/legado/app/ui/account/AccountAdminFilterSortTest.kt` [NEW]

## Test Criteria
- [ ] Toàn bộ unit tests đạt 100% PASS.
- [ ] Notification hiển thị "DrDucBook WebDAV".
- [ ] Thẻ sách hiển thị ảnh bìa, metadata chuẩn xác trên máy ảo.
- [ ] Danh sách tài khoản Admin sắp xếp và lọc mượt mà theo ngày tạo và lần đăng nhập cuối.
