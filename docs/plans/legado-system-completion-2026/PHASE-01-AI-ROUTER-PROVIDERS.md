# Phase 01 — AI Router và provider

## 1. Mục tiêu

Hoàn thiện AI Router thành nơi cấu hình duy nhất cho chatbot, dịch, sáng tác và Ebook Editor; hỗ trợ model cụ thể hoặc combo fallback trong cùng một mục chọn.

## 2. Phạm vi ảnh hưởng

`ui/ai/router`, `ui/config/ai`, `ui/config/ai/prompt`, `domain/model/AiRouterModels.kt`, `AiProfileRepository`, `AiRouterRepository`, OAuth repository/handlers, secret store, Home/My navigation và Local GGUF config.

Phụ thuộc Phase 00. Phase 02 và AI phần Phase 03 không được dùng route mới trước gate này.

## 3. Task

### C01.01 — Model picker và provider config

- **Mục tiêu:** tìm, chọn, nhập tay và đồng bộ model mà không hiển thị danh sách dài trên màn hình chính.
- **Thực hiện:** tạo picker searchable theo tên/ID không dấu; nhóm model theo provider; popup advanced cho protocol/auth/models URL/headers; secret chỉ hiển thị masked.
- **Ví dụ:** tìm `gpt-5`, nhập model ID chưa có catalog, sync model list bằng API.
- **Thông qua:** mở popup không tự lưu; search đúng; model cũ vẫn có badge missing; test connection trước save; không lộ API key.

### C01.02 — Một trường chọn model hoặc combo

- **Mục tiêu:** gom lựa chọn model/combo và cho phép chọn combo mà không nhập model riêng.
- **Thực hiện:** UI dùng `AiPromptTargetSelection.Model/Route`; khi chọn route, `routeProfileId` là authoritative, `modelProfileId` chỉ giữ compatibility với dữ liệu cũ; validation yêu cầu model hoặc route, không yêu cầu cả hai.
- **Ví dụ:** Translation chọn combo 3 target; Chatbot chọn một model; preset cũ chỉ có model vẫn chạy.
- **Thông qua:** save/load giữ đúng lựa chọn; runtime không chuyển route thành model đơn lẻ; test migration preset cũ pass.

### C01.03 — OAuth route binding

- **Mục tiêu:** OAuth đăng nhập xong dùng được cho chatbot và dịch.
- **Thực hiện:** credential/account ID được nối vào target; fetch/verify model; probe inference; refresh lỗi không bị nuốt; OAuth credential không có token không được fallback.
- **Ví dụ:** ChatGPT OAuth pass probe nhưng Translation probe fail; route giữ trạng thái failed và không báo ready.
- **Thông qua:** integration test từng trạng thái `verifying/active/relogin/failed`; Nox test đăng nhập và gọi cả Chat + Translation.

### C01.04 — Health, quota và fallback

- **Mục tiêu:** dashboard phản ánh provider/target thật.
- **Thực hiện:** aggregate success rate, latency, cooldown, credential status; circuit breaker theo target; ghi attempt không chứa secret.
- **Ví dụ:** target A timeout 2 lần bị cooldown, target B nhận request kế tiếp.
- **Thông qua:** trace thứ tự target đúng; cooldown không làm route rỗng khi còn target khỏe; UI có trạng thái degraded/error rõ.

### C01.05 — Local GGUF entry

- **Mục tiêu:** cấu hình local model an toàn và không tự chiếm default.
- **Thực hiện:** picker file, metadata/RAM/ABI, inspect-load-generate-unload probe; chặn ABI không hỗ trợ.
- **Ví dụ:** chọn GGUF hợp lệ trên x86_64; chọn file không phải GGUF; máy thiếu RAM.
- **Thông qua:** invalid/unsupported không chuyển ready; valid model load và unload không leak; test Nox x86_64 pass.

### C01.06 — Shortcut và dọn settings

- **Mục tiêu:** AI Router truy cập được từ Home, Giá sách/Khám phá và Cá nhân mà không trùng config cũ.
- **Thực hiện:** giữ route compatibility, thêm shortcut theo navigation hiện tại, ẩn mục cấu hình trùng lặp.
- **Ví dụ:** Home → Router → Back giữ stack; My → Router mở đúng dashboard.
- **Thông qua:** navigation test và Nox screenshot không có duplicate entry.

## 4. Điều kiện đóng

Router/model/combo/OAuth/local GGUF pass unit + integration; Chat và Translation dùng được combo trên Nox; secret không xuất hiện trong log/backup; compile/assemble pass.

## 5. Trạng thái thực thi 26/07/2026

| Task | Trạng thái | Bằng chứng |
|---|---|---|
| C01.01 | VERIFIED | Picker dùng chung có search/nhóm provider/missing badge; test và Nox popup pass. |
| C01.02 | AUTOMATED_DONE | Một trường model hoặc combo; route authoritative; model-only dùng direct path; test save/validation/routing pass. |
| C01.03 | AUTOMATED_DONE | OAuth binding, verification state, repair và token eligibility pass test; profile debug Nox chưa có OAuth account để chạy live login. |
| C01.04 | VERIFIED | Thứ tự combo không còn bị model request ghi đè; dashboard health/attempt hiển thị trên Nox. |
| C01.05 | DEVICE_PARTIAL | File picker GGUF, ABI/RAM/runtime metadata và unload sau probe đã có; DocumentsUI mở/đóng không crash, chưa generate bằng file GGUF thật. |
| C01.06 | VERIFIED | Shortcut Home/My có sẵn; bổ sung Giá sách/Khám phá; xóa mục Router trùng trong Cài đặt AI; route cũ vẫn redirect. |

**Kết luận:** mã nguồn và automated gate Phase 01 đã hoàn tất. Chưa gắn `VERIFIED_DONE` cho toàn phase cho đến khi chạy OAuth Chat + Translation và một lượt GGUF generate thật trên thiết bị.
