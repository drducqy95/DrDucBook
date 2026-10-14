# Phase 05: Kiểm Thử Toàn Diện, Phục Hồi Lỗi Tự Động & Bàn Giao

> **Thuộc kế hoạch:** `PLN-261014-1845` — Gemini Web Advanced Enhancements  
> **Trạng thái:** 🟡 Sẵn sàng triển khai  
> **Các file tác động chính:**
> - `app/src/test/java/io/legado/app/data/repository/ai/GeminiWebSessionManagerTest.kt`
> - `app/src/test/java/io/legado/app/data/repository/ai/GeminiWebHandlerTest.kt`

---

## 1. Trọng Tâm Kiểm Thử & Đảm Bảo Độ Ổn Định

1. **Unit Tests cho Cơ Chế Xoay Cookie (`RotateCookies`):**
   - Viết test case giả lập (mock server) endpoint `https://accounts.google.com/RotateCookies`.
   - Kiểm tra việc gửi payload `[000,"-0000000000000000000"]`, bóc tách header `Set-Cookie`, cập nhật `__Secure-1PSIDTS` vào `cookieHeader`.
   - Kiểm tra kịch bản chỉ có `__Secure-1PSID`: Tự động trigger rotate để sinh ra session đầy đủ.
2. **Unit Tests cho Header JSPB & Trích Xuất Metadata:**
   - Kiểm tra hàm `buildModelJspbHeader` sinh ra đúng mảng JSON 17 phần tử với mã model phù hợp (`1`, `2`, `3`, `6`).
   - Kiểm tra việc bóc tách `cid`, `rid`, `rcid` từ cấu trúc candidate JSON của Google.
   - Kiểm tra hàm `buildFReqPayload` khi có và khi không có `metadata`.
3. **Kiểm Tra Biên Dịch & Không Làm Ảnh Hưởng Tính Năng Cũ:**
   - Chạy lệnh biên dịch: `.\gradlew.bat :app:compileAppDebugKotlin`.
   - Chạy toàn bộ test suite: `.\gradlew.bat test`.
   - Đảm bảo các tính năng dịch QT, NMT, Model combo, TTS, Source health không bị ảnh hưởng.

---

## 2. Kế Hoạch Triển Khai Kiểm Thử

```bash
# 1. Chạy các unit test mới của Gemini Web
.\gradlew.bat test --tests "io.legado.app.data.repository.ai.GeminiWeb*"

# 2. Biên dịch Kotlin toàn app
.\gradlew.bat :app:compileAppDebugKotlin

# 3. Kiểm tra git status & tạo commit chuẩn hóa
```

---

## 3. Tiêu Chí Nghiệm Thu Phase 05
- [ ] Tất cả unit tests trong `GeminiWebSessionManagerTest` và `GeminiWebHandlerTest` đều pass 100%.
- [ ] App compile Kotlin không có bất kỳ lỗi hoặc warning mới nào.
- [ ] Toàn bộ mã nguồn sạch đẹp, tuân thủ Clean Architecture và quy tắc Surgical Changes của `AGENTS.md`.
