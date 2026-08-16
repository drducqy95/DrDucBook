# Hướng dẫn nguồn sách

Nguồn sách gồm các rule giúp app tìm và đọc truyện từ website hoặc API.

## Luồng cơ bản

1. Search: tìm truyện theo từ khóa.
2. Book info: lấy thông tin truyện.
3. TOC: lấy danh sách chương.
4. Content: lấy nội dung chương.

## Search

Cần kiểm tra:

- URL tìm kiếm có nhận đúng từ khóa không.
- Header/cookie nếu nguồn chặn bot.
- Rule danh sách kết quả.
- Rule tên, tác giả, bìa và URL chi tiết.

## Book info

Các trường thường dùng:

- Tên truyện.
- Tác giả.
- Bìa.
- Mô tả.
- Thể loại.
- Trạng thái.
- URL mục lục.

## TOC

Mục lục cần lấy tên chương và URL chương. Nếu nguồn phân trang mục lục, cần thêm rule lấy trang tiếp theo hoặc toàn bộ danh sách.

## Content

Nội dung chương có thể là:

- Văn bản HTML.
- JSON chứa đoạn văn.
- Danh sách ảnh.
- Nội dung cần giải mã bằng JavaScript.

Sau khi lấy nội dung, có thể dùng rule thay thế để xóa quảng cáo hoặc phần dư.

