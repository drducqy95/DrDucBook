# Phase 07: Polish, R8 Verification & Device Testing

Status: ⬜ Pending
Dependencies: Phases 01-06

## 1. Mục tiêu

Phase tổng hợp cuối cùng đảm bảo toàn bộ tính năng mới hoạt động ổn định trên thiết bị thực tế, qua R8 minification, và xử lý các edge case chưa được bao phủ trong Phases 01-06.

---

## 2. Các Hạng Mục Bổ Sung (Gaps từ Phases 01-06)

### 2.1. Bảo Toàn HTML Markup Trong QT Instant
- Văn bản Reader có thể chứa thẻ HTML (`<p>`, `<br>`, `<img>`, Ruby tags...).
- Thuật toán `translateClassic` phải nhận diện và bypass các thẻ HTML/entity (`&nbsp;`, `&quot;`) mà không dịch nhầm.
- Tích hợp `protectMarkup` hoặc cơ chế token-level bypass nhanh chóng.

### 2.2. Tab Miêu Công Tử Trong Batch Install
- `BookSourceHubScreen` có 2 tab: YCKCEO (nguồn lẻ) và Miao Gong Zi (bộ nguồn gói).
- Quyết định: Ẩn thanh batch khi ở tab bundle, HOẶC hỗ trợ chọn nhiều bundle.
- Đảm bảo UX nhất quán giữa 2 tab.

### 2.3. Bộ Lọc "Chưa Cài" Trong Kho Nguồn
- Bổ sung FilterChip "Chưa cài" (Uninstalled only) trên BookSourceHubScreen.
- Giúp người dùng tìm nhanh nguồn chưa có trên máy.

### 2.4. R8 ProGuard Keep Rules
- Đảm bảo các model class mới (`ApiKeyBundle`, `ApiKeyEntry`, `ApiKeySyncStatus`) không bị R8 xóa/đổi tên.
- Kiểm tra serialization annotations (`@Serializable`, `@Keep`).
- Build `assembleAppRelease` thành công.

### 2.5. Kiểm Thử Thiết Bị Thực Tế
- Cài APK debug lên LDPlayer emulator-5554.
- Đo FPS cuộn sách khi bật QT Instant (target: 60fps).
- Kiểm tra batch install hoạt động trên cả 2 tab.
- Kiểm tra API key sync đăng nhập → restore → sử dụng ngay.

---

## 3. Implementation Steps

1. [ ] Tích hợp HTML markup bypass vào `translateClassic` và `translateClassicMapped`.
2. [ ] Quyết định và triển khai hành vi batch trên tab Miao Gong Zi.
3. [ ] Thêm FilterChip "Chưa cài" vào `BookSourceHubScreen`.
4. [ ] Cập nhật `proguard-rules.pro` cho API Key Sync models.
5. [ ] Build `assembleAppRelease` và kiểm tra R8 output.
6. [ ] Cài đặt APK lên thiết bị thực tế và kiểm thử toàn diện.
7. [ ] Chạy `.\\gradlew.bat :app:compileAppDebugKotlin` cuối cùng.

## Test Criteria
- [ ] QT Instant không cắt nát HTML tags.
- [ ] Batch install hoạt động trên cả 2 tab Kho Nguồn.
- [ ] Filter "Chưa cài" lọc chính xác.
- [ ] `assembleAppRelease` biên dịch thành công.
- [ ] Tất cả unit tests PASS.
- [ ] Thiết bị thực tế: cuộn mượt 60fps, QT < 2ms, API key sync round-trip OK.

## Files to Modify
- `app/src/main/java/io/legado/app/data/repository/QuickTranslationRepository.kt` (markup bypass)
- `app/src/main/java/io/legado/app/ui/book/source/hub/BookSourceHubScreen.kt` (filter chip, bundle tab)
- `app/proguard-rules.pro` (keep rules)
