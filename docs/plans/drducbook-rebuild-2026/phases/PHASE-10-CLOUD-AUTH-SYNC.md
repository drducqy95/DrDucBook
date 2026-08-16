# Phase 10 - Hugging Face, Supabase Auth va Sync

## Muc tieu phase

Loai package Google Drive URLs, phan phoi private assets tu Hugging Face ma khong dua HF token vao app; them tai khoan Supabase va snapshot co conflict tren Supabase/Google Drive tuy theo dich nguoi dung chon.

## Pham vi file chinh

- `ExternalAssetCatalog.kt`, `LocalAiModelCatalog.kt`, download/install repositories
- `supabase/functions/**`, `supabase/migrations/**`, local Supabase config va deploy runbook `[NEW]`
- Supabase Auth/account UI, Credential Manager va OAuth callback
- Postgres metadata repositories, private Storage snapshot/assets, Google Drive `appDataFolder` adapter va restore
- Gradle dependencies/build config, RLS/security/integration tests

## Ranh gioi kien truc

- Hugging Face dataset `Drduc/Legadofork` la kho nguon chuan cua package va model.
- `HF_READ_TOKEN` chi ton tai trong Supabase Edge Function secrets; Android, WebService, Vue web, log va backup khong duoc nhan token nay.
- Edge Function xac thuc Supabase JWT, allow-list artifact va cap ticket mot lan co TTL ngan. Download endpoint proxy `HEAD`/`GET`/`Range` tu HF theo streaming, khong buffer toan bo file.
- Artifact vuot ngan sach size/thoi gian Edge Runtime phai duoc release pipeline mirror bat bien tu HF sang private Supabase Storage; app chi nhan signed URL TTL ngan sau khi Edge Function xac thuc manifest/hash. Khong cho app tu chon bucket/path tuy y.
- Supabase publishable key la client config cong khai; moi bang/bucket client truy cap deu bat RLS. Secret/service-role key chi o server-side secrets.
- Google co hai consent tach biet: social identity cho Supabase Auth va optional Drive authorization voi scope `drive.appdata`. Login khong tu xin Drive; email user co the link Drive sau. Drive chi luu snapshot DrDucBook, khong phan phoi package.

## Task chi tiet

### P10.T01 - Kiem ke/upload package va HF manifest

**Muc tieu:** Moi asset co path/version/hash va khong con catalog Drive URL.

**Pham vi file:** External/local AI catalogs, asset inventory/manifest, upload metadata va provenance docs.

**Thuc hien:** Inventory URL/file/license/size/ABI/language/unpack; upload vao `Drduc/Legadofork`; tao signed/versioned manifest va release mapping; verify SHA-256; danh dau artifact can Supabase Storage mirror theo size/runtime budget da benchmark.

**Dieu kien thong qua:** 100% catalog asset co manifest entry; hash/size match; license/provenance hop le; khong token trong command/log; Google Drive URLs chi con trong migration evidence.

**Log:** Ghi asset IDs, HF paths, hashes, delivery class va missing/deferred items.

### P10.T02 - Supabase Edge Function cap HF download ticket

**Muc tieu:** User da dang nhap tai duoc private HF artifact ma khong biet HF token hoac server secret.

**Pham vi file:** `supabase/functions/asset-ticket/**`, `supabase/functions/asset-download/**`, ticket migration/RLS, HF streaming client, Storage mirror publisher va function tests.

**Thuc hien:** Verify Supabase JWT; allow-list repo/path/version/hash tu server manifest; cap opaque one-time ticket co user/artifact/expiry; forward `HEAD`/`GET`/`Range`, ETag va cancellation; rate limit theo user/device; redacted structured logs; mirror artifact vuot Edge budget vao private Storage va cap signed URL ngan han.

**Dieu kien thong qua:** Anonymous, expired/replayed ticket, path traversal, arbitrary repo/path va user khac deu bi chan; partial range/hash dung; proxy khong buffer file; artifact lon dung Storage signed URL; rotate `HF_READ_TOKEN` khong rebuild app.

**Log:** Ghi function deployment version, ticket/range/replay matrix, latency/size budget, Storage mirror hash va secret scan; khong ghi JWT/ticket/signed URL.

### P10.T03 - App catalog/downloader migrate khoi Drive

**Muc tieu:** App chi doc manifest va Supabase delivery contract, sau do verify artifact truoc install.

**Pham vi file:** App asset catalog repository/cache/downloader/installer, Supabase Functions/Storage adapters, old Drive catalog references va integration tests.

**Thuc hien:** Catalog repository/cache; refresh ticket khi het han; resumable Range; ETag/version/update; checksum/size/disk-space/atomic install; remove all package Drive URLs; khong cho WebService/Agent truy cap signed URL hoac session token.

**Dieu kien thong qua:** Fresh/resume/update/corrupt/offline/ticket-expired tests pass; signed URL het han dung han; `rg`/APK scan khong con Drive package URL, HF token, Supabase secret/service key.

**Log:** Ghi asset download matrix, cancellation/resume evidence va scans.

### P10.T04 - Supabase Auth email va Google

**Muc tieu:** Dang ky/dang nhap/logout/link/re-auth an toan bang Supabase Auth.

**Pham vi file:** Supabase Auth gateway/repository/use cases, account Contract/ViewModel/Screen, Android Credential Manager, OAuth callback va auth tests.

**Thuc hien:** Email/password, verification/reset; Google Credential Manager lay ID token/nonce va exchange voi Supabase; account collision/link policy; PKCE/deep-link `drducbook://auth/callback`; session refresh/revoke; Compose/MVI account UI.

**Dieu kien thong qua:** Happy/error/cancel/offline/process restart pass; callback chi mo app moi; logout/revoke xoa session local; email/Google cho cung identity khong tao duplicate ngoai policy; khong credential/token plaintext.

