# Nhat ky thuc thi plan DrDucBook

File nay la append-only trong qua trinh trien khai. Khong xoa entry cu; neu can sua, them entry `CORRECTION` tham chieu task va entry truoc.

## Quy tac

- Moi task co it nhat mot entry `STARTED` va mot entry ket thuc `DONE`, `BLOCKED` hoac `DEFERRED`.
- Entry `DONE` phai co lenh test va ket qua cu the; "compile thanh cong" khong thay the test hanh vi.
- Khong ghi token, cookie, OAuth code, noi dung sach/nguon nhay cam hoac duong dan private cua nguoi dung.
- Bang chung anh/log lon duoc luu ngoai file nay va lien ket bang duong dan repository tuong doi.

## Mau entry

```markdown
## YYYY-MM-DD HH:mm - PXX.TYY - STATUS

- Nguoi/agent thuc hien:
- Muc tieu:
- Thoi gian bat dau/ket thuc:
- File thay doi:
- Tom tat trien khai:
- Lenh kiem tra:
- Ket qua:
- Bang chung:
- Rui ro/cong viec con lai:
- Task tiep theo:
```

## Khoi tao

## 2026-07-29 - PLAN - CREATED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Tao bo plan theo phase cho tai cau truc DrDucBook.
- File thay doi: `docs/plans/drducbook-rebuild-2026/**`
- Tom tat: Khoi tao muc luc, task matrix, phase specs va quy tac log bat buoc.
- Ket qua: Plan duoc tao; chua co task trien khai san pham nao bat dau.
- Task tiep theo: P00.T01.

## 2026-07-29 08:20 - P00.T01 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Kiem ke code, tai lieu va baseline hien tai truoc khi tai cau truc.
- Thoi gian bat dau/ket thuc: 2026-07-29 08:20 / dang thuc hien
- File thay doi: `docs/plans/drducbook-rebuild-2026/TASK-MATRIX.md`, `PLAN-LOG.md`, `reports/P00-T01-INVENTORY.md`
- Tom tat trien khai: Bat dau doi chieu Gradle, manifest, Room, navigation, Browser/source/cookie, health, Agent, media, WebService, backup va asset catalogs.
- Lenh kiem tra: Se duoc ghi khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P00-T01-INVENTORY.md`
- Rui ro/cong viec con lai: Repo khong co metadata `.git`; can tach ro repo truth va claim trong docs cu.
- Task tiep theo: P00.T02 sau khi inventory duoc khoa.

## 2026-07-29 08:34 - P00.T01 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Khoa repo truth cho toan bo subsystem truoc tai cau truc.
- Thoi gian bat dau/ket thuc: 2026-07-29 08:20 / 2026-07-29 08:34
- File thay doi: `reports/P00-T01-INVENTORY.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`
- Tom tat trien khai: Da doi chieu build/package, Room, navigation, Browser/cookie/source health, Agent, Authoring/Ebook, media, WebService, backup, Firebase va asset catalogs.
- Lenh kiem tra: `rg` tren Gradle/manifest/source/docs; PowerShell dem source/test/entity/DAO/tool/web route va kiem tra catalog URLs.
- Ket qua: Inventory khoa Room 105/53 entities/39 DAO, 9 top-level destinations, 41 Agent tools, 27 HTTP + 3 WebSocket routes va 9 dong Google Drive URL.
- Bang chung: `reports/P00-T01-INVENTORY.md`
- Rui ro/cong viec con lai: Workspace khong co `.git`; docs cu co claim DONE/DEVICE_PARTIAL khong dong nhat va khong duoc dung thay baseline test P00.T05.
- Task tiep theo: P00.T02.

## 2026-07-29 08:34 - P00.T02 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Khoa secret handling, redaction, backup exclusions va tao secret scan lap lai duoc.
- Thoi gian bat dau/ket thuc: 2026-07-29 08:34 / dang thuc hien
- File thay doi: Security baseline report, secret scan script, `.gitignore` neu can, task matrix va plan log.
- Tom tat trien khai: Bat dau kiem tra secret patterns, build config va cac diem co the lo cookie/token.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P00-T02-SECURITY-BASELINE.md`
- Rui ro/cong viec con lai: Token trong conversation phai duoc xem la da lo va thu hoi ben ngoai repo.
- Task tiep theo: P00.T03 sau khi security gate pass.

## 2026-07-29 08:48 - P00.T02 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Khoa secret handling, redaction, backup exclusions va secret scan.
- Thoi gian bat dau/ket thuc: 2026-07-29 08:34 / 2026-07-29 08:48
- File thay doi: `.gitignore`, `scripts/security/scan-secrets.ps1`, `CookieManager.kt`, `CookieManagerSecurityTest.kt`, `reports/P00-T02-SECURITY-BASELINE.md`.
- Tom tat trien khai: Them ignore rules, scanner khong in matched values, policy secret/rotation va sua nhanh AppLog lam lo cookie.
- Lenh kiem tra: `& ./scripts/security/scan-secrets.ps1`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.http.CookieManagerSecurityTest" --no-daemon --console=plain`.
- Ket qua: Secret scan PASS (5 allow-listed, 0 unapproved); CookieManagerSecurityTest PASS; BUILD SUCCESSFUL.
- Bang chung: `reports/P00-T02-SECURITY-BASELINE.md`, Gradle XML report trong `app/build/test-results/testAppDebugUnitTest/`.
- Rui ro/cong viec con lai: Viec revoke token HF cu la external gate P10/P11; CookieVault encryption thuc hien tai P04.
- Task tiep theo: P00.T03.

