# Kế hoạch khắc phục tải gói, Supabase, NMT và sao lưu đồng bộ

Ngày lập: 04/08/2026  
Phạm vi: DrDucBook Android, Supabase project đang cấu hình trong bản release  
Trạng thái: `IN_PROGRESS - RUNTIME ĐÃ TRIỂN KHAI, CHỜ SMOKE TEST XÁC THỰC`

## 1. Mục tiêu

Khôi phục bốn luồng đang lỗi trong bản release:

1. Tải các gói asset (TTS/NMT và các gói private) thành công.
2. Loại bỏ lỗi Supabase `PGRST205` do thiếu bảng `account_access` và các bảng cloud.
3. Ngăn app tự thoát khi chạy NMT, đồng thời hiển thị lỗi có thể xử lý.
4. Làm cho sao lưu/khôi phục và đồng bộ có phản hồi rõ ràng, thực hiện đúng quyền tài khoản.
5. Cập nhật tài khoản `drducqy95@gmail.com` thành `admin` sau khi schema đã được triển khai.
6. Giữ session đăng nhập qua việc rời màn Account, tái tạo màn hình và khởi động lại process.

## 2. Bằng chứng và nguyên nhân

### 2.1. Tải gói — đã xác nhận

`AssetDeliveryRepository` lấy ticket trước khi tải artifact. Hai endpoint được tạo bởi:

- `app/src/main/java/io/legado/app/data/repository/AssetDeliveryRepository.kt:68`
- `app/src/main/java/com/drducbook/app/cloud/AssetDeliveryClientContract.kt:20`
- `supabase/functions/asset-ticket/index.ts`
- `supabase/functions/asset-download/index.ts`

Trước khi triển khai, gọi trực tiếp project Supabase trả về:

```text
HTTP 404
{"code":"NOT_FOUND","message":"Requested function was not found"}
```

Vì vậy lỗi `Asset ticket request failed (404)` là do Edge Function chưa được deploy, không phải do ZIP, checksum, thiết bị hay mạng. Các artifact/manifest cục bộ vẫn hợp lệ. Sau khi deploy, gọi không có token đã chuyển sang HTTP 401; vẫn cần một access token thật để kiểm chứng ticket và ZIP hợp lệ.

### 2.2. Supabase — đã xác nhận

REST API trước triển khai trả `PGRST205` cho các bảng quyền/cloud. Mã ứng dụng đã có repository và migration, nhưng migration chưa được áp dụng vào project. Sau `db push`, SQL linked project xác nhận đủ bảng quyền, ticket và đồng bộ; REST không có token trả 401 thay vì PGRST205.

Các migration liên quan:

- `supabase/migrations/20260731060000_cloud_sync_foundation.sql`
- `supabase/migrations/20260731044500_artifact_tickets.sql`
- `supabase/migrations/20260803090000_account_access_control.sql`
- `supabase/migrations/20260803120000_web_service_entitlement.sql`
- `supabase/migrations/20260804090000_account_access_bootstrap_and_schema_reload.sql`

`SupabaseAccountAccessRepository` hiện ném lỗi ngay khi bảng không tồn tại nên không thể dùng quyền mặc định FREE làm fallback.

### 2.3. Sao lưu/đồng bộ — nguyên nhân trực tiếp và khoảng trống tích hợp

Trong `AccountScreen.kt`, nút Supabase và Google Drive chỉ bật khi `state.access != null`. Do truy vấn `account_access` lỗi, nút bị disable và không phát sinh Intent, tạo cảm giác bấm không có hành động.

`AccountViewModel.kt` còn tải access và quota trong cùng một khối; một lỗi quota có thể làm mất cả trạng thái access trên UI. Ngoài ra, planner/conflict model đã có nhưng chưa được nối thành một orchestration đồng bộ đầy đủ từ snapshot → upload → head/event → restore/conflict.

### 2.4. Session bị mất trên UI — nguyên nhân rất có khả năng

Session key vẫn tồn tại trong SharedPreferences của app, nên thông tin đăng nhập đã được lưu vật lý. Vấn đề là:

