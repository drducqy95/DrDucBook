# Phase 02: Kho Nguồn - Kiểm Tra & Đánh Dấu Nguồn Đã Cài Đặt

Status: ⬜ Pending
Dependencies: None

## Mục tiêu
Tự động đối chiếu danh sách nguồn trong Kho Nguồn (`BookSourceHub`) với các nguồn truyện hiện có trong cơ sở dữ liệu Room (`BookSourceDao`), hiển thị huy hiệu và trạng thái trực quan để người dùng biết nguồn nào đã được cài đặt trên máy.

## Requirements
- `BookSourceHubViewModel` lắng nghe danh sách nguồn cục bộ qua `appDb.bookSourceDao.flowAll()`.
- Tạo cơ chế chuẩn hóa so khớp (match theo `bookSourceUrl` / host domain hoặc `bookSourceName`).
- Trong `BookSourceHubUiState`, lưu trữ `installedUrls: ImmutableSet<String>` và `installedNames: ImmutableSet<String>` (hoặc tập hợp `installedIds`).
- Trên UI `BookSourceHubScreen`:
  - Card nguồn đã cài đặt: hiển thị badge "✓ Đã cài" (nhãn xanh / primary container).
  - Nút tải có thể đổi icon từ `CloudDownload` sang biểu tượng cập nhật hoặc trạng thái hoàn thành.

## Implementation Steps
1. [ ] Cập nhật `BookSourceHubContract.kt`:
   - Thêm `installedUrls: ImmutableSet<String> = persistentSetOf()` và `installedNames: ImmutableSet<String> = persistentSetOf()` vào `BookSourceHubUiState`.
2. [ ] Cập nhật `BookSourceHubViewModel.kt`:
   - Khởi tạo lắng nghe `appDb.bookSourceDao.flowAll()` trong `init`.
   - Cập nhật state với tập hợp URL và Tên của các nguồn đã cài.
3. [ ] Cập nhật `BookSourceHubScreen.kt`:
   - Trong `YckceoSourceCard`, tính toán `isInstalled`:
     `val isInstalled = (item.originUrl.isNotBlank() && state.installedUrls.contains(normalizeUrl(item.originUrl))) || state.installedNames.contains(item.name)`
   - Hiển thị badge "✓ Đã cài" bên cạnh tiêu đề nguồn.
4. [ ] Bổ sung string resources song ngữ (`source_hub_installed = "Đã cài"` / `"Installed"`).

## Files to Modify
- `app/src/main/java/io/legado/app/ui/book/source/hub/BookSourceHubContract.kt`
- `app/src/main/java/io/legado/app/ui/book/source/hub/BookSourceHubViewModel.kt`
- `app/src/main/java/io/legado/app/ui/book/source/hub/BookSourceHubScreen.kt`
- `app/src/main/res/values/strings.xml` & `app/src/main/res/values-vi/strings.xml`
