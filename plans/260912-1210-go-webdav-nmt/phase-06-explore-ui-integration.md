# Phase 06: Explore UI & Managed Source Registry (Track A - WebDAV)

Status: ⬜ Pending
Dependencies: `phase-05-android-service-auth.md`

## Objective
Xây dựng giao diện Thư viện Drive chuyên biệt đặt tại tab **Khám phá (`ExploreScreen.kt`)** với component `DriveLibrarySection`, hỗ trợ quản lý nhiều nguồn Drive đồng thời thông qua `ManagedSourceRegistry`, bộ phân giải link công khai đa nền tảng `DriveLinkResolver`, duyệt cây thư mục/tập tin theo breadcrumb và tải sách có chọn lọc vào kệ sách.

## Scope
| In Scope | Out of Scope |
|---|---|
| Component `DriveLibrarySection` nhúng trực tiếp vào đầu `ExploreScreen` | Thay đổi logic cào web của `BookSource` |
| Quản lý nhiều nguồn đám mây đồng thời (`ManagedSourceRegistry`) | NMT Translation model selection (Track B) |
| Bộ phân giải `DriveLinkResolver` (Google Drive, OneDrive, Dropbox, HTTP) | |
| Modal BottomSheet thêm nguồn `AddDriveSourceSheet` | |
| MVI Architecture: `DriveLibraryContract`, `DriveLibraryViewModel` | |
| Tích hợp duyệt catalog và tải sách chọn lọc qua `RemoteBookWebDav` | |

## Requirements
### Functional
- [ ] REQ-06.1: Giao diện tab Khám phá (`ExploreScreen.kt`):
  - Hiển thị khối `DriveLibrarySection` ở vị trí đầu danh sách (phía trên danh sách BookSource online).
  - Trạng thái chưa có nguồn: Hiển thị card kêu gọi "Thêm thư viện Drive / Cloud" trực quan, nhỏ gọn.
  - Trạng thái có nguồn: Hiển thị danh sách nguồn dạng horizontal chips/tabs kèm trạng thái kết nối (Chấm xanh: Đang kết nối, Chấm vàng: Chờ xác thực, Chấm xám: Ngắt kết nối).
  - Duyệt thư mục: Hiển thị breadcrumbs ("Thư mục gốc > Thư mục con") và danh sách tập tin/thư mục trong nguồn đang chọn.
- [ ] REQ-06.2: Modal thêm nguồn `AddDriveSourceSheet`:
  - **Tab 1 - Google Drive (Tài khoản)**: Nút đăng nhập Google, chọn thư mục bằng Drive folder picker hoặc SAF tree, nhập tên hiển thị nguồn.
  - **Tab 2 - Public Link (Đa nền tảng)**: Ô nhập link, nút dán từ clipboard, tự động nhận diện provider (Google Drive, OneDrive, Dropbox, HTTP), preview thông tin trước khi lưu.
- [ ] REQ-06.3: Quản lý nhiều nguồn `ManagedSourceRegistry`:
  - Lưu trữ danh sách nguồn trong DataStore Preferences dưới dạng JSON array.
  - Ánh xạ mỗi nguồn thành một bản ghi `Server` trong Room DB (`appDb.serverDao`) để tương thích với `RemoteBookWebDav`.
  - Hỗ trợ đổi nguồn active, chỉnh sửa tên, ngắt kết nối, hoặc xóa nguồn (không xóa sách đã tải vào kệ).
  - Tính toán `sourceKey = hash(provider + resourceId + rootPath)` đảm bảo tính idempotent (dán cùng link 2 lần không tạo duplicate).
- [ ] REQ-06.4: Bộ giải mã `DriveLinkResolver`:
  - Tự động chuẩn hóa URL, gọt bỏ các tham số tracking (`?usp=sharing`, `?rlkey=...`).
  - Phân tích và trích xuất Resource ID (Folder ID hoặc File ID).
  - Kiểm tra tính công khai của liên kết trước khi kích hoạt.
- [ ] REQ-06.5: Tải sách chọn lọc:
  - Chỉ tải những cuốn sách mà người dùng chủ động bấm tải (không tự động tải hàng loạt).
  - Tải xong tự động nhập vào Kệ sách (`LocalBook.importFiles()`) với origin `webdavTag` chuẩn để reader mở được.
  - Hiển thị badge "Đã có trong kệ" nếu file đã được tải trước đó.

### Non-Functional
- [ ] NF-06.1: Tuân thủ MVI/UDF: `@Stable UiState`, sealed interface `Intent`, sealed interface `Effect`.
- [ ] NF-06.2: Tương thích cả hai theme engine: Material 3 Expressive và Miuix.
- [ ] NF-06.3: Tuân thủ Edge-to-Edge và insets padding trên Android 15+.

## Implementation Steps
### Step 1: Data Model & Repository
1. [ ] Tạo `app/src/main/java/io/legado/app/domain/model/ManagedDriveSource.kt`
2. [ ] Tạo `app/src/main/java/io/legado/app/data/repository/ManagedSourceRegistry.kt`:
   - Lưu trữ danh sách nguồn trong DataStore
   - Tạo/cập nhật bản ghi `Server` tương ứng trong `appDb.serverDao` với URL loopback `http://127.0.0.1:<port>/` và password là session secret

