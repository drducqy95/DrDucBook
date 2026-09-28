# Phase 04: Gomobile Bridge & AAR Pipeline (Track A - WebDAV)

Status: ⬜ Pending
Dependencies: `phase-03-webdav-server.md`

## Objective
Hoàn thiện lớp giao tiếp C-shared Gomobile (bridge), xây dựng pipeline tự động hóa đóng gói AAR từ Go source code, thiết lập cấu hình Gradle dependency trong `:app`, và hoàn thiện Proguard/R8 keep rules chống crash khi build Release.

## Scope
| In Scope | Out of Scope |
|---|---|
| `native/go-webdav/bind/bridge.go` public Gomobile API | Android Service background coordination (Phase 05) |
| Kotlin wrapper `GoBridge.kt` | UI Jetpack Compose screens (Phase 06) |
| Pipeline `tools/go-webdav-android/build.ps1` | NMT model export (Track B) |
| Proguard / R8 keep rules cho `go.**` | |
| Tích hợp AAR vào `app/build.gradle.kts` | |

## Requirements
### Functional
- [ ] REQ-04.1: Gomobile bridge public API cung cấp các methods tương thích Gomobile type system:
  - `NewServer(configJSON string) (*Server, error)`
  - `Start(accessToken string) error`
  - `UpdateAccessToken(token string) error`
  - `Stop() error`
  - `IsRunning() bool`
  - `Port() int`
  - `SessionSecret() string`
  - `LastError() string`
- [ ] REQ-04.2: Kotlin class `GoBridge` wrap Gomobile Java output thành interface thân thiện với Coroutines:
  - Chuyển đổi mã lỗi thành sealed class `DriveBridgeError`
  - Cung cấp state flow theo dõi trạng thái server
  - Thread-safe token update
- [ ] REQ-04.3: Script `tools/go-webdav-android/build.ps1` kiểm tra dependencies, chạy `gomobile bind` cho cả 3 ABIs (`armeabi-v7a`, `arm64-v8a`, `x86_64`), tính SHA-256 checksum và copy vào `app/libs/go-webdav.aar`.
- [ ] REQ-04.4: Proguard rules bảo vệ các class `go.Seq`, `go.webdav.Server`, `go.webdav.Webdav` và các native methods không bị R8 đổi tên hay xóa bỏ.

### Non-Functional
- [ ] NF-04.1: Build script có tính lặp lại (reproducible), có cờ kiểm tra version của Go và NDK.
- [ ] NF-04.2: R8 release build không ném lỗi `NoSuchMethodError` hay `UnsatisfiedLinkError` khi khởi động trên thiết bị thật.

## Implementation Steps
### Step 1: Hoàn thiện Gomobile Bridge
1. [ ] Cập nhật `native/go-webdav/bind/bridge.go`:
   - Parse `configJSON` thành server config
   - Khởi tạo `server.Server` và quản lý lifecycle
   - Quản lý token update và session secret getter

### Step 2: Xây dựng Kotlin Adapter
2. [ ] Tạo `app/src/main/java/io/legado/app/help/drive/GoBridge.kt`:
   - Wrap instance của Gomobile-generated `Server`
   - Expose methods `start(accessToken: String)`, `updateToken(token: String)`, `stop()`, `port: Int`, `sessionSecret: String`
   - Quản lý thread pool riêng trên `Dispatchers.IO`

### Step 3: Tự động hóa đóng gói AAR
3. [ ] Hoàn thiện `tools/go-webdav-android/build.ps1`:
   - Xác thực `ANDROID_NDK_HOME`
   - Chạy `go vet` và `go test` trước khi build
   - Gọi `gomobile bind` với flags `-v -ldflags="-s -w"`
   - Ghi checksum vào `app/libs/go-webdav.aar.sha256`

### Step 4: Cấu hình Gradle & R8 Rules
4. [ ] Cập nhật `app/build.gradle.kts`: thêm dependency `implementation(files("libs/go-webdav.aar"))`
5. [ ] Cập nhật `app/proguard-rules.pro`:
   ```proguard
   -keep class go.** { *; }
   -keep interface go.** { *; }
   ```

### Step 5: Verification Tests
6. [ ] Viết unit test Kotlin `GoBridgeTest.kt` kiểm tra lifecycle khởi động, lấy port, lấy session secret và stop
7. [ ] Build thử `assembleAppRelease` và kiểm tra APK bằng APK Analyzer xem symbols JNI còn nguyên vẹn

## Files to Create/Modify
- `native/go-webdav/bind/bridge.go` — Public Gomobile bind interface
- `tools/go-webdav-android/build.ps1` — Production build script
- `app/src/main/java/io/legado/app/help/drive/GoBridge.kt` — Kotlin type-safe adapter
- `app/build.gradle.kts` — Link AAR
- `app/proguard-rules.pro` — R8 keep rules
- `app/src/test/java/io/legado/app/help/drive/GoBridgeTest.kt` — Unit test suite

## Test Criteria
- [ ] PASS-04.1: `.\tools\go-webdav-android\build.ps1` build thành công AAR với 3 ABIs.
- [ ] PASS-04.2: `.\gradlew.bat :app:compileAppDebugKotlin` pass không có compilation warnings liên quan bridge.
- [ ] PASS-04.3: `.\gradlew.bat :app:assembleAppRelease` thành công và `GoBridge` chạy bình thường trên LDPlayer (Release build).

---
Next Phase: `phase-05-android-service-auth.md`
