# P10.T10 — Thực thi khắc phục tải gói, Supabase, NMT và đồng bộ

Ngày kiểm tra: 04/08/2026  
Trạng thái: `IN_PROGRESS - RUNTIME DEPLOYED, AUTHENTICATED SMOKE PENDING`

## Đã thực hiện trong code

- Session Supabase chờ `awaitInitialization()` và chỉ phát trạng thái xác thực thật sự; trạng thái khởi tạo/refresh lỗi tạm thời không còn làm UI tự xóa tài khoản.
- Account ViewModel tải access và quota độc lập; lỗi quota không xóa quyền đã tải trước đó.
- Account screen hiển thị lý do khi quyền sao lưu chưa tải, thay vì để nút disabled không giải thích.
- Asset downloader hiển thị phần thân lỗi HTTP giới hạn kích thước; UI phân loại lỗi Edge Function/migration chưa deploy.
- NMT kiểm tra bộ nhớ khả dụng trước khi load, dùng cấu hình tối ưu hóa/thực thi tiết kiệm cho thiết bị RAM thấp, và đóng các ONNX session đã mở nếu session kế tiếp thất bại.
- Tạo script promotion admin an toàn, idempotent theo email:
  `supabase/scripts/promote-drducqy95-admin.sql`

## Kiểm tra cục bộ

```text
Gradle :app:compileAppDebugKotlin: PASS
Gradle :app:testAppDebugUnitTest: PASS
Gradle :app:assembleAppDebug: PASS
Node migration/asset tests: 12/12 PASS
Android unit suite: BUILD SUCCESSFUL (8m 31s)
```

APK debug x86_64 đã được tạo. Cài đè lên emulator hiện tại bị từ chối vì APK đang có chữ ký khác bản `com.drducbook.app.debug` đã cài (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`); không gỡ app để tránh xóa dữ liệu người dùng trên thiết bị.

APK debug mới:

- x86_64: `app/build/outputs/apk/app/debug/app-app-x86_64-debug.apk`, SHA-256
  `0EC6C620191EFFAD2A70DEEF6AFF916D274E33B210457A0BA8F9EDFD86928DC5`.
- Universal: `app/build/outputs/apk/app/debug/app-app-universal-debug.apk`, SHA-256
  `7AAD5640FF99F1FB2420B87023126A926E8A589AA2F108860A20AB087DBBED1D`.

## Runtime Supabase đã triển khai

- `npx supabase db push` đã áp dụng đủ bốn migration cloud/account.
- SQL kiểm tra linked project trả về đủ sáu bảng: `account_access`, `profiles`,
  `artifact_tickets`, `sync_snapshots`, `sync_heads`, `sync_events`.
- `asset-ticket` đang ACTIVE với `verify_jwt=true`; `asset-download` đang ACTIVE với
  `verify_jwt=false` và tự kiểm tra opaque ticket.
- `ASSET_TICKET_SECRET` và `HF_READ_TOKEN` đã được cấu hình server-side; các biến
  `SUPABASE_*` được runtime inject tự động.
- Gọi không có access token hiện trả HTTP 401 cho ticket/download, thay vì 404
  `Requested function was not found`.
- REST `account_access` hiện trả 401 khi thiếu token, thay vì PGRST205; đây là bằng
  chứng endpoint đã thấy bảng sau khi refresh schema.
- Storage linked project đã có hai bucket private `drducbook-snapshots` và
  `drducbook-user-assets`.
- Script `supabase/scripts/promote-drducqy95-admin.sql` đã chạy thành công và truy vấn
  xác minh trả đúng một tài khoản có role `admin` cùng đủ quyền quản trị.

## Runtime còn chờ

Các gate cần chạy bằng phiên đăng nhập thật trong APK mới:

- Cấp ticket bằng access token thật và tải ít nhất một gói TTS/NMT, xác minh SHA-256.
- Bấm sao lưu/khôi phục Supabase và Google Drive trong app, xác minh có phản hồi và dữ liệu
  thay đổi sau restore.
- Dịch NMT một đoạn ngắn trên debug/noR8/release, theo dõi logcat và xác nhận process
  không tự thoát.
- Rotate/revoke legacy `service_role` key nếu key đã từng xuất hiện trong terminal/log.

## Bước triển khai máy chủ bắt buộc

1. **Đã xong:** Áp dụng bốn migration cloud/account theo thứ tự thời gian.
2. **Đã xong:** Deploy `asset-ticket` và `asset-download`.
3. **Đã xong:** Cấu hình secrets server-side theo checklist release.
4. **Đã xong:** Chạy `supabase/scripts/promote-drducqy95-admin.sql` và xác minh role/permissions.
5. **Còn chờ:** Smoke test có access token thật cho REST, ticket, download, account access, Supabase backup và Google Drive.
6. **Còn chờ:** Tái hiện NMT sau khi model đã tải thành công; thu logcat/tombstone để đóng gate crash.

## Điều kiện đóng task

Task chỉ được chuyển `DONE` khi runtime không còn 404/PGRST205, tài khoản vẫn tồn tại sau process recreation, tải asset kiểm tra được SHA-256, backup/restore có phản hồi thực tế, admin hiển thị đúng quyền và NMT chạy thử không làm process chết.
