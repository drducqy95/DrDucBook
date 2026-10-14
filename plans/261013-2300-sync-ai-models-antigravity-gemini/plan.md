# Plan: Rà soát & Cập nhật Model cho Antigravity, Gemini Web và Google Gemini API (Milestone P74)
## Dựa trên chính sách mới của hãng đối với các gói: Free, Google AI Plus, Google AI Pro, Google AI Ultra

## 1. Mục tiêu (Objective)
Cập nhật toàn diện kiến trúc kết nối, routing, và danh sách model cho 3 provider:
1. **Google Antigravity** (`ANTIGRAVITY` / `oauth_antigravity`)
2. **Gemini Web** (`GEMINI_WEB` / `gemini_web`)
3. **Google Gemini API** (`GEMINI_GENERATE_CONTENT` / `gemini`)

Thích ứng chuẩn xác với chính sách phân cấp 4 gói thuê bao mới của Google:
- **Free ($0/tháng)**: Chỉ được cấp quyền dùng **Gemini Flash-Lite** trên giao diện Web/App. Hạn mức cơ sở tính theo tuần.
- **Google AI Plus ($4.99/tháng)**: Mở khóa **Gemini Flash** và **Flash-Lite**, 2x hạn mức. Không có Pro.
- **Google AI Pro ($19.99/tháng)**: Mở khóa toàn bộ **Gemini Pro** (Gemini 3.1 Pro / 3.8 Pro), context 1M tokens, 4x hạn mức, Deep Think reasoning, Gemini Spark.
- **Google AI Ultra ($99.99 - $199.99/tháng)**: Dành cho power users/developers, hạn mức 5x-20x gói Pro, mở khóa **Google Antigravity** toàn diện, Deep Think Max, và third-party frontier models (Claude Opus 4.7, Claude Sonnet 4.6, GPT-OSS 120B).

---

## 2. Các Bước Triển Khai Chi Tiết (Action Plan)

### Task 74.1: Cập nhật Google Antigravity & Quản Lý Tier
- Trong `AntigravityProjectResolver.kt`:
  - Thêm model `claude-opus-4-7-thinking` ("Claude Opus 4.7 (Thinking)", contextWindow = 1_000_000, maxOutputTokens = 64_000) vào `ANTIGRAVITY_SUPPORTED_MODELS`.
- Trong `AiOAuthRepository.kt`:
  - Khi gọi `resolveGoogleAccount`, đọc giá trị `tierId` ("FREE", "PRO", "ULTRA") từ `loadPayload` / `allowedTiers` và lưu vào `account.data["tierId"]` để app và runtime metadata nhận diện chính xác cấp độ tài khoản (Free vs Pro vs Ultra).

### Task 74.2: Cập nhật Gemini Web Provider (Thích Ứng Gói Free/Plus/Pro/Ultra)
- Trong `GeminiWebHandler.kt`:
  - Thêm `AiAvailableModel("gemini-web-flash-lite", "Gemini Web (Flash-Lite · Free)", 1_000_000, 8_192)` vào `fetchModels()`. Model này tương ứng với mã Google `MODE_CATEGORY = 6` (`FLASH_LITE`).
  - Nâng cấp `gemini-web-default` contextWindow từ 32,000 lên 1,000,000 tokens.
  - Bổ sung directive prompt riêng cho `lite` trong `buildFullPrompt()`: *"Hãy phản hồi cực nhanh, ngắn gọn, súc tích và đi thẳng vào nội dung chính."*
  - Bổ sung cơ chế auto-downgrade/fallback: nếu người dùng gửi model Pro/Flash nhưng tài khoản Free bị Google từ chối (`BardErrorInfo [1/2/3]` hoặc lỗi quyền hạn gói), tự động hạ cấp thử lại với `gemini-web-flash-lite`.
