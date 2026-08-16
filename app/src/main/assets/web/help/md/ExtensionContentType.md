# Kiểu nội dung mở rộng

Kiểu nội dung mở rộng giúp app nhận biết dữ liệu trả về từ nguồn hoặc từ server là văn bản, ảnh, âm thanh, file nén hay dữ liệu đặc biệt.

## Khi nào cần chỉnh

Chỉ cần chỉnh khi nguồn trả sai header hoặc nội dung không được app nhận diện đúng, ví dụ:

- Trang trả HTML nhưng header báo plain text.
- Chương ảnh trả về danh sách URL ảnh.
- API trả JSON nhưng rule lại cần đọc như văn bản.
- Âm thanh TTS trả về file nhị phân.

## Các nhóm thường dùng

- HTML: trang web cần phân tích selector, XPath hoặc JS.
- JSON: dữ liệu API cần trích field.
- Text: nội dung thuần.
- Image: ảnh bìa, ảnh chương hoặc truyện tranh.
- Audio: kết quả TTS.
- Binary: dữ liệu nhị phân khác.

## Lưu ý

- Ưu tiên sửa đúng rule và header trước khi ép kiểu nội dung.
- Không ép kiểu ảnh hoặc âm thanh thành text nếu dữ liệu là nhị phân.
- Với nguồn cần đăng nhập, kiểm tra cookie và header trước khi kết luận sai kiểu nội dung.

