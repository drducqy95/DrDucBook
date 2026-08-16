# P01.T04 - Supabase va build identities

## Ket qua

Trang thai: `DONE`

- Supabase Kotlin BOM `3.6.0` va Auth/Postgrest/Storage/Functions duoc them cung Ktor OkHttp engine.
- Public config duoc inject tu environment hoac `local.properties`: `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `GOOGLE_AUTH_CLIENT_ID`, `GOOGLE_DRIVE_CLIENT_ID`.
- Khong co secret server trong BuildConfig; `sb_secret_*` bi tu choi.
- Auth dung PKCE va callback `drducbook://auth/callback`.
- `DrDucBookApplication` khoi tao/validate client khi co config; config trong thi no-op an toan.
- Google sign-in scopes chi gom `openid`, `email`, `profile`; Drive backup giu luong consent rieng voi `drive.appdata` cho P10.

## Firebase removal

- Go Google Services plugin, Firebase BOM, Analytics, Performance va `google-services.json`.
- Go Firebase preference/runtime manager/init provider va baseline profile entries cu.
- Packaged manifest khong co `FirebaseInitProvider`; APK khong co FirebaseApp/Analytics/Performance config.
- ML Kit OCR/translate van dung namespace `com.google.firebase.components`/encoders nhu mot SPI transitive noi bo. Chung duoc giu de khong pha OCR/dich, nhung khong khoi tao Firebase App va khong phai analytics/performance backend.

## Verification

- `SupabaseClientProviderTest`: blank config, publishable config va server-secret rejection PASS.
- `CloudConsentScopesTest`: login khong xin Drive scope PASS.
- Callback/manifest tests PASS; startup thiet bi khong crash khi deployment config dang trong.
- Debug signing SHA-1: `45:EF:05:67:37:FC:3C:F6:94:BB:AF:53:A1:87:5C:5D:EB:0D:E2:3E`.
- Debug signing SHA-256: `65:65:EC:30:42:09:57:CC:DE:42:C8:23:9F:29:DF:13:8C:06:B9:1B:4B:29:FE:4A:52:5E:6C:14:A0:AF:38:F1`.

## Deployment handoff

Supabase project URL/publishable key va hai Google client ID chua duoc ghi vao repo. P10 se dang ky project dashboard, Google OAuth fingerprints va live login/Drive consent. Google Drive backup van nam trong plan va khong dung de phan phoi package.