## 2026-07-29 08:48 - P00.T03 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Tao compatibility corpus cong khai/synthetic co provenance va automated tests cho Legado/VBook/API contracts.
- Thoi gian bat dau/ket thuc: 2026-07-29 08:48 / dang thuc hien
- File thay doi: `app/src/test/resources/compat/**`, compatibility tests, corpus report va task matrix.
- Tom tat trien khai: Bat dau khoa fixtures Book/RSS/TTS, VBook capabilities, deep links, ReaderProvider va Web API routes.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P00-T03-COMPATIBILITY-CORPUS.md`
- Rui ro/cong viec con lai: Fixture phai synthetic/public, khong chua credential hay noi dung co ban quyen.
- Task tiep theo: P00.T04 sau khi corpus tests pass.

## 2026-07-29 08:55 - PLAN - ARCHITECTURE_CHANGED_TO_SUPABASE

- Nguoi/agent thuc hien: Codex theo chi dao nguoi dung.
- Muc tieu: Chuyen toan bo target cloud tu Firebase/Cloud Run/Google Drive sang Supabase.
- Thoi gian bat dau/ket thuc: 2026-07-29 08:55 / 2026-07-29 08:55
- File thay doi: README, task matrix, Phase 00/01/07/10/11, security/inventory reports va ADR se tao o P00.T04.
- Tom tat trien khai: Supabase Auth thay Firebase Auth; Edge Functions thay Cloud Run; Postgres+RLS va private Storage thay Drive snapshot; Google van la social login provider thong qua Supabase Auth.
- Lenh kiem tra: `rg -n -i "firebase|cloud run|google drive|appDataFolder|app check" docs/plans/drducbook-rebuild-2026` va link/field validation sau patch.
- Ket qua: Quyet dinh target da khoa; current-state inventory van ghi Firebase hien co de P01 go bo co bang chung.
- Bang chung: Supabase official docs duoc tham chieu trong ADR cloud tai P00.T04.
- Rui ro/cong viec con lai: Artifact vuot Edge Runtime budget phai duoc mirror bat bien tu HF sang private Supabase Storage; RLS/storage policies phai co SQL tests.
- Task tiep theo: Tiep tuc P00.T03, sau do tao Supabase ADR tai P00.T04.

## 2026-07-29 09:18 - P00.T03 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Khoa compatibility corpus co provenance va automated execution cho Legado/VBook/public contracts.
- Thoi gian bat dau/ket thuc: 2026-07-29 08:48 / 2026-07-29 09:18
- File thay doi: `app/src/test/resources/compat/**`, `CompatibilityCorpusTest.kt`, `reports/P00-T03-COMPATIBILITY-CORPUS.md`, task matrix va plan log.
- Tom tat trien khai: Tao 20 synthetic CC0 payload; parse Book/RSS/TTS; inspect va chay 8 script thuoc 6 VBook kinds; khoa ReaderProvider/deep-link/27 HTTP/3 WebSocket contracts; ghi SHA-256.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.compat.CompatibilityCorpusTest" --no-daemon --console=plain`.
- Ket qua: BUILD SUCCESSFUL; 5 tests, 0 failures/errors/skipped; 20/20 checksum match.
- Bang chung: `reports/P00-T03-COMPATIBILITY-CORPUS.md`, `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.compat.CompatibilityCorpusTest.xml`.
- Rui ro/cong viec con lai: Contract fixtures la baseline hien tai; implementation package/facade moi se duoc xac minh tai P01/P11.
- Task tiep theo: P00.T04.

## 2026-07-29 09:18 - P00.T04 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Khoa cac quyet dinh kien truc kho dao nguoc, bao gom cloud Supabase thay the Firebase/Drive.
- Thoi gian bat dau/ket thuc: 2026-07-29 09:18 / dang thuc hien
- File thay doi: `docs/plans/drducbook-rebuild-2026/adr/**`, task matrix va plan log.
- Tom tat trien khai: Bat dau ADR cho coexistence/facade, source-cookie-health, appearance, WebService, Agent, media va HF/Supabase Auth/Postgres/Storage.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `adr/**`.
- Rui ro/cong viec con lai: Can khoa authority, cong, schema owner, RLS va rollback cho moi decision.
- Task tiep theo: P00.T05 sau khi ADR validation pass.

## 2026-07-29 09:24 - PLAN - GOOGLE_DRIVE_BACKUP_RETAINED

- Nguoi/agent thuc hien: Codex theo chi dao moi cua nguoi dung.
- Muc tieu: Giu sao luu/dong bo Google Drive trong khi backend chinh van chuyen sang Supabase.
- Thoi gian bat dau/ket thuc: 2026-07-29 09:24 / 2026-07-29 09:24
- File thay doi: README, P01/P07/P10/P11, task matrix, inventory/security reports va ADR-001/ADR-008.
- Tom tat trien khai: Them Google Drive `appDataFolder` nhu snapshot target tuy chon voi consent rieng; them P10.T06 va day snapshot/tests thanh P10.T07/P10.T08; giu Drive package migration sang HF.
- Lenh kiem tra: `rg` target docs; 77-task phase/matrix structural validation; Google Drive official appDataFolder/scope documentation verification.
- Ket qua: Target modes la `SUPABASE`, `GOOGLE_DRIVE`, `BOTH`; Supabase login khong tu xin Drive scope.
- Bang chung: `adr/ADR-008-SUPABASE-HF-AUTH-SYNC.md`, `phases/PHASE-10-CLOUD-AUTH-SYNC.md`.
- Rui ro/cong viec con lai: Multi-target partial commit va identity mismatch can integration tests P10.T08.
- Task tiep theo: Hoan tat P00.T04.

## 2026-07-29 09:30 - P00.T04 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Khoa cac quyet dinh kien truc va rollback truoc implementation.
- Thoi gian bat dau/ket thuc: 2026-07-29 09:18 / 2026-07-29 09:30
- File thay doi: `adr/README.md`, `adr/ADR-001..008`, `reports/P00-T04-ARCHITECTURE-DECISIONS.md`, P01/P07/P10/P11 va plan tracking.
- Tom tat trien khai: Khoa identity/coexistence, compatibility island, SourceKey/CookieVault/health, appearance, Workspace, WebService, Agent sandbox, media va HF/Supabase/Drive architecture.
- Lenh kiem tra: ADR section validator; phase/matrix field+ID validator; README link validator; official Supabase/Google Drive docs review.
- Ket qua: 8/8 ADR PASS; 77/77 task IDs match; 0 field error; 14/14 README links valid.
- Bang chung: `reports/P00-T04-ARCHITECTURE-DECISIONS.md`, `adr/**`.
- Rui ro/cong viec con lai: Edge artifact budget, namespace/facade dependency va multi-target cloud conflict phai duoc test o phase owner.
- Task tiep theo: P00.T05.

## 2026-07-29 09:30 - P00.T05 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Chay compile, focused/full unit, debug assemble va web type/build de khoa baseline Phase 00.
- Thoi gian bat dau/ket thuc: 2026-07-29 09:30 / dang thuc hien
- File thay doi: Build/test outputs, `reports/P00-T05-BASELINE-TESTS.md`, task matrix va plan log.
- Tom tat trien khai: Bat dau gate Android va Web; loi san co neu co se duoc tach khoi regression Phase 00.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P00-T05-BASELINE-TESTS.md`.
- Rui ro/cong viec con lai: Full unit/build co the mat thoi gian; khong dong phase neu session con chay.
- Task tiep theo: Dong Phase 00 neu tat ca mandatory gate pass.

## 2026-07-29 09:44 - P00.T05 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Khoa baseline compile/test/APK/Web/security truoc P01.
- Thoi gian bat dau/ket thuc: 2026-07-29 09:30 / 2026-07-29 09:44
- File thay doi: Build/test outputs, synced `app/src/main/assets/web/vue`, `reports/P00-T05-BASELINE-TESTS.md`, task matrix va plan log.
- Tom tat trien khai: Compile Kotlin; chay focused/full unit; assemble 4 debug APK; type-check/build/sync Vue; hash artifacts; quet secret lan cuoi.
- Lenh kiem tra: Cac lenh Gradle/pnpm/security trong `reports/P00-T05-BASELINE-TESTS.md`.
- Ket qua: Compile PASS; focused 54/54; full 655 tests, 0 failure/error, 1 skipped; 4 APK PASS; Web type/build PASS; secret scan 0 unapproved.
- Bang chung: `reports/P00-T05-BASELINE-TESTS.md`, Gradle XML/HTML reports, APK output va Web dist/assets.
- Rui ro/cong viec con lai: AGP/Gradle deprecations, Baseline Profile compatibility warning, Firebase removal va Room schema export duoc giao phase owner.
- Task tiep theo: P01.T01 khi nguoi dung yeu cau tiep tuc implementation.

## 2026-07-29 09:44 - PHASE 00 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Dong baseline, security, compatibility corpus, architecture contracts va build evidence.
- Thoi gian bat dau/ket thuc: 2026-07-29 08:20 / 2026-07-29 09:44
- File thay doi: `reports/P00-T01..T05`, `adr/**`, corpus/tests, security scanner/policy, task matrix va plan log.
- Tom tat trien khai: 5/5 task DONE; target cloud la Supabase voi Google Drive appDataFolder backup tuy chon; khong thay doi product identity trong phase nay.
- Lenh kiem tra: Inventory/structure validators, compatibility/security tests, full Android build/test va Web build.
- Ket qua: Phase gate PASS; P01 san sang.
- Bang chung: `reports/P00-T05-BASELINE-TESTS.md` va cac report P00 truoc.
- Rui ro/cong viec con lai: Token HF cu can revoke ben ngoai truoc P10 deploy; app icon chua import vi thuoc P01.T05.
- Task tiep theo: P01.T01.

## 2026-07-29 09:50 - P01.T01 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Chuyen application ID/Gradle namespace sang `com.drducbook.app` va giu compatibility packages co chu dich.
- Thoi gian bat dau/ket thuc: 2026-07-29 09:50 / dang thuc hien
- File thay doi: Gradle identity, generated-class imports/facade, manifest class resolution, compatibility allow-list/report va tests.
- Tom tat trien khai: Da luu APK baseline app cu truoc khi build moi; dang inventory R/BuildConfig/databinding va manifest class references bi tac dong boi namespace.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `artifacts/phase01/legacy-baseline/legado-baseline-x86_64-debug.apk`, SHA-256 `583ceaf7229561a0b8f584025bdddfbacabfe8cd610f394434a633603f0b9d3f`.
- Rui ro/cong viec con lai: 472 source/test files dung unqualified `R`; namespace doi can generated-class compatibility/import strategy co compile evidence.
- Task tiep theo: P01.T02 sau khi compile identity moi pass.

## 2026-07-29 10:28 - P01.T01 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Chuyen install/Gradle identity sang `com.drducbook.app` va giu compatibility island.
- Thoi gian bat dau/ket thuc: 2026-07-29 09:50 / 2026-07-29 10:28
- File thay doi: `settings.gradle`, app/baseline Gradle config, generated-class imports, fully-qualified manifest components, `com.drducbook.app/**`, report/task tracking.
- Tom tat trien khai: Doi namespace/application ID; chuyen 478 file import generated classes; tao product AppIdentity/Application; giu `io.legado.app` cho legacy contracts.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`.
- Ket qua: BUILD SUCCESSFUL trong 9 phut 24 giay sau full recompilation.
- Bang chung: `reports/P01-T01-IDENTITY.md`, APK baseline app cu co SHA-256 da ghi.
- Rui ro/cong viec con lai: R8/ABI gate P01.T03; merged manifest/deep links P01.T02.
- Task tiep theo: P01.T02.

## 2026-07-29 10:28 - P01.T02 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Khoa authorities/deep links rieng va khong xung dot app cu.
- Thoi gian bat dau/ket thuc: 2026-07-29 10:28 / dang thuc hien
- File thay doi: AndroidManifest, AuthCallbackActivity/deep-link contract, manifest identity tests va report.
- Tom tat trien khai: Provider/FileProvider/Startup dung `${applicationId}`; them `drducbook://import` va `drducbook://auth/callback` tach host/path; giu `legado`/`yuedu` chooser aliases.
- Lenh kiem tra: Dang chay focused tests va merged manifest assertions.
- Ket qua: Dang thuc hien.
- Bang chung: `ManifestIdentityTest`, `DrDucBookDeepLinksTest`.
- Rui ro/cong viec con lai: Can verify callback compile voi supabase-kt 3.6.0 va merged release authority.
- Task tiep theo: P01.T03.

## 2026-07-29 10:42 - P01.T02 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Tach Android authorities va deep links de app moi cai song song.
- Thoi gian bat dau/ket thuc: 2026-07-29 10:28 / 2026-07-29 10:42
- File thay doi: AndroidManifest, product auth/deep-link handlers, manifest/deep-link tests va report.
- Tom tat trien khai: Them `drducbook://auth/callback`, `drducbook://import`; giu legacy schemes; authorities dung `${applicationId}`.
- Lenh kiem tra: Focused `ManifestIdentityTest` va `DrDucBookDeepLinksTest`; merged manifest scan.
- Ket qua: BUILD SUCCESSFUL; 2/2 tests PASS; debug package/authorities moi duoc xac nhan.
- Bang chung: `reports/P01-T02-MANIFEST-DEEP-LINKS.md` va merged manifest.
- Rui ro/cong viec con lai: Device chooser/provider install test P01.T06.
- Task tiep theo: P01.T03.

## 2026-07-29 10:42 - P01.T03 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Khoa legacy compatibility surface qua allow-list, R8 keep rules va minified tests.
- Thoi gian bat dau/ket thuc: 2026-07-29 10:42 / dang thuc hien
- File thay doi: Compatibility manifest/docs, ProGuard/R8 rules, ABI/corpus tests va minified outputs.
- Tom tat trien khai: Dang doi chieu Rhino registered classes, entities, ReaderProvider/Web/VBook surfaces voi P00 corpus.
- Lenh kiem tra: Se cap nhat khi release/noR8/minified gates chay.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P01-T03-LEGACY-COMPATIBILITY.md`.
- Rui ro/cong viec con lai: Khong keep toan bo `io.legado.app`; chi keep public/reflection/serialization surface co owner.
- Task tiep theo: P01.T04.

## 2026-07-29 18:44 - P01.T03 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Khoa compatibility island, R8 keep rules va ABI release.
- Thoi gian bat dau/ket thuc: 2026-07-29 10:42 / 2026-07-29 18:44
- File thay doi: `legacy-compat-rules.pro`, ABI allow-list/verifier, compatibility contract va report.
- Tom tat trien khai: Giu `io.legado.app` nhu package island; khong keep package-wide; khoa Rhino, VBook, entities va ReaderProvider.
- Lenh kiem tra: Compatibility corpus; final release assemble; `verify-legacy-abi.ps1`.
- Ket qua: Corpus 5/5 PASS; 17/17 class trong 4 dex; release R8 PASS.
- Bang chung: `reports/P01-T03-LEGACY-COMPATIBILITY.md`.
- Rui ro/cong viec con lai: Regenerate Baseline Profile tai P11.
- Task tiep theo: P01.T04.

## 2026-07-29 18:44 - P01.T04 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Chuyen build identity sang Supabase va loai Firebase product runtime.
- Thoi gian bat dau/ket thuc: 2026-07-29 10:45 / 2026-07-29 18:44
- File thay doi: Gradle catalog/build, `com.drducbook.app.cloud/**`, auth callback, privacy docs, tests.
- Tom tat trien khai: Supabase Auth/Postgrest/Storage/Functions + PKCE; public env config; Google login va Drive scopes tach; go Firebase App/Analytics/Perf/config.
- Lenh kiem tra: Supabase/consent focused tests, dependency insight, packaged manifest scan, device cold start.
- Ket qua: Tests/startup PASS; khong FirebaseInitProvider; ML Kit transitive component SPI duoc ghi ro.
- Bang chung: `reports/P01-T04-SUPABASE-BUILD-IDENTITY.md`.
- Rui ro/cong viec con lai: Live Supabase/Google dashboard binding thuoc P10; Drive backup van duoc giu.
- Task tiep theo: P01.T05.

## 2026-07-29 18:44 - P01.T05 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan tat thuong hieu va icon DrDucBook sach.
- Thoi gian bat dau/ket thuc: 2026-07-29 11:10 / 2026-07-29 18:44
- File thay doi: strings/manifest/icons, `branding/**`, branding scripts, Vue/web assets va screenshots.
- Tom tat trien khai: Xoa marker/watermark; tao alpha trong suot; density/adaptive/monochrome/favicon; tat ca alias cung icon; sua foreground 432 px thanh xxxhdpi de khong bi cat.
- Lenh kiem tra: `verify-icons.py`, BrandIdentityTest, AAPT badging, pnpm build va device visual QA.
- Ket qua: 14/14 assets metadata/alpha PASS; web build PASS; visual QA PASS.
- Bang chung: `reports/P01-T05-BRAND-ICON.md`, `artifacts/phase01/icon-qa.png`.
- Rui ro/cong viec con lai: Icon tuy chinh do nguoi dung thuoc P03.
- Task tiep theo: P01.T06.

## 2026-07-29 18:44 - P01.T06 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Chung minh coexistence, data isolation va full build gate.
- Thoi gian bat dau/ket thuc: 2026-07-29 18:05 / 2026-07-29 18:44
- File thay doi: APK outputs, device screenshots, report/task tracking.
- Tom tat trien khai: Cai/mo hai app; doi chieu UID/data/provider; giai cai/cai lai DrDucBook; giu nguyen Legado DB; build debug/noR8/release.
- Lenh kiem tra: Full unit, assemble 3 variants, AAPT/apksigner/ADB/provider scans, ABI verifier.
- Ket qua: 663 tests (0 fail/error, 1 skipped); all builds PASS; 2 packages co UID/data dir rieng; no crash/provider conflict.
- Bang chung: `reports/P01-T06-COEXISTENCE-RELEASE.md`, `artifacts/phase01/device-*.png`.
- Rui ro/cong viec con lai: AGP incremental splitter can retry full package; production signing thuoc P11.
- Task tiep theo: Phase 02.

## 2026-07-29 18:44 - PHASE 01 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Dong nhan dien, cloud build foundation, compatibility va cai song song DrDucBook.
- Thoi gian bat dau/ket thuc: 2026-07-29 09:50 / 2026-07-29 18:44
- Tom tat trien khai: 6/6 task DONE; package `com.drducbook.app`; Supabase foundation; icon trong suot; R8 compatibility; device coexistence.
- Ket qua: Tat ca gate bat buoc PASS; Phase 02 san sang.
- Bang chung: `reports/P01-T01..T06`, TASK-MATRIX va final APK hashes.
- Rui ro/cong viec con lai: Deployment credentials/signing khong ghi repo va duoc ban giao dung phase owner.
- Task tiep theo: P02.T01.

## 2026-07-29 - P02.T01 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Rut gon navigation con Home, Bookshelf, Explore, Workspace va My.
- File thay doi: Main destination/state/screen, navigation settings, icons, route tests.
- Tom tat trien khai: Collapse saved order cu ve Workspace; bo Browser/Agent/Writing/Ebook/RSS khoi top-level; giu toan bo route sau.
- Lenh kiem tra: Debug Kotlin compile va focused navigation tests.
- Ket qua: Compile PASS; route migration/back stack tests PASS.
- Bang chung: `reports/P02-T01-NAVIGATION.md`.
- Rui ro/cong viec con lai: Visual responsive gate thuoc P02.T04.
- Task tiep theo: P02.T02.

## 2026-07-29 21:13 - P02.T02 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Tao Workspace Compose/MVI gom Sang tac, Ebook, Agent va Nguon RSS.
- Thoi gian ket thuc: 2026-07-29 21:13.
- File thay doi: `ui/workspace/**`, DI, MainScreen/MainNavGraph, strings va unit tests.
- Tom tat trien khai: StateFlow/SharedFlow MVI; recent projects/tasks; badge chi hien khi co du lieu; loading/error/empty; callback Navigation 3.
- Lenh kiem tra: Focused Phase 02 unit suite, debug assemble/install va Android 14 visual/device route smoke.
- Ket qua: Workspace state tests PASS; 4 module dung nhan; Workspace -> Sang tac dung route; khong badge 0 hoac text tran.
- Bang chung: `reports/P02-T02-WORKSPACE.md`, `artifacts/phase02/phone-workspace-final.png`, `artifacts/phase02/workspace-writing-route-final.png`.
- Rui ro/cong viec con lai: Tablet/state recreation thuoc P02.T04.
- Task tiep theo: P02.T03.

## 2026-07-29 21:30 - P02.T03 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Khoa hanh vi Back/Exit cua Browser va giu route/session.
- Thoi gian ket thuc: 2026-07-29 21:30.
- File thay doi: Browser Contract/ViewModel/Screen/RouteScreen, MainNavigator/MainActivity, strings va tests.
- Tom tat trien khai: Back history -> tab -> app route; nut X thoat Browser; cold Home fallback; explicit route giu den khi Navigation 3 consume.
- Lenh kiem tra: Focused test suite, debug assemble/install, Explore/warm/cold Browser device smoke tren Android 14.
- Ket qua: Test PASS; X quay lai Explore/Settings dung route; session tests PASS; Browser khong con o top-level nav.
- Bang chung: `reports/P02-T03-BROWSER-EXIT.md`, `artifacts/phase02/phone-browser-final.png`, `artifacts/phase02/warm-browser-exit-return.png`.
- Rui ro/cong viec con lai: Tablet, rotation va process restoration thuoc P02.T04.
- Task tiep theo: P02.T04.

## 2026-07-29 21:38 - P02.T04 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Giu navigation/Browser state va responsive bottom bar/rail qua configuration/process recreation.
- Thoi gian ket thuc: 2026-07-29 21:38.
- File thay doi: MainActivity responsive policy, Navigation 3 state flow, tests va device artifacts.
- Tom tat trien khai: `sw600dp` rail policy; pending route consume; saveable nav state; Browser session store; compact/expanded continuity.
- Lenh kiem tra: Responsive/navigation/session unit tests; force-stop restore; rotation; phone/tablet visual smoke.
- Ket qua: Browser restore example.edu; Workspace giu active qua density/config change; rail 5 muc khong tran; emulator restore 900x1600@320.
- Bang chung: `reports/P02-T04-RESTORATION-RESPONSIVE.md`, `artifacts/phase02/browser-process-restored.png`, `artifacts/phase02/tablet-workspace-final.png`.
- Rui ro/cong viec con lai: Full localization/accessibility/unit gate thuoc P02.T05.
- Task tiep theo: P02.T05.

## 2026-07-29 21:42 - P02.T05 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Dong localization, accessibility, About fix va full regression gate.
- Thoi gian ket thuc: 2026-07-29 21:42.
- File thay doi: values/values-vi, Workspace/Browser semantics, About Material/Miuix screens, tests va artifacts.
- Tom tat trien khai: EN/VI strings; route value fix; touch/semantics labels; Compose-compatible About PNG va can le mo ta.
- Lenh kiem tra: Resource audit, full `:app:testAppDebugUnitTest`, assemble debug, Android 14 About/phone/tablet smoke.
- Ket qua: 677 tests, 0 failure/error, 1 skipped; APK build PASS; About khong crash; a11y/visual checklist PASS.
- Bang chung: `reports/P02-T05-QUALITY.md`, `artifacts/phase02/about-fixed-final.png`, test XML reports.
- Rui ro/cong viec con lai: Device-farm TalkBack automation de danh cho release gate.
- Task tiep theo: Dong Phase 02.

## 2026-07-29 21:42 - PHASE 02 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Rut gon navigation, tao Workspace va bien Browser thanh route co diem thoat ro rang.
- Thoi gian ket thuc: 2026-07-29 21:42.
- Tom tat trien khai: 5 destination; Workspace MVI; Browser Back/Exit; route/session restoration; responsive rail; EN/VI/a11y; About fix.
- Ket qua: 5/5 task DONE; full unit/build/device gate PASS.
- Bang chung: `reports/P02-T01-NAVIGATION.md` den `reports/P02-T05-QUALITY.md`, `artifacts/phase02/**`.
- Rui ro/cong viec con lai: Khong co blocker; cac tinh nang Browser-source-cookie chuyen sang Phase 04 theo dependency plan.
- Task tiep theo: Phase 03.

## 2026-07-30 00:01 - P03.T01 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Khoa schema/repository cho AppearanceProfile, preset va asset store.
- Thoi gian bat dau/ket thuc: 2026-07-30 00:01 / dang thuc hien
- File thay doi: `app/src/main/java/io/legado/app/domain/model/AppearanceProfile.kt`, `app/src/main/java/io/legado/app/data/repository/AppearanceRepository.kt`, `app/src/main/java/io/legado/app/domain/usecase/AppearanceUseCase.kt`, `reports/P03-T01-APPEARANCE-PROFILE.md`
- Tom tat trien khai: Bat dau doi chieu preset, state snapshot, migration va fallback asset store.
- Lenh kiem tra: Se cap nhat sau khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P03-T01-APPEARANCE-PROFILE.md`
- Rui ro/cong viec con lai: Phai giu Material/Miuix doc chung mot contract va khong lam hong legacy fallback.
- Task tiep theo: P03.T02.

## 2026-07-30 00:02 - P03.T01 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Khoa schema/repository cho AppearanceProfile, preset va asset store.
- Thoi gian bat dau/ket thuc: 2026-07-30 00:01 / 2026-07-30 00:02
- File thay doi: `app/src/main/java/io/legado/app/domain/model/AppearanceProfile.kt`, `app/src/main/java/io/legado/app/data/repository/AppearanceRepository.kt`, `app/src/main/java/io/legado/app/domain/usecase/AppearanceUseCase.kt`, `reports/P03-T01-APPEARANCE-PROFILE.md`
- Tom tat trien khai: Tao profile/state/snapshot/preset, luu atomic va cleanup asset theo content-hash.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain`.
- Ket qua: BUILD SUCCESSFUL; app compile/assemble PASS.
- Bang chung: `reports/P03-T01-APPEARANCE-PROFILE.md`
- Rui ro/cong viec con lai: Schema version bump can migration gate o phase cuoi.
- Task tiep theo: P03.T02.

## 2026-07-30 00:02 - P03.T02 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Gom Chu de, Bieu tuong, Hinh nen va Xem truoc vao mot trung tam ca nhan hoa.
- Thoi gian bat dau/ket thuc: 2026-07-30 00:02 / dang thuc hien
- File thay doi: `app/src/main/java/io/legado/app/ui/personalization/**`, `app/src/main/java/io/legado/app/ui/main/MainNavGraph.kt`, `reports/P03-T02-PERSONALIZATION-CENTER.md`
- Tom tat trien khai: Bat dau kiem tra MVI/RouteScreen, draft state va flow back/apply/discard.
- Lenh kiem tra: Se cap nhat sau khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P03-T02-PERSONALIZATION-CENTER.md`
- Rui ro/cong viec con lai: Can giu process recreation va unsaved-change confirmation.
- Task tiep theo: P03.T03.

## 2026-07-30 00:03 - P03.T02 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Gom Chu de, Bieu tuong, Hinh nen va Xem truoc vao mot trung tam ca nhan hoa.
- Thoi gian bat dau/ket thuc: 2026-07-30 00:02 / 2026-07-30 00:03
- File thay doi: `app/src/main/java/io/legado/app/ui/personalization/**`, `app/src/main/java/io/legado/app/ui/main/MainNavGraph.kt`, `reports/P03-T02-PERSONALIZATION-CENTER.md`
- Tom tat trien khai: Tao Contract/ViewModel/Screen/RouteScreen, picker effect va draft persistence qua SavedStateHandle.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain`.
- Ket qua: BUILD SUCCESSFUL; visual QA co `p3-center-theme.png`, `p3-theme-tab.png`, `p3-preview-tab.png`.
- Bang chung: `reports/P03-T02-PERSONALIZATION-CENTER.md`
- Rui ro/cong viec con lai: Unsaved-change va restore flow phai duoc giu o P11.
- Task tiep theo: P03.T03.

## 2026-07-30 00:03 - P03.T03 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien IconSlot va trinh chinh icon.
- Thoi gian bat dau/ket thuc: 2026-07-30 00:03 / dang thuc hien
- File thay doi: `app/src/main/java/io/legado/app/domain/model/IconSlot.kt`, `app/src/main/java/io/legado/app/ui/personalization/PersonalizationIconTab.kt`, `reports/P03-T03-ICON-EDITOR.md`
- Tom tat trien khai: Bat dau doi chieu slot coverage, import SVG/PNG/WebP va preview scale/padding/tint/background.
- Lenh kiem tra: Se cap nhat sau khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P03-T03-ICON-EDITOR.md`
- Rui ro/cong viec con lai: Icon khong duoc lam dich layout hoac mat contentDescription.
- Task tiep theo: P03.T04.

## 2026-07-30 00:04 - P03.T03 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien IconSlot va trinh chinh icon.
- Thoi gian bat dau/ket thuc: 2026-07-30 00:03 / 2026-07-30 00:04
- File thay doi: `app/src/main/java/io/legado/app/domain/model/IconSlot.kt`, `app/src/main/java/io/legado/app/ui/personalization/PersonalizationIconTab.kt`, `reports/P03-T03-ICON-EDITOR.md`
- Tom tat trien khai: Khoa slot registry, import asset va runtime icon wiring cho navigation/Workspace/Browser/reader.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain`.
- Ket qua: BUILD SUCCESSFUL; visual QA co `p3-theme-copper-applied.png`, `p3-theme-forest-applied.png`, `p3-theme-ink-applied.png`, `p3-theme-ink-miuix.png`.
- Bang chung: `reports/P03-T03-ICON-EDITOR.md`
- Rui ro/cong viec con lai: Future slot expansion can phai giu stable dimension va fallback asset.
- Task tiep theo: P03.T04.

## 2026-07-30 00:04 - P03.T04 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien wallpaper toan app va theo module.
- Thoi gian bat dau/ket thuc: 2026-07-30 00:04 / dang thuc hien
- File thay doi: `app/src/main/java/io/legado/app/ui/personalization/PersonalizationWallpaperTab.kt`, `app/src/main/java/io/legado/app/ui/personalization/PersonalizationPreviewTab.kt`, `reports/P03-T04-WALLPAPER.md`
- Tom tat trien khai: Bat dau kiem tra target/fit/alignment/opacity/blur/overlay va contrast preview.
- Lenh kiem tra: Se cap nhat sau khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P03-T04-WALLPAPER.md`
- Rui ro/cong viec con lai: Preview label phai ro tren nen sang va nen toi.
- Task tiep theo: P03.T05.

## 2026-07-30 00:05 - P03.T04 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien wallpaper toan app va theo module.
- Thoi gian bat dau/ket thuc: 2026-07-30 00:04 / 2026-07-30 00:05
- File thay doi: `app/src/main/java/io/legado/app/ui/personalization/PersonalizationWallpaperTab.kt`, `app/src/main/java/io/legado/app/ui/personalization/PersonalizationPreviewTab.kt`, `reports/P03-T04-WALLPAPER.md`
- Tom tat trien khai: Ho tro module override, preview live va sua nhan `Xem truoc hinh nen` theo mau tuong phan khi khong co asset.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain`; adb screenshot smoke.
- Ket qua: BUILD SUCCESSFUL; `p3-wallpaper-tab.png` duoc cap nhat voi nhan preview contrast.
- Bang chung: `reports/P03-T04-WALLPAPER.md`
- Rui ro/cong viec con lai: Wallpaper asset co the can warning/overlay o phase sau neu do sang qua cao.
- Task tiep theo: P03.T05.

## 2026-07-30 00:05 - P03.T05 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Khoa import/export `.drductheme` versioned va an toan.
- Thoi gian bat dau/ket thuc: 2026-07-30 00:05 / dang thuc hien
- File thay doi: `app/src/main/java/io/legado/app/help/config/ThemePackageManager.kt`, `app/src/main/java/io/legado/app/help/config/ThemePackageSecurityPolicy.kt`, `reports/P03-T05-DRDUCTHEME.md`
- Tom tat trien khai: Bat dau kiem tra manifest, checksum, MIME, path traversal va preview import.
- Lenh kiem tra: Se cap nhat sau khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P03-T05-DRDUCTHEME.md`
- Rui ro/cong viec con lai: Package khong duoc chua executable content hoac asset unreferenced.
- Task tiep theo: P03.T06.

## 2026-07-30 00:06 - P03.T05 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Khoa import/export `.drductheme` versioned va an toan.
- Thoi gian bat dau/ket thuc: 2026-07-30 00:05 / 2026-07-30 00:06
- File thay doi: `app/src/main/java/io/legado/app/help/config/ThemePackageManager.kt`, `app/src/main/java/io/legado/app/help/config/ThemePackageSecurityPolicy.kt`, `reports/P03-T05-DRDUCTHEME.md`
- Tom tat trien khai: Khoa format v2, preview truoc import va validate checksum/MIME/size/path/SVG content.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain`.
- Ket qua: BUILD SUCCESSFUL; `ThemePackageSecurityPolicyTest` PASS theo phase gate.
- Bang chung: `reports/P03-T05-DRDUCTHEME.md`
- Rui ro/cong viec con lai: Moi format version moi phai giu backward compatibility.
- Task tiep theo: P03.T06.

## 2026-07-30 00:06 - P03.T06 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Khoa backup/fallback va visual QA cho phase ca nhan hoa.
- Thoi gian bat dau/ket thuc: 2026-07-30 00:06 / dang thuc hien
- File thay doi: `app/src/main/java/io/legado/app/data/repository/AppearanceRepository.kt`, `reports/P03-T06-QUALITY.md`, `docs/plans/drducbook-rebuild-2026/artifacts/phase03/**`
- Tom tat trien khai: Bat dau doi chieu snapshot backup, restore cleanup va screenshot matrix.
- Lenh kiem tra: Se cap nhat sau khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P03-T06-QUALITY.md`
- Rui ro/cong viec con lai: Backup/restore va visual smoke can giu hash match va fallback asset.
- Task tiep theo: Dong Phase 03.

## 2026-07-30 00:07 - P03.T06 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Khoa backup/fallback va visual QA cho phase ca nhan hoa.
- Thoi gian bat dau/ket thuc: 2026-07-30 00:06 / 2026-07-30 00:07
- File thay doi: `app/src/main/java/io/legado/app/data/repository/AppearanceRepository.kt`, `reports/P03-T06-QUALITY.md`, `docs/plans/drducbook-rebuild-2026/artifacts/phase03/**`
- Tom tat trien khai: Snapshot appearance/assets, cleanup unreferenced asset va cap nhat screenshot matrix cuoi cung.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain`; adb install/screenshot smoke.
- Ket qua: BUILD SUCCESSFUL; wallpaper contrast screenshot moi da cap nhat.
- Bang chung: `reports/P03-T06-QUALITY.md`
- Rui ro/cong viec con lai: Google Drive backup dong bo van giu cho phase cloud sau nay.
- Task tiep theo: PHASE 03 - DONE.

## 2026-07-30 00:07 - PHASE 03 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Dong phase ca nhan hoa giao dien Android.
- Thoi gian bat dau/ket thuc: 2026-07-30 00:01 / 2026-07-30 00:07
- Tom tat trien khai: 6/6 task DONE; theme, icon, wallpaper, `.drductheme`, backup va visual QA deu co evidence.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain`; adb visual smoke.
- Ket qua: Phase gate PASS; `p3-wallpaper-tab.png` da duoc cap nhat voi contrast label moi.
- Bang chung: `reports/P03-T01..T06`, `docs/plans/drducbook-rebuild-2026/artifacts/phase03/**`
- Rui ro/cong viec con lai: Phase 04 se tiep tuc cac module source/browser/cookie theo dependency plan.
- Task tiep theo: P04.T01.
## 2026-07-30 00:22 - P04.T01 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Gan Browser tab voi source context on dinh qua SourceKey va domain index.
- Thoi gian bat dau/ket thuc: 2026-07-30 00:22 / dang thuc hien
- File thay doi: `app/src/main/java/io/legado/app/domain/model/SourceKey.kt`, `app/src/main/java/io/legado/app/domain/gateway/SourceDomainIndexGateway.kt`, `app/src/main/java/io/legado/app/domain/usecase/ResolveBrowserSourceContextUseCase.kt`, `app/src/main/java/io/legado/app/data/repository/SourceDomainIndexRepository.kt`, `app/src/main/java/io/legado/app/ui/browser/BrowserContract.kt`, `app/src/main/java/io/legado/app/ui/browser/BrowserViewModel.kt`, `app/src/main/java/io/legado/app/ui/browser/BrowserTabStore.kt`, `app/src/main/java/io/legado/app/data/dao/BookSourceDao.kt`, `app/src/main/java/io/legado/app/di/appModule.kt`
- Tom tat trien khai: Bat dau tao source key/domain index, rebuild flow tu source/RSS/VBook metadata va luu sourceKey cho session browser.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P04-T01-SOURCE-CONTEXT.md`
- Rui ro con lai: Match rule phai giu exact host, subdomain, redirect va khong tao match gia cho VBook non-HTTP.
- Task tiep theo: P04.T01 can verification va doc tracking.

## 2026-07-30 00:56 - P04.T01 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Gan Browser tab voi source context on dinh qua SourceKey va domain index.
- Thoi gian bat dau/ket thuc: 2026-07-30 00:22 / 2026-07-30 00:56
- File thay doi: `app/src/main/java/io/legado/app/domain/model/SourceKey.kt`, `app/src/main/java/io/legado/app/domain/gateway/SourceDomainIndexGateway.kt`, `app/src/main/java/io/legado/app/domain/usecase/ResolveBrowserSourceContextUseCase.kt`, `app/src/main/java/io/legado/app/data/repository/SourceDomainIndexRepository.kt`, `app/src/main/java/io/legado/app/ui/browser/BrowserContract.kt`, `app/src/main/java/io/legado/app/ui/browser/BrowserViewModel.kt`, `app/src/main/java/io/legado/app/ui/browser/BrowserTabStore.kt`, `app/src/main/java/io/legado/app/data/dao/BookSourceDao.kt`, `app/src/main/java/io/legado/app/di/appModule.kt`, `app/src/test/java/io/legado/app/domain/model/SourceDomainIndexTest.kt`, `app/src/test/java/io/legado/app/ui/browser/BrowserTabStoreTest.kt`, `reports/P04-T01-SOURCE-CONTEXT.md`
- Tom tat trien khai: Tao index tu book/RSS/VBook metadata, giu sourceKey trong tab session va cho BrowserViewModel reconcile context khi navigate/switch/close.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain --stacktrace`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.SourceDomainIndexTest" --tests "io.legado.app.ui.browser.BrowserTabStoreTest" --rerun-tasks --no-daemon --console=plain`
- Ket qua: BUILD SUCCESSFUL; `SourceDomainIndexTest` 6 tests PASS; `BrowserTabStoreTest` 4 tests PASS.
- Bang chung: `reports/P04-T01-SOURCE-CONTEXT.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.model.SourceDomainIndexTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.browser.BrowserTabStoreTest.xml`
- Rui ro con lai: P04.T02 se dung context nay de thay Browser Home va action surface.
- Task tiep theo: P04.T02.

## 2026-07-30 01:27 - P04.T02 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Thay Google home bang Browser Home noi bo, hien thi shortcut nguon, summary health va action surface theo source context.
- Thoi gian bat dau/ket thuc: 2026-07-30 01:27 / dang thuc hien
- File thay doi: `ui/browser/**`, `domain/model/SourceKey.kt`, `data/repository/SourceDomainIndexRepository.kt`, strings, Browser tab tests, report/task tracking.
- Tom tat trien khai: Bat dau tach home mode khoi web page mode de RouteScreen khong load WebView khi o home, dong thoi noi source shortcut/health/action menu vao context da tao o P04.T01.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P04-T02-BROWSER-HOME.md`
- Rui ro/cong viec con lai: Bookmark CRUD va cookie vault full sync van thuoc P04.T03-P04.T05; P04.T02 chi expose home/action UI tren contracts hien co.
- Task tiep theo: P04.T03 sau khi P04.T02 pass.

## 2026-07-30 02:19 - P04.T02 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Thay Google home bang Browser Home noi bo, hien thi shortcut nguon, summary health va action surface theo source context.
- Thoi gian bat dau/ket thuc: 2026-07-30 01:27 / 2026-07-30 02:19
- File thay doi: `app/src/main/java/io/legado/app/domain/model/SourceKey.kt`, `app/src/main/java/io/legado/app/data/repository/SourceDomainIndexRepository.kt`, `app/src/main/java/io/legado/app/ui/browser/BrowserContract.kt`, `app/src/main/java/io/legado/app/ui/browser/BrowserViewModel.kt`, `app/src/main/java/io/legado/app/ui/browser/BrowserTabStore.kt`, `app/src/main/java/io/legado/app/ui/browser/BrowserRouteScreen.kt`, `app/src/main/java/io/legado/app/ui/browser/BrowserScreen.kt`, `app/src/main/java/io/legado/app/ui/main/MainNavGraph.kt`, strings, `app/src/test/java/io/legado/app/ui/browser/BrowserTabStoreTest.kt`, `reports/P04-T02-BROWSER-HOME.md`
- Tom tat trien khai: Browser Home noi bo khong load WebView, hien source shortcut/recent tabs/health summary, them menu source action va back-to-app, dong bo cookie WebView ve source cookie store khi can.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain --stacktrace`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.ui.browser.BrowserTabStoreTest" --tests "io.legado.app.domain.model.SourceDomainIndexTest" --no-daemon --console=plain`
- Ket qua: BUILD SUCCESSFUL; `BrowserTabStoreTest` 5 tests PASS; `SourceDomainIndexTest` 6 tests PASS.
- Bang chung: `reports/P04-T02-BROWSER-HOME.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.browser.BrowserTabStoreTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.model.SourceDomainIndexTest.xml`
- Rui ro/cong viec con lai: Bookmark CRUD P04.T03, CookieVault P04.T04, full cookie sync P04.T05 va targeted probe P04.T06 van tach dung dependency plan.
- Task tiep theo: P04.T03.

## 2026-07-30 02:20 - P04.T03 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Tao bookmark web rieng, pin/hide preference cho source shortcut va de Browser Home phan anh source import/edit/disable/delete ngay.
- Thoi gian bat dau/ket thuc: 2026-07-30 02:20 / dang thuc hien
- File thay doi: Browser bookmark entities/DAO/repository/UI, Room schema/migration, backup policy, tests va report.
- Tom tat trien khai: Bat dau tach bookmark web khoi bookmark sach, giu source shortcut derive tu enabled HTTP source va them preference pin/hide.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P04-T03-BROWSER-BOOKMARKS.md`
- Rui ro/cong viec con lai: CookieVault va full cookie sync van thuoc P04.T04-P04.T05.
- Task tiep theo: P04.T04 sau khi P04.T03 pass.

## 2026-07-30 02:46 - P04.T03 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Tao bookmark web rieng, pin/hide preference cho source shortcut va de Browser Home phan anh source import/edit/disable/delete ngay.
- Thoi gian bat dau/ket thuc: 2026-07-30 02:20 / 2026-07-30 02:46
- File thay doi: Browser bookmark domain/entity/DAO/repository/UI, Room schema/migration, backup/restore, strings, tests va report.
- Tom tat trien khai: Tach bookmark web khoi bookmark sach; them preference pin/an theo `SourceKey`; Browser Home tim kiem bookmark/shortcut va derive shortcut tu source HTTP enabled.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain --stacktrace`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.BrowserBookmarkRepositoryTest" --tests "io.legado.app.ui.browser.BrowserHomeDataTest" --tests "io.legado.app.ui.browser.BrowserTabStoreTest" --tests "io.legado.app.domain.model.SourceDomainIndexTest" --no-daemon --console=plain`
- Ket qua: BUILD SUCCESSFUL; 15 focused tests PASS, 0 failure/error/skipped.
- Bang chung: `reports/P04-T03-BROWSER-BOOKMARKS.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.BrowserBookmarkRepositoryTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.browser.BrowserHomeDataTest.xml`
- Rui ro/cong viec con lai: CookieVault encryption, cookie runtime sync va targeted probe van thuoc P04.T04-P04.T06.
- Task tiep theo: P04.T04.

## 2026-07-30 02:47 - P04.T04 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Tao CookieVault co schema scoped/expiry va ma hoa gia tri cookie thay cho luu plaintext.
- Thoi gian bat dau/ket thuc: 2026-07-30 02:47 / dang thuc hien
- File thay doi: Cookie entity/DAO/migration, encrypted codec/Keystore, gateway/interface, compatibility adapter, tests va report.
- Tom tat trien khai: Bat dau doi chieu `Cookie`, `CookieDao`, `CookieManager`, `CookieStore`, backup/restore va Rhino JS surface de giu compatibility trong khi khong lo plaintext.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P04-T04-COOKIE-VAULT.md`
- Rui ro/cong viec con lai: P04.T05 moi noi dong bo day du WebView/OkHttp/Cronet/Rhino/VBook; P04.T04 chi khoa vault/schema/encryption va adapter compatibility.
- Task tiep theo: P04.T05 sau khi CookieVault pass.

## 2026-07-30 03:44 - P04.T04 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Tao CookieVault co schema scoped/expiry va ma hoa gia tri cookie thay cho luu plaintext.
- Thoi gian bat dau/ket thuc: 2026-07-30 02:47 / 2026-07-30 03:44
- File thay doi: `SourceCookieGateway`, `CookieVaultEntity`, `CookieDao`, `data/cookie/*`, `AppDatabase`, `DatabaseMigrations`, schema `107.json`, `appModule`, `App`, `CookieManager`, `CookieStore`, cookie vault/security tests va `reports/P04-T04-COOKIE-VAULT.md`.
- Tom tat trien khai: Them vault encrypted bang AES-GCM Android Keystore, migration `106 -> 107`, adapter compatibility cho `CookieStore`, migrate legacy plaintext sang vault, cleanup expiry va fallback scope theo host sach.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.cookie.CookieVaultRepositoryTest" --tests "io.legado.app.help.http.CookieManagerSecurityTest" --no-daemon --console=plain`
- Ket qua: BUILD SUCCESSFUL; 4 focused tests PASS, 0 failure/error/skipped.
- Bang chung: `reports/P04-T04-COOKIE-VAULT.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.cookie.CookieVaultRepositoryTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.http.CookieManagerSecurityTest.xml`
- Rui ro/cong viec con lai: P04.T05 van can dong bo day du WebView/OkHttp/Cronet/Rhino/VBook va bo `removeSessionCookies` toan cuc khoi luong ap cookie vao WebView.
- Task tiep theo: P04.T05.

## 2026-07-30 03:46 - P04.T05 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Dong bo cookie WebView, OkHttp, Cronet, Rhino, VBook, SourceLogin va Browser qua cung `CookieStore`/`SourceCookieGateway`.
- Thoi gian bat dau/ket thuc: 2026-07-30 03:46 / dang thuc hien
- File thay doi: HTTP/Cronet/Rhino/VBook cookie adapters, BrowserRouteScreen WebView bridge, SourceLogin/WebView login paths, tests va report.
- Tom tat trien khai: Bat dau audit cac diem apply/import cookie de loai bo xoa session toan cuc va dam bao moi runtime doc/ghi qua vault.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P04-T05-COOKIE-RUNTIME-SYNC.md`
- Rui ro/cong viec con lai: Can giu tuong thich Legado/VBook ext/plugin va khong ghi cookie value vao log/report.
- Task tiep theo: P04.T06 sau khi runtime sync pass.

## 2026-07-30 04:05 - P04.T05 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Dong bo cookie WebView, OkHttp, Cronet, Rhino, VBook, SourceLogin va Browser qua cung `CookieStore`/`SourceCookieGateway`.
- Thoi gian bat dau/ket thuc: 2026-07-30 03:46 / 2026-07-30 04:05
- File thay doi: `CookieManager`, `BackstageWebView`, `BrowserRouteScreen`, `WebViewActivity`, `WebViewLoginFragment`, `RssReadWebController`, `BottomWebViewDialog`, `VbookExecutor`, report va test evidence.
- Tom tat trien khai: Tao helper apply/capture cookie WebView; persist `Set-Cookie` vao vault; sync RSS, login, browser, backstage va VBook fetch qua cung gateway; bo xoa session toan cuc trong WebView apply path.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.cookie.CookieVaultRepositoryTest" --tests "io.legado.app.help.http.CookieManagerSecurityTest" --no-daemon --console=plain`
- Ket qua: BUILD SUCCESSFUL; 4 focused tests PASS, 0 failure/error/skipped.
- Bang chung: `reports/P04-T05-COOKIE-RUNTIME-SYNC.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.cookie.CookieVaultRepositoryTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.http.CookieManagerSecurityTest.xml`
- Rui ro/cong viec con lai: P04.T06 van can tong hop dang nhap/probe target; P04.T07 can device/browser smoke cho cac luong cookie moi.
- Task tiep theo: P04.T06.

## 2026-07-30 04:06 - P04.T06 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Noi Browser sync-login voi targeted probe theo sourceUrl va giu dashboard check all-source.
- Thoi gian bat dau/ket thuc: 2026-07-30 04:06 / dang thuc hien
- File thay doi: `app/src/main/java/io/legado/app/worker/BookSourceHealthWorker.kt`, `app/src/main/java/io/legado/app/worker/BookSourceHealthCheckProcessor.kt`, `app/src/main/java/io/legado/app/ui/browser/BrowserRouteScreen.kt`, `app/src/main/java/io/legado/app/di/appModule.kt`, tests va report.
- Tom tat trien khai: Bat dau tach processor check 1 nguon vs all-source, them inputData `sourceUrl` cho worker va chuyen Browser effect sang runNow target.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P04-T06-BROWSER-LOGIN-TARGETED-PROBE.md`
- Rui ro/cong viec con lai: Can giu all-source dashboard action khong doi hanh vi va khong lam roi cookie sync path.
- Task tiep theo: P04.T06.

## 2026-07-30 04:34 - P04.T06 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Noi Browser sync-login voi targeted probe theo sourceUrl va giu dashboard check all-source.
- Thoi gian bat dau/ket thuc: 2026-07-30 04:06 / 2026-07-30 04:34
- File thay doi: `app/src/main/java/io/legado/app/worker/BookSourceHealthWorker.kt`, `app/src/main/java/io/legado/app/worker/BookSourceHealthCheckProcessor.kt`, `app/src/main/java/io/legado/app/ui/browser/BrowserRouteScreen.kt`, `app/src/main/java/io/legado/app/di/appModule.kt`, `app/src/test/java/io/legado/app/worker/BookSourceHealthCheckProcessorTest.kt`, `app/src/test/java/io/legado/app/worker/BookSourceHealthWorkerTest.kt`, `reports/P04-T06-BROWSER-LOGIN-TARGETED-PROBE.md`
- Tom tat trien khai: Worker now nhan `sourceUrl` target; processor check 1 source hay all enabled; Browser sync-login sync cookie voi scope sourceUrl va enqueue targeted probe; dashboard `CheckNow` van chay all-source.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.worker.BookSourceHealthCheckProcessorTest" --tests "io.legado.app.worker.BookSourceHealthWorkerTest" --tests "io.legado.app.domain.usecase.ProbeBookSourceUseCaseTest" --no-daemon --console=plain`
- Ket qua: BUILD SUCCESSFUL; focused tests PASS.
- Bang chung: `reports/P04-T06-BROWSER-LOGIN-TARGETED-PROBE.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.worker.BookSourceHealthCheckProcessorTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.worker.BookSourceHealthWorkerTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.usecase.ProbeBookSourceUseCaseTest.xml`
- Rui ro/cong viec con lai: P04.T07 van can device/browser smoke cho login/probe/cookie luong moi va regression source isolation.
- Task tiep theo: P04.T07.

## 2026-07-30 05:11 - P04.T07 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Dong gate phase bang unit/integration/device tests cho Browser/source/cookie.
- Thoi gian bat dau/ket thuc: 2026-07-30 05:11 / dang thuc hien
- File thay doi: `app/src/test/java/io/legado/app/domain/model/SourceDomainIndexTest.kt`, `app/src/test/java/io/legado/app/ui/browser/BrowserTabStoreTest.kt`, `app/src/test/java/io/legado/app/data/cookie/CookieVaultRepositoryTest.kt`, `app/src/test/java/io/legado/app/worker/BookSourceHealthCheckProcessorTest.kt`, `app/src/androidTest/java/io/legado/app/integration/SourceBrowserIntegrationTest.kt`, `app/src/androidTest/java/io/legado/app/HttpTest.kt`, report va task tracking.
- Tom tat trien khai: Bat dau bo sung regression cho multiple tabs, same host, secure/host-only cookie, logout/removeCookie, targeted no-op va device smoke tren emulator.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P04-T07-BROWSER-SOURCE-COOKIE-REGRESSION.md`
- Rui ro/cong viec con lai: SSL block va download listener con can device smoke khac neu can nang gate.
- Task tiep theo: P04.T07.

## 2026-07-30 05:11 - P04.T07 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Dong gate phase bang unit/integration/device tests cho Browser/source/cookie.
- Thoi gian bat dau/ket thuc: 2026-07-30 05:11 / 2026-07-30 05:11
- File thay doi: `app/src/test/java/io/legado/app/domain/model/SourceDomainIndexTest.kt`, `app/src/test/java/io/legado/app/ui/browser/BrowserTabStoreTest.kt`, `app/src/test/java/io/legado/app/data/cookie/CookieVaultRepositoryTest.kt`, `app/src/test/java/io/legado/app/worker/BookSourceHealthCheckProcessorTest.kt`, `app/src/androidTest/java/io/legado/app/integration/SourceBrowserIntegrationTest.kt`, `app/src/androidTest/java/io/legado/app/HttpTest.kt`, report va task tracking.
- Tom tat trien khai: Mo rong regression cho source domain matching, Browser tab policy, CookieVault secure/host-only/logout/fail-closed, targeted health no-op, va browser cookie bridge device smoke.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.SourceDomainIndexTest" --tests "io.legado.app.ui.browser.BrowserTabStoreTest" --tests "io.legado.app.ui.browser.BrowserBackPolicyTest" --tests "io.legado.app.ui.browser.BrowserHomeDataTest" --tests "io.legado.app.data.repository.BrowserBookmarkRepositoryTest" --tests "io.legado.app.data.cookie.CookieVaultRepositoryTest" --tests "io.legado.app.help.http.CookieManagerSecurityTest" --tests "io.legado.app.worker.BookSourceHealthCheckProcessorTest" --tests "io.legado.app.worker.BookSourceHealthWorkerTest" --no-daemon --console=plain`; `.\gradlew.bat :app:compileAppDebugAndroidTestKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=io.legado.app.integration.SourceBrowserIntegrationTest" --no-daemon --console=plain`
- Ket qua: BUILD SUCCESSFUL; unit tests PASS; androidTest compile PASS; emulator smoke PASS.
- Bang chung: `reports/P04-T07-BROWSER-SOURCE-COOKIE-REGRESSION.md`; `app/build/outputs/androidTest-results/connected/debug/flavors/app/TEST-emulator-5554 - 14-_app-app.xml`; `app/build/outputs/androidTest-results/connected/debug/flavors/app/emulator-5554 - 14/testlog/test-results.log`
- Rui ro/cong viec con lai: Browser download listener va SSL block co smoke runtime nhung chua co test tach rieng; phase 05 se tiep tuc source-health dashboard.
- Task tiep theo: P05.T01.

## 2026-07-30 05:16 - P05.T01 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Tao source-health run/stage contracts va Room schema rieng, giu summary cu va khong sua du lieu source.
- Thoi gian bat dau/ket thuc: 2026-07-30 05:16 / dang thuc hien
- File thay doi: `domain/sourcehealth/**`, `data/entities/sourcehealth/**`, `data/dao/SourceCheckDao.kt`, `data/repository/sourcehealth/SourceCheckRepository.kt`, Room database/migration, DI, processor, tests va report.
- Tom tat trien khai: Bat dau them `SourceCheckRun`, `SourceCheckStageResult`, profile/status enums, transactional DAO insert/finish, schema 108 va noi shallow worker ghi stage `probe`.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P05-T01-SOURCE-HEALTH-SCHEMA.md`
- Rui ro/cong viec con lai: `CheckSourceService` deep legacy van ton tai va se duoc hop nhat o task sau.
- Task tiep theo: P05.T02 sau khi schema/test pass.

## 2026-07-30 06:02 - P05.T01 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Tao source-health run/stage contracts va Room schema rieng, giu summary cu va khong sua du lieu source.
- Thoi gian bat dau/ket thuc: 2026-07-30 05:16 / 2026-07-30 06:02
- File thay doi: `SourceCheckModels.kt`, `SourceCheckRunEntity.kt`, `SourceCheckStageResultEntity.kt`, `SourceCheckDao.kt`, `SourceCheckRepository.kt`, `AppDatabase.kt`, `DatabaseMigrations.kt`, `appDatabaseModule.kt`, `appModule.kt`, `BookSourceHealthCheckProcessor.kt`, tests, schema `108.json`, `reports/P05-T01-SOURCE-HEALTH-SCHEMA.md`, task matrix va plan log.
- Tom tat trien khai: Room 108 them `source_check_runs` va `source_check_stage_results`; processor moi source ghi mot `QUICK` run + stage `probe`; repository cap nhat latest summary `book_source_health` trong khi source entity bat bien.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.sourcehealth.SourceCheckRepositoryTest" --tests "io.legado.app.worker.BookSourceHealthCheckProcessorTest" --tests "io.legado.app.worker.BookSourceHealthWorkerTest" --tests "io.legado.app.domain.usecase.ProbeBookSourceUseCaseTest" --tests "io.legado.app.domain.model.BookSourceHealthModelsTest" --no-daemon --console=plain`; `.\gradlew.bat :app:compileAppDebugAndroidTestKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=io.legado.app.sourcehealth.SourceCheckMigrationTest" --no-daemon --console=plain`
- Ket qua: BUILD SUCCESSFUL; focused unit tests PASS; androidTest compile PASS; `SourceCheckMigrationTest` 1 test PASS tren `emulator-5554 - 14`.
- Bang chung: `reports/P05-T01-SOURCE-HEALTH-SCHEMA.md`; `app/schemas/io.legado.app.data.AppDatabase/108.json`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.sourcehealth.SourceCheckRepositoryTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.worker.BookSourceHealthCheckProcessorTest.xml`; `app/build/outputs/androidTest-results/connected/debug/flavors/app/TEST-emulator-5554 - 14-_app-app.xml`
- Rui ro/cong viec con lai: Deep `CheckSourceService` van la service cu; Book-only shallow `probe` moi duoc ghi history, RSS/VBook va stage chi tiet thuoc P05.T02-P05.T04; retention cleanup thuoc P05.T06.
- Task tiep theo: P05.T02.

## 2026-07-30 07:02 - P05.T02 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Tao probe adapters rieng cho Book/RSS/VBook, tra stage evidence co latency/status va giu capability thieu o `SKIPPED`.
- Thoi gian bat dau/ket thuc: 2026-07-30 07:02 / dang thuc hien
- File thay doi: `domain/sourcehealth/SourceCheckModels.kt`, `domain/gateway/SourceHealthProbeGateways.kt`, `data/repository/sourcehealth/*ProbeRepository.kt`, `SourceCheckStageRunner.kt`, DI, tests va report.
- Tom tat trien khai: Bat dau them contract evidence khong phu thuoc DB, stage runner redacted diagnostic va ba adapter Book/RSS/VBook theo matrix P05.T02.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P05-T02-PROBE-ADAPTERS.md`
- Rui ro/cong viec con lai: Can giu worker cu bat bien cho den P05.T03 engine map adapter evidence vao run/stage.
- Task tiep theo: P05.T02.

## 2026-07-30 07:22 - P05.T02 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Tao probe adapters rieng cho Book/RSS/VBook, tra stage evidence co latency/status va giu capability thieu o `SKIPPED`.
- Thoi gian bat dau/ket thuc: 2026-07-30 07:02 / 2026-07-30 07:22
- File thay doi: `app/src/main/java/io/legado/app/domain/sourcehealth/SourceCheckModels.kt`, `app/src/main/java/io/legado/app/domain/gateway/SourceHealthProbeGateways.kt`, `app/src/main/java/io/legado/app/data/repository/sourcehealth/SourceCheckStageRunner.kt`, `BookSourceHealthProbeRepository.kt`, `RssSourceHealthProbeRepository.kt`, `VbookSourceHealthProbeRepository.kt`, `appModule.kt`, `SourceHealthProbeRepositoriesTest.kt`, `reports/P05-T02-PROBE-ADAPTERS.md`, task matrix va plan log.
- Tom tat trien khai: Book adapter chay `reachability/search/explore/detail/toc/content/media`; RSS chay `feed/list/article/content`; VBook dung manifest/profile de chay `manifest/scripts/home/search/detail/toc/content/track`; optional rule/capability thieu duoc `SKIPPED`; diagnostic duoc redact/cat gon.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.sourcehealth.SourceHealthProbeRepositoriesTest" --tests "io.legado.app.data.repository.sourcehealth.SourceCheckRepositoryTest" --tests "io.legado.app.worker.BookSourceHealthCheckProcessorTest" --tests "io.legado.app.worker.BookSourceHealthWorkerTest" --tests "io.legado.app.domain.usecase.ProbeBookSourceUseCaseTest" --tests "io.legado.app.domain.model.BookSourceHealthModelsTest" --tests "io.legado.app.help.vbook.VbookPluginInspectorTest" --tests "io.legado.app.help.vbook.VbookMediaParserTest" --no-daemon --console=plain`
- Ket qua: BUILD SUCCESSFUL; focused source-health/VBook unit tests PASS.
- Bang chung: `reports/P05-T02-PROBE-ADAPTERS.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.sourcehealth.SourceHealthProbeRepositoriesTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.sourcehealth.SourceCheckRepositoryTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.vbook.VbookPluginInspectorTest.xml`
- Rui ro/cong viec con lai: Adapter evidence chua duoc engine chung aggregate vao latest summary; P05.T03 se lam `SourceCheckEngine`, classification va profile Quick/Standard/Full.
- Task tiep theo: P05.T03.

## 2026-07-30 07:24 - P05.T03 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Tao `SourceCheckEngine` chung cho Quick/Standard/Full, aggregate adapter evidence va phan loai loi.
- Thoi gian bat dau/ket thuc: 2026-07-30 07:24 / dang thuc hien
- File thay doi: `SourceCheckClassifier.kt`, `BookSourceHealthModels.kt`, `SourceCheckEngine.kt`, `SourceCheckRepository.kt`, worker processor, UI status labels, tests va report.
- Tom tat trien khai: Bat dau mo rong health status, tao classifier deterministic va engine dung adapter Book/RSS/VBook.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P05-T03-SOURCE-CHECK-ENGINE.md`
- Rui ro/cong viec con lai: Can giu scheduled worker hien co khong doi public behavior trong khi chuyen sang engine.
- Task tiep theo: P05.T03.

## 2026-07-30 07:57 - P05.T03 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Tao `SourceCheckEngine` chung cho Quick/Standard/Full, aggregate adapter evidence va phan loai loi.
- Thoi gian bat dau/ket thuc: 2026-07-30 07:24 / 2026-07-30 07:57
- File thay doi: `app/src/main/java/io/legado/app/domain/sourcehealth/SourceCheckClassifier.kt`, `BookSourceHealthModels.kt`, `SourceCheckEngine.kt`, `SourceCheckRepository.kt`, `BookSourceHealthCheckProcessor.kt`, `BookSourceHealthDao.kt`, Browser/SourceHealth labels, strings, tests, report, task matrix va plan log.
- Tom tat trien khai: Engine chung chay Book/VBook/RSS adapter, tao run khong stage `probe` gia, aggregate status `HEALTHY/DEGRADED/AUTH/CAPTCHA/RATE/NETWORK/TLS/RULE/EMPTY/MEDIA/UNSUPPORTED/STALE/OFFLINE`, persist summary va xu ly cancellation/failure deterministic.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.sourcehealth.SourceCheckEngineTest" --tests "io.legado.app.domain.sourcehealth.SourceCheckClassifierTest" --tests "io.legado.app.domain.model.BookSourceHealthModelsTest" --tests "io.legado.app.worker.BookSourceHealthCheckProcessorTest" --tests "io.legado.app.worker.BookSourceHealthWorkerTest" --no-daemon --console=plain`
- Ket qua: BUILD SUCCESSFUL; focused engine/classifier/worker tests PASS.
- Bang chung: `reports/P05-T03-SOURCE-CHECK-ENGINE.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.sourcehealth.SourceCheckEngineTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.sourcehealth.SourceCheckClassifierTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.worker.BookSourceHealthCheckProcessorTest.xml`
- Rui ro/cong viec con lai: RSS engine path san sang nhung scheduler/foreground orchestration thuoc P05.T04; deprecated Browser `ListItem` warning con ton tai tu UI cu.
- Task tiep theo: P05.T04.

## 2026-07-30 08:10 - P05.T04 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Dong worker, concurrency, backoff va foreground run cho source health.
- Thoi gian bat dau/ket thuc: 2026-07-30 08:10 / dang thuc hien
- File thay doi: `CheckSource.kt`, `CheckSourceSessionStore.kt`, `CheckSourceService.kt`, `BookSourceHealthCheckProcessor.kt`, `BookSourceHealthWorker.kt`, `SourceCheckEngine.kt`, tests va report.
- Tom tat trien khai: Bat dau hop nhat session store, pause/resume/cancel notification, target work, domain backoff va timeout handling.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P05-T04-WORKER-CONCURRENCY-FOREGROUND.md`
- Rui ro/cong viec con lai: Con can xac nhan tat ca regression tests o service/worker/engine.
- Task tiep theo: Hoan tat P05.T04.

## 2026-07-30 09:49 - P05.T04 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Dong worker, concurrency, backoff va foreground run cho source health.
- Thoi gian bat dau/ket thuc: 2026-07-30 08:10 / 2026-07-30 09:49
- File thay doi: `CheckSource.kt`, `CheckSourceSessionStore.kt`, `CheckSourceService.kt`, `BookSourceHealthCheckProcessor.kt`, `BookSourceHealthWorker.kt`, `SourceCheckEngine.kt`, tests va report.
- Tom tat trien khai: Service giu session qua restart, co pause/resume/cancel trong notification; worker co periodic/manual exact-target path; processor group theo domain + backoff; engine timeout/cancellation duoc classify va luu history dung.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; focused unit suite qua `CheckSourceSessionStoreTest`, `CheckSourceServiceTest`, `SourceCheckEngineTest`, `BookSourceHealthCheckProcessorTest`, `BookSourceHealthWorkerTest`.
- Ket qua: BUILD SUCCESSFUL; 12 tests PASS, 0 failures/errors/skipped.
- Bang chung: `reports/P05-T04-WORKER-CONCURRENCY-FOREGROUND.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.service.CheckSourceSessionStoreTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.service.CheckSourceServiceTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.sourcehealth.SourceCheckEngineTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.worker.BookSourceHealthCheckProcessorTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.worker.BookSourceHealthWorkerTest.xml`
- Rui ro/cong viec con lai: Con the tach concurrency doc lap hon neu can dua ve config nguoi dung sau nay.
- Task tiep theo: P05.T05.

## 2026-07-30 10:02 - P05.T05 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien dashboard source health va noi Browser/source list vao cung history/action surface.
- Thoi gian bat dau/ket thuc: 2026-07-30 10:02 / dang thuc hien
- File thay doi: `BookSourceHealthRepository.kt`, `BookSourceHealthModels.kt`, `SourceHealthContract.kt`, `SourceHealthViewModel.kt`, `SourceHealthScreen.kt`, Main navigation, Browser/RSS/source integrations, strings, tests va report.
- Tom tat trien khai: Bat dau nang dashboard tu list co ban len summary/filter/search/detail/action; ket noi target source tu Browser va RSS source manage.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P05-T05-SOURCE-HEALTH-DASHBOARD.md`
- Rui ro/cong viec con lai: Can giu compatibility voi legacy BookSourceActivity/RSS Activity, khong auto disable source.
- Task tiep theo: Hoan tat P05.T05.

## 2026-07-30 11:55 - P05.T05 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien dashboard source health va noi Browser/source list vao cung history/action surface.
- Thoi gian bat dau/ket thuc: 2026-07-30 10:02 / 2026-07-30 11:55
- File thay doi: `BookSourceHealthRepository.kt`, `BookSourceHealthModels.kt`, `SourceHealthContract.kt`, `SourceHealthViewModel.kt`, `SourceHealthScreen.kt`, `MainNavKey.kt`, `MainNavGraph.kt`, `MainNavigator.kt`, `MainIntent.kt`, `values/strings.xml`, `values-vi/strings.xml`, tests va report.
- Tom tat trien khai: Dashboard gio co summary/search/filter/recent runs/source sheet; Browser mo source health theo source dang xem; sheet co action check/open browser/edit; edit source van di qua legacy Activity de giu tuong thich Legado/VBook.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.ui.main.MainNavigatorTest" --tests "io.legado.app.ui.main.MainIntentTest" --tests "io.legado.app.ui.book.source.health.SourceHealthStateTest" --tests "io.legado.app.data.repository.BookSourceHealthRepositoryTest" --no-daemon --console=plain`
- Ket qua: BUILD SUCCESSFUL; focused unit tests PASS.
- Bang chung: `reports/P05-T05-SOURCE-HEALTH-DASHBOARD.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.main.MainNavigatorTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.main.MainIntentTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.book.source.health.SourceHealthStateTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.BookSourceHealthRepositoryTest.xml`
- Rui ro/cong viec con lai: P05.T06 van can retention/cleanup, P05.T07 van can mo rong test suite source health.
- Task tiep theo: P05.T06.

## 2026-07-30 12:20 - P05.T06 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien retention va cleanup cho lich su source health.
- Thoi gian bat dau/ket thuc: 2026-07-30 12:20 / dang thuc hien
- File thay doi: `SourceCheckRetentionPolicy.kt`, `SourceCheckDao.kt`, `BookSourceHealthDao.kt`, `SourceCheckRepository.kt`, `SourceHealthRetentionWorker.kt`, `App.kt`, `SourceHelp.kt`, tests va report.
- Tom tat trien khai: Bat dau chot policy 30 ngay/100 run moi source, cleanup khong xoa active/latest run, worker dinh ky va hook xoa source.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P05-T06-HISTORY-RETENTION-CLEANUP.md`
- Rui ro/cong viec con lai: Can xac nhan focused unit tests va compile evidence.
- Task tiep theo: Hoan tat P05.T06.

## 2026-07-30 12:20 - P05.T06 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien retention va cleanup cho lich su source health.
- Thoi gian bat dau/ket thuc: 2026-07-30 12:20 / 2026-07-30 12:20
- File thay doi: `SourceCheckRetentionPolicy.kt`, `SourceCheckDao.kt`, `BookSourceHealthDao.kt`, `SourceCheckRepository.kt`, `SourceHealthRetentionWorker.kt`, `App.kt`, `SourceHelp.kt`, `SourceCheckRepositoryTest.kt`, report, task matrix va plan log.
- Tom tat trien khai: Retention mac dinh giu 30 ngay/100 run moi source, khong xoa run `RUNNING`, luon giu latest finished run, xoa run/stage/summary khi source bi xoa, va schedule cleanup worker theo `sourceDailyHealth`.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.sourcehealth.SourceCheckRepositoryTest" --tests "io.legado.app.worker.BookSourceHealthWorkerTest" --no-daemon --console=plain`
- Ket qua: BUILD SUCCESSFUL; focused unit tests PASS.
- Bang chung: `reports/P05-T06-HISTORY-RETENTION-CLEANUP.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.sourcehealth.SourceCheckRepositoryTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.worker.BookSourceHealthWorkerTest.xml`
- Rui ro/cong viec con lai: P05.T07 can hop nhat matrix test suite theo source type/profile/status va chay regression rong hon.
- Task tiep theo: P05.T07.

## 2026-07-30 12:26 - P05.T07 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Dong source health test suite theo gate Phase 05.
- Thoi gian bat dau/ket thuc: 2026-07-30 12:26 / dang thuc hien
- File thay doi: `SourceCheckClassifierTest.kt`, `SourceHealthProbeRepositoriesTest.kt`, `SourceCheckEngineTest.kt`, report, task matrix va plan log.
- Tom tat trien khai: Bat dau bo sung coverage cho classification, profile depth Book/RSS/VBook va source immutability.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P05-T07-SOURCE-HEALTH-TEST-SUITE.md`
- Rui ro/cong viec con lai: Can chay focused source-health suite va ghi matrix evidence.
- Task tiep theo: Hoan tat P05.T07.

## 2026-07-30 12:26 - P05.T07 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Dong source health test suite theo gate Phase 05.
- Thoi gian bat dau/ket thuc: 2026-07-30 12:26 / 2026-07-30 12:26
- File thay doi: `SourceCheckClassifierTest.kt`, `SourceHealthProbeRepositoriesTest.kt`, `SourceCheckEngineTest.kt`, `P05-T07-SOURCE-HEALTH-TEST-SUITE.md`, task matrix va plan log.
- Tom tat trien khai: Them matrix classifier cho offline/auth/captcha/rate/DNS/TLS/rule/empty/media/stale/http, them profile depth tests cho Book/RSS/VBook, them source immutability test cho engine, va lap matrix evidence dong Phase 05.
- Lenh kiem tra: Focused unit suite 12 class qua `:app:testAppDebugUnitTest`; thu chay lai `SourceCheckMigrationTest` Android nhung lan chay vuot 5 phut nen khong tinh evidence moi.
- Ket qua: BUILD SUCCESSFUL; 33 focused unit tests PASS; Android migration artifact co san PASS.
- Bang chung: `reports/P05-T07-SOURCE-HEALTH-TEST-SUITE.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.sourcehealth.SourceCheckClassifierTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.sourcehealth.SourceHealthProbeRepositoriesTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.sourcehealth.SourceCheckEngineTest.xml`; `app/build/outputs/androidTest-results/connected/debug/flavors/app/TEST-emulator-5554 - 14-_app-app.xml`
- Rui ro/cong viec con lai: Phase 06 se tiep tuc WebService identity/pairing/config API; connected test rerun can moi truong thiet bi on dinh hon neu muon evidence moi hon artifact hien co.
- Task tiep theo: P06.T01.

## 2026-07-30 12:30 - P06.T01 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Them identity/port rieng va pairing/session cho DrDucBook WebService v2, giu route legacy khong doi.
- Thoi gian bat dau/ket thuc: 2026-07-30 12:30 / dang thuc hien
- File thay doi: `WebServiceModels.kt`, `WebServiceIdentityStore.kt`, `KtorServer.kt`, `WebService.kt`, `OtherConfig.kt`, `MyViewModel.kt`, `MyScreen.kt`, strings, tests va report.
- Tom tat trien khai: Bat dau tao port policy 1124/1125, `/api/v2/instance`, broker one-time code, bearer session, revoke va UI pairing trong block WebService.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P06-T01-WEB-SERVICE-IDENTITY-PAIRING.md`
- Rui ro/cong viec con lai: Can compile/test va dam bao route legacy van khong doi.
- Task tiep theo: Hoan tat P06.T01.

## 2026-07-30 13:03 - P06.T01 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Them identity/port rieng va pairing/session cho DrDucBook WebService v2, giu route legacy khong doi.
- Thoi gian bat dau/ket thuc: 2026-07-30 12:30 / 2026-07-30 13:03
- File thay doi: `WebServiceModels.kt`, `WebServiceIdentityStore.kt`, `KtorServer.kt`, `WebService.kt`, `PreferKey.kt`, `OtherConfig.kt`, `MyViewModel.kt`, `MyScreen.kt`, `values/strings.xml`, `values-vi/strings.xml`, `WebServiceModelsTest.kt`, report, task matrix va plan log.
- Tom tat trien khai: WebService mac dinh dung HTTP `1124`/WS `1125`, tu suggest cap port trong, co `/api/v2/instance`, one-time pairing code, bearer session expire/revoke, UI tao/copy ma pairing trong block WebService, va revoke session khi service dung.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.webservice.WebServiceModelsTest" --no-daemon --console=plain`
- Ket qua: BUILD SUCCESSFUL; `WebServiceModelsTest` 3 tests PASS.
- Bang chung: `reports/P06-T01-WEB-SERVICE-IDENTITY-PAIRING.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.webservice.WebServiceModelsTest.xml`
- Rui ro/cong viec con lai: Chua co Ktor route integration test vi project chua co ktor test host dependency; P06.T02 se dung bearer session nay cho policy/config API.
- Task tiep theo: P06.T02.

## 2026-07-30 13:44 - P06.T02 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Them WebService policy/config API cho web UI dieu khien Export va Dich tu dong; background se duoc mo rong o P06.T03.
- Thoi gian bat dau/ket thuc: 2026-07-30 13:44 / dang thuc hien
- File thay doi: `WebServiceModels.kt`, `WebServicePolicyStore.kt`, `KtorServer.kt`, `PreferKey.kt`, `WebServiceModelsTest.kt`, `modules/web/src/api/webService.ts`, `modules/web/src/store/webServiceStore.ts`, web exports va report.
- Tom tat trien khai: Bat dau chot policy schema, ETag/revision, same-origin guard, bearer-session requirement, CORS cho `Authorization`/`If-Match`/`PATCH`, va client/store rieng cho API v2.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P06-T02-WEB-POLICY-CONFIG-API.md`
- Rui ro/cong viec con lai: Can compile/test va type-check web.
- Task tiep theo: Hoan tat P06.T02.

## 2026-07-30 13:44 - P06.T02 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Them WebService policy/config API cho web UI dieu khien Export va Dich tu dong; background se duoc mo rong o P06.T03.
- Thoi gian bat dau/ket thuc: 2026-07-30 13:44 / 2026-07-30 13:44
- File thay doi: `WebServiceModels.kt`, `WebServicePolicyStore.kt`, `KtorServer.kt`, `PreferKey.kt`, `WebServiceModelsTest.kt`, `modules/web/src/api/webService.ts`, `modules/web/src/store/webServiceStore.ts`, `modules/web/src/api/index.ts`, `modules/web/src/store/index.ts`, report, task matrix va plan log.
- Tom tat trien khai: Policy v2 co `exportEnabled`, `autoTranslationEnabled`, `updatedAt`, `revision`, header `ETag`; `PATCH` yeu cau `If-Match` va tra `428`/`409` khi thieu/cu; `GET/PATCH/POST reset` deu can bearer session va same-origin; web co API client/store v2 tach khoi legacy interceptor.
- Lenh kiem tra: `.\gradlew.bat :app:clean --no-daemon --console=plain`; `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.webservice.WebServiceModelsTest" --no-daemon --console=plain`; `pnpm type-check` trong `modules/web`.
- Ket qua: BUILD SUCCESSFUL; `WebServiceModelsTest` 5 tests PASS; web type-check PASS.
- Bang chung: `reports/P06-T02-WEB-POLICY-CONFIG-API.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.webservice.WebServiceModelsTest.xml`; `modules/web` `vue-tsc --build --force` PASS.
- Rui ro/cong viec con lai: Chua co Ktor integration test host; P06.T03 se them background storage va UI web dung policy/store nay.
- Task tiep theo: P06.T03.

## 2026-07-30 13:51 - P06.T03 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Them storage rieng va UI web cho background WebService, doc lap voi Android appearance.
- Thoi gian bat dau/ket thuc: 2026-07-30 13:51 / dang thuc hien
- File thay doi: `WebServiceModels.kt`, `WebServicePolicyStore.kt`, `WebServiceBackgroundStore.kt`, `KtorServer.kt`, `modules/web/src/App.vue`, `modules/web/src/views/WebServiceSettings.vue`, web API/store/router/CSS va tests.
- Tom tat trien khai: Bat dau mo rong policy background, them private bitmap asset store, route upload/get/delete v2, va panel web cho pairing/toggle/background.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P06-T03-WEB-BACKGROUND-STORAGE-UI.md`
- Rui ro/cong viec con lai: Can compile Android, focused model tests, web type-check/build va ghi report.
- Task tiep theo: Hoan tat P06.T03.

## 2026-07-30 14:30 - P06.T03 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Them storage rieng va UI web cho background WebService, doc lap voi Android appearance.
- Thoi gian bat dau/ket thuc: 2026-07-30 13:51 / 2026-07-30 14:30
- File thay doi: `WebServiceModels.kt`, `WebServicePolicyStore.kt`, `WebServiceBackgroundStore.kt`, `KtorServer.kt`, `WebServiceModelsTest.kt`, `App.vue`, `web-service.css`, `WebServiceSettings.vue`, `webService.ts`, `webServiceStore.ts`, `router/index.ts`, `BookShelf.vue`, `ToolBar.vue`, web dist/assets, report, task matrix va plan log.
- Tom tat trien khai: Policy co background asset/style, backend luu anh private theo SHA-256 PNG, route upload/get/delete dung bearer session + same-origin + `If-Match`, web settings co pairing/toggle/background controls, shell web ap background bang object URL va CSS dim/blur.
- Lenh kiem tra: `pnpm type-check`; `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.webservice.WebServiceModelsTest" --no-daemon --console=plain`; `pnpm build`; chay lai `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain` sau timeout assemble.
- Ket qua: BUILD SUCCESSFUL; `WebServiceModelsTest` 7 tests PASS; web type-check/build PASS; `:app:assembleAppDebug` vuot 15 phut nen khong tinh la evidence.
- Bang chung: `reports/P06-T03-WEB-BACKGROUND-STORAGE-UI.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.webservice.WebServiceModelsTest.xml`; `modules/web/dist/assets/WebServiceSettings-DZCEFlse.js`; `app/src/main/assets/web/vue/assets/WebServiceSettings-DZCEFlse.js`.
- Rui ro/cong viec con lai: Chua co Ktor multipart integration test va Playwright visual/contrast evidence; P06.T04 tiep tuc noi Export gate vao pipeline.
- Task tiep theo: P06.T04.

## 2026-07-30 15:15 - P06.T04 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Noi `exportEnabled` vao API/UI export web de khi tat thi backend tra `FEATURE_DISABLED`.
- Thoi gian bat dau/ket thuc: 2026-07-30 14:40 / 2026-07-30 15:15
- File thay doi: `WebServiceModels.kt`, `WebServiceExportController.kt`, `KtorServer.kt`, `WebServiceModelsTest.kt`, `modules/web/src/api/webService.ts`, `modules/web/src/components/SourceList.vue`, `modules/web/src/views/WebServiceSettings.vue`, `modules/web/dist/**`, `app/src/main/assets/web/vue/**`, report, task matrix va plan log.
- Tom tat trien khai: Them request contract export, controller export v2, gate same-origin + bearer session + `exportEnabled`, source/RSS JSON export qua backend, bookshelf JSON, chapter TXT va book TXT. Body rong dung request mac dinh, JSON hong tra `EXPORT_REQUEST_INVALID`.
- Lenh kiem tra: `pnpm type-check`; `pnpm build`; `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.webservice.WebServiceModelsTest" --no-daemon --console=plain`.
- Ket qua: PASS; web build da sync vao Android assets; `WebServiceModelsTest` 8 tests PASS.
- Bang chung: `reports/P06-T04-EXPORT-FEATURE-GATE.md`
- Rui ro/cong viec con lai: Chua co Ktor integration test cho route 403/auth; EPUB/PDF/HTML/CBZ can job/temp-file broker rieng vi native export hien phu thuoc Android document tree URI/foreground service; book TXT lon can stream/progress/cancel.
- Task tiep theo: P06.T05.

## 2026-07-30 15:40 - P06.T05 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Noi `autoTranslationEnabled` vao API/UI job dich chuong web, dung cau hinh dich cua app va khong dua credential ra trinh duyet.
- Thoi gian bat dau/ket thuc: 2026-07-30 15:15 / 2026-07-30 15:40
- File thay doi: `WebServiceModels.kt`, `WebServiceTranslationJobController.kt`, `KtorServer.kt`, `WebServiceModelsTest.kt`, `modules/web/src/api/webService.ts`, `modules/web/src/views/BookChapter.vue`, `modules/web/src/views/WebServiceSettings.vue`, `modules/web/dist/**`, `app/src/main/assets/web/vue/**`, report, task matrix va plan log.
- Tom tat trien khai: Them API v2 `POST/GET/DELETE /api/v2/translation/jobs`, gate same-origin + bearer session + `autoTranslationEnabled`, runtime job controller dung `TranslationManager`, huy job khi tat policy, web reader co nut `Dich/Huy`, polling tien do va ap noi dung dich vao chuong hien tai.
- Lenh kiem tra: `pnpm type-check`; `pnpm build`; `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.webservice.WebServiceModelsTest" --no-daemon --console=plain`.
- Ket qua: PASS; web build da sync vao Android assets; `WebServiceModelsTest` 9 tests PASS.
- Bang chung: `reports/P06-T05-AUTO-TRANSLATION-JOBS.md`
- Rui ro/cong viec con lai: Job runtime-only; chua co Ktor integration test auth/403/cancel; chua co realtime WebSocket/SSE; dich hang loat nhieu chuong can queue rieng.
- Task tiep theo: P06.T06.

## 2026-07-30 16:05 - P06.T06 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Khoa contract HTTP/WebSocket legacy de web cu va cong cu Legado/VBook van ket noi duoc voi DrDucBook.
- Thoi gian bat dau/ket thuc: 2026-07-30 15:40 / 2026-07-30 16:05
- File thay doi: `WebServiceModels.kt`, `KtorServer.kt`, `WebServiceModelsTest.kt`, report, task matrix va plan log.
- Tom tat trien khai: Them `WebServiceLegacyContract` cho 14 POST, 12 GET, 3 WebSocket route legacy va shape `ReturnData`; KtorServer dung constants nay; test khoa route quan trong va xac nhan API v2 khong trung path legacy.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.webservice.WebServiceModelsTest" --no-daemon --console=plain`.
- Ket qua: PASS; `WebServiceModelsTest` 10 tests PASS.
- Bang chung: `reports/P06-T06-LEGACY-WEB-COMPATIBILITY.md`
- Rui ro/cong viec con lai: Chua co Ktor/WebSocket integration test thuc te; P06.T07 se dong QA web va packaged assets.
- Task tiep theo: P06.T07.

## 2026-07-30 16:05 - P06.T07 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Dong QA web service: type-check/build, asset sync va responsive smoke test cho web packaged assets.
- Thoi gian bat dau/ket thuc: 2026-07-30 16:05 / dang thuc hien
- File thay doi du kien: `modules/web/dist/**`, `app/src/main/assets/web/vue/**`, report, task matrix va plan log.
- Tom tat trien khai: Chuan bi chay type/build va browser smoke neu cong cu local browser kha dung.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P06-T07-WEB-QA.md`
- Rui ro/cong viec con lai: Co the thieu backend Android WebService that de test pairing/live API; neu vay se ghi ro pham vi smoke.
- Task tiep theo: Hoan tat P06.T07.

## 2026-07-30 17:02 - P06.T07 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Dong QA web service: type-check/build, asset sync va responsive smoke test cho web packaged assets.
- Thoi gian bat dau/ket thuc: 2026-07-30 16:05 / 2026-07-30 17:02
- File thay doi: `modules/web/src/api/index.ts`, `modules/web/src/views/WebServiceSettings.vue`, `modules/web/dist/**`, `app/src/main/assets/web/vue/**`, `reports/P06-T07-WEB-QA.md`, `reports/artifacts/P06-T07-webservice-*.png`, `reports/artifacts/P06-T07-webservice-smoke.json`, task matrix va plan log.
- Tom tat trien khai: Sua circular import web runtime, them fallback instance text khi backend WebService chua san sang, sua copy va CSS control background, build/sync assets, smoke desktop/mobile khong loi console, khong overflow va khong con text `undefined`.
- Lenh kiem tra: `pnpm type-check`; `pnpm build`; `node .codex-tmp/p06-web-qa/smoke.mjs`; `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`.
- Ket qua: PASS; smoke desktop 1365x900 va mobile 390x844 deu co 3 panel, header `Dang cho WebService tu app`, khong overflow ngang, 0 console/page error; Android Kotlin compile BUILD SUCCESSFUL.
- Bang chung: `reports/P06-T07-WEB-QA.md`; `reports/artifacts/P06-T07-webservice-desktop-1365x900.png`; `reports/artifacts/P06-T07-webservice-mobile-390x844.png`; `reports/artifacts/P06-T07-webservice-smoke.json`; `app/src/main/assets/web/vue/assets/WebServiceSettings-D00wCIik.js`.
- Rui ro/cong viec con lai: Chua chay live pairing/export/translation voi Android WebService backend that; can dua vao Phase 11 E2E/Ktor-WebSocket integration.
- Task tiep theo: P07.T01.

## 2026-07-30 17:05 - P07.T01 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Tao repository sang tac/ebook co version, atomic write, asset store rieng va migration tu repository hien tai.
- Thoi gian bat dau/ket thuc: 2026-07-30 17:05 / dang thuc hien
- File thay doi du kien: `domain/model/**authoring**`, `domain/gateway/AuthoringProjectGateway.kt`, `data/repository/AuthoringProjectRepository.kt`, storage/asset helpers, DI, focused tests, report, task matrix va plan log.
- Tom tat trien khai: Bat dau kiem ke model/repository/UI authoring hien co de bo sung lop luu tru an toan ma khong pha UI Workspace/Sang tac/Ebook da co.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P07-T01-AUTHORING-REPOSITORY.md`
- Rui ro/cong viec con lai: Can giu compatibility voi document JSON hien co va cac usecase export/validate.
- Task tiep theo: Hoan tat P07.T01.

## 2026-07-30 18:35 - P07.T01 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Tao repository sang tac/ebook co version, atomic write, asset store rieng va migration tu repository hien tai.
- Thoi gian bat dau/ket thuc: 2026-07-30 17:05 / 2026-07-30 18:35
- File thay doi: `AuthoringProjectRepository.kt`, `AuthoringProjectFileStore.kt`, `appModule.kt`, `AuthoringProjectFileStoreTest.kt`, `reports/P07-T01-AUTHORING-REPOSITORY.md`, task matrix va plan log.
- Tom tat trien khai: Them storage manifest schema v1, content hash, atomic temp-write/fsync/move, per-project lock, content-addressed asset import/index, legacy raw JSON migration va discriminator `blockType` cho Ebook block polymorphic.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.AuthoringProjectFileStoreTest" --no-daemon --console=plain`; cum Authoring/Ebook/Workspace focused tests; `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`.
- Ket qua: PASS; `AuthoringProjectFileStoreTest` 5 tests PASS; cum lien quan 12 tests PASS; Android Kotlin compile BUILD SUCCESSFUL.
- Bang chung: `reports/P07-T01-AUTHORING-REPOSITORY.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.AuthoringProjectFileStoreTest.xml`.
- Rui ro/cong viec con lai: P07.T04 can quarantine/recovery UI cho manifest corrupt/hash mismatch; P07.T05 noi asset index vao Supabase/Google Drive backup.
- Task tiep theo: P07.T02.

## 2026-07-30 18:38 - P07.T02 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien module Sang tac tren nen repository P07.T01: CRUD project/chapter, edit text, autosave/undo/redo, search/replace va hooks AI an toan.
- Thoi gian bat dau/ket thuc: 2026-07-30 18:38 / dang thuc hien
- File thay doi du kien: `ui/authoring/writing/**`, `domain/usecase/AuthoringProjectUseCase.kt`, focused tests, report, task matrix va plan log.
- Tom tat trien khai: Bat dau doc contract/viewmodel/screen hien co va ap dung guideline Compose/MVI cua repo.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P07-T02-WRITING-MODULE.md`
- Rui ro/cong viec con lai: Can giu man hinh stateless va khong dua logic business vao composable.
- Task tiep theo: Hoan tat P07.T02.

## 2026-07-30 19:05 - P07.T02 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien module Sang tac tren nen repository P07.T01: CRUD project/chapter, edit text, autosave/undo/redo, search/replace va hooks AI an toan.
- Thoi gian bat dau/ket thuc: 2026-07-30 18:38 / 2026-07-30 19:05
- File thay doi: `WritingContract.kt`, `WritingViewModel.kt`, `WritingScreen.kt`, `WritingEditOperations.kt`, `AuthoringProjectUseCase.kt`, `strings.xml`, `AuthoringProjectUseCaseTest.kt`, `WritingEditOperationsTest.kt`, `reports/P07-T02-WRITING-MODULE.md`, task matrix va plan log.
- Tom tat trien khai: Them duplicate project/chapter, undo/redo snapshot, autosave debounce/flush on stop, literal search/replace, image asset insert vao chapter marker, UI controls va tests. AI suggestion van tach rieng cho den khi nguoi dung bam Apply.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.usecase.AuthoringProjectUseCaseTest" --tests "io.legado.app.ui.authoring.writing.WritingEditOperationsTest" --no-daemon --console=plain`; `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`.
- Ket qua: PASS; `AuthoringProjectUseCaseTest` 4 tests PASS; `WritingEditOperationsTest` 3 tests PASS; Android Kotlin compile BUILD SUCCESSFUL.
- Bang chung: `reports/P07-T02-WRITING-MODULE.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.usecase.AuthoringProjectUseCaseTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.authoring.writing.WritingEditOperationsTest.xml`.
- Rui ro/cong viec con lai: Chua co device screenshot/process recreation cho Writing; P07.T06 se dong UI smoke va P07.T04 se persist history/recovery.
- Task tiep theo: P07.T03.

## 2026-07-30 19:08 - P07.T03 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien Ebook editor, preview va export: metadata/cover/TOC/layout/media/preflight, EPUB3/PDF/TXT deterministic.
- Thoi gian bat dau/ket thuc: 2026-07-30 19:08 / dang thuc hien
- File thay doi du kien: `ui/authoring/ebook/**`, `domain/usecase/ExportAuthoringProjectUseCase.kt`, `service/export/**`, tests, report, task matrix va plan log.
- Tom tat trien khai: Bat dau kiem ke Ebook editor/preview/export hien co theo guideline Compose/MVI.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P07-T03-EBOOK-EDITOR-EXPORT.md`
- Rui ro/cong viec con lai: Can giu export tu BookInfo/Bookshelf khong bi anh huong va khong pha VBook lock.
- Task tiep theo: Hoan tat P07.T03.

## 2026-07-30 19:49 - P07.T03 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien Ebook editor, preview va export: metadata/cover/TOC/layout/media/preflight, EPUB3/PDF/TXT deterministic.
- Thoi gian bat dau/ket thuc: 2026-07-30 19:08 / 2026-07-30 19:49
- File thay doi: `EbookExportWriter.kt`, `EbookLayoutRenderer.kt`, `ExportAuthoringProjectUseCase.kt`, `ValidateEbookProjectUseCase.kt`, `EbookEditorScreen.kt`, `EbookExportWriterTest.kt`, `ValidateEbookProjectUseCaseTest.kt`, `reports/P07-T03-EBOOK-EDITOR-EXPORT.md`, task matrix va plan log.
- Tom tat trien khai: TXT export normalize LF; EPUB3 dat ZIP timestamp co dinh, cover MIME/extension dung, anh trung ten duoc dat ten theo checksum noi dung; HTML image MIME day du; preview normalize CRLF; export/preflight chap nhan `file://`; UI clone book bo separator mojibake.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.service.export.EbookExportWriterTest" --tests "io.legado.app.domain.usecase.ValidateEbookProjectUseCaseTest" --tests "io.legado.app.service.export.EbookLayoutRendererTest" --tests "io.legado.app.service.export.EbookExportScopeTest" --tests "io.legado.app.domain.model.EbookDocumentTest" --no-daemon --console=plain`; `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=io.legado.app.service.export.EbookExportWriterInstrumentedTest" --no-daemon --console=plain`.
- Ket qua: PASS; unit tests 13 tests PASS; Kotlin compile BUILD SUCCESSFUL; emulator `EbookExportWriterInstrumentedTest` 2 tests PASS voi PDF fixed-layout va all-modern-format export.
- Bang chung: `reports/P07-T03-EBOOK-EDITOR-EXPORT.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.service.export.EbookExportWriterTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.usecase.ValidateEbookProjectUseCaseTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.service.export.EbookLayoutRendererTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.service.export.EbookExportScopeTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.model.EbookDocumentTest.xml`; `app/build/outputs/androidTest-results/connected/debug/flavors/app/TEST-emulator-5554 - 14-_app-app.xml`.
- Rui ro/cong viec con lai: Chua co screenshot/manual Compose smoke cho Ebook editor/preview va chua chay external EPUBCheck; P07.T06 se dong UI smoke/validator neu can. P07.T04 can corruption recovery/autosave history.
- Task tiep theo: P07.T04.

## 2026-07-30 19:49 - P07.T04 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Khong silently drop project loi; them quarantine/recovery/autosave history toi thieu cho authoring store.
- Thoi gian bat dau/ket thuc: 2026-07-30 19:49 / dang thuc hien
- File thay doi du kien: authoring file store/repository/usecase, recovery tests, report, task matrix va plan log.
- Tom tat trien khai: Bat dau kiem ke `AuthoringProjectFileStore` va fault handling hien co sau P07.T01.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P07-T04-AUTHORING-RECOVERY.md`
- Rui ro/cong viec con lai: Can tranh doi public API qua lon khi P07.T05 con can backup/sync assets.
- Task tiep theo: Hoan tat P07.T04.

## 2026-07-30 20:00 - P07.T04 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Khong silently drop project loi; them quarantine/recovery/autosave history toi thieu cho authoring store.
- Thoi gian bat dau/ket thuc: 2026-07-30 19:49 / 2026-07-30 20:00
- File thay doi: `AuthoringProjectGateway.kt`, `AuthoringProjectUseCase.kt`, `AuthoringProjectRepository.kt`, `AuthoringProjectFileStore.kt`, `AuthoringProjectFileStoreTest.kt`, `reports/P07-T04-AUTHORING-RECOVERY.md`, task matrix va plan log.
- Tom tat trien khai: Them recovery diagnostics API; manifest corrupt/hash mismatch/unsupported schema duoc quarantine va restore tu snapshot moi nhat; asset-index corrupt duoc quarantine; missing asset sinh diagnostic; autosave history giu 5 manifest snapshots.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.AuthoringProjectFileStoreTest" --no-daemon --console=plain`; `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`.
- Ket qua: PASS; `AuthoringProjectFileStoreTest` 10 tests PASS; Android Kotlin compile BUILD SUCCESSFUL.
- Bang chung: `reports/P07-T04-AUTHORING-RECOVERY.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.AuthoringProjectFileStoreTest.xml`.
- Rui ro/cong viec con lai: UI recovery surface chua rieng, se noi vao P07.T06/P11; P07.T05 can loai tru `authoring/recovery/**` khoi backup cloud theo ADR-008.
- Task tiep theo: P07.T05.

## 2026-07-30 20:04 - P07.T05 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Backup/sync authoring project va content-addressed assets, exclude recovery/temp cache theo policy Supabase/Google Drive snapshot.
- Thoi gian bat dau/ket thuc: 2026-07-30 20:04 / dang thuc hien
- File thay doi du kien: authoring backup adapter, backup/restore snapshot integration tests, report, task matrix va plan log.
- Tom tat trien khai: Bat dau kiem ke backup/snapshot pipeline hien co de noi authoring assets vao khong pha Google Drive backup hien huu.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P07-T05-AUTHORING-BACKUP-SYNC.md`
- Rui ro/cong viec con lai: Supabase/Drive conflict UI thuc su nam P10.T07; P07.T05 se dong local snapshot adapter va policy evidence.
- Task tiep theo: Hoan tat P07.T05.

## 2026-07-30 20:23 - P07.T05 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Backup/sync authoring project va content-addressed assets, exclude recovery/temp cache theo policy Supabase/Google Drive snapshot.
- Thoi gian bat dau/ket thuc: 2026-07-30 20:04 / 2026-07-30 20:23
- File thay doi: `AuthoringBackupFiles.kt`, `AuthoringProjectFileStore.kt`, `Backup.kt`, `Restore.kt`, `AuthoringBackupFilesTest.kt`, `reports/P07-T05-AUTHORING-BACKUP-SYNC.md`, task matrix va plan log.
- Tom tat trien khai: Them adapter snapshot local cho `authoring/projects/**` va `authoring/assets/**`, validate manifest hash + asset sha256/size, loai tru `authoring/recovery/**` va `.tmp`, restore qua staging co rollback; noi vao backup/restore zip hien huu de Google Drive/WebDAV/local backup giu authoring data.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.AuthoringBackupFilesTest" --tests "io.legado.app.data.repository.AuthoringProjectFileStoreTest" --no-daemon --console=plain`; `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`.
- Ket qua: PASS; `AuthoringBackupFilesTest` 3 tests PASS; `AuthoringProjectFileStoreTest` 10 tests PASS; Android Kotlin compile BUILD SUCCESSFUL.
- Bang chung: `reports/P07-T05-AUTHORING-BACKUP-SYNC.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.AuthoringBackupFilesTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.AuthoringProjectFileStoreTest.xml`.
- Rui ro/cong viec con lai: Supabase/Google Drive cloud conflict, account binding va multi-target restore thuoc P10.T05-P10.T08; P07.T06 se dong regression suite cho Authoring/Ebook.
- Task tiep theo: P07.T06.

## 2026-07-30 20:23 - P07.T06 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Dong gate logic, filesystem, export, UI navigation va device integration cho Sang tac/Bien tap Ebook.
- Thoi gian bat dau/ket thuc: 2026-07-30 20:23 / dang thuc hien
- File thay doi du kien: `EbookExportWriter.kt`, `AuthoringBackupFiles.kt`, focused tests, report, task matrix va plan log.
- Tom tat trien khai: Bat dau bo sung large asset/OOM-prevention tests, process-recreate reload test va UI navigation smoke.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P07-T06-AUTHORING-EBOOK-TEST-SUITE.md`
- Rui ro/cong viec con lai: Can chay focused unit va instrumented export/authoring integration.
- Task tiep theo: Hoan tat P07.T06.

## 2026-07-30 20:38 - P07.T06 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Dong gate logic, filesystem, export, UI navigation va device integration cho Sang tac/Bien tap Ebook.
- Thoi gian bat dau/ket thuc: 2026-07-30 20:23 / 2026-07-30 20:38
- File thay doi: `EbookExportWriter.kt`, `AuthoringBackupFiles.kt`, `AuthoringBackupFilesTest.kt`, `EbookExportWriterTest.kt`, `MainNavigatorTest.kt`, `reports/P07-T06-AUTHORING-EBOOK-TEST-SUITE.md`, task matrix va plan log.
- Tom tat trien khai: Stream-copy anh EPUB3/CBZ va stream-hash backup asset; them regression asset lon, repository recreate/backup round-trip, UI route smoke cho Writing/Ebook/Preview; gom phase test matrix.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.AuthoringBackupFilesTest" --tests "io.legado.app.data.repository.AuthoringProjectFileStoreTest" --tests "io.legado.app.domain.usecase.AuthoringProjectUseCaseTest" --tests "io.legado.app.ui.authoring.writing.WritingEditOperationsTest" --tests "io.legado.app.service.export.EbookExportWriterTest" --tests "io.legado.app.domain.usecase.ValidateEbookProjectUseCaseTest" --tests "io.legado.app.service.export.EbookLayoutRendererTest" --tests "io.legado.app.service.export.EbookExportScopeTest" --tests "io.legado.app.domain.model.EbookDocumentTest" --tests "io.legado.app.ui.main.MainNavigatorTest" --no-daemon --console=plain`; `.\gradlew.bat :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=io.legado.app.service.export.EbookExportWriterInstrumentedTest,io.legado.app.integration.TranslationAuthoringIntegrationTest" --no-daemon --console=plain`.
- Ket qua: PASS; focused unit 45 tests PASS, 0 failures/errors/skipped; emulator `emulator-5554 - 14` instrumented 3 tests PASS, 0 failures/errors/skipped.
- Bang chung: `reports/P07-T06-AUTHORING-EBOOK-TEST-SUITE.md`; unit XML trong `app/build/test-results/testAppDebugUnitTest/`; device XML `app/build/outputs/androidTest-results/connected/debug/flavors/app/TEST-emulator-5554 - 14-_app-app.xml`.
- Rui ro/cong viec con lai: Chua co EPUBCheck external validator va Compose screenshot click-through rieng; dua vao P11 release/E2E audit.
- Task tiep theo: P08.T01.

## 2026-07-30 20:38 - PHASE 07 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Dong nen tang Sang tac/Bien tap Ebook co luu tru an toan, preview/export va backup evidence.
- Thoi gian bat dau/ket thuc: 2026-07-30 17:05 / 2026-07-30 20:38
- File thay doi: `ui/authoring/**`, `domain/usecase/*Authoring*`, `domain/usecase/*Ebook*`, `data/repository/Authoring*`, `service/export/**`, backup/restore integration, tests, reports P07.T01-P07.T06, task matrix va plan log.
- Tom tat trien khai: 6/6 task DONE; repository versioned/atomic, module Sang tac, Ebook editor/export, recovery/history, backup snapshot va phase test suite da co evidence.
- Lenh kiem tra: Focused Authoring/Ebook unit suites, compile gates trong P07.T01-P07.T05, va P07.T06 device integration.
- Ket qua: Phase gate PASS; khong co failing test trong focused suite; 3 instrumented export/authoring tests pass tren emulator.
- Bang chung: `reports/P07-T01-AUTHORING-REPOSITORY.md` den `reports/P07-T06-AUTHORING-EBOOK-TEST-SUITE.md`.
- Rui ro/cong viec con lai: Cloud conflict/account sync thuoc P10; end-to-end UI screenshot/release validator thuoc P11.
- Task tiep theo: P08.T01.

## 2026-07-30 20:39 - P08.T01 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Kiem ke va khoa built-in Agent tool contracts: ID, schema, permission, available/registered semantics va tests.
- Thoi gian bat dau/ket thuc: 2026-07-30 20:39 / dang thuc hien
- File thay doi du kien: `AiToolRepository.kt`, `AiToolGateway.kt`, Agent dashboard state/UI, tool catalog tests, report, task matrix va plan log.
- Tom tat trien khai: Bat dau inventory registry 41 tools, doi chieu permission map va dieu tra vi sao app chi bao 20 tools.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P08-T01-AGENT-TOOL-CONTRACTS.md`
- Rui ro/cong viec con lai: Khong duoc auto-enable custom/mutation tools; can tach registered vs available.
- Task tiep theo: Hoan tat P08.T01.

## 2026-07-30 20:49 - P08.T01 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Kiem ke va khoa built-in Agent tool contracts: ID, schema, permission, available/registered semantics va tests.
- Thoi gian bat dau/ket thuc: 2026-07-30 20:39 / 2026-07-30 20:49
- File thay doi: `AiToolRepository.kt`, `AiToolGateway.kt`, `AgentDashboardContract.kt`, `AgentDashboardViewModel.kt`, `AgentDashboardScreen.kt`, `strings.xml` cac locale, `AiToolRepositoryToolCatalogTest.kt`, `AgentDashboardStateMapperTest.kt`, `reports/P08-T01-AGENT-TOOL-CONTRACTS.md`, task matrix va plan log.
- Tom tat trien khai: Khoa 41 built-in tool IDs; them validator reject unknown/malformed args; structured unknown-tool error; dashboard tach `registeredTools` 41 voi `availableTools` 20 khi safety flags mac dinh tat mutation/skill/plugin.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.AiToolRepositoryToolCatalogTest" --tests "io.legado.app.ui.ai.agent.AgentDashboardStateMapperTest" --tests "io.legado.app.domain.agent.AgentPermissionBrokerTest" --tests "io.legado.app.security.AgentPermissionSecurityTest" --no-daemon --console=plain`.
- Ket qua: PASS; 23 focused tests PASS, 0 failures/errors/skipped; Gradle BUILD SUCCESSFUL.
- Bang chung: `reports/P08-T01-AGENT-TOOL-CONTRACTS.md`; XML reports trong `app/build/test-results/testAppDebugUnitTest/`.
- Rui ro/cong viec con lai: P08.T02 can thay feature flag thô bang permission broker/audit log; custom JS tool lifecycle chua bat dau.
- Task tiep theo: P08.T02.

## 2026-07-30 20:54 - P08.T02 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien permission broker va audit log cho Agent mutation/sensitive tools.
- Thoi gian bat dau/ket thuc: 2026-07-30 20:54 / dang thuc hien
- File thay doi du kien: `AgentModels.kt`, `AgentPermissionBroker.kt`, `AiAgentGateway.kt`, `ExecuteApprovedAgentActionUseCase.kt`, `AiChatGenerationUseCase.kt`, Agent audit Room entity/DAO/migration, chat approval UI, dashboard audit UI, tests, report, task matrix va plan log.
- Tom tat trien khai: Bat dau them capability matrix, approval scope one-time/session/always, audit storage va redaction tests.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P08-T02-PERMISSION-AUDIT.md`
- Rui ro/cong viec con lai: Can dam bao default-deny, revoke, process restart va migration 108->109 pass.
- Task tiep theo: Hoan tat P08.T02.

## 2026-07-30 21:44 - P08.T02 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien permission broker va audit log cho Agent mutation/sensitive tools.
- Thoi gian bat dau/ket thuc: 2026-07-30 20:54 / 2026-07-30 21:44
- File thay doi: `AgentModels.kt`, `AgentPermissionBroker.kt`, `AiAgentGateway.kt`, `ExecuteApprovedAgentActionUseCase.kt`, `AiChatGenerationUseCase.kt`, `AiAgentAudit.kt`, `AiAgentDao.kt`, `AiAgentRepository.kt`, `AppDatabase.kt`, `DatabaseMigrations.kt`, `AiChatContract.kt`, `AiChatViewModel.kt`, `AiChatScreen.kt`, `AgentDashboardContract.kt`, `AgentDashboardViewModel.kt`, `AgentDashboardScreen.kt`, `strings.xml`, `values-vi/strings.xml`, `app/schemas/io.legado.app.data.AppDatabase/109.json`, permission/audit tests, migration test, report, task matrix va plan log.
- Tom tat trien khai: Them capability levels read/write/network/file/source/authoring; approval scope one-time/session/always voi revoke/default-deny; persistent `ai_agent_audits`; audit request/result/error/duration; UI chat chon scope; `AiToolGateway.execute()` truyen `conversationId` de session grant dung scope; Agent dashboard hien audit gan day; proposal preview va repository audit deu sanitize.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.agent.AgentPermissionBrokerTest" --tests "io.legado.app.domain.agent.AgentAuditSanitizerTest" --tests "io.legado.app.domain.usecase.ExecuteApprovedAgentActionUseCaseTest" --tests "io.legado.app.domain.usecase.AiChatGenerationUseCaseTest" --tests "io.legado.app.data.repository.AiAgentRepositoryAuditTest" --tests "io.legado.app.ui.ai.agent.AgentDashboardStateMapperTest" --no-daemon --console=plain`; `.\gradlew.bat :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=io.legado.app.AgentAuditMigrationTest" --no-daemon --console=plain`; `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`.
- Ket qua: PASS; focused unit 28 tests PASS, 0 failures/errors/skipped; `AgentAuditMigrationTest` 1 test PASS tren `emulator-5554 - 14`; Kotlin compile BUILD SUCCESSFUL.
- Bang chung: `reports/P08-T02-PERMISSION-AUDIT.md`; unit XML trong `app/build/test-results/testAppDebugUnitTest/`; device XML `app/build/outputs/androidTest-results/connected/debug/flavors/app/TEST-emulator-5554 - 14-_app-app.xml`; schema `app/schemas/io.legado.app.data.AppDatabase/109.json`.
- Rui ro/cong viec con lai: Custom JS tool manifest/runtime chua co; session/always grant dang memory-only va se can lifecycle/persistence policy ro hon neu UI P08.T04 muon luu dai han.
- Task tiep theo: P08.T03.

## 2026-07-30 21:58 - P08.T03 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Them custom JavaScript tool manifest/runtime an toan cho Agent ma khong mo Android/Java/file/secret access.
- Thoi gian bat dau/ket thuc: 2026-07-30 21:58 / dang thuc hien
- File thay doi du kien: `domain/agenttools/**`, `data/agenttools/**`, custom tool threat tests, report, task matrix va plan log.
- Tom tat trien khai: Bat dau reuse Rhino `SafeContextFactory`, tao manifest parser/validator, schema validator, network allow-list bridge va malicious script tests.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P08-T03-CUSTOM-JS-TOOL-RUNTIME.md`
- Rui ro/cong viec con lai: Can xac minh timeout/output/network gate va khong noi custom tool vao enabled registry truoc P08.T04.
- Task tiep theo: Hoan tat P08.T03.

## 2026-07-30 22:18 - P08.T03 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Them custom JavaScript tool manifest/runtime an toan cho Agent ma khong mo Android/Java/file/secret access.
- Thoi gian bat dau/ket thuc: 2026-07-30 21:58 / 2026-07-30 22:18
- File thay doi: `CustomAgentToolModels.kt`, `CustomAgentToolManifestParser.kt`, `CustomAgentToolRuntime.kt`, `CustomAgentToolManifestRuntimeTest.kt`, `reports/P08-T03-CUSTOM-JS-TOOL-RUNTIME.md`, task matrix va plan log.
- Tom tat trien khai: Them manifest schemaVersion/id/version/schema/capability/checksum validator; custom JS runtime `execute(input, context)` trong Rhino safe context; output schema/cap; network bridge chi mo qua `NETWORK` + allow-list; block Java/Android/process/reflection/path/secret/storage APIs.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.agenttools.CustomAgentToolManifestRuntimeTest" --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.agenttools.CustomAgentToolManifestRuntimeTest" --tests "io.legado.app.data.repository.AiToolRepositoryToolCatalogTest" --tests "io.legado.app.domain.agent.AgentPermissionBrokerTest" --tests "io.legado.app.security.AgentPermissionSecurityTest" --no-daemon --console=plain`; `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`.
- Ket qua: PASS; custom runtime 7 tests PASS; focused P08 regression 31 tests PASS; Kotlin compile BUILD SUCCESSFUL.
- Bang chung: `reports/P08-T03-CUSTOM-JS-TOOL-RUNTIME.md`; XML reports trong `app/build/test-results/testAppDebugUnitTest/`.
- Rui ro/cong viec con lai: Lifecycle Draft/Validate/Test/Approve/Enable chua co va thuoc P08.T04; custom JS v1 chi mo `READ`/`NETWORK`, mutation/file/source/authoring van qua built-in tool + permission broker.
- Task tiep theo: P08.T04.

## 2026-07-30 22:26 - P08.T04 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien lifecycle UI cho custom Agent tool: Draft -> Validate -> Run Fixture -> Approve -> Enable.
- Thoi gian bat dau/ket thuc: 2026-07-30 22:26 / dang thuc hien
- File thay doi du kien: custom tool domain/gateway/repository/entity/DAO/migration, Agent dashboard, custom tool Compose Contract/ViewModel/Screen, main navigation/DI, strings, tests, report, task matrix va plan log.
- Tom tat trien khai: Bat dau tach custom tool lifecycle khoi `ai_skills`, luu version immutable va yeu cau user approve/enable thu cong.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P08-T04-CUSTOM-TOOL-LIFECYCLE-UI.md`
- Rui ro/cong viec con lai: Can dam bao khong auto-enable, registry chi mo tool da approved/enabled, va migration 109->110 duoc validate.
- Task tiep theo: Hoan tat P08.T04.

## 2026-07-30 23:44 - P08.T04 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien lifecycle UI cho custom Agent tool: Draft -> Validate -> Run Fixture -> Approve -> Enable.
- Thoi gian bat dau/ket thuc: 2026-07-30 22:26 / 2026-07-30 23:44
- File thay doi: `CustomAgentToolModels.kt`, `CustomAgentToolGateway.kt`, `AiCustomTool.kt`, `AiCustomToolVersion.kt`, `AiCustomToolDao.kt`, `CustomAgentToolRepository.kt`, `AiToolRepository.kt`, `AppDatabase.kt`, `DatabaseMigrations.kt`, `app/schemas/io.legado.app.data.AppDatabase/110.json`, `AgentDashboardScreen.kt`, `ui/ai/agent/tools/**`, `MainNavKey.kt`, `MainNavGraph.kt`, `MainNavigator.kt`, `appModule.kt`, `strings.xml`, `values-vi/strings.xml`, `CustomAgentToolRepositoryTest.kt`, `CustomAgentToolMigrationTest.kt`, `MigrationTest.kt`, `test_db_migration_fixture.json`, report, task matrix va plan log.
- Tom tat trien khai: Them custom tool Room lifecycle storage DB 110; repository enforce Draft/Validate/Fixture/Approve/Enable; Agent registry tach registered/available custom tools; route `MainRouteAiCustomTools`; Agent dashboard co loi vao Custom Agent tools; UI Compose cho editor, validate, run fixture, approve, enable/disable, rollback va delete; migration golden fixture cap nhat len schema 110.
- Lenh kiem tra: `.\gradlew.bat :app:clean --no-daemon --console=plain`; `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:compileAppDebugAndroidTestKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.CustomAgentToolRepositoryTest" --tests "io.legado.app.domain.agenttools.CustomAgentToolManifestRuntimeTest" --tests "io.legado.app.data.repository.AiToolRepositoryToolCatalogTest" --tests "io.legado.app.domain.agent.AgentPermissionBrokerTest" --tests "io.legado.app.security.AgentPermissionSecurityTest" --tests "io.legado.app.ui.ai.agent.AgentDashboardStateMapperTest" --no-daemon --console=plain`.
- Ket qua: PASS; Kotlin compile BUILD SUCCESSFUL; Android test compile BUILD SUCCESSFUL; focused JVM 38 tests PASS, 0 failures/errors/skipped.
- Bang chung: `reports/P08-T04-CUSTOM-TOOL-LIFECYCLE-UI.md`; XML unit reports trong `app/build/test-results/testAppDebugUnitTest/`.
- Rui ro/cong viec con lai: Connected `CustomAgentToolMigrationTest` da thu chay tren `emulator-5554` nhung timeout/khong tao report; P08.T05 can tiep tuc Skill/VBook compatibility va lifecycle; P08.T06 can gate security/regression rong hon.
- Task tiep theo: P08.T05.

## 2026-07-31 00:05 - P08.T05 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Giu skill/VBook draft/install hien co, tach ro khoi custom Agent tool va khoa compatibility lifecycle bang tests.
- Thoi gian bat dau/ket thuc: 2026-07-31 00:05 / dang thuc hien
- File thay doi du kien: `AiSkillRepository.kt`, `CustomAgentToolManifestParser.kt`, Agent skill/custom tool/VBook importer/compatibility tests, report, task matrix va plan log.
- Tom tat trien khai: Bat dau them provenance vao manifest skill versioned, legacy fallback test, custom tool anti-impersonation validator, VBook importer ZIP/identity tests va public fixture import/run.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P08-T05-SKILL-VBOOK-COMPATIBILITY.md`
- Rui ro/cong viec con lai: Can dam bao custom tool khong cham VBook/legacy API, VBook public fixtures import/run, ZIP/path/URL/identity attack bi chan.
- Task tiep theo: Hoan tat P08.T05.

## 2026-07-31 00:05 - P08.T05 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Giu skill/VBook draft/install hien co, tach ro khoi custom Agent tool va khoa compatibility lifecycle bang tests.
- Thoi gian bat dau/ket thuc: 2026-07-31 00:05 / 2026-07-31 00:05
- File thay doi: `AiSkillRepository.kt`, `CustomAgentToolManifestParser.kt`, `AiSkillRepositoryTest.kt`, `CustomAgentToolManifestRuntimeTest.kt`, `CompatibilityCorpusTest.kt`, `VbookImportRepositoryTest.kt`, `VbookPluginImporterSecurityTest.kt`, report, task matrix va plan log.
- Tom tat trien khai: Skill manifest moi ghi `provenance`/`lifecycle` khong tang Room schema; legacy skill version thieu provenance van enable duoc neu files day du; custom Agent tool reject dau hieu VBook/legacy API; corpus VBook import/run qua importer cho text/comic/audio/video va reject TTS/translator; VBook ZIP traversal va registry identity mismatch duoc test.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.compat.CompatibilityCorpusTest" --tests "io.legado.app.help.vbook.VbookPluginInspectorTest" --tests "io.legado.app.help.vbook.VbookPluginImporterTest" --tests "io.legado.app.help.vbook.VbookPluginImporterSecurityTest" --tests "io.legado.app.data.repository.vbook.VbookImportRepositoryTest" --tests "io.legado.app.data.repository.vbook.VbookRegistryParserTest" --tests "io.legado.app.data.repository.AiToolRepositoryPluginDraftValidatorTest" --tests "io.legado.app.data.repository.AiSkillRepositoryTest" --tests "io.legado.app.domain.agent.AgentSkillValidatorTest" --tests "io.legado.app.domain.agenttools.CustomAgentToolManifestRuntimeTest" --no-daemon --console=plain`.
- Ket qua: PASS; Kotlin compile BUILD SUCCESSFUL; focused JVM 40 tests PASS, 0 failures/errors/skipped.
- Bang chung: `reports/P08-T05-SKILL-VBOOK-COMPATIBILITY.md`; XML unit reports trong `app/build/test-results/testAppDebugUnitTest/`.
- Rui ro/cong viec con lai: P08.T06 van can dong gate security/regression rong hon, gom permission denial/cancellation/timeout/audit/backup/restore/malicious scripts va minified release.
- Task tiep theo: P08.T06.

## 2026-07-31 00:06 - P08.T06 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Dong gate security/regression cho toan bo Agent/tool system sau P08.T01-P08.T05.
- Thoi gian bat dau/ket thuc: 2026-07-31 00:06 / dang thuc hien
- File thay doi du kien: Agent sanitizer/repository tests, custom tool lifecycle restore tests, compatibility/security tests, minified release evidence, report, task matrix va plan log.
- Tom tat trien khai: Bat dau gom gate built-in tools, custom lifecycle, permission denial, cancellation, timeout, audit redaction, backup/restore, malicious scripts va minified release.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P08-T06-AGENT-SECURITY-REGRESSION.md`
- Rui ro/cong viec con lai: Can tao report va cap nhat task matrix/log sau khi release gate co bang chung.
- Task tiep theo: Hoan tat P08.T06.

## 2026-07-31 01:03 - P08.T06 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Dong gate security/regression cho toan bo Agent/tool system sau P08.T01-P08.T05.
- Thoi gian bat dau/ket thuc: 2026-07-31 00:06 / 2026-07-31 01:03
- File thay doi: `AgentAuditSanitizer.kt`, `AiAgentRepository.kt`, `AgentAuditSanitizerTest.kt`, `AiAgentRepositoryAuditTest.kt`, `CustomAgentToolRepositoryTest.kt`, `CustomAgentToolManifestRuntimeTest.kt`, `reports/P08-T06-AGENT-SECURITY-REGRESSION.md`, task matrix va plan log.
- Tom tat trien khai: Mo rong audit redaction cho cookie/password/secret query-style; sanitize proposal `argumentsPreview` truoc khi persist; khoa repository recreation de khong auto-enable latest draft; chay ma tran security/runtimes/compatibility va minified release R8.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.AiToolRepositoryToolCatalogTest" --tests "io.legado.app.data.repository.AiToolRepositoryInternetPageTest" --tests "io.legado.app.data.repository.AiToolRepositoryPluginDraftValidatorTest" --tests "io.legado.app.domain.agent.AgentPermissionBrokerTest" --tests "io.legado.app.domain.agent.AgentAuditSanitizerTest" --tests "io.legado.app.domain.agent.AgentToolLoopGuardTest" --tests "io.legado.app.domain.agent.AgentSkillValidatorTest" --tests "io.legado.app.domain.agenttools.CustomAgentToolManifestRuntimeTest" --tests "io.legado.app.data.repository.CustomAgentToolRepositoryTest" --tests "io.legado.app.data.repository.AiAgentRepositoryAuditTest" --tests "io.legado.app.domain.usecase.ExecuteApprovedAgentActionUseCaseTest" --tests "io.legado.app.domain.usecase.RunAiAgentUseCaseTest" --tests "io.legado.app.security.AgentPermissionSecurityTest" --tests "io.legado.app.ui.ai.agent.AgentDashboardStateMapperTest" --tests "io.legado.app.data.repository.AiSkillRepositoryTest" --tests "io.legado.app.compat.CompatibilityCorpusTest" --tests "io.legado.app.help.vbook.VbookPluginImporterSecurityTest" --tests "io.legado.app.data.repository.vbook.VbookImportRepositoryTest" --no-daemon --console=plain`; `.\gradlew.bat :app:assembleAppRelease --no-daemon --console=plain`.
- Ket qua: PASS; focused P08.T06 JVM 81 tests PASS, 0 failures/errors/skipped; minified release BUILD SUCCESSFUL in 14m 56s, tao universal/armeabi-v7a/x86_64/arm64-v8a unsigned APK va R8 mapping.
- Bang chung: `reports/P08-T06-AGENT-SECURITY-REGRESSION.md`; XML unit reports trong `app/build/test-results/testAppDebugUnitTest/`; APKs trong `app/build/outputs/apk/app/release/`; mapping trong `app/build/outputs/mapping/appRelease/`.
- Rui ro/cong viec con lai: Connected P08.T06 device suite rieng chua chay lai; Agent mutation/skill/plugin flags van mac dinh tat nen app chi mo 20 tool safe cho model neu user chua bat.
- Task tiep theo: P09.T01.

## 2026-07-31 01:04 - P09.T01 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Sua source content-rule media resolution de rule media khong bi hien nhu text, dac biet label + HLS URL.
- Thoi gian bat dau/ket thuc: 2026-07-31 01:04 / dang thuc hien
- File thay doi du kien: `MediaResolverRepository.kt`, `help/media/**`, media parser tests, report, task matrix va plan log.
- Tom tat trien khai: Bat dau them parser cho raw content-rule media result, reuse source content pipeline voi book/chapter/context day du va giu headers/referrer.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P09-T01-SOURCE-CONTENT-MEDIA-RESOLUTION.md`
- Rui ro/cong viec con lai: Can dam bao text chapter binh thuong khong bi nhan nham va VBook adapter khong bi doi semantics.
- Task tiep theo: Hoan tat P09.T01.

## 2026-07-31 01:18 - P09.T01 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Sua source content-rule media resolution de rule media khong bi hien nhu text, dac biet label + HLS URL.
- Thoi gian bat dau/ket thuc: 2026-07-31 01:04 / 2026-07-31 01:18
- File thay doi: `MediaResolverRepository.kt`, `MediaSourceRuleResultParser.kt`, `MediaSourceRuleResultParserTest.kt`, `reports/P09-T01-SOURCE-CONTENT-MEDIA-RESOLUTION.md`, task matrix va plan log.
- Tom tat trien khai: `MediaResolverRepository` dung source content pipeline cho audio/video source khong phai VBook voi `needSave=false`; them parser label+URL, JSON/VBook-style result, header/referrer merge va relative URL normalize; text paragraph khong co media URL bi reject.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.media.MediaSourceRuleResultParserTest" --tests "io.legado.app.help.media.MediaUriResolverTest" --tests "io.legado.app.help.vbook.VbookMediaParserTest" --no-daemon --console=plain`.
- Ket qua: PASS; Kotlin compile BUILD SUCCESSFUL; focused parser JVM 11 tests PASS, 0 failures/errors/skipped.
- Bang chung: `reports/P09-T01-SOURCE-CONTENT-MEDIA-RESOLUTION.md`; XML unit reports trong `app/build/test-results/testAppDebugUnitTest/`.
- Rui ro/cong viec con lai: Chua co full repository integration test voi Room DAO gia lap; P09.T02 can khoa ResolvedMedia contract/golden serialization va credential-safe persistence.
- Task tiep theo: P09.T02.

## 2026-07-31 01:19 - P09.T02 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Khoa ResolvedMedia contracts cho direct/HLS/DASH/local, variants/tracks va credential-safe serialization.
- Thoi gian bat dau/ket thuc: 2026-07-31 01:19 / dang thuc hien
- File thay doi du kien: `ResolvedMedia.kt`, media serialization/sanitizer helpers, parser adapters/tests, report, task matrix va plan log.
- Tom tat trien khai: Bat dau them contract DTO/golden tests cho variant/subtitle/audio headers, stable IDs, protocol/kind va redaction cho persistent/log output.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P09-T02-RESOLVED-MEDIA-CONTRACTS.md`
- Rui ro/cong viec con lai: Can khong doi public semantics playback/download hien co.
- Task tiep theo: Hoan tat P09.T02.

## 2026-07-31 01:28 - P09.T02 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Khoa ResolvedMedia contracts cho direct/HLS/DASH/local, variants/tracks va credential-safe serialization.
- Thoi gian bat dau/ket thuc: 2026-07-31 01:19 / 2026-07-31 01:28
- File thay doi: `ResolvedMedia.kt`, `ResolvedMediaContract.kt`, `MediaDownloadRepository.kt`, `ResolvedMediaContractTest.kt`, `reports/P09-T02-RESOLVED-MEDIA-CONTRACTS.md`, task matrix va plan log.
- Tom tat trien khai: Them contract schema version 1 cho media/variant/subtitle/audio; them duration/drm/download filename metadata; redacted contract an toan cho log; persistent media headers drop Cookie/Authorization/API key/token truoc khi ghi `headersJson` download task.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.ResolvedMediaContractTest" --tests "io.legado.app.help.media.MediaSourceRuleResultParserTest" --tests "io.legado.app.help.media.MediaUriResolverTest" --tests "io.legado.app.help.vbook.VbookMediaParserTest" --no-daemon --console=plain`.
- Ket qua: PASS; focused media contract/parser JVM 15 tests PASS, 0 failures/errors/skipped.
- Bang chung: `reports/P09-T02-RESOLVED-MEDIA-CONTRACTS.md`; XML unit reports trong `app/build/test-results/testAppDebugUnitTest/`.
- Rui ro/cong viec con lai: `sourceUri` van persist raw de tranh pha resume; P09.T03/P09.T04 can dung CookieVault/runtime cookie bridge thay raw Cookie header trong DB.
- Task tiep theo: P09.T03.

## 2026-07-31 01:29 - P09.T03 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien Media3 playback service/UI, uu tien service-owned playback, quality/track controls, progress resume va error/retry behavior.
- Thoi gian bat dau/ket thuc: 2026-07-31 01:29 / dang thuc hien
- File thay doi du kien: `MediaPlaybackService.kt`, `MediaPlayerViewModel.kt`, `help/media/**`, focused playback tests, report, task matrix va plan log.
- Tom tat trien khai: Bat dau khoa resume/clip position policy va giu playback position khi doi quality/variant.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P09-T03-MEDIA3-PLAYBACK-SERVICE-UI.md`
- Rui ro/cong viec con lai: Device playback/PiP/background chua the thay the bang JVM tests; can ghi ro neu chua chay device.
- Task tiep theo: Hoan tat P09.T03.

## 2026-07-31 01:33 - P09.T03 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien Media3 playback service/UI, uu tien service-owned playback, quality/track controls, progress resume va error/retry behavior.
- Thoi gian bat dau/ket thuc: 2026-07-31 01:29 / 2026-07-31 01:33
- File thay doi: `MediaPlaybackPositionPolicy.kt`, `MediaPlaybackService.kt`, `MediaPlayerViewModel.kt`, `MediaPlaybackPositionPolicyTest.kt`, `reports/P09-T03-MEDIA3-PLAYBACK-SERVICE-UI.md`, task matrix va plan log.
- Tom tat trien khai: Them policy testable cho persisted absolute progress, clip-relative snapshot/duration va bounded seek; service dung policy khi prepare/seek/publish; doi quality/variant giu current position thay vi reset ve 0.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.media.MediaPlaybackPositionPolicyTest" --tests "io.legado.app.domain.model.ResolvedMediaContractTest" --tests "io.legado.app.help.media.MediaSourceRuleResultParserTest" --tests "io.legado.app.help.media.MediaUriResolverTest" --tests "io.legado.app.help.vbook.VbookMediaParserTest" --no-daemon --console=plain`.
- Ket qua: PASS; focused playback/media JVM 19 tests PASS, 0 failures/errors/skipped; Kotlin compile BUILD SUCCESSFUL trong cung lenh.
- Bang chung: `reports/P09-T03-MEDIA3-PLAYBACK-SERVICE-UI.md`; XML unit reports trong `app/build/test-results/testAppDebugUnitTest/`.
- Rui ro/cong viec con lai: Device playback/PiP/background/headset matrix chua chay; P09.T07 se can device gate.
- Task tiep theo: P09.T04.

## 2026-07-31 01:37 - P09.T04 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien HLS downloader cho playlist don/master, AES-128, init map, byte-range, discontinuity va resume checkpoint.
- Thoi gian bat dau/ket thuc: 2026-07-31 01:37 / dang thuc hien
- File thay doi du kien: `MediaDownloadTransferPolicy.kt`, `MediaDownloadService.kt`, focused HLS transfer tests, report, task matrix va plan log.
- Tom tat trien khai: Bat dau mo rong parser HLS de chon variant, resolve segment/map, tinh IV, parse BYTERANGE/EXT-X-MAP va truyen Range header vao downloader.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P09-T04-HLS-DOWNLOADER.md`
- Rui ro/cong viec con lai: Device/offline playback thuc te se can P09.T07; P09.T04 tap trung parser/downloader behavior va compile gate.
- Task tiep theo: Hoan tat P09.T04.

## 2026-07-31 01:50 - P09.T04 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien HLS downloader cho playlist don/master, AES-128, init map, byte-range, discontinuity va resume checkpoint.
- Thoi gian bat dau/ket thuc: 2026-07-31 01:37 / 2026-07-31 01:50
- File thay doi: `MediaDownloadTransferPolicy.kt`, `MediaDownloadService.kt`, `MediaDownloadTransferPolicyTest.kt`, `reports/P09-T04-HLS-DOWNLOADER.md`, task matrix va plan log.
- Tom tat trien khai: Them parser master variant/byte-range/init map/discontinuity/AES IV; service chon variant tot nhat, gioi han depth/loop, gui Range header, tai segment vao scratch file truoc khi append, retry tung segment va checkpoint sau khi append thanh cong.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.media.MediaDownloadTransferPolicyTest" --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.media.MediaDownloadTransferPolicyTest" --tests "io.legado.app.help.media.MediaPlaybackPositionPolicyTest" --tests "io.legado.app.domain.model.ResolvedMediaContractTest" --tests "io.legado.app.help.media.MediaSourceRuleResultParserTest" --tests "io.legado.app.help.media.MediaUriResolverTest" --tests "io.legado.app.help.vbook.VbookMediaParserTest" --no-daemon --console=plain`; `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`.
- Ket qua: PASS; focused HLS 8 tests PASS, focused media regression 27 tests PASS, Kotlin compile BUILD SUCCESSFUL.
- Bang chung: `reports/P09-T04-HLS-DOWNLOADER.md`; XML unit reports trong `app/build/test-results/testAppDebugUnitTest/`.
- Rui ro/cong viec con lai: Device/offline playback thuc te va HLS output voi fixture nguoi dung se duoc dong gate o P09.T07.
- Task tiep theo: P09.T05.

## 2026-07-31 01:52 - P09.T05 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien Direct/DASH download, resume va export.
- Thoi gian bat dau/ket thuc: 2026-07-31 01:52 / dang thuc hien
- File thay doi du kien: `MediaDownloadTransferPolicy.kt`, `MediaDownloadService.kt`, media download tests, report, task matrix va plan log.
- Tom tat trien khai: Bat dau tu khoang trong ro nhat la DASH planner/downloader; Direct da co Range/ETag/Last-Modified/checksum va UI da co SAF export nen se duoc khoa bang regression evidence.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P09-T05-DIRECT-DASH-EXPORT.md`
- Rui ro/cong viec con lai: Can tranh overclaim voi DASH phuc tap/DRM/live; P09.T07 van la gate device/offline.
- Task tiep theo: Hoan tat P09.T05.

## 2026-07-31 02:06 - P09.T05 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien Direct/DASH download, resume va export.
- Thoi gian bat dau/ket thuc: 2026-07-31 01:52 / 2026-07-31 02:06
- File thay doi: `MediaDownloadTransferPolicy.kt`, `MediaDownloadService.kt`, `MediaDownloadTransferPolicyTest.kt`, `reports/P09-T05-DIRECT-DASH-EXPORT.md`, task matrix va plan log.
- Tom tat trien khai: Them DASH XML planner an toan, SegmentList/SegmentTemplate/Timeline/range/template substitution va best representation selection; service xu ly `MediaProtocol.DASH`, refresh 401/403 mot lan, retry/checkpoint tung segment, Range header va finalize qua checksum/probe hien co; xac nhan Direct resume va SAF export flow hien co trong report.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.media.MediaDownloadTransferPolicyTest" --no-daemon --console=plain`; `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.media.MediaDownloadTransferPolicyTest" --tests "io.legado.app.help.media.MediaPlaybackPositionPolicyTest" --tests "io.legado.app.domain.model.ResolvedMediaContractTest" --tests "io.legado.app.help.media.MediaSourceRuleResultParserTest" --tests "io.legado.app.help.media.MediaUriResolverTest" --tests "io.legado.app.help.vbook.VbookMediaParserTest" --no-daemon --console=plain`.
- Ket qua: PASS; `MediaDownloadTransferPolicyTest` 10 tests PASS; focused media regression 29 tests PASS; Kotlin compile BUILD SUCCESSFUL.
- Bang chung: `reports/P09-T05-DIRECT-DASH-EXPORT.md`; XML unit reports trong `app/build/test-results/testAppDebugUnitTest/`.
- Rui ro/cong viec con lai: DASH live/dynamic, multi-adaptation mux, DRM va SAF device export can P09.T07/P11 gate.
- Task tiep theo: P09.T06.

## 2026-07-31 02:18 - P09.T06 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien Download management va error recovery UI cho media download.
- Thoi gian bat dau/ket thuc: 2026-07-31 02:18 / dang thuc hien
- File thay doi du kien: `MediaDownloadRepository.kt`, `MediaDownloadService.kt`, `MediaDownloadsContract.kt`, `MediaDownloadsViewModel.kt`, `MediaDownloadsScreen.kt`, focused media download tests, report, task matrix va plan log.
- Tom tat trien khai: Bat dau tu reconcile sau process restart, notification actions, UI summary/error recovery va test seam cho state management.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P09-T06-DOWNLOAD-MANAGEMENT-RECOVERY-UI.md`
- Rui ro/cong viec con lai: Device notification action/export SAF se can P09.T07/P11 gate neu khong co emulator trong turn nay.
- Task tiep theo: Hoan tat P09.T06.

## 2026-07-31 02:52 - P09.T06 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien Download management va error recovery UI cho media download.
- Thoi gian bat dau/ket thuc: 2026-07-31 02:18 / 2026-07-31 02:52
- File thay doi: `MediaDownloadGateway.kt`, `MediaDownloadDao.kt`, `MediaDownloadRepository.kt`, `MediaDownloadService.kt`, `MediaDownloadsContract.kt`, `MediaDownloadsViewModel.kt`, `MediaDownloadsScreen.kt`, media download string resources, `MediaDownloadRepositoryTest.kt`, `MediaDownloadsStateTest.kt`, report, task matrix va plan log.
- Tom tat trien khai: Them batch pause/resume/cancel/reconcile API; service reconcile RUNNING cu truoc khi start worker; notification co Pause/Resume/Cancel all; UI download co summary, localized state/filter/sort labels, disabled batch buttons va progress accessibility; reducer UI co test thuan.
- Lenh kiem tra: `.\gradlew.bat :app:clean --no-daemon --console=plain`; `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.MediaDownloadRepositoryTest" --tests "io.legado.app.ui.media.download.MediaDownloadsStateTest" --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.media.MediaDownloadTransferPolicyTest" --tests "io.legado.app.help.media.MediaPlaybackPositionPolicyTest" --tests "io.legado.app.domain.model.ResolvedMediaContractTest" --tests "io.legado.app.help.media.MediaSourceRuleResultParserTest" --tests "io.legado.app.help.media.MediaUriResolverTest" --tests "io.legado.app.help.vbook.VbookMediaParserTest" --tests "io.legado.app.data.repository.MediaDownloadRepositoryTest" --tests "io.legado.app.ui.media.download.MediaDownloadsStateTest" --no-daemon --console=plain`.
- Ket qua: PASS; Kotlin compile BUILD SUCCESSFUL; focused new tests 4 PASS; focused media/download regression 33 tests PASS, 0 failures/errors/skipped. Can `:app:clean` sau Gradle timeout dau tien de giai phong generated build lock.
- Bang chung: `reports/P09-T06-DOWNLOAD-MANAGEMENT-RECOVERY-UI.md`; XML unit reports trong `app/build/test-results/testAppDebugUnitTest/`.
- Rui ro/cong viec con lai: Device notification tap, SAF export tap, process kill/restart tren emulator/phone va offline playback output thuc te se duoc dong gate o P09.T07/P11.
- Task tiep theo: P09.T07.

## 2026-07-31 02:56 - P09.T07 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Dong gate player/downloader tren release artifact bang media unit/integration/device evidence.
- Thoi gian bat dau/ket thuc: 2026-07-31 02:56 / dang thuc hien
- File thay doi du kien: media device/integration reports, task matrix va plan log; chi sua code/test neu gate thieu seam ro rang.
- Tom tat trien khai: Bat dau tu emulator `emulator-5554`, media unit regression da PASS o P09.T06, tiep tuc connected instrumentation, release/R8 va logcat/device evidence.
- Lenh kiem tra: Se cap nhat khi task hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P09-T07-MEDIA-INTEGRATION-DEVICE.md`
- Rui ro/cong viec con lai: Playback/PiP/background/offline output co the can manual or instrumentation moi neu existing suite chua bao phu.
- Task tiep theo: Hoan tat P09.T07.

## 2026-07-31 04:18 - P09.T07 - DONE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Dong gate player/downloader tren release artifact bang media unit/integration/device evidence.
- Thoi gian bat dau/ket thuc: 2026-07-31 02:56 / 2026-07-31 04:18
- File thay doi: `MediaDevicePlaybackSmokeTest.kt`, `reports/P09-T07-MEDIA-INTEGRATION-DEVICE.md`, task matrix va plan log.
- Tom tat trien khai: Them instrumentation smoke tao WAV offline, prepare/play/pause/release bang Media3 `ExoPlayer`, probe output bang `MediaExtractor`; chay connected integration gom VBook HLS contract va media migration; mo app bang `am start` thay cho `monkey`; quet logcat crash/media; xac nhan release/R8 APK va mapping.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugAndroidTestKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=io.legado.app.integration.MediaDevicePlaybackSmokeTest,io.legado.app.integration.VbookMediaIntegrationTest,io.legado.app.MediaDownloadMigrationTest" --no-daemon --console=plain`; `.\gradlew.bat :app:packageAppRelease --stacktrace`; `.\gradlew.bat :app:assembleAppRelease`; `adb shell am start -W -n com.drducbook.app.debug/io.legado.app.ui.main.MainActivity`; `adb shell dumpsys meminfo com.drducbook.app.debug`; `adb logcat -d`.
- Ket qua: PASS; Android test compile PASS; connected 4 tests PASS tren `emulator-5554 - 14`; release package/assemble PASS; app launch `Status: ok`; meminfo `TOTAL PSS: 363498 KB`, `TOTAL RSS: 455380 KB`; crash/media logcat filters khong co match.
- Bang chung: `reports/P09-T07-MEDIA-INTEGRATION-DEVICE.md`; `app/build/outputs/androidTest-results/connected/debug/flavors/app/TEST-emulator-5554 - 14-_app-app.xml`; release APKs va `app/build/outputs/mapping/appRelease/mapping.txt`.
- Rui ro/cong viec con lai: Chua claim DRM/live DASH/PiP/background/manual SAF/notification tap; cac gate nang nay se nam o P11 regression/rollout.
- Task tiep theo: P10.T01.

## 2026-07-31 04:20 - P10.T01 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Kiem ke package/model/voice, chuyen catalog khoi Google Drive URL va tao HF manifest cho `Drduc/Legadofork`.
- Thoi gian bat dau/ket thuc: 2026-07-31 04:20 / dang thuc hien
- File thay doi du kien: Android asset catalogs, `supabase/artifacts/hf-artifacts-manifest.json`, script manifest/upload/verify, focused catalog tests, report, task matrix va plan log.
- Tom tat trien khai: Bat dau tu `ExternalAssetCatalog`, `LocalAiModelCatalog`, local `artifacts/drive-assets` va ADR-008; khong dung token HF da lo trong hoi thoai.
- Lenh kiem tra: Se cap nhat khi task hoan tat hoac checkpoint.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P10-T01-HF-ASSET-MANIFEST.md`
- Rui ro/cong viec con lai: Can token HF moi qua secret/environment de upload/verify dataset private; can source local/license card cho mot so artifact.
- Task tiep theo: Hoan tat P10.T01 hoac tiep tuc P10.T02 voi manifest allow-list neu upload secret chua san sang.

## 2026-07-31 04:44 - P10.T01 - CHECKPOINT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Ghi nhan phan in-repo cua HF asset manifest va catalog migration.
- Thoi gian bat dau/ket thuc: 2026-07-31 04:20 / dang thuc hien
- File thay doi: `ExternalAssetCatalog.kt`, `LocalAiModelCatalog.kt`, `HfArtifactManifestTest.kt`, `scripts/build-hf-asset-manifest.ps1`, `supabase/artifacts/hf-artifacts-manifest.json`, `reports/P10-T01-HF-ASSET-MANIFEST.md`, task matrix va plan log.
- Tom tat trien khai: Them `AssetDeliveryCatalog`, thay Drive URL bang `drducbook-asset://...`, tao manifest 35 artifact, verify 31 local ZIP bang SHA-256/size, danh dau Valtec/Hy-MT2 can mirror va Piper can license review.
- Lenh kiem tra: `powershell -ExecutionPolicy Bypass -File scripts/build-hf-asset-manifest.ps1`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.HfArtifactManifestTest" --no-daemon --console=plain`; `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `rg -n "drive\.google\.com|Google Drive/Legado System Assets" app/src/main/java app/src/test/java supabase scripts`.
- Ket qua: Manifest generation PASS; focused catalog/manifest tests 2 PASS; Kotlin compile BUILD SUCCESSFUL; main catalog khong con Drive URL. HF dataset public API tra 401 va khong co HF env token an toan nen upload/verify chua thuc hien.
- Bang chung: `reports/P10-T01-HF-ASSET-MANIFEST.md`; `supabase/artifacts/hf-artifacts-manifest.json`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.model.HfArtifactManifestTest.xml`.
- Rui ro/cong viec con lai: Can token HF moi da rotate trong secret/environment, source local hoac HF verify cho Valtec/Hy-MT2, va license card cho Piper truoc khi dong DONE.
- Task tiep theo: Tiep tuc P10.T02/P10.T03 phan local/Supabase contract neu upload secret chua san sang.

## 2026-07-31 04:46 - P10.T02 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Tao Supabase Edge Function cap ticket download HF khong lo HF token cho client.
- Thoi gian bat dau/ket thuc: 2026-07-31 04:46 / dang thuc hien
- File thay doi du kien: `supabase/functions/**`, `supabase/migrations/**`, ticket/range tests, report, task matrix va plan log.
- Tom tat trien khai: Bat dau bang manifest allow-list P10.T01; may khong co Deno/Supabase CLI nen uu tien shared logic test duoc bang Node.
- Lenh kiem tra: Se cap nhat khi checkpoint/hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P10-T02-SUPABASE-ASSET-TICKET.md`
- Rui ro/cong viec con lai: Can Supabase CLI/local stack va HF secret moi de dong gate runtime.
- Task tiep theo: Hoan tat P10.T02.

## 2026-07-31 05:02 - P10.T02 - CHECKPOINT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Ghi nhan Edge Function ticket/proxy, DB migration va test logic local.
- Thoi gian bat dau/ket thuc: 2026-07-31 04:46 / dang thuc hien
- File thay doi: `supabase/config.toml`, `asset_ticket.mjs`, `http.mjs`, `asset-ticket/index.ts`, `asset-download/index.ts`, `20260731044500_artifact_tickets.sql`, `scripts/test-asset-ticket.mjs`, `reports/P10-T02-SUPABASE-ASSET-TICKET.md`, task matrix va plan log.
- Tom tat trien khai: Them ticket HMAC TTL 10 phut, allow-list artifact, rate-limit 30 ticket/phut/user, Range normalization, HF resolve URL builder, one-time DB consume RPC, CORS va wrappers Edge Function. Function doc secret only qua env: `ASSET_TICKET_SECRET`, `SUPABASE_SERVICE_ROLE_KEY`, `HF_READ_TOKEN`.
- Lenh kiem tra: `node --test scripts/test-asset-ticket.mjs`; `node --check supabase/functions/_shared/asset_ticket.mjs`; `node --check supabase/functions/_shared/http.mjs`; secret scan regex cho HF/Supabase/ticket secrets.
- Ket qua: Node ticket/range tests 3 PASS; JS syntax check PASS; secret scan khong co match. `deno` va `supabase` khong co trong PATH nen chua serve/deploy/test local stack.
- Bang chung: `reports/P10-T02-SUPABASE-ASSET-TICKET.md`; `scripts/test-asset-ticket.mjs`; `supabase/migrations/20260731044500_artifact_tickets.sql`.
- Rui ro/cong viec con lai: Can local Supabase/Deno runtime, token HF moi, JWT/replay/range integration va Storage mirror flow cho artifact lon truoc khi dong DONE.
- Task tiep theo: P10.T03 local app downloader contract, hoac tiep tuc P10.T02 khi co CLI/secret.

## 2026-07-31 05:05 - P10.T03 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Chuyen app catalog/downloader contract khoi Drive sang Supabase asset-ticket/asset-download.
- Thoi gian bat dau/ket thuc: 2026-07-31 05:05 / dang thuc hien
- File thay doi du kien: app asset delivery contract/repository/downloader tests, report, task matrix va plan log.
- Tom tat trien khai: Bat dau tu URI noi bo P10.T01 va Edge Function contract P10.T02; Auth/session thuc te se phu thuoc P10.T04.
- Lenh kiem tra: Se cap nhat khi checkpoint/hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P10-T03-APP-ASSET-DELIVERY-CONTRACT.md`
- Rui ro/cong viec con lai: Can Auth/session, runtime function va downloader/install UX de dong task.
- Task tiep theo: Hoan tat P10.T03.

## 2026-07-31 05:14 - P10.T03 - CHECKPOINT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Ghi nhan client-side Supabase asset delivery contract.
- Thoi gian bat dau/ket thuc: 2026-07-31 05:05 / dang thuc hien
- File thay doi: `AssetDeliveryClientContract.kt`, `AssetDeliveryClientContractTest.kt`, `reports/P10-T03-APP-ASSET-DELIVERY-CONTRACT.md`, task matrix va plan log.
- Tom tat trien khai: App parse duoc `drducbook-asset://download|catalog`, build request `asset-ticket` bang Supabase JWT, build request `asset-download` bang opaque ticket/Range va scan khong chua HF/server secret. Catalog Android da bi khoa bang test khong con Drive URL.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "com.drducbook.app.cloud.AssetDeliveryClientContractTest" --tests "io.legado.app.domain.model.HfArtifactManifestTest" --no-daemon --console=plain`.
- Ket qua: PASS; 7 focused tests PASS; build compile phases trong test run thanh cong.
- Bang chung: `reports/P10-T03-APP-ASSET-DELIVERY-CONTRACT.md`; `app/build/test-results/testAppDebugUnitTest/TEST-com.drducbook.app.cloud.AssetDeliveryClientContractTest.xml`; `TEST-io.legado.app.domain.model.HfArtifactManifestTest.xml`.
- Rui ro/cong viec con lai: Chua co Auth/session va downloader/install runtime; UI van can noi URI noi bo vao UX tai/cai dat sau khi P10.T04 co tai khoan.
- Task tiep theo: P10.T04 Supabase Auth.

## 2026-07-31 05:56 - P10.T04 - CHECKPOINT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Them nen tang Supabase Auth email/Google va Account UI de lam co so cho cloud sync.
- Thoi gian bat dau/ket thuc: 2026-07-31 05:17 / dang thuc hien
- File thay doi: `libs.versions.toml`, `app/build.gradle.kts`, `AccountAuthModels.kt`, `AccountAuthGateway.kt`, `AccountAuthUseCase.kt`, `SupabaseAccountAuthRepository.kt`, `GoogleCredentialBridge.kt`, `AccountContract.kt`, `AccountViewModel.kt`, `AccountScreen.kt`, `appModule.kt`, `MainNavKey.kt`, `MainNavGraph.kt`, `MainNavigator.kt`, `MainIntent.kt`, `ConfigNavScreen.kt`, `ConfigTag.kt`, account/auth/navigation tests, report, task matrix va plan log.
- Tom tat trien khai: Them Credential Manager/Google ID dependency; tao gateway/usecase Supabase Auth; tao Google ID token bridge co nonce URL-safe va redaction; them Account screen Compose/MVI trong Settings; wire Koin/navigation/config tag; sua test route account.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain --stacktrace`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.AccountAuthModelsTest" --tests "io.legado.app.ui.account.AccountContractTest" --tests "io.legado.app.ui.main.MainNavigatorTest" --tests "io.legado.app.ui.main.MainIntentTest" --tests "com.drducbook.app.auth.DrDucBookDeepLinksTest" --tests "com.drducbook.app.ManifestIdentityTest" --tests "com.drducbook.app.cloud.SupabaseClientProviderTest" --tests "com.drducbook.app.cloud.CloudConsentScopesTest" --no-daemon --console=plain`; secret scan hep cho HF/Supabase/ticket secret literals.
- Ket qua: Kotlin compile BUILD SUCCESSFUL in 8m47s; focused Auth/navigation/callback/config suite BUILD SUCCESSFUL in 1m; secret scan hep khong co match.
- Bang chung: `reports/P10-T04-SUPABASE-AUTH.md`; 8 XML unit reports trong `app/build/test-results/testAppDebugUnitTest/`.
- Rui ro/cong viec con lai: Chua co Supabase local stack/Google OAuth runtime de verify happy/error/cancel/offline/process restart, email verification/reset va account collision/link policy tren device.
- Task tiep theo: P10.T05 Supabase Postgres/RLS/Storage sync foundation song song voi viec giu P10.T04 runtime gate mo.

## 2026-07-31 06:05 - P10.T05 - CHECKPOINT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Tao foundation Supabase Postgres/RLS/private Storage cho sync metadata va snapshot theo user.
- Thoi gian bat dau/ket thuc: 2026-07-31 05:57 / dang thuc hien
- File thay doi: `20260731060000_cloud_sync_foundation.sql`, `CloudSyncModels.kt`, `CloudSyncGateway.kt`, `CloudSyncUseCase.kt`, `CloudSyncClientContract.kt`, `SupabaseCloudSyncRepository.kt`, `CloudSyncClientContractTest.kt`, `scripts/test-cloud-sync-migration.mjs`, `appModule.kt`, report, task matrix va plan log.
- Tom tat trien khai: Them tables `profiles/cloud_devices/sync_snapshots/sync_heads/sync_events`, bat RLS/revoke anon, private buckets `drducbook-snapshots` va `drducbook-user-assets`, storage policies theo folder user id, snapshot immutable update-deny, app contract validate UUID/revision/hash/size/path ownership, DI gateway/usecase.
- Lenh kiem tra: `node --test scripts/test-cloud-sync-migration.mjs`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "com.drducbook.app.cloud.CloudSyncClientContractTest" --tests "com.drducbook.app.cloud.SupabaseClientProviderTest" --no-daemon --console=plain`; `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; secret scan hep.
- Ket qua: Node migration tests 4 PASS; Kotlin cloud sync tests PASS; Kotlin compile BUILD SUCCESSFUL; secret scan hep khong co match.
- Bang chung: `reports/P10-T05-SUPABASE-RLS-STORAGE-FOUNDATION.md`; XML unit reports trong `app/build/test-results/testAppDebugUnitTest/`.
- Rui ro/cong viec con lai: Chua co Supabase CLI/local stack de apply migration va test cross-user RLS/private Storage/signed URL/delete-account cleanup bang runtime that.
- Task tiep theo: P10.T06 Google Drive appDataFolder backup transport.

## 2026-07-31 06:12 - P10.T06 - CHECKPOINT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Giu backup Google Drive qua `appDataFolder` theo least privilege va tach khoi Google sign-in identity.
- Thoi gian bat dau/ket thuc: 2026-07-31 06:06 / dang thuc hien
- File thay doi: `GoogleDriveBackupModels.kt`, `GoogleDriveBackupGateway.kt`, `GoogleDriveBackupUseCase.kt`, `GoogleDriveAppDataContract.kt`, `GoogleDriveAppDataBackupRepository.kt`, `GoogleDriveAppDataContractTest.kt`, `appModule.kt`, report, task matrix va plan log.
- Tom tat trien khai: Them Drive appDataFolder contract chi xin `drive.appdata`, request list/upload trong `appDataFolder`, metadata namespace `drducbook`, account mismatch bang hash, token/request redaction va DI gateway/usecase.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "com.drducbook.app.cloud.GoogleDriveAppDataContractTest" --tests "com.drducbook.app.cloud.CloudConsentScopesTest" --no-daemon --console=plain`; `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; secret scan hep.
- Ket qua: Focused Drive contract tests PASS; Kotlin compile BUILD SUCCESSFUL; secret scan hep khong co match.
- Bang chung: `reports/P10-T06-GOOGLE-DRIVE-APPDATA-CONTRACT.md`; XML unit reports trong `app/build/test-results/testAppDebugUnitTest/`.
- Rui ro/cong viec con lai: Chua co AuthorizationClient/revoke/re-consent UI, encrypted Drive token store va Drive API runtime upload/download/resumable evidence.
- Task tiep theo: P10.T07 snapshot schema, conflict va restore.

## 2026-07-31 06:42 - P10.T07 - CHECKPOINT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Khoa snapshot schema, excluded runtime datasets, multi-target conflict classification va restore safety policy.
- Thoi gian bat dau/ket thuc: 2026-07-31 06:20 / dang thuc hien
- File thay doi: `CloudSnapshotModels.kt`, `CloudSnapshotPolicy.kt`, `CloudSnapshotPolicyTest.kt`, `reports/P10-T07-SNAPSHOT-CONFLICT-RESTORE.md`, task matrix va plan log.
- Tom tat trien khai: Them schema manifest v1 voi included/excluded dataset ro rang; validate path/hash/size/record count; classify local-only/target-only/conflict/Supabase-Drive divergence/invalid target; restore plan bat buoc verify hash truoc transactional commit.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.usecase.CloudSnapshotPolicyTest" --no-daemon --console=plain`; `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; secret scan hep cho HF/Supabase/ticket secret literals.
- Ket qua: `CloudSnapshotPolicyTest` BUILD SUCCESSFUL in 42s; `:app:compileAppDebugKotlin` BUILD SUCCESSFUL in 40s; secret scan hep khong co match.
- Bang chung: `reports/P10-T07-SNAPSHOT-CONFLICT-RESTORE.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.usecase.CloudSnapshotPolicyTest.xml`.
- Rui ro/cong viec con lai: Chua co snapshot builder/restorer runtime, conflict UI, optimistic CAS tren Supabase/Drive heads va device/runtime scenarios; chuyen sang P10.T08/P11 gates.
- Task tiep theo: P10.T08 cloud security, range/hash va sync tests.

## 2026-07-31 07:08 - P10.T08 - CHECKPOINT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Dong cac cloud security/integration gates co the chay local cho asset ticket, RLS/Storage migration, app cloud contracts, Drive appDataFolder va snapshot policy.
- Thoi gian bat dau/ket thuc: 2026-07-31 06:43 / dang thuc hien
- File thay doi: `scripts/test-cloud-security-gates.mjs`, `reports/P10-T08-CLOUD-SECURITY-INTEGRATION-GATES.md`, task matrix va plan log.
- Tom tat trien khai: Them Node gate scan secret/legacy Drive URL, validate HF manifest, dam bao `asset-ticket` khong doc HF token va chi persist ticket hash, `asset-download` consume ticket truoc HF fetch, artifact ticket RPC one-time service-role, RLS/private bucket/immutable snapshot/delete cascade van dung.
- Lenh kiem tra: `node --test scripts/test-asset-ticket.mjs scripts/test-cloud-sync-migration.mjs scripts/test-cloud-security-gates.mjs`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "com.drducbook.app.cloud.AssetDeliveryClientContractTest" --tests "io.legado.app.domain.model.HfArtifactManifestTest" --tests "com.drducbook.app.cloud.CloudSyncClientContractTest" --tests "com.drducbook.app.cloud.GoogleDriveAppDataContractTest" --tests "com.drducbook.app.cloud.CloudConsentScopesTest" --tests "com.drducbook.app.cloud.SupabaseClientProviderTest" --tests "io.legado.app.domain.usecase.CloudSnapshotPolicyTest" --no-daemon --console=plain`; `Get-Command supabase`; `Get-Command deno`.
- Ket qua: Node suite 13 PASS; Kotlin cloud contract suite BUILD SUCCESSFUL in 1m02s; `supabase` va `deno` khong co trong PATH.
- Bang chung: `reports/P10-T08-CLOUD-SECURITY-INTEGRATION-GATES.md`; XML cloud contract reports trong `app/build/test-results/testAppDebugUnitTest/`.
- Rui ro/cong viec con lai: Chua the apply Supabase local stack/serve Edge Functions/test JWT revoke/replay/RLS cross-user/Storage signed URL/Drive OAuth runtime/multi-device restore do thieu Supabase CLI, Deno va account/OAuth runtime.
- Task tiep theo: P11.T01 full build/test matrix voi nhung gate co the chay trong workspace hien tai.

## 2026-07-31 08:16 - P11.T01 - SOURCE EXPLORE COMPAT HOTFIX

- Nguoi/agent thuc hien: Codex
- Muc tieu: Sua hoi quy Khám phá/source sau nang cap: source va danh muc hien nhung danh sach sach trang.
- Thoi gian bat dau/ket thuc: 2026-07-31 07:30 / 2026-07-31 08:16
- File thay doi: `ExploreBooksGateway.kt`, `ExploreRepository.kt`, `ExploreBooksUseCase.kt`, `VbookPluginAdapter.kt`, `ExploreShowViewModel.kt`, `ExploreScreen.kt`, `ExploreBooksUseCaseTest.kt`, `reports/P11-T01-SOURCE-EXPLORE-COMPAT-HOTFIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Chon URL danh muc dau tien da parse thay vi dua nguyen `exploreUrl` vao parser; VBook `vbook://home` resolve sang discover URL that; preview Khám phá hien loi/rong + nut thu lai.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.usecase.ExploreBooksUseCaseTest" --no-daemon --console=plain`; `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain`; `adb install -r app-app-x86_64-debug.apk`; LDPlayer screenshot smoke.
- Ket qua: Unit test moi 2/2 PASS theo XML; Kotlin compile PASS; assemble debug PASS; cai de LDPlayer PASS; Khám phá hien sach/bia cho source `Legado`.
- Bang chung: `reports/P11-T01-SOURCE-EXPLORE-COMPAT-HOTFIX.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.usecase.ExploreBooksUseCaseTest.xml`; screenshot tam `C:\Users\vanki\AppData\Local\Temp\drducbook-after-fix.png`.
- Rui ro/cong viec con lai: Mot so source trong DB debug da bi dich vao rule/JS ky thuat; can import lai tu goi source sach neu source rieng le van hong do du lieu bi bien doi.

## 2026-07-31 09:05 - P11.T01-HOTFIX - CORRECTION

- Nguoi/agent thuc hien: Codex
- Muc tieu: Ghi dung phan fix cuoi cua hoi quy Khám phá/source.
- Thoi gian bat dau/ket thuc: 2026-07-31 08:45 / 2026-07-31 09:05
- File thay doi: `app/src/main/java/io/legado/app/di/appModule.kt`, `docs/plans/drducbook-rebuild-2026/reports/P11-T01-SOURCE-EXPLORE-COMPAT-HOTFIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Bind `AndroidCookieVaultCodec` theo interface `CookieVaultCodec`, de `CookieVaultRepository`/`SourceCookieGateway` duoc Koin tao dung trong runtime Khám phá.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain`; `adb -s emulator-5554 install -r app\build\outputs\apk\app\debug\app-app-x86_64-debug.apk`; LDPlayer smoke Khám phá; logcat filter `InstanceCreationException|NoBeanDefFoundException|SourceCookieGateway`.
- Ket qua: Compile PASS; assemble debug PASS; install PASS; nguồn `Thân Sĩ Truyện Tranh (WNACG)` hiển thị danh sách sách/bìa; logcat không còn lỗi Koin/SourceCookieGateway.
- Bang chung: `reports/P11-T01-SOURCE-EXPLORE-COMPAT-HOTFIX.md`; screenshot tam `C:\Users\vanki\AppData\Local\Temp\drducbook_screen_explore_02.png`.
- Rui ro/cong viec con lai: Hotfix nay la regression ad-hoc, khong thay the P11.T01 full build/test matrix trong task matrix.
- Task tiep theo: Tiep tuc P10.T03 app asset delivery/downloader.

## 2026-07-31 11:36 - P10.T03 - COMPLETED APP DOWNLOADER

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien app-side downloader cho package/model catalog da migrate khoi Drive.
- Thoi gian bat dau/ket thuc: 2026-07-31 11:05 / 2026-07-31 11:36
- File thay doi: `AssetDeliveryModels.kt`, `AssetDeliveryGateway.kt`, `AssetDeliveryUseCase.kt`, `AssetDeliveryRepository.kt`, `AssetDeliveryContract.kt`, `AssetDeliveryViewModel.kt`, `AssetDeliveryScreen.kt`, `MainActivity.kt`, `MainIntent.kt`, `MainNavKey.kt`, `MainNavigator.kt`, `MainNavGraph.kt`, `ContextExtensions.kt`, `appModule.kt`, strings EN/VI, `AssetDeliveryCatalogResolverTest.kt`, `reports/P10-T03-APP-ASSET-DELIVERY-CONTRACT.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: `drducbook-asset://download|catalog` duoc resolve trong app; `openUrl` mo route downloader noi bo; downloader lay Supabase ticket bang session JWT, tai file bang opaque ticket, ghi file tam va chi expose file khi size/SHA-256 khop catalog; UI Compose/MVI cho chon catalog, tai/huy/tai lai/mo file va mo Account khi can dang nhap. Cac mo ta package khong con noi Drive.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.AssetDeliveryCatalogResolverTest" --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "com.drducbook.app.cloud.AssetDeliveryClientContractTest" --tests "io.legado.app.domain.model.HfArtifactManifestTest" --tests "io.legado.app.domain.model.AssetDeliveryCatalogResolverTest" --no-daemon --console=plain`; `rg -n "drive\.google\.com|hf_[A-Za-z0-9]{30,}" app/src/main/java app/src/test/java supabase scripts`.
- Ket qua: Kotlin compile PASS; resolver test 4 PASS; focused asset/manifest suite 11 PASS; secret/Drive scan khong co HF token, chi con 2 match `drive.google.com` trong test assertion cam Drive URL.
- Bang chung: `reports/P10-T03-APP-ASSET-DELIVERY-CONTRACT.md`; `app/build/test-results/testAppDebugUnitTest/TEST-com.drducbook.app.cloud.AssetDeliveryClientContractTest.xml`; `TEST-io.legado.app.domain.model.HfArtifactManifestTest.xml`; `TEST-io.legado.app.domain.model.AssetDeliveryCatalogResolverTest.xml`.
- Rui ro/cong viec con lai: Can runtime Supabase/HF live cho expired/replay ticket, Range, corrupt/offline/resume va artifact lon; gate nay tiep tuc nam o P10.T08/P11.
- Task tiep theo: Quay lai P11.T01 full build/test matrix hoac chay smoke device cho downloader khi co Supabase session/backend.

## 2026-07-31 11:54 - P10.T07 - SNAPSHOT ARCHIVE/CONFLICT/STAGING CHECKPOINT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Bien snapshot policy thanh artifact `.drducsnapshot` co the upload/verify, khoa plan lua chon conflict va them staging truoc restore.
- Thoi gian bat dau/ket thuc: 2026-07-31 11:39 / 2026-07-31 11:54
- File thay doi: `CloudSnapshotModels.kt`, `CloudSnapshotPolicy.kt`, `CloudSnapshotArchive.kt`, `CloudSnapshotRestoreStaging.kt`, `CloudSnapshotPolicyTest.kt`, `CloudSnapshotArchiveTest.kt`, `CloudSnapshotRestoreStagingTest.kt`, `reports/P10-T07-SNAPSHOT-CONFLICT-RESTORE.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Them ZIP archive builder/reader voi `manifest.json` va `entries/{dataset}.json`; archive tinh SHA-256/size, reject excluded dataset, path traversal, duplicate/undeclared file va checksum mismatch. Them resolution plan: auto chi cho no-change/local-only/target-only; conflict/targets-diverged/invalid bat buoc user choice; restore khi Supabase/Drive diverged phai chon ro target. Them restore staging verify archive va ghi payload vao thu muc rieng, khong commit vao DB/file runtime.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.usecase.CloudSnapshotArchiveTest" --tests "io.legado.app.domain.usecase.CloudSnapshotPolicyTest" --tests "io.legado.app.domain.usecase.CloudSnapshotRestoreStagingTest" --no-daemon --console=plain`; `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`.
- Ket qua: Snapshot focused suite BUILD SUCCESSFUL; `CloudSnapshotArchiveTest` 5 PASS; `CloudSnapshotPolicyTest` 11 PASS; `CloudSnapshotRestoreStagingTest` 3 PASS; Kotlin compile PASS.
- Bang chung: `reports/P10-T07-SNAPSHOT-CONFLICT-RESTORE.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.usecase.CloudSnapshotArchiveTest.xml`; `TEST-io.legado.app.domain.usecase.CloudSnapshotPolicyTest.xml`; `TEST-io.legado.app.domain.usecase.CloudSnapshotRestoreStagingTest.xml`.
- Rui ro/cong viec con lai: P10.T07 van IN_PROGRESS theo phase spec do chua co Supabase/Drive upload/download adapters, head CAS, conflict UI/use cases, subsystem snapshot adapters day du va runtime scenarios.
- Task tiep theo: Tiep tuc P10.T07 adapters/CAS/UI hoac chuyen P10.T08/P11 khi can integration gates.

## 2026-07-31 12:04 - P10.T07 - ADAPTER/CAS CHECKPOINT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Khoa domain contract cho subsystem snapshot adapters va optimistic compare-and-set head writes.
- Thoi gian bat dau/ket thuc: 2026-07-31 11:56 / 2026-07-31 12:04
- File thay doi: `CloudSnapshotModels.kt`, `CloudSnapshotPolicy.kt`, `CloudSnapshotDatasetAdapter.kt`, `CloudSnapshotPolicyTest.kt`, `CloudSnapshotDatasetAdapterTest.kt`, `reports/P10-T07-SNAPSHOT-CONFLICT-RESTORE.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Them `CloudSnapshotHeadWritePlan` va CAS verify de che do `BOTH` tao hai plan doc lap cho Supabase/Drive; reject observed head sai target/incomplete/concurrent change. Them `CloudSnapshotDatasetAdapter`, registry va `CloudSnapshotUseCase` de reject missing/duplicate/excluded/non-transactional adapters, build archive tu included datasets va validate toan bo staged entries truoc khi commit restore adapter.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.usecase.CloudSnapshotDatasetAdapterTest" --tests "io.legado.app.domain.usecase.CloudSnapshotPolicyTest" --tests "io.legado.app.domain.usecase.CloudSnapshotArchiveTest" --tests "io.legado.app.domain.usecase.CloudSnapshotRestoreStagingTest" --no-daemon --console=plain`; `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`.
- Ket qua: Snapshot suite 28 tests PASS; `CloudSnapshotDatasetAdapterTest` 6 PASS; `CloudSnapshotPolicyTest` 14 PASS; `CloudSnapshotArchiveTest` 5 PASS; `CloudSnapshotRestoreStagingTest` 3 PASS; Kotlin compile PASS.
- Bang chung: `reports/P10-T07-SNAPSHOT-CONFLICT-RESTORE.md`; XML reports trong `app/build/test-results/testAppDebugUnitTest/`.
- Rui ro/cong viec con lai: P10.T07 van IN_PROGRESS do chua co runtime adapter cho Room/file subsystems, Supabase/Drive upload/download va CAS implementation that, conflict UI va Supabase/Drive integration scenarios.
- Task tiep theo: Tiep tuc P10.T07 runtime adapters/UI hoac P10.T08/P11 runtime gate khi co Supabase/Drive environment.

## 2026-07-31 12:05 - P11.T01 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Chay full build/test matrix kha thi trong workspace sau P10 va hotfix Khám phá.
- Thoi gian bat dau/ket thuc: 2026-07-31 12:05 / dang thuc hien
- File thay doi: `reports/P11-T01-FULL-BUILD-TEST-MATRIX.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Bat dau gate Android compile/unit/lint/assemble, Web type/build va backend/local cloud Node tests; cac gate Supabase/Drive/HF runtime that se ghi ro neu moi truong thieu CLI/secret/account.
- Lenh kiem tra: Se cap nhat khi tung gate hoan tat.
- Ket qua: Dang thuc hien.
- Bang chung: `reports/P11-T01-FULL-BUILD-TEST-MATRIX.md`.
- Rui ro/cong viec con lai: Full matrix co the phat hien hoi quy tu cac phase truoc; khong dong DONE neu mandatory local gate fail.
- Task tiep theo: Hoan tat P11.T01 hoac fix hoi quy dau tien neu matrix fail.

## 2026-07-31 12:32 - P10.T01 - HF UPLOAD SCRIPT CHECKPOINT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Chuan bi upload artifact tu Google Drive export sang dataset `Drduc/Legadofork` va ghi ro gate token/source.
- Thoi gian bat dau/ket thuc: 2026-07-31 12:18 / 2026-07-31 12:32
- File thay doi: `scripts/upload-hf-artifacts.ps1`, `reports/P10-T01-HF-ASSET-MANIFEST.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Them script upload Git LFS doc token tu `HF_TOKEN`/`HUGGINGFACE_HUB_TOKEN`, verify size/SHA-256 truoc khi copy, day 31 artifact local verified vao dung `hfPath` va upload manifest vao `manifest/hf-artifacts-manifest.json`; khong in token va xoa `_netrc` tam sau khi chay. Ghi nhan Supabase project URL public da nhan la `https://faegbafmkpsocoecrhvz.supabase.co`, nhung app/runtime van can `SUPABASE_PUBLISHABLE_KEY`.
- Lenh kiem tra: `.\scripts\upload-hf-artifacts.ps1 -DryRun`; `try { .\scripts\upload-hf-artifacts.ps1 } catch { $_.Exception.Message }`.
- Ket qua: Dry run PASS; 31 artifact local verified san sang upload, tong 1,753,597,758 bytes; 4 artifact metadata-only con thieu source (`hy-mt2-1.8b-stq-stride16`, `hy-mt2-1.8b-v2`, `hy-mt2-1.8b-v2-stq42`, `tts-valtec-vietnamese`). Upload that chua chay vi khong co token HF trong environment.
- Bang chung: `reports/P10-T01-HF-ASSET-MANIFEST.md`; output dry run trong terminal task.
- Rui ro/cong viec con lai: Token HF da tung paste trong chat khong duoc dung lai; can dat token moi/da rotate vao environment ngoai log, bo sung source Valtec/Hy-MT2 dung hash, va review license Piper truoc khi dong P10.T01 DONE.
- Task tiep theo: Chay `.\scripts\upload-hf-artifacts.ps1` khi `HF_TOKEN` duoc set ngoai chat; sau do verify HF tree va cap nhat P10.T01/P10.T02.

## 2026-07-31 12:34 - P11.T01 - CHECKPOINT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Ghi nhan ket qua full matrix da chay truoc khi chuyen huong sang HF upload.
- Thoi gian bat dau/ket thuc: 2026-07-31 12:05 / dang thuc hien
- File thay doi: `reports/P11-T01-FULL-BUILD-TEST-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Node cloud/local backend gate pass; Android full unit pass; lenh lint + debug/noR8/release assemble gop chung bi timeout sau 904s nen chua duoc tinh la pass/fail; da stop Gradle daemon.
- Lenh kiem tra: `node --test scripts/test-asset-ticket.mjs scripts/test-cloud-sync-migration.mjs scripts/test-cloud-security-gates.mjs`; `.\gradlew.bat :app:testAppDebugUnitTest --no-daemon --console=plain`; `.\gradlew.bat :app:lint :app:assembleAppDebug :app:assembleAppNoR8 :app:assembleAppRelease --no-daemon --console=plain`; `.\gradlew.bat --stop`.
- Ket qua: Node 13 PASS; Android unit BUILD SUCCESSFUL in 2m15s; combined lint/assemble timed out after 904s.
- Bang chung: `reports/P11-T01-FULL-BUILD-TEST-MATRIX.md`; Gradle unit XML reports trong `app/build/test-results/testAppDebugUnitTest/`.
- Rui ro/cong viec con lai: Can tach lint/debug/noR8/release thanh cac lenh rieng hoac tang timeout de dong P11.T01.
- Task tiep theo: Quay lai P11.T01 sau khi hoan tat/upload HF neu nguoi dung yeu cau tiep tuc release matrix.

## 2026-07-31 15:39 - P11.T01/P10.T01 - BUILD MATRIX AND HF TOKEN CHECKPOINT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Dong them cac gate local con thieu cua P11.T01 va chuan bi upload HF bang token environment khong in ra log.
- Thoi gian bat dau/ket thuc: 2026-07-31 14:30 / 2026-07-31 15:39
- File thay doi: `scripts/upload-hf-artifacts.ps1`, `reports/P10-T01-HF-ASSET-MANIFEST.md`, `reports/P11-T01-FULL-BUILD-TEST-MATRIX.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Mo rong script HF de doc token tu Process/User/Machine environment voi `HF_TOKEN`, `HUGGINGFACE_HUB_TOKEN`, `HF_WRITE_TOKEN`, `HUGGINGFACE_TOKEN`; dry-run van verify 31 artifact local. Chay lai web type/build, Android compile/debug/noR8/release assemble rieng va ghi checksum APK.
- Lenh kiem tra: `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\upload-hf-artifacts.ps1 -DryRun`; `pnpm type-check`; `pnpm build`; `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain`; `.\gradlew.bat :app:assembleAppNoR8 --no-daemon --console=plain`; `.\gradlew.bat :app:packageAppRelease --no-daemon --console=plain --stacktrace`; `.\gradlew.bat :app:assembleAppRelease --no-daemon --console=plain`; `.\gradlew.bat --stop`.
- Ket qua: HF dry-run PASS 31 ready/4 metadata-only; token HF van khong doc duoc tu Process/User/Machine environment; web type-check PASS; web build PASS va sync asset; compile PASS; debug assemble PASS; noR8 assemble PASS; release package PASS sau retry; release assemble PASS; unit XML aggregate 893 tests, 0 failures, 0 errors, 1 skipped; lint rieng bi interruption va khong sinh `lint-results*` report.
- Bang chung: `reports/P10-T01-HF-ASSET-MANIFEST.md`; `reports/P11-T01-FULL-BUILD-TEST-MATRIX.md`; APK checksums trong P11 report; unit XML trong `app/build/test-results/testAppDebugUnitTest`.
- Rui ro/cong viec con lai: P10.T01 upload that can token environment ma Codex doc duoc; P11.T01 van chua DONE do lint chua co ket qua sach va cac runtime gate Supabase/Drive/HF that van thieu.
- Task tiep theo: Chay HF upload khi token xuat hien hoac chay lai lint bang gate rieng neu khong bi ngat.

## 2026-07-31 16:20 - P10.T01 - LOCAL HF TOKEN WRAPPER

- Nguoi/agent thuc hien: Codex
- Muc tieu: Tao cach van hanh upload HF theo yeu cau nguoi dung muon tu sua token trong script, dong thoi han che token vao source/report.
- Thoi gian bat dau/ket thuc: 2026-07-31 16:05 / 2026-07-31 16:20
- File thay doi: `.secrets/upload-hf-artifacts.with-token.local.ps1`, `reports/P10-T01-HF-ASSET-MANIFEST.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Tao wrapper local trong `.secrets/` da duoc git-ignore; token nam o placeholder dau file do nguoi dung tu thay tren may; wrapper set `HF_TOKEN` chi trong process con va goi `scripts/upload-hf-artifacts.ps1`.
- Lenh kiem tra: `powershell -NoProfile -ExecutionPolicy Bypass -File .\.secrets\upload-hf-artifacts.with-token.local.ps1 -DryRun`.
- Ket qua: Guard placeholder PASS, script dung voi thong bao yeu cau thay token truoc khi upload.
- Bang chung: `.secrets/upload-hf-artifacts.with-token.local.ps1`; `reports/P10-T01-HF-ASSET-MANIFEST.md`.
- Rui ro/cong viec con lai: Sau khi nguoi dung thay token that trong wrapper local, can chay upload that va verify HF dataset tree/download URLs.
- Task tiep theo: Chay `powershell -NoProfile -ExecutionPolicy Bypass -File .\.secrets\upload-hf-artifacts.with-token.local.ps1` sau khi nguoi dung xac nhan da sua token.

## 2026-07-31 17:13 - P10.T01 - HF DATASET UPLOAD VERIFIED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Upload cac package/model local tu manifest len dataset Hugging Face `Drduc/Legadofork` de thay the luong tai tu Google Drive.
- Thoi gian bat dau/ket thuc: 2026-07-31 16:55 / 2026-07-31 17:13
- File thay doi: `scripts/upload-hf-artifacts.ps1`, `.secrets/upload-hf-artifacts.with-token.local.ps1`, `reports/P10-T01-HF-ASSET-MANIFEST.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Sua uploader de `git add` chi nhung path ton tai vi `models/` chua co file local; them git user config local cho repo tam; chuyen wrapper local sang `%TEMP%\drducbook-hf-upload-work` tren o C de tranh o D gan day. Upload that 31 artifact local len Hugging Face bang Git LFS.
- Lenh kiem tra: `powershell -NoProfile -ExecutionPolicy Bypass -File .\.secrets\upload-hf-artifacts.with-token.local.ps1`; `Invoke-WebRequest https://huggingface.co/datasets/Drduc/Legadofork/raw/main/manifest/hf-artifacts-manifest.json`; `git ls-remote https://huggingface.co/datasets/Drduc/Legadofork refs/heads/main`; `curl.exe -I -L https://huggingface.co/datasets/Drduc/Legadofork/resolve/main/packages/translation/legado-qt-clean-20260721.zip`; `curl.exe -I -L https://huggingface.co/datasets/Drduc/Legadofork/resolve/main/packages/tts/piper/legado-tts-piper-yannew-20260721.zip`.
- Ket qua: HF upload PASS; commit `0171747e034c3d881dc6e182a5130a5d12b20872`; LFS 31/31 objects uploaded, 1.8GB; manifest raw HTTP 200 voi 35 artifact; sample translation ZIP va Piper ZIP deu final HTTP 200 qua CDN.
- Bang chung: `reports/P10-T01-HF-ASSET-MANIFEST.md`; HF commit `0171747e034c3d881dc6e182a5130a5d12b20872`.
- Rui ro/cong viec con lai: Valtec va 3 Hy-MT2 van metadata-only vi chua co source local; Piper license/provenance chua dong; Supabase Edge Function secret/runtime va private dataset gate con thuoc P10.T02/P10.T08. Cac thu muc upload tam tren o D tu lan fail truoc dang chiem dung luong, shell chan xoa de quy nen can don thu cong.
- Task tiep theo: Cai dat Supabase HF secret/Edge Function runtime hoac bo sung source Valtec/Hy-MT2; sau do tiep tuc P11 lint/runtime gates.

## 2026-07-31 19:42 - P11.T01 - LINT FIX AND APK REBUILD CHECKPOINT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Tiep tuc P11.T01 sau upload HF, don temp de co dung luong build, xu ly loi lint `NewApi` va rebuild APK matrix.
- Thoi gian bat dau/ket thuc: 2026-07-31 17:20 / 2026-07-31 19:42
- File thay doi: `scripts/cleanup-hf-upload-work.ps1`, `app/src/main/java/io/legado/app/ui/widget/components/effect/BgEffectBackground.kt`, `reports/P10-T01-HF-ASSET-MANIFEST.md`, `reports/P11-T01-FULL-BUILD-TEST-MATRIX.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Them cleanup script co guard duong dan va don cac repo upload tam `Legadofork-*`, giai phong o D tu ~9MB len >4GB. Sua `BgEffectBackground` bang fallback Android < 33 va helper `BgEffectBackgroundApi33` annotate `@RequiresApi(TIRAMISU)` de khong khoi tao `BgEffectPainter` tren API thap. Rebuild debug/noR8/release APK.
- Lenh kiem tra: `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\cleanup-hf-upload-work.ps1`; `.\gradlew.bat :app:lintAppDebug --no-daemon --console=plain`; `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain`; `.\gradlew.bat :app:assembleAppRelease --no-daemon --console=plain`; `.\gradlew.bat :app:assembleAppNoR8 --no-daemon --console=plain`.
- Ket qua: Cleanup PASS; `lintAppDebug` truoc fix fail voi 1 error `NewApi`; compile PASS sau fix; debug assemble PASS; release assemble PASS in 16m03s; noR8 assemble PASS in 11m49s. Lint sau fix khong tao report moi trong timeout/poll dai, da stop Gradle daemon, nen full lint van chua tinh PASS.
- Bang chung: `reports/P11-T01-FULL-BUILD-TEST-MATRIX.md`; APK checksums moi trong report; `app/build/outputs/apk/app/*`.
- Rui ro/cong viec con lai: Can mot clean lint report hoac quyet dinh lint timeout gate; can tiep tuc P11.T02-P11.T07 va cac runtime gate Supabase/Drive/HF private.
- Task tiep theo: Chay tiep gate P11 co the thuc hien tren device/local, hoac cau hinh Supabase CLI/secret de dong P10.T02/P10.T08 runtime.

## 2026-07-31 19:50 - P11.T02 - SIDE-BY-SIDE DEVICE SMOKE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Kiem tra app moi cai song song va launch doc lap voi ban cu va VBook tren emulator hien co.
- Thoi gian bat dau/ket thuc: 2026-07-31 19:43 / 2026-07-31 19:50
- File thay doi: `reports/P11-T02-SIDE-BY-SIDE-REGRESSION.md`, `reports/artifacts/p11-side-by-side-new-app.png`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Xac nhan device `emulator-5554` online; package `com.drducbook.app.debug`, `io.legato.kazusa.debug`, `com.vbook.android` cung ton tai; cai lai APK debug moi; launch app moi va app cu; kiem tra PID/logcat va dataDir rieng.
- Lenh kiem tra: `adb devices -l`; `adb install -r app-app-x86_64-debug.apk`; `adb shell am start -S -n com.drducbook.app.debug/io.legado.app.ui.main.MainActivity`; `adb shell am start -S -n io.legato.kazusa.debug/io.legado.app.ui.main.MainActivity`; `adb shell dumpsys package ...`; `adb logcat -d -t 300`; `adb shell screencap`.
- Ket qua: Install PASS; app moi PID `4733`; app cu PID `5002`; dataDir moi/cu/VBook tach rieng; khong thay crash/Koin trong smoke logcat; screenshot UI `Gia sach` da luu.
- Bang chung: `reports/P11-T02-SIDE-BY-SIDE-REGRESSION.md`; `reports/artifacts/p11-side-by-side-new-app.png`.
- Rui ro/cong viec con lai: Chua chay data mutation/source import/export side-by-side va P11.T03 compatibility corpus tren device.
- Task tiep theo: Tiep tuc P11.T03 Legado/VBook compatibility regression hoac bo sung side-by-side data-flow smoke.

## 2026-07-31 19:55 - P10.T02 - RUNTIME TOOLING RECHECK

- Nguoi/agent thuc hien: Codex
- Muc tieu: Cap nhat P10.T02 sau khi HF dataset da upload thanh cong va kiem tra lai tooling Supabase runtime.
- Thoi gian bat dau/ket thuc: 2026-07-31 19:51 / 2026-07-31 19:55
- File thay doi: `reports/P10-T02-SUPABASE-ASSET-TICKET.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Ghi nhan HF manifest/artifact live da verify o commit `0171747`, khong con blocker upload public cho asset ticket. Kiem tra lai `supabase` va `deno` van khong co trong PATH; `node/npm/npx` co.
- Lenh kiem tra: `Get-Command supabase`; `Get-Command deno`; `Get-Command node,npm,npx,pnpm`.
- Ket qua: Supabase CLI/Deno chua co; runtime Edge Function private/proxy van chua the deploy/serve trong moi truong hien tai neu khong cai tooling va cau hinh secret/access token.
- Bang chung: `reports/P10-T02-SUPABASE-ASSET-TICKET.md`; `reports/P10-T01-HF-ASSET-MANIFEST.md`.
- Rui ro/cong viec con lai: Can Supabase access token/project secret va CLI/Deno de dong P10.T02/P10.T08 runtime; token HF trong `.secrets` nen xoa/rotate sau khi dua vao Supabase secret.
- Task tiep theo: Cai Supabase CLI/secret runtime hoac tiep tuc P11.T03 device compatibility.

## 2026-07-31 19:57 - P10.T01 - LOCAL TOKEN SCRUB

- Nguoi/agent thuc hien: Codex
- Muc tieu: Xoa token HF khoi wrapper local sau khi upload da verify.
- Thoi gian bat dau/ket thuc: 2026-07-31 19:56 / 2026-07-31 19:57
- File thay doi: `.secrets/upload-hf-artifacts.with-token.local.ps1`, `reports/P10-T01-HF-ASSET-MANIFEST.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Dua `$HfToken` trong wrapper local ve placeholder `PASTE_HF_WRITE_TOKEN_HERE`, khong in hoac ghi lai token that.
- Lenh kiem tra: Kiem tra regex dong `$HfToken` chi tra `Placeholder=True`, khong in gia tri secret.
- Ket qua: Token local da scrub thanh cong.
- Bang chung: `reports/P10-T01-HF-ASSET-MANIFEST.md`.
- Rui ro/cong viec con lai: Neu can deploy Supabase private HF fetch, hay tao/rotate token moi va dua vao Supabase secret `HF_READ_TOKEN`, khong dua lai vao source/chat.
- Task tiep theo: P11.T03/P10.T02 runtime.

## 2026-07-31 20:42 - P09.T08 - VBOOK MEDIA PLAYER HOTFIX

- Nguoi/agent thuc hien: Codex
- Muc tieu: Sua loi nguon video Legado/VBook mo nhu reader/browser va bo sung download/settings player theo mau VBook nguoi dung gui.
- Thoi gian bat dau/ket thuc: 2026-07-31 20:00 / 2026-07-31 20:42
- File thay doi: `help/book/BookExtensions.kt`, `help/vbook/VbookPluginAdapter.kt`, `ui/main/MainNavGraph.kt`, `ui/book/info/BookInfoViewModel.kt`, `help/config/MediaPlayerConfig.kt`, `constant/PreferKey.kt`, `domain/model/ResolvedMedia.kt`, `service/MediaPlaybackService.kt`, `ui/media/player/**`, `values*/strings.xml`, `ExploreBookOpenPolicyTest.kt`, `reports/P09-T08-MEDIA-PLAYER-VBOOK-HOTFIX.md`, `TASK-MATRIX.md`, `PHASE-09-MEDIA.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Them chuan hoa `Book.type` theo `BookSource.bookSourceType` va ap dung cho VBook parse/Kham pha/BookInfo; them player settings sheet gom tu phat, tu chuyen tap, resume, tua 5/10/15/30 giay, giu man hinh, do sang/am luong, phu de; them nut tai tap ro trong controls; playback service ton trong tuy chon khong resume vi tri cu.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.ui.main.ExploreBookOpenPolicyTest" --tests "io.legado.app.help.media.MediaSourceRuleResultParserTest" --no-daemon --console=plain`; `.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain`; `adb install -r app/build/outputs/apk/app/debug/app-app-x86_64-debug.apk`; `adb shell am start -S -n com.drducbook.app.debug/io.legado.app.ui.main.MainActivity`; logcat fatal scan.
- Ket qua: Compile PASS; focused unit tests PASS; debug APK assemble PASS; LDPlayer install/launch smoke PASS, PID `6318`, version `3.26.13_debug`, ABI `x86_64`. Mot lan test song song bi loi tam thoi `R.jar already contains entry ...`, chay lai chung mot Gradle invocation da PASS.
- Bang chung: `reports/P09-T08-MEDIA-PLAYER-VBOOK-HOTFIX.md`; test XML cho `ExploreBookOpenPolicyTest` va `MediaSourceRuleResultParserTest`; debug APK trong `app/build/outputs/apk/app/debug/`; LDPlayer package `com.drducbook.app.debug`.
- Rui ro/cong viec con lai: Can mo dung nguon video LDPlayer cua nguoi dung de xac nhan man hinh player/no-browser va download queue bang media that.
- Task tiep theo: Cai APK debug moi len LDPlayer va mo lai nguon video mau; neu pass, tiep tuc P11.T03 compatibility regression.

