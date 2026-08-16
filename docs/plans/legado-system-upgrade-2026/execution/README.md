# Execution Plans — Legado System Upgrade 2026

Ngày tạo: 24/07/2026
Baseline codebase: Room version 98, phân tích 24/07/2026

## Mục đích

Thư mục này chứa kế hoạch triển khai chi tiết cho từng phase.
Mỗi file bổ sung cho spec gốc tại `../PHASE-XX-*.md` bằng cách:

- Xác định trạng thái hiện tại (đã có gì, còn thiếu gì)
- Điều chỉnh phạm vi theo thực tế codebase
- Chia task nhỏ cấp file/function
- Ghi rõ điều kiện nghiệm thu và test cụ thể

## Thứ tự thực thi (Wave)

| Wave | Phase | File | Ghi chú |
|:---:|:---:|---|---|
| 1 | 01 | [PHASE-01-TASKS.md](./PHASE-01-TASKS.md) | **Blocker** — phải xong trước P02, P03, P07 |
| 1 | 05 | [PHASE-05-TASKS.md](./PHASE-05-TASKS.md) | Độc lập, chạy song song P01 |
| 2 | 02 | [PHASE-02-TASKS.md](./PHASE-02-TASKS.md) | Phụ thuộc P01 |
| 2 | 03 | [PHASE-03-TASKS.md](./PHASE-03-TASKS.md) | Phụ thuộc P01 (ML Kit/revision) |
| 3 | 04 | [PHASE-04-TASKS.md](./PHASE-04-TASKS.md) | Phần VBook/health độc lập; Browser phụ thuộc P03 cho dịch trang |
| 3 | 06 | [PHASE-06-TASKS.md](./PHASE-06-TASKS.md) | Phụ thuộc P04 (VBook media) |
| 4 | 07 | [PHASE-07-TASKS.md](./PHASE-07-TASKS.md) | Phụ thuộc P01 (AI suggestion), P03 (revision) |
| 5 | 08 | [PHASE-08-TASKS.md](./PHASE-08-TASKS.md) | Chạy xuyên suốt, đóng cuối cùng |

## Quy ước

- `[DONE]` — Artifact đã tồn tại và đủ theo spec
- `[PARTIAL]` — Artifact tồn tại nhưng thiếu tính năng theo spec
- `[TODO]` — Chưa tạo hoặc chưa bắt đầu
- `[VERIFY]` — Cần kiểm tra thực tế trên Nox/device
- Mỗi task có ID dạng `P{phase}.T{task}` để tracking

## Gate build chung

```powershell
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain
```
