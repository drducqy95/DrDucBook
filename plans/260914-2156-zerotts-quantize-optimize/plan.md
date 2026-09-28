# Kế Hoạch: Tối Ưu Tốc Độ & Lượng Tử Hóa ZeroTTS ONNX Trên Android

Ngày tạo: 2026-09-14T21:56:00+07:00
Trạng thái: 🟡 Đang Chờ Duyệt

## Tổng Quan

ZeroTTS đã hoạt động đúng chức năng (import + synthesis thành công), nhưng tốc độ sinh âm thanh quá chậm trên thiết bị Android. Kế hoạch này tối ưu toàn diện qua 3 giai đoạn: sửa lỗi runtime → lượng tử hóa model → tối ưu nâng cao.

## Quyết Định Đã Xác Nhận

| Câu hỏi | Quyết định |
|---|---|
| Phân phối HuggingFace | **Thay thế bản FP32 cũ** bằng bản INT8 mới (~500 MB thay vì ~900 MB) |
| Số luồng CPU | **Tự động dò nhân CPU** (`availableProcessors / 2`, tối thiểu 2, tối đa 4) |
| Thứ tự thực hiện | **Hoàn thành tất cả Phase 1 + 2 + 3, rồi test tổng thể cuối cùng** |

## Tech Stack

- **Android Runtime**: ONNX Runtime Android 1.27.0 + XNNPACK
- **Quantization Tool**: Python `onnxruntime.quantization` (Dynamic INT8)
- **Model Source**: `zeroweight-ai/ZeroTTS` (4 ONNX graphs, 903 MB FP32)
- **Target**: Repo HuggingFace `Drduc/Legadofork`

## Các Giai Đoạn

| Phase | Tên | Trạng Thái | Tiến Độ |
|---|---|---|---|
| 01 | Tối Ưu Runtime Android (Không đổi model) | ⬜ Chờ | 0% |
| 02 | Lượng Tử Hóa Model & Đóng Gói INT8 | ⬜ Chờ | 0% |
| 03 | Tối Ưu Nâng Cao (ORT Format + Streaming) | ⬜ Chờ | 0% |

## Lệnh Nhanh

- Bắt đầu Phase 1: `/code phase-01`
- Xem tiến độ: `/next`
- Lưu context: `/save-brain`
