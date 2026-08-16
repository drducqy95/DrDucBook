# Phase 05 — TTS Settings & Model Management — Kế hoạch triển khai

Spec gốc: [../PHASE-05-TTS-MODEL-MANAGEMENT.md](../PHASE-05-TTS-MODEL-MANAGEMENT.md)
Wave: **1** (song song Phase 01, độc lập)
Ước lượng: 2–3 ngày

---

## 1. Mục tiêu

Tạo Compose MVI TTS Settings và Local TTS Model Manager: chọn engine (System/HTTP/Local),
import/validate/test model ONNX, chọn voice, nghe thử, điều chỉnh rate/pitch/interval,
hiển thị hướng dẫn cấu trúc gói model, service routing đúng engine/model/voice.

---

## 2. Trạng thái hiện tại

### Đã có

| Artifact | Trạng thái | Ghi chú |
|---|:---:|---|
| `ValtecOnnxTtsEngine.kt` | DONE | 9.7 KB, ONNX runtime |
| `LocalTtsModel.kt` | DONE | 1.2 KB, data model |
| `LocalTtsModelImporter.kt` | PARTIAL | 8 KB, import logic có nhưng cần security hardening |
| `LocalTtsModelRegistry.kt` | DONE | 6.3 KB, model registry |
| `VietnameseG2p.kt` | DONE | 7 KB |
| `TTSReadAloudService.kt` | DONE | System TTS service |
| `HttpReadAloudService.kt` | DONE | HTTP TTS service |
| `LocalTtsReadAloudService.kt` | PARTIAL | Local TTS service, cần verify resolve logic |
| `BaseReadAloudService.kt` | DONE | Base service |
| `ReadAloud.kt` | DONE | Model coordinator |
| `ReadTtsConfig.kt` | DONE | Existing settings (legacy) |

### Chưa có

| Artifact | Ưu tiên |
|---|:---:|
| `LocalTtsModelGateway.kt` | HIGH |
| `LocalTtsModelRepository.kt` | HIGH |
| `TestLocalTtsModelUseCase.kt` | HIGH |
| Thư mục `ui/config/tts/` (toàn bộ) | **CRITICAL** |
| Navigation routes TTS | HIGH |

---

## 3. Task chi tiết

### P05.T01 — Domain gateway & repository `[TODO]`

**Files:**
- `domain/gateway/LocalTtsModelGateway.kt` `[NEW]`
- `data/repository/LocalTtsModelRepository.kt` `[NEW]`

**Gateway interface:**
```kotlin
interface LocalTtsModelGateway {
    fun observeModels(): Flow<ImmutableList<LocalTtsModelInfo>>
    suspend fun importModel(uri: Uri): ImportResult
    suspend fun importModelFromUrl(manifestUrl: String): ImportResult
    suspend fun deleteModel(modelId: String): DeleteResult
    suspend fun testModel(modelId: String, voiceId: Int, testPhrase: String): TestResult
    suspend fun selectDefaultModel(modelId: String, voiceId: Int)
    fun getModelInfo(modelId: String): LocalTtsModelInfo?
}
```

**Repository:** delegate sang `LocalTtsModelRegistry` + `LocalTtsModelImporter` + `ValtecOnnxTtsEngine`

---

### P05.T02 — Test use case `[TODO]`

**File:** `domain/usecase/TestLocalTtsModelUseCase.kt` `[NEW]`

**Logic:**
1. Load model vào engine
2. Synthesize câu test ngắn (configurable)
3. Verify PCM: sample rate đúng, frame count > 0, không NaN/overflow
4. Phát preview qua audio focus tạm thời
5. Probe fail → rollback model cũ, giữ diagnostic redacted
6. Giải phóng session khi stop/đổi model

---

### P05.T03 — TTS Settings screen `[TODO → CRITICAL]`

**Files:**
- `ui/config/tts/TtsSettingsContract.kt` `[NEW]`
- `ui/config/tts/TtsSettingsViewModel.kt` `[NEW]`
- `ui/config/tts/TtsSettingsScreen.kt` `[NEW]`

