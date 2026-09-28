# Phase 05: Android Service & Google Auth (Track A - WebDAV)

Status: ⬜ Pending
Dependencies: `phase-04-gomobile-bridge-aar.md`

## Objective
Xây dựng Foreground Service `DriveWebDavService` (`dataSync` type) để duy trì WebDAV server nền, mở rộng lớp xác thực `GoogleDriveAuthorizationBridge` để cấp quyền `drive.file` (folder-scoped không cần Google verification nhạy cảm), và cơ chế tự động làm mới access token (silent re-authorization) khi nhận mã lỗi `AUTH_EXPIRED`.

## Scope
| In Scope | Out of Scope |
|---|---|
| `DriveWebDavService` kế thừa `BaseService` với foreground notification | Explore UI screens (Phase 06) |
| Khai báo manifest và permissions `FOREGROUND_SERVICE_DATA_SYNC` | Catalog file browsing UI (Phase 06) |
| `DriveWebDavServiceController` điều phối start/stop/bind service | NMT models (Track B) |
| Mở rộng `CloudConsentScopes` và `GoogleDriveAuthorizationBridge` | |
| Tự động làm mới access token qua Google Play Services Identity API | |

## Requirements
### Functional
- [ ] REQ-05.1: `DriveWebDavService`:
  - Khởi chạy dưới dạng Foreground Service với channel notification riêng `channelIdWeb` hoặc `channelIdDriveWebDav`.
  - Notification hiển thị trạng thái server ("Đang chạy", "Chờ xác thực", "Lỗi"), nút "Dừng" (`ACTION_STOP`).
  - Quản lý vòng đời `GoBridge`: start khi service start, stop khi service destroy.
  - Phục hồi an toàn nếu app bị hệ điều hành kill và tái khởi động.
- [ ] REQ-05.2: `DriveWebDavServiceController`:
  - Cung cấp `StateFlow<ServiceState>` cho UI: `STOPPED`, `STARTING`, `RUNNING`, `AUTH_REQUIRED`, `ERROR`.
  - Hỗ trợ bind service để nhận thông tin port và session secret.
  - Tự động kích hoạt silent re-authorization qua `GoogleDriveAuthorizationBridge` khi nhận tín hiệu token hết hạn từ `GoBridge`.
- [ ] REQ-05.3: Google Auth Scope:
  - Thêm scope `https://www.googleapis.com/auth/drive.file` trong `CloudConsentScopes`.
  - Tách bạch hoàn toàn với scope backup hiện có (`drive.appdata`), không làm ảnh hưởng tính năng sao lưu Google Drive.
  - Hỗ trợ mở Google Drive Folder Picker (SAF hoặc Storage Access Framework / Google Drive intent) để người dùng chỉ định thư mục sách cần đọc.
- [ ] REQ-05.4: Android 13+ / Android 14+ compat:
  - Khai báo đúng `foregroundServiceType="dataSync"` trong `AndroidManifest.xml`.
  - Yêu cầu quyền `POST_NOTIFICATIONS` hợp lệ trước khi gọi `startForeground()`.

### Non-Functional
- [ ] NF-05.1: Token refresh diễn ra trong suốt dưới nền trong vòng < 3 giây mà không làm gián đoạn việc đọc sách.
- [ ] NF-05.2: Tuyệt đối không log access token hoặc session secret ra Logcat.

## Implementation Steps
### Step 1: Mở rộng Google Drive Auth Bridge & Scopes
1. [ ] Cập nhật `app/src/main/java/com/drducbook/app/cloud/CloudConsentScopes.kt`: thêm `const val googleDriveFile`
2. [ ] Mở rộng `app/src/main/java/io/legado/app/help/google/GoogleDriveAuthorizationBridge.kt`:
   - Thêm hàm `authorizeDriveFile(context)`
   - Thêm hàm `silentRefreshToken(context)` sử dụng Play Services Identity API

### Step 2: Xây dựng DriveWebDavService
3. [ ] Tạo `app/src/main/java/io/legado/app/service/DriveWebDavService.kt` kế thừa `BaseService`
4. [ ] Tạo notification builder với action Stop PendingIntent
5. [ ] Tích hợp `GoBridge` chạy trên CoroutineScope riêng của service
6. [ ] Xử lý callbacks từ `GoBridge` khi gặp lỗi `AUTH_EXPIRED` -> thông báo controller

### Step 3: Manifest & Khai báo Service
7. [ ] Cập nhật `app/src/main/AndroidManifest.xml`:
   ```xml
   <service
       android:name=".service.DriveWebDavService"
       android:exported="false"
       android:foregroundServiceType="dataSync">
       <property
           android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
           android:value="local WebDAV proxy for cloud book reading" />
   </service>
   ```

### Step 4: Controller & Service Connection
8. [ ] Tạo `app/src/main/java/io/legado/app/help/drive/DriveWebDavServiceController.kt`
9. [ ] Quản lý kết nối ServiceConnection, khởi động foreground service qua `ContextCompat.startForegroundService`
10. [ ] Điều phối silent auth và cập nhật token cho `GoBridge` qua method `updateAccessToken`

### Step 5: Test Vòng Đời & Token Expiry
11. [ ] Viết test mô phỏng chu kỳ start/stop service 10 lần liên tục
12. [ ] Viết test mô phỏng token hết hạn và xác nhận controller gọi silent re-auth cập nhật lại token thành công

## Files to Create/Modify
- `app/src/main/java/io/legado/app/service/DriveWebDavService.kt` — Foreground Service
- `app/src/main/java/io/legado/app/help/drive/DriveWebDavServiceController.kt` — Service lifecycle coordinator
- `app/src/main/java/com/drducbook/app/cloud/CloudConsentScopes.kt` — Add drive.file scope
- `app/src/main/java/io/legado/app/help/google/GoogleDriveAuthorizationBridge.kt` — Add drive.file auth methods
- `app/src/main/AndroidManifest.xml` — Declare service and permissions
- `app/src/test/java/io/legado/app/service/DriveWebDavServiceTest.kt` — Service unit tests

## Test Criteria
- [ ] PASS-05.1: Service khởi động hiển thị notification đúng tiêu đề và port trên Android 14 (LDPlayer / HONOR device).
- [ ] PASS-05.2: Bấm nút "Dừng" trên notification tắt service và giải phóng Go WebDAV server hoàn toàn.
- [ ] PASS-05.3: Silent re-authorization thành công tự động cập nhật token mà không cần popup màn hình đăng nhập lại.

---
Next Phase: `phase-06-explore-ui-integration.md`
