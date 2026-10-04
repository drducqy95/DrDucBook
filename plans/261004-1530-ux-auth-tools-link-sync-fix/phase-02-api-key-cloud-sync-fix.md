# Phase 02: Supabase API Key Cloud Sync Fix

Status: ⬜ Pending
Dependencies: None

## Objective
Khắc phục triệt để lỗi khi đồng bộ API key lên đám mây Supabase:
`Supabase trả về lỗi HTTP 400: {"statusCode":"404","error":"not_found","message":"Object not found","code":"NoSuchKey"}` (hiển thị trong màn hình Cài đặt AI của người dùng).

## Root Cause Analysis
- Trong `ApiKeySyncRepository.kt` dòng 119-123:
  ```kotlin
  rest.putFile(
      path = "storage/v1/object/${CloudSyncClientContract.USER_ASSET_BUCKET}/$objectPath",
      source = tempFile,
      upsert = true,
  )
  ```
- Phương thức `rest.putFile` sử dụng HTTP `PUT`. Trong tài liệu Supabase Storage API:
  - `PUT /storage/v1/object/{bucket}/{wildcard}` là endpoint dành cho việc **UPDATE** (cập nhật tệp đã tồn tại). Nếu tệp chưa từng tồn tại trên bucket của người dùng, Supabase trả về mã lỗi HTTP 400 / 404 với payload `{"statusCode":"404","error":"not_found","message":"Object not found","code":"NoSuchKey"}`.
  - Endpoint chuẩn để **tạo mới hoặc ghi đè (upsert)** tệp trong Supabase Storage API là **`POST /storage/v1/object/{bucket}/{wildcard}`** kèm theo header `x-upsert: true`.

## Requirements

### Functional
- [ ] Bổ sung hàm `postFile(path, source, upsert = true)` trong `SupabaseAuthenticatedRestClient.kt` sử dụng phương thức HTTP `POST` kèm header `x-upsert: true`.
- [ ] Cập nhật `ApiKeySyncRepository.kt`: chuyển lệnh đẩy file lên đám mây từ `putFile` sang `postFile(..., upsert = true)`.
- [ ] Cập nhật `SupabaseAccountCloudBackupRepository.kt`: cập nhật dòng 98 từ `putFile` sang `postFile` với `upsert = true` để đồng bộ và ngăn chặn lỗi NoSuchKey khi tạo bản sao lưu đầu tiên.
- [ ] Bổ sung xử lý lỗi chi tiết và thông điệp tiếng Việt thân thiện khi bucket hoặc token gặp sự cố.
- [ ] Đảm bảo cơ chế tải về (`pullFromCloud`) kiểm tra đúng trường hợp tệp tồn tại hoặc chưa có sao lưu.

### Non-Functional
- [ ] Đảm bảo tính toàn vẹn của mã hóa End-to-End (AES-GCM 256-bit + HKDF) không bị ảnh hưởng.
- [ ] Unit tests cho `ApiKeySyncRepository` bao phủ cả trường hợp tạo mới lần đầu và cập nhật đè.

## Implementation Steps
1. [ ] Cập nhật `SupabaseAuthenticatedRestClient.kt`:
   - Định nghĩa `suspend fun postFile(path: String, source: File, upsert: Boolean = false)`:
     ```kotlin
     val builder = Request.Builder()
         .url(url(path))
         .header("x-upsert", upsert.toString())
         .post(source.asRequestBody(OCTET_STREAM_MEDIA_TYPE))
     execute(builder)
     ```
2. [ ] Cập nhật `ApiKeySyncRepository.kt`:
   - Đổi `rest.putFile` thành `rest.postFile(..., upsert = true)`.
3. [ ] Cập nhật `SupabaseAccountCloudBackupRepository.kt`:
   - Đổi `rest.putFile` thành `rest.postFile(..., upsert = true)`.
4. [ ] Cập nhật unit test `ApiKeySyncRepositoryTest.kt` để xác minh lệnh upload gọi `postFile` với `x-upsert: true`.

## Files to Create/Modify
- `app/src/main/java/io/legado/app/data/repository/SupabaseAuthenticatedRestClient.kt`
- `app/src/main/java/io/legado/app/data/repository/ApiKeySyncRepository.kt`
- `app/src/main/java/io/legado/app/data/repository/SupabaseAccountCloudBackupRepository.kt`
- `app/src/test/java/io/legado/app/data/repository/ApiKeySyncRepositoryTest.kt`

## Test Criteria
- [ ] Chạy unit test `ApiKeySyncRepositoryTest` kiểm tra upload thành công với `postFile`.
- [ ] Thao tác trực tiếp trên app: Bấm "Đồng bộ lên đám mây", trả về kết quả thành công và hiển thị timestamp đồng bộ thay vì lỗi HTTP 400 NoSuchKey.

---
Next Phase: [Phase 03 - Antigravity 403 Validation Handler](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/plans/261004-1530-ux-auth-tools-link-sync-fix/phase-03-antigravity-403-validation-fix.md)
