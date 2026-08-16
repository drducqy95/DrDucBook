# Baseline audit

Ngày audit: 25/07/2026

## Bằng chứng build và test

| Hạng mục | Kết quả | Ghi chú |
|---|---|---|
| `:app:compileAppDebugKotlin` | PASS | khoảng 166 giây trên workspace hiện tại |
| Test Router/Agent/QT/ML Kit/NMT/Authoring | PASS | 72 test, 0 failure, 0 error |
| LDPlayer SM-S9280 (Android 14) | VERIFIED | `127.0.0.1:5555` connected (x86_64, PSS: ~128MB, RSS: ~205MB) |
| Room | IMPLEMENTED | version 102, schema export có version 1–102 |
| General migration test | PARTIAL | `MigrationTest` hiện chưa nạp `ALL_MIGRATIONS` |
| Release APK | CHƯA XÁC MINH | output hiện có debug APK cũ, chưa có release artifact mới trong audit |

## Đối chiếu trạng thái theo phase

| Phase | Đã có | Còn thiếu hoặc chưa chứng minh |
|---|---|---|
| 01 Router | route, dashboard, searchable model picker, model/combo-only prompt target, OAuth binding tests, route target, GGUF picker, shortcuts | OAuth login + Chat/Translation và generate thật bằng GGUF trên Nox |
| 02 Agent | loop, approved-action, 41 tools, memory, skill lifecycle, bubble panel/session, context secret suppression, proposal/run audit dashboard; 516 unit test + APK + Nox Dashboard/Router/secret pass | live mutation acceptance và ma trận Reader/Browser/Media/Writing/Ebook khi stream đang chạy |
| 03 Translation | Aho-Corasick, Jieba/POS, QT cache, ML Kit prerequisite/CJK repair, NMT importer | runtime soak, revision UI/history, mapping provenance, manga pipeline |
| 04 Sources | VBook adapter/importer/inspector, registry parser/repository, WebView legacy | registry UI, source health/worker, Browser Compose, page translation |
| 05 TTS | Valtec ONNX engine/importer/registry, settings links và voice catalog | model manager/domain gateway, cancel/progress đầy đủ, Piper runtime/import |
| 06 Media | Media3 player/resolver, audio service, seek/speed/subtitle cơ bản | persistent download, unified playback, audiobook import/offline management |
| 07 Authoring | Writing/Ebook Editor cơ bản, JSON project, export, VBook lock, drop-cap helper | block document, canvas/layer, preview/renderer, validator, pre-writing workspace |
| 08 Release | migrations tới 102, schema exports, một số migration tests, benchmark QT | fixture chain, integration/security suite, feature flags, Nox matrix, release signing/size gate |

## Blocker hiện tại

1. Không có bằng chứng Nox cho bản hiện tại.
2. System picker/import cần tái lập riêng để phân biệt DocumentsUI, main-thread contention và preload native.
3. Combo-only đã được sửa và có test; gate còn lại của Phase 01 là OAuth/GGUF thật trên thiết bị.
4. `QuickDictionarySelectionResolver` còn dùng tìm kiếm chuỗi, scaling vị trí và dịch thử candidate; chưa có provenance mapping cho màn hình không phải raw.
5. TTS importer hiện quét ZIP hai lần và chưa có test class tương ứng trong baseline test run.
6. General migration test tạo từ version 50 nhưng chưa khai báo migration thực tế trong `ALL_MIGRATIONS`.

## Quy tắc cập nhật

Mỗi thay đổi phải ghi vào `EVIDENCE-LOG.md`. Không được đánh dấu `VERIFIED_DONE` chỉ vì compile pass hoặc vì file đã tồn tại.
