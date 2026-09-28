# Phase 12: Multi-Model Runtime & Cache Isolation (Track B - NMT)

Status: ⬜ Pending
Dependencies: `phase-11-nmt-quantize-package.md`

## Objective
Tái cấu trúc kiến trúc NMT Runtime hiện tại (vốn chỉ hỗ trợ 1 model hardcoded `hachimi_onnx`) thành hệ thống đa mô hình (Multi-Model System): hỗ trợ đăng ký mô hình linh hoạt qua `NmtModelCatalog` và `NmtModelDescriptor`, luân chuyển mô hình an toàn bộ nhớ trong tiến trình `:nmt_onnx`, chuyển giao `modelId` xuyên suốt IPC Messenger Bundle, và cách ly cache bản dịch giữa các mô hình khác nhau.

## Scope
| In Scope | Out of Scope |
|---|---|
| Mở rộng `NmtDecodeConfig` với trường `modelId: String` | Giao diện chọn model trong Compose (Phase 13) |
| Định nghĩa `NmtModelDescriptor` và `NmtModelCatalog` | Asset delivery import flow (Phase 13) |
| Cập nhật `HachimiOnnxModelRegistry` hỗ trợ nhiều thư mục model | Device performance benchmarks (Phase 14) |
| Cập nhật `HachimiOnnxModelImporter` import vào đúng thư mục | |
| Cập nhật `HachimiOnnxRuntimeCoordinator` và `HachimiOnnxTranslator` | |
| Cập nhật `NmtOnnxService` và `NmtTranslationRepository` | |
| Cập nhật `TranslateChapterUseCase` với cache isolation | |

## Requirements
### Functional
- [ ] REQ-12.1: `NmtDecodeConfig`:
  - Bổ sung trường `val modelId: String = "hachimi_onnx"` (giữ mặc định tương thích ngược).
  - Gson serialize / deserialize xuyên qua `Bundle` trong IPC không làm mất trường này.
- [ ] REQ-12.2: Danh mục mô hình `NmtModelCatalog`:
  - `hachimi_onnx`: Model gốc HachimiMT-60 zh->vi, `recommendedNoRepeatNgramSize = 2`, attribution: `"HachimiMT-60 zh→vi"`.
  - `hachimi_mt60_qt_zh_vi`: Model QT Hán Việt, `recommendedNoRepeatNgramSize = 0`, attribution: `"HachimiMT-60-QT (CC-BY-4.0)"`.
- [ ] REQ-12.3: Thư mục cài đặt `HachimiOnnxModelRegistry`:
  - Thay đổi đường dẫn cài đặt từ cố định `filesDir/nmt_models/hachimi_onnx` thành theo `modelId`: `filesDir/nmt_models/<modelId>`.
  - Giữ nguyên tương thích ngược: nếu người dùng đã có `hachimi_onnx` cài từ trước, model vẫn được nhận diện nguyên vẹn.
  - Đọc `model_manifest.json` của từng model để lấy metadata và thông số giải mã.
- [ ] REQ-12.4: Nhập file ZIP `HachimiOnnxModelImporter`:
  - Đọc `model_manifest.json` trong file ZIP trước khi giải nén để xác định `modelId`.
  - Di chuyển atomic vào đúng thư mục `${modelId}` mà không ghi đè model khác.
- [ ] REQ-12.5: Điều phối bộ nhớ `HachimiOnnxRuntimeCoordinator`:
  - Theo dõi `currentModelId`. Khi nhận yêu cầu dịch với `modelId` khác mô hình đang nạp:
    - Giải phóng toàn bộ OrtSession của model cũ để thu hồi native memory.
    - Tăng `generation` để vô hiệu hóa các tham chiếu cũ.
    - Nạp 5 đồ thị ONNX của model mới.
  - Đảm bảo tại một thời điểm chỉ có tối đa 1 mô hình nằm trên bộ nhớ RAM tiến trình `:nmt_onnx`.
- [ ] REQ-12.6: Cách ly Cache bản dịch (`TranslateChapterUseCase`):
  - Cập nhật hàm `providerConfigurationRevision()`:
    ```kotlin
    "${TranslationConfig.provider}|${TranslationConfig.nmtModelId}|${currentNmtDecodeConfig()}"
    ```
  - Đảm bảo khi đổi từ model Hachimi thường sang Hachimi-QT, cache bản dịch của chương cũ không bị nhầm lẫn giữa hai phong cách dịch.
- [ ] REQ-12.7: Cấu hình preferences:
  - Thêm `PreferKey.nmtModelId = "nmt_model_id"`
  - Thêm `TranslationConfig.nmtModelId` (mặc định `"hachimi_onnx"`).

