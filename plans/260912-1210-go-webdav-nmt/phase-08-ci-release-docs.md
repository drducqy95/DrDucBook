# Phase 08: CI/CD, Release & Developer Docs (Track A - WebDAV)

Status: ⬜ Pending
Dependencies: `phase-07-testing-verification.md`

## Objective
Thiết lập quy trình tích hợp liên tục (CI) tự động hóa build AAR từ Go source code, cập nhật tài liệu phát triển cho lập trình viên, chuẩn bị tài liệu hướng dẫn người dùng, và chốt release candidate cho Track A.

## Scope
| In Scope | Out of Scope |
|---|---|
| Tài liệu hướng dẫn cài đặt môi trường Go/Gomobile/NDK | NMT model conversion (Track B) |
| Cập nhật GitHub Actions / CI workflow để build AAR | |
| Tài liệu kiến trúc `docs/specs/local-webdav-architecture.md` | |
| Kiểm tra size breakdown của APK Release cuối cùng | |

## Requirements
### Functional
- [ ] REQ-08.1: Tạo tài liệu `tools/go-webdav-android/README.md` hướng dẫn chi tiết từng bước:
  - Phiên bản Go tối thiểu, cài đặt `gomobile` và `gobind`.
  - Thiết lập biến môi trường `ANDROID_NDK_HOME`.
  - Lệnh chạy build script và xử lý các lỗi thường gặp (DNS, CGO, Windows path issues).
- [ ] REQ-08.2: CI pipeline:
  - Cache Go modules (`go/pkg/mod`) và Gomobile build artifacts.
  - Tự động build AAR nếu có thay đổi trong thư mục `native/go-webdav/`.
  - Xác thực SHA-256 checksum của AAR trước khi build Android APK.
- [ ] REQ-08.3: Tạo tài liệu kiến trúc `docs/specs/local-webdav-architecture.md` ghi nhận toàn bộ thiết kế hệ thống, flowchart xác thực, và quyết định kiến trúc.

### Non-Functional
- [ ] NF-08.1: Thời gian build AAR trên CI runner < 3 phút khi có cache.
- [ ] NF-08.2: Tài liệu rõ ràng, minh bạch cho các nhà phát triển kế thừa.

## Implementation Steps
### Step 1: Hoàn thiện Developer Docs
1. [ ] Viết `tools/go-webdav-android/README.md`
2. [ ] Viết `docs/specs/local-webdav-architecture.md`

### Step 2: Cấu hình CI / Workflow
3. [ ] Cập nhật file cấu hình CI (GitHub Actions workflow) để bổ sung setup Go step
4. [ ] Thêm step cache Gomobile toolchain và dependencies

### Step 3: Release Packaging & Verification
5. [ ] Chạy full release build `./gradlew assembleAppRelease`
6. [ ] Đo lường kích thước APK per-ABI và Universal APK, đối chiếu với budget ban đầu

## Files to Create/Modify
- `tools/go-webdav-android/README.md` — Developer setup & build guide
- `docs/specs/local-webdav-architecture.md` — Architecture documentation
- `.github/workflows/android.yml` (hoặc build scripts CI tương ứng) — CI integration

## Test Criteria
- [ ] PASS-08.1: Làm theo hướng dẫn trong `README.md` trên một máy tính clean có thể build thành công AAR từ zero.
- [ ] PASS-08.2: CI runner build thành công APK Release có tích hợp Go WebDAV AAR.

---
Next Phase: `phase-09-nmt-contract-audit.md` (Track B Start)
