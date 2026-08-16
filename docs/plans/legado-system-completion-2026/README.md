# Kế hoạch hoàn thiện Legado 2026

Ngày tạo: 25/07/2026  
Phạm vi: tiếp nối `docs/plans/legado-system-upgrade-2026/execution/` và hợp nhất các hạng mục còn thiếu trong `docs/LEGADO_SYSTEM_UPGRADE_PLAN_VI.md`.

## Mục đích

Folder này là baseline kế tiếp để triển khai thực tế. Tài liệu cũ được giữ nguyên làm lịch sử; không dùng các trạng thái `TODO/DONE` của snapshot ngày 24/07 làm trạng thái hiện tại.

Kế hoạch có thêm **Phase 00** để xử lý các lỗi đang chặn release:

- app debug lag/crash sau khi dịch lâu;
- mở system file picker bị treo trên thiết bị Android;
- màn hình model/NMT/TTS/GGUF không tải được;
- combo fallback không được dùng thực tế;
- ML Kit còn CJK trong output;
- mapping lựa chọn từ màn hình đã dịch về raw sai.

## Baseline đã xác nhận

- Room hiện tại: version 102.
- `:app:compileAppDebugKotlin`: pass.
- Bộ test trọng yếu hiện tại: 72 test, 0 failure, 0 error.
- Thiết bị debug hiện hành từ 2026-07-26 là LDPlayer tại `127.0.0.1:5555` (`SM-S9280`, Android 14, x86_64).
- Debug APK cũ trong output khoảng 309–423 MiB tùy ABI/universal; `src/debug` chứa khoảng 225 MiB model/từ điển. Release phải kiểm tra riêng, không suy luận từ debug APK.
- Workspace không có Git metadata; trước migration hoặc thay đổi native phải tạo snapshot ngoài repo.

## Thứ tự triển khai

| Phase | File | Vai trò |
|---|---|---|
| 00 | [PHASE-00-BASELINE-STABILIZATION.md](./PHASE-00-BASELINE-STABILIZATION.md) | Khóa crash, lag, picker, import và lỗi dịch trước khi mở tính năng mới |
| 01 | [PHASE-01-AI-ROUTER-PROVIDERS.md](./PHASE-01-AI-ROUTER-PROVIDERS.md) | Hoàn thiện Router, model picker, combo và OAuth |
| 02 | [PHASE-02-AI-AGENT-CHAT-BUBBLE.md](./PHASE-02-AI-AGENT-CHAT-BUBBLE.md) | Hoàn thiện Agent, permission, tools, skill và bubble |
| 03 | [PHASE-03-TRANSLATION-RUNTIME.md](./PHASE-03-TRANSLATION-RUNTIME.md) | QT, ML Kit, NMT, revision và manga translation |
| 04 | [PHASE-04-VBOOK-SOURCE-BROWSER.md](./PHASE-04-VBOOK-SOURCE-BROWSER.md) | Registry VBook, source health và Browser |
| 05 | [PHASE-05-TTS-MODEL-MANAGEMENT.md](./PHASE-05-TTS-MODEL-MANAGEMENT.md) | Model TTS, voice catalog và service |
| 06 | [PHASE-06-MEDIA-DOWNLOAD-AUDIOBOOK.md](./PHASE-06-MEDIA-DOWNLOAD-AUDIOBOOK.md) | Player, download bền vững và audiobook |
| 07 | [PHASE-07-AUTHORING-EBOOK.md](./PHASE-07-AUTHORING-EBOOK.md) | Sáng tác, Ebook Editor, renderer và export |
| 08 | [PHASE-08-INTEGRATION-RELEASE.md](./PHASE-08-INTEGRATION-RELEASE.md) | Migration, security, LDPlayer, ký APK và release |

## Definition of Done chung

Một task chỉ được đánh dấu hoàn tất khi:

1. Contract/domain/data flow đã có, lỗi có mã phân loại và không ghi đè dữ liệu người dùng.
2. DI, navigation, localization, persistence và migration liên quan đã nối đủ.
3. Unit test logic cốt lõi pass.
4. Integration/instrumentation test pass nếu task liên quan DB, file, native, network, WebView, service hoặc Media3.
5. Compile, unit test và assemble variant liên quan pass.
6. LDPlayer có happy path, loading, empty, cancel và error path.
7. Logcat không có crash, ANR, OOM, Koin error hoặc secret plaintext.
8. `EVIDENCE-LOG.md` có lệnh, variant, thiết bị, thời điểm, artifact và kết quả.

## Quy ước trạng thái

`VERIFIED_DONE` cần code + test + LDPlayer. `AUTOMATED_DONE` cần code + test nhưng chưa có LDPlayer. `IMPLEMENTED_UNVERIFIED` chỉ có code. `PARTIAL`, `TODO`, `BLOCKED`, `SUPERSEDED` dùng cho phần còn lại.
