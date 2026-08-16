# Gỡ lỗi nguồn sách

Màn gỡ lỗi nguồn sách dùng để kiểm tra từng bước của nguồn: tìm kiếm, thông tin sách, mục lục và nội dung chương.

## Cách dùng nhanh

1. Mở quản lý nguồn sách.
2. Chọn nguồn cần kiểm tra.
3. Mở menu gỡ lỗi.
4. Nhập từ khóa, URL truyện hoặc URL chương cần thử.
5. Chạy từng bước và đọc log trả về.

## Đọc log

- HTTP status khác thành công thường là lỗi mạng, chặn truy cập hoặc sai địa chỉ.
- Kết quả rỗng ở bước tìm kiếm thường do rule danh sách không còn khớp trang nguồn.
- Có HTML nhưng không trích được dữ liệu thường do rule tiêu đề, danh sách hoặc nội dung cần chỉnh.
- JavaScript lỗi cú pháp sẽ dừng tại rule đang chạy.
- Nếu log trả trang captcha hoặc trang đăng nhập, cần bổ sung cookie, header hoặc đổi nguồn.

Khi báo lỗi nguồn, nên gửi kèm tên nguồn, thao tác, log gỡ lỗi và trang gốc nếu có.

