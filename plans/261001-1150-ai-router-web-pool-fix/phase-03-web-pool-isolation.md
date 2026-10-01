# Phase 03: Cách ly phiên Web Pool Multi-Account (Gemini Web & ChatGPT Web)

Status: ✅ Completed
Dependencies: [Phase 02](phase-02-gemini-web-auto.md)

## Objective
Khắc phục triệt để lỗi khi người dùng cấu hình combo xoay tua (pool / round-robin / fallback) giữa nhiều tài khoản Gemini Web hoặc ChatGPT Web. Cô lập cache phiên theo từng thông tin xác thực (`credentialKey`) và loại bỏ việc ghi đè cookie từ WebView toàn cục.

## Context & Phân tích chi tiết nguyên nhân gốc rễ (Root Cause)

Khi người dùng cấu hình xoay pool giữa nhiều tài khoản Gemini Web hoặc ChatGPT Web, nhưng dùng 1 tài khoản lẻ thì chạy tốt, nguyên nhân đến từ 3 điểm nghẽn kiến trúc sau:

1. **Bug Singleton Cache trong `GeminiWebSessionManager.kt`:**
   - Trong `GeminiWebSessionManager`:
     ```kotlin
     @Volatile private var cachedSession: GeminiWebSession? = null
     ```
   - Khi gọi `getSession(explicitCredential)`:
     ```kotlin
     val current = cachedSession
     if (current != null && (now - current.fetchedAt) < 30 * 60 * 1000L) {
         return@withLock current // ❌ LỖI NGHIÊM TRỌNG: Không kiểm tra explicitCredential có khớp hay không!
     }
     ```
   - Hậu quả: Khi Tài khoản A dịch xong, `cachedSession` lưu cookies và `SNlM0e` của Tài khoản A. Khi combo xoay sang Tài khoản B, `getSession()` lập tức trả về session của Tài khoản A!

2. **Bug Singleton Cache trong `ChatGptWebSessionManager.kt`:**
   - Tương tự, `ChatGptWebSessionManager` chỉ có 1 biến `@Volatile private var cachedSession: ChatGptWebSession? = null`.
   - Khi xoay từ Tài khoản A sang Tài khoản B (nếu cấu hình bằng cookie), nó trả về `accessToken` cũ của Tài khoản A cho Tài khoản B!

3. **Xung đột Cookie WebView toàn cục trong `ChatGptWebHandler.kt`:**
   - Trong `ChatGptWebHandler.kt` (dòng 151-155):
     ```kotlin
     val cookies = cookieFromCred.ifBlank {
         CookieManager.getInstance().getCookie("https://chatgpt.com").orEmpty()
     }
     ```
   - `CookieManager.getInstance()` là singleton của hệ điều hành Android, chỉ lưu cookie của tài khoản vừa đăng nhập WebView gần nhất.

## Chi tiết các Call Sites cần sửa (từ phân tích codebase)

### `GeminiWebSessionManager.invalidateSession()` — 5 call sites:
| # | File | Line | Context |
|---|------|------|---------|
| 1 | `GeminiWebHandler.kt` | 135 | HTTP 401/403 check trong `streamInternal()` |
| 2 | `GeminiWebHandler.kt` | 174 | RPC error `[["er"...` trong `checkRpcError()` |
| 3 | `GeminiWebHandler.kt` | 181 | `BardErrorInfo [1/2/3]` trong `checkRpcError()` |
| 4 | `WebSessionManagerTest.kt` | 16 | `@Before fun setup()` |
| 5 | `WebSessionManagerTest.kt` | 39 | Test `geminiWebSessionManagerParsesWizGlobalDataFormat` |

### `ChatGptWebSessionManager.invalidateSession()` — 2 call sites:
| # | File | Line | Context |
|---|------|------|---------|
| 1 | `ChatGptWebHandler.kt` | 196 | HTTP 401/403 check trong `streamInternal()` |
| 2 | `WebSessionManagerTest.kt` | 17 | `@Before fun setup()` |

### Credential Availability ✅
- `GeminiWebHandler.streamInternal()`: `request.model.provider.apiKey` luôn in scope → dùng làm cache key.
- `ChatGptWebHandler.streamInternal()`: `request.model.provider.apiKey` luôn in scope → dùng làm cache key.
- `AiRouterRepository` guarantee: Trong cả routed và direct execution, `request.model.provider.apiKey` luôn chứa resolved credential string.

## Requirements

### Functional
- [ ] Trong `GeminiWebSessionManager.kt`:
  - Thay thế biến đơn `cachedSession` bằng `ConcurrentHashMap<String, GeminiWebSession>`.
  - Khóa (Key) normalize bằng `credential?.trim().orEmpty().ifBlank { "__default_webkit_cookie__" }`.
  - Cập nhật `invalidateSession(explicitCredential: String? = null)`:
    - Nếu `explicitCredential == null` → `cache.clear()` (backward-compatible với unit tests `setup()`).
    - Nếu `explicitCredential != null` → chỉ xóa entry `normalizeKey(explicitCredential)`.
  - Giới hạn kích thước cache map (tối đa ~20 entries, evict oldest) tránh memory leak.
- [ ] Trong `ChatGptWebSessionManager.kt`:
  - Thay thế biến đơn `cachedSession` bằng `ConcurrentHashMap<String, ChatGptWebSession>` theo hash credential.
  - Cập nhật `invalidateSession(explicitCredential: String? = null)` tương ứng (null → clear all, non-null → clear specific).
