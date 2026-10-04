# Phase 05: Kiểm Thử Toàn Diện & Đo Lường Hiệu Năng

Status: ⬜ Pending
Dependencies: Phases 01, 02, 03, 04

## Mục tiêu
Kiểm thử toàn diện 4 tính năng đã hoàn thiện: kiểm tra tính chính xác của VBook Importer, kiểm thử tính năng nhận diện & cài hàng loạt trong Kho Nguồn, chạy toàn bộ bộ test Quick Translation và benchmark tốc độ phản hồi.

## Requirements
- `VbookImportViewModel`: Kiểm thử khởi tạo danh sách rỗng, không tự tích chọn nguồn.
- `BookSourceHubViewModel`: Kiểm thử nhận diện `installedUrls/installedNames` và gọi `importSelectedSources`.
- `QuickTranslationRepository`: Chạy unit test kiểm tra tính chính xác bản dịch và đo thời gian dịch một chương mẫu (> 5000 ký tự).

## Implementation Steps
1. [ ] Viết / Cập nhật test case cho `VbookImportViewModelTest` (xác nhận `selectedPluginIds` rỗng sau khi preview).
2. [ ] Viết test case cho `BookSourceHubViewModelTest` (xác nhận nguồn đã cài được đánh dấu chính xác và batch import xử lý đúng số lượng).
3. [ ] Chạy kiểm thử đơn vị `QuickTranslationRepositoryTest` và kiểm tra thời gian thực thi (benchmark < 5ms).
4. [ ] Chạy `.\gradlew.bat :app:compileAppDebugKotlin` để xác nhận toàn bộ project biên dịch sạch sẽ không có cảnh báo/lỗi.

## Test Criteria
- `VbookImportViewModel`: Preview xong -> `selectedPluginIds.isEmpty() == true`.
- `BookSourceHub`: Hiển thị badge đã cài và batch install thành công.
- `QuickTranslation`: Tốc độ dịch đạt mức phản hồi ngay lập tức, các trường hợp tên riêng/từ ghép/hán việt dịch chuẩn xác.