- `SupabaseAccountAuthRepository.observeSession()` chuyển `Initializing`, `RefreshFailure` và `NotAuthenticated` đều thành session `null`.
- `AccountViewModel` xóa `activeSession` và `activeAccess` ở mọi lần nhận `null`.

Refresh tạm thời lỗi hoặc ViewModel được tạo lại vì rời màn hình khiến UI hiển thị như đăng xuất, dù session chưa bị xóa. Chỉ `NotAuthenticated` chắc chắn hoặc hành động đăng xuất của người dùng mới được phép xóa session hiển thị.

### 2.5. NMT crash — chưa đủ dữ liệu để kết luận tuyệt đối

Thiết bị kiểm tra hiện không có model NMT trong thư mục private của app và không có crash log/tombstone tương ứng. Tuy nhiên, `HachimiOnnxTranslator` mở nhiều ONNX session lớn (encoder, decoder, tokenizer, detokenizer) trong heap growth limit thấp khoảng 192 MB, chưa có preflight bộ nhớ hoặc staged cleanup.

Đây là giả thuyết ưu tiên: native ONNX/OOM trong lúc khởi tạo session có thể làm process chết, trường hợp này Kotlin `try/catch` không bắt được. Cần tái hiện sau khi endpoint tải gói hoạt động để phân biệt native memory, model hỏng, ABI và R8.

## 3. Trình tự triển khai

### P0 — Khôi phục Supabase runtime

1. Chạy toàn bộ migration theo thứ tự thời gian.
2. Reload schema cache/PostgREST.
3. Deploy `asset-ticket` và `asset-download`.
4. Cấu hình `ASSET_TICKET_SECRET`, `HF_READ_TOKEN` ở Edge Function; `SUPABASE_URL` và service role được Supabase tự inject, không đưa service role vào APK.
5. Kiểm tra bucket Storage, RLS và quyền truy cập snapshot.
6. Smoke test endpoint: 404 không còn; request chưa xác thực trả 401; request hợp lệ trả ticket/ZIP.

**Gate P0:** Đã đạt phần triển khai/schema/chế độ xác thực; còn authenticated smoke để xác minh ticket, tải ZIP và checksum.

### P1 — Phân quyền và tài khoản admin

1. Xác nhận user Google `drducqy95@gmail.com` đã tồn tại trong `auth.users`.
2. Cập nhật đúng một bản ghi `public.account_access` thành role `admin`.
3. Cấp các entitlement: `cloud_backup`, `download_content`, `export_ebook`, `authoring_chapter`, `edit_ebook_chapter`, `web_service`, `manage_accounts`.
4. Refresh session và kiểm tra màn Account hiển thị admin.
5. Nếu user chưa tồn tại, yêu cầu đăng nhập Google một lần rồi chạy lại bước promotion.

**Gate P1:** Promotion server-side đã chạy và truy vấn xác minh role/permissions thành công; còn smoke test UI với phiên đăng nhập mới.

### P2 — Ổn định session và Account UI

1. Mô hình hóa các trạng thái `Initializing`, `Authenticated`, `RefreshFailedWithCachedSession`, `SignedOut`.
2. Không xóa session khi đang khởi tạo hoặc refresh lỗi tạm thời.
3. Đọc session đã lưu trước khi gọi refresh mạng.
4. Tách loading access và quota; lỗi quota không làm mất access.
5. Hiển thị lý do khi nút backup bị khóa.
6. Bổ sung test rời màn hình, xoay màn hình, process recreation, mất mạng và đăng xuất chủ động.

**Gate P2:** Code đã sửa observer/session và access/quota độc lập; còn xác minh rời màn hình/process recreation trên APK mới.

### P3 — Hoàn thiện sao lưu/đồng bộ

1. Nối Intent upload/restore Supabase và Google Drive vào trạng thái UI.
2. Hiển thị tiến trình, thành công, lỗi và retry.
3. Hoàn thiện snapshot/head/event orchestration và conflict handling.
4. Kiểm thử free, premium, admin, mất mạng, token hết hạn và Drive chưa cấp quyền.

