# Phase 04: Automated Testing, APK Build & LDPlayer Live Validation

Status: ⬜ Pending
Dependencies: Phase 01, Phase 02, Phase 03

## Objective
Chạy toàn bộ unit test suite liên quan đến ML Kit và QuickDictionary, biên dịch bản build APK Debug x86_64, cài đặt lên giả lập LDPlayer (`emulator-5554`), thực hiện dịch lại và kiểm chứng trực tiếp chất lượng dịch trên màn hình đọc sách.

## Requirements

### Functional
- [ ] Chạy unit test Gradle:
  - `MlKitDictionaryEnforcerTest`
  - `MlKitGrammarPostProcessorTest`
  - `MlKitPronounNeutralizerTest`
  - `QuickTranslationRepositoryTest`
- [ ] Biên dịch `app-app-x86_64-debug.apk` sạch sẽ không có lỗi Kotlin compilation.
- [ ] Cài đặt APK qua ADB vào `emulator-5554`.
- [ ] Khởi chạy app, mở Chương 1 của *Luân Hồi Lạc Viên*, bấm "Dịch lại chương" với provider ML Kit.
- [ ] Chụp màn hình các trang đọc sách để kiểm chứng:
  - Tên nhân vật, địa danh, thế lực được dịch chuẩn theo từ điển QT và bộ nhớ dịch.
  - Ngữ pháp trôi chảy, không bị viết hoa giữa câu, đại từ chuẩn xác.
  - Không còn placeholder hoặc vết cắt nối cơ học.

## Implementation Steps
1. [ ] Chạy test cục bộ: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.MlKit*"`
2. [ ] Biên dịch APK: `.\gradlew.bat :app:assembleAppDebug`
3. [ ] Cài đặt lên thiết bị: `adb -s emulator-5554 install -r app\build\outputs\apk\app\debug\app-app-x86_64-debug.apk`
4. [ ] Mở ứng dụng và thực hiện dịch lại live trên LDPlayer.
5. [ ] Chụp ảnh kiểm chứng và lập báo cáo nghiệm thu hoàn tất.

## Test Criteria
- [ ] Tất cả unit tests đạt 100% PASS.
- [ ] Ứng dụng chạy mượt mà, không crash ART VM, văn phong tiếng Việt chuẩn ngữ pháp và sát nghĩa nguyên tác.