- Trong `AiProviderCatalog.kt`:
  - Thêm `gemini-web-flash-lite` vào catalog entry `gemini_web`, cập nhật contextWindow và ghi chú rõ ràng các gói Free, Plus, Pro, Ultra.
- Trong `AiRouterViewModel.kt`:
  - Thêm `"gemini-web-flash-lite"` vào `GEMINI_WEB_KNOWN_FREE_MODELS`.

### Task 74.3: Cập nhật Google Gemini API Provider (Thế Hệ Gemini 3.8 & 3.7)
- Trong `AiModels.kt`:
  - Đổi default preset `AiProviderPresets.items` của Google Gemini thành `gemini-3.8-flash` ("Gemini 3.8 Flash").
- Trong `AiProviderCatalog.kt`:
  - Bổ sung các model mới vào catalog `gemini`:
    - `gemini-3.8-flash` (1,000,000 / 64,000)
    - `gemini-3.8-flash-lite` (1,000,000 / 64,000)
    - `gemini-3.8-pro` (2,000,000 / 64,000)
    - `gemini-3.7-flash` (1,000,000 / 64,000)
    - `gemini-3.7-flash-lite` (1,000,000 / 64,000)
    - `gemini-3.7-pro` (2,000,000 / 64,000)
- Trong `GeminiHandler.kt`:
  - Thêm `gemini-3.8-flash`, `gemini-3.8-flash-lite`, `gemini-3.8-pro`, `gemini-3.7-flash`, `gemini-3.7-flash-lite`, `gemini-3.7-pro` vào đầu danh sách `PREFERRED_GEMINI_TRANSLATION_MODELS`.
- Trong `AiRouterViewModel.kt` & `AiProviderEditViewModel.kt`:
  - Thêm dải model Gemini 3.8 và 3.7 vào đầu `PREFERRED_GEMINI_MODELS`.
- Trong `GeminiHandlerTest.kt`:
  - Cập nhật test case default model sang `gemini-3.8-flash`.

### Task 74.4: Kiểm Thử & Xác Nhận (Verification)
- Chạy kiểm tra biên dịch Kotlin `:app:compileAppDebugKotlin` -> **PASSED (31s)**.
- Chạy unit tests: `AntigravityHandlerTest`, `GeminiHandlerTest`, `WebSessionManagerTest` -> **PASSED 100% (24/24 tests)**.
- Cập nhật `project_progress.json` đánh dấu hoàn tất Milestone P74 (100% DONE).

---

## 3. Kết Quả Thực Thi (Execution Results)
- ✅ **Google Antigravity:** Đã loại bỏ hoàn toàn các model Gemini < 3.8 (`gemini-3.7-flash-*`, `gemini-3.6-flash-*`, `gemini-3.1-flash-lite`) và Gemini 3.1 Pro (`gemini-pro-agent`, `gemini-3.1-pro-low`) khỏi active model list. Đưa toàn bộ vào `ANTIGRAVITY_RETIRED_MODEL_IDS` để Room DB tự động chuyển sang trạng thái DEPRECATED. Bổ sung `claude-opus-4-7-thinking` và trích xuất lưu `tierId` (`FREE`, `PRO`, `ULTRA`).
- ✅ **Gemini Web:** Bổ sung `gemini-web-flash-lite` (Google category code 6) dành cho tài khoản Free/Guest. Nâng cấp contextWindow của `gemini-web-default` lên 1,000,000 tokens. Thêm prompt instruction directive chuyên biệt cho `lite`.
- ✅ **Google Gemini API:** Nâng default preset lên `gemini-3.8-flash`. Thêm dải Gemini 3.8 và 3.7 vào catalog và đưa lên đầu danh sách xếp hạng ưu tiên trong `GeminiHandler.kt`, `AiRouterViewModel.kt`, `AiProviderEditViewModel.kt`.
- ✅ **Kiểm thử & Biên dịch:** Sửa và pass toàn bộ unit tests liên quan, compile Kotlin hoàn toàn sạch sẽ.