**Gate P3:** Code contract hiện đã có trạng thái lỗi/phản hồi; còn chạy upload/restore Supabase và Google Drive bằng tài khoản thật để đóng gate hành vi.

### P4 — Ổn định NMT

1. Sau P0, tải model và tái hiện trên release/debug/noR8.
2. Thu thập logcat crash, tombstone, `ApplicationExitInfo` và mức RAM theo từng session.
3. Kiểm tra file đầy đủ, SHA-256 và ABI trước khi load.
4. Load session theo từng bước; đóng các session đã mở nếu bước sau thất bại.
5. Dùng cấu hình tiết kiệm RAM: ít thread, tối ưu hóa thấp hơn, giới hạn chunk/token.
6. Nếu native ONNX vẫn làm process chết, chuyển inference sang process riêng.
7. Thêm instrumentation smoke test trên arm64 và x86_64.

**Gate P4:** Đã thêm memory preflight, low-RAM profile và cleanup staged ONNX session; chưa thể đóng gate 10 chương vì thiết bị đang có APK ký khác và chưa cài được APK mới.

## 4. Kiểm thử tổng hợp

Các test contract/migration hiện có đã đạt `12/12`. Sau khi triển khai cần chạy thêm:

```powershell
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain
```

Smoke test thiết bị phải bao gồm:

- đăng nhập Google → rời Account → mở lại → khởi động lại app;
- tải một gói TTS và một gói NMT, kiểm tra SHA-256;
- upload/restore Supabase;
- cấp quyền và upload/restore Google Drive;
- chạy NMT trong release, debug và noR8;
- xác nhận free/premium/admin theo đúng entitlement.

## 5. Rủi ro và nguyên tắc an toàn

- Không dùng publishable key để cập nhật role admin; thao tác promotion phải ở SQL Editor/service role.
- Không ghi service role, OAuth token, cookie hoặc nội dung sách vào log/plan.
- Không coi lỗi NMT là đã giải quyết khi chưa có crash trace sau khi model tải thành công.
- Không đánh dấu task `DONE` nếu chỉ compile; phải có smoke test hành vi và bằng chứng runtime.

## 6. Trạng thái hiện tại

- Phân tích mã nguồn: hoàn tất.
- Kiểm tra runtime Supabase/Edge Function: đã deploy và endpoint unauthenticated trả 401 đúng thiết kế.
- Chỉnh sửa mã nguồn: hoàn tất các thay đổi session/access, asset error mapping và NMT memory safety; backup/Drive cần runtime smoke.
- Supabase runtime: bốn migration đã push; `asset-ticket`/`asset-download` ACTIVE; secrets server-side đã cấu hình; promotion admin đã xác minh.
- Test local: Node contract/migration/asset `12/12 PASS`; Android unit `BUILD SUCCESSFUL`.
- Build debug: `assembleAppDebug` `BUILD SUCCESSFUL`; APK x86_64 mới không thể cài đè emulator hiện tại do mismatch chữ ký, không uninstall để bảo toàn dữ liệu. SHA-256 x86_64: `0EC6C620191EFFAD2A70DEEF6AFF916D274E33B210457A0BA8F9EDFD86928DC5`; universal: `7AAD5640FF99F1FB2420B87023126A926E8A589AA2F108860A20AB087DBBED1D`.
- Còn mở: authenticated ticket/download + checksum, Supabase/Drive backup/restore, process recreation/session và NMT smoke trên APK mới; rotate legacy service-role key nếu key từng xuất hiện trong log.

## 7. Hướng dẫn triển khai Supabase

Quy trình copy/paste đầy đủ cho Windows, gồm cài CLI, link project, dry-run/db push, cấu hình secrets, deploy Edge Functions, promotion `drducqy95@gmail.com`, Google redirect và smoke test nằm tại:

[SUPABASE-DEPLOYMENT-GUIDE.md](./SUPABASE-DEPLOYMENT-GUIDE.md)

## 8. Bằng chứng triển khai ngày 04/08/2026

