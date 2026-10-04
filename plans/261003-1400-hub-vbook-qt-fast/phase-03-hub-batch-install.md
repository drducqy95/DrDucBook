# Phase 03: Kho Nguồn - Chọn Nhiều & Cài Đặt Hàng Loạt

Status: ⬜ Pending
Dependencies: Phase 02

## Mục tiêu
Cho phép người dùng tick chọn nhiều nguồn trực tiếp trong Kho Nguồn (`BookSourceHub`) và cài đặt tất cả chỉ bằng một thao tác bấm.

## Requirements
- Hỗ trợ chọn/bỏ chọn từng nguồn thông qua checkbox trên mỗi card.
- Cung cấp nút "Chọn tất cả" và "Bỏ chọn tất cả".
- Thanh công cụ hành động hàng loạt (Batch Bar) hiển thị số lượng nguồn đã chọn và nút "Cài đặt hàng loạt (X)".
- Khi bấm "Cài đặt", ViewModel xử lý tải và import danh sách nguồn được chọn với thanh tiến trình rõ ràng (`isBatchImporting`, `batchProgress = Pair(current, total)`).
- Hiển thị Toast kết quả sau khi hoàn thành.

## Implementation Steps
1. [ ] Cập nhật `BookSourceHubContract.kt`:
   - Thêm vào `BookSourceHubUiState`:
     - `selectedSourceIds: ImmutableSet<String> = persistentSetOf()`
     - `isBatchImporting: Boolean = false`
     - `batchProgress: Pair<Int, Int>? = null`
   - Thêm Intent:
     - `ToggleSelectSource(id: String)`
     - `SelectAllSources`
     - `ClearSourceSelection`
     - `ImportSelectedSources`
2. [ ] Cập nhật `BookSourceHubViewModel.kt`:
   - Xử lý các intent chọn nguồn.
   - Viết hàm `importSelectedSources()`:
     Lặp qua các `OnlineBookSourceItem` tương ứng với `selectedSourceIds`, gọi `repository.importFromUrl(item.downloadUrl)`, cập nhật `batchProgress`, xử lý lỗi từng nguồn mà không làm gián đoạn toàn bộ quá trình, sau đó xóa danh sách chọn và thông báo thành công.
3. [ ] Cập nhật `BookSourceHubScreen.kt`:
   - Bổ sung `Checkbox` trên mỗi `YckceoSourceCard`.
   - Bổ sung thanh công cụ hàng loạt ghim ở dưới danh sách khi `selectedSourceIds.isNotEmpty()`.
4. [ ] Bổ sung string resources song ngữ (`source_hub_select_all`, `source_hub_deselect_all`, `source_hub_batch_import`, `source_hub_batch_imported_success`).

## Files to Modify
- `app/src/main/java/io/legado/app/ui/book/source/hub/BookSourceHubContract.kt`
- `app/src/main/java/io/legado/app/ui/book/source/hub/BookSourceHubViewModel.kt`
- `app/src/main/java/io/legado/app/ui/book/source/hub/BookSourceHubScreen.kt`
- `app/src/main/res/values/strings.xml` & `app/src/main/res/values-vi/strings.xml`