**Contract:**
```kotlin
@Stable data class TtsSettingsUiState(
    val selectedEngine: TtsEngine = TtsEngine.SYSTEM,
    val localModels: ImmutableList<LocalTtsModelItem> = persistentListOf(),
    val selectedModelId: String? = null,
    val selectedVoiceId: Int? = null,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val paragraphInterval: Int = 300,
    val serviceStatus: ServiceStatus = ServiceStatus.IDLE,
    val lastError: String? = null,
    val isTesting: Boolean = false,
    val sheet: TtsSettingsSheet = TtsSettingsSheet.None,
)
enum class TtsEngine { SYSTEM, HTTP, LOCAL }
sealed interface TtsSettingsSheet {
    data object None : TtsSettingsSheet
    data object ModelGuide : TtsSettingsSheet
    data object ModelManager : TtsSettingsSheet
}
```

**Screen layout:**
1. Engine dropdown: System Android TTS / HTTP TTS / Local ONNX
2. Khi Local: model dropdown (search), voice dropdown, `Nghe thử`, `Quản lý model`, `Hướng dẫn nạp`
3. Sliders: rate (0.5–3.0), pitch (0.5–2.0), paragraph interval (0–2000ms)
4. Timer setting, follow-system toggle
5. Service status indicator, last error, repair shortcut
6. Dùng `ClickableSettingItem`, `SliderSettingItem`, `ListSettingItem`

---

### P05.T04 — TTS Model Manager screen `[TODO]`

**Files:**
- `ui/config/tts/TtsModelManagerContract.kt` `[NEW]`
- `ui/config/tts/TtsModelManagerViewModel.kt` `[NEW]`
- `ui/config/tts/TtsModelManagerScreen.kt` `[NEW]`

**Yêu cầu:**
1. Model card: name, language, engine type, size, sample rate, voice count, license, installed date
2. Actions per model: Test, Set default, Inspect files, Delete
3. Import: SAF file picker hoặc URL manifest (URL ZIP + SHA-256 + size + license)
4. Progress: bytes + giai đoạn (extract → validate → install → test)
5. Cancel: xóa staging, không ảnh hưởng model đang dùng
6. Delete model đang dùng → chọn fallback hoặc confirm chuyển System TTS
7. Empty state: hướng dẫn nạp model

---

### P05.T05 — Model guide sheet `[TODO]`

**File:** `ui/config/tts/TtsModelGuideSheet.kt` `[NEW]`

**Nội dung hiển thị:**
```
📦 Cấu trúc gói ZIP hỗ trợ:

model_name/
├── text_encoder.onnx
├── duration_predictor.onnx
├── flow.onnx
├── decoder.onnx
├── tts_config.json
└── LICENSE (tùy chọn)

📝 tts_config.json tối thiểu:
{
  "sample_rate": 24000,
  "speakers": {
    "Giọng nữ 1": 0,
    "Giọng nam 1": 1
  }
}
```

---

### P05.T06 — Navigation & DI `[TODO]`

**Files:**
- `ui/main/MainNavKey.kt` `[MODIFY]` — thêm `MainRouteTtsSettings`, `MainRouteTtsModelManager`
- `ui/main/MainNavGraph.kt` `[MODIFY]` — entry cho 2 screens
- `di/appModule.kt` `[MODIFY]` — viewModelOf + singleOf gateway/repository

---

### P05.T07 — Import security hardening `[PARTIAL → DONE]`

**File:** `model/tts/LocalTtsModelImporter.kt` `[MODIFY]`

**Yêu cầu:**
1. Chặn: absolute path, `..`, symlink, file ngoài whitelist (`.onnx`, `.json`, `LICENSE`)
2. Limits: entry count (≤20), per-entry size (≤2 GB), total extracted (≤4 GB)
3. SHA-256 trên 4 file ONNX bắt buộc
4. Extract vào staging dir → validate → atomic rename/copy
5. Model ID từ checksum → cùng checksum không cài trùng
6. Update cùng ID: giữ backup cho tới runtime test pass

---

### P05.T08 — Service integration `[PARTIAL → DONE]`

