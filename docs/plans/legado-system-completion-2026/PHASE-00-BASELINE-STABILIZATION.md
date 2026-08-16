# Phase 00 — Baseline và ổn định runtime

## 1. Mục tiêu

Đưa bản debug hiện tại về trạng thái có thể mở màn hình cài đặt, chọn file, tải model và dịch lâu trên Nox mà không treo, crash, OOM hoặc mất kết quả.

## 2. Phạm vi ảnh hưởng

`ui/config/translation`, `ui/config/readConfig`, `ui/config/ai`, `ui/config/ai/prompt`, `utils/ActivityResultContracts.kt`, các importer NMT/TTS/GGUF/từ điển, `QuickTranslationRepository`, `TranslateChapterUseCase`, `AiRouterRepository`, `QuickDictionarySelectionResolver` và log/diagnostic.

Không thay đổi schema hoặc mở feature mới trước khi phase này pass.

## 3. Task

### C00.01 — Tái lập Nox và lập baseline

- **Mục tiêu:** có APK/hash/device/log chính xác cho lỗi người dùng báo.
- **Thực hiện:** cài x86_64 debug mới nhất; đo startup, mở Translation/AI/TTS, mở picker, chạy 30 phút dịch; lấy logcat, memory và ANR trace.
- **Ví dụ:** mở màn Translation 10 lần liên tiếp trong khi QT warm-up; mở picker ngay sau khi vào màn hình.
- **Thông qua:** có script/scenario tái lập hoặc kết luận rõ lỗi không tái lập; không ghi nhận “đã pass” khi Nox offline.

### C00.02 — Sửa đường mở system file picker

- **Mục tiêu:** picker xuất hiện độc lập với preload model và không bị coroutine màn hình giữ tài nguyên.
- **Thực hiện:** launcher chỉ tạo Intent; mọi scan URI, query metadata và import chạy sau callback trên IO; kiểm tra `ACTION_OPEN_DOCUMENT/GET_CONTENT`, MIME và `EXTRA_LOCAL_ONLY`; hủy job cũ khi mở picker mới.
- **Ví dụ:** bấm Import NMT rồi hủy; bấm lại 10 lần; chọn ZIP từ Downloads và từ provider khác.
- **Thông qua:** cold picker ≤4 giây, warm ≤2 giây, 10 vòng mở/hủy không ANR; main thread không đọc ZIP hoặc khởi tạo native model.

### C00.03 — Điều phối runtime nặng

- **Mục tiêu:** QT/Jieba, NMT ONNX, TTS ONNX và GGUF không tranh chấp bộ nhớ/CPU.
- **Thực hiện:** lazy load, mutex theo runtime, unload idle session, giới hạn queue và hiển thị trạng thái `loading/ready/busy/error`; không warm-up trong composition nếu chưa có user action.
- **Ví dụ:** đang đọc TTS thì mở màn hình Translation; đang dịch thì mở AI model picker.
- **Thông qua:** không OOM/crash; mọi job bị hủy giải phóng handle; màn hình settings vẫn render khi runtime đang bận.

### C00.04 — Import atomic và có thể hủy

- **Mục tiêu:** NMT/TTS/GGUF/từ điển không làm treo UI và không để staging hỏng.
- **Thực hiện:** chung hóa progress/cancel; giới hạn file/entry/dung lượng; checksum; `.importing` rồi rename; rollback khi lỗi encoding, thiếu file, hết dung lượng hoặc cancel.
- **Ví dụ:** hủy khi ZIP đang giải nén 50%; import ZIP duplicate; import file sai MIME nhưng đúng nội dung.
- **Thông qua:** không file staging mồ côi sau restart; import lại được; UI có progress/error cụ thể.

### C00.05 — Giữ kết quả khi dịch lỗi giữa chừng

- **Mục tiêu:** lỗi provider không xóa toàn bộ kết quả đã dịch.
- **Thực hiện:** lưu chunk checkpoint; phân biệt `completed/failed/pending`; retry từ chunk lỗi; giữ output partial và lỗi có mã.
- **Ví dụ:** chunk 1–5 thành công, chunk 6 trả empty/safety block/timeout; fallback thành công ở chunk 6.
- **Thông qua:** mở lại chương vẫn thấy chunk 1–5; retry không dịch lại chunk đã chốt; log có provider/model/attempt nhưng không có secret.

### C00.06 — Combo fallback thực sự được dùng

- **Mục tiêu:** mọi task AI dùng route/combo đã chọn, không quay về model đơn lẻ.
- **Thực hiện:** route được truyền xuyên suốt prompt → use case → `AiGenerateRequest`; empty output cũng là failure; target tiếp theo được thử theo thứ tự; không trộn stream sau khi đã phát content.
- **Ví dụ:** target A empty, target B trả nội dung; target A timeout, target B rate-limit, target C thành công.
- **Thông qua:** test trace chứng minh đúng route/target; combo-only không bị lỗi “model required”.

### C00.07 — Mapping lựa chọn về raw

- **Mục tiêu:** bôi đen ở Reader/Explore/translated UI không tự ghi nhầm raw.
- **Thực hiện:** tách `displayRange` khỏi `rawRange`; QT/Hán-Việt trả segment provenance; AI/ML/NMT trả confidence và alternatives; confidence thấp phải xác nhận.
- **Ví dụ:** cùng một tên xuất hiện 3 lần; output dịch đổi độ dài; text có HTML, newline và khoảng trắng lặp.
- **Thông qua:** mapping exact của QT/Hán-Việt đạt 100% test; mapping không chắc không tự tạo dictionary entry.

### C00.08 — Gate đóng phase

- **Test:** focused unit, instrumentation import, translation soak, memory/ANR và Nox smoke.
- **Thông qua:** compile + unit + assemble pass; không crash/OOM/ANR; picker và 4 loại import pass cả cancel/error; QT warm ≤1 giây cho benchmark 3.520 ký tự.

