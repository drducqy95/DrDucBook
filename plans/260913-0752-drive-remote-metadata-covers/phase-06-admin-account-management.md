# Phase 06: Admin Account Management Enhancement (Registration Date, Last Sign-in, Filters & Sorting)

Status: ⬜ Pending
Dependencies: Phase 05

## Objective
Hoàn thiện tính năng Quản trị tài khoản (Admin Account Management) trong màn hình `AccountScreen`:
1. Bổ sung thông tin **Ngày đăng ký** (`created_at`) và **Lần đăng nhập gần nhất** (`last_sign_in_at`).
2. Bổ sung **Bộ lọc nâng cao** (Filter) kết hợp theo vai trò (Role) và trạng thái hoạt động (mới đăng nhập, lâu chưa hoạt động, v.v.).
3. Bổ sung tính năng **Sắp xếp** (Sort) theo:
   - Ngày đăng ký: Mới nhất $\rightarrow$ Cũ nhất (Mặc định) hoặc Cũ nhất $\rightarrow$ Mới nhất.
   - Lần đăng nhập gần nhất: Gần nhất $\rightarrow$ Lâu nhất.
   - Email / Tên tài khoản: A $\rightarrow$ Z.

## Requirements

### Functional
- [ ] Backend Supabase & Migration:
  - Bổ sung migration SQL: thêm cột `last_sign_in_at timestamptz` vào `public.account_access`.
  - Cập nhật trigger `bootstrap_account_access` để đồng bộ `last_sign_in_at` và `created_at` từ bảng `auth.users` khi người dùng đăng nhập hoặc cập nhật phiên.
  - Cập nhật hàm RPC `admin_list_accounts` (hoặc truy vấn trực tiếp bảng `account_access`) lấy thêm `created_at` và `last_sign_in_at`.
- [ ] Data Layer:
  - Cập nhật `AccountAccess` và `AccountAccessModels.kt`:
    - `createdAtEpochMillis: Long? = null`
    - `lastSignInAtEpochMillis: Long? = null`
  - Cập nhật `SupabaseAccountAccessRepository.kt`:
    - Bổ sung `created_at,last_sign_in_at` vào `ACCESS_COLUMNS`.
    - Phân tích chuỗi ISO-8601 sang `Instant.toEpochMilli()`.
- [ ] Contract & ViewModel:
  - Cập nhật `AccountAdminUi` trong `AccountContract.kt` lưu trữ `createdAtEpochMillis` và `lastSignInAtEpochMillis`.
  - Định nghĩa enum `AccountSortOption`:
    - `CREATED_AT_DESC` (Ngày đăng ký mới nhất)
    - `CREATED_AT_ASC` (Ngày đăng ký cũ nhất)
    - `LAST_SIGN_IN_DESC` (Đăng nhập gần nhất)
    - `EMAIL_ASC` (Email A-Z)
  - Định nghĩa enum `AccountActivityFilter`:
    - `ALL` (Tất cả)
    - `ACTIVE_RECENTLY` (Đăng nhập trong 30 ngày)
    - `INACTIVE` (Chưa từng đăng nhập hoặc > 30 ngày)
  - Cập nhật `AccountUiState` lưu `adminSortOption: AccountSortOption` và `adminActivityFilter: AccountActivityFilter`.
  - Nâng cấp hàm `filterAdminAccounts`: kết hợp tìm kiếm email/userId, lọc role, lọc thời gian hoạt động và sắp xếp danh sách.
- [ ] UI Layer (`AccountScreen.kt`):
  - Hiển thị thông tin ngày đăng ký và lần đăng nhập gần nhất trong mỗi `ClickableSettingItem`:
    - `Đăng ký: dd/MM/yyyy HH:mm`
    - `Đăng nhập: dd/MM/yyyy HH:mm` (hoặc "Chưa từng đăng nhập")
  - Bổ sung thanh điều khiển Sort & Filter trên danh sách Admin:
    - Chip chọn tiêu chí sắp xếp kèm icon mũi tên đảo chiều.
    - Bộ lọc trạng thái hoạt động trực quan.

### Non-Functional
- [ ] Xử lý mượt mà khi danh sách tài khoản lên tới hàng nghìn tài khoản.
- [ ] Tương thích ngược: nếu database cũ chưa có cột `last_sign_in_at`, tự động fallback về hiển thị `created_at` mà không gây lỗi.

## Implementation Steps
1. [ ] Viết migration SQL `supabase/migrations/20260913084500_account_access_timestamps.sql`.
2. [ ] Cập nhật `AccountAccessModels.kt`.
3. [ ] Cập nhật `SupabaseAccountAccessRepository.kt`.
4. [ ] Cập nhật `AccountContract.kt` và `AccountViewModel.kt`.
5. [ ] Cập nhật `AccountScreen.kt`.
6. [ ] Bổ sung string resources tiếng Việt và tiếng Anh.

## Files to Create/Modify
- `supabase/migrations/20260913084500_account_access_timestamps.sql` [NEW]
- `app/src/main/java/io/legado/app/domain/model/AccountAccessModels.kt` [MODIFY]
- `app/src/main/java/io/legado/app/data/repository/SupabaseAccountAccessRepository.kt` [MODIFY]
- `app/src/main/java/io/legado/app/ui/account/AccountContract.kt` [MODIFY]
- `app/src/main/java/io/legado/app/ui/account/AccountViewModel.kt` [MODIFY]
- `app/src/main/java/io/legado/app/ui/account/AccountScreen.kt` [MODIFY]
- `app/src/main/res/values/strings.xml` & `values-vi/strings.xml` [MODIFY]

## Test Criteria
- [ ] Admin tải danh sách tài khoản thấy đầy đủ ngày đăng ký và lần đăng nhập gần nhất.
- [ ] Chuyển đổi sắp xếp theo ngày đăng ký (mới nhất / cũ nhất) và lần đăng nhập gần nhất hoạt động chính xác 100%.
- [ ] Bộ lọc tài khoản hoạt động gần đây / lâu không hoạt động lọc đúng kết quả.