**Files:**
- `service/LocalTtsReadAloudService.kt` `[MODIFY]`
- `model/ReadAloud.kt` `[MODIFY]`

**Yêu cầu:**
1. `ReadAloud` resolve engine reference: `system`, `http:<profileId>`, `local-tts:<modelId>:<voiceId>`
2. `LocalTtsReadAloudService`: xác nhận model/voice còn tồn tại trước start
3. Rate/pitch/interval áp dụng nhất quán giữa System/HTTP/Local
4. Media notification, pause/resume/stop, chapter progress — không regression

---

## 5. Test bắt buộc

### Existing test (phải pass)

```powershell
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.service.TTSReadAloudProgressTest"
```

### Unit tests mới

| Test case | Priority |
|---|:---:|
| ZIP valid structure | HIGH |
| ZIP nested single root | HIGH |
| ZIP missing required file (mỗi file) | HIGH |
| Zip traversal (`../`) | CRITICAL |
| Zip duplicate filename | MEDIUM |
| Entry/total size limits | HIGH |
| Cancel → cleanup staging | HIGH |
| `tts_config.json` invalid JSON | HIGH |
| Missing speakers object | HIGH |
| Duplicate voice ID | MEDIUM |
| Invalid sample rate (0, negative, huge) | HIGH |
| Same checksum dedupe | HIGH |
| Failed update → rollback old model | HIGH |
| Engine reference parse/serialize | HIGH |
| Deleted model → fallback | HIGH |
| Service selection System/HTTP/Local | HIGH |
| Rate/pitch boundary (min/max clamp) | MEDIUM |
| Model manager state: import/test/delete/process recreation | MEDIUM |

### Nox smoke test

| # | Kịch bản |
|:---:|---|
| 1 | Mở TTS Settings từ Cài đặt |
| 2 | Xem hướng dẫn cấu trúc package |
| 3 | Import model fixture, chọn voice, nghe thử |
| 4 | Đổi rate/pitch/interval → đọc chapter |
| 5 | Pause/resume/stop từ notification |
| 6 | Xóa model đang dùng → fallback an toàn |
| 7 | Import gói lỗi → báo đúng file/config, không staging rác |

---

## 6. Điều kiện đóng phase

- [x] TTS Settings và model manager truy cập được, không còn chức năng ẩn
- [x] User biết chính xác file/config cần nạp
- [x] Model chỉ cài sau validate + runtime probe
- [x] Đọc chapter bằng local voice hoạt động và service controls không regression

---

## 7. Trạng thái thực thi 2026-07-26

| Task | Trạng thái | Ghi chú |
|---|---|---|
| P05.T01 | DONE | Gateway/repository quan sát model, import, delete, test và chọn mặc định. |
| P05.T02 | DONE | `TestLocalTtsModelUseCase` kiểm tra câu rỗng, PCM frame, sample rate và NaN/Infinity. |
| P05.T03 | DONE | Cài đặt đọc là entry TTS canonical cho rate/interval/System/HTTP/Local và nối thẳng sang Model Manager. |
| P05.T04 | DONE | Compose MVI Model Manager có loading/empty, import, voice picker, test, default, metadata, delete. |
| P05.T05 | DONE | Guide hiển thị song song ZIP Valtec nhiều ONNX và ZIP Piper đúng một cặp `.onnx + .onnx.json`; token/metadata Piper được tạo tự động. |
| P05.T06 | DONE | Route `settings/read/tts_models`, Navigation 3 và Koin DI đã nối. |
| P05.T07 | DONE | Import có byte/stage progress, cancel coroutine, probe runtime trước commit, atomic rollback và test cleanup staging 100 vòng. |
| P05.T08 | DONE | Valtec/Piper dùng chung synthesis engine; local service có cache WAV, pause/resume/stop, audio focus, rate/interval và giải phóng native session. Piper `banmai` pass runtime trên LDPlayer. |

LDPlayer `127.0.0.1:5555`: route, SAF, import Piper 2 file, eSpeak-ng, sherpa-onnx init/test pass; không có AndroidRuntime/CrashReport mới. Phase 05 đã đóng.
