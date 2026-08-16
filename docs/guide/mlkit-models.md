# Google ML Kit và model dịch ngoại tuyến

ML Kit tải model ngôn ngữ theo yêu cầu và dịch hoàn toàn trên thiết bị sau khi model đã sẵn sàng.

## Tải model

1. Mở **Cài đặt dịch**.
2. Chọn **Google ML Kit**.
3. Chọn ngôn ngữ nguồn và đích, sau đó tải model.
4. Đợi trạng thái chuyển sang sẵn sàng rồi chạy đoạn thử ngắn.

## Cache

Cache ML Kit độc lập với QT, NMT, Google Translate và AI. Đổi provider không xóa cache. Chỉ các chunk liên quan được làm mới khi từ điển truyện thay đổi.

## Kiểm tra output

Kết quả còn chữ CJK ngoài danh sách cho phép bị coi là chưa đạt. Ứng dụng giữ bản cache đã pass, chỉ đánh dấu chunk lỗi để thử lại thay vì xóa toàn chương.

## Khắc phục lỗi

- Xác nhận model ngôn ngữ đã tải xong.
- Kiểm tra đủ dung lượng trống và kết nối khi tải lần đầu.
- Nếu output còn CJK, thử lại chunk lỗi hoặc đổi cặp ngôn ngữ; không cần xóa cache provider khác.

> Ảnh minh họa: `docs/assets/guide/mlkit-model-manager.png`
