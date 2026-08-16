# P11.T05 - Domain, docs, privacy va release metadata

Status: IN_PROGRESS - code/config/docs checkpoint PASS, external domain/OAuth/public URL approvals pending

## Muc tieu

Hoan tat cac input ben ngoai va tai lieu can co truoc rollout: domain/app links, Supabase OAuth redirect/site URL, Google OAuth/Drive consent metadata, privacy/legal/support/update docs, release notes va runbook van hanh.

## Pham vi

- Android identity/domain/deep link manifest.
- Supabase public config, Auth callback, Storage/Postgres/Functions runtime metadata.
- Google Sign-In va Google Drive `appDataFolder` consent contract.
- WebService pairing/ports docs, side-by-side behavior, support/privacy/release docs.
- `assetlinks.json` va HTTPS app-link verification khi co domain that.

## Ket qua audit hien tai

- Package release da la `com.drducbook.app`; debug la `com.drducbook.app.debug`.
- Supabase client doc public config tu `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `GOOGLE_AUTH_CLIENT_ID`, `GOOGLE_DRIVE_CLIENT_ID`.
- Supabase config validate HTTPS URL va chan `sb_secret_` trong publishable key.
- Auth callback runtime dang dung custom scheme `drducbook://auth/callback` qua `AuthCallbackActivity` va Supabase PKCE.
- Google Sign-In scope tach Drive: chi `openid`, `email`, `profile`.
- Google Drive backup scope rieng chi `https://www.googleapis.com/auth/drive.appdata`.
- Drive backup namespace la `drducbook`, head file `drducbook-head.json`, appData path khong dung full Drive scope.
- Supabase storage namespaces da co trong migration: `drducbook-snapshots`, `drducbook-user-assets`.
- Da tao release docs noi bo tai `docs/release/`: privacy, terms, support, release notes, operations runbook, app-links checklist, Google OAuth consent checklist, Supabase runtime checklist.
- Da tao `docs/release/assetlinks.template.json` cho package `com.drducbook.app`; template con can release SHA-256 signing fingerprint truoc khi publish thanh `.well-known/assetlinks.json`.
- Da tao verifier `scripts/release/verify-release-metadata.ps1` va JSON evidence `reports/artifacts/P11-T05-RELEASE-METADATA-CHECK.json`.
- Release signing config trong `app/build.gradle.kts` chi bat khi co bo `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
- Workflow `.github/workflows/auto-release.yml` tao `app/my-release-key.jks` tu GitHub secret `SIGNING_KEY` va ghi cac `RELEASE_*` vao `gradle.properties`; local workspace hien khong co secret production de tao signed artifact hop le.

## External input/blocker

- Chua co domain HTTPS that de cau hinh app links, asset links va link checker.
- Chua co `assetlinks.json` production vi can Play/App signing certificate fingerprint hoac signing key rollout target; hien moi co template.
- Chua co bang chung Supabase dashboard da cau hinh:
  - Site URL;
  - Redirect URL `drducbook://auth/callback`;
  - Neu chuyen sang HTTPS app link sau nay: URL callback HTTPS tu domain that.
- Chua co bang chung Google Cloud Console/OAuth consent screen:
  - app name/support email/developer contact;
  - Android OAuth client package `com.drducbook.app`;
  - SHA-1/SHA-256 signing cert release/debug neu can;
  - Drive `appDataFolder` scope justification.
- Chua co public URL privacy policy/support/terms de gan vao Google consent va release metadata; docs noi bo da san sang de publish.

## Data-flow privacy checklist

- Account/Auth: Supabase Auth session, Google Sign-In profile/email theo consent rieng.
- Backup/sync: Supabase Postgres + private Storage snapshot; Google Drive `appDataFolder` tuy chon, khong xin full Drive.
- User assets: private Supabase Storage cho snapshot/background/tai san app.
- Local data: sach/nguon/plugin/cookie/cache/model local; snapshot policy can noi ro cai gi duoc/khong duoc dong bo.
- Browser/source cookies: dung cho nguon sach/RSS/video va khong dua vao snapshot theo cloud security plan.
- AI: API key/token/model profile/user prompts co the luu local/cloud theo chinh sach; khong log secret; Agent tool audit can redaction.
- HF assets: goi tai xuong model/dictionary qua manifest/HF mirror, khong can user token trong app runtime.
- Data deletion: can mo ta xoa tai khoan, xoa Supabase data, xoa Drive appData, xoa local data/cache.

## Lenh kiem tra

```powershell
rg -n "applicationId|namespace|SUPABASE|supabase|redirect|OAuth|oauth|assetlinks|drducbook" app/build.gradle.kts app/src/main supabase docs -g "!*build*"
rg --files | rg "assetlinks\.json|\.well-known|privacy-policy|terms|support|release-notes|runbook|app-links"
.\gradlew.bat :app:testAppDebugUnitTest --tests "com.drducbook.app.cloud.CloudConsentScopesTest" --tests "com.drducbook.app.cloud.GoogleDriveAppDataContractTest" --tests "com.drducbook.app.cloud.SupabaseClientProviderTest" --tests "com.drducbook.app.cloud.CloudSyncClientContractTest" --console=plain --no-daemon
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\release\verify-release-metadata.ps1
```

## Ket qua

- Static config audit PASS cho package, Supabase config path, Auth callback, Drive appData contract va migration bucket names.
- Release docs verifier PASS cho 8/8 docs noi bo; khong thay secret pattern trong docs.
- Assetlinks template JSON hop le va dung package `com.drducbook.app`.
- Assetlinks fingerprint/public domain/privacy/support/terms URLs van pending external input.
- Focused cloud/OAuth contract tests PASS in 1m39s.
- Verifier refresh 2026-08-02 11:18: docs checked 8/8 ok; assetlinks template exists/valid/package ok; decision `metadata_blocked_by_external_inputs`.

## Dieu kien thong qua con lai

- Co domain HTTPS that va `assetlinks.json` verified.
- Supabase dashboard redirect/site URL duoc cau hinh va co screenshot/log verification.
- Google OAuth consent/client metadata duoc cau hinh, Drive scope justification approved neu can.
- Privacy/support/terms/release/runbook docs co URL/link checker pass.
- Release metadata khong con placeholder domain/icon/secret.

## Nhat ky

2026-08-01 16:43 - STARTED. Mo P11.T05 tu TODO sang audit checkpoint. Da xac nhan code contract Supabase/Google Drive co nen tang dung, nhung external domain/OAuth/privacy URLs chua duoc cung cap trong repo nen task chua the DONE.

2026-08-01 19:21 - DOCS CHECKPOINT PASS. Them `docs/release` gom privacy/terms/support/release notes/runbook/app-links/Google OAuth/Supabase runtime checklist va `assetlinks.template.json`. Them verifier `scripts/release/verify-release-metadata.ps1`; ket qua docs ok 8/8, template assetlinks hop le, blocker con lai la public HTTPS domain, privacy/support/terms URLs va release signing fingerprint.

2026-08-02 11:18 - RELEASE METADATA REFRESH. Chay lai `scripts/release/verify-release-metadata.ps1`: docs checked=8, docs ok=8, assetlinks template hop le, fingerprint placeholder van pending. Blocker con lai duoc thu hep ro thanh 5 external inputs: `assetlinks_fingerprint_pending`, `public_domain_pending`, `privacy_url_pending`, `support_url_pending`, `terms_url_pending`. Ra soat signing workflow xac nhan signed production build can GitHub secret `SIGNING_KEY` va bo `RELEASE_*`; local workspace khong co production signing secret nen khong tao signed APK gia.
