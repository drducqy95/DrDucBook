# Phase 03: Hoàn thiện Công cụ Tạo & Sửa lỗi Nguồn sách (Source Creation & Repair Tools)

## 1. Mục tiêu
Giải quyết các hạn chế hiện tại của công cụ xử lý nguồn sách trong Agent/Chatbot:
1. Nâng cấp `repair_book_source` để sửa được các selector quy tắc bóc tách thực tế (`ruleSearch`, `ruleToc`, `ruleContent`, `ruleBookInfo`, `header`, `loginUrl`, `weight`), thay vì chỉ chỉnh cờ enabled/vbook stubs đơn giản.
2. Bổ sung công cụ `save_book_source` cho phép Agent tạo mới hoặc cập nhật nguồn sách trực tiếp một cách hoàn chỉnh (kèm kiểm tra validation cấu trúc JSON chặt chẽ trước khi lưu).
3. Bổ sung công cụ `test_book_source_rule` giúp Agent có thể thử nghiệm bóc tách trực tiếp trên URL hoặc HTML để kiểm tra xem rule mới sửa có hoạt động đúng không trước khi lưu vào cơ sở dữ liệu.

## 2. Thiết kế chi tiết các công cụ

### 2.1. Nâng cấp toàn diện `repair_book_source`
- **Các trường mới hỗ trợ sửa đổi trực tiếp:**
  - `bookSourceName` (String): Đổi tên nguồn.
  - `bookSourceUrl` (String): Đổi base URL của nguồn (khi website đổi domain/tên miền).
  - `bookSourceGroup` (String): Nhóm nguồn.
  - `ruleSearch` (JsonObject hoặc String): Cập nhật quy tắc tìm kiếm (subfields: `name`, `author`, `bookUrl`, `coverUrl`, `intro`, `checkKeyWord`, v.v.).
  - `ruleBookInfo` (JsonObject hoặc String): Cập nhật quy tắc trang thông tin sách (subfields: `name`, `author`, `intro`, `tocUrl`, `coverUrl`).
  - `ruleToc` (JsonObject hoặc String): Cập nhật quy tắc mục lục (subfields: `chapterList`, `chapterName`, `chapterUrl`, `nextTocUrl`).
  - `ruleContent` (JsonObject hoặc String): Cập nhật quy tắc nội dung chương (subfields: `content`, `nextContentUrl`, `replaceRegex`).
  - `header` (String): Thêm/sửa User-Agent, Cookie, Referer.
  - `loginUrl` (String): Cập nhật URL đăng nhập.
  - `weight` (Int): Độ ưu tiên của nguồn khi tìm kiếm.
- **Quy trình thực thi:**
  1. Tìm nguồn hiện tại qua `resolveBookSource(args)`.
  2. Ghi nhận `before` snapshot.
  3. Áp dụng các thay đổi rule cụ thể vào đối tượng `BookSource`.
  4. Cập nhật `lastUpdateTime = System.currentTimeMillis()` và lưu qua `bookSourceDao.update(source)`.
  5. Nếu `runHealthCheck = true` (mặc định true): Chạy kiểm tra sức khỏe nguồn bằng `sourceCheckEngine.checkBookSource(source)` để báo cáo ngay kết quả thành công hay thất bại của các bước chẩn đoán.
- **Rủi ro:** `AgentActionRisk.WRITE`.
- **Nhóm quyền:** `ChatbotToolCategory.BOOK_SOURCE_AND_PLUGINS`.

### 2.2. Bổ sung `save_book_source` (Tạo mới hoặc lưu nguồn sách trực tiếp)
- **Đầu vào:**
  - `sourceJson` (String, bắt buộc nếu truyền full JSON nguồn): Chuỗi JSON đầy đủ của BookSource (Legado format).
  - Hoặc các trường cấu hình trực tiếp: `bookSourceName`, `bookSourceUrl`, `bookSourceType`, `ruleSearch`, `ruleToc`, `ruleContent`, v.v.
  - `enableAfterSave` (Boolean, mặc định true).
- **Quy trình xử lý:**
  1. Parse và validate bằng `BookSourceValidator` để đảm bảo không có lỗi cú pháp hoặc trường bắt buộc bị thiếu.
  2. Ghi nhận hoặc cập nhật nguồn vào DB qua `SourceHelp.insertBookSource(source)`.
  3. Trả về thông tin nguồn đã lưu kèm trạng thái kích hoạt.
- **Rủi ro:** `AgentActionRisk.WRITE`.
- **Nhóm quyền:** `ChatbotToolCategory.BOOK_SOURCE_AND_PLUGINS`.

### 2.3. Bổ sung `test_book_source_rule` (Thử nghiệm rule bóc tách trực tiếp)
- **Đầu vào:**
  - `sourceUrl` (String, tùy chọn): URL nguồn đã cài đặt cần test.
  - `sourceJson` (String, tùy chọn): JSON nguồn chưa lưu (dùng để test trước khi cài đặt).
  - `testType` (String, bắt buộc): `"SEARCH"`, `"INFO"`, `"TOC"`, hoặc `"CONTENT"`.
  - `targetUrl` (String, tùy chọn): URL của trang sách / chương truyện cụ thể dùng làm mẫu thử.
  - `keyword` (String, tùy chọn): Từ khóa tìm kiếm nếu `testType == "SEARCH"`.
- **Quy trình xử lý:**
  1. Khởi tạo đối tượng `BookSource` tương ứng.
  2. Sử dụng `WebBook` hoặc `AnalyzeRule` thực hiện request mạng và bóc tách theo rule tương ứng.
  3. Bắt và phân tích lỗi (nếu selector không tìm thấy phần tử, hoặc selector trả về danh sách rỗng).
  4. Trả về kết quả mẫu (ví dụ: tên 3 chương đầu tiên tìm được, 1 đoạn nội dung chương đầu bóc tách được) để Agent tự đánh giá tính chính xác của selector.
- **Rủi ro:** `AgentActionRisk.READ` (An toàn, tự duyệt).

## 3. Tiêu chí kiểm thử
- Thử sửa selector `ruleToc.chapterList` của một nguồn bị lỗi -> Lưu thành công và re-check đạt PASS.
- Thử lưu một nguồn Legado mới qua `save_book_source` -> Xuất hiện trên danh sách nguồn sách của app.
- Gọi `test_book_source_rule` trên một trang web mẫu -> Trả về danh sách kết quả bóc tách mà không gây crash hay rò rỉ bộ nhớ.
