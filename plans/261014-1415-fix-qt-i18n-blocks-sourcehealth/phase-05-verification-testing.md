# Phase 05: Verification, Benchmarks & LDPlayer Live Test

Status: ⬜ Pending
Dependencies: Phase 01, Phase 02, Phase 03, Phase 04

## Objective
Chạy toàn bộ bộ kiểm thử tự động (Unit Tests, Performance Benchmarks, Kotlin Compilation, APK Build), triển khai lên thiết bị giả lập LDPlayer (`emulator-5554`), và kiểm tra thực tế trên ứng dụng:
1. Xác nhận tốc độ dịch QT và tốc độ mapping "Thêm từ điển" trong Reader.
2. Xác nhận dialog "Mô hình NMT hoạt động" hiển thị chuẩn tiếng Việt không còn Mojibake.
3. Xác nhận các block tải gói đã được thu gọn tinh tế, chỉ còn tên gói tải.
4. Xác nhận tính năng "Sức khỏe nguồn" phản ánh trung thực, chính xác và đồng nhất với thực tế.

## Implementation Steps
1. [ ] **Chạy Unit Tests & Benchmark Suite**:
   - `.\gradlew.bat test --tests "io.legado.app.data.repository.QuickTranslationRepositoryTest"`
   - `.\gradlew.bat test --tests "io.legado.app.ui.quickdict.QuickDictionarySelectionResolverTest"`
   - `.\gradlew.bat test --tests "io.legado.app.model.translation.HachimiOnnxModelRegistryTest"`
   - `.\gradlew.bat test --tests "io.legado.app.data.repository.sourcehealth.SourceCheckEngineTest"`
2. [ ] **Kiểm tra biên dịch Kotlin nhanh**:
   - `.\gradlew.bat :app:compileAppDebugKotlin`
3. [ ] **Build APK Debug & Cài đặt lên LDPlayer**:
   - `.\gradlew.bat assembleAppDebug`
   - `adb -s emulator-5554 install -r app/build/outputs/apk/app/debug/app-app-universal-debug.apk`
4. [ ] **Thực hiện kiểm thử thực tế trên LDPlayer (Live Verification)**:
   - Mở màn hình Cài đặt dịch: Kiểm tra hộp thoại "Mô hình NMT hoạt động" hiển thị đúng `HachimiMT-60-QT zh-vi (QT/Hán Việt)`.
   - Mở Asset Delivery: Kiểm tra các block thẻ gói tải đã thu gọn chỉ còn tên gói tải.
   - Mở Reader: Bôi đen một cụm từ và bấm "Thêm từ điển", đo thời gian phản hồi (phải bật lên ngay lập tức < 50ms).
   - Mở Sức khỏe nguồn: Bấm "Kiểm tra ngay", xác nhận nguồn `ptwxz` và các nguồn khác trả về kết quả chính xác, đồng nhất với việc đọc truyện thực tế.
5. [ ] **Cập nhật Trinity State**:
   - Cập nhật `project_progress.json`, `.brain/session.json`.

## Test Criteria
- [ ] Mọi test suite đều vượt qua 100%.
- [ ] Toàn bộ 4 điểm người dùng yêu cầu được xác nhận trực tiếp trên giao diện thiết bị.
