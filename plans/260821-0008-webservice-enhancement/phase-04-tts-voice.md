# Phase 04: TTS Voice Selection & Speech Rate

Status: ✅ Complete
Dependencies: Phase 01 (i18n keys)

## Objective

Thêm chức năng chọn giọng đọc TTS và tùy chỉnh tốc độ đọc TTS trong cài đặt WebService. Backend endpoints mới để list/select models + voices + speech rate, frontend UI với dropdown + speech rate slider + preview audio.

## Scope

| In Scope | Out of Scope |
|---|---|
| Backend: `models()` + `selectModel()` endpoints with speechRate & ttsFollowSys | Download/import model mới từ web |
| KtorServer.kt: 2 routes mới | Native SpeakEngineConfigSheet changes |
| Local TTS synthesis speed support (Piper/Valtec/Android TTS) | Voice catalog browsing (HuggingFace) |
| webService.ts: types + functions with speech rate | Per-book voice selection |
| WebServiceSettings.vue: TTS voice & speech rate section | |
| i18n.ts: TTS & speech rate keys | |

## Requirements

### Functional
- [x] REQ-01: `GET /api/v2/tts/models` trả danh sách TTS engines grouped by type:
  - System TTS (Android default + installed engines)
  - Local ONNX models (Piper/Valtec) with voices list
  - HttpTTS rules
  - `speechRate` và `ttsFollowSys`
- [x] REQ-02: `POST /api/v2/tts/models/select` set global TTS engine + speech rate
- [x] REQ-03: WebServiceSettings.vue section "Giọng đọc TTS":
  - Dropdown chọn engine/model (grouped by type)
  - Sub-dropdown chọn voice (khi model multi-voice)
  - Cài đặt tốc độ đọc với Switch "Theo hệ thống" và Slider dải 0.5x - 2.0x
  - Metadata badges: engine type, language
  - Nút "Nghe thử" → synthesize "Xin chào, đây là giọng đọc mẫu" → play audio
- [x] REQ-04: Hiện engine đang active với badge "Đang dùng"
- [x] REQ-05: Sau khi select → TTS trên web reader và synthesize dùng giọng và tốc độ mới ngay

### Non-Functional
- [x] NF-01: Models API response ≤ 1 giây
- [x] NF-02: Preview synthesis timeout ≤ 30 giây

## Implementation Steps

### Step 1: Backend — WebServiceTtsController.kt
1. [x] Thêm `fun models(): WebServiceTtsModelsResponse`:
   - List system TTS engines via Android `TextToSpeech.getEngines()`
   - List local ONNX models via `LocalTtsModelRegistry(appCtx).list()` + voices
   - List HttpTTS rules via `appDb.httpTTSDao.all`
   - Map to `WebServiceTtsModelResponse` DTOs
   - Include `selectedEngine`, `speechRate`, `ttsFollowSys`
2. [x] Thêm `fun selectModel(request: WebServiceTtsModelSelectRequest)`:
   - Set `ReadConfig.ttsEngine`, `ReadConfig.ttsSpeechRate`, `ReadConfig.ttsFollowSys`
   - Update `LocalTtsSynthesis` & engine synthesis with `speed` parameter

### Step 2: Backend — KtorServer.kt
3. [x] Thêm route `GET /api/v2/tts/models`
4. [x] Thêm route `POST /api/v2/tts/models/select`

### Step 3: Frontend — webService.ts
5. [x] Thêm types: `WebServiceTtsModel`, `WebServiceTtsVoice`, `WebServiceTtsModelsResponse` (với `speechRate`, `ttsFollowSys`)
6. [x] Thêm `getWebServiceTtsModels()` function
7. [x] Thêm `selectWebServiceTtsModel(modelId, voiceId?, speechRate?, ttsFollowSys?)` function

### Step 4: Frontend — WebServiceSettings.vue
8. [x] Thêm section "Giọng đọc TTS" sau section "Chức năng":
   - `el-select` cho engine/model (grouped optgroups: System/Local/HTTP)
   - Conditional `el-select` cho voice (chỉ hiện khi model có ≥2 voices)
   - Switch "Theo hệ thống" + Slider tốc độ đọc (0.5x - 2.0x)
   - Metadata display: engine type tag, language
   - "Nghe thử" button: call `synthesizeWebServiceTts("Xin chào...")` → `<audio>` play
9. [x] Load models on mount, auto-select current engine & speed
10. [x] On change → call `selectWebServiceTtsModel`

### Step 5: i18n keys
11. [x] Thêm keys: `ttsVoice`, `ttsVoiceDescription`, `ttsEngine`, `selectVoice`, `previewVoice`, `systemTts`, `localModel`, `httpTts`, `noTtsModels`, `currentEngine`, `previewText`, `speechRate`, `followSystemSpeed`, `speechRateSaved`

## Files to Create/Modify
- `app/src/main/java/io/legado/app/web/WebServiceTtsController.kt` — Models, selectModel, synthesize speed
- `app/src/main/java/io/legado/app/model/tts/LocalTtsSynthesis.kt` — Speed parameter in synthesis & cache key
- `app/src/main/java/io/legado/app/model/tts/PiperOnnxTtsEngine.kt` — Synthesis speed
- `app/src/main/java/io/legado/app/model/tts/ValtecOnnxTtsEngine.kt` — Synthesis speed lengthScale
- `app/src/main/java/io/legado/app/web/KtorServer.kt` — TTS endpoints
- `modules/web/src/api/webService.ts` — Types + API functions
- `modules/web/src/views/WebServiceSettings.vue` — TTS UI & speech rate slider
- `modules/web/src/i18n.ts` — TTS & speech rate keys

## Pass Criteria

- [x] PASS-01: `GET /api/v2/tts/models` trả JSON chứa models array với ≥1 model
- [x] PASS-02: Mở WebService Settings → thấy section "Giọng đọc TTS"
- [x] PASS-03: Dropdown hiện danh sách engines/models grouped by type
- [x] PASS-04: Chọn model có nhiều voices → sub-dropdown voices hiện ra
- [x] PASS-05: Cài đặt tốc độ đọc hỗ trợ chuyển đổi Theo hệ thống / Tùy chỉnh (0.5x - 2.0x)
- [x] PASS-06: Click "Nghe thử" → tổng hợp và phát audio mẫu thành công
- [x] PASS-07: `npm run build` + `.\gradlew.bat assembleAppDebug` thành công
- [x] PASS-08: Verify thực tế trên LDPlayer + Chrome browser

## Build Gate
```bash
cd modules/web && pnpm build
cd ../.. && .\gradlew.bat assembleAppDebug
adb connect 127.0.0.1:5555
adb install -r app/build/outputs/apk/app/debug/app-app-universal-debug.apk
```

---
Next Phase: → [phase-05-provider-cache.md](./phase-05-provider-cache.md)
