# Phase 05 — TTS và quản lý model giọng đọc

## 1. Mục tiêu

Tách hoàn toàn model TTS khỏi APK release, cung cấp catalog tải ngoài, import/test/xóa model rõ ràng và hỗ trợ các voice package thực tế trong thư mục archive.

## 2. Phạm vi ảnh hưởng

`model/tts`, `LocalTtsReadAloudService`, `ReadAloudConfigSheet`, `ReadConfigScreen`, `ExternalAssetCatalog`, catalog Google Drive, ONNX/Piper importer/runtime và local TTS preferences.

Phụ thuộc Phase 00 cho import/runtime coordinator.

## 3. Task

### C05.01 — TTS gateway và model manager

- **Mục tiêu:** model list/search/select/test/unload/delete không phụ thuộc màn Reader.
- **Thực hiện:** gateway/repository/use case; model metadata, voice list, size, checksum, engine, ABI và readiness.
- **Ví dụ:** chọn Valtec voice, test preview, unload rồi xóa; model hỏng hiển thị error.
- **Thông qua:** UI state có loading/empty/error; delete model đang dùng bị chặn hoặc chuyển fallback rõ ràng.

### C05.02 — Import bảo mật và progress

- **Mục tiêu:** import ZIP không treo và không giải nén path nguy hiểm.
- **Thực hiện:** scan một lần hoặc spool bounded, entry/size limit, strict config validation, cancel, atomic install và test staging cleanup.
- **Ví dụ:** ZIP traversal, duplicate required file, thiếu `tts_config.json`, hết dung lượng, cancel giữa ONNX lớn.
- **Thông qua:** 100 vòng unit/fault test không để staging; main thread không đọc file; thông báo lỗi có hướng xử lý.

### C05.03 — Piper voice runtime/import

- **Mục tiêu:** các voice `.onnx + .onnx.json` trong archive được dùng thật hoặc bị báo rõ là unsupported.
- **Thực hiện:** phân tích package `D:\Downloads\Archives\tts-model-20260719T061938Z-1-001`; chốt schema Piper, tokenizer, phonemizer, speaker và engine; không chỉ hiển thị link tải.
- **Ví dụ:** voice đơn, multi-speaker, thiếu config, model không tương thích tiếng Việt.
- **Thông qua:** voice được import và test runtime trên LDPlayer; không nhận nhầm Piper vào Valtec.

### C05.04 — Asset catalog và link tải

- **Mục tiêu:** Settings hiển thị Valtec và từng voice riêng, link tải ngoài APK.
- **Thực hiện:** catalog có name, engine, language, size, checksum, import instruction, Google Drive direct/folder link; không bundle model vào release.
- **Thông qua:** click từng voice mở đúng file; link hỏng có fallback folder; release asset scan không có model TTS.

### C05.05 — Service integration

- **Mục tiêu:** đọc sách dùng đúng model/voice, resume và giải phóng player/ONNX session.
- **Thông qua:** start/pause/seek/speed/stop, đổi voice, process death và audio focus pass; không giữ native handle sau stop.

## 4. Điều kiện đóng

Valtec và các engine được hỗ trợ pass import/test/delete/service trên Nox; catalog tải ngoài đúng; unit/security/instrumentation pass; release APK không chứa model.

## 5. Trạng thái thực thi 2026-07-26

| Task | Trạng thái | Bằng chứng |
|---|---|---|
| C05.01 | DONE | Có `LocalTtsModelGateway`, repository, test use case và Compose Model Manager độc lập Reader. UI hiển thị voice, size, checksum, readiness; test, đặt mặc định và xóa có fallback System TTS. |
| C05.02 | DONE | Importer đọc SAF một lần, chặn traversal/whitelist/file trùng, giới hạn 20 entry, 2 GiB/entry, 4 GiB tổng; có byte/stage progress, cancel, probe runtime trước commit, atomic rollback và fault cleanup 100 vòng. |
| C05.03 | DONE | Archive có 30 ONNX, 29 JSON và 28 cặp cùng tên hợp lệ. Importer hỗ trợ song song Valtec nhiều ONNX và Piper 2 file, tự tạo `tokens.txt`, thêm metadata ONNX, cài eSpeak-ng dùng chung và chạy sherpa-onnx. `banmai` đã import/test runtime trên LDPlayer x86_64, không có lỗi native hoặc crash. |
| C05.04 | DONE | Catalog Valtec và từng Piper voice dùng link Drive ngoài APK; release x86_64 scan không có `tts_models`, `.onnx` hoặc `.gguf`. |
| C05.05 | DONE | Valtec và Piper cùng đi qua `LocalTtsSynthesisEngine`; service xác nhận model/voice, cache WAV, pause/resume/stop, rate/interval, audio focus và release session. Piper thực tế đã khởi tạo/tổng hợp trên LDPlayer. |

Phase 05 đã đóng ngày 2026-07-26; unit test, debug build và LDPlayer smoke test đều pass.