### Non-Functional
- [ ] NF-12.1: Quá trình chuyển đổi giữa hai model diễn ra an toàn dưới mutex, không xảy ra race condition hay crash bộ nhớ.
- [ ] NF-12.2: Toàn bộ các unit test hiện có của `TranslateChapterUseCase` và `HachimiOnnxTranslator` tiếp tục pass 100%.

## Implementation Steps
### Step 1: Cập nhật Config & Descriptor Models
1. [ ] Cập nhật `app/src/main/java/io/legado/app/domain/gateway/NmtTranslationGateway.kt`: thêm `modelId` vào `NmtDecodeConfig`
2. [ ] Tạo `app/src/main/java/io/legado/app/model/translation/NmtModelDescriptor.kt`
3. [ ] Tạo `app/src/main/java/io/legado/app/model/translation/NmtModelCatalog.kt`
4. [ ] Cập nhật `PreferKey.kt` và `TranslationConfig.kt`

### Step 2: Mở rộng Model Registry & Importer
5. [ ] Cập nhật `HachimiOnnxModelRegistry.kt`:
   - Thêm các overload method nhận `modelId: String`
   - Đọc `model_manifest.json` theo từng thư mục model
6. [ ] Cập nhật `HachimiOnnxModelImporter.kt`:
   - Trích xuất manifest từ staging và chuyển file vào thư mục của `modelId` tương ứng

### Step 3: Quản lý Runtime & IPC Service
7. [ ] Cập nhật `HachimiOnnxRuntimeCoordinator.kt`:
   - Bổ sung `currentModelId: String?` và logic chuyển đổi runtime an toàn
8. [ ] Cập nhật `HachimiOnnxTranslator.kt`:
   - Nhận `modelId` từ `NmtDecodeConfig`
   - Điều chỉnh decoder parameters dựa trên manifest hoặc catalog
   - Gán `attribution` từ descriptor của model được chọn
9. [ ] Cập nhật `NmtOnnxService.kt`:
   - Parse `modelId` từ `config_json` trong bundle và chuyển cho coordinator

### Step 4: Cập nhật Cache Isolation trong UseCase
10. [ ] Cập nhật `TranslateChapterUseCase.kt`:
    - Đưa `TranslationConfig.nmtModelId` vào `currentNmtDecodeConfig()`
    - Đưa `modelId` vào `providerConfigurationRevision()`

### Step 5: Unit Test Đa Mô Hình
11. [ ] Viết unit test mô phỏng switch model: nạp model 1 -> dịch -> nạp model 2 -> dịch -> xác nhận output và attribution khác nhau
12. [ ] Viết unit test xác nhận cache key thay đổi khi đổi `nmtModelId`

## Files to Create/Modify
- `app/src/main/java/io/legado/app/domain/gateway/NmtTranslationGateway.kt` — Update NmtDecodeConfig
- `app/src/main/java/io/legado/app/model/translation/NmtModelDescriptor.kt` — Model descriptor
- `app/src/main/java/io/legado/app/model/translation/NmtModelCatalog.kt` — Model catalog
- `app/src/main/java/io/legado/app/model/translation/HachimiOnnxModelRegistry.kt` — Multi-model registry
- `app/src/main/java/io/legado/app/model/translation/HachimiOnnxModelImporter.kt` — Multi-model importer
- `app/src/main/java/io/legado/app/model/translation/HachimiOnnxRuntimeCoordinator.kt` — Runtime switching
- `app/src/main/java/io/legado/app/data/repository/HachimiOnnxTranslator.kt` — Model-aware translation
- `app/src/main/java/io/legado/app/service/NmtOnnxService.kt` — IPC model routing
- `app/src/main/java/io/legado/app/data/repository/NmtTranslationRepository.kt` — Dynamic attribution
- `app/src/main/java/io/legado/app/domain/usecase/TranslateChapterUseCase.kt` — Cache isolation
- `app/src/main/java/io/legado/app/constant/PreferKey.kt` — Preference keys
- `app/src/main/java/io/legado/app/ui/config/translation/TranslationConfig.kt` — Configuration properties

## Test Criteria
- [ ] PASS-12.1: `.\gradlew.bat test --tests "io.legado.app.model.translation.*"` vượt qua 100% tests.
- [ ] PASS-12.2: Thay đổi `nmtModelId` sinh ra hash revision khác nhau trong `TranslateChapterUseCase`.
- [ ] PASS-12.3: Switch model không rò rỉ bộ nhớ native của ONNX Runtime.

---
Next Phase: `phase-13-nmt-ui-asset-delivery.md`
