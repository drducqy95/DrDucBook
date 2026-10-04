# Phase 01: Book Intro UI & Typography Formatting

Status: ⬜ Pending
Dependencies: None

## Objective
Tối ưu hóa hiển thị phần giới thiệu truyện trong `BookInfoScreen` và `BookInfoViewModel`:
- Loại bỏ hiện tượng văn bản dính chùm thành một khối không thể đọc được khi nguồn truyện cung cấp các thẻ emoji/metadata không có dấu xuống dòng (như ảnh người dùng cung cấp: `📖Sách ID:53485 🗳️Thời điểm hiện tại... 🔥Nhiệt độ:... 📜Tóm tắt:...`).
- Tách bạch phần thông tin kỹ thuật (Metadata / Thông số nguồn) và phần Tóm tắt nội dung truyện (Synopsis).
- Thiết lập bố cục đoạn văn rõ ràng, khoảng cách dòng thoáng (lineHeight phù hợp), thụt đầu dòng tự nhiên, hỗ trợ chế độ xem mở rộng / thu gọn ("Xem thêm" / "Thu gọn").

## Requirements

### Functional
- [ ] Phân tích và nhận diện cấu trúc thông tin giới thiệu truyện:
  - Nhận diện các thẻ metadata có emoji/nhãn đầu mục (`📖`, `🗳️`, `🔥`, `📁`, `🏷️`, `🕰️`, `📜`, `🔻`, `❗️`, `【...】`, `[...]:`).
  - Tự động tách các thẻ này thành các dòng riêng biệt hoặc nhóm thẻ (chips/badges).
  - Tách riêng đoạn văn bản tóm tắt truyện (`📜Tóm tắt: ...` hoặc phần văn xuôi chính) thành khu vực văn bản độc lập.
  - Tách phần bình luận / ghi chú nguồn (`🔻Quận bình luận về:`, `❗️Góc trên bên phải...`) thành phần ghi chú phụ có thể thu gọn.
  - **Heuristic kiểm tra**: Nếu văn bản đã có sẵn cấu trúc đoạn rõ ràng (`\n\n`) và không chứa chuỗi thẻ metadata dính chùm, giữ nguyên định dạng tự nhiên, tránh xử lý dư thừa.
- [ ] Hỗ trợ làm sạch các ký tự rác HTML (`<p>`, `<br>`, `&nbsp;`, `\u3000`) nhưng giữ nguyên cấu trúc phân đoạn tự nhiên (`\n\n`).
- [ ] Cung cấp nút chuyển đổi "Xem thêm" / "Thu gọn" cho phần giới thiệu dài (> 5-6 dòng) để tránh đẩy các nút điều hướng bên dưới xuống quá sâu.
- [ ] **Tương thích hoàn hảo với QuickDictionary**:
  - Không bọc `QuickDictionarySelectableText` trong các container cắt xén layout cứng làm hỏng menu pop-up tra từ điển.
  - Điều khiển hiển thị số dòng qua `maxLines` hoặc phân đoạn rõ ràng.
  - Loại bỏ lệnh gọi `HtmlCompat.fromHtml` dư thừa ở `BookInfoScreen` (tập trung chuyển đổi duy nhất tại `BookIntroFormatter` / `BookInfoViewModel`).

### Non-Functional
- [ ] Giữ nguyên tương thích với `QuickDictionarySelectableText` để người dùng vẫn có thể tra từ điển Hán-Việt/dịch nhanh ngay trong phần giới thiệu cả ở trạng thái thu gọn và mở rộng.
- [ ] Đảm bảo giao diện mượt mà theo phong cách Material 3 Expressive, màu sắc hài hòa theo chủ đề hệ thống (`LegadoTheme.colorScheme`).

## Implementation Steps
1. [ ] Tạo `io/legado/app/help/book/BookIntroFormatter.kt`:
   - Bộ tiền xử lý thông minh tách metadata emoji và chuẩn hóa đoạn văn bản synopsis.
   - Hàm `format(raw: String?): FormattedBookIntro` phân loại: metadata tags, synopsis text, auxiliary notes.
2. [ ] Cập nhật `BookInfoViewModel.kt`:
   - Sử dụng `BookIntroFormatter` trong `formatIntro` và mapping UI state.
3. [ ] Cập nhật `BookInfoScreen.kt`:
   - Xóa `HtmlCompat.fromHtml` trùng lặp trong Composable.
   - Thiết kế composable hiển thị Intro:
     - Khối thông tin thẻ nguồn / Metadata Tags (nếu có).
     - Khối nội dung tóm tắt chính với `lineHeight = 22.sp`, khoảng cách đoạn `\n\n`.
     - Cơ chế ExpandableText ("Xem thêm" / "Thu gọn").
4. [ ] Cập nhật `strings.xml` / `strings-vi.xml`:
   - Bổ sung chuỗi "Xem thêm", "Thu gọn", "Thông tin nguồn", "Tóm tắt truyện".

## Files to Create/Modify
- `app/src/main/java/io/legado/app/help/book/BookIntroFormatter.kt` - Bộ xử lý cấu trúc đoạn và thẻ metadata
- `app/src/main/java/io/legado/app/ui/book/info/BookInfoViewModel.kt` - Logic phân tích định dạng intro
- `app/src/main/java/io/legado/app/ui/book/info/BookInfoScreen.kt` - UI composable hiển thị intro
- `app/src/main/res/values/strings.xml` & `app/src/main/res/values-vi/strings.xml` - Chuỗi tài nguyên
- `app/src/test/java/io/legado/app/help/book/BookIntroFormatterTest.kt` - Unit tests cho bộ định dạng intro

## Test Criteria
- [ ] Mở sách có metadata dính chùm (như Alice, 52shuku), văn bản hiển thị từng dòng rõ ràng, tóm tắt tách biệt.
- [ ] Bấm tra từ điển QuickDictionary trên đoạn giới thiệu hoạt động chính xác cả khi thu gọn và xem thêm.
- [ ] Nút "Xem thêm" / "Thu gọn" hoạt động mượt mà không bị giật layout.

---
Next Phase: [Phase 02 - Supabase API Key Sync Fix](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/plans/261004-1530-ux-auth-tools-link-sync-fix/phase-02-api-key-cloud-sync-fix.md)