## 2026-08-01 05:43 - P11.T03 - VBOOK REGEX AND COMIC CONTENT HOTFIX

- Nguoi/agent thuc hien: Codex
- Muc tieu: Sua loi reader hien literal `<p>/<br>`, nguon truyen tranh VBook khong tai anh, va nguon comic bi mo nhu reader truyen chu do type cu/alias comic.
- Thoi gian bat dau/ket thuc: 2026-08-01 05:20 / 2026-08-01 05:43
- File thay doi: `VbookPluginAdapter.kt`, `VbookRegistryModels.kt`, `VbookPluginAdapterTest.kt`, `VbookPluginImporterTest.kt`, `reports/P11-T03-LEGADO-VBOOK-COMPATIBILITY-REGRESSION.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Them text HTML cleanup cho VBook chap; chuyen chap comic tu URL anh tran/object/image array/HTML img thanh `<img src="...">`; bo sung alias comic Viet hoa; tu reconcile type source VBook da cai cu ve image/audio/video khi profile plugin xac nhan.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests io.legado.app.help.vbook.VbookPluginAdapterTest --tests io.legado.app.help.vbook.VbookPluginImporterTest`.
- Ket qua: BUILD SUCCESSFUL in 7m30s; VBook adapter/importer tests PASS; `:app:compileAppDebugKotlin` PASS trong cung Gradle invocation.
- Bang chung: `reports/P11-T03-LEGADO-VBOOK-COMPATIBILITY-REGRESSION.md`; Gradle XML reports trong `app/build/test-results/testAppDebugUnitTest/`.
- Rui ro/cong viec con lai: Noi dung chuong da cache tren device co the can tai lai/xoa cache de thay `<img>`/text moi; can cai APK moi len LDPlayer va mo lai nguon comic that.
- Task tiep theo: Build/cai APK debug moi va smoke nguon truyen tranh tren LDPlayer neu device dang online.

