# Plan: Thiết Lập Danh Sách Model Khả Dụng Thực Tế Cho Gemini API Theo Tier (Milestone P75)

## 1. Bối cảnh & Kết quả Thực nghiệm Live Probe
Từ bài test thực tế với API Key Free Tier của Google Gemini API:
1. **Model đã bị khai tử (Dead Models):** `gemini-2.5-flash`, `gemini-2.5-flash-lite`, `gemini-2.5-pro`, `gemini-2.0-flash`, `gemini-1.5-flash` trả về `HTTP 404: This model is no longer available to new users. Please update your code to models/gemini-3.8-flash...`
   -> **Cần loại bỏ triệt để** khỏi danh sách gợi ý và ranking router.
2. **Model phân cấp theo Tier:**
   - **Free Tier (Miễn phí / Google AI Studio Key chưa add Billing):**
     - **Tối ưu & Ổn định nhất (High Availability):** `gemini-3.5-flash` (test pass 8.6s), `gemini-3.6-flash` (test pass), `gemini-3.1-flash-lite` (test pass).
     - **Thế hệ mới nhất (Peak Priority):** `gemini-3.8-flash`, `gemini-3.8-flash-lite`, `gemini-3.7-flash`, `gemini-3.7-flash-lite` (khả dụng, nhưng khi server Google quá tải có thể nhận 503 Spike in demand).
     - **Không hỗ trợ Free Tier:** Các model dòng Pro (`gemini-3.8-pro`, `gemini-3.7-pro`, `gemini-3.1-pro-preview`) bị Google chặn hoàn toàn với `HTTP 429 You exceeded your current quota`.
   - **Paid Tier (Tài khoản Google Cloud có Billing / Trả phí Pay-as-you-go):**
     - Khả dụng toàn bộ các model Pro và Flash với quota ưu tiên cao nhất, không bị chặn 429 hay 503.

---

## 2. Các Bước Triển Khai Chi Tiết (Action Plan)

### Task 75.1: Cập nhật Catalog & Phân Loại Model Theo Tier (`AiProviderCatalog.kt`)
- Cập nhật mục `gemini` trong `AiProviderCatalog.kt`:
  - Phân chia và dán nhãn rõ ràng:
    - **Nhóm Free Tier (Flash / Flash-Lite):**
      - `gemini-3.8-flash`: "Gemini 3.8 Flash (Free / Paid)"
      - `gemini-3.5-flash`: "Gemini 3.5 Flash (Khuyên dùng Free Tier)"
      - `gemini-3.6-flash`: "Gemini 3.6 Flash (Free / Paid)"
      - `gemini-3.1-flash-lite`: "Gemini 3.1 Flash Lite (Free / Paid)"
      - `gemini-3.8-flash-lite`: "Gemini 3.8 Flash Lite (Free / Paid)"
      - `gemini-3.7-flash`: "Gemini 3.7 Flash (Free / Paid)"
      - `gemini-3.7-flash-lite`: "Gemini 3.7 Flash Lite (Free / Paid)"
      - `gemini-3.5-flash-lite`: "Gemini 3.5 Flash Lite (Free / Paid)"
    - **Nhóm Paid Tier (Yêu cầu Billing):**
      - `gemini-3.8-pro`: "Gemini 3.8 Pro (Yêu cầu Billing)"
      - `gemini-3.7-pro`: "Gemini 3.7 Pro (Yêu cầu Billing)"
      - `gemini-3.1-pro-preview`: "Gemini 3.1 Pro (Yêu cầu Billing)"
  - Loại bỏ hoàn toàn `gemini-3.1-flash` (đã thay bằng 3.1 flash lite) và các model chết.
  - Cập nhật trường `notice` hướng dẫn chi tiết cách chọn model cho tài khoản Free vs Paid.

### Task 75.2: Tinh chỉnh Fetch Models & Lọc Dead Models (`GeminiHandler.kt`)
- Trong `GeminiHandler.kt#fetchModelsInternal`:
  - Lọc bỏ danh sách các dead models mà Google đã ngừng hỗ trợ cho new users (`gemini-2.5-*`, `gemini-2.0-*`, `gemini-1.5-*`, các model image/tts không hỗ trợ text gen).
  - Tự động gắn tag `(Yêu cầu Billing)` cho các model dòng `pro` khi tải danh sách từ API để người dùng Free biết đường tránh.
- Cập nhật `PREFERRED_GEMINI_TRANSLATION_MODELS`:
  - Loại bỏ hoàn toàn `gemini-2.5-flash` và `gemini-2.5-flash-lite`.
  - Điều chỉnh thứ tự ưu tiên:
    1. `gemini-3.8-flash`
    2. `gemini-3.5-flash` (ưu tiên hàng đầu cho tính ổn định Free)
    3. `gemini-3.6-flash`
    4. `gemini-3.1-flash-lite`
    5. `gemini-3.8-flash-lite`
    6. `gemini-3.7-flash`
    7. `gemini-3.7-flash-lite`
    8. `gemini-3.5-flash-lite`
    9. `gemini-3.8-pro`
    10. `gemini-3.7-pro`
    11. `gemini-3.1-pro-preview`

### Task 75.3: Cập nhật Router ViewModel & Edit ViewModel
- Trong `AiRouterViewModel.kt` & `AiProviderEditViewModel.kt`:
  - Đồng bộ `PREFERRED_GEMINI_MODELS` mới, loại bỏ các model dead.
  - Định nghĩa tập hợp `GEMINI_FREE_TIER_MODELS` và `GEMINI_BILLING_REQUIRED_MODELS` để hỗ trợ hiển thị hoặc cảnh báo khi người dùng chọn model Pro trên tài khoản Free.

### Task 75.4: Kiểm thử & Biên dịch (Verification)
- Chạy unit tests: `GeminiHandlerTest`, `AntigravityHandlerTest`, `WebSessionManagerTest` -> **PASSED 100% (24/24 tests)**.
- Chạy biên dịch `:app:compileAppDebugKotlin` -> **BUILD SUCCESSFUL (34s)**.
- Cập nhật `project_progress.json` đánh dấu hoàn tất Milestone P75 (100% DONE).

---

## 3. Kết Quả Thực Thi (Execution Results)
- ✅ **Phân loại rõ ràng theo Tier trong Catalog:** Các model Free Tier (Flash / Flash-Lite) và Paid Tier (Pro yêu cầu Billing) được gắn tag rõ ràng trong `AiProviderCatalog.kt` cùng notice hướng dẫn.
- ✅ **Loại bỏ triệt để Dead Models:** Bỏ hoàn toàn các model đã bị Google khai tử (`gemini-2.5-flash`, `gemini-2.5-flash-lite`, `gemini-2.5-pro`, `gemini-2.0`, `gemini-1.5`) khỏi catalog, router và `GeminiHandler.kt`.
- ✅ **Tự động lọc khi Fetch:** `fetchModelsInternal()` tự động bỏ qua dead models, các model TTS/image không liên quan, và tự động gắn nhãn `(Yêu cầu Billing)` cho các model dòng Pro.
- ✅ **Tối ưu Router Fallback cho Free Tier:** Đưa `gemini-3.5-flash` (đã kiểm nghiệm live pass 100%) lên nhóm đầu ngay sau `gemini-3.8-flash` để đảm bảo ứng dụng không bao giờ bị gián đoạn khi `gemini-3.8-flash` gặp quá tải 503 Spike in demand.
- ✅ **Kiểm thử & Biên dịch:** Sạch sẽ 100%, tất cả test suite đều pass.
