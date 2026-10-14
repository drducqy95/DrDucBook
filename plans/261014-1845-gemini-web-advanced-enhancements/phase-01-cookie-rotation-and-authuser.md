# Phase 01: Cơ Chế Tự Động Làm Mới Cookie (RotateCookies) & Hỗ Trợ Đa Tài Khoản authUser

> **Thuộc kế hoạch:** `PLN-261014-1845` — Gemini Web Advanced Enhancements  
> **Trạng thái:** 🟡 Sẵn sàng triển khai  
> **Các file tác động chính:**
> - `app/src/main/java/io/legado/app/data/repository/ai/GeminiWebSessionManager.kt`
> - `app/src/main/java/io/legado/app/data/repository/ai/GeminiWebHandler.kt`

---

## 1. Vấn Đề Cốt Lõi Cần Giải Quyết

1. **Vấn đề Cookie nhanh hết hạn (`__Secure-1PSIDTS` Expiration):**
   - Google Gemini Web yêu cầu bắt buộc cặp cookie: `__Secure-1PSID` (cookie tài khoản dài hạn, sống vài tháng đến 1 năm) và `__Secure-1PSIDTS` (token phiên ngắn hạn, sống khoảng 12–24 giờ).
   - Khi `__Secure-1PSIDTS` hết hạn, request `StreamGenerate` sẽ lập tức bị Google từ chối với lỗi 401/403 hoặc chuyển hướng đến trang đăng nhập `accounts.google.com/ServiceLogin`.
   - Trong phiên bản hiện tại của DrDucBook, khi gặp lỗi này, người dùng buộc phải vào trình duyệt máy tính, mở DevTools để copy lại toàn bộ Cookie mới rất phiền toái.
2. **Vấn đề Đa Tài Khoản (`authUser` / Multi-Login Slot):**
   - Người dùng thường đăng nhập từ 2 đến nhiều tài khoản Google trên cùng một trình duyệt. Tài khoản chính là `/u/0/`, tài khoản thứ 2 là `/u/1/`, tài khoản thứ 3 là `/u/2/`.
   - Nếu cookie được copy từ tab `/u/1/` hoặc `/u/2/` nhưng DrDucBook luôn gọi endpoint mặc định `https://gemini.google.com/app` thì Google sẽ báo lỗi không khớp tài khoản hoặc không tìm thấy `SNlM0e`.

---

## 2. Kế Thừa Kỹ Thuật Từ `gemini-web-to-api`

Qua phân tích `gemini_service.go` (dòng 408–468):
1. **Endpoint Rotate Cookies của Google:**
   - **URL:** `POST https://accounts.google.com/RotateCookies`
   - **Headers:**
     - `Content-Type: application/json`
     - `User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36`
     - `Cookie: __Secure-1PSID=<psid>; __Secure-1PSIDTS=<psidts>`
   - **Body:** Bắt buộc chính xác chuỗi JSON: `[000,"-0000000000000000000"]`
   - **Phản hồi từ Google:** Khi thành công (HTTP 200), Google sẽ trả về cookie mới trong header `Set-Cookie: __Secure-1PSIDTS=<new_token>; Path=/; Domain=.google.com; Secure; HttpOnly; Priority=HIGH`.
2. **Quy trình Rotation & Auto-Recovery:**
   - Nếu người dùng chỉ cung cấp `__Secure-1PSID`: Tự động gọi `RotateCookies` để lấy `__Secure-1PSIDTS` trước khi gọi init.
   - Khi request `StreamGenerate` trả về mã 401/403 hoặc `RPC er`: Thử chạy `RotateCookies()` ngay lập tức để lấy lại `PSIDTS` mới và retry request 1 lần trước khi báo lỗi cho người dùng.
3. **Cấu hình & Tự Động Nhận Diện `authUser`:**
   - Cho phép nhập JSON credential có trường `auth_user`, `authUser`, hoặc `userIndex` (ví dụ: `{"cookie": "...", "authUser": "1"}`).
   - Tự động nhận diện nếu người dùng dán URL chứa `/u/1/` hoặc `/u/2/`.
   - Rewrite endpoint linh hoạt:
     ```kotlin
     fun getGeminiUrl(endpoint: String, authUser: String): String {
         if (authUser.isBlank() || authUser == "0") return endpoint
         return endpoint.replace("https://gemini.google.com/", "https://gemini.google.com/u/$authUser/")
     }
     ```

---

## 3. Các Bước Triển Khai Cụ Thể Trong DrDucBook

### Bước 1: Mở rộng `GeminiWebSession`
```kotlin
data class GeminiWebSession(
    val cookies: Map<String, String>,
    val snlm0e: String,
    val cookieHeader: String,
    val buildLabel: String = "boq_gemini-web-uiserver_20261012.15_p0",
    val authUser: String = "",
    val sessionId: String = "",
    val pushId: String = "feeds/mcudyrk2a4khkz",
    val fetchedAt: Long = System.currentTimeMillis(),
)
```

### Bước 2: Thêm hàm `rotateCookies(...)` vào `GeminiWebSessionManager`
- Gửi `POST https://accounts.google.com/RotateCookies` với payload `[000,"-0000000000000000000"]`.
- Bóc tách `Set-Cookie` lấy `__Secure-1PSIDTS` mới.
- Hợp nhất vào `cookieHeader` hiện tại và lưu vào cache.

### Bước 3: Cơ chế Tự động Thử Lại (Auto-Retry on Expired Cookie)
- Trong `GeminiWebHandler.kt`, khi bắt gặp `checkRpcError` hoặc HTTP 401/403:
  1. Gọi `GeminiWebSessionManager.rotateSession(apiKey)`.
  2. Nếu xoay cookie thành công: Thực hiện retry lại request 1 lần duy nhất với phiên làm việc mới.
  3. Chỉ khi rotation thất bại mới ném ngoại lệ thông báo cho người dùng.

---

## 4. Tiêu Chí Nghiệm Thu
- [ ] Người dùng chỉ cần dán `__Secure-1PSID`, app tự động lấy được `__Secure-1PSIDTS`.
- [ ] Khi `__Secure-1PSIDTS` hết hạn sau 24h, app tự động xoay và tiếp tục dịch truyện mà không báo lỗi.
- [ ] Hoạt động trơn tru với các tài khoản Google nằm ở slot `/u/1/`, `/u/2/`.
