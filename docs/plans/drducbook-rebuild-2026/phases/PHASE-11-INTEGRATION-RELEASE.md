# Phase 11 - Tich hop, kiem dinh va phat hanh

## Muc tieu phase

Chung minh toan bo he thong hoat dong cung nhau tren debug/noR8/release, app cu va DrDucBook cung ton tai, compatibility corpus pass va co ke hoach rollout/rollback van hanh duoc.

## Pham vi file chinh

- Toan bo `app/**`, `modules/**`, backend va CI/release configs `[VERIFY/FIX WITH LOG]`
- integration/instrumentation/E2E/performance/security tests
- `docs/**`, privacy/legal/support/update metadata, release notes
- `docs/plans/drducbook-rebuild-2026/TASK-MATRIX.md`, `PLAN-LOG.md`, final report

## Task chi tiet

### P11.T01 - Full build/test matrix

**Muc tieu:** Chay mot ma tran lap lai duoc cho tat ca artifact phat hanh.

**Pham vi file:** Gradle/CI configs, Android tests, web tests, backend tests va report scripts.

**Thuc hien:** Compile, unit, lint, debug/noR8/release, ABI splits; web type-check/build/Playwright; backend unit/integration; capture versions/checksums.

**Dieu kien thong qua:** Tat ca mandatory gates pass; flaky/skip co owner va quyet dinh; artifact checksums va report paths duoc luu.

**Log:** Bang lenh/exit code/duration/report va artifact hash.

### P11.T02 - Side-by-side end-to-end regression

**Muc tieu:** Kiem tra lifecycle thuc te khi app cu dang cai.

**Pham vi file:** Instrumentation/manual test scripts, manifest assertions va evidence assets.

**Thuc hien:** Install/update/uninstall; launch/deep-link chooser; Provider/cookie/WebView/data/notification/tile/Supabase session/Google Drive authorization/backup; hai WebService ports; device reboot.

**Dieu kien thong qua:** Khong install conflict, data leak, authority collision hoac service bind sai; moi app tiep tuc chay sau uninstall app kia.

**Log:** Package/version/device matrix, screenshots va logcat scan.

### P11.T03 - Legado/VBook compatibility regression

**Muc tieu:** Chung minh package refactor khong pha ext/plugin/API cong khai.

**Pham vi file:** Compatibility fixtures/tests, legacy facade/R8 rules, ReaderProvider va Web API contracts.

**Thuc hien:** Import/execute Book/RSS/TTS/VBook all types; JS globals/cookies; source login; deep links; ReaderProvider; web routes/sockets; release minified.

**Dieu kien thong qua:** 100% corpus duoc chot pass hoac co deferred decision; JSON round-trip khong mat field; ABI facade con trong APK.

**Log:** Compatibility matrix va golden diffs.

### P11.T04 - Performance, accessibility va security audit

**Muc tieu:** Loai regression phi chuc nang truoc rollout.

**Pham vi file:** Benchmark/profile configs, accessibility tests, security scanners va cac fix phat sinh co log.

**Thuc hien:** Startup/memory/battery/DB/Browser/health/media/download; TalkBack/contrast/touch targets; secret/cookie/path traversal/CORS/WebView/R8/backup audit.

**Dieu kien thong qua:** Dat nguong ADR/release; khong critical/high unresolved; medium co owner/risk acceptance; khong ANR/OOM trong smoke matrix.

**Log:** Metrics before/after, findings va closure links.

### P11.T05 - Domain, docs, privacy va release metadata

**Muc tieu:** Hoan tat moi external input va tai lieu nguoi dung/van hanh.

**Pham vi file:** `AppDomains`, Supabase redirect URLs/site URL, Google Drive OAuth/consent metadata, `assetlinks.json`, privacy/legal/support/update docs, README, release notes, deploy runbooks.

**Thuc hien:** Gan domain that; verify HTTPS/app links/Supabase OAuth/Drive consent; mo ta account/Storage/Postgres/Drive/cookie/AI/HF/data deletion; huong dan WebService pairing/ports va side-by-side behavior.

**Dieu kien thong qua:** Link checker/app-link verification pass; privacy noi dung khop data flow; khong placeholder domain/icon/secret.

**Log:** Domain verification, docs links va approvals.

### P11.T06 - Staged rollout va rollback rehearsal

**Muc tieu:** Phat hanh co the dung/rollback ma khong mat data DrDucBook.

**Pham vi file:** CI/CD, versioning/signing configs, backend deploy/rollback scripts, feature flags va operational docs.

**Thuc hien:** Internal -> alpha -> beta -> production gates; backend backward compatibility; manifest rollback; kill switches cho HF/translation/health scheduling; backup before destructive restore.

**Dieu kien thong qua:** Rehearsal rollback app/backend/manifest pass; signing artifact verified; monitoring/alert owner ro; rollout gate co approval.

**Log:** Deployment revisions, rehearsal result, rollback time va approver.

### P11.T08 - AI translation pipeline rewrite validation

**Muc tieu:** Sau hotfix P06.T05, doc va doi chieu engine tham khao `D:\Dev\Projects\legado-qt-main\legado-qt-main\Translator Engine\Translator Engine`, loai bo duong runtime con dung pipeline dich AI cu va khoa pipeline moi bang contract/test/device evidence.

**Pham vi file:** `app/src/main/java/io/legado/app/domain/model/*Translation*`, `app/src/main/java/io/legado/app/domain/usecase/*Translate*`, prompt catalog/AI settings, WebService auto-translation job wiring, translation cache/migration neu co, tests va report P11.

**Thuc hien:** Kiem ke moi duong dich cu con sot (`[result]`, `[dictionary]`, paragraph marker, regex parser output cu, fallback ghi de output); doc mapping tu Translator Engine reference; chot contract moi gom context pack, raw segment ids, QT draft, locked dictionary, schema JSON, QC/repair; migrate runtime app va web job sang contract moi; danh dau/force retranslate cache cu khi can; xoa dead fallback sau khi regression pass.

**Dieu kien thong qua:** Khong con runtime production dung prompt/output contract cu; tests bao phu dung/sai/thieu/trung segment id, CJK leak, JSON repair, provider fallback, cache cu va web job; dich thu it nhat mot chuong that tren LDPlayer khong mat doan, khong chen tag/marker va co log token/model an toan.

**Log:** Reference files da doc, mapping cu->moi, path pipeline cu da xoa/giu fallback, test/device evidence, rui ro provider/cache con lai.

### P11.T07 - Final report va dong plan log

**Muc tieu:** Chot repo truth, khong dong phase bang danh dau hang loat.

**Pham vi file:** `TASK-MATRIX.md`, `PLAN-LOG.md`, final completion report va known-issues register.

**Thuc hien:** Doi chieu 76 task voi commits/files/tests/log; liet ke deferred/known risks; ghi artifact version/hash; chot operational handoff.

**Dieu kien thong qua:** Moi DONE task co log/evidence; matrix khop code; khong task IN_PROGRESS/BLOCKED bi an; final report duoc review.

**Log:** Entry `P11.T07 DONE` va `PLAN COMPLETE` voi token/build/test usage neu he thong yeu cau.

## Gate dong phase

- P11.T01-P11.T08 deu `DONE`.
- Khong critical/high security issue, release blocker hoac placeholder external input.
- Final report, task matrix, plan log va artifact hashes khop nhau.
