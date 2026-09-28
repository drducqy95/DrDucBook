# Phase 01: Spike & Decision Gate (Track A - WebDAV)

Status: ⬜ Pending
Dependencies: None (Spike phase)

## Objective
Xác thực môi trường Go/Gomobile/NDK trên máy phát triển, đo đạc kích thước AAR, xác minh tích hợp Gradle và R8 keep rules, kiểm tra HTTP Basic Auth và XML namespace của client `WebDav.kt`.

## Scope
| In Scope | Out of Scope |
|---|---|
| Khởi tạo module `native/go-webdav` với Go 1.22+ | Full filesystem implementation (Phase 02) |
| Script `tools/go-webdav-android/build.ps1` spike Gomobile bind | UI components (Phase 06) |
| Đo dung lượng AAR cho 3 ABIs: `armeabi-v7a`, `arm64-v8a`, `x86_64` | Production Android Service (Phase 05) |
| Test hello-world bridge call từ Kotlin test/LDPlayer | NMT export (Track B) |
| Audit chính xác XML schema của `WebDav.kt` và verify Basic Auth | |

## Requirements
### Functional
- [ ] REQ-01.1: Gomobile bind biên dịch thành công file `.aar` chứa C-shared libraries cho cả 3 ABIs (`armeabi-v7a`, `arm64-v8a`, `x86_64`).
- [ ] REQ-01.2: Kotlin code trong `:app` import và invoke được native method `NewServer()` / `Start()` / `Stop()` từ AAR.
- [ ] REQ-01.3: Xác nhận port strategy: Port 0 (system assigned) khởi động thành công và trả về port ngẫu nhiên khả dụng.
- [ ] REQ-01.4: Xác nhận cơ chế bảo mật Session Secret: WebDAV HTTP server trên localhost yêu cầu HTTP Basic Authentication với password ngẫu nhiên và reject 401 nếu thiếu hoặc sai secret.
- [ ] REQ-01.5: Xác nhận XML output từ Go server đáp ứng đúng định dạng parser Jsoup của `WebDav.kt` (hỗ trợ cả namespace `a:` và `D:`).

### Non-Functional
- [ ] NF-01.1: AAR size không làm tăng kích thước APK vượt quá budget cho phép (~5-10MB cho mỗi split APK).
- [ ] NF-01.2: R8 full mode obfuscation không làm strip hoặc mangle các symbol JNI của `go.Seq` và package `go.webdav`.

## Implementation Steps
### Step 1: Khởi tạo Go Module & Dummy Bridge
1. [ ] Tạo thư mục `native/go-webdav` và chạy `go mod init io.legado.gowebdav`
2. [ ] Thêm dependencies: `golang.org/x/net/webdav`, `google.golang.org/api/drive/v3`, `golang.org/x/oauth2`
3. [ ] Viết `native/go-webdav/bind/bridge.go` với dummy API `NewServer`, `Start`, `Stop`, `Port`, `SessionSecret`

### Step 2: Xây dựng Build Script Gomobile
4. [ ] Tạo `tools/go-webdav-android/build.ps1` kiểm tra Go, NDK (từ `ANDROID_NDK_HOME` hoặc SDK path), gomobile
5. [ ] Chạy lệnh `gomobile bind -target=android/arm,android/arm64,android/amd64 -o app/libs/go-webdav.aar ./native/go-webdav/bind`
6. [ ] Đo dung lượng AAR và từng `.so` trong AAR

### Step 3: Tích hợp AAR vào `:app` & R8 Rules
7. [ ] Cập nhật `app/build.gradle.kts` để import `libs/go-webdav.aar`
8. [ ] Thêm Proguard/R8 rules trong `app/proguard-rules.pro` giữ nguyên `go.**` và `go.Seq`
9. [ ] Viết unit test hoặc instrumented test trên LDPlayer để gọi `GoBridge.testLifecycle()`

### Step 4: Audit XML & Basic Auth Compatibility
10. [ ] Tạo script Go test kiểm tra phản hồi `PROPFIND` với các trường `displayname`, `getcontentlength`, `getlastmodified`, `resourcetype`, `creationdate`
11. [ ] Parse thử output XML bằng Jsoup theo đúng logic của `WebDav.kt:parseBody()`
12. [ ] Thử nghiệm Basic Auth header: `Authorization: Basic base64(legado:session_secret)`

## Files to Create/Modify
- `native/go-webdav/go.mod` — Go module manifest
- `native/go-webdav/bind/bridge.go` — Gomobile export definitions
- `tools/go-webdav-android/build.ps1` — Automated AAR build script
- `app/build.gradle.kts` — Link AAR dependency
- `app/proguard-rules.pro` — Proguard keep rules for Go runtime
- `app/src/test/java/io/legado/app/help/drive/GoBridgeSpikeTest.kt` — Spike verification test

## Test Criteria
- [ ] PASS-01.1: `.\tools\go-webdav-android\build.ps1` chạy không lỗi, tạo ra `app/libs/go-webdav.aar`.
- [ ] PASS-01.2: `.\gradlew.bat :app:compileAppDebugKotlin` biên dịch thành công khi import bridge classes.
- [ ] PASS-01.3: `.\gradlew.bat :app:assembleAppRelease` chạy R8 shrinking thành công không bị strip native methods.
- [ ] PASS-01.4: Output XML của Go WebDAV được Jsoup parse ra danh sách `WebDavFile` chính xác 100%.

---
Next Phase: `phase-02-go-drive-filesystem.md`
