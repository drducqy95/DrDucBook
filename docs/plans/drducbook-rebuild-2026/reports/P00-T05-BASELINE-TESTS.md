# P00.T05 - Baseline build va test

## Ket qua

Trang thai: `DONE`

Tat ca mandatory Android/Web/security gates cua Phase 00 da pass ngay 2026-07-29. Khong co baseline blocker.

## Gate matrix

| Gate | Thoi gian | Ket qua | Bang chung |
|---|---:|---|---|
| `:app:compileAppDebugKotlin` | 34 giay | PASS | Gradle problems report; compile task success |
| Focused unit suite | 52 giay | PASS, 54 tests/17 suites | XML trong `app/build/test-results/testAppDebugUnitTest/` tai thoi diem chay |
| Full `:app:testAppDebugUnitTest` | 1 phut 59 giay | PASS, 655 tests/156 suites; 1 skipped | `app/build/test-results/testAppDebugUnitTest/`, HTML test report |
| `:app:assembleAppDebug` | 4 phut 33 giay | PASS, 4 APK | `app/build/outputs/apk/app/debug/` |
| `pnpm --dir modules/web type-check` | 30 giay | PASS | `vue-tsc --build --force` exit 0 |
| `pnpm --dir modules/web build` | 38 giay | PASS | Vite 5.4.21, 1,633 modules, build 22.66 giay |
| Final secret scan | khoang 83 giay | PASS, 5 allow-listed, 0 unapproved | `scripts/security/scan-secrets.ps1` |

## Focused suite moi

Claim cu noi "42 focused tests" nhung workspace/docs khong luu canonical class list de lap lai. P00 thay claim mo ho bang lenh co pattern ro rang bao phu compatibility, secret logging, VBook, media, cache/download va Ebook export:

```powershell
.\gradlew.bat :app:testAppDebugUnitTest `
  --tests "io.legado.app.compat.CompatibilityCorpusTest" `
  --tests "io.legado.app.help.http.CookieManagerSecurityTest" `
  --tests "io.legado.app.help.vbook.*" `
  --tests "io.legado.app.data.repository.vbook.*" `
  --tests "io.legado.app.help.media.*" `
  --tests "io.legado.app.model.cache.*" `
  --tests "io.legado.app.service.export.*" `
  --no-daemon --console=plain
```

Ket qua la 54 tests/17 suites, 0 failure/error/skipped, vuot moc 42 va co the lap lai. Full suite sau do pass 655 tests, 0 failures, 0 errors va 1 skipped: `QuickDictionaryPackStoreTest.fiveMillionLineImportAndWarmLookupPerformanceGate` (performance gate duoc danh dau skip san, khong phai regression P00).

## APK artifacts

| APK | Bytes | SHA-256 |
|---|---:|---|
| `app-app-arm64-v8a-debug.apk` | 220,949,872 | `aee5227af0ab7cca0b22c3461e95c8020ebbb24b3763ea97514dba8400794018` |
| `app-app-armeabi-v7a-debug.apk` | 182,281,685 | `05ca3a7008ad43d847a314e678ace9eec1c559d4375ccd2421a0afd454c7b50a` |
| `app-app-x86_64-debug.apk` | 243,096,244 | `583ceaf7229561a0b8f584025bdddfbacabfe8cd610f394434a633603f0b9d3f` |
| `app-app-universal-debug.apk` | 336,044,777 | `e5fe89f2c83b24456a666d1a3be59ab51ec3d8f954b247382184c31e0d36c1e5` |

Day la artifact baseline cua app cu truoc P01, chua mang application ID/icon DrDucBook.

## Web evidence

- Production output: 15 files, 703,828 bytes.
- Packaged Android web assets: 15 files, 703,828 bytes.
- Build script xoa/tao lai `app/src/main/assets/web/vue` va sync thanh cong.
- Largest output la `vendor-DQqeohSF.js` 429.39 kB, gzip 151.46 kB.

## Canh bao baseline

Khong can sua trong P00, nhung phai theo doi trong phase owner:

1. `android.newDsl=false` deprecated va se bi go trong AGP 10.
2. Gradle `applicationVariants` API obsolete; can chuyen Android Components API.
3. Baseline Profile plugin bao chua test voi AGP 9.2.1.
4. Deprecated Gradle features hien lam build khong tuong thich Gradle 10.
5. `processAppDebugGoogleServices` van chay; Firebase/google-services removal la P01.T04 theo ADR-008.
6. `copyRoomSchemas NO-SOURCE`; Room schema export/migration evidence can audit lai truoc schema change.

## Commands

```powershell
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain
pnpm --dir modules/web type-check
pnpm --dir modules/web build
& .\scripts\security\scan-secrets.ps1
```

## Phase 00 gate

- P00.T01-P00.T05 co report/log va deu `DONE`.
- Secret scan sach; token HF da gui van la compromised external-revoke gate.
- Compatibility corpus co 20 payload/provenance va execution tests.
- 8 ADR khoa architecture Supabase + optional Google Drive backup.
- Phase 01 co the bat dau tu P01.T01; khong tu dong thay doi app identity trong P00.
