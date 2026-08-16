# Phase 06 - WebService, Export, Dich tu dong va background

## Muc tieu phase

Cho WebService DrDucBook chay song song app cu, co pairing va policy rieng. App Android chi quan ly master switch; nguoi dung web chi duoc bat/tat Export, Dich tu dong va thay background.

## Pham vi file chinh

- `service/WebService.kt`, `web/KtorServer.kt`, controllers/web sockets, WebService settings block
- `domain/model/WebService*.kt`, repository/config storage `[NEW]`
- `modules/web/src/**`, Vite config/build sync va Playwright tests `[NEW/MODIFY]`
- web assets trong Android, backup snapshot, strings/notifications

## Task chi tiet

### P06.T01 - WebService identity, cong va pairing

**Muc tieu:** Phan biet DrDucBook voi app cu va bao ve API v2.

**Pham vi file:** `WebService.kt`, `KtorServer.kt`, WebService Android setting block, pairing/session backend va tests.

**Thuc hien:** Default HTTP 1124/WS 1125, validate pair/free-port suggestion; `/api/v2/instance`; one-time QR/code exchange session; app chi hien master toggle/address/copy/pairing.

**Dieu kien thong qua:** Hai service chay cung luc; browser nhan dung instance; session expire/revoke; khong log pairing secret.

**Log:** Ghi ports/device/session tests va screenshot app setting.

### P06.T02 - Web policy/config API

**Muc tieu:** Luu feature gates va config revision trong app backend.

**Pham vi file:** WebServicePolicy/domain repository, Ktor v2 routes, Pinia API/store clients, backup adapter va contract tests.

**Thuc hien:** `WebServicePolicy(exportEnabled, autoTranslationEnabled)`, `ConfigRevision`; GET/PATCH v2, ETag/If-Match/409; same-origin CORS; localStorage chi giu endpoint/session/draft.

**Dieu kien thong qua:** Concurrent edit khong ghi de; unauthorized/expired session bi chan; reset/default deterministic; policy backup round-trip.

**Log:** Ghi API contract, status code tests va security evidence.

### P06.T03 - Web background storage va UI

**Muc tieu:** Cho thay mot background doc lap voi Android appearance.

**Pham vi file:** Web background models/storage/routes, Vue settings UI, CSS layers, upload validator va Playwright tests.

**Thuc hien:** Browser settings panel voi image picker/crop, cover/contain, position, dim, blur, delete/reset; upload MIME/size/hash/path validation; backend private asset store va ETag cache.

**Dieu kien thong qua:** Background ap dung Shell/Bookshelf/Reader/Source editor, content van du contrast; corrupt/oversize/SVG external bi chan; Android theme thay doi khong anh huong web.

**Log:** Ghi file limits, visual screenshots va upload security tests.

### P06.T04 - Export feature gate va pipeline

**Muc tieu:** Bat/tat toan bo lenh export tren web.

**Pham vi file:** Web export policy/use cases/routes, Vue export controls, streaming/temp-file handling va tests.

**Thuc hien:** Gate source/RSS JSON, book/chapter va ebook/TXT; an/disable UI; backend v2 tra `403 FEATURE_DISABLED`; stream filename/MIME/Content-Disposition, progress/cancel; reuse Android export use cases.

**Dieu kien thong qua:** Gate khong bi bo qua qua API v2; export lon khong OOM; cancel/xuat loi cleanup temp; legacy read endpoints van hoat dong.

**Log:** Ghi export matrix, file hashes va failure tests.

### P06.T05 - Auto-translation jobs

**Muc tieu:** Dich chuong web bang cau hinh AI cua DrDucBook ma khong lo API key.

**Pham vi file:** Translation job models/repository/routes, existing translation use-case adapter, web reader UI/store va security tests.

**Thuc hien:** POST/GET/DELETE job API; provider/model/language/glossary lay tu app; chunking, rate limit, dedupe, cancel, cache content/config hash; fallback original.

**Dieu kien thong qua:** Toggle off tra feature-disabled; missing provider/quota/timeout co loi ro va khong retry vo han; browser khong nhan credential.

**Log:** Ghi job scenarios, cache behavior va credential scan.

### P06.T06 - Legacy HTTP/WebSocket compatibility

**Muc tieu:** Giu frontend/cong cu Legado cu ket noi duoc voi dia chi DrDucBook.

**Pham vi file:** Legacy Ktor routes/controllers/WebSockets, web API client va golden contract fixtures.

**Thuc hien:** Golden tests cho routes `/getBookshelf`, content/source/RSS/replace rules va three WebSockets; payload/ReturnData khong doi; v2 security khong lam hong v1.

**Dieu kien thong qua:** Golden contract pass; web source debug/search/read flow hoat dong; port changes duoc frontend parse dung.

**Log:** Ghi route matrix va golden diff.

### P06.T07 - Web QA

**Muc tieu:** Dong gate frontend va packaged assets.

**Pham vi file:** `modules/web` type/build/E2E configs, asset sync script, packaged Android web assets va QA reports.

**Thuc hien:** Type-check, build, asset sync, Playwright desktop/mobile; pairing, toggles, background, export, translation, reload/offline/409; keyboard/reduced-motion/contrast.

**Dieu kien thong qua:** Khong mojibake; khong layout overflow; packaged web va dev build cung hanh vi; screenshots co bang chung.

**Log:** Ghi lenh, report, screenshot paths va bundle size.

## Gate dong phase

- App Android chi co master WebService controls.
- Web chi co Export, Dich tu dong va Background controls moi.
- V1 compatibility va V2 pairing/security cung pass.