```text
npx supabase db push: PASS (5 migrations)
npx supabase functions deploy asset-ticket --use-api: PASS, ACTIVE, verify_jwt=true
npx supabase functions deploy asset-download --no-verify-jwt --use-api: PASS, ACTIVE, verify_jwt=false
npx supabase secrets list: PASS (ASSET_TICKET_SECRET, HF_READ_TOKEN; không ghi giá trị)
SQL linked schema check: PASS (6 bảng cloud/account)
Storage bucket check: PASS (2 bucket private)
Admin promotion script: PASS (role admin + đủ entitlement)
Unauthenticated ticket/download: HTTP 401 (không còn 404)
Node contract/migration/asset tests: 12/12 PASS
Android unit tests: BUILD SUCCESSFUL
Android debug assemble: BUILD SUCCESSFUL
```

### Tiêu chí đóng kế hoạch

Không chuyển kế hoạch sang `DONE` chỉ dựa trên compile/build. Chỉ đóng sau khi có bằng chứng
authenticated smoke trên APK mới: tải asset và kiểm tra SHA-256, backup/restore hai backend,
session còn sau process recreation, free/premium/admin đúng quyền và NMT không làm process
chết. Nếu chưa có access token hoặc thiết bị không cài được APK do chữ ký, giữ trạng thái
`IN_PROGRESS` và ghi rõ blocker trong report execution.

## 9. Google OAuth/Drive verification (04/08/2026)

### Đã kiểm tra trong mã nguồn

- Google Credential Manager dùng web/server client ID do Supabase cung cấp; nonce được băm SHA-256 trước khi gửi và token không được ghi log.
- Google Drive chỉ xin scope `drive.appdata`, dùng storage riêng của ứng dụng; không xin scope Drive toàn bộ.
- Đã bổ sung thông báo riêng cho `access_denied`, `DEVELOPER_ERROR`, HTTP 401/403 và Drive API chưa bật.
- Client ID local được đồng bộ theo client ID Google do chủ dự án cung cấp. Bí mật không đưa vào APK.

### Phần bắt buộc thao tác trên Google Cloud/Supabase

1. Trong Google Cloud → Google Auth Platform → OAuth consent screen → Testing, thêm `drducqy95@gmail.com` và các tài khoản kiểm thử vào **Test users**.
2. Bật Google Drive API cho cùng project; kiểm tra OAuth Android client có package `com.drducbook.app` và SHA-1 của bản cài đặt.
3. Trong Supabase Auth → Google, đặt Web client ID là client ID đã nhập trong `local.properties`, nhập Client Secret tương ứng; redirect URL phải là URL callback Supabase.
4. Nếu phát hành cho người dùng ngoài Test users, hoàn tất Branding/Verification của Google. Đây là bước xác minh ngoài mã app, không thể bỏ qua bằng thay đổi Kotlin.

### Gate Google

Giữ `IN_PROGRESS` cho đến khi chủ project thêm test user và smoke test đăng nhập Google + backup Drive trên APK debug/release. Thông báo 403 hiện đã hướng dẫn trực tiếp nguyên nhân và cách xử lý.

## 10. Execution continuation (2026-08-09)

The following implementation work is now present in the source tree:

- Model asset import now reports resolve/download/import failures, preserves cancellation, and exposes a retry action. Quick-dictionary archives import all supported entries in deterministic order and reject unsafe archive paths.
- Supabase sessions are encrypted with Android Keystore-backed AES-GCM storage. Authenticated REST calls refresh once on HTTP 401. Large cloud snapshots use chunked TUS uploads; snapshots are encrypted before hashing/uploading and legacy unencrypted archives remain readable for migration.
- Google Drive app-data backup and user-selected SAF folders share the encrypted snapshot format. SAF writes a partial file, finalizes it atomically where the provider permits, keeps a small retention window, and supports daily/weekly WorkManager scheduling with the password kept in the app Keystore.
- WebService startup and Ktor routes now require entitlement/pairing, reject unsafe file paths, avoid request-triggered foreground-service starts, and prevent concurrent mutable book state in image requests. The global unsafe TLS override was removed from the shared HTTP client.
- Export supports original/balanced/small image optimization. Large comic exports show a Send-to-Kindle recommendation; the completed export notification can open the Kindle email flow with a remembered receiving address. Unsupported Kindle formats such as CBZ are now excluded from that action and the activity rejects them with a clear EPUB/PDF guidance message.

