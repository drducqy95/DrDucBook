# Phase 11: INT8 Quantization, Packaging & HuggingFace Upload (Track B - NMT)

Status: ⬜ Pending
Dependencies: `phase-10-nmt-export-prototype.md`

## Objective
Thực hiện lượng tử hóa động INT8 (Dynamic Quantization) trên mô hình FP32 để giảm kích thước và tăng tốc độ suy luận trên CPU di động (ARM64/x86_64), đo lường độ suy hao chất lượng bản dịch, sinh `model_manifest.json` chuẩn v2, đóng gói file ZIP hoàn chỉnh kèm giấy phép CC-BY-4.0, và upload artifact lên HuggingFace repository.

## Scope
| In Scope | Out of Scope |
|---|---|
| INT8 Dynamic Quantization trên `encoder_model.onnx` và `decoder_model_merged.onnx` | Android Kotlin runtime integration (Phase 12) |
| Đo lường độ suy hao chất lượng FP32 vs INT8 | UI Model Selector (Phase 13) |
| Sinh `model_manifest.json` chuẩn version 2 | Device benchmark (Phase 14) |
| Script PowerShell đóng gói `package_hachimi_qt.ps1` | |
| Tạo `NOTICE.txt` và `LICENSE` ghi rõ nguồn gốc CC-BY-4.0 | |
| Upload artifact ZIP lên repository HuggingFace | |

## Requirements
### Functional
- [ ] REQ-11.1: Lượng tử hóa INT8:
  - Áp dụng `onnxruntime.quantization.quantize_dynamic` với `weight_type=QuantType.QInt8`.
  - Giữ nguyên tokenizer và detokenizer (không lượng tử hóa do chỉ chứa chuỗi và bảng tra token).
  - Dung lượng file sau khi lượng tử hóa và nén ZIP đạt mức tối ưu: ~60-80MB (so với ~250MB bản FP32).
- [ ] REQ-11.2: Đánh giá chất lượng sau lượng tử hóa:
  - So sánh kết quả dịch INT8 vs FP32 trên 30 câu Golden Test Set.
  - Tỷ lệ sai khác câu từ < 2%, ngữ nghĩa và các thuật ngữ Hán Việt giữ nguyên 100%.
- [ ] REQ-11.3: Sinh `model_manifest.json` định dạng chuẩn v2:
  ```json
  {
    "schemaVersion": 2,
    "modelId": "hachimi_mt60_qt_zh_vi",
    "displayName": "HachimiMT-60-QT zh→vi (QT/Hán Việt)",
    "sourceRepo": "ngocdang83/HachimiMT-60-QT",
    "sourceRevision": "<pinned_sha>",
    "sourceLanguage": "zh",
    "targetLanguage": "vi",
    "style": "QT/convert_register",
    "license": "CC-BY-4.0",
    "attribution": "ngocdang83/HachimiMT-60-QT (CC-BY-4.0)",
    "requiredFiles": [
      "encoder_model.onnx",
      "decoder_model_merged.onnx",
      "tokenizer.onnx",
      "target_tokenizer.onnx",
      "detokenizer.onnx"
    ],
    "sha256": {
      "encoder_model.onnx": "...",
      "decoder_model_merged.onnx": "...",
      "tokenizer.onnx": "...",
      "target_tokenizer.onnx": "...",
      "detokenizer.onnx": "..."
    },
    "recommendedNoRepeatNgramSize": 0,
    "supportsSourcePrompt": false,
    "decoderLayers": 2,
    "attentionHeads": 8,
    "headDimension": 64,
    "decoderStartTokenId": 1,
    "eosTokenId": 2,
    "specialTokenIds": [0, 1, 2, 3]
  }
  ```
- [ ] REQ-11.4: Bản quyền & Ghi công:
  - File `NOTICE.txt`: Ghi nhận model gốc `ngocdang83/HachimiMT-60-QT` và tác giả Ngọc Đặng theo điều khoản giấy phép Creative Commons Attribution 4.0 International (CC-BY-4.0).
  - File `LICENSE`: Bản đầy đủ giấy phép CC-BY-4.0.
- [ ] REQ-11.5: Đóng gói và Upload HuggingFace:
  - Đóng gói file ZIP: `hachimi-mt60-qt-zh-vi-onnx.zip`.
  - Tính toán SHA-256 của toàn bộ file ZIP và từng file bên trong.
  - Upload lên repository HuggingFace của dự án (ví dụ `Drduc/legado-models`).
  - Ghi nhận URL tải trực tiếp: `https://huggingface.co/Drduc/legado-models/resolve/main/hachimi-mt60-qt-zh-vi-onnx.zip`.

### Non-Functional
- [ ] NF-11.1: Quá trình đóng gói và tính checksum có thể tái hiện 100% bằng script tự động.

## Implementation Steps
### Step 1: Script Lượng Tử Hóa
1. [ ] Mở rộng `tools/nmt-onnx/quantize.py`:
   - Thực hiện quantize dynamic encoder và decoder
   - Xuất model ra thư mục `dist/hachimi_mt60_qt_zh_vi/`

### Step 2: Đánh Giá Chất Lượng INT8
2. [ ] Chạy `validate_hachimi_qt.py --quantized` để đối chiếu với output FP32
3. [ ] Xuất báo cáo sai khác (nếu có) ra `dist/quantization_report.txt`

### Step 3: Đóng Gói Manifest & License
4. [ ] Viết script `tools/nmt-onnx/package_hachimi_qt.ps1`:
   - Tính toán SHA-256 từng file ONNX
   - Điền các trường vào `model_manifest.json`
   - Copy `NOTICE.txt` và `LICENSE`
   - Tạo file nén `hachimi-mt60-qt-zh-vi-onnx.zip`
   - Tính SHA-256 của file ZIP

### Step 4: Upload HuggingFace & Ghi Nhận Artifact
5. [ ] Upload file ZIP lên HuggingFace repository
6. [ ] Cập nhật manifest nội bộ `supabase/artifacts/hf-artifacts-manifest.json` và `ExternalAssetCatalog.kt`

## Files to Create/Modify
- `tools/nmt-onnx/quantize.py` — Dynamic quantization script
- `tools/nmt-onnx/package_hachimi_qt.ps1` — Packaging and checksum script
- `tools/nmt-onnx/NOTICE.txt` — Attribution notice (CC-BY-4.0)
- `tools/nmt-onnx/LICENSE` — CC-BY-4.0 full text
- `supabase/artifacts/hf-artifacts-manifest.json` — Asset catalog manifest

## Test Criteria
- [ ] PASS-11.1: File ZIP sau lượng tử hóa có kích thước < 85MB.
- [ ] PASS-11.2: Toàn bộ 5 file `.onnx` trong file ZIP đều có checksum khớp chính xác với `model_manifest.json`.
- [ ] PASS-11.3: Link download HuggingFace tải về bình thường và verify SHA-256 thành công.

---
Next Phase: `phase-12-nmt-multimodel-runtime.md`
