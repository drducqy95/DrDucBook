# Phase 07: Automated Verification & On-Device Testing

Status: ⬜ Pending
Dependencies: Phases 01, 02, 03, 04, 05, 06

## Objective
Thực thi toàn diện các bài kiểm thử tự động, biên dịch code, và kiểm tra thực tế trên máy ảo Android LDPlayer (`emulator-5554`) để xác nhận tất cả 6 vấn đề đã được khắc phục hoàn hảo:
1. UI Giới thiệu truyện hiển thị đẹp mắt, tách biệt metadata và tóm tắt, xuống dòng chuẩn.
2. Đồng bộ API Key lên đám mây Supabase thành công 100% (không còn lỗi NoSuchKey).
3. Google Antigravity hiển thị đúng link xác minh tài khoản khi gặp HTTP 403 `VALIDATION_REQUIRED` và tự động chuyển sang route dự phòng nếu cấu hình trong combo.
4. Chatbot có màn hình cài đặt quyền công cụ, bật quyền tự động duyệt giúp chatbot thực thi trơn tru không bị treo.
5. URL ngoại vi khớp với BookSource đã cài đặt tự động mở trang chi tiết sách hoặc khám phá.
6. URL sách được làm sạch, `wordCount` an toàn, dịch truyện trả về âm Hán-Việt thay vì tên Pinyin.

## Test Matrix

| ID | Test Target | Command / Steps | Expected Outcome |
|---|---|---|---|
| TC-01 | Unit Tests | `.\gradlew.bat testAppDebugUnitTest --tests "io.legado.app.data.repository.ApiKeySyncRepositoryTest"` | PASS 100% |
| TC-02 | Unit Tests | `.\gradlew.bat testAppDebugUnitTest --tests "io.legado.app.domain.agent.AgentPermissionBrokerTest"` | PASS 100% |
| TC-03 | Unit Tests | `.\gradlew.bat testAppDebugUnitTest --tests "io.legado.app.utils.UrlSanitizerTest"` | PASS 100% |
| TC-04 | Unit Tests | `.\gradlew.bat testAppDebugUnitTest --tests "io.legado.app.domain.model.AiProviderFailureTest"` | PASS 100% |
| TC-05 | Unit Tests | `.\gradlew.bat testAppDebugUnitTest --tests "io.legado.app.domain.usecase.AiRouterPolicyTest"` | PASS 100% |
| TC-06 | Unit Tests | `.\gradlew.bat testAppDebugUnitTest --tests "io.legado.app.help.book.BookIntroFormatterTest"` | PASS 100% |
| TC-07 | Unit Tests | `.\gradlew.bat testAppDebugUnitTest --tests "io.legado.app.domain.usecase.ExternalUrlResolverUseCaseTest"` | PASS 100% |
| TC-08 | Kotlin Compile Check | `.\gradlew.bat :app:compileAppDebugKotlin` | BUILD SUCCESSFUL |
| TC-09 | APK Assembly & Install | `.\gradlew.bat assembleAppDebug`<br>`adb -s emulator-5554 install -r app/build/outputs/apk/app/debug/app-app-universal-debug.apk` | Installed successfully |
| TC-10 | Live Supabase Sync | Mở Cài đặt AI -> Đồng bộ lên đám mây | Thành công, hiện timestamp |
| TC-11 | Live Chatbot Tool Test | Mở Chatbot -> Bật quyền -> Yêu cầu tìm kiếm/thao tác | Thực thi mượt mà, không gián đoạn |
| TC-12 | Live Book Intro UI | Mở sách có metadata dính chùm | Tách dòng đẹp mắt, có nút xem thêm |
| TC-13 | Live URL Routing | Mở URL sách qua adb shell am start | Nhận diện đúng nguồn, mở BookInfo |

## Execution Plan
1. [ ] Chạy toàn bộ các unit tests mới tạo và các test liên quan để đảm bảo không có regression.
2. [ ] Biên dịch Kotlin với `compileAppDebugKotlin`.
3. [ ] Build APK và cài đặt lên LDPlayer `emulator-5554`.
4. [ ] Khởi chạy và kiểm tra trực quan trên thiết bị thực tế.
5. [ ] Cập nhật `project_progress.json`, `.brain/session.json` và chuẩn bị báo cáo hoàn thành cho người dùng.
