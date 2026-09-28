# Phase 05: Rich Book Card UI, View Modes Switcher & Preview Sheet

Status: ⬜ Pending
Dependencies: Phase 04

## Objective
Xây dựng giao diện thẻ sách phong phú (Rich Book Card) chuẩn Material 3 Expressive, bổ sung thanh chuyển đổi 3 chế độ xem (Thẻ sách / Lưới bìa / Danh sách tệp) và BottomSheet xem trước thông tin truyện khi duyệt Drive.

## Requirements

### Functional
- [ ] `DriveBookCard`:
  - Bìa sách tỷ lệ 5:7 với Coil AsyncImage, bo góc 8dp, hiệu ứng đổ bóng hoặc shimmer.
  - Tên truyện in đậm, Title Case, tối đa 2 dòng.
  - Huy hiệu định dạng (Format Badge Chip): `EPUB` (tím), `PDF` (đỏ), `DOCX` (xanh dương), `MOBI/AZW3` (cam), `TXT/MD` (xanh lá).
  - Tên tác giả, dung lượng tệp, ngày cập nhật.
  - Đoạn tóm tắt nội dung 2 dòng.
  - Nút tải về nhanh (Join / Download) hoặc trạng thái "Đã có trên kệ sách".
- [ ] `DriveBookPreviewSheet`:
  - BottomSheet hiện ra khi người dùng nhấn vào thẻ sách.
  - Hiển thị bìa lớn, tên sách, tác giả, nguồn tệp, ngày tải lên, phần tóm tắt mở rộng có thể cuộn.
  - Nút "Thêm vào Kệ Sách" (tải về nền) và nút "Đọc thử" (mở đọc tạm qua stream).
- [ ] `ViewModeSwitcher`:
  - 3 chế độ:
    1. **Thẻ Sách (Rich Catalog Mode - Mặc định)**: Danh sách thẻ sách chi tiết.
    2. **Lưới Bìa Sách (Cover Grid Mode)**: Lưới 3 cột bìa sách lớn như Kệ Sách.
    3. **Danh Sách Tệp (Compact File Mode)**: Danh sách cây tệp cổ điển.
  - Lưu tùy chọn chế độ xem vào App Preferences (`PreferKey`).
- [ ] Cập nhật `DriveLibrarySection.kt` và `RemoteBookScreen.kt` để hiển thị `DriveBookCard` và `ViewModeSwitcher`.

### Non-Functional
- [ ] Tương thích cả Material 3 Expressive và Miuix engine.
- [ ] Hiệu ứng chuyển động mượt mà giữa các chế độ xem bằng AnimatedContent.

## Implementation Steps
1. [ ] Viết `DriveBookCard.kt`.
2. [ ] Viết `DriveBookPreviewSheet.kt`.
3. [ ] Cập nhật `DriveLibraryContract.kt` thêm `viewMode` và `selectedBookPreview`.
4. [ ] Cập nhật `DriveLibrarySection.kt` tích hợp view mode switcher và `DriveBookCard`.
5. [ ] Cập nhật `RemoteBookScreen.kt` tương thích chế độ thẻ sách.

## Files to Create/Modify
- `app/src/main/java/io/legado/app/ui/drive/DriveBookCard.kt` [NEW]
- `app/src/main/java/io/legado/app/ui/drive/DriveBookPreviewSheet.kt` [NEW]
- `app/src/main/java/io/legado/app/ui/drive/DriveLibraryContract.kt` [MODIFY]
- `app/src/main/java/io/legado/app/ui/drive/DriveLibrarySection.kt` [MODIFY]
- `app/src/main/java/io/legado/app/ui/book/import/remote/RemoteBookScreen.kt` [MODIFY]

## Test Criteria
- [ ] Giao diện hiển thị ảnh bìa, badge định dạng, tác giả và tóm tắt giống như phần Nguồn Sách.
- [ ] Chuyển đổi giữa 3 chế độ xem (Catalog / Grid / File) mượt mà không lỗi bố cục.
- [ ] Nhấn vào sách mở BottomSheet xem trước đầy đủ.
