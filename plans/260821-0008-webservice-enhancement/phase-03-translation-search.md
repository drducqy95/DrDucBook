# Phase 03: Translation Search Fix

Status: ✅ Completed
Dependencies: Phase 01 (i18n keys)

## Objective

Fix chức năng dịch từ khóa tiếng Việt sang tiếng Trung để tìm kiếm truyện. Thêm error feedback rõ ràng và fallback chain cho translation provider.

## Scope

| In Scope | Out of Scope |
|---|---|
| BookShelf.vue — error feedback + loading state | Thêm translation provider mới |
| WebServiceUiTranslationController.kt — fallback chain | Thay đổi SearchBooksUseCase |
| i18n.ts — thêm translation search keys | BookSearchWebSocket.kt (đã hoạt động) |

## Requirements

### Functional
- [ ] REQ-01: Khi dịch từ khóa fail → hiện `ElMessage.warning` với message cụ thể, không catch im lặng
- [ ] REQ-02: Hiện loading indicator "Đang dịch từ khóa..." khi đang chờ translation
- [ ] REQ-03: Backend fallback chain: LLM provider → Google Translate → trả lỗi rõ ràng
- [ ] REQ-04: Nếu translation trả text giống input (dịch thất bại ngầm) → warning + tìm bằng keyword gốc
- [ ] REQ-05: Backend trả HTTP 503 + error message khi tất cả provider fail (thay vì trả text gốc)

### Non-Functional
- [ ] NF-01: Translation timeout 120s (giữ nguyên)
- [ ] NF-02: Error message phải đủ thông tin để user biết cần làm gì (VD: "Chưa cấu hình AI provider")

## Implementation Steps

### Step 1: Backend — Fallback chain + error reporting
1. [ ] Sửa `WebServiceUiTranslationController.translate()`:
   - Khi target=zh: thử `TranslationConfig.llmProvider` → fallback `PROVIDER_GOOGLE` → log + throw
   - Detect "dịch thất bại ngầm": nếu output == input → thử provider tiếp
   - Trả `WebServiceUiTranslationResponse` với field `error` khi fail
2. [ ] Thêm logging cho từng provider attempt

### Step 2: Frontend — Error feedback + loading state
3. [ ] `BookShelf.vue`: Thêm `translating` ref → hiện loading spinner bên cạnh search bar
4. [ ] Thay `catch {}` im lặng → `catch (e) { ElMessage.warning(t('searchTranslationFailed')) }`
5. [ ] Check: nếu `translated === query` (dịch ra giống gốc) → warning + dùng keyword gốc

### Step 3: i18n keys
6. [ ] Thêm keys: `searchTranslationFailed`, `translatingKeyword`, `noTranslationProvider`

## Files to Create/Modify
- `app/src/main/java/io/legado/app/web/WebServiceUiTranslationController.kt` — Fallback chain
- `modules/web/src/views/BookShelf.vue` — Error feedback + loading
- `modules/web/src/i18n.ts` — Thêm keys

## Pass Criteria

- [ ] PASS-01: Bật toggle `中` → tìm "tu tiên" → nếu có provider → hiện "Từ khóa đã dịch: 修仙" + kết quả
- [ ] PASS-02: Nếu KHÔNG có provider → hiện warning message rõ ràng ("Chưa cấu hình...")
- [ ] PASS-03: Trong lúc đang dịch → hiện loading indicator
- [ ] PASS-04: Tìm bằng tiếng Trung trực tiếp (修仙) → vẫn hoạt động bình thường (bypass translation)
- [ ] PASS-05: `pnpm build` + `.\gradlew.bat assembleAppDebug` thành công
- [ ] PASS-06: Verify trên LDPlayer + browser

## Build Gate
```bash
cd modules/web && pnpm build
cd ../.. && .\gradlew.bat assembleAppDebug
adb connect 127.0.0.1:5555
adb install -r app/build/outputs/apk/app/debug/app-app-universal-debug.apk
```

---
Next Phase: → [phase-04-tts-voice.md](./phase-04-tts-voice.md)