**Log:** Ghi auth scenarios, local Supabase/device evidence va redirect config; khong ghi token, email that hoac user data.

### P10.T05 - Supabase Postgres, RLS va private Storage foundation

**Muc tieu:** Moi user chi truy cap duoc metadata va snapshot/assets cua chinh minh.

**Pham vi file:** `supabase/migrations/**`, generated database contract, Postgrest/Storage gateways, sync settings UI, RLS/policy tests.

**Thuc hien:** Tao profiles/devices/sync_heads/snapshots/artifact_tickets schema; ownership bang `auth.uid()`; RLS SELECT/INSERT/UPDATE/DELETE; private buckets va object path `{user_id}/...`; signed upload/download TTL; quota, retention, delete-account va retry/backoff policies.

**Dieu kien thong qua:** RLS bat tren 100% exposed tables; user A khong doc/ghi/list object user B; anonymous bi chan; service-role khong co trong client; disable sync khong xoa local data; delete-account cleanup co audit evidence.

**Log:** Ghi migration version, RLS matrix, bucket policies, quota/retention va local Supabase test results.

### P10.T06 - Google Drive appDataFolder backup transport

**Muc tieu:** Giu sao luu/dong bo Google Drive theo least privilege ma khong tron token Drive voi Supabase login.

**Pham vi file:** Google AuthorizationClient/Drive API gateway, encrypted token/session storage, `appDataFolder` snapshot index/client, sync target settings va consent/revoke tests.

**Thuc hien:** Chi xin `https://www.googleapis.com/auth/drive.appdata` khi user bat Drive target; cho phep email account link Google Drive; hien ro Supabase identity va Drive identity; luu immutable snapshot object + versioned `head.json`; retry/backoff/resumable upload; revoke/re-consent va unlink khong xoa local data.

**Dieu kien thong qua:** Login Supabase khong tu hien Drive consent; app khong co broad Drive scope; app cu khong doc/ghi DrDucBook namespace; offline/revoke/account-mismatch/partial upload pass; token khong nam trong Room plaintext/log/backup.

**Log:** Ghi OAuth client logical ID, scopes, consent/revoke/account-mismatch matrix va Drive file IDs da hash; khong ghi access/refresh token hoac user email.

### P10.T07 - Snapshot schema, multi-target conflict va restore

**Muc tieu:** Backup day du data da chot va khong auto-merge conflict.

**Pham vi file:** Snapshot manifest/builder/uploader/downloader/restorer, Postgres/private Storage va Google Drive adapters, conflict UI/use cases, subsystem backup adapters va tests.

**Thuc hien:** Manifest schema/revision/device/checksum/target heads; include sources/progress/projects/Agent/manual bookmarks/appearance/Web policy/health summary; redact/exclude source credential fields va exclude cookie/session/cache/models/media; optimistic compare-and-set cho Supabase head va Drive `head.json`; target modes `SUPABASE`, `GOOGLE_DRIVE`, `BOTH`; transactional restore.

**Dieu kien thong qua:** Local-only/cloud-only/both-changed/targets-diverged/invalid/corrupt/interrupted/concurrent-device scenarios pass; user chon local/target/cloud-copy; khong auto-merge; hash verify truoc commit; RLS/Storage ownership va Drive namespace van dung sau restore.

**Log:** Ghi snapshot schema, size/hash, conflict matrix va restore evidence; khong ghi noi dung snapshot.

### P10.T08 - Cloud security va integration tests

**Muc tieu:** Dong gate Functions, Auth, RLS, downloader, Supabase sync va Drive backup.

**Pham vi file:** Supabase local-stack/Edge Function/Auth/RLS/Storage, Google Drive authorization/transport, download security va integration suites, APK/log/secret scan reports.

**Thuc hien:** Chay local Supabase migrations/functions; test JWT expiry/revoke, ticket replay/path tampering/rate limits, RLS cross-user, signed URL expiry, Range/hash, Storage mirror, Drive scope/revoke/account mismatch/resumable upload, multi-target divergence, offline/concurrent conflict, account deletion va secret rotation; scan source/APK/log.

**Dieu kien thong qua:** Toan bo test matrix pass; client khong the bypass RLS/allow-list hoac truy cap Drive ngoai appDataFolder; HF token, Supabase server secrets va Drive tokens khong co trong source/APK/log/backup; operational rollback duoc dien tap.

**Log:** Ghi report paths, migration/function version va remaining operational risks.

## Tai lieu chuan

- Supabase Kotlin: https://supabase.com/docs/reference/kotlin/installing
- Supabase Auth Google: https://supabase.com/docs/guides/auth/social-login/auth-google?platform=android
- Edge Function secrets va auth: https://supabase.com/docs/guides/functions/secrets va https://supabase.com/docs/guides/functions/auth
- Private Storage, RLS va signed URLs: https://supabase.com/docs/guides/storage/buckets/fundamentals
- Google Drive appDataFolder: https://developers.google.com/workspace/drive/api/guides/appdata

## Gate dong phase

- App khong chua HF token, Supabase secret/service key, Drive token hoac package Google Drive URL.
- Supabase Auth callback, session lifecycle, RLS, private Storage va delete-account pass test tren hai user tach biet.
- Drive chi xin `drive.appdata` khi user bat; revoke/unlink khong mat local data; Drive account khac Supabase identity phai duoc xac nhan ro.
- Snapshot conflict/restore tren `SUPABASE`, `GOOGLE_DRIVE`, `BOTH` co integration evidence; cookie/session/cache/model/media khong vao snapshot.
- Edge Function/Storage delivery dap ung Range, hash, expiry, rate limit va kich thuoc thuc te cua catalog.
