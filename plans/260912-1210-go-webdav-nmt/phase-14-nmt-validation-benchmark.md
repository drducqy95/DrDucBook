# Phase 14: Device Validation & Benchmark (Track B - NMT)

Status: ⬜ Pending
Dependencies: `phase-13-nmt-ui-asset-delivery.md`

## Objective
Thực thi kiểm thử thực tế trên thiết bị vật lý (ARM64) và giả lập (x86_64), đo đạc các chỉ số hiệu năng (thời gian khởi động nguội, thời gian sinh token đầu tiên TTFS, tốc độ tokens/giây, tiêu thụ RAM), kiểm tra tính năng hủy dịch giữa chừng, phục hồi sau khi process `:nmt_onnx` bị crash/kill, và xác nhận không có hồi quy trên mô hình cũ `hachimi_onnx`.

## Scope
| In Scope | Out of Scope |
|---|---|
| Benchmark trên LDPlayer (x86_64) và điện thoại thật (ARM64 - HONOR / Huawei) | Thêm mô hình thứ ba ngoài Hachimi |
| Đo lường RAM RSS của tiến trình `:nmt_onnx` | WebDAV features (Track A) |
| Kiểm tra dịch chương truyện thực tế trong Reader | |
| Kiểm tra switch model liên tục trong lúc dịch | |
| Kiểm tra hủy (Cancel) và timeout recovery | |
| Kiểm tra hồi quy model `hachimi_onnx` | |

## Requirements
### Functional
- [ ] REQ-14.1: Kiểm thử dịch chương thực tế:
  - Mở một chương truyện tiếng Trung dài > 3000 từ.
  - Dịch bằng model `HachimiMT-60 zh→vi` gốc: Xác nhận văn phong tự nhiên, đúng tiến độ từng chunk.
  - Dịch lại bằng model `HachimiMT-60-QT zh→vi`: Xác nhận văn phong Hán Việt chuẩn xác, các từ ngữ tu tiên / cảnh giới / đại từ nhân xưng chuẩn phong cách QuickTranslator.
  - Kiểm tra từ điển áp dụng chính xác trên cả hai mô hình.
- [ ] REQ-14.2: Kiểm tra hủy & chuyển đổi mô hình (Stress & Interruption Test):
  - Bấm dịch -> bấm Hủy giữa chừng -> gửi request dịch mới -> hệ thống phản hồi ngay lập tức không bị treo tiến trình IPC.
  - Đang dịch bằng model A -> chuyển cấu hình sang model B -> dịch tiếp -> hệ thống nạp model B và giải phóng model A an toàn.
- [ ] REQ-14.3: Xử lý sự cố (Process Death Recovery):
  - Dùng `kill -9` trên tiến trình `:nmt_onnx` khi đang dịch -> app chính bắt được lỗi, tự động rebind service và phục hồi ở lần gọi dịch tiếp theo.
- [ ] REQ-14.4: Kiểm tra hồi quy (Regression Test):
  - Toàn bộ cơ chế dịch bằng Cloud AI (DeepSeek, Claude, ChatGPT, Google) và Local AI (GGUF) vẫn hoạt động nguyên vẹn, không bị ảnh hưởng bởi thay đổi cấu hình NMT.

### Non-Functional
- [ ] NF-14.1: Hiệu năng trên thiết bị ARM64 tầm trung:
  - Cold load time (nạp 5 file ONNX lần đầu): < 1.5 giây.
  - Tốc độ sinh text: > 15-25 tokens/giây trên CPU ARM64.
  - Mức tiêu thụ bộ nhớ RAM của tiến trình `:nmt_onnx`: < 300MB khi đang chạy suy luận.
- [ ] NF-14.2: Không xảy ra rò rỉ bộ nhớ native của ONNX Runtime sau 50 lần dịch liên tục.

## Implementation Steps
### Step 1: Chuẩn Bị Thiết Bị & Cài Đặt
1. [ ] Cài đặt file APK Release lên LDPlayer (x86_64) và thiết bị Android 14 (ARM64)
2. [ ] Nhập file ZIP của cả 2 mô hình `hachimi_onnx` và `hachimi_mt60_qt_zh_vi`

### Step 2: Thực Hiện Benchmark Hiệu Năng
3. [ ] Chạy bài test dịch 10 đoạn văn bản chuẩn, ghi nhận:
   - Thời gian load model
   - Tốc độ tokens/giây
   - Mức RAM RSS đo qua `adb shell dumpsys meminfo io.legato.kazusa:nmt_onnx`
4. [ ] Ghi lại kết quả vào `docs/reports/nmt_benchmark_report.md`

### Step 3: Kiểm Tra Văn Phong & Tên Riêng
5. [ ] Dịch một chương truyện tu tiên có nhiều từ ngữ đặc thù (trúc cơ, kết đan, nguyên anh, phi kiếm)
6. [ ] So sánh hai bản dịch để đánh giá sự khác biệt rõ rệt giữa Hachimi thường và Hachimi-QT
7. [ ] Xác nhận không có hiện tượng mất chữ hoặc lặp từ bất thường

### Step 4: Sign-off & Báo Cáo
8. [ ] Hoàn tất checklist nghiệm thu Milestone P34 Track B

## Files to Create/Modify
- `docs/reports/nmt_benchmark_report.md` — Performance benchmark report

## Test Criteria
- [ ] PASS-14.1: Cả hai mô hình NMT dịch trơn tru trong Reader trên thiết bị thật.
- [ ] PASS-14.2: Tốc độ dịch đạt > 15 tokens/giây trên CPU ARM64.
- [ ] PASS-14.3: Không xảy ra crash, rò rỉ bộ nhớ, hay xung đột cache giữa hai model.

---
End of Plan: Milestone P34