### Step 2: Link Resolver & Use Case
3. [ ] Tạo `app/src/main/java/io/legado/app/help/drive/DriveLinkResolver.kt`
4. [ ] Tạo `app/src/main/java/io/legado/app/domain/usecase/DriveWebDavConnectionUseCase.kt`:
   - Kết nối pipeline: Resolve link -> Kiểm tra auth -> Bật Service -> Upsert Registry -> Cập nhật Server row

### Step 3: Contract & ViewModel
5. [ ] Tạo `app/src/main/java/io/legado/app/ui/drive/DriveLibraryContract.kt`:
   - `DriveLibraryUiState`, `DriveSourceUi`, `DriveCatalogItem`, `BreadcrumbItem`
   - `DriveLibraryIntent`, `DriveLibraryEffect`, `DriveLibraryDialog`, `DriveLibrarySheet`
6. [ ] Tạo `app/src/main/java/io/legado/app/ui/drive/DriveLibraryViewModel.kt` kế thừa `ViewModel()`:
   - Quản lý state luồng duyệt cây thư mục qua `RemoteBookWebDav`
   - Xử lý tải và nhập sách qua `RemoteBookRepository` và `LocalBook`

### Step 4: UI Composable Components
7. [ ] Tạo `app/src/main/java/io/legado/app/ui/drive/DriveLibrarySection.kt`:
   - Header hiển thị icon Cloud, selector nguồn
   - LazyRow breadcrumbs
   - LazyColumn/Grid danh sách folders và files
   - Nút hành động tải xuống hoặc đọc ngay
8. [ ] Tạo `app/src/main/java/io/legado/app/ui/drive/AddDriveSourceSheet.kt`:
   - Segmented tab cho Google Drive và Public Link
   - Form nhập link với preview và validation
9. [ ] Tạo `app/src/main/java/io/legado/app/ui/drive/DriveLibraryRouteScreen.kt` quản lý ActivityResultLauncher

### Step 5: Tích hợp vào ExploreScreen & DI
10. [ ] Đăng ký dependencies trong `app/src/main/java/io/legado/app/di/appModule.kt`:
    - `singleOf(::DriveLinkResolver)`
    - `singleOf(::ManagedSourceRegistry)`
    - `singleOf(::DriveWebDavConnectionUseCase)`
    - `viewModelOf(::DriveLibraryViewModel)`
11. [ ] Cập nhật `ExploreScreen.kt`:
    - Inject `DriveLibraryViewModel`
    - Đặt `DriveLibrarySection` vào vị trí đầu tiên của `ListScaffold` body
    - Lắng nghe effects để mở màn hình đọc truyện (`onOpenBookInfo` hoặc reader)
12. [ ] Thêm chuỗi ngôn ngữ tiếng Việt (`values-vi/strings.xml`) và tiếng Anh (`values/strings.xml`)

## Files to Create/Modify
- `app/src/main/java/io/legado/app/domain/model/ManagedDriveSource.kt` — Source domain model
- `app/src/main/java/io/legado/app/data/repository/ManagedSourceRegistry.kt` — Multi-source storage
- `app/src/main/java/io/legado/app/help/drive/DriveLinkResolver.kt` — Public link parser
- `app/src/main/java/io/legado/app/domain/usecase/DriveWebDavConnectionUseCase.kt` — Orchestrator usecase
- `app/src/main/java/io/legado/app/ui/drive/DriveLibraryContract.kt` — MVI Contract
- `app/src/main/java/io/legado/app/ui/drive/DriveLibraryViewModel.kt` — MVI ViewModel
- `app/src/main/java/io/legado/app/ui/drive/DriveLibrarySection.kt` — Composable UI section in Explore
- `app/src/main/java/io/legado/app/ui/drive/AddDriveSourceSheet.kt` — Add source bottom sheet
- `app/src/main/java/io/legado/app/ui/drive/DriveLibraryRouteScreen.kt` — Activity Result wrapper
- `app/src/main/java/io/legado/app/ui/main/explore/ExploreScreen.kt` — Embed DriveLibrarySection
- `app/src/main/java/io/legado/app/di/appModule.kt` — Register DI components
- `app/src/main/res/values/strings.xml` & `values-vi/strings.xml` — Localization strings

## Test Criteria
- [ ] PASS-06.1: Mở tab Khám phá (`ExploreScreen`) hiển thị section Drive Library mượt mà, không giật lag.
- [ ] PASS-06.2: Thêm Google Drive source qua đăng nhập tài khoản -> duyệt được cây thư mục sách.
- [ ] PASS-06.3: Thêm Public link (Google Drive / OneDrive / Dropbox) -> load được danh sách file.
- [ ] PASS-06.4: Thêm 2 nguồn khác nhau -> chuyển tab qua lại mượt mà, tải đúng cây thư mục của từng nguồn.
- [ ] PASS-06.5: Bấm tải một cuốn sách `.epub` -> tải thành công -> hiển thị badge đã có -> bấm đọc mở trực tiếp trong reader.

---
Next Phase: `phase-07-testing-verification.md`
