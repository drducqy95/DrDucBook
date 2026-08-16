# Phase 04 — VBook, source health và Browser

## 1. Mục tiêu

Hoàn thiện registry/import VBook, kiểm tra sức khỏe nguồn, Browser trong Khám phá, đăng nhập/captcha/cookie và dịch trang.

## 2. Phạm vi ảnh hưởng

`help/vbook`, `data/repository/vbook`, BookSource DAO/entity, WorkManager/service health, `ui/browser`, `ui/main/explore`, cookie/login bridge và page translation.

Phụ thuộc Phase 00; page translation phụ thuộc route Phase 01 và residual policy Phase 03.

## 3. Task

### C04.01 — Registry client và import UI

- **Mục tiêu:** nhập registry từ URL/file/ZIP với preview, validation và rollback.
- **Ví dụ:** registry có novel/comic/video/tts/translate; một plugin sai manifest; import trùng version.
- **Thông qua:** preview hiển thị capability; plugin invalid không cài; import cancel không để file dở; update không ghi đè custom source.

### C04.02 — Source health

- **Mục tiêu:** biết nguồn nào khỏe, chậm, cần login hoặc bị lỗi.
- **Thực hiện:** entity/DAO `BookSourceHealth`, probe bounded, WorkManager periodic, backoff và privacy-safe diagnostic; migration 102→103.
- **Ví dụ:** nguồn timeout, HTTP 403, cần cookie, parse rule lỗi.
- **Thông qua:** probe không tải nội dung lớn; UI có last check/error/latency; worker không chạy đồng thời trùng source.

### C04.03 — Browser Compose

- **Mục tiêu:** Browser người dùng đầy đủ trong Khám phá, không chỉ WebView legacy.
- **Thực hiện:** tab store, history, back/forward, reload, loading/error/empty, download handoff và capability state.
- **Thông qua:** đổi tab không mất URL; process recreation khôi phục tab; system picker/secret screen không bị bubble che.

### C04.04 — Login/captcha/cookie bridge

- **Mục tiêu:** login nguồn an toàn và dùng cookie đúng scope.
- **Ví dụ:** login success, captcha cần user, cookie hết hạn, redirect ngoài domain.
- **Thông qua:** cookie không lộ log/Agent context; redirect policy chặn private/local target; source search dùng cookie đã cấp phép.

### C04.05 — Page translation

- **Mục tiêu:** dịch vùng text trang Browser qua ML Kit hoặc AI route mà không phá DOM.
- **Thực hiện:** selectable text snapshot, preserve links/code, batch/chunk, cancel/retry và mapping page→source.
- **Thông qua:** không dịch script/style/input/password; CJK residual policy pass; đóng tab hủy job sạch.

### C04.06 — Capability/content type

- **Mục tiêu:** không suy luận audiobook/video chỉ từ registry type.
- **Thông qua:** capability probing và compatibility matrix phân biệt novel/comic/video/audio/TTS/translate; nguồn không phù hợp bị chặn trước khi tạo task.

## 4. Điều kiện đóng

Registry fixture, health worker, Browser navigation, login/cookie và page translation pass; migration 102→103 có fixture dữ liệu cũ; Nox không có WebView crash/ANR.

## 5. Implementation status (2026-07-26, LDPlayer)

- C04.01: DONE. Registry URL and JSON file import are supported. The sample URL `https://www.vbookext.me/api/registry/vbook-fd1246b6.json` was previewed on device and returned 133 plugins. Preview shows capability/type and install/update/skip state; incompatible TTS/translator entries are rejected as book sources.
- C04.02: DONE / DEVICE_PARTIAL. `BookSourceHealth` table, DAO, migration 102->103, bounded probe, unique WorkManager schedule, offline-all guard and Compose health list are implemented. LDPlayer opened the screen successfully; real daily network scan remains a background-device gate.
- C04.03: DONE / DEVICE_SMOKE_PASS. Browser is now a direct main navigation shortcut named `Trình duyệt`, in addition to the Khám phá toolbar shortcut. Browser Compose hosts a guarded WebView with address/search, tab restore, back/forward, reload/stop, home, desktop mode, external open, share/copy and download handoff.
- C04.04: PARTIAL. Normal WebView cookies are loaded from and synced back to the scoped app cookie store. Login/captcha remains user-driven. SSL errors and unsafe URL schemes are blocked. Incognito profile isolation, popup handling and a dedicated source-login result screen remain open.
- C04.05: PARTIAL / UNIT_PASS. Visible text-node extraction excludes script/style/form/password/contenteditable/code nodes; node identity/hash, chunking, CJK residual rejection, mutation debounce and original restore are implemented. Real ML Kit language-pack/page translation on device remains to be verified.
- C04.06: DONE_FOR_REGISTRY_IMPORT. Registry capability classification and incompatible-type guard are implemented; deeper runtime content probing remains open for video/audio edge cases.

Current phase gate: NOT CLOSED. Browser core and VBook/source-health foundations are usable; login/captcha completeness, incognito/history/bookmark coverage and real page-translation device proof are still required.
