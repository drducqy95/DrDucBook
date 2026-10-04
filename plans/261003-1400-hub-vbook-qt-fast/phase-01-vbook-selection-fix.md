# Phase 01: VBook Importer - Bỏ Tự Động Tích Chọn Nguồn

Status: ⬜ Pending
Dependencies: None

## Mục tiêu
Đảm bảo khi mở màn hình xem trước (preview) danh sách plugin Vbook, không có plugin nào bị tự động đánh dấu tick. Chỉ những nguồn người dùng bấm chọn thủ công mới được nạp vào máy.

## Requirements
- Khi nạp URL/file registry JSON, `selectedPluginIds` khởi tạo là tập hợp rỗng (`persistentSetOf()`).
- Nút "Chọn tất cả" và "Bỏ chọn" vẫn hoạt động bình thường khi người dùng có nhu cầu chọn nhanh.
- Nút "Cài đặt đã chọn" chỉ enabled khi `selectedPluginIds.isNotEmpty()`, và chỉ cài đặt các nguồn trong tập hợp này.

## Implementation Steps
1. [ ] Sửa `VbookImportViewModel.kt`: Trong hàm `loadPreview()`, thay đổi dòng gán `selectedPluginIds = installable.toImmutableSet()` thành `selectedPluginIds = persistentSetOf()`.
2. [ ] Kiểm tra lại `VbookImportScreen.kt` để xác nhận trạng thái hiển thị checkbox và nút cài đặt phản ánh đúng danh sách rỗng ban đầu.

## Files to Modify
- `app/src/main/java/io/legado/app/ui/vbook/importer/VbookImportViewModel.kt`
- `app/src/test/java/io/legado/app/domain/usecase/ImportVbookRegistryUseCaseTest.kt` (nếu cần cập nhật test assertion)
