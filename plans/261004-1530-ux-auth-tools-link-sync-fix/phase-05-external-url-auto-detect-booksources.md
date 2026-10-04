# Phase 05: External URL Auto-Detection & BookSource Routing

Status: ⬜ Pending
Dependencies: None

## Objective
Bổ sung chức năng tự động nhận diện, nạp truyện theo BookSource khi nhận link URL từ bên ngoài:
- Khi người dùng dán hoặc mở một URL (từ trình duyệt, clipboard, intent chia sẻ từ ứng dụng khác):
  - Ứng dụng tự động kiểm tra xem URL đó có thuộc về bất kỳ nguồn truyện (`BookSource`) nào đã được cài đặt hay không (dựa trên domain, base URL hoặc `bookUrlPattern`).
  - Nếu đúng nguồn truyện đã cài:
    - Nếu là link chi tiết/đọc truyện: Tự động nạp thông tin sách và mở màn hình Chi tiết sách (`BookInfoActivity`).
    - Nếu là link danh mục/khám phá: Tự động mở danh sách truyện theo nguồn trong Khám phá (`MainRouteExploreShow`).

## Requirements

### Functional
- [ ] Xây dựng `ExternalUrlResolverUseCase`:
  - Trích xuất và chuẩn hóa logic so khớp nguồn truyện từ `AddToBookshelfDialog`:
    1. Chuẩn hóa URL đầu vào: loại bỏ khoảng trắng, ký tự ngoặc nhọn `{`, dấu nháy kép `"`, chuẩn hóa `https://` và `http://`.
    2. So khớp qua URL option parameter `{origin: "..."}`.
    3. So khớp chính xác hoặc tiền tố với `bookSourceDao.getBookSourceAddBook(baseUrl)`.
    4. So khớp biểu thức chính quy `bookUrlPattern` qua `bookSourceDao.hasBookUrlPattern`.
  - Phân loại kết quả phân giải (`ResolvedExternalUrl`):
    - `BookDetail(bookSource: BookSource, bookUrl: String)` -> Nạp thông tin sách qua `WebBook.getBookInfoAwait` và mở `BookInfoActivity`.
    - `ExploreCategory(bookSource: BookSource, categoryUrl: String)` -> Mở danh mục trong `MainRouteExploreShow`.
    - `Unmatched(url: String)` -> Mở trình duyệt ngoài hoặc hiển thị thông báo.
- [ ] Cập nhật `AddToBookshelfDialog.kt` để tái sử dụng `ExternalUrlResolverUseCase` thay vì tự triển khai logic so khớp riêng biệt.
- [ ] Xử lý Intent Filter trong `AndroidManifest.xml` & `OpenUrlConfirmActivity` / `MainActivity`:
  - Tiếp nhận cả HTTP/HTTPS URL từ bên ngoài để tự động nhận diện nguồn truyện đã cài.
- [ ] Tự động phát hiện URL trong Clipboard khi mở app (tùy chọn trong Cài đặt, mặc định hỏi xác nhận qua Snackbar / Dialog):
  - *"Phát hiện liên kết truyện từ nguồn [Tên nguồn]: Bạn có muốn mở không?"* -> Nhấn "Mở" sẽ lập tức điều hướng.

### Non-Functional
- [ ] Tốc độ truy vấn nguồn nhanh (< 50ms) bằng cách tận dụng index SQLite và cache bộ nhớ của `bookSourceDao`.
- [ ] Không làm gián đoạn người dùng nếu liên kết không phải là truyện hoặc nguồn chưa cài đặt.

## Implementation Steps
1. [ ] Tạo `io/legado/app/domain/usecase/ExternalUrlResolverUseCase.kt`.
2. [ ] Đăng ký `ExternalUrlResolverUseCase` trong `di/appModule.kt`.
3. [ ] Cập nhật `AddToBookshelfDialog.kt` để delegate sang `ExternalUrlResolverUseCase`.
4. [ ] Cập nhật `OpenUrlConfirmActivity.kt`:
   - Sử dụng `ExternalUrlResolverUseCase` để mở thông minh thay vì chỉ mở trình duyệt ngoài.
5. [ ] Cập nhật `MainActivity.kt` & `HomeScreen.kt`:
   - Thêm cơ chế kiểm tra clipboard khi resume / startup.
6. [ ] Cập nhật `AndroidManifest.xml`:
   - Bổ sung intent-filter cho HTTP/HTTPS deep link.

## Files to Create/Modify
- `app/src/main/java/io/legado/app/domain/usecase/ExternalUrlResolverUseCase.kt`
- `app/src/main/java/io/legado/app/ui/association/OpenUrlConfirmActivity.kt`
- `app/src/main/java/io/legado/app/ui/association/AddToBookshelfDialog.kt`
- `app/src/main/java/io/legado/app/ui/main/MainActivity.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/test/java/io/legado/app/domain/usecase/ExternalUrlResolverUseCaseTest.kt`

## Test Criteria
- [ ] Truyền vào link truyện `https://fanqienovel.com/page/...` hoặc `https://www.shenwen.org/...` -> Tự động nhận diện nguồn Fanqie/Shenwen và mở BookInfo.
- [ ] Truyền vào link khám phá của nguồn -> Mở đúng màn hình Khám phá.

---
Next Phase: [Phase 06 - Translation URL & Pinyin Normalization](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/plans/261004-1530-ux-auth-tools-link-sync-fix/phase-06-book-translation-url-normalization-pinyin-fix.md)
