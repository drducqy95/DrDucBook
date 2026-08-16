# Phase 05 - He thong kiem tra tinh trang nguon

## Muc tieu phase

Hop nhat shallow health worker va deep CheckSourceService thanh engine co stage, lich su, phan loai loi, rate limit va targeted recheck cho Book/RSS/VBook ma khong sua du lieu nguon.

## Pham vi file chinh

- `worker/BookSourceHealthWorker.kt`, `service/CheckSourceService.kt`, `model/CheckSource.kt`
- `domain/model/BookSourceHealthModels.kt`, `ProbeBookSourceUseCase.kt`, gateways
- `data/entities/BookSourceHealth.kt`, DAO/repository, Room schema
- `domain/sourcehealth/**`, `data/repository/sourcehealth/**` `[NEW]`
- `ui/book/source/health/**`, source/RSS manage screens, Browser integration
- VBook executor/inspector adapters va test fixtures

## Task chi tiet

### P05.T01 - Source health contracts va schema

**Muc tieu:** Luu moi check run va ket qua tung stage, khong ghi loi vao source group/comment.

**Pham vi file:** Source health domain models, Room entities/DAO/migration, repository queries va schema tests.

**Thuc hien:** Tao `SourceCheckRun`, `SourceCheckStageResult`, profile/status enums, DAO transactional insert/finish; map health record cu neu can trong DrDucBook upgrade.

**Dieu kien thong qua:** Run dang chay/crash/cancel co state hop le; source entity bat bien; query latest/history/filter nhanh va co index.

**Log:** Ghi schema version, entity/index va migration tests.

### P05.T02 - Probe adapters cho Book/RSS/VBook

**Muc tieu:** Moi loai nguon co pipeline dung kha nang cua no.

**Pham vi file:** Book/RSS/VBook probe gateways/repositories, VBook executor adapters va deterministic fixtures.

**Thuc hien:** Book stages reachability/search/explore/detail/toc/content/media; RSS feed/list/article/content; VBook manifest/scripts/home/search/detail/toc/content/track; dung fixture deterministic.

**Dieu kien thong qua:** Adapter tra evidence co stage/latency/status, khong nem raw secret/HTML; source thieu optional capability duoc `SKIPPED`, khong `FAILED`.

**Log:** Ghi capability matrix va fixture coverage.

### P05.T03 - Engine Quick/Standard/Full va classification

**Muc tieu:** Mot engine chung cho scheduled, manual va post-login checks.

**Pham vi file:** SourceCheckEngine/use cases, profile/classification/redaction logic va unit tests.

**Thuc hien:** Quick low-cost; Standard den item/detail/toc; Full den content/media; phan loai healthy/degraded/auth/captcha/rate-limit/network/TLS/rule/empty/media/offline/stale; redact diagnostic.

**Dieu kien thong qua:** Timeout/cancellation dung; offline khong tang failure; 401/403/captcha/DNS/TLS/parse/content-empty co status chinh xac; aggregate deterministic.

**Log:** Ghi classification table va unit test report.

### P05.T04 - Worker, concurrency va foreground run

**Muc tieu:** Chay an toan, khong spam website va co the dieu khien.

**Pham vi file:** WorkManager workers, foreground check service, scheduler/config, notifications va restart/device tests.

**Thuc hien:** WorkManager Quick 24h; targeted Standard unique work; Full foreground co pause/resume/cancel; group concurrency theo registrable domain; jitter/backoff va progress persistence.

**Dieu kien thong qua:** Khong duplicate run; process restart resume/mark interrupted dung; rate limits duoc ton trong; notification actions hoat dong.

**Log:** Ghi scheduling IDs, concurrency measurements va device test.

### P05.T05 - Dashboard va tich hop UI

**Muc tieu:** Dua health thanh thong tin co the hanh dong.

**Pham vi:** SourceHealth Contract/ViewModel/Screen, Book/RSS source list, Browser source indicator, Workspace badges.

**Thuc hien:** Summary/filter/search/history/stage details/latency trend; action Check/Login Browser/Edit/Disable; khong auto disable; Agent chi doc/yeu cau check qua permission.

**Dieu kien thong qua:** Loading/empty/offline/error/progress states dung; filter totals khop DB; targeted login flow noi day du.

**Log:** Screenshot states va UI/ViewModel test reports.

### P05.T06 - History retention va cleanup

**Muc tieu:** Kiem soat kich thuoc DB va quyen rieng tu.

**Pham vi file:** Source health DAO cleanup queries, retention worker/config, delete-source hooks va storage tests.

**Thuc hien:** Giu latest summary va gioi han run theo age/count; xoa theo source delete; khong luu HTML/noi dung sach/cookie; compact diagnostics.

**Dieu kien thong qua:** Cleanup idempotent, khong xoa active/latest run; DB size test dat nguong da ghi trong ADR.

**Log:** Ghi retention defaults, before/after DB metrics va tests.

### P05.T07 - Source health test suite

**Muc tieu:** Xac minh engine tren tat ca profile va loai nguon.

**Pham vi file:** Source health unit/integration/UI fixtures va reports cho Book/RSS/VBook.

**Dieu kien thong qua:** Unit/integration tests cover success, partial, offline, auth, captcha, rate limit, DNS/TLS, broken rule, empty, media, cancel/resume, backoff, retention va source immutability.

**Log:** Matrix theo source type/profile/status va report paths.

## Gate dong phase

- Scheduled Quick, targeted Standard va manual Full hoat dong.
- Khong task nao ghi group/comment/rule/enabled vao source.
- Browser/source lists/dashboard doc cung health repository.
