# Phase 03: Google Antigravity HTTP 403 VALIDATION_REQUIRED Handler & Fallback

Status: ⬜ Pending
Dependencies: None

## Objective
Xử lý lỗi dịch thuật AI xảy ra khi gọi Google Antigravity (`cloudcode-pa.googleapis.com`):
```
HTTP 403: {
  "error": {
    "code": 403,
    "message": "Verify your account to continue.",
    "status": "PERMISSION_DENIED",
    "details": [
      {
        "@type": "type.googleapis.com/google.rpc.ErrorInfo",
        "reason": "VALIDATION_REQUIRED",
        "domain": "cloudcode-pa.googleapis.com",
        "metadata": {
          "validation_url_link_text": "Verify your account",
          "validation_url": "https://accounts.google.com/signin/continue?sarp=1&scc=1&continue=https://developers...
```
Hiện tại, lỗi này bị gộp chung vào `AiFailureKind.AUTHENTICATION` ("Xác thực hoặc quyền truy cập bị từ chối") mà không trích xuất link `validation_url`, khiến người dùng không biết cách xác minh tài khoản Google. Đồng thời, lỗi này làm đứt gãy toàn bộ quá trình dịch chương nhiều chunk thay vì tự động chuyển sang model/provider dự phòng trong combo/pool.

## Requirements

### Functional
- [ ] Bổ sung `AiFailureKind.VALIDATION_REQUIRED` vào enum `AiFailureKind`:
  - Nhãn hiển thị tiếng Việt: "Yêu cầu xác minh tài khoản"
  - Đánh dấu `affectsCredential = true` nhưng cấu hình cooldown ngắn trong `AiRouterPolicy`: **5 phút** (`5L * 60L * 1_000L`), thay vì 24 giờ như `AUTHENTICATION`.
  - Cấu hình `mayFallback(VALIDATION_REQUIRED) = true` để cho phép router tự động chuyển sang model/profile tiếp theo trong combo.
- [ ] Nhận diện lỗi `VALIDATION_REQUIRED` trong `AntigravityHandler.kt` và `AiProviderFailureClassifier.kt`:
  - Khi nhận HTTP 403, bóc tách JSON error details để tìm `details -> reason == "VALIDATION_REQUIRED"`.
  - Trích xuất `validation_url` từ `metadata.validation_url`.
- [ ] Bổ sung trường `val actionUrl: String? = null` vào `AiProviderFailure`.
- [ ] Hiển thị thông báo lỗi rõ ràng kèm nút/hành động "Xác minh tài khoản Google" (mở `actionUrl` bằng trình duyệt).
- [ ] Trong `TranslateChapterUseCase.kt` & `AiRouterRepository`:
  - Khi một chunk translation gặp lỗi `VALIDATION_REQUIRED`, router chuyển sang target tiếp theo trong combo/pool nhờ `mayFallback = true`.
  - Sau khi người dùng xác minh trên trình duyệt, tài khoản sẵn sàng sử dụng lại sau 5 phút mà không bị đóng băng 24 giờ.

### Non-Functional
- [ ] Đảm bảo credential bị checkpoint được đánh dấu trạng thái phù hợp (`AiCapabilityStatus.AUTH_FAILED` với cooldown 5 phút) để router không liên tục gửi request vào tài khoản đang bị khóa tạm thời.

## Implementation Steps
1. [ ] Cập nhật `io/legado/app/domain/model/AiProviderFailure.kt`:
   - Thêm `VALIDATION_REQUIRED` vào `AiFailureKind`.
   - Thêm trường `val actionUrl: String? = null` vào `AiProviderFailure`.
   - Cập nhật `AiProviderFailureClassifier.classify`: phát hiện chuỗi `"VALIDATION_REQUIRED"` và trích xuất `validation_url`.
2. [ ] Cập nhật `io/legado/app/domain/usecase/AiRouterPolicy.kt`:
   - `affectsCredential(VALIDATION_REQUIRED)` -> `true`.
   - `mayFallback(VALIDATION_REQUIRED)` -> `true`.
   - `cooldownMillis(VALIDATION_REQUIRED)` -> 5 phút (`5 * 60_000L`).
3. [ ] Cập nhật `AntigravityHandler.kt`:
   - Khi nhận HTTP 403, bóc tách JSON body để tìm `details -> reason == "VALIDATION_REQUIRED"` và trích xuất `validation_url`.
   - Ném exception chứa rõ mã lỗi và `validation_url` để `AiProviderFailureClassifier` bắt chính xác.
4. [ ] Cập nhật UI thông báo / Translation Sheet để có nút bấm mở `actionUrl` nếu có.
5. [ ] Cập nhật Unit Tests trong `AiProviderFailureTest.kt` và `AiRouterPolicyTest.kt`.

## Files to Create/Modify
- `app/src/main/java/io/legado/app/domain/model/AiProviderFailure.kt`
- `app/src/main/java/io/legado/app/domain/usecase/AiRouterPolicy.kt`
- `app/src/main/java/io/legado/app/data/repository/ai/AntigravityHandler.kt`
- `app/src/main/java/io/legado/app/ui/book/read/sheet/AiChapterTranslateSheet.kt`
- `app/src/test/java/io/legado/app/domain/model/AiProviderFailureTest.kt`
- `app/src/test/java/io/legado/app/domain/usecase/AiRouterPolicyTest.kt`

## Test Criteria
- [ ] Unit test giả lập HTTP 403 với `VALIDATION_REQUIRED` kiểm tra trích xuất chính xác `actionUrl` và phân loại đúng `AiFailureKind.VALIDATION_REQUIRED`.
- [ ] Kiểm tra cooldown của `VALIDATION_REQUIRED` là 5 phút thay vì 24 giờ.
- [ ] Khi Antigravity bị 403, UI hiển thị thông điệp "Tài khoản Google yêu cầu xác minh" và cung cấp liên kết mở trình duyệt.

---
Next Phase: [Phase 04 - Chatbot Tool Pre-approval Settings](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/plans/261004-1530-ux-auth-tools-link-sync-fix/phase-04-chatbot-tool-permissions-settings.md)
