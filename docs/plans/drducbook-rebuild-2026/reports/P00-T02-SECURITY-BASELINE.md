# P00.T02 - Security baseline

Thoi diem: 2026-07-29  
Pham vi: secret storage, repository scan, logging/redaction, backup exclusions va external credential handling.

## 1. Secret policy da khoa

| Secret/credential | Noi luu cho phep | Cam |
|---|---|---|
| Hugging Face token | Supabase Edge Function secret `HF_READ_TOKEN` | APK, BuildConfig, source, test fixture, log, Room, backup |
| Supabase publishable key | Build config/environment; public client config, chi an toan khi RLS bat | Hard-code service/secret key, dung de bypass RLS |
| Supabase secret/service key | Supabase-managed Edge Function secrets | Android/Web client, repository, log, backup |
| Supabase access/refresh token | Supabase Auth SDK-managed secure session | Log, analytics, crash report, backup, Agent/WebService |
| Google ID token/nonce | Memory tam thoi de exchange voi Supabase Auth | Room plaintext, log, backup, browser storage |
| Google Drive access/refresh token | Google/Android provider-managed secure authorization session, chi sau `drive.appdata` consent | Supabase session, Room plaintext, log, backup, Agent/WebService |
| AI provider credential | `AndroidAiSecretStore`/encrypted reference | Screen context, audit payload, export, backup |
| Source/Browser cookie | CookieVault ma hoa trong P04; hien tai DB plaintext la migration risk | Log, Agent context, WebService, backup |
| Signing key | CI/local secret store | Repository, docs, build output archive cong khai |

Token HF da gui trong conversation duoc xem la compromised, khong duoc su dung cho bat ky build/deploy nao. Viec revoke tren Hugging Face la external action khong the xac minh tu workspace; P10/P11 phai yeu cau bang chung token cu da revoke va token moi read-only duoc dat bang `supabase secrets set` truoc deploy.

## 2. Repository scan

- Scanner: `scripts/security/scan-secrets.ps1`.
- Scanner chi in detector, relative file va line; khong in matched value.
- Build outputs, Gradle cache, IDE files va `node_modules` bi loai mac dinh.
- Allow-list toi thieu:
  - `app/google-services.json`: Firebase mobile client configuration hien co, khong phai server secret; P01 se go file/plugin nay khi chuyen sang Supabase.
  - Ba test redaction/validator co token gia co chu dich.
- Moi finding moi ngoai allow-list lam script exit code 1.

## 3. Logging va redaction

- Agent audit da co `sanitizeForAgentAudit` cho JSON secret fields, Authorization, query token va known token formats.
- Source-health diagnostics da co redaction va length cap; engine moi phai tiep tuc dung structured/redacted evidence.
- Da sua `CookieManager.loadRequest` error path: khong ghi cookie value, exception message hoac throwable stack vao `AppLog`; chi ghi domain va exception class.
- Da them `CookieManagerSecurityTest` de ngan regression.
- P04 phai thay Cookie DB plaintext bang encrypted CookieVault va exclude cookies khoi backup.

## 4. Ignore policy

`.gitignore` da bo sung `.env*` (tru `.env.example`), JKS/keystore, `.secrets` va service-account key JSON patterns. `google-services.json` la current-state allow-list va phai duoc xoa cung Firebase plugin tai P01.

## 5. Backup va telemetry exclusions

- Bat buoc exclude: HF/Supabase/Google/AI tokens, source cookies, WebView sessions, pairing codes/session tokens, translation cache, browser history va backend secret refs co gia tri.
- Log/analytics chi duoc ghi logical IDs, status codes, durations, byte counts va redacted error classes.
- Crash reports khong duoc attach request/response headers hoac serialized account/source objects chua redaction.

## 6. Rotation va incident process

1. Danh dau credential compromised va vo hieu hoa tai provider.
2. Chay scanner, APK scan va log scan; xac dinh no tung xuat hien o dau.
3. Tao credential moi voi least privilege; luu trong Supabase project secrets hoac provider-managed client session dung boundary.
4. Deploy/restart workload, xac minh old credential bi reject va new credential hoat dong.
5. Ghi logical credential ID, thoi gian, owner va evidence vao operational log, khong ghi value.

## 7. Dieu kien pass P00.T02

- Secret scanner exit 0 voi 0 unapproved finding.
- Cookie logging regression test pass.
- Ignore rules va policy report duoc tao.
- External HF token duoc danh dau compromised va khong duoc dung; revoke evidence la gate truoc P10 deploy/P11 release.

## 8. Bang chung thuc thi

- `scripts/security/scan-secrets.ps1`: PASS trong 79,7 giay; 5 finding allow-listed, 0 finding unapproved.
- Allow-listed findings: 2 Firebase mobile API keys trong `google-services.json`, 3 Bearer token gia trong redaction/validator tests.
- `:app:testAppDebugUnitTest --tests io.legado.app.help.http.CookieManagerSecurityTest`: BUILD SUCCESSFUL, 1 test pass.
- Lan chay test dau vuot timeout 120 giay trong luc Gradle warm-up; lan chay lai voi timeout 360 giay hoan tat trong 61,8 giay.