- [ ] **⚠️ [THIẾU SÓT ĐÃ BỔ SUNG] `GeminiWebHandler.checkRpcError()` (line ~174-181):**
  - Hàm hiện tại signature là `checkRpcError(line: String)` → cần thêm param `credentialKey: String? = null`.
  - Trong `streamInternal()` (line ~149), truyền `checkRpcError(line, request.model.provider.apiKey)`.
  - Bên trong `checkRpcError`, gọi `invalidateSession(credentialKey)` thay vì `invalidateSession()`.
- [ ] **⚠️ [THIẾU SÓT ĐÃ BỔ SUNG] `GeminiWebHandler.kt` HTTP 401/403 (line 135):**
  - Cập nhật `GeminiWebSessionManager.invalidateSession()` → `GeminiWebSessionManager.invalidateSession(request.model.provider.apiKey)`.
- [ ] **⚠️ [THIẾU SÓT ĐÃ BỔ SUNG] `ChatGptWebHandler.kt` HTTP 401/403 (line 196):**
  - Cập nhật `ChatGptWebSessionManager.invalidateSession()` → `ChatGptWebSessionManager.invalidateSession(request.model.provider.apiKey)`.
- [ ] **⚠️ [THIẾU SÓT ĐÃ BỔ SUNG] `GeminiWebSessionManager.resolveCookieHeader()` fallback (line 167):**
  - `CookieManager.getInstance().getCookie(GEMINI_BASE_URL)` cũng là global singleton → khi credential rỗng, cần ghi chú rằng chế độ "guest" (dùng WebKit cookie) chỉ hỗ trợ 1 tài khoản, không hỗ trợ pool rotation.
- [ ] **⚠️ [THIẾU SÓT ĐÃ BỔ SUNG] `ChatGptWebSessionManager.resolveCookieHeader()` fallback (line 154, 190):**
  - Tương tự, fallback về `CookieManager.getInstance()` chỉ hỗ trợ 1 tài khoản. Ghi rõ documentation.
- [ ] Trong `ChatGptWebHandler.kt`:
  - Ưu tiên tuyệt đối cookie từ credential của chính provider đó.
  - Khi provider đã có `explicitCred`, KHÔNG tự ý fallback sang `CookieManager.getInstance()` (line 153) để tránh ô nhiễm cookie giữa các tài khoản trong pool.

### Non-Functional
- Giới hạn kích thước cache map (tránh memory leak nếu người dùng đổi credential nhiều lần) — max 20 entries, evict by oldest `fetchedAt`.
- Đảm bảo tính toàn vẹn đa luồng với `Mutex` (cho suspend coroutine) + `ConcurrentHashMap` (cho thread-safe reads).

## Implementation Steps
1. [ ] Tạo helper function `normalizeKey(credential: String?): String` trong cả 2 SessionManager.
2. [ ] Sửa `GeminiWebSessionManager.kt`: thay `cachedSession` → `ConcurrentHashMap`, cập nhật `getSession()` lookup theo key, cập nhật `invalidateSession()` với overloaded param.
3. [ ] Sửa `ChatGptWebSessionManager.kt`: tương tự, thay singleton cache → keyed cache.
4. [ ] Sửa `GeminiWebHandler.checkRpcError()`: thêm `credentialKey` param, truyền credential từ `streamInternal`.
5. [ ] Sửa `GeminiWebHandler.kt` line 135: truyền credential vào `invalidateSession`.
6. [ ] Sửa `ChatGptWebHandler.kt` line 196: truyền credential vào `invalidateSession`.
7. [ ] Sửa `ChatGptWebHandler.kt` line 153: khi `explicitCred` non-blank, bỏ fallback sang `CookieManager.getInstance()`.
8. [ ] Viết unit test đa tài khoản: 2 credential khác nhau → 2 session độc lập.
9. [ ] Viết unit test invalidate session: credential A bị invalidate, credential B vẫn intact.

## Files to Create/Modify
- `app/src/main/java/io/legado/app/data/repository/ai/GeminiWebSessionManager.kt` — Keyed cache + credential-aware invalidation
- `app/src/main/java/io/legado/app/data/repository/ai/ChatGptWebSessionManager.kt` — Keyed cache + credential-aware invalidation
- `app/src/main/java/io/legado/app/data/repository/ai/GeminiWebHandler.kt` — Pass credential to `checkRpcError` + `invalidateSession`
- `app/src/main/java/io/legado/app/data/repository/ai/ChatGptWebHandler.kt` — Pass credential to `invalidateSession` + isolate cookie fallback
- `app/src/test/java/io/legado/app/data/repository/ai/WebSessionManagerTest.kt` — Multi-account isolation tests

## Test Criteria
- [ ] Test 2 credential khác nhau gọi `getSession()` / `resolveAccessToken()` trả về đúng session riêng biệt cho từng credential.
- [ ] Invalidate session của credential 1 không ảnh hưởng đến session của credential 2.
- [ ] `invalidateSession(null)` xóa toàn bộ cache (backward-compatible).
- [ ] Biên dịch Kotlin thành công.

---
Next Phase: [Phase 04: Bảo vệ Route Combo & Chống Override Fallback](phase-04-combo-fallback-protection.md)
