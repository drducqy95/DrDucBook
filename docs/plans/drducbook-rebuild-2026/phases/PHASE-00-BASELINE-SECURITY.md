# Phase 00 - Baseline, hop dong va bao mat

## Muc tieu phase

Tao moc do co the lap lai truoc khi doi package/architecture; khoa compatibility corpus, public contracts va secret policy. Phase nay khong trien khai tinh nang nguoi dung.

## Pham vi file chinh

- `app/build.gradle.kts`, `gradle/libs.versions.toml`, `app/src/main/AndroidManifest.xml` `[READ/REPORT]`
- `app/src/main/java/io/legado/app/**` va `app/src/test/**` `[READ/TEST ONLY]`
- `modules/web/**` `[READ/BUILD ONLY]`
- `docs/plans/drducbook-rebuild-2026/**` `[MODIFY]`
- `app/src/test/resources/compat/**` `[NEW, neu fixture co quyen phan phoi]`

## Task chi tiet

### P00.T01 - Kiem ke code, docs va baseline

**Muc tieu:** Chot repo truth cho package, Room, navigation, Agent tools, media, source runtime, WebService, backup va asset catalog.

**Pham vi file:** Android/Web manifests, Gradle configs, source/test inventories va bao cao baseline trong folder plan; chi doc code san pham.

**Thuc hien:** Lap inventory co duong dan va trang thai `DONE/PARTIAL/MISSING`; doi chieu docs cu voi code/test; ghi ro dirty/generated output khong thuoc plan.

**Dieu kien thong qua:** Bao cao inventory khong con claim mau thuan; moi subsystem co owner boundary va test hien co; khong sua code san pham.

**Log:** Entry `P00.T01 DONE` phai lien ket inventory va liet ke lenh tim/kiem tra da dung.

### P00.T02 - Xu ly secret va security baseline

**Muc tieu:** Bao dam khong secret nao duoc dua vao APK/repository/log truoc khi tich hop cloud.

**Pham vi:** `.gitignore`, build config secret loading, docs deploy backend, script scan secret `[NEW/MODIFY]`; khong ghi token thuc.

**Thuc hien:** Xac nhan token HF da thu hoi; dinh nghia Supabase Edge Function secrets, publishable/secret key boundary, JWT/RLS policy, redaction rules cho token/cookie/header va backup exclusions.

**Dieu kien thong qua:** Secret scan pass; APK/source/test fixture khong chua credential; co quy trinh rotate va audit.

**Log:** Ghi ten secret logical va ket qua scan, tuyet doi khong ghi secret value.

### P00.T03 - Khoa compatibility corpus Legado/VBook

**Muc tieu:** Bien "tuong thich" thanh bo test lap lai duoc.

**Pham vi:** `app/src/test/resources/compat/**`, source parser/VBook tests, Web API contract fixtures `[NEW/MODIFY]`.

**Thuc hien:** Chon fixture cong khai cho Book/RSS/TTS JSON+JS, VBook text/comic/audio/video/TTS/translator, deep link va ReaderProvider payload; luu nguon/license/checksum.

**Dieu kien thong qua:** Fixture khong chua credential/noi dung vi pham; import va baseline execution pass; hash va provenance duoc ghi.

**Log:** Ghi fixture ID, nguon, checksum, test bao phu; khong dan noi dung dai vao log.

### P00.T04 - Khoa ADR va public contracts

**Muc tieu:** Khoa cac quyet dinh kho dao nguoc truoc implementation.

**Pham vi:** `docs/architecture/**` hoac `docs/plans/drducbook-rebuild-2026/adr/**` `[NEW]`.

**Thuc hien:** ADR cho app coexistence, legacy facade, SourceKey, CookieVault, SourceCheckEngine, AppearanceProfile, WebService policy, Agent sandbox, HF proxy va snapshot conflict.

**Dieu kien thong qua:** Moi ADR co context, decision, alternatives, consequence va rollback; khong con authority/cong/schema mo ho.

**Log:** Lien ket tung ADR va ghi quyet dinh nao thay the plan/docs cu.

### P00.T05 - Chay baseline build/test/report

**Muc tieu:** Tao bang chung truoc thay doi de phan biet regression moi va loi san co.

**Pham vi:** build/test output; khong formatter/code rewrite.

**Thuc hien:** Chay Kotlin compile, focused unit tests, full unit test kha thi, debug assemble, web type-check/build; ghi test san co fail rieng.

**Dieu kien thong qua:** Lenh, exit code, thoi gian va report path day du; 42 focused tests da biet duoc xac minh lai hoac log giai thich chenh lech.

**Log:** Entry phai co bang ket qua tung gate va baseline blockers.

## Gate dong phase

- P00.T01-P00.T05 deu `DONE`.
- Khong secret lo; compatibility corpus va ADR co the duoc task sau tham chieu.
- `TASK-MATRIX.md` va `PLAN-LOG.md` khop report.
