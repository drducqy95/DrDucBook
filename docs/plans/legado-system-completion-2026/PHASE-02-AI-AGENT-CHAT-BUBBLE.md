# Phase 02 — AI Agent và Chat Bubble

## 0. Trạng thái thực thi 26/07/2026

| Task | Trạng thái | Bằng chứng chính |
|---|---|---|
| C02.01 Agent contract và loop | AUTOMATED_DONE | Max 12 bước, dừng khi lặp tool + args 3 lần, cancel/max-step/final có test |
| C02.02 Permission broker | AUTOMATED_DONE | Token one-time, hash/expiry/conversation/batch bất biến; approved action chỉ chốt sau khi tool chạy xong |
| C02.03 Tool registry | AUTOMATED_DONE | 41 tool; internet, nguồn/sách/chương, bookmark, memory, dictionary, download, skill/plugin và Writing/Ebook project |
| C02.04 Bubble và context | AUTOMATED_DONE / DEVICE_PARTIAL | Process store, panel transcript/input/stop, badge, IME/cutout; Nox xác nhận bubble/panel và ẩn editor secret, còn thiếu ma trận Reader/Browser/Media/Writing |
| C02.05 Skill/plugin lifecycle | AUTOMATED_DONE | Draft mặc định tắt, version/activate/rollback, allow-list, secret/null/path/URL dependency validation |
| C02.06 Dashboard/audit | VERIFIED_DONE | Loading/error/empty, proposal audit, run trace, model/provider, confirm skill mutation; Nox hiển thị đúng 41 tool và lịch sử run |

### Quyết định triển khai

- `ChatBubbleCoordinator` dùng `Application.ActivityLifecycleCallbacks` làm hook duy nhất cho mọi Activity; không chèn hook lặp vào ba base Activity.
- Proposal có trạng thái `FAILED` và `PARTIAL`; batch chạy dở không bị ghi nhầm thành `APPROVED`.
- Trace, lỗi, final preview và proposal JSON được làm mờ secret tại `AiAgentRepository`, là lớp chặn cuối trước Room.
- Gate hiện tại: `AUTOMATED_DONE / DEVICE_PARTIAL`; full unit suite và APK đã pass, còn ma trận đa Activity và mutation live cần lượt device acceptance riêng.

## 1. Mục tiêu

Biến chatbot thành Agent có loop, tools đọc/ghi, permission broker, memory, skill/plugin và bubble toàn app nhưng không cần system overlay.

## 2. Phạm vi ảnh hưởng

`domain/agent`, `RunAiAgentUseCase`, `AiChatGenerationUseCase`, `AiToolRepository`, Agent/Memory/Skill entities và DAO, `ui/ai/agent`, `ui/ai/chat`, `ui/ai/bubble`, Activity lifecycle/context registry, VBook/book/dictionary gateways.

Phụ thuộc Phase 01; mutation tool không được mở nếu permission broker chưa pass.

## 3. Task

### C02.01 — Agent contract và loop

- **Mục tiêu:** loop có state, trace, cancel, max step và loop detection.
- **Thực hiện:** khóa `AgentStep`, `ToolCall`, `ToolResult`, `ProposedAction`, `PermissionToken`; tối đa 12 tool steps; 3 lần tool + args giống nhau thì dừng.
- **Ví dụ:** Agent tìm sách → trả card → chờ confirm → thêm sách → báo kết quả.
- **Thông qua:** final, cancel, max-step và loop-detected đều có test; không orphan coroutine.

### C02.02 — Permission broker bắt buộc

- **Mục tiêu:** repository tự chặn mutation kể cả khi UI bị bypass.
- **Thực hiện:** token one-time chứa proposal hash, args hash, conversation ID, expiry; batch target bất biến; replay bị từ chối.
- **Ví dụ:** user xác nhận thêm sách A nhưng request đổi sang B phải bị reject.
- **Thông qua:** thiếu/sai/hết hạn/replay/batch-changed đều không đổi dữ liệu; audit trace có before/after.

### C02.03 — Tool registry và dữ liệu

- **Mục tiêu:** Agent đọc/sửa dữ liệu thực tế.
- **Thực hiện:** giữ read tools internet/book source/book/chapter/cache/dictionary/memory; mutation qua proposal cho book, dictionary, bookmark, project, download, plugin.
- **Ví dụ:** `search_book_sources` → `search_online_books` → `add_book_to_bookshelf` sau confirm.
- **Thông qua:** read tool trả dữ liệu đúng; mutation chưa confirm không ghi DB; integration test end-to-end pass.

### C02.04 — Bubble và context bridge

- **Mục tiêu:** session không mất khi đổi màn hình/activity.
- **Thực hiện:** process-scoped coordinator, panel phone/tablet, stop/cancel, unread/approval/error badge, context DTO read-only; ẩn ở secret screen/system picker.
- **Ví dụ:** bubble từ Reader sang Browser vẫn giữ conversation; mở API key popup thì bubble ẩn.
- **Thông qua:** Home/Reader/Browser/Media/Writing đều hiển thị; drag/snap/IME/cutout đúng; không capture password/WebView object.

### C02.05 — Skill/plugin lifecycle

- **Mục tiêu:** skill có manifest/SKILL.md được validate, version, rollback và không tự activate.
- **Thực hiện:** giới hạn kích thước, secret, tool allow-list, executable content và dependency; mutation install cần confirm.
- **Ví dụ:** skill chứa API key hoặc null byte bị từ chối; version lỗi rollback về version active.
- **Thông qua:** validator/security test pass; draft disabled mặc định; migration skill giữ dữ liệu.

### C02.06 — Dashboard và audit UI

- **Mục tiêu:** người dùng xem được run, trace, proposal, memory và skill status.
- **Thông qua:** empty/loading/error/cancel đều hiển thị; audit không có plaintext secret; Agent không làm lag dashboard.

## 4. Điều kiện đóng

Agent integration và security suite pass; Nox chuyển Activity không mất session; mọi mutation có diff/confirm/audit; bubble bị loại trừ đúng các màn hình nhạy cảm.