## 2026-08-01 05:49 - P11.T03 - APK INSTALL SMOKE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Build va cai ban debug co hotfix VBook regex/comic len LDPlayer hien co.
- Thoi gian bat dau/ket thuc: 2026-08-01 05:43 / 2026-08-01 05:49
- File thay doi: `reports/P11-T03-LEGADO-VBOOK-COMPATIBILITY-REGRESSION.md`, `PLAN-LOG.md`; APK output trong `app/build/outputs/apk/app/debug/`.
- Tom tat trien khai: Build debug APK moi, cai ban x86_64 len `emulator-5554`, launch app moi va quet logcat fatal.
- Lenh kiem tra: `.\gradlew.bat :app:assembleAppDebug`; `adb devices -l`; `adb install -r -t app\build\outputs\apk\app\debug\app-app-x86_64-debug.apk`; `adb shell am start -S -n com.drducbook.app.debug/io.legado.app.ui.main.MainActivity`; logcat filter `FATAL`, `AndroidRuntime`, `VbookPluginException`.
- Ket qua: Assemble PASS in 4m13s; install PASS; launch PASS; PID `4235`; `versionName=3.26.13_debug`; fatal filters empty.
- Bang chung: `reports/P11-T03-LEGADO-VBOOK-COMPATIBILITY-REGRESSION.md`; debug APK `app-app-x86_64-debug.apk`.
- Rui ro/cong viec con lai: Chua dieu khien UI den mot nguon truyen tranh cu the trong smoke nay; neu chuong da cache noi dung cu, can tai lai/xoa cache chuong de thay output moi.
- Task tiep theo: Nguoi dung mo lai nguon comic dang loi tren LDPlayer; neu con trang text/khong anh, thu logcat va ten ext de mo tiep runtime-specific fix.

