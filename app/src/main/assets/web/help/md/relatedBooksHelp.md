# Truyện liên quan

Truyện liên quan là danh sách gợi ý lấy từ trang chi tiết, trang tác giả, thẻ thể loại hoặc API của nguồn.

## Rule cần có

Một cấu hình truyện liên quan thường cần:

- Rule vùng danh sách.
- Rule tên truyện.
- Rule tác giả nếu có.
- Rule bìa nếu có.
- Rule URL chi tiết.
- Rule mô tả ngắn nếu nguồn cung cấp.

## Cách kiểm tra

1. Mở thông tin sách.
2. Kéo đến vùng truyện liên quan.
3. Nếu danh sách rỗng, mở gỡ lỗi nguồn ở bước thông tin sách.
4. Kiểm tra HTML/JSON có thật sự chứa danh sách liên quan không.

## Lỗi thường gặp

- URL chi tiết là đường dẫn tương đối nhưng chưa ghép domain.
- Rule danh sách bắt cả truyện hiện tại hoặc banner.
- Bìa bị thiếu vì nguồn dùng lazy image.
- Nguồn chỉ trả truyện liên quan sau khi chạy JavaScript phía web.

