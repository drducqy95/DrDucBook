# AI Router và cấu hình model

AI Router quản lý provider, credential, model lẻ và combo fallback dùng chung cho chatbot, dịch, sáng tác và biên tập ebook.

## Thiết lập provider

1. Mở **AI Router** từ thanh điều hướng.
2. Chọn **Thêm API key/token** hoặc đăng nhập OAuth ở provider hỗ trợ.
3. Nhập danh sách model từ provider, sau đó bật các model cần dùng.
4. Mở chi tiết model để chạy kiểm tra kết nối.

Credential được lưu qua kho bí mật của Android. Log chẩn đoán chỉ hiển thị dữ liệu đã che.

## Model lẻ và combo fallback

- **Model lẻ**: chỉ gọi đúng model đã chọn.
- **Combo fallback**: gọi lần lượt các model trong combo khi model trước lỗi, hết hạn mức, trả rỗng hoặc không đạt kiểm tra output.
- Mỗi prompt có một mục chọn đích AI thống nhất. Tên combo được hiển thị khi prompt đang dùng combo.
- Cấu hình của AI Agent và cấu hình dịch là hai tác vụ độc lập.

## OAuth

Sau khi đăng nhập, chạy kiểm tra bằng một model OAuth lẻ trước khi đưa model vào combo. Nếu model báo xác thực sai, đăng nhập lại và kiểm tra thời gian hệ thống của thiết bị.

## Chẩn đoán

Dashboard hiển thị loại lỗi, số lỗi liên tiếp, độ trễ và lịch sử định tuyến. Một model lỗi không làm mất kết quả đã hoàn tất của model khác.

> Ảnh minh họa: `docs/assets/guide/ai-router-dashboard.png`
