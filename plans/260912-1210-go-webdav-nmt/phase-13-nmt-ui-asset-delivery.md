# Phase 13: Model Selector UI & Asset Delivery Catalog (Track B - NMT)

Status: ⬜ Pending
Dependencies: `phase-12-nmt-multimodel-runtime.md`

## Objective
Nâng cấp giao diện cài đặt dịch `TranslationConfigScreen.kt` để hiển thị bộ chọn mô hình NMT (Model Selector) khi chọn provider là NMT, hiển thị trạng thái đã cài đặt / chưa cài đặt, tự động áp dụng các tham số tối ưu (ví dụ tắt no-repeat bigram khi chọn bản QT), hiển thị cảnh báo thông minh nếu người dùng cố bật no-repeat bigram trên bản QT, và đăng ký artifact mới vào `ExternalAssetCatalog` cùng pipeline Asset Delivery.

## Scope
| In Scope | Out of Scope |
|---|---|
| Model Selector dropdown / list item trong `TranslationConfigScreen.kt` | Device performance benchmarks (Phase 14) |
| Hiển thị trạng thái cài đặt (Đã tải / Chưa tải) kèm nút Tải / Nhập ZIP | Sửa đổi các provider AI khác (Cloud/Local) |
| Tự động điều chỉnh `nmtNoRepeatBigram = false` khi chọn model QT | |
| Cảnh báo UI khi bật no-repeat bigram trên model QT | |
| Đăng ký `translation-hachimi-qt-onnx` trong `ExternalAssetCatalog.kt` | |
| Cập nhật `AssetDeliveryImportRepository.kt` để route model ZIP | |
| Thêm strings đa ngôn ngữ (vi, zh, en) | |

## Requirements
### Functional
- [ ] REQ-13.1: Giao diện Model Selector tại `TranslationConfigScreen.kt`:
  - Khi `TranslationConfig.llmProvider == TranslationConfig.PROVIDER_NMT`:
    - Hiển thị mục cài đặt "Mô hình NMT" cho phép chọn giữa:
      1. `HachimiMT-60 zh→vi` (Văn phong dịch tự nhiên, mượt mà)
      2. `HachimiMT-60-QT zh→vi` (Văn phong Hán Việt / QuickTranslator / Tiên hiệp)
    - Hiển thị icon trạng thái bên cạnh từng model: Đã cài đặt (Checkmark xanh) hoặc Chưa tải (Icon Download).
    - Nếu chọn model chưa tải: Hiển thị ngay nút "Tải về" (qua Asset Delivery hoặc trình duyệt) và nút "Nhập file ZIP" thủ công.
- [ ] REQ-13.2: Tự động điều chỉnh tham số giải mã (Smart Auto-Config):
  - Khi người dùng chuyển sang model `hachimi_mt60_qt_zh_vi`:
    - Tự động chuyển `nmtNoRepeatBigram` thành `false` (`noRepeatNgramSize = 0`).
  - Nếu người dùng cố tình bật lại `nmtNoRepeatBigram` khi đang dùng model QT:
    - Hiển thị cảnh báo màu cam bên dưới: *"Mô hình QT sử dụng nhiều từ ngữ Hán Việt lặp tự nhiên. Bật chặn lặp từ có thể làm hỏng câu dịch."*
- [ ] REQ-13.3: Dialog thông tin mô hình (Model Metadata Dialog):
  - Hiển thị thông tin chi tiết: Tên model, tác giả (Ngọc Đặng), nguồn (HuggingFace `ngocdang83/HachimiMT-60-QT`), giấy phép (CC-BY-4.0), số tham số, dung lượng.
- [ ] REQ-13.4: Đăng ký Asset Delivery:
  - Thêm artifact `translation-hachimi-qt-onnx` trong `ExternalAssetCatalog.kt`:
    - `id = "translation-hachimi-qt-onnx"`
    - `displayName = "HachimiMT-60-QT ONNX (zh→vi, QT)"`
    - `downloadUrl = "https://huggingface.co/Drduc/legado-models/resolve/main/hachimi-mt60-qt-zh-vi-onnx.zip"`
  - `AssetDeliveryImportRepository.kt`:
    - Nhận diện category `TRANSLATION` và artifact ID của QT model.
    - Gọi `HachimiOnnxModelImporter.import(context, uri)` để giải nén atomic vào đúng thư mục `hachimi_mt60_qt_zh_vi`.

### Non-Functional
- [ ] NF-13.1: Giao diện mượt mà, phản hồi ngay lập tức khi chuyển đổi giữa các model.
- [ ] NF-13.2: Toàn bộ text trên giao diện được định nghĩa trong `strings.xml` và dịch đầy đủ tiếng Việt.

## Implementation Steps
### Step 1: Đăng ký Asset Delivery Catalog
1. [ ] Cập nhật `app/src/main/java/io/legado/app/domain/model/ExternalAssetCatalog.kt`:
   - Thêm `hachimiQtOnnxAssetId`
   - Thêm `ExternalPackageAsset` cho QT model
2. [ ] Cập nhật `app/src/main/java/io/legado/app/data/repository/AssetDeliveryImportRepository.kt`:
   - Định tuyến import cho QT model ID

### Step 2: Cập nhật UI Components trong TranslationConfigScreen
3. [ ] Cập nhật `app/src/main/java/io/legado/app/ui/config/translation/TranslationConfigScreen.kt`:
   - Thêm `ModelSelectorItem` hiển thị danh sách từ `NmtModelCatalog.models`
   - Hiển thị badge trạng thái `isInstalled(model.id)`
   - Xử lý sự kiện click chọn model: cập nhật `TranslationConfig.nmtModelId` và auto-toggle `nmtNoRepeatBigram`
   - Thêm cảnh báo khi bật no-repeat bigram trên model QT

### Step 3: Thêm Resource Strings
4. [ ] Cập nhật `app/src/main/res/values/strings.xml` và `values-vi/strings.xml`:
   - `nmt_model_selection`, `nmt_model_hachimi_standard`, `nmt_model_hachimi_qt`, `nmt_qt_no_repeat_warning`, v.v.

### Step 4: Kiểm tra Giao Diện
5. [ ] Mở màn hình Cài đặt dịch trong app trên LDPlayer
6. [ ] Chọn NMT provider -> Kiểm tra bộ chọn mô hình xuất hiện đầy đủ
7. [ ] Thử chọn model QT -> kiểm tra switch no-repeat bigram tự tắt và hiện cảnh báo nếu bật

## Files to Create/Modify
- `app/src/main/java/io/legado/app/domain/model/ExternalAssetCatalog.kt` — Add QT asset descriptor
- `app/src/main/java/io/legado/app/data/repository/AssetDeliveryImportRepository.kt` — Import routing for QT
- `app/src/main/java/io/legado/app/ui/config/translation/TranslationConfigScreen.kt` — Model selector UI
- `app/src/main/res/values/strings.xml` & `values-vi/strings.xml` — Localization strings

## Test Criteria
- [ ] PASS-13.1: Giao diện `TranslationConfigScreen` hiển thị cả hai model Hachimi với trạng thái cài đặt chính xác.
- [ ] PASS-13.2: Bấm tải hoặc nhập file ZIP model QT -> tiến trình giải nén thành công và model chuyển sang trạng thái "Đã cài đặt".
- [ ] PASS-13.3: Chuyển sang model QT tự động tắt no-repeat bigram; cố tình bật lên hiển thị cảnh báo trực quan.

---
Next Phase: `phase-14-nmt-validation-benchmark.md`
