# Model TTS cục bộ

Trình quản lý TTS hỗ trợ model Piper dạng hai file và gói bốn file.

## Cấu trúc hai file

- `<voice>.onnx`
- `<voice>.onnx.json`

## Cấu trúc bốn file

- model ONNX
- file cấu hình JSON tương ứng
- file phoneme/voice metadata
- file bổ sung được manifest khai báo

Tên model và JSON phải ghép đúng cặp. Mỗi voice có thể được import riêng, kiểm tra riêng và chọn độc lập.

## Import

1. Mở **Cài đặt TTS > Model cục bộ**.
2. Chọn file hoặc gói ZIP.
3. Xem danh sách voice được nhận diện.
4. Import, chạy câu thử và chọn voice mặc định.

Bộ import giới hạn số entry, kích thước giải nén, tên file trùng và path traversal. Gói lỗi không ghi đè model đang hoạt động.

> Ảnh minh họa: `docs/assets/guide/local-tts-import.png`