## 2026-08-01 13:38 - P06.T05 - AI TRANSLATION PIPELINE HOTFIX

- Nguoi/agent thuc hien: Codex
- Muc tieu: Bo pipeline dich AI cu dua tren `[result]`/`[dictionary]`; thay bang pipeline refiner moi tham khao `Translator Engine` voi context pack, QT draft, JSON `refined_segments`, exact ID va no-CJK QC.
- Thoi gian bat dau/ket thuc: 2026-08-01 12:40 / 2026-08-01 13:38
- File thay doi: `AiTranslationRefinePipeline.kt`, `TranslateChapterUseCase.kt`, `TranslationConstants.kt`, `AiTranslationRefinePipelineTest.kt`, `TranslateChapterAiRetryTest.kt`, `AiPromptCatalogTest.kt`, `reports/P06-T05-AI-TRANSLATION-PIPELINE-HOTFIX.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Tao pipeline Android Stage 2->4 gom context pack/raw_segments/QT draft/locked dictionary; runtime AI request khong con prompt paragraph marker cu; parser bat thieu/trung/sai ID va CJK trong output tieng Viet; `new_entities` tiep tuc hoc dictionary; legacy output chi con la fallback doc tuong thich.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.AiTranslationRefinePipelineTest" --tests "io.legado.app.domain.model.AiPromptCatalogTest"`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.usecase.TranslateChapterAiRetryTest"`; focused VBook/Markdown/Agent/translation regression suite; `.\gradlew.bat :app:compileAppDebugKotlin`; `.\gradlew.bat :app:assembleAppDebug`; `adb install -r -t app-app-x86_64-debug.apk`; `adb shell am start -n com.drducbook.app.debug/io.legado.app.ui.main.MainActivity`.
- Ket qua: Focused pipeline/prompt/usecase tests PASS; focused VBook + Markdown + Agent + translation suite PASS; Kotlin compile PASS; debug assemble PASS; LDPlayer install PASS; app launch PASS.
- Bang chung: `reports/P06-T05-AI-TRANSLATION-PIPELINE-HOTFIX.md`; Gradle XML reports trong `app/build/test-results/testAppDebugUnitTest/`; APK debug trong `app/build/outputs/apk/app/debug/`.
- Rui ro/cong viec con lai: Cache chuong da dich bang pipeline cu can force retranslate neu muon output moi; preset nguoi dung cu co the con noi dung `[result]` nhung runtime da co pipeline override ep JSON.
- Task tiep theo: Nguoi dung test mot chuong dich AI that tren LDPlayer; neu provider tra JSON kem, bo sung repair prompt/schema fallback theo log cu the.