Verification on the current workspace:

```text
:app:compileAppDebugKotlin       PASS
:app:testAppDebugUnitTest        PASS (1002 tests, 0 failures, 1 skipped)
modules/web: pnpm build           PASS
```

The local `:app:lintAppDebug` and `assembleAppNoR8` attempts exceeded the available 15-minute execution window while consuming several gigabytes of heap; they were stopped without a compiler error or a produced report/APK. They must be rerun on the CI/build machine before release sign-off. Authenticated Supabase/Google/SAF/Kindle smoke tests also remain external gates because they require the project credentials, OAuth console changes, and a real device/account. TUS upload currently resumes chunks within one process; a process-death resume session is not persisted and is a follow-up if offline/background uploads must survive process termination.

## 11. Supabase cloud configuration execution (2026-08-09)

Executed against project `faegbafmkpsocoecrhvz` using Supabase CLI 2.113.0:

```text
db push --dry-run --linked: PASS; remote database is up to date
Migrations present remotely: 5 (through 20260804090000)
Required public tables: account_access, profiles, artifact_tickets, sync_snapshots, sync_heads, sync_events
RLS: enabled on all required public tables
Storage buckets: drducbook-snapshots and drducbook-user-assets, both private, 512 MiB limit
Admin account drducqy95@gmail.com: role admin with all required entitlements
Google Auth provider: enabled (checked through /auth/v1/settings)
asset-ticket: deployed, ACTIVE, verify_jwt=true, version 2
asset-download: deployed, ACTIVE, verify_jwt=false, version 2
Unauthenticated asset-ticket and asset-download probes: HTTP 401
```

The repository now has the CLI as a development dependency (`supabase` 2.113.0) with a lockfile. No secret value was added to the repository. `supabase status` was not used for cloud validation because it inspects the optional local Docker stack; Docker is not running on this machine.

## 12. Google OAuth/Drive execution continuation (2026-08-09)

Executed in the user's authenticated Edge session for Google Cloud project `drducbook`:

```text
Google Drive API: enabled
OAuth publishing status: Testing
Test users: vankiepvodanh12@gmail.com, drducqy95@gmail.com
OAuth Data Access: https://www.googleapis.com/auth/drive.appdata saved
Android release client: com.drducbook.app + SHA-1 F9:8B:DF:44:74:83:AD:BE:D5:CA:1F:59:11:38:E0:24:1C:0C:DE:9E
Android debug client: com.drducbook.app.debug + the same SHA-1 (created 2026-08-09)
Supabase Web client: DrDucBook Supabase OAuth, callback /auth/v1/callback
Verification Center: verification not required while publishing status is Testing
```

The local `GOOGLE_AUTH_CLIENT_ID` was corrected to the Supabase Web client ID. This is the server/audience client ID required by Credential Manager and Supabase; the Android clients remain registered for package and certificate validation. No OAuth client secret or access token was written to the repository.

After this correction, `:app:compileAppDebugKotlin` passed, the focused `CloudBackupCryptoTest` passed, and `:app:assembleAppDebug` passed. The rebuilt APKs are under `app/build/outputs/apk/app/debug/`; install only after confirming the existing package has compatible signing, because uninstalling a differently signed old APK would erase its local data.

Production verification is intentionally not submitted: Google requires the homepage and privacy policy to be publicly hosted on a verified domain, with the same privacy URL linked from the homepage and OAuth consent screen. The legal drafts are now authored from the codebase in `docs/release/homepage.md`, `privacy-policy.md`, `terms-of-use.md`, and `support.md`; public hosting/domain ownership and final legal review remain. Publishing the app or submitting verification requires the project owner to review and confirm those details first. The release checklist is in [google-oauth-consent.md](../../release/google-oauth-consent.md).
