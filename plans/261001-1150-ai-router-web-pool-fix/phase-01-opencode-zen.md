# Phase 01: OpenCode Zen & Go Header Bypass

Status: ✅ Completed
Dependencies: None

## Objective
Khắc phục lỗi HTTP `403 FreeTierError` trên OpenCode Zen Free và lỗi HTTP `400 MissingSessionID` trên OpenCode Go bằng cách inject đầy đủ các HTTP headers nhận diện OpenCode CLI vào OkHttp request pipeline, đồng thời cập nhật danh mục model hoạt động.

## Context & Root Cause
- Upstream OpenCode đã thắt chặt server:
  - OpenCode Zen Free (`https://opencode.ai/zen/v1`): Bắt buộc kiểm tra client identification. Nếu thiếu headers CLI, server trả về `403 FreeTierError: OpenCode's free tier can only be used from within OpenCode`.
  - OpenCode Go (`https://opencode.ai/zen/go/v1`): Bắt buộc có `x-opencode-session: <uuid>`, nếu thiếu trả về `400 MissingSessionID`.
- Giải pháp (đã test & xác minh qua live curl tương đương 9Router / OmniRoute):
  - `User-Agent`: `opencode/1.1.2/cli`
  - `x-opencode-client`: `cli`
  - `x-opencode-session`: UUID session ngẫu nhiên hoặc theo phiên
  - `x-opencode-project`: UUID ngẫu nhiên
  - `x-opencode-request`: UUID ngẫu nhiên

## Requirements

### Functional
- [ ] Trong `OpenAiChatHandler.kt`:
  - Sửa `openAiChatHeaders()` (line 243): Nếu `provider.baseUrl.contains("opencode.ai")`, tự động bổ sung 5 headers CLI.
  - Hàm này đã bao phủ cả 3 luồng: `generateInternal` (line 75), `streamInternal` (line 123), `fetchModelsInternal` (line 229) — KHÔNG cần sửa thêm.
- [ ] **⚠️ [THIẾU SÓT ĐÃ BỔ SUNG] `OpenAiImageRepository.kt`** (line 52):
  - File này cũng gọi `openAiChatHeaders()` → sẽ tự động được hưởng lợi nếu provider là OpenCode.
  - KHÔNG cần sửa thêm, nhưng cần **ghi chú xác nhận** trong PR rằng image gen qua OpenCode (nếu hỗ trợ) cũng sẽ có đúng headers.
- [ ] **⚠️ [THIẾU SÓT ĐÃ BỔ SUNG] `OpenAiResponsesHandler.kt`:**
  - File này có header function RIÊNG `openAiResponsesHeaders()` (line 427-457), KHÔNG reuse `openAiChatHeaders()`.
  - OpenCode hiện dùng protocol `OPENAI_CHAT_COMPLETIONS`, nhưng cần future-proof: thêm logic inject tương tự vào `openAiResponsesHeaders()` cho trường hợp OpenCode chuyển sang OPENAI_RESPONSES protocol.
  - **⚠️ [THIẾU SÓT ĐÃ BỔ SUNG] `fetchOpenAiCompatibleModels()`** trong `OpenAiResponsesHandler.kt` (line 257-281) build headers inline, KHÔNG dùng `openAiResponsesHeaders()` → cần refactor để delegate về `openAiResponsesHeaders()` hoặc inject OpenCode headers.
- [ ] **⚠️ [THIẾU SÓT ĐÃ BỔ SUNG] Trong `AiModels.kt`** (line 119-127):
  - `AiProviderPresets.items` cũng chứa entry `opencode_free` với URL `opencode.ai/zen/v1`. Cần cập nhật model name/id cho đồng bộ với catalog.
- [ ] Trong `AiProviderCatalog.kt`:
  - Cập nhật danh sách model của `opencode_free`:
    - `deepseek-v4-flash-free` (DeepSeek V4 Flash Free - 128k context)
    - `nemotron-3.5-lightning-free` (Nemotron 3.5 Lightning Free - 128k context)
    - `mimo-v2.6-flash-free` (MiMo V2.6 Flash Free - 128k context)
    - `space-bunny-free` (Space Bunny Free - 128k context)
    - `longcat-2.5-preview-free` (LongCat 2.5 Preview Free - 128k context)
    - `big-pickle` (Big Pickle - 200k context)
  - Cập nhật `notice` thông báo việc đã inject CLI headers.

### Non-Functional
- Sinh UUID nhẹ nhàng, không gây block luồng hay leak bộ nhớ.
- Header không làm ảnh hưởng đến các provider OpenAI-compatible khác (chỉ kích hoạt khi baseUrl chứa `opencode.ai`).

## Implementation Steps
1. [ ] Sửa `openAiChatHeaders` trong `OpenAiChatHandler.kt` (line 243-255) để inject 5 headers khi URL chứa `opencode.ai`.
2. [ ] Sửa `openAiResponsesHeaders` trong `OpenAiResponsesHandler.kt` (line 427-457) thêm logic inject tương tự cho future-proofing.
3. [ ] Refactor `fetchOpenAiCompatibleModels()` trong `OpenAiResponsesHandler.kt` (line 257-281) để delegate headers về `openAiResponsesHeaders()` thay vì build inline.
4. [ ] Cập nhật danh sách model và notice trong `AiProviderCatalog.kt` cho `opencode_free` (line 46-63).
5. [ ] Đồng bộ `AiProviderPresets.items` trong `AiModels.kt` (line 119-127) với model mới.
6. [ ] Viết unit test xác nhận headers được sinh đúng khi provider là OpenCode.
7. [ ] Viết unit test xác nhận provider khác KHÔNG bị inject headers OpenCode.

## Files to Create/Modify
- `app/src/main/java/io/legado/app/data/repository/ai/OpenAiChatHandler.kt` — Inject CLI headers vào `openAiChatHeaders()`
- `app/src/main/java/io/legado/app/data/repository/ai/OpenAiResponsesHandler.kt` — Inject headers + refactor `fetchOpenAiCompatibleModels`
- `app/src/main/java/io/legado/app/domain/model/AiProviderCatalog.kt` — Cập nhật active models & notice
- `app/src/main/java/io/legado/app/domain/model/AiModels.kt` — Đồng bộ AiProviderPresets entry
- `app/src/test/java/io/legado/app/data/repository/ai/OpenAiChatHandlerTest.kt` — Test header injection

## Test Criteria
- [ ] Unit test `openAiChatHeaders` cho URL `opencode.ai` kiểm tra đủ 5 headers.
- [ ] Unit test `openAiResponsesHeaders` cho URL `opencode.ai` kiểm tra tương tự.
- [ ] Provider khác (DeepSeek, Groq, NVIDIA) không bị inject headers lạ.
- [ ] Biên dịch Kotlin thành công.

---
Next Phase: [Phase 02: Tự động kích hoạt Gemini Web Free](phase-02-gemini-web-auto.md)