## 2026-08-01 13:39 - P08.T06 - CHATBOT TOOLS AND SKILL HOTFIX

- Nguoi/agent thuc hien: Codex
- Muc tieu: Sua loi regex/Markdown lam vo bo cuc tra loi chatbot va bo sung tool/skill guidance de chatbot check model, quota va ho tro sua loi nguon sach.
- Thoi gian bat dau/ket thuc: 2026-08-01 11:30 / 2026-08-01 13:39
- File thay doi: `MarkdownBlock.kt`, `AiToolRepository.kt`, `AgentPermissionBroker.kt`, `AiChatGenerationUseCase.kt`, `appModule.kt`, `MarkdownBlockNormalizerTest.kt`, `AiToolRepositoryToolCatalogTest.kt`, `AgentPermissionBrokerTest.kt`, `AiChatGenerationUseCaseTest.kt`, `reports/P08-T06-CHATBOT-TOOLS-SKILL-HOTFIX.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Them normalizer Markdown truoc parse va render list item on dinh; them 4 tool agent `get_ai_runtime_status`, `get_ai_quota_status`, `diagnose_book_source`, `repair_book_source`; cap nhat permission/approval va system guidance cho model/quota/source repair.
- Lenh kiem tra: Focused Markdown/Agent unit tests; focused VBook + Markdown + Agent + AI translation regression suite; `.\gradlew.bat :app:compileAppDebugKotlin`; `.\gradlew.bat :app:assembleAppDebug`; install/launch LDPlayer.
- Ket qua: Markdown/Agent focused tests PASS; regression suite PASS; compile PASS; assemble PASS; LDPlayer install/launch PASS.
- Bang chung: `reports/P08-T06-CHATBOT-TOOLS-SKILL-HOTFIX.md`; Gradle XML reports trong `app/build/test-results/testAppDebugUnitTest/`.
- Rui ro/cong viec con lai: Quota exact chi co khi provider tra du lieu; repair source metadata khong thay the viec sua script plugin neu logic nguon/anti-bot bi doi.
- Task tiep theo: Neu chatbot con bi vo layout voi mau Markdown moi, thu raw response de them normalizer regression; neu source repair that fail, dung diagnose output de mo tool draft/test plugin.

## 2026-08-01 13:49 - P10.T07 - SNAPSHOT CONFLICT RESOLVER CHECKPOINT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Bo sung domain resolver/use-case prompt cho conflict restore/sync snapshot, khong auto-merge khi local va cloud deu doi, va bat selected target khi Supabase/Drive diverged.
- Thoi gian bat dau/ket thuc: 2026-08-01 13:42 / 2026-08-01 13:49
- File thay doi: `CloudSnapshotConflictResolver.kt`, `CloudSnapshotConflictResolverTest.kt`, `reports/P10-T07-SNAPSHOT-CONFLICT-RESTORE.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Tao `CloudSnapshotConflictResolver` gom prompt option cho conflict, diverged target va invalid target; gom restore Supabase/Google Drive thanh lua chon rieng khi hai dich den lech nhau; giu automatic plan rieng cho fast-forward/noop/invalid thay vi tron vao user conflict.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.usecase.CloudSnapshotConflictResolverTest" --tests "io.legado.app.domain.usecase.CloudSnapshotPolicyTest" --tests "io.legado.app.domain.usecase.CloudSnapshotDatasetAdapterTest" --console=plain`.
- Ket qua: BUILD SUCCESSFUL in 4m39s; conflict resolver/policy/adapter focused tests PASS.
- Bang chung: `reports/P10-T07-SNAPSHOT-CONFLICT-RESTORE.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.usecase.CloudSnapshotConflictResolverTest.xml`.
- Rui ro/cong viec con lai: P10.T07 van IN_PROGRESS vi chua co Compose conflict UI sheet, runtime Supabase/Drive CAS wiring, upload/download runtime va integration scenarios.
- Task tiep theo: Tiep tuc conflict UI/wiring neu dong local gate P10.T07, hoac chuyen sang P10.T08/P11 runtime gate khi co Supabase/Drive environment.

## 2026-08-01 14:05 - P10.T07 - SNAPSHOT CONFLICT UI SHEET CHECKPOINT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Bo sung Compose sheet va UI mapper cho lua chon xu ly xung dot snapshot, tach ro UI text/icon khoi domain resolver.
- Thoi gian bat dau/ket thuc: 2026-08-01 13:50 / 2026-08-01 14:05
- File thay doi: `CloudSnapshotConflictUiMapper.kt`, `CloudSnapshotConflictSheet.kt`, `CloudSnapshotConflictUiMapperTest.kt`, `values/strings.xml`, `values-vi/strings.xml`, `reports/P10-T07-SNAPSHOT-CONFLICT-RESTORE.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Them mapper `cloud_snapshot_*` resource cho conflict/targets-diverged/invalid-target; them bottom sheet hien cac lua chon giu may nay, restore cloud/Supabase/Drive, va luu local copy rieng; click option tao `CloudSnapshotResolutionPlan` qua `CloudSnapshotConflictResolver`.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.usecase.CloudSnapshotConflictResolverTest" --tests "io.legado.app.ui.config.backupConfig.CloudSnapshotConflictUiMapperTest" --console=plain`; `.\gradlew.bat :app:compileAppDebugKotlin --console=plain`.
- Ket qua: Focused resolver/mapper tests PASS in 1m29s; `:app:compileAppDebugKotlin` PASS in 8s.
- Bang chung: `reports/P10-T07-SNAPSHOT-CONFLICT-RESTORE.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.config.backupConfig.CloudSnapshotConflictUiMapperTest.xml`.
- Rui ro/cong viec con lai: P10.T07 van IN_PROGRESS vi runtime Supabase/Drive adapter chua tra conflict state that de tu dong hien sheet; CAS runtime, upload/download runtime va integration scenarios con pending.
- Task tiep theo: Tiep tuc runtime cloud head/CAS wiring hoac P10.T08 runtime gates khi co Supabase/Drive environment.

## 2026-08-01 14:18 - P10.T07 - SNAPSHOT SYNC PLANNER CHECKPOINT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Bo sung domain planner noi observed Supabase/Drive heads, base/local revision, conflict prompt va CAS write plan truoc khi runtime adapter thuc hien sync.
- Thoi gian bat dau/ket thuc: 2026-08-01 14:06 / 2026-08-01 14:18
- File thay doi: `CloudSnapshotSyncPlanner.kt`, `CloudSnapshotSyncPlannerTest.kt`, `reports/P10-T07-SNAPSHOT-CONFLICT-RESTORE.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Them `CloudSnapshotSyncState`, `CloudSnapshotSyncDecision` va `CloudSnapshotSyncPlanner`; planner chan invalid/diverged heads truoc khi so revision, tra automatic plan cho fast-forward/restore an toan, tra prompt khi can user choice, va chi tao CAS head write plans cho upload/local-copy.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.usecase.CloudSnapshotSyncPlannerTest" --tests "io.legado.app.domain.usecase.CloudSnapshotConflictResolverTest" --tests "io.legado.app.domain.usecase.CloudSnapshotPolicyTest" --console=plain`; `.\gradlew.bat :app:compileAppDebugKotlin --console=plain`.
- Ket qua: Focused planner/policy/resolver tests PASS in 47s; `:app:compileAppDebugKotlin` PASS in 6s.
- Bang chung: `reports/P10-T07-SNAPSHOT-CONFLICT-RESTORE.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.usecase.CloudSnapshotSyncPlannerTest.xml`.
- Rui ro/cong viec con lai: P10.T07 van IN_PROGRESS vi chua co runtime Supabase Storage/Drive appDataFolder uploader/downloader, optimistic CAS implementation that va integration scenarios tren cloud.
- Task tiep theo: Tiep tuc runtime adapter/CAS implementation neu co environment, hoac dong cac gate P11 local nhu performance/security audit.

## 2026-08-01 14:44 - P11.T04 - SECURITY/A11Y AUDIT CHECKPOINT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Bat dau audit performance/accessibility/security P11.T04 bang cac gate local, sua ngay issue critical/high ro rang thay vi chi ghi report.
- Thoi gian bat dau/ket thuc: 2026-08-01 14:19 / 2026-08-01 14:44
- File thay doi: `CustomAgentToolManifestRuntimeTest.kt`, `MediaPlayerRouteScreen.kt`, `MediaPlaybackService.kt`, `ResolvedMediaPlayer.kt`, `RssReadWebController.kt`, `WebViewLoginFragment.kt`, `BackstageWebView.kt`, `BottomWebViewDialog.kt`, `reports/P11-T04-PERFORMANCE-A11Y-SECURITY-AUDIT.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Secret scan ban dau bat fixture `hf_...`; doi sang secret placeholder khong giong HF token that. Sua Media3 opt-in sang annotation truc tiep `@UnstableApi`. Doi cac WebView con `SslErrorHandler.proceed()` sang `cancel()` de khong silently bo qua SSL error trong RSS/login/backstage/dialog WebView.
- Lenh kiem tra: `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\security\scan-secrets.ps1`; `node --test scripts\test-cloud-security-gates.mjs`; focused audit unit Gradle suite; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.agenttools.CustomAgentToolManifestRuntimeTest" --console=plain`; `rg -n "handler\?\.proceed\(\)|SslErrorHandler.*proceed" app\src\main\java`; `.\gradlew.bat :app:compileAppDebugKotlin --console=plain --no-daemon`; `.\gradlew.bat :app:lintAppDebug`.
- Ket qua: Secret scan PASS sau fix voi 3 allow-listed/0 unapproved; Node cloud security 6 PASS; focused audit unit suite PASS; custom Agent tool test PASS; SSL proceed scan khong con match; Kotlin compile PASS in 1m36s sau final Media3/WebView fix. Lint AppDebug timeout sau 5 va 10 phut, report cu co 8 errors da duoc fix trong source nhung lint clean report moi chua co.
- Bang chung: `reports/P11-T04-PERFORMANCE-A11Y-SECURITY-AUDIT.md`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.agenttools.CustomAgentToolManifestRuntimeTest.xml`; `app/build/reports/lint-results-appDebug.*`.
- Rui ro/cong viec con lai: P11.T04 van IN_PROGRESS vi can lint hoan tat khong timeout, triage warning backlog accessibility/performance/localization, va can device metrics startup/memory/battery/ANR/OOM.
- Task tiep theo: Tiep tuc P11.T04 lint/perf/a11y triage hoac P11.T05 metadata/privacy khi co domain/OAuth inputs.

## 2026-08-01 14:52 - PLAN - ADD P11.T08 AI TRANSLATION PIPELINE REWRITE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Bo sung task rieng sau cac checkpoint hien co de doc reference Translator Engine, bo pipeline dich AI cu con sot va khoa pipeline moi truoc final report.
- Thoi gian bat dau/ket thuc: 2026-08-01 14:49 / 2026-08-01 14:52
- File thay doi: `phases/PHASE-11-INTEGRATION-RELEASE.md`, `TASK-MATRIX.md`, `reports/P11-T08-AI-TRANSLATION-PIPELINE-REWRITE.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Them P11.T08 vao gate Phase 11, dat dependency tu P06.T05/P11.T04 va bat P11.T07 phu thuoc P11.T08 de final report khong dong truoc khi pipeline dich moi duoc kiem dinh.
- Lenh kiem tra: Tai lieu-only change; kiem tra bang doc lai phase/task matrix/report sau patch.
- Ket qua: Plan da ghi ro reference `D:\Dev\Projects\legado-qt-main\legado-qt-main\Translator Engine\Translator Engine`, checklist xoa pipeline cu, dieu kien thong qua va bang chung can co.
- Bang chung: `reports/P11-T08-AI-TRANSLATION-PIPELINE-REWRITE.md`.
- Rui ro/cong viec con lai: Can quyen doc path reference ngoai workspace khi bat dau P11.T08; neu path thay doi, task phai cap nhat reference truoc khi code.
- Task tiep theo: Quay lai P11.T04 accessibility/performance/security triage dang dang do.

## 2026-08-01 15:05 - P11.T04 - ACCESSIBILITY/PERFORMANCE SOURCE FIX CHECKPOINT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Tiep tuc P11.T04 bang cach sua hai warning co rui ro that trong stale lint report: cursor handle chon van ban va allocation trong draw animation.
- Thoi gian bat dau/ket thuc: 2026-08-01 14:53 / 2026-08-01 15:05
- File thay doi: `ReadBookRouteScreen.kt`, `FadePageDelegate.kt`, `reports/P11-T04-PERFORMANCE-A11Y-SECURITY-AUDIT.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Them `TextSelectionCursorView` override `performClick()` cho hai cursor handle, giu controller goi `v.performClick()` khi ACTION_UP. Doi `FadePageDelegate` sang dung lai `fadePaint` thay vi tao `Paint()` trong `onDraw()`.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --console=plain --no-daemon`; static scan xoa `ImageView(context).apply`/`Paint()` allocation; static scan xac nhan `TextSelectionCursorView`/`fadePaint`; `Get-Process java`.
- Ket qua: Kotlin compile PASS in 2m11s; static scan khong con bad pattern tai hai file vua sua; Java process check khong con tien trinh.
- Bang chung: `reports/P11-T04-PERFORMANCE-A11Y-SECURITY-AUDIT.md`; source files tren.
- Rui ro/cong viec con lai: Full lint AppDebug van can chay thanh cong de xac nhan tool-level; P11.T04 con warning backlog localization/a11y/performance va device metrics startup/memory/battery/ANR/OOM.
- Task tiep theo: Tiep tuc P11.T04 hoac chuyen sang P11.T05/P11.T08 khi can dong cac gate sau.

## 2026-08-01 15:31 - P11.T04 - READER KOIN CRASH HOTFIX

- Nguoi/agent thuc hien: Codex
- Muc tieu: Sua loi debug moi cai crash khi doc sach tren Huawei/Android 12 do Koin khong tao duoc `ReadBookViewModel`.
- Thoi gian bat dau/ket thuc: 2026-08-01 15:11 / 2026-08-01 15:31
- File thay doi: `app/src/main/java/io/legado/app/di/appModule.kt`, `reports/P11-T04-PERFORMANCE-A11Y-SECURITY-AUDIT.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Crash log cho thay chain `ReadBookViewModel -> GenerateChapterSummaryUseCase -> AiToolAwareGenerationUseCase -> AiToolGateway -> SourceCheckEngine`, root cause `NoDefinitionFoundException: kotlin.jvm.functions.Function0`. Thay `singleOf(::SourceCheckEngine)` bang dang ky thu cong de Koin khong resolve tham so default `now: () -> Long`.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --console=plain --no-daemon`; `SourceCheckEngineTest`; `BookSourceHealthCheckProcessorTest`; `AiToolRepositoryToolCatalogTest`; `.\gradlew.bat :app:assembleAppDebug --console=plain --no-daemon`; install LDPlayer x86_64; direct `book/read` route smoke; logcat Koin/FATAL scan.
- Ket qua: Compile PASS; 3 focused tests PASS khi chay noi tiep; debug assemble PASS; LDPlayer install PASS; `book/read` route tao `ReadBookViewModel` va khong con `Function0`/Koin/FATAL match trong logcat. Mot lan test song song bi loi output binary/EOF do Gradle collision, da rerun tung test va PASS.
- Bang chung: `reports/P11-T04-PERFORMANCE-A11Y-SECURITY-AUDIT.md`; APK debug trong `app/build/outputs/apk/app/debug/`; SHA-256 universal `FFD9C2DFADC0CC38A2BE99DC7A8AABF2BFA4B9280312286B4AB5FD647BBF3AFD`; SHA-256 arm64 `97025BA1EEEB236E66254883086731DF48A576CCD21B66A3A3832BEC3A0795BE`.
- Rui ro/cong viec con lai: Can nguoi dung cai APK moi len may Huawei HBN-LX9 va mo lai sach that de xac nhan voi data that; P11.T04 van IN_PROGRESS vi full lint/perf device gates con pending.
- Task tiep theo: Neu Huawei con crash, doc log moi; neu pass, tiep tuc P11.T08 pipeline dich AI hoac P11.T04 lint/perf gates.

