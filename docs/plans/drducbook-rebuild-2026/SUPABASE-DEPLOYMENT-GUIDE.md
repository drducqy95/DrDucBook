# Hướng dẫn triển khai Supabase cho DrDucBook

Tài liệu này dành cho project:

```text
Project ref: faegbafmkpsocoecrhvz
Supabase URL: https://faegbafmkpsocoecrhvz.supabase.co
Thư mục chạy lệnh: D:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main
```

Quy trình sử dụng Supabase CLI dưới đây tuân theo tài liệu CLI chính thức: cài CLI dạng dependency với Node.js 20+, đăng nhập, link project, `db push`, quản lý secrets và deploy functions. Tham khảo [Supabase CLI getting started](https://supabase.com/docs/guides/local-development/cli/getting-started), [db push/link](https://supabase.com/docs/reference/cli/supabase-db-push) và [functions deploy/secrets](https://supabase.com/docs/reference/cli/supabase-login).

## 0. Nguyên tắc bảo mật

- Publishable key có thể nằm trong Android build config.
- Database password, Supabase access token, `SUPABASE_SERVICE_ROLE_KEY`, `HF_READ_TOKEN` và `ASSET_TICKET_SECRET` chỉ được nhập vào CLI/Dashboard hoặc secret manager.
- Không commit `.env`, không chụp màn hình secret và không gửi service-role key vào chat.
- Không chạy `db reset --linked` trên project production. Migration đã áp dụng phải được thay đổi bằng migration mới, không sửa lịch sử cũ.

## 1. Chuẩn bị máy Windows

Mở PowerShell và kiểm tra Node.js:

```powershell
node --version
npm --version
```

Node.js phải là phiên bản 20 trở lên. Tại thư mục gốc của repository, cài Supabase CLI dạng dependency của project:

```powershell
Set-Location 'D:\Downloads\Archives\legado-with-MD3-main\legado-with-MD3-main'
npm install --save-dev supabase
npx supabase --version
```

Cách này không cần cài lệnh `supabase` toàn hệ thống; tất cả lệnh phía dưới dùng `npx supabase`.

## 2. Đăng nhập và link đúng project

Tạo Personal Access Token trong Supabase Dashboard → Account → Access Tokens. Chỉ nhập token khi CLI hỏi, không lưu vào repository.

```powershell
npx supabase login
npx supabase link --project-ref faegbafmkpsocoecrhvz
```

### Nếu gặp `Access token not provided`

Đây là Personal Access Token của tài khoản Supabase, khác với database password và khác với publishable key của Android.

1. Mở [Supabase Account Tokens](https://supabase.com/dashboard/account/tokens).
2. Chọn `Generate new token`, đặt tên dễ nhận biết, ví dụ `drducbook-windows-cli`.
3. Sao chép token ngay khi được tạo; token không được gửi vào chat hoặc commit vào repository.
4. Tại PowerShell chạy:

```powershell
npx supabase login
```

Khi CLI hỏi `Enter your access token`, dán token rồi nhấn Enter. Có thể tắt telemetry nếu muốn:

```powershell
npx supabase telemetry disable
```

Sau khi login thành công, chạy lại:

```powershell
npx supabase link --project-ref faegbafmkpsocoecrhvz
```

Lúc này CLI mới hỏi database password. Nếu vẫn báo không có quyền, token đang thuộc tài khoản không có quyền vào organization/project `faegbafmkpsocoecrhvz`; hãy đăng nhập đúng tài khoản hoặc nhờ owner mời tài khoản vào organization.

Khi `link` hỏi database password, nhập database password của project. Không dùng publishable key ở bước này. Kiểm tra project đã link:

```powershell
npx supabase status
npx supabase projects list
```

Nếu CLI yêu cầu xác nhận project, phải chọn đúng `faegbafmkpsocoecrhvz`; không link nhầm project local `drducbook-local` trong `supabase/config.toml`.

## 3. Kiểm tra migration trước khi áp dụng

Các migration cần được áp dụng theo thứ tự timestamp:

```text
20260731044500_artifact_tickets.sql
20260731060000_cloud_sync_foundation.sql
20260803090000_account_access_control.sql
20260803120000_web_service_entitlement.sql
```

Xem trước thay đổi:

```powershell
npx supabase db push --dry-run
```

Nếu danh sách có migration ngoài bốn nhóm trên, dừng lại và kiểm tra `supabase/migrations` trước khi tiếp tục. Khi đã xác nhận đúng project và đúng danh sách:

```powershell
npx supabase db push
```

Sau khi chạy xong, vào Dashboard → SQL Editor và kiểm tra:

```sql
select to_regclass('public.account_access') as account_access,
       to_regclass('public.profiles') as profiles,
       to_regclass('public.artifact_tickets') as artifact_tickets,
       to_regclass('public.sync_snapshots') as sync_snapshots,
       to_regclass('public.sync_heads') as sync_heads,
       to_regclass('public.sync_events') as sync_events;

select version
from supabase_migrations.schema_migrations
order by version desc;
```

Tất cả bảng phải trả về tên bảng, không được `NULL`. Nếu PostgREST còn giữ schema cache cũ, chờ một lát rồi refresh Dashboard; không tạo bảng thủ công trùng tên.

## 4. Cấu hình secret cho Edge Functions

Hai function sử dụng các biến server-side:

```text
ASSET_TICKET_SECRET
HF_READ_TOKEN
```

`SUPABASE_URL` và `SUPABASE_SERVICE_ROLE_KEY` là biến hệ thống được Supabase tự động
inject vào Edge Functions. CLI sẽ bỏ qua tên bắt đầu bằng `SUPABASE_`, vì vậy không
cần và không được đặt lại hai biến này bằng `supabase secrets set`.

Tạo secret ticket ngẫu nhiên ngay trong PowerShell, không ghi ra file:

```powershell
$randomBytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($randomBytes)
$assetTicketSecret = [Convert]::ToBase64String($randomBytes)
```

Đặt secrets bằng CLI. Thay các placeholder trong bộ nhớ lệnh bằng giá trị thật; không commit lệnh có giá trị thật:

```powershell
npx supabase secrets set `
  "ASSET_TICKET_SECRET=$assetTicketSecret" `
  "HF_READ_TOKEN=<HUGGINGFACE_READ_TOKEN>" `
  --project-ref faegbafmkpsocoecrhvz
```

Kiểm tra tên secret, không in giá trị:

```powershell
npx supabase secrets list --project-ref faegbafmkpsocoecrhvz
```

Nếu muốn thao tác trong Dashboard: Project Settings → Edge Functions → Secrets → thêm
`ASSET_TICKET_SECRET` và `HF_READ_TOKEN`. Không lưu service-role key vào repository,
APK hoặc secret tùy biến của function; runtime đã cấp biến hệ thống tương ứng.

Nếu đã từng hiển thị legacy `service_role` key trong terminal/log, hãy vào Project
Settings → API Keys để rotate/revoke key cũ ngay sau khi triển khai. Không gửi key đó
qua chat và không dùng lại trong ứng dụng Android.

## 5. Deploy Edge Functions

`asset-ticket` yêu cầu JWT. `asset-download` dùng opaque ticket nên không yêu cầu JWT gateway; function tự kiểm tra ticket.

```powershell
npx supabase functions deploy asset-ticket `
  --project-ref faegbafmkpsocoecrhvz `
  --use-api

npx supabase functions deploy asset-download `
  --project-ref faegbafmkpsocoecrhvz `
  --no-verify-jwt `
  --use-api
```

Kiểm tra function đã tồn tại:

```powershell
npx supabase functions list --project-ref faegbafmkpsocoecrhvz
```

Phải thấy cả `asset-ticket` và `asset-download`. Nếu endpoint vẫn trả `Requested function was not found`, function đang deploy nhầm project hoặc deploy chưa hoàn tất.

## 6. Cập nhật `drducqy95@gmail.com` thành admin

Chỉ chạy sau khi migration `account_access` đã thành công và tài khoản Google đã đăng nhập ít nhất một lần.

Mở SQL Editor, mở file:

```text
supabase/scripts/promote-drducqy95-admin.sql
```

Chạy toàn bộ file. Script sẽ:

1. Tìm đúng user trong `auth.users` theo email không phân biệt hoa thường.
2. Cập nhật đúng một dòng `public.account_access`.
3. Gán role `admin` và toàn bộ entitlement.
4. Dừng bằng lỗi nếu không tìm thấy user hoặc số dòng cập nhật khác một.

Kiểm tra kết quả:

```sql
select users.id,
       users.email,
       access.role,
       access.permissions,
       access.updated_at
from auth.users users
join public.account_access access on access.user_id = users.id
where lower(users.email) = lower('drducqy95@gmail.com');
```

Nếu không có dòng kết quả, hãy đăng nhập Google bằng email đó trong app, chờ trigger tạo `account_access`, rồi chạy lại script. Không sửa role bằng REST từ Android và không cấp service-role key cho app.

## 7. Kiểm tra Google Auth và redirect

Trong Supabase Dashboard → Authentication → URL Configuration:

```text
Redirect URL: drducbook://auth/callback
```

Trong Google Cloud Console → OAuth client, thêm callback web của Supabase:

```text
https://faegbafmkpsocoecrhvz.supabase.co/auth/v1/callback
```

Trong Supabase Dashboard → Authentication → Providers → Google:

- bật Google;
- nhập Client ID tương ứng với Android/Web flow đang dùng;
- nhập Client Secret chỉ ở Dashboard nếu provider yêu cầu OAuth web;
- lưu và thử đăng nhập trong app.

Sau đăng nhập, app phải hiển thị email, provider `google`, access/quota và không tự mất session khi rời màn Account.

## 8. Smoke test runtime

### 8.1. Kiểm tra bảng bằng access token người dùng

Lấy access token từ phiên đăng nhập của app hoặc công cụ kiểm thử nội bộ; không ghi token vào log.

```powershell
$projectUrl = 'https://faegbafmkpsocoecrhvz.supabase.co'
$publishableKey = '<PUBLISHABLE_KEY>'
$accessToken = '<USER_ACCESS_TOKEN>'
$headers = @{
  apikey = $publishableKey
  Authorization = "Bearer $accessToken"
}

Invoke-RestMethod `
  -Uri "$projectUrl/rest/v1/account_access?select=user_id,email,role,permissions&limit=1" `
  -Headers $headers
```

Kết quả phải là một mảng JSON có `role` và `permissions`, không phải `PGRST205`.

### 8.2. Cấp ticket asset

```powershell
$ticketBody = '{"artifactId":"translation-quick-clean"}'

$ticketResponse = Invoke-RestMethod `
  -Method Post `
  -Uri "$projectUrl/functions/v1/asset-ticket" `
  -Headers $headers `
  -ContentType 'application/json' `
  -Body $ticketBody

$ticketResponse
```

Kết quả phải có `ticket` và không được trả 404. Không copy ticket vào log công khai.

### 8.3. Tải artifact

Trong app, mở catalog asset và tải một gói TTS cùng một gói NMT. Thành công chỉ được công nhận khi:

- download hoàn tất;
- kích thước khớp manifest;
- SHA-256 được app kiểm tra;
- file được import/mở thành công;
- retry không tạo file `.part` hỏng còn lại.

### 8.4. Sao lưu và phân quyền

Trong app:

1. Đăng nhập bằng free, premium và admin.
2. Xác nhận free không thấy quản lý tài khoản và web service bị khóa.
3. Admin mở được quản lý tài khoản và thấy `drducqy95@gmail.com` là admin.
4. Bấm sao lưu Supabase và khôi phục.
5. Cấp Google Drive rồi bấm sao lưu/khôi phục.
6. Rời màn Account, mở lại và khởi động lại app; session vẫn phải còn.

## 9. Kiểm tra NMT sau khi asset runtime hoạt động

1. Cài gói NMT thành công.
2. Dịch một đoạn ngắn trên debug.
3. Dịch cùng đoạn trên release/noR8.
4. Theo dõi `adb logcat` với các từ khóa `FATAL EXCEPTION`, `SIGABRT`, `SIGSEGV`, `OutOfMemoryError`.
5. Nếu process vẫn chết, lưu tombstone và `ApplicationExitInfo` trước khi sửa tiếp; không kết luận là lỗi R8 chỉ dựa vào việc app tự thoát.

## 10. Xử lý lỗi thường gặp

| Lỗi | Cách xử lý |
|---|---|
| `PGRST205 account_access` | Kiểm tra `npx supabase link`, chạy `npx supabase db push`, sau đó refresh schema/cache. |
| `Requested function was not found` | Deploy lại đúng project ref và kiểm tra `npx supabase functions list`. |
| Ticket trả 401 | Access token hết hạn/thiếu header; đăng nhập lại app và thử lại. |
| Ticket trả 403 | Artifact không có trong manifest hoặc user không đủ entitlement. |
| Download trả 401/404 từ HF | Kiểm tra `HF_READ_TOKEN`, dataset private và artifact path trong manifest. |
| Admin script không tìm thấy user | Đăng nhập Google một lần bằng email đích, rồi chạy lại script. |
| Google redirect lỗi | Kiểm tra cả Supabase redirect URL và Google OAuth callback URL. |
| Cài APK debug báo sai chữ ký | Dùng emulator sạch hoặc gỡ bản test cũ sau khi sao lưu dữ liệu; không gỡ app người dùng thật. |

## 11. Checklist đóng triển khai

- [ ] `npx supabase db push` hoàn tất.
- [ ] Sáu bảng cloud/account trả về từ SQL/REST.
- [ ] Secrets tồn tại nhưng không bị in giá trị.
- [ ] Hai Edge Function hiển thị trong Functions list.
- [ ] Asset ticket/download smoke test không còn 404.
- [ ] `drducqy95@gmail.com` có role `admin`.
- [ ] Google login và redirect hoạt động.
- [ ] Session tồn tại sau khi rời Account/process recreation.
- [ ] Supabase backup/restore và Google Drive backup/restore hoạt động.
- [ ] NMT chạy release/debug/noR8 mà không làm app tự thoát.
