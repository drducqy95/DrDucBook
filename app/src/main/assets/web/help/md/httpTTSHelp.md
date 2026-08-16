# HTTP TTS

HTTP TTS cho phép dùng dịch vụ đọc truyện qua HTTP thay vì chỉ dùng engine TTS hệ thống.

## Thành phần cấu hình

- Tên cấu hình.
- URL endpoint.
- Method: thường là GET hoặc POST.
- Header: ví dụ Content-Type, Authorization, User-Agent.
- Body: nội dung gửi lên server nếu dùng POST.
- Rule âm thanh: cách lấy URL/file âm thanh từ phản hồi.

## Biến thường dùng

Tùy màn cấu hình, có thể dùng biến như:

- Nội dung cần đọc.
- Ngôn ngữ.
- Tốc độ đọc.
- Giọng đọc.
- Token hoặc khóa API nếu người dùng tự cấu hình.

## Lưu ý bảo mật

- Không chia sẻ rule chứa khóa API cá nhân.
- Nếu dùng dịch vụ trả phí, kiểm tra giới hạn quota.
- Nếu server trả lỗi, xem log HTTP để biết lỗi do xác thực, định dạng body hay giới hạn dịch vụ.

## Kiểm tra

Sau khi lưu, dùng nút thử đọc với một câu ngắn. Nếu không phát âm thanh:

1. Kiểm tra status HTTP.
2. Kiểm tra phản hồi là audio trực tiếp hay JSON chứa URL audio.
3. Kiểm tra rule trích audio.
4. Kiểm tra quyền mạng và cấu hình proxy nếu có.