## 2026-08-01 15:36 - P11.T08 - STARTED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Bat dau gate rewrite/validation pipeline dich AI moi, doc reference Translator Engine va loai duong runtime production con dung contract `[result]`/`[dictionary]`.
- Thoi gian bat dau: 2026-08-01 15:36
- File thay doi ban dau: `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Chuyen P11.T08 sang `IN_PROGRESS`; buoc tiep theo la doc source tham chieu co chon loc, kiem ke pipeline Android hien tai va sua/test neu con fallback cu.
- Rui ro/cong viec con lai: Reference nam ngoai workspace; chi doc cac file source/report can thiet, tranh `.env`, `.git`, `Output`.
- Task tiep theo: Doc `Pipeline_Schema_Report.md`, cac stage script chinh va pipeline Android.

## 2026-08-01 15:57 - P11.T08 - AI TRANSLATION PIPELINE CODE/TEST CHECKPOINT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Bo pipeline dich AI cu dang nhan `[result]`/`[dictionary]`, khoa runtime refiner moi theo schema JSON tham chieu Translator Engine.
- Thoi gian bat dau/ket thuc: 2026-08-01 15:36 / 2026-08-01 15:57
- File thay doi: `AiTranslationRefinePipeline.kt`, `AiTranslationChunkPipeline.kt`, `TranslateChapterUseCase.kt`, `AiTranslationRefinePipelineTest.kt`, `AiTranslationChunkPipelineTest.kt`, `TranslateChapterAiRetryTest.kt`, `TranslateChapterFinalizeOutputTest.kt`, `reports/P11-T08-AI-TRANSLATION-PIPELINE-REWRITE.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Doc reference `Pipeline_Schema_Report.md` va cac stage 2/3/4/QC script. Refiner Android nay chi chap nhan JSON object co `refined_segments`; output cu `[result]`/`[dictionary]` bi reject/retry. Parser stream cu da xoa; finalizer khong con nhanh boc `[result]`; test fake AI output chuyen sang JSON va entity character hoc thanh `QuickDictionaryType.NAME`.
- Lenh kiem tra: static scan `[result]/[dictionary]`; focused AI pipeline tests; `.\gradlew.bat :app:compileAppDebugKotlin --console=plain --no-daemon`; `.\gradlew.bat :app:assembleAppDebug --console=plain --no-daemon`; install x86_64 APK len LDPlayer; launch smoke; logcat crash/Koin/translation parse scan.
- Ket qua: Focused tests PASS; Kotlin compile PASS; debug assemble PASS; LDPlayer install PASS; launch smoke PASS; logcat 500 dong gan nhat khong co `FATAL EXCEPTION`/Koin/translation parse fatal. APK x86_64 SHA-256 `181EC999EC525781912ACBA7EFEEA4939BEEC6E89338A1409A99658AF048F0B6`.
- Bang chung: `reports/P11-T08-AI-TRANSLATION-PIPELINE-REWRITE.md`; `app/build/test-results/testAppDebugUnitTest/`; APK debug trong `app/build/outputs/apk/app/debug/`.
- Rui ro/cong viec con lai: P11.T08 van IN_PROGRESS vi chua dich thu mot chuong that bang model that tren device va chua quyet dinh cache invalidation cho cac ban dich cu da tao bang pipeline `[result]`.
- Task tiep theo: Chay live chapter translation gate khi co cau hinh model hop le; sau do tiep tuc P11.T04 lint/perf hoac P11.T05 release metadata.

## 2026-08-01 16:06 - P11.T08 - LEGACY AI CACHE INVALIDATION CHECKPOINT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Dam bao ban dich/cache chunk sinh tu pipeline cu `[result]`/`[dictionary]` khong duoc dung lai trong reader sau khi pipeline moi da khoa JSON.
- Thoi gian bat dau/ket thuc: 2026-08-01 15:58 / 2026-08-01 16:06
- File thay doi: `TranslateChapterUseCase.kt`, `TranslateChapterAiRetryTest.kt`, `reports/P11-T08-AI-TRANSLATION-PIPELINE-REWRITE.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Them regex nhan dien section contract cu `[result]`, `[/result]`, `[dictionary]` tai `isUsableCachedTranslation()` cho provider APP_AI. Cache cu bi bo qua va duong dich lai se tao chunk moi theo schema refiner JSON.
- Lenh kiem tra: static scan marker/parser cu; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.usecase.TranslateChapterAiRetryTest" --tests "io.legado.app.domain.model.AiTranslationRefinePipelineTest" --tests "io.legado.app.domain.usecase.TranslateChapterFinalizeOutputTest" --console=plain --no-daemon`; `.\gradlew.bat :app:assembleAppDebug --console=plain --no-daemon`; install x86_64 APK len LDPlayer; launch smoke; logcat crash scan.
- Ket qua: Focused tests PASS; assemble PASS; install PASS; launch smoke PASS; logcat 500 dong gan nhat khong co crash/Koin/translation parse fatal. APK x86_64 SHA-256 `AA3645CC50B3D96B77084E56D60F7C0A6DDC782227D956202EE38261CA0D5B82`.
- Bang chung: `reports/P11-T08-AI-TRANSLATION-PIPELINE-REWRITE.md`; `app/build/test-results/testAppDebugUnitTest/`; APK debug trong `app/build/outputs/apk/app/debug/`.
- Rui ro/cong viec con lai: P11.T08 van IN_PROGRESS vi live chapter translation voi model that tren device chua co evidence hien tai.
- Task tiep theo: Neu AI profile hop le da cau hinh tren LDPlayer, chay live chapter gate; neu chua, tiep tuc P11.T04 lint/perf hoac P11.T05 metadata/privacy.

## 2026-08-01 16:36 - P11.T04 - LINT BACKLOG CLEANUP CHECKPOINT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Tiep tuc P11.T04/P11.T01 bang cach chay lai full lint voi timeout dai hon va giam cac warning lint/source co the sua an toan.
- Thoi gian bat dau/ket thuc: 2026-08-01 16:07 / 2026-08-01 16:36
- File thay doi: 14 UI ViewModel co `onCleared()`, `AiChatViewModel.kt`, `reports/P11-T04-PERFORMANCE-A11Y-SECURITY-AUDIT.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: `:app:lintAppDebug` van timeout sau 20 phut va report lint khong cap nhat so voi timestamp 06:23. Da cleanup nhom `EmptySuperCall` bang cach xoa `super.onCleared()` rong trong cac UI ViewModel, giu nguyen cleanup rieng tung file. Sua 2 Kotlin warnings trong `AiChatViewModel` (`!!` khong can va dieu kien null luon dung).
- Lenh kiem tra: `.\gradlew.bat :app:lintAppDebug --console=plain --no-daemon`; `.\gradlew.bat --stop`; `Get-Process java`; static scan `super.onCleared()`; `.\gradlew.bat :app:compileAppDebugKotlin --console=plain --no-daemon`; `.\gradlew.bat :app:assembleAppDebug --console=plain --no-daemon`; install x86_64 APK len LDPlayer; launch smoke; logcat crash scan.
- Ket qua: lint timeout sau 20 phut, khong tao report moi; Gradle daemon da stop va Java process sach. Static scan khong con `super.onCleared()` trong `app/src/main/java/io/legado/app/ui`; Kotlin compile PASS in 1m14s va khong con 2 warning `AiChatViewModel` vua sua. Debug assemble PASS; LDPlayer install/launch PASS; logcat 500 dong gan nhat khong co crash/Koin/translation parse fatal. APK x86_64 SHA-256 `5E32888308BBF392B7D41D356FB4A9025EBFD0BB714B200AC423106B0BED64A2`.
- Bang chung: `reports/P11-T04-PERFORMANCE-A11Y-SECURITY-AUDIT.md`; output compile; static scan.
- Rui ro/cong viec con lai: P11.T04/P11.T01 van IN_PROGRESS vi can clean lint report hien hanh hoac mot lint gate thay the khong timeout, va con can device perf/accessibility/runtime metrics.
- Task tiep theo: Tiep tuc triage warning high-signal con lai tu stale lint report hoac chay perf/source-health/media device smoke.

## 2026-08-01 16:43 - P11.T05 - DOMAIN/DOCS/PRIVACY METADATA AUDIT START

- Nguoi/agent thuc hien: Codex
- Muc tieu: Mo P11.T05, kiem ke external metadata/domain/OAuth/privacy hien co va cac blocker con thieu truoc rollout.
- Thoi gian bat dau/ket thuc: 2026-08-01 16:42 / 2026-08-01 16:43
- File thay doi: `reports/P11-T05-DOMAIN-DOCS-PRIVACY-RELEASE-METADATA.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Xac nhan package `com.drducbook.app`, debug suffix `.debug`, Supabase public config, Auth callback `drducbook://auth/callback`, Google Sign-In scopes tach Drive va Drive backup chi dung `drive.appdata`. Scan khong thay `assetlinks.json`/`.well-known` va chua co privacy/support/release URL chinh thuc trong repo.
- Lenh kiem tra: static config/domain scan; `rg --files` assetlinks/privacy/support docs; `.\gradlew.bat :app:testAppDebugUnitTest --tests "com.drducbook.app.cloud.CloudConsentScopesTest" --tests "com.drducbook.app.cloud.GoogleDriveAppDataContractTest" --tests "com.drducbook.app.cloud.SupabaseClientProviderTest" --tests "com.drducbook.app.cloud.CloudSyncClientContractTest" --console=plain --no-daemon`.
- Ket qua: Focused cloud/OAuth contract tests PASS in 1m39s. P11.T05 chuyen `IN_PROGRESS`.
- Bang chung: `reports/P11-T05-DOMAIN-DOCS-PRIVACY-RELEASE-METADATA.md`; test results under `app/build/test-results/testAppDebugUnitTest/`.
- Rui ro/cong viec con lai: Can domain HTTPS that, assetlinks voi signing cert, Supabase dashboard redirect/site URL evidence, Google OAuth consent metadata, privacy/support/terms/release URLs va link checker.
- Task tiep theo: Khi co domain/metadata, tao `assetlinks.json`, privacy/support/release docs va chay verification; neu chua co input, tiep tuc P11.T06/P11 device/runtime gates.

## 2026-08-01 17:03 - P11.T04 - READER DEBUG CRASH HOTFIX 2

- Nguoi/agent thuc hien: Codex
- Muc tieu: Sua loi debug moi cai van crash khi vao man doc sau khi Koin `Function0` da het.
- Thoi gian bat dau/ket thuc: 2026-08-01 16:47 / 2026-08-01 17:03
- File thay doi: `SourceCheckEngine.kt`, `SourceCheckEngineTest.kt`, `EffectiveReplacesSheet.kt`, `ReadBookScreen.kt`, `reports/P11-T04-PERFORMANCE-A11Y-SECURITY-AUDIT.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Dua `now: () -> Long` ra khoi primary constructor cua `SourceCheckEngine` de khoa loi Koin auto resolve `Function0` trong tuong lai. Sua crash Compose snapshot bang cach de `EffectiveReplacesSheet` nhan `preferences.chineseConverterType` tu state man doc, khong doc truc tiep `ReadConfig.chineseConverterType` trong composition.
- Lenh kiem tra: Focused source-health tests; `.\gradlew.bat :app:compileAppDebugKotlin --console=plain --no-daemon`; `.\gradlew.bat :app:assembleAppDebug --console=plain --no-daemon`; install x86_64 APK len LDPlayer; direct `book/read` route smoke; logcat/dumpsys/crash-folder scan.
- Ket qua: Tests PASS; compile PASS; assemble PASS; install PASS. Sau khi mo `book/read` 8 giay, LDPlayer van o `MainActivity`, khong co `CrashReportActivity`, khong co `NoDefinitionFoundException`/`Function0`/snapshot crash trong app log scan, va khong sinh file crash moi.
- Bang chung: `reports/P11-T04-PERFORMANCE-A11Y-SECURITY-AUDIT.md`; APK arm64 SHA-256 `E72CCAF17534E5FC5A256488439FEA60946E10F4A666BF670254581EDA8E2419`; x86_64 SHA-256 `406F18372A844F3B7030D574604BF1C95D9BA970C824C08091C1395CEF29FE3F`; universal SHA-256 `6969DB420D7E7E7C2CEFD6C9C2ACC6A2908B9E06715FDB83A454FD1159B20180`.
- Rui ro/cong viec con lai: Can nguoi dung cai APK moi len may Huawei HBN-LX9 va mo sach that de xac nhan voi data that; P11.T04 van IN_PROGRESS vi full lint/perf device gates con pending.
- Task tiep theo: Neu Huawei pass, quay lai P11.T06 rollout/rollback hoac tiep tuc P11.T04 lint/perf gate.

## 2026-08-01 17:10 - P11.T06 - STAGED ROLLOUT VA ROLLBACK REHEARSAL

- Nguoi/agent thuc hien: Codex
- Muc tieu: Tao bang kiem va runbook phat hanh theo dot/rollback cho DrDucBook truoc khi release production.
- Thoi gian bat dau/ket thuc: 2026-08-01 17:03 / 2026-08-01 17:10
- File thay doi: `scripts/release/verify-rollout-rollback.ps1`, `reports/P11-T06-STAGED-ROLLOUT-ROLLBACK-REHEARSAL.md`, `reports/artifacts/P11-T06-ROLLBACK-REHEARSAL.json`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Them verifier PowerShell de kiem tra version, applicationId, ABI split, APK checksums, release/noR8 artifact availability, FeatureFlags, Web Service policy gates va Supabase/HF asset delivery controls. Viet runbook rollout Stage 0-4 va rollback cap 1-3.
- Lenh kiem tra: `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\release\verify-rollout-rollback.ps1`.
- Ket qua: Verifier PASS ve mat chay script, xac nhan version `3.26.13`, `versionCode` mac dinh `32640`, 4 debug APK, 35 HF artifacts, FeatureFlags/Web Service/Supabase-HF controls co nen tang dung.
- Bang chung: `reports/P11-T06-STAGED-ROLLOUT-ROLLBACK-REHEARSAL.md`; `reports/artifacts/P11-T06-ROLLBACK-REHEARSAL.json`.
- Rui ro/cong viec con lai: P11.T06 van IN_PROGRESS vi thieu release/noR8 artifact hien tai, thieu previous stable rollback artifact, P10 runtime gates con pending, P11.T05 domain/privacy/release metadata con pending, HF manifest con 4 metadata-only/storage mirror required.
- Task tiep theo: Build release/noR8 artifacts khi san sang signing/runtime input, chay lai verifier, bo sung Play/Internal track rollback evidence, roi moi chuyen P11.T06 sang DONE.

## 2026-08-01 18:06 - P11.T06 - NOR8 ARTIFACT VA RELEASE BLOCKER CHECKPOINT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Giam blocker noi bo cua P11.T06 bang cach build noR8/release artifacts va chay lai verifier.
- Thoi gian bat dau/ket thuc: 2026-08-01 17:11 / 2026-08-01 18:06
- File thay doi: `scripts/release/verify-rollout-rollback.ps1`, `reports/P11-T06-STAGED-ROLLOUT-ROLLBACK-REHEARSAL.md`, `reports/artifacts/P11-T06-ROLLBACK-REHEARSAL.json`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Build `:app:assembleAppNoR8` thanh cong va sinh 4 APK noR8 hop le. Build `:app:assembleAppRelease` di toi buoc package nhung that bai vi het dung luong dia, de lai release APK partial/unsigned. Nang cap verifier de kiem tra APK zip integrity va bat release unsigned.
- Lenh kiem tra: `.\gradlew.bat :app:assembleAppNoR8 --console=plain --no-daemon`; `.\gradlew.bat :app:assembleAppRelease --console=plain --no-daemon`; `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\release\verify-rollout-rollback.ps1`; ZIP integrity scan bang .NET `System.IO.Compression.ZipFile`.
- Ket qua: noR8 build PASS. Release build FAIL voi `There is not enough space on the disk`. Verifier hien bao 12 APK total, debug valid 4/4, noR8 valid 4/4, release valid 2/4, invalid release 2/4, unsigned release 4/4.
- Bang chung: `reports/P11-T06-STAGED-ROLLOUT-ROLLBACK-REHEARSAL.md`; `reports/artifacts/P11-T06-ROLLBACK-REHEARSAL.json`.
- Rui ro/cong viec con lai: Can giai phong dung luong dia, cau hinh release signing, build lai release/AAB voi exit code 0, xac dinh previous stable artifact, va hoan thanh cac gate P10/P11.T05 truoc khi rollout.
- Task tiep theo: Sau khi co them dung luong/signing, chay lai `:app:assembleAppRelease`, verifier, va cap nhat P11.T06.

## 2026-08-01 19:13 - P11.T06 - RELEASE RETRY TIMEOUT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Thu build lai release sau khi da don cache trung gian va xac minh khong de tien trinh treo.
- Thoi gian bat dau/ket thuc: 2026-08-01 18:07 / 2026-08-01 19:13
- File thay doi: `reports/P11-T06-STAGED-ROLLOUT-ROLLBACK-REHEARSAL.md`, `reports/artifacts/P11-T06-ROLLBACK-REHEARSAL.json`, `PLAN-LOG.md`.
- Tom tat trien khai: Don cac thu muc build/cache co the tai tao trong workspace, giai phong `D:` len khoang 6.7 GB trong khi giu lai `app/build/outputs`. Retry `:app:assembleAppRelease` nhung lenh cham timeout 30 phut va khong cap nhat release output moi. Dung Gradle bang `.\gradlew.bat --stop --console=plain`.
- Lenh kiem tra: `.\gradlew.bat :app:assembleAppRelease --console=plain --no-daemon`; `.\gradlew.bat --stop --console=plain`; `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\release\verify-rollout-rollback.ps1`; Java process scan.
- Ket qua: Khong con Java/Gradle process treo. Verifier van bao debug valid 4/4, noR8 valid 4/4, release valid 2/4, invalid release 2/4, unsigned release 4/4.
- Bang chung: `reports/P11-T06-STAGED-ROLLOUT-ROLLBACK-REHEARSAL.md`; `reports/artifacts/P11-T06-ROLLBACK-REHEARSAL.json`.
- Rui ro/cong viec con lai: Can them dung luong/hoac clean sau hon va signing config truoc khi release build co the duoc chap nhan.
- Task tiep theo: Tam giu P11.T06 IN_PROGRESS; chuyen sang cac gate khong can build nang hoac cho nguoi dung cap them dung luong/signing.

## 2026-08-01 19:16 - P11.T07 - FINAL CHECKPOINT REPORT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Tao bao cao tong ket thuc te cho phase 4-11 va khong dong plan khi con blocker.
- Thoi gian bat dau/ket thuc: 2026-08-01 19:13 / 2026-08-01 19:16
- File thay doi: `reports/P11-T07-FINAL-REPORT-PLAN-CHECKPOINT.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Doi chieu ma tran P04-P11, ghi ro P04-P09 DONE, P10/P11 con IN_PROGRESS, va liet ke blocker release/domain/runtime/full corpus/live model.
- Lenh kiem tra: `rg -n "\| P0[4-9]\.T|\| P10\.T|\| P11\.T" docs\plans\drducbook-rebuild-2026\TASK-MATRIX.md`.
- Ket qua: P11.T07 chuyen tu TODO sang IN_PROGRESS voi checkpoint report; plan chua du dieu kien `PLAN COMPLETE`.
- Bang chung: `reports/P11-T07-FINAL-REPORT-PLAN-CHECKPOINT.md`.
- Rui ro/cong viec con lai: Can xu ly cac blocker trong report truoc khi doi P11.T07 thanh DONE.
- Task tiep theo: Tiep tuc cac gate khong can build nang hoac cho nguoi dung giai phong dung luong/signing/domain/runtime input.

## 2026-08-01 19:21 - P11.T05 - RELEASE DOCS VA METADATA VERIFIER

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien phan tai lieu release/privacy/support trong repo va tao verifier metadata noi bo.
- Thoi gian bat dau/ket thuc: 2026-08-01 19:17 / 2026-08-01 19:21
- File thay doi: `docs/release/privacy-policy.md`, `docs/release/terms-of-use.md`, `docs/release/support.md`, `docs/release/release-notes.md`, `docs/release/operations-runbook.md`, `docs/release/app-links-checklist.md`, `docs/release/assetlinks.template.json`, `docs/release/google-oauth-consent.md`, `docs/release/supabase-runtime-checklist.md`, `scripts/release/verify-release-metadata.ps1`, `reports/P11-T05-DOMAIN-DOCS-PRIVACY-RELEASE-METADATA.md`, `reports/artifacts/P11-T05-RELEASE-METADATA-CHECK.json`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Tao bo docs release noi bo bao phu privacy/data flow, terms, support, release notes, operations/rollback, app-links, Google OAuth consent va Supabase runtime. Them verifier kiem tra file bat buoc, keyword chinh, secret pattern, template assetlinks va URL external.
- Lenh kiem tra: `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\release\verify-release-metadata.ps1`.
- Ket qua: Verifier PASS cho 8/8 docs, template assetlinks JSON hop le va dung package `com.drducbook.app`; decision van `metadata_blocked_by_external_inputs` do chua co domain HTTPS, public privacy/support/terms URLs va release signing fingerprint.
- Bang chung: `reports/artifacts/P11-T05-RELEASE-METADATA-CHECK.json`; `reports/P11-T05-DOMAIN-DOCS-PRIVACY-RELEASE-METADATA.md`.
- Rui ro/cong viec con lai: Can publish docs len public HTTPS URL, gan domain, them fingerprint release vao assetlinks va lay screenshot/log Supabase/Google dashboard.
- Task tiep theo: Tiep tuc cac gate local nhe hoac cho nguoi dung cung cap domain/signing/runtime evidence.

## 2026-08-01 19:22 - P10.T08 - CLOUD LOCAL GATES RE-RUN

- Nguoi/agent thuc hien: Codex
- Muc tieu: Xac minh lai cac gate cloud local/static khong can secret runtime.
- Thoi gian bat dau/ket thuc: 2026-08-01 19:21 / 2026-08-01 19:22
- File thay doi: `reports/P10-T08-CLOUD-SECURITY-INTEGRATION-GATES.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Chay lai Node cloud gate suite cho asset ticket, HF manifest, secret leak scan, function contract, RLS/private storage va snapshot migration.
- Lenh kiem tra: `node --test scripts/test-asset-ticket.mjs scripts/test-cloud-sync-migration.mjs scripts/test-cloud-security-gates.mjs`.
- Ket qua: 13 tests PASS, failures = 0, duration ~993 ms.
- Bang chung: `reports/P10-T08-CLOUD-SECURITY-INTEGRATION-GATES.md`.
- Rui ro/cong viec con lai: P10.T08 van IN_PROGRESS vi Supabase CLI/Deno/Edge Function/Google Drive runtime gates chua co moi truong hoac bang chung that.
- Task tiep theo: Tiep tuc gate local nhe hoac cho nguoi dung cung cap runtime/secrets/dashboard evidence.

## 2026-08-01 19:26 - P10.T01 - VALTEC/HY-MT2 LOCAL SOURCE VERIFIED

- Nguoi/agent thuc hien: Codex
- Muc tieu: Giam blocker P10.T01 bang cach doi chieu 4 artifact tung metadata-only voi file source local hien co.
- Thoi gian bat dau/ket thuc: 2026-08-01 19:22 / 2026-08-01 19:26
- File thay doi: `scripts/build-hf-asset-manifest.ps1`, `scripts/upload-hf-artifacts.ps1`, `supabase/artifacts/hf-artifacts-manifest.json`, `reports/P10-T01-HF-ASSET-MANIFEST.md`, `reports/P10-T08-CLOUD-SECURITY-INTEGRATION-GATES.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Tim thay 3 Hy-MT2 GGUF trong `.codex-tmp/models` va Valtec ZIP trong `.codex-tmp/release-assets`; hash khop manifest. Sua manifest builder de tu nhan file local neu ton tai va ghi `local_verified_upload_pending`. Doi default upload workdir cua uploader chinh sang `%TEMP%\drducbook-hf-upload-work` de tranh lam day o D khi upload that.
- Lenh kiem tra: `Get-FileHash` cho 4 file; `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-hf-asset-manifest.ps1`; `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\upload-hf-artifacts.ps1 -DryRun`; `node --test scripts/test-asset-ticket.mjs scripts/test-cloud-sync-migration.mjs scripts/test-cloud-security-gates.mjs`.
- Ket qua: Manifest builder PASS; dry-run uploader readyCount 35, readyBytes 3,298,972,546, metadataOnlyCount 0; Node cloud gates PASS 13/13.
- Bang chung: `reports/P10-T01-HF-ASSET-MANIFEST.md`; `reports/P10-T08-CLOUD-SECURITY-INTEGRATION-GATES.md`.
- Rui ro/cong viec con lai: 4 artifact lon van `storage_mirror_required` va can upload/mirror runtime; 29 Piper ZIP van license-review-required; Supabase private dataset/Edge Function runtime gate van pending.
- Task tiep theo: Khi co dung luong/network/token/runtime, upload/mirror 4 artifact lon va dong license/provenance Piper.

## 2026-08-01 19:30 - P10.T01 - HF UPLOADER LFS SAFETY

- Nguoi/agent thuc hien: Codex
- Muc tieu: Giam rui ro upload HF that tai nguoc LFS cu va lam day o tam.
- Thoi gian bat dau/ket thuc: 2026-08-01 19:27 / 2026-08-01 19:30
- File thay doi: `scripts/upload-hf-artifacts.ps1`, `reports/P10-T01-HF-ASSET-MANIFEST.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Them `GIT_LFS_SKIP_SMUDGE=1` quanh buoc clone HuggingFace dataset va restore bien moi truong trong `finally`; uploader van copy 35 artifact local vao repo clone truoc khi commit/push.
- Lenh kiem tra: `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\upload-hf-artifacts.ps1 -DryRun`; `node --test scripts/test-asset-ticket.mjs scripts/test-cloud-security-gates.mjs`.
- Ket qua: Dry-run PASS, readyCount 35, metadataOnlyCount 0; Node focused cloud gates PASS 9/9.
- Bang chung: `reports/P10-T01-HF-ASSET-MANIFEST.md`.
- Rui ro/cong viec con lai: Chua chay upload that vi can token/network/runtime va quyet dinh mirror 4 artifact lon.
- Task tiep theo: Khi nguoi dung san sang token/network, chay upload that voi uploader da skip LFS smudge; sau do verify HF commit/range.

## 2026-08-01 20:51 - P10.T01/P10.T08 - HF PRIVATE UPLOAD COMPLETE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Upload 4 artifact lon con lai len HuggingFace, cap nhat manifest de app co the tai qua HF proxy, va verify remote private dataset.
- Thoi gian bat dau/ket thuc: 2026-08-01 20:09 / 2026-08-01 20:51
- File thay doi: `scripts/build-hf-asset-manifest.ps1`, `supabase/artifacts/hf-artifacts-manifest.json`, `reports/P10-T01-HF-ASSET-MANIFEST.md`, `reports/P10-T02-SUPABASE-ASSET-TICKET.md`, `reports/P10-T08-CLOUD-SECURITY-INTEGRATION-GATES.md`, `reports/P11-T06-STAGED-ROLLOUT-ROLLBACK-REHEARSAL.md`, `reports/P11-T07-FINAL-REPORT-PLAN-CHECKPOINT.md`, `reports/artifacts/P11-T06-ROLLBACK-REHEARSAL.json`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Chay upload that bang token trong environment, khong in token. Commit `1b844fe` upload 4 LFS objects Valtec + 3 Hy-MT2 (~1.5 GB). Cap nhat manifest de 35/35 artifact dung `hf_proxy`, khong con `storage_mirror_required`; commit `adc61e3` push manifest-only. Don repo tam trong `%TEMP%` sau moi lan upload.
- Lenh kiem tra: `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\upload-hf-artifacts.ps1`; authenticated HF API/manifest/HEAD checks; `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\cleanup-hf-upload-work.ps1`; `node --test scripts/test-asset-ticket.mjs scripts/test-cloud-sync-migration.mjs scripts/test-cloud-security-gates.mjs`; `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\release\verify-rollout-rollback.ps1`.
- Ket qua: HF remote revision `adc61e3a041893fc38233e02fee3a183bde5083c`, dataset private true, artifactCount 35, `hf_proxy` 35, `storage_mirror_required` 0, `metadata_only_pending_source` 0. 4 artifact lon HEAD HTTP 200 va Content-Length khop manifest. Node cloud gates PASS 13/13. P11.T06 verifier khong con HF storage warning.
- Bang chung: `reports/P10-T01-HF-ASSET-MANIFEST.md`; `reports/P10-T02-SUPABASE-ASSET-TICKET.md`; `reports/P10-T08-CLOUD-SECURITY-INTEGRATION-GATES.md`; `reports/P11-T06-STAGED-ROLLOUT-ROLLBACK-REHEARSAL.md`; `reports/P11-T07-FINAL-REPORT-PLAN-CHECKPOINT.md`; `reports/artifacts/P11-T06-ROLLBACK-REHEARSAL.json`.
- Rui ro/cong viec con lai: 29 Piper voice packages van `license-review-required`; Supabase Edge Function secret/runtime, Auth/Drive runtime va release/domain/signing gates van pending.
- Task tiep theo: Cau hinh Supabase `HF_READ_TOKEN`/ticket secrets va chay runtime ticket/download/range gates; dong license/provenance Piper truoc release public.

## 2026-08-01 22:50 - P08.T07 - AI ROUTER PROVIDER POOL HOTFIX

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien AI Router theo co che provider pool: mot provider quan ly nhieu account OAuth/API key, combo chi chon model va runtime tu xoay credential cua provider.
- Thoi gian bat dau/ket thuc: 2026-08-01 22:18 / 2026-08-01 22:50
- File thay doi: `AiRouterDao.kt`, `AiRouterRepository.kt`, `AiOAuthRepository.kt`, `RepairAiRouteBindingsUseCase.kt`, `AiRouterContract.kt`, `AiRouterViewModel.kt`, `AiRouterScreen.kt`, `AiProviderConfigSheet.kt`, `AiRouterRepositoryTest.kt`, `RepairAiRouteBindingsUseCaseTest.kt`, `reports/P08-T07-AI-ROUTER-PROVIDER-POOL-HOTFIX.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Them credential cursor theo provider/target; target model-only resolve va xoay vong credential dang bat cua provider. OAuth login moi khong tao target rieng theo account nua. Startup repair gom target OAuth cu theo account thanh target model-pool va xoa target trung lap. UI provider OAuth co sheet pool de dang nhap them nhieu account va quan ly credential; provider API key tiep tuc luu nhieu key trong sheet provider.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.AiRouterRepositoryTest" --tests "io.legado.app.domain.usecase.RepairAiRouteBindingsUseCaseTest" --console=plain --no-daemon`; `.\gradlew.bat :app:compileAppDebugKotlin --console=plain --no-daemon`; `.\gradlew.bat :app:assembleAppDebug --console=plain --no-daemon`.
- Ket qua: Focused AI Router/repair tests PASS; Kotlin compile PASS; debug APK assemble PASS.
- Bang chung: `reports/P08-T07-AI-ROUTER-PROVIDER-POOL-HOTFIX.md`; Gradle test XML trong `app/build/test-results/testAppDebugUnitTest/`; debug APK trong `app/build/outputs/apk/app/debug/`.
- Rui ro/cong viec con lai: Chua co device screenshot rieng cho sheet provider pool; quota exact van phu thuoc provider; route tuy bien cu gan credential explicit van duoc ton trong.
- Task tiep theo: Cai APK debug moi neu can device smoke AI Router UI, sau do tiep tuc live model/quota/provider runtime gate va pipeline dich AI P11.T08.

## 2026-08-02 00:31 - P11.T01/P11.T04 - LINT BLOCKING GATE PASS

- Nguoi/agent thuc hien: Codex
- Muc tieu: Xac nhan lai yeu cau provider pool moi nhat va dong blocker lint fatal/error dang chan P11.T01/P11.T04.
- Thoi gian bat dau/ket thuc: 2026-08-01 23:47 / 2026-08-02 00:31 local.
- File thay doi: `ReadBookRouteScreen.kt`, `MediaPlaybackService.kt`, `ResolvedMediaPlayer.kt`, `MediaPlayerRouteScreen.kt`, `reports/P11-T01-FULL-BUILD-TEST-MATRIX.md`, `reports/P11-T04-PERFORMANCE-A11Y-SECURITY-AUDIT.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Doi cursor custom view sang `AppCompatImageView`; doi Media3 unstable usage sang AndroidX `@OptIn(UnstableApi::class)` tai implementation boundary de khong day lint opt-in ra call site. Doi chieu P08.T07: provider screen quan ly nhieu account/API key, combo chi chon model, runtime tu xoay credential cua provider.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --console=plain --no-daemon`; `.\gradlew.bat :app:lintAppDebug --console=plain --no-daemon`; parse `app/build/reports/lint-results-appDebug.xml`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.AiRouterRepositoryTest" --tests "io.legado.app.domain.usecase.RepairAiRouteBindingsUseCaseTest" --console=plain --no-daemon`; `.\gradlew.bat :app:assembleAppDebug --console=plain --no-daemon`.
- Ket qua: Kotlin compile PASS. `:app:lintAppDebug` PASS in 39m10s; report moi co 3024 issues gom 0 fatal/error, 2998 warnings, 26 hints. Focused AI Router provider pool/repair tests PASS in 1m53s. Debug APK assemble PASS in 1m54s; universal SHA-256 `32229C6E42E58A99F5FC81793E5A18144D70AB4131D003870A8D7F271221E36D`, x86_64 SHA-256 `BC0BA9361AA9BCA772F81C803B52D95BB15A2781863AEE255140556468DB2C23`.
- Bang chung: `reports/P11-T01-FULL-BUILD-TEST-MATRIX.md`; `reports/P11-T04-PERFORMANCE-A11Y-SECURITY-AUDIT.md`; `app/build/reports/lint-results-appDebug.xml`.
- Rui ro/cong viec con lai: P11.T04 van IN_PROGRESS vi warning backlog va device performance/accessibility metrics chua dong; P10/P11 runtime/domain/release gates van pending.
- Task tiep theo: Tiep tuc triage warning backlog hoac chay cac runtime gate khi co du lieu/device/secrets can thiet.

## 2026-08-02 01:20 - P10.T01/P10.T08 - PIPER LICENSE REVIEW GATE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Giam blocker P10.T01 bang cach tach Piper voices da co license/provenance ro khoi nhom license pending, dong thoi giu ticket guard cho voice chua ro license.
- Thoi gian bat dau/ket thuc: 2026-08-02 00:45 / 2026-08-02 01:20 local.
- File thay doi: `scripts/build-hf-asset-manifest.ps1`, `scripts/test-cloud-security-gates.mjs`, `supabase/artifacts/hf-artifacts-manifest.json`, `docs/release/piper-voice-license-review.md`, `reports/P10-T01-HF-ASSET-MANIFEST.md`, `reports/P10-T08-CLOUD-SECURITY-INTEGRATION-GATES.md`, `reports/P11-T06-STAGED-ROLLOUT-ROLLBACK-REHEARSAL.md`, `reports/P11-T07-FINAL-REPORT-PLAN-CHECKPOINT.md`, `reports/artifacts/P11-T06-ROLLBACK-REHEARSAL.json`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Kiem tra ZIP local chi co `.onnx` va `.onnx.json`. Doi chieu upstream/mirror `doof-ferb/nghitts-copy` co license `apache-2.0`, thu muc `piper-tts` va 25 voice khop danh sach local. Giu 4 voice `indo_goreng`, `john`, `mattheo`, `mattheo1` o trang thai `license-review-required` vi chua co license card du ro.
- Lenh kiem tra: `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-hf-asset-manifest.ps1`; manifest license/inventory count scan; `node --test scripts/test-asset-ticket.mjs scripts/test-cloud-sync-migration.mjs scripts/test-cloud-security-gates.mjs`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.HfArtifactManifestTest" --console=plain --no-daemon`; `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\release\verify-rollout-rollback.ps1`.
- Ket qua: Manifest regenerate PASS. Piper `Apache-2.0` 25, `license-review-required` 4, `local_verified` 31, `local_verified_license_pending` 4. Node cloud gates PASS 14/14. Android HF manifest test PASS. P11.T06 verifier PASS/decision van `blocked_for_production_rollout`, artifact JSON da cap nhat `local_verified_license_pending` 4.
- Bang chung: `docs/release/piper-voice-license-review.md`; `supabase/artifacts/hf-artifacts-manifest.json`; `reports/P10-T01-HF-ASSET-MANIFEST.md`; `reports/P10-T08-CLOUD-SECURITY-INTEGRATION-GATES.md`.
- Rui ro/cong viec con lai: Can bo sung license card hop le cho 4 voice con lai hoac loai chung khoi public catalog. Supabase Edge Function/secret runtime, Auth/Drive runtime va release/domain gates van pending.
- Task tiep theo: Xu ly 4 voice Piper pending, hoac chuyen sang runtime gate Supabase khi co CLI/Deno/secret/device evidence.

