# Phase 07: WebDAV Test Matrix & Verification (Track A - WebDAV)

Status: ⬜ Pending
Dependencies: `phase-06-explore-ui-integration.md`

## Objective
Thực thi toàn bộ ma trận kiểm thử tự động và thủ công cho tính năng Local WebDAV: Go unit tests, Kotlin MVI unit tests, Android instrumentation tests, bảo mật mạng (loopback confinement, session secret), quản lý bộ nhớ, kiểm thử chịu tải và phục hồi sau sự cố mạng/process death.

## Scope
| In Scope | Out of Scope |
|---|---|
| Go unit tests (Filesystem, Server, Multi-provider, Range reader) | NMT model benchmarks (Phase 14) |
| Kotlin unit tests (ViewModel, Resolver, Registry, UseCase) | |
| Android instrumentation tests (Service lifecycle, R8 loading) | |
| Security verification: Loopback confinement, Basic Auth secret enforcement | |
| Stress test: Tải file lớn (>100MB), danh mục lớn (>500 files), RAM budget | |
| Regression test: Kiểm tra các tính năng WebDAV hiện có (Sao lưu WebDAV, Remote Import) | |

## Requirements
### Functional
- [ ] REQ-07.1: Go test suite đạt độ phủ mã nguồn > 80% trên các gói `drive`, `server`, `bind`.
- [ ] REQ-07.2: Kotlin test suite kiểm tra đầy đủ các chuyển đổi trạng thái của `DriveLibraryViewModel` (Success, Error, Auth Expired, Disconnect, Switch Source).
- [ ] REQ-07.3: Kiểm tra tính idempotent: Thêm cùng một URL link 10 lần liên tục chỉ tạo ra duy nhất 1 nguồn với cùng `sourceKey`.
- [ ] REQ-07.4: Kiểm tra phục hồi:
  - Khi token hết hạn giữa chừng -> request tự retry sau khi silent re-auth thành công.
  - Khi tắt app trong Recents -> Service foreground phục hồi hoặc dọn dẹp tài nguyên socket sạch sẽ.
- [ ] REQ-07.5: Kiểm tra hồi quy (Regression):
  - Tính năng sao lưu Google Drive (`GoogleDriveAppDataBackupRepository`) vẫn hoạt động bình thường.
  - Tính năng WebDAV Server bên ngoài (`RemoteBookScreen`) vẫn kết nối các server Nextcloud/Koofr/InfiniCLOUD cũ không lỗi.

### Non-Functional
- [ ] NF-07.1: Bảo mật mạng tuyệt đối: Quét cổng từ máy tính cùng mạng LAN xác nhận cổng localhost của Go WebDAV hoàn toàn không phản hồi hoặc bị từ chối kết nối.
- [ ] NF-07.2: Thử nghiệm tải liên tục file 200MB không gây ra `OutOfMemoryError` trên thiết bị có 2GB RAM khả dụng.

## Implementation Steps
### Step 1: Chạy Go Test Suite Toàn Diện
1. [ ] Chạy `cd native/go-webdav && go test -v -race -coverprofile=coverage.out ./...`
2. [ ] Kiểm tra race condition detector trên các thao tác cập nhật token và shutdown server

### Step 2: Kotlin Unit Tests
3. [ ] Viết `DriveLinkResolverTest.kt`: Test 15 mẫu URL từ các dịch vụ khác nhau (hợp lệ, sai cú pháp, link riêng tư, link có tracking query)
4. [ ] Viết `ManagedSourceRegistryTest.kt`: Test CRUD, multi-source storage, session secret rotation
5. [ ] Viết `DriveLibraryViewModelTest.kt`: Test intents `SelectSource`, `DeleteSource`, `OpenFolder`, `DownloadFile`

### Step 3: Security & Network Confinement Tests
6. [ ] Kiểm tra curl từ cùng thiết bị không có Basic Auth header -> HTTP 401
7. [ ] Kiểm tra curl với sai secret -> HTTP 401
8. [ ] Thử nghiệm kết nối tới IP LAN của máy -> kết nối bị từ chối (Connection Refused)
9. [ ] Quét Logcat kiểm tra không xuất hiện access token hay session secret

### Step 4: Kiểm thử Trực Tiếp trên Thiết Bị Thật / Giả Lập
10. [ ] Cài đặt APK Debug và Release lên LDPlayer (Android 9/12) và HONOR / Huawei (Android 14)
11. [ ] Thực hiện kịch bản người dùng hoàn chỉnh:
    - Mở tab Khám phá
    - Đăng nhập Google Drive -> Chọn thư mục sách -> Duyệt danh sách
    - Tải một truyện EPUB -> Mở đọc trong Reader -> Đọc mượt mà, lật trang bình thường
    - Dán một link Google Drive public khác -> Duyệt danh mục -> Tải sách thứ hai
    - Chuyển đổi qua lại giữa 2 thư viện nguồn
    - Dừng service -> Trạng thái hiển thị chính xác

## Files to Create/Modify
- `native/go-webdav/drive/filesystem_test.go` — Comprehensive Go tests
- `app/src/test/java/io/legado/app/help/drive/DriveLinkResolverTest.kt` — Link parser unit tests
- `app/src/test/java/io/legado/app/data/repository/ManagedSourceRegistryTest.kt` — Registry unit tests
- `app/src/test/java/io/legado/app/ui/drive/DriveLibraryViewModelTest.kt` — MVI ViewModel unit tests

## Test Criteria
- [ ] PASS-07.1: 100% Go và Kotlin unit tests vượt qua thành công.
- [ ] PASS-07.2: Không có rò rỉ bộ nhớ (memory leaks) hay goroutine leaks sau 20 lần duyệt và tải sách.
- [ ] PASS-07.3: Tính năng sao lưu hiện tại và Remote Book hiện tại không bị ảnh hưởng.

---
Next Phase: `phase-08-ci-release-docs.md`
