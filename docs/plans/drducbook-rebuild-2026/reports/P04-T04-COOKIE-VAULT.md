# P04.T04 - CookieVault schema va encryption

## Ket qua

Trang thai: `DONE`

## Trien khai

- Them `SourceCookieGateway` lam contract chung cho cookie source, giu API `cookie.get/set/remove` tu Legado/VBook thong qua `CookieStore`.
- Them bang Room `cookie_vault` voi `scopeKey`, `domain`, `path`, `name`, `valueCiphertext`, `origin`, `expiresAt`, `secure`, `httpOnly`, `sameSite`, `hostOnly`, `persistent`, `createdAt`, `updatedAt`.
- Tang Room schema len `107`, them migration `106 -> 107` va export schema `107.json`.
- Them `AndroidCookieVaultCodec` dung AES-GCM key trong Android Keystore; gia tri cookie persistent chi luu o cot `valueCiphertext`.
- Them `CookieVaultRepository` de merge, replace, xoa theo key, cleanup expiry, match domain/path/hostOnly va migrate plaintext legacy tu bang `cookies`.
- Them `CookieHeaderCodec` va `CookieScopeResolver` de gom/parse cookie header co thu tu on dinh va fallback scope theo host sach khi public suffix lookup khong kha dung.
- Noi `CookieManager`/`CookieStore` qua `SourceCookieGateway`, giu session cookie trong memory va persistent cookie trong vault.
- Startup app goi migration legacy cookie mot lan; log chi ghi so luong record migrate, khong ghi cookie value.
- CookieVault khong duoc them vao backup/sync surface; cookie plaintext legacy duoc xoa sau khi import vao vault.

## Verification

- `:app:compileAppDebugKotlin`: PASS.
- `:app:testAppDebugUnitTest --tests "io.legado.app.data.cookie.CookieVaultRepositoryTest" --tests "io.legado.app.help.http.CookieManagerSecurityTest"`: PASS.
- `CookieVaultRepositoryTest`: 3 tests, 0 failure, 0 error, 0 skipped.
- `CookieManagerSecurityTest`: 1 test, 0 failure, 0 error, 0 skipped.

## Bang chung

- `app/src/main/java/io/legado/app/domain/gateway/SourceCookieGateway.kt`
- `app/src/main/java/io/legado/app/data/entities/CookieVaultEntity.kt`
- `app/src/main/java/io/legado/app/data/dao/CookieDao.kt`
- `app/src/main/java/io/legado/app/data/cookie/AndroidCookieVaultCodec.kt`
- `app/src/main/java/io/legado/app/data/cookie/CookieHeaderCodec.kt`
- `app/src/main/java/io/legado/app/data/cookie/CookieScopeResolver.kt`
- `app/src/main/java/io/legado/app/data/cookie/CookieVaultRepository.kt`
- `app/src/main/java/io/legado/app/data/AppDatabase.kt`
- `app/src/main/java/io/legado/app/data/DatabaseMigrations.kt`
- `app/schemas/io.legado.app.data.AppDatabase/107.json`
- `app/src/main/java/io/legado/app/di/appModule.kt`
- `app/src/main/java/io/legado/app/App.kt`
- `app/src/main/java/io/legado/app/help/http/CookieManager.kt`
- `app/src/main/java/io/legado/app/help/http/CookieStore.kt`
- `app/src/main/java/io/legado/app/utils/CookieManagerExtensions.kt`
- `app/src/test/java/io/legado/app/data/cookie/CookieVaultRepositoryTest.kt`
- `app/src/test/java/io/legado/app/help/http/CookieManagerSecurityTest.kt`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.cookie.CookieVaultRepositoryTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.http.CookieManagerSecurityTest.xml`

## Rui ro con lai

- P04.T05 van can dong bo day du WebView, OkHttp, Cronet, Rhino va VBook qua cung gateway theo tung navigation/request.
- `CookieManager.applyToWebView` hien con goi `removeSessionCookies` toan cuc; cleanup nay thuoc P04.T05 de tranh xoa nham session ngoai source.
- Truong hop Android Keystore mat key hien fail closed bang cach bo qua value khong decrypt duoc va xoa record loi khi doc; can thiet ke UX logout/re-login ro hon o P04.T06.