## 2026-08-02 01:34 - P08.T07 - AI ROUTER PROVIDER POOL FOLLOW-UP

- Nguoi/agent thuc hien: Codex
- Muc tieu: Khoa lai yeu cau moi nhat: mot man hinh provider quan ly nhieu tai khoan OAuth hoac nhieu API key, combo chi chon model va runtime tu xoay credential cua provider tuong ung.
- Thoi gian bat dau/ket thuc: 2026-08-02 01:22 / 2026-08-02 01:34 local.
- File thay doi: `AiProviderGrid.kt`, `AiRouterDashboardMapper.kt`, `AiRouterRepositoryTest.kt`, `reports/P08-T07-AI-ROUTER-PROVIDER-POOL-HOTFIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Doi wording route/combo de khong con goi y chon credential cu the; target hien la model + provider pool. Them regression test `modelOnlyTargetRotatesProviderApiKeyPool` de xac nhan nhieu API key cua cung provider duoc xoay vong khi target chi chon model.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.AiRouterRepositoryTest" --console=plain --no-daemon`.
- Ket qua: Focused AI Router repository tests PASS; command da chay ca `:app:compileAppDebugKotlin` trong pipeline test.
- Bang chung: `reports/P08-T07-AI-ROUTER-PROVIDER-POOL-HOTFIX.md`; Gradle test XML trong `app/build/test-results/testAppDebugUnitTest/`.
- Rui ro/cong viec con lai: Chua co screenshot device moi cho sheet provider; route tuy bien cu gan credential explicit van duoc ton trong de tranh pha cau hinh cu.
- Task tiep theo: Tiep tuc P11.T04 warning backlog va cac runtime gate AI/Supabase/translation con lai.

## 2026-08-02 07:35 - P11.T04 - THEME VA EXPORT EBOOK HOTFIX

- Nguoi/agent thuc hien: Codex
- Muc tieu: Sau khi hoan tat lint cleanup, kiem tra va sua hai loi nguoi dung neu: mau chu de/khong gian khoi thieu tu nhien va xuat ebook khong dung thiet ke, dac biet voi anh truyen tranh.
- Thoi gian bat dau/ket thuc: 2026-08-02 06:24 / 2026-08-02 07:35 local.
- File thay doi: `ThemeSurfaceAdjustments.kt`, `ThemeEngine.kt`, `ThemeOverride.kt`, `ThemeEngineTest.kt`, `EbookExportModels.kt`, `ExportBookService.kt`, `EbookExportWriter.kt`, `EbookExportWriterTest.kt`, `reports/P11-T04-PERFORMANCE-A11Y-SECURITY-AUDIT.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Them surface ramp dong nhat cho AMOLED/Transparent va dung chung o theme engine/theme override de tranh lech mau giua card/panel/sheet. Export ebook nay luu anh bang URL tuyet doi va alias tuong doi, writer thay ca hai dang khi ghi EPUB/HTML de anh truyen tranh khong bi mat hoac lot duong dan cu.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.service.export.EbookExportWriterTest" --tests "io.legado.app.ui.theme.ThemeEngineTest" --console=plain --no-daemon`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.service.export.EbookExportScopeTest" --console=plain --no-daemon`; `.\gradlew.bat :app:compileAppDebugKotlin --console=plain --no-daemon`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.service.export.EbookExportWriterTest" --tests "io.legado.app.service.export.EbookExportScopeTest" --tests "io.legado.app.ui.theme.ThemeEngineTest" --console=plain --no-daemon`; `.\gradlew.bat :app:assembleAppDebug --console=plain --no-daemon`; `adb -s emulator-5554 install -r -t app/build/outputs/apk/app/debug/app-app-x86_64-debug.apk`; `adb -s emulator-5554 shell am start -S -n com.drducbook.app.debug/io.legado.app.ui.main.MainActivity`; `adb -s emulator-5554 logcat -d -t 500`.
- Ket qua: Ebook export writer/theme tests PASS; export scope test PASS; Kotlin compile PASS; debug APK assemble PASS. X86_64 debug SHA-256 `ABAD1D53E41B734D060F0F2B83F8C2B71349ED82B38095CBC82005777AF6C159`; universal debug SHA-256 `FE9D3ECC10B9621C1E78436D0A676D7CD9585C6F5E297B81F9AE0F9DE7B92191`. Cai x86_64 debug len LDPlayer PASS; launch `MainActivity` PASS; app process con song sau 6 giay va logcat 500 dong gan nhat khong co crash match. Luu y: mot tien trinh Gradle cu bi timeout va duoc dung truoc khi rerun thanh cong.
- Bang chung: `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.service.export.EbookExportWriterTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.service.export.EbookExportScopeTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.theme.ThemeEngineTest.xml`; `reports/P11-T04-PERFORMANCE-A11Y-SECURITY-AUDIT.md`.
- Rui ro/cong viec con lai: P11.T04 van IN_PROGRESS vi can device performance/accessibility metrics va triage backlog `MissingTranslation`/`UnusedResources` lon; can cai APK moi len may that/LDPlayer de nhin lai theme va thu export ebook bang du lieu nguoi dung.
- Task tiep theo: Neu nguoi dung mo debug moi, smoke test theme/export tren thiet bi; tiep tuc runtime gates AI/Supabase/translation va device perf sau do.

## 2026-08-02 07:57 - P10.T01/P10.T08/P11.T06 - PIPER PENDING QUARANTINE

- Nguoi/agent thuc hien: Codex
- Muc tieu: Giam blocker release HF/Piper bang cach dam bao 4 voice chua co license card khong nam trong public Android catalog va khong nhan duoc Supabase ticket, trong khi van giu manifest entry de audit.
- Thoi gian bat dau/ket thuc: 2026-08-02 07:45 / 2026-08-02 07:57 local.
- File thay doi: `ExternalAssetCatalog.kt`, `AssetDeliveryModels.kt`, `ReadConfigScreen.kt`, `ReadAloudConfigSheet.kt`, `AssetDeliveryCatalogResolverTest.kt`, `HfArtifactManifestTest.kt`, `AssetDeliveryClientContractTest.kt`, `scripts/test-cloud-security-gates.mjs`, `scripts/release/verify-rollout-rollback.ps1`, `docs/release/piper-voice-license-review.md`, `reports/P10-T01-HF-ASSET-MANIFEST.md`, `reports/P10-T08-CLOUD-SECURITY-INTEGRATION-GATES.md`, `reports/P11-T06-STAGED-ROLLOUT-ROLLBACK-REHEARSAL.md`, `reports/P11-T07-FINAL-REPORT-PLAN-CHECKPOINT.md`, `reports/artifacts/P11-T06-ROLLBACK-REHEARSAL.json`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Them `releaseEligible` cho TTS voice asset; 4 Piper voice pending (`indo_goreng`, `john`, `mattheo`, `mattheo1`) dat `releaseEligible=false`. Public UI/resolver dung `releaseEligibleTtsVoiceCatalog`; direct internal URI cua 4 voice pending bi reject. Node cloud gate them check `asset-ticket` tra `451 license_review_required`. Rollout verifier ghi them `licensePendingTicketBlocked=true` va `publicPiperCatalogFiltered=true`.
- Lenh kiem tra: `node --test scripts/test-asset-ticket.mjs scripts/test-cloud-security-gates.mjs`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.AssetDeliveryCatalogResolverTest" --tests "io.legado.app.domain.model.HfArtifactManifestTest" --tests "com.drducbook.app.cloud.AssetDeliveryClientContractTest" --console=plain --no-daemon`; `.\gradlew.bat :app:compileAppDebugKotlin --console=plain --no-daemon`; `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\release\verify-rollout-rollback.ps1`.
- Ket qua: Node focused cloud gates PASS 11/11; Android catalog/HF/client contract tests PASS 13/13; sau do rerun gop voi P11.T03 compatibility de giu XML evidence moi nhat PASS 13 lop/49 tests; Kotlin compile PASS; P11.T06 verifier PASS va van quyet dinh `blocked_for_production_rollout` do release APK partial/unsigned, P10 runtime gates va P11.T05 domain/public metadata.
- Bang chung: `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.model.AssetDeliveryCatalogResolverTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.model.HfArtifactManifestTest.xml`; `app/build/test-results/testAppDebugUnitTest/TEST-com.drducbook.app.cloud.AssetDeliveryClientContractTest.xml`; `reports/artifacts/P11-T06-ROLLBACK-REHEARSAL.json`.
- Rui ro/cong viec con lai: 4 voice van chua co license card nen chi co the mo lai khi co provenance hop le; P10 runtime Supabase/Auth/Drive, P11.T05 domain/signing va release APK production van pending.
- Task tiep theo: Tiep tuc runtime/release gates con lai hoac chay compatibility/minified ABI gate neu can tien do local.

## 2026-08-02 08:06 - P11.T03 - COMPATIBILITY LOCAL REFRESH

- Nguoi/agent thuc hien: Codex
- Muc tieu: Lam moi gate compatibility Legado/VBook local sau cac hotfix VBook, media, export/theme va Piper catalog; giam phan `full corpus` pending trong P11.T03.
- Thoi gian bat dau/ket thuc: 2026-08-02 07:58 / 2026-08-02 08:06 local.
- File thay doi: `reports/P11-T03-LEGADO-VBOOK-COMPATIBILITY-REGRESSION.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Chay lai full focused compatibility corpus/VBook parser/importer/inspector/executor/adapter/media parser suite; sau do chay release Kotlin compile de xac nhan source compile duoc tren release variant.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.compat.CompatibilityCorpusTest" --tests "io.legado.app.data.repository.vbook.VbookRegistryParserTest" --tests "io.legado.app.data.repository.vbook.VbookRegistryRepositoryTest" --tests "io.legado.app.data.repository.vbook.VbookImportRepositoryTest" --tests "io.legado.app.help.vbook.VbookPluginImporterTest" --tests "io.legado.app.help.vbook.VbookPluginImporterSecurityTest" --tests "io.legado.app.help.vbook.VbookPluginInspectorTest" --tests "io.legado.app.help.vbook.VbookExecutorTest" --tests "io.legado.app.help.vbook.VbookPluginAdapterTest" --tests "io.legado.app.help.vbook.VbookMediaParserTest" --console=plain --no-daemon`; `.\gradlew.bat :app:compileAppReleaseKotlin --console=plain --no-daemon`.
- Ket qua: 36 focused compatibility/VBook tests PASS, 0 failures/errors; sau do rerun gop voi Piper/catalog tests PASS 13 lop/49 tests de giu XML evidence moi nhat; release Kotlin compile PASS in 4m32s. Full minified/package release ABI gate chua rerun vi o D chi con khoang 1.81 GB va P11.T06 dang bao release output cu partial/unsigned.
- Bang chung: XML test reports trong `app/build/test-results/testAppDebugUnitTest/`; `reports/P11-T03-LEGADO-VBOOK-COMPATIBILITY-REGRESSION.md`.
- Rui ro/cong viec con lai: P11.T03 van IN_PROGRESS vi minified package ABI evidence phu thuoc P11.T06 release/disk/signing gate va device corpus smoke thuc te cho nhieu nguon van can them neu nguoi dung bao source rieng.
- Task tiep theo: Tiep tuc P11.T06 release artifact cleanup/signing gate khi co dung luong, hoac P10 runtime/Supabase gates khi co secret/CLI.

## 2026-08-02 09:25 - P08.T06/P08.T07/P11.T08/P11.T04 - TRANSLATION EXPORT, OPENCODE/OAUTH, TOOL POLICY HOTFIX

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan thien co che chot ban dich trong reader/export, sua export ebook khong hien file sau khi chon xuat, on dinh OpenCode Free/OAuth Codex-Antigravity va sua tool call bi feature policy chan sai ky vong.
- Thoi gian bat dau/ket thuc: 2026-08-02 08:10 / 2026-08-02 09:25 local.
- File thay doi chinh: `TranslationManager.kt`, `ReadBook.kt`, `ReaderContentMode.kt`, `ReadBookViewModel.kt`, `ReadBookContract.kt`, `ReadBookMenuBar.kt`, `ManageTranslationRevisionUseCase.kt`, `TranslationRevisionViewModel.kt`, `ExportBookService.kt`, `EbookExportModels.kt`, `BookshelfManageScreen*.kt`, `BookInfo*.kt`, `EbookExportSheet.kt`, `DocumentUtils.kt`, `AiProviderCatalog.kt`, `AiRouterViewModel.kt`, `AiRouterDashboardMapper.kt`, `FeatureFlags.kt`, `AiOAuthRepository.kt`, strings `values/values-vi`, focused tests va cac report P08/P11 lien quan.
- Tom tat trien khai:
  - Reader co content page `TRANSLATION`, uu tien ban dich da chot/user-edited, sau do cache theo thu tu AI provider -> NMT -> Quick Translator -> Google -> ML Kit.
  - Chot ban dich co the chot ca payload cache cu thanh revision vinh vien; ban chot chi doi khi user sua/chot lai.
  - Export ebook co lua chon `original`, `translation`, `both`; translation-only khong con am tham xuat raw khi thieu cache; noi dung dich xuat ebook dung cung resolver voi reader.
  - SAF export tao file bang MIME type dung theo duoi file de file vua xuat hien dung trong document provider.
  - `opencode_free` chuyen sang OpenCode Console endpoint khong Authorization header; catalog them free models moi va loai `hy3-free`.
  - OAuth authorization-code token exchange chi gui `code_verifier` khi provider bat PKCE, sua Antigravity.
  - Default Agent feature flags bat mutation/skill/plugin tools cho cai dat moi; tool ghi van can approval, nhung khong con bi policy chan truoc khi tao proposal.
- Lenh kiem tra:
  - `.\gradlew.bat :app:compileAppDebugKotlin --console=plain --no-daemon`
  - `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.AiProviderCatalogTest" --tests "io.legado.app.ui.ai.router.AiRouterDashboardMapperTest" --tests "io.legado.app.domain.agent.AgentPermissionBrokerTest" --tests "io.legado.app.data.repository.AiToolRepositoryToolCatalogTest" --console=plain --no-daemon`
  - `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.ReaderContentModeTest" --tests "io.legado.app.ui.book.read.ReaderTranslationModePolicyTest" --tests "io.legado.app.service.export.EbookExportScopeTest" --tests "io.legado.app.domain.usecase.ManageTranslationRevisionUseCaseTest" --console=plain --no-daemon`
  - `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.usecase.AiChatGenerationUseCaseTest" --tests "io.legado.app.data.repository.ai.OpenAiResponsesHandlerTest" --console=plain --no-daemon`
  - `.\gradlew.bat :app:assembleAppDebug --console=plain --no-daemon`
  - `adb -s emulator-5554 install -r -t app\build\outputs\apk\app\debug\app-app-x86_64-debug.apk`
  - `adb -s emulator-5554 shell am start -n com.drducbook.app.debug/io.legado.app.ui.main.MainActivity`
- Ket qua: Kotlin compile PASS; focused provider/tool tests PASS; focused reader/export/revision tests PASS; focused chat tool/Codex Responses tests PASS; debug assemble PASS; x86_64 APK install len `emulator-5554` PASS; app duoc Android restore vao `WebViewActivity` dang mo truoc do.
- APK moi: x86_64 SHA-256 `1091ADAB7EE51C72D5C86DBA2B84A367B83FAA13D6A3A4B2154BAA290A34F529`; universal SHA-256 `B5497F40692091A66861FACA7F50735C7BCF0FAC6CF1FA575D1DAE2FD140B514`.
- Bang chung: `reports/P08-T06-CHATBOT-TOOLS-SKILL-HOTFIX.md`, `reports/P08-T07-AI-ROUTER-PROVIDER-POOL-HOTFIX.md`, `reports/P11-T08-AI-TRANSLATION-PIPELINE-REWRITE.md`, `reports/P11-T04-PERFORMANCE-A11Y-SECURITY-AUDIT.md`, Gradle XML trong `app/build/test-results/testAppDebugUnitTest/`.
- Rui ro/cong viec con lai: Can user smoke tren du lieu that: chot mot chuong dich trong reader, xuat translation-only va kiem tra file trong thu muc da chon; OpenCode Free van phu thuoc endpoint public moi cua provider; OAuth Codex/Antigravity can login live bang tai khoan user de xac nhan token/project/account metadata.
- Task tiep theo: Neu user xac nhan AI provider da on, tiep tuc kiem tra tool call live trong chat/Agent va sau do xu ly cac loi chuc nang con lai.

## 2026-08-02 11:16 - P08.T06 - AGENT TOOL CALL POLICY ALIAS HOTFIX

- Nguoi/agent thuc hien: Codex
- Muc tieu: Sua loi live trong chatbot `Stream interrupted: Tool 'create_vbook_plugin.draft' is disabled by the current feature policy` sau khi provider tra alias tool dang dau cham.
- Thoi gian bat dau/ket thuc: 2026-08-02 10:18 / 2026-08-02 11:16 local.
- File thay doi: `AgentToolNameNormalizer.kt`, `AgentPermissionBroker.kt`, `AiChatGenerationUseCase.kt`, `RunAiAgentUseCase.kt`, `AiToolRepository.kt`, `LabConfig.kt`, `PreferKey.kt`, `App.kt`, `AgentPermissionBrokerTest.kt`, `AiChatGenerationUseCaseTest.kt`, `reports/P08-T06-CHATBOT-TOOLS-SKILL-HOTFIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: Them canonicalizer cho alias tool provider (`create_vbook_plugin.draft` -> `create_vbook_plugin_draft`); dua canonical name qua trace, proposal, approval, permission broker va repository execute. Startup migration nay ghi ca DataStore va SharedPreferences cho ba policy `agentMutation`, `agentSkill`, `agentPlugin`, ke ca truong hop marker cu da true nhung flag cu van false.
- Lenh kiem tra: `.\gradlew.bat :app:compileAppDebugKotlin --console=plain --no-daemon`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.agent.AgentPermissionBrokerTest" --tests "io.legado.app.data.repository.AiToolRepositoryToolCatalogTest" --tests "io.legado.app.domain.usecase.AiChatGenerationUseCaseTest" --console=plain --no-daemon`; `.\gradlew.bat :app:assembleAppDebug --console=plain --no-daemon`; `adb -s emulator-5554 install -r -t app/build/outputs/apk/app/debug/app-app-x86_64-debug.apk`; `adb -s emulator-5554 shell am force-stop com.drducbook.app.debug`; `adb -s emulator-5554 shell am start -n com.drducbook.app.debug/io.legado.app.ui.main.MainActivity`.
- Ket qua: Compile PASS; focused broker/catalog/chat tests PASS; debug assemble PASS; APK x86_64 install PASS; LDPlayer DataStore va SharedPreferences deu xac nhan `featureAgentMutation`, `featureAgentSkill`, `featureAgentPlugin`, `featureAgentToolPolicyUpgrade` la true. X86_64 debug SHA-256 `7BE9A60DA06A33E26C2F9AB73ABB1FFC4A6B871215B546F301DABAD6F0CD95D1`; universal debug SHA-256 `6F6FC9CF11B87FC698391DD00FA4B73303BFD7AABF5474CF721AFCE204B65643`.
- Bang chung: `reports/P08-T06-CHATBOT-TOOLS-SKILL-HOTFIX.md`; Gradle XML trong `app/build/test-results/testAppDebugUnitTest/`; device DataStore/SP decoded sau launch.
- Rui ro/cong viec con lai: Can user thu lai live prompt tao plugin VBook trong chat; tool write/plugin van can hop xac nhan cua app theo thiet ke.
- Task tiep theo: Tiep tuc P11.T06 release/signing gate hoac cac P10 runtime Supabase/Auth/Drive con pending.

## 2026-08-02 11:20 - P11.T06 - RELEASE ZIP RECOVERY VERIFIER REFRESH

- Nguoi/agent thuc hien: Codex
- Muc tieu: Lam moi rollout/rollback verifier sau khi release artifact da duoc build lai, xoa blocker cu `disk-full/partial APK` khoi P11.T06 neu bang chung hien tai khong con dung.
- Thoi gian bat dau/ket thuc: 2026-08-02 11:17 / 2026-08-02 11:20 local.
- File thay doi: `reports/P11-T06-STAGED-ROLLOUT-ROLLBACK-REHEARSAL.md`, `reports/P11-T03-LEGADO-VBOOK-COMPATIBILITY-REGRESSION.md`, `reports/artifacts/P11-T06-ROLLBACK-REHEARSAL.json`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Lenh kiem tra: `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\release\verify-rollout-rollback.ps1`.
- Ket qua: Verifier PASS va ghi JSON moi. APKs total=12; valid debug=4; valid noR8=4; valid release=4; invalid release=0; unsigned release=4; HF artifacts=35; decision van `blocked_for_production_rollout`.
- Bang chung: `docs/plans/drducbook-rebuild-2026/reports/artifacts/P11-T06-ROLLBACK-REHEARSAL.json`; report P11.T06 da cap nhat release SHA-256 moi.
- Rui ro/cong viec con lai: P11.T06 van IN_PROGRESS vi chua co signed release APK/AAB, previous stable rollback artifact, P10 runtime gates va P11.T05 domain/privacy/release metadata.
- Task tiep theo: Kiem tra kha nang signing/release metadata neu co keystore/secrets, hoac tiep tuc cac P10 runtime gates co the chay local.

## 2026-08-02 11:23 - P11.T05/P11.T06 - RELEASE METADATA AND SIGNING BLOCKER AUDIT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Ra soat blocker signing/domain sau khi release ZIP da hop le, tranh nham lan unsigned release voi loi build/disk.
- Thoi gian bat dau/ket thuc: 2026-08-02 11:20 / 2026-08-02 11:23 local.
- File thay doi: `reports/P11-T05-DOMAIN-DOCS-PRIVACY-RELEASE-METADATA.md`, `reports/P11-T06-STAGED-ROLLOUT-ROLLBACK-REHEARSAL.md`, `reports/artifacts/P11-T05-RELEASE-METADATA-CHECK.json`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Lenh kiem tra: `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\release\verify-release-metadata.ps1`; doc/workflow inspection cho `app/build.gradle.kts` va `.github/workflows/auto-release.yml`.
- Ket qua: P11.T05 verifier PASS phan docs 8/8 va assetlinks template hop le; decision van `metadata_blocked_by_external_inputs`. Signing Gradle da san sang nhan `RELEASE_*`, workflow da san sang decode `SIGNING_KEY`, nhung local khong co secret production nen signed release APK/AAB chua the tao hop le.
- Bang chung: `reports/artifacts/P11-T05-RELEASE-METADATA-CHECK.json`; workflow lines `Release Apk Sign` dung `SIGNING_KEY`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- Rui ro/cong viec con lai: Can production HTTPS domain, release signing fingerprint, public privacy/support/terms URLs, Supabase/Google dashboard evidence va signed artifact that.
- Task tiep theo: Neu co external inputs/secrets thi rerun release metadata/signing; neu chua, tiep tuc local P10 runtime contract/gate refresh.

## 2026-08-02 11:36 - P10.T08/P10.T05 - CLOUD LOCAL GATES REFRESH

- Nguoi/agent thuc hien: Codex
- Muc tieu: Lam moi cloud security/RLS/sync/app contract evidence sau cac hotfix moi va truoc khi tiep tuc runtime gates.
- Thoi gian bat dau/ket thuc: 2026-08-02 11:24 / 2026-08-02 11:36 local.
- File thay doi: `reports/P10-T08-CLOUD-SECURITY-INTEGRATION-GATES.md`, `reports/P10-T05-SUPABASE-RLS-STORAGE-FOUNDATION.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Lenh kiem tra: `node --test scripts/test-asset-ticket.mjs scripts/test-cloud-sync-migration.mjs scripts/test-cloud-security-gates.mjs`; `.\gradlew.bat :app:testAppDebugUnitTest --tests "com.drducbook.app.cloud.AssetDeliveryClientContractTest" --tests "io.legado.app.domain.model.HfArtifactManifestTest" --tests "com.drducbook.app.cloud.CloudSyncClientContractTest" --tests "com.drducbook.app.cloud.GoogleDriveAppDataContractTest" --tests "com.drducbook.app.cloud.CloudConsentScopesTest" --tests "com.drducbook.app.cloud.SupabaseClientProviderTest" --tests "io.legado.app.domain.usecase.CloudSnapshotPolicyTest" --console=plain --no-daemon`.
- Ket qua: Node gates PASS 15/15; Kotlin cloud contract suite PASS 36/36; Gradle BUILD SUCCESSFUL in 8m25s.
- Bang chung: XML test reports trong `app/build/test-results/testAppDebugUnitTest/`; P10.T08 report checkpoint 2026-08-02 11:36.
- Rui ro/cong viec con lai: P10.T08/P10.T05 van IN_PROGRESS vi can Supabase CLI/Deno/Edge Function runtime, auth users, Google Drive OAuth/appDataFolder runtime va multi-device restore smoke.
- Task tiep theo: Kiem tra tooling/runtime availability cho Supabase/Deno/Google OAuth, hoac tiep tuc P11 live model/device gates.

## 2026-08-02 11:51 - P11.T01/P11.T04/P11.T08 - FULL UNIT REGRESSION RECOVERY

- Nguoi/agent thuc hien: Codex
- Muc tieu: Sua 2 loi con lai trong full Android debug unit suite sau cac hotfix AI/export: local AI prompt suffix bi mat va export EPUB anh local fail tren duong dan Windows/JVM.
- Thoi gian bat dau/ket thuc: 2026-08-02 11:37 / 2026-08-02 11:51 local.
- File thay doi: `LocalAiTranslationPrompt.kt`, `FileDocExtensions.kt`, `reports/P11-T01-FULL-BUILD-TEST-MATRIX.md`, `reports/P11-T04-PERFORMANCE-A11Y-SECURITY-AUDIT.md`, `reports/P11-T08-AI-TRANSLATION-PIPELINE-REWRITE.md`, `TASK-MATRIX.md`, `PLAN-LOG.md`.
- Tom tat trien khai: `LocalAiTranslationPrompt` nay tach user suffix sau `TranslationConstants.DEFAULT_PROMPT` hien tai va dua vao `STYLE` compact cho local model. `FileDoc.fromDir/fromFile/asFile` dung `Uri.fromFile` va giai ma local file path an toan cho filesystem paths/`file://` Uris co drive letter, tranh `uri.path!!` null khi export EPUB dong goi anh local.
- Lenh kiem tra: `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.LocalAiTranslationPromptTest" --tests "io.legado.app.domain.usecase.ExportAuthoringProjectUseCaseTest" --console=plain --no-daemon`; `.\gradlew.bat :app:testAppDebugUnitTest --no-daemon --console=plain`; `.\gradlew.bat :app:assembleAppDebug --console=plain --no-daemon`; `adb -s emulator-5554 install -r -t app/build/outputs/apk/app/debug/app-app-x86_64-debug.apk`; `adb -s emulator-5554 shell am start -n com.drducbook.app.debug/io.legado.app.ui.main.MainActivity`.
- Ket qua: Targeted regression tests PASS; full Android debug unit suite PASS latest: 222 XML files, 944 tests, 0 failures, 0 errors, 1 skipped; debug assemble PASS; x86_64 APK install len `emulator-5554` PASS; app process con song sau launch va logcat 700 dong gan nhat khong co crash match. Mot tien trinh Gradle bi timeout trong lan chay dau da duoc dung truoc khi rerun sach.
- APK moi: x86_64 SHA-256 `566E5F51ADC95AFD3E18D85282611CD9F15378922F4EB778288038D602DFBD90`; universal SHA-256 `A45E63380E69F3B010AFE75880F08370475316DC3D9C526A9134766C95D39F06`.
- Bang chung: XML reports trong `app/build/test-results/testAppDebugUnitTest/`; `reports/P11-T01-FULL-BUILD-TEST-MATRIX.md`.
- Rui ro/cong viec con lai: P11.T01/P11.T04/P11.T08 van IN_PROGRESS vi con live model chapter gate, device perf/accessibility metrics va P10/P11 external runtime/signing/domain gates.
- Task tiep theo: Tiep tuc live AI provider/tool-call smoke hoac runtime Supabase/Drive gates khi co moi truong/credential can thiet.

## 2026-08-02 12:00 - PROJECT MEMORY CHECKPOINT

- Nguoi/agent thuc hien: Codex
- Muc tieu: Luu ket qua phien lam viec vao memory project de phien sau tiep tuc nhanh, khong can doc lai toan bo thread.
- File thay doi: `PROJECT-MEMORY.md`, `PLAN-LOG.md`.
- Ket qua: Tao `docs/plans/drducbook-rebuild-2026/PROJECT-MEMORY.md` voi workspace, APK/hash moi nhat, test/build/install evidence, checkpoint da hoan tat, open gates va thu tu viec tiep theo. Khong luu token/secret.
- Task tiep theo: Dung `PROJECT-MEMORY.md` lam diem resume truoc khi tiep tuc live Agent tool-call, AI provider smoke, export translation smoke hoac P10/P11 runtime/release gates.

## 2026-08-04 - P10.T10 - DOWNLOAD-SUPABASE-NMT-SYNC - IN_PROGRESS

- Nguoi/agent thuc hien: Codex
- Muc tieu: Thuc thi plan khac phuc tai goi, Supabase account access, session, sao luu/dong bo va NMT.
- File thay doi: `SupabaseAccountAuthRepository.kt`, `AccountViewModel.kt`, `AccountScreen.kt`, `AssetDeliveryRepository.kt`, `AssetDeliveryViewModel.kt`, `HachimiOnnxTranslator.kt`, `supabase/scripts/promote-drducqy95-admin.sql`, `reports/P10-T10-DOWNLOAD-SUPABASE-NMT-SYNC-EXECUTION.md`.
- Tom tat trien khai: Session observer cho doi initialization va bo qua refresh failure tam thoi; access/quota duoc tai doc lap; backup UI hien ly do khi access chua san sang; asset error co body HTTP gioi han; NMT co memory preflight, che do low-RAM va cleanup staged ONNX sessions; them script promotion admin server-side cho `drducqy95@gmail.com`.
- Lenh kiem tra: `./gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`; `./gradlew.bat :app:testAppDebugUnitTest --no-daemon --console=plain`; `node --test scripts/test-account-access-migration.mjs scripts/test-cloud-sync-migration.mjs scripts/test-asset-ticket.mjs`.
- Ket qua: Kotlin compile PASS; Android unit suite PASS; Node migration/asset tests 12/12 PASS.
- Bang chung: `reports/P10-T10-DOWNLOAD-SUPABASE-NMT-SYNC-EXECUTION.md`.
- Rui ro/cong viec con lai: Supabase project van tra 404 cho Edge Functions va PGRST205 cho cac bang; CLI khong co trong PATH va dashboard chua dang nhap trong moi truong, nen migration/deploy va promotion admin chua the chay. NMT can crash trace sau khi model tai thanh cong.
- Task tiep theo: Dang nhap Supabase dashboard hoac cung cap kenh deploy server-side; apply migration/deploy functions, chay promotion admin va smoke test runtime.

## 2026-08-04 - P10.T10 - RUNTIME DEPLOYMENT CHECKPOINT - IN_PROGRESS

- Nguoi/agent thuc hien: Codex
- Muc tieu: Hoan tat phan deploy Supabase va ghi nhan bang chung runtime sau khi user lien ket CLI project.
- Lenh da chay: `npx supabase db push`; `npx supabase secrets set`; `npx supabase functions deploy asset-ticket --use-api`; `npx supabase functions deploy asset-download --no-verify-jwt --use-api`; `npx supabase functions list`; `npx supabase db query --linked`; `npx supabase db query --linked --file supabase/scripts/promote-drducqy95-admin.sql`.
- Ket qua: Bon migration ap dung thanh cong; sau bang cloud/account ton tai; hai Edge Function ACTIVE voi che do JWT dung thiet ke; secret `ASSET_TICKET_SECRET` va `HF_READ_TOKEN` da cau hinh (khong ghi gia tri); endpoint ticket/download khi thieu token tra 401 thay vi 404; `account_access` khong con PGRST205; tai khoan admin muc tieu da duoc xac minh voi role/permissions day du.
- Cap nhat tai lieu: `SUPABASE-DEPLOYMENT-GUIDE.md` ghi ro bien `SUPABASE_*` duoc runtime inject, khong dat lai bang CLI; them canh bao rotate legacy service-role key neu da lo trong terminal/log. Report P10.T10 da chuyen sang `RUNTIME DEPLOYED, AUTHENTICATED SMOKE PENDING`.
- Rui ro/cong viec con lai: Can access token that de smoke test cap ticket/tai asset, thao tac backup/restore Supabase va Google Drive, NMT tren APK moi; can rotate/revoke legacy service-role key sau khi kiem tra API Keys. APK debug chua cai de len emulator do mismatch chu ky; khong uninstall de bao toan du lieu.
- Task tiep theo: Chay full unit/build lai sau patch cuoi, cap nhat hash APK, sau do thuc hien authenticated runtime smoke va dong task khi cac gate trong report dat.

## 2026-08-04 - P10.T10 - LOCAL BUILD AND PLAN AUDIT - IN_PROGRESS

- Nguoi/agent thuc hien: Codex
- Lenh kiem tra: `node --test scripts/test-account-access-migration.mjs scripts/test-cloud-sync-migration.mjs scripts/test-asset-ticket.mjs`; `.\gradlew.bat :app:testAppDebugUnitTest --no-daemon --console=plain`; `.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain`.
- Ket qua: Node 12/12 PASS; Android unit BUILD SUCCESSFUL; debug assemble BUILD SUCCESSFUL.
- APK moi: x86_64 SHA-256 `0EC6C620191EFFAD2A70DEEF6AFF916D274E33B210457A0BA8F9EDFD86928DC5`; universal SHA-256 `7AAD5640FF99F1FB2420B87023126A926E8A589AA2F108860A20AB087DBBED1D`.
- Ke hoach `PLAN-20260804-DOWNLOAD-SUPABASE-NMT-SYNC.md` da cap nhat sang `IN_PROGRESS - RUNTIME DA TRIEN KHAI, CHO SMOKE TEST XAC THUC`, ghi ro bang chung migration/function/schema/admin, va tach gate da xong/con mo.
- Rui ro/cong viec con lai: APK khong cai de len emulator hien tai do mismatch chu ky; authenticated ticket/download, backup/restore, process recreation va NMT smoke chua co bang chung tren APK moi. Khong uninstall emulator de tranh mat du lieu.

## 2026-08-04 - P10.T10 - STORAGE AND RUNTIME EVIDENCE REFRESH - IN_PROGRESS

- Kiem tra: `npx supabase db query --linked "select id, public from storage.buckets order by id;"`.
- Ket qua: Hai bucket `drducbook-snapshots` va `drducbook-user-assets` ton tai, deu private; bo sung bang chung vao plan/report.

## 2026-08-04 - P11.T06 - SIGNED RELEASE CHECKPOINT - IN_PROGRESS

- Lenh: `.\gradlew.bat :app:assembleAppRelease --no-daemon --console=plain`; `.\gradlew.bat :app:bundleAppRelease -PdisableAbiSplits=true --no-daemon --console=plain`.
- Ket qua: APK release BUILD SUCCESSFUL (4 ABI artifacts); AAB release BUILD SUCCESSFUL. APKs da verify v2/v3 voi certificate fingerprint `5deac3fa21ff41c7319041ae2e4e3c1f2ce71e3c31c34113f218411d88872803`.
- Artifact/hash: APK arm64 `257372DA43986A92BA9048D0334EFC6A6F1E5B52269EEDE1C2199DB7824C20C8`; armeabi `82877D87CBC99F9B238EAAC0FFCAD1BE3EDDD4DDF73CDE0A7216B4FAE66F0F2B`; x86_64 `EE7F86C10040948025A200870066D80C09F34DC7E84BF4B48B21DAB5608C218D`; universal `F8A2A523554BDD1734FAD26F9353C5086DE42A208E953C233342470608CDF743`; AAB `954D17C8FA0FFC5BEFAD02509092812EDF89F2CE70AD3EC7ED574112FC314939`.
- Build config: Them `-PdisableAbiSplits=true` de AAB khong bi loi multiple shrunk-resources; APK split mac dinh van giu nguyen. 
- Rui ro/cong viec con lai: P11.T05 domain/public metadata, previous stable rollback artifact va P10 authenticated runtime smoke van IN_PROGRESS.
