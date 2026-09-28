# Phase 02: Lượng Tử Hóa Model & Đóng Gói INT8

Trạng thái: ⬜ Chờ
Phụ thuộc: Phase 01
Dự kiến tăng tốc thêm: **1.5–2.5 lần** (cộng dồn với Phase 01 → tổng ~5–10 lần)
Dự kiến giảm dung lượng: **~903 MB → ~500 MB** (~45% nhỏ hơn)

## Mục Tiêu

Áp dụng lượng tử hóa Mixed-Precision Dynamic INT8 lên 3 mô hình Transformer (chỉ quantize `MatMul`/`Gemm`), giữ nguyên Vocoder ở FP32 để bảo toàn chất lượng giọng đọc. Đóng gói lại và tải lên HuggingFace **thay thế** bản FP32 cũ.

## Phân Tích Độ Nhạy Lượng Tử Hóa Theo Từng Khối

```
┌──────────────────────────────────┬──────────────────────┬───────────────────────────────────────────┐
│ Mô hình                          │ Phương án             │ Lý do                                     │
├──────────────────────────────────┼──────────────────────┼───────────────────────────────────────────┤
│ text_encoder.onnx (308 MB)       │ ✅ INT8 Dynamic       │ Transformer text, độ nhạy thấp.           │
│                                  │ (MatMul/Gemm only)   │ → ~170 MB (-45%)                          │
├──────────────────────────────────┼──────────────────────┼───────────────────────────────────────────┤
│ prefix_step.onnx (332 MB)        │ ✅ INT8 Dynamic       │ NGHẼN LỚN NHẤT (51×/câu). Lượng tử hóa   │
│                                  │ (MatMul/Gemm only)   │ MatMul cải thiện rõ rệt. → ~185 MB        │
├──────────────────────────────────┼──────────────────────┼───────────────────────────────────────────┤
│ local_frame_decode.onnx (178 MB) │ ✅ INT8 Dynamic       │ NGHẼN LỚN THỨ HAI (50×/câu). Giữ         │
│                                  │ (MatMul/Gemm only)   │ Softmax/Sampling ở FP32. → ~95 MB         │
├──────────────────────────────────┼──────────────────────┼───────────────────────────────────────────┤
│ moss_audio_tokenizer_* (~45 MB)  │ ⛔ GIỮ FP32           │ CỰC NHẠY: Vocoder ConvTranspose1d.        │
│                                  │                      │ INT8 gây rè/vỡ tiếng kim loại.            │
└──────────────────────────────────┴──────────────────────┴───────────────────────────────────────────┘
```

> **CẢNH BÁO**: KHÔNG được lượng tử hóa `moss_audio_tokenizer_decode_full.onnx`. Các thử nghiệm thực tế trong ngành (VITS, Piper, HiFi-GAN) đã chứng minh INT8 lên ConvTranspose1d của vocoder luôn gây ra tạp âm kim loại rất khó chịu.

## Yêu Cầu

### Chức năng
- [ ] Pipeline Python lượng tử hóa 3 model transformer sang INT8
- [ ] Script kiểm tra chất lượng âm thanh giữa FP32 và INT8
- [ ] Script đóng gói tạo file `legado-tts-zerotts-base.zip` mới (thay thế bản cũ)
- [ ] Android engine tự nhận diện và nạp file `.int8.onnx` nếu có
- [ ] Tải lên HuggingFace thay thế bản FP32

### Phi chức năng
- [ ] Chất lượng giọng nói INT8 không có sai biệt nghe được so với FP32
- [ ] Dung lượng zip cuối cùng ≤ 550 MB

## Các Bước Triển Khai

### 1. [ ] Tạo công cụ Python lượng tử hóa

**Tệp mới**: `tools/zerotts-quant/pyproject.toml` + `tools/zerotts-quant/quantize_zerotts.py`

```python
from onnxruntime.quantization import quantize_dynamic, QuantType

TRANSFORMER_MODELS = [
    "text_encoder.onnx",
    "prefix_step.onnx",
    "local_frame_decode.onnx",
]
SKIP_MODELS = [
    "moss_audio_tokenizer_decode_full.onnx",
    "moss_audio_tokenizer_decode_step.onnx",
]

for model_name in TRANSFORMER_MODELS:
    quantize_dynamic(
        model_input=f"source/{model_name}",
        model_output=f"output/{model_name.replace('.onnx', '.int8.onnx')}",
        weight_type=QuantType.QInt8,           # Signed INT8 cho ARM NEON SDOT
        op_types_to_quantize=["MatMul", "Gemm"],  # Chỉ lớp dense
        per_channel=True,                      # Chính xác hơn
        reduce_range=False,                    # False cho ARM (True chỉ cho legacy x86)
    )
```

Dependencies: `onnx>=1.16`, `onnxruntime>=1.18`

### 2. [ ] Chạy lượng tử hóa trên các file ONNX nguồn

Sử dụng file ONNX gốc tại `dist/zerotts_work/source/onnx/`:
- Input: 3 file FP32 (tổng ~818 MB)
- Output: 3 file INT8 (dự kiến ~450 MB)

### 3. [ ] Cập nhật script đóng gói `repackage_zerotts.py`

**Tệp sửa**: `scripts/repackage_zerotts.py`

- Mặc định sử dụng file `.int8.onnx` nếu tồn tại
- Vẫn sao chép `moss_audio_tokenizer_*` ở dạng FP32 nguyên bản
- Giữ nguyên `config.json`, `tokenizer.json`, `zerotts_voices.json`, voice embeddings

### 4. [ ] Hỗ trợ nạp model INT8 trên Android

**Tệp sửa**: `app/.../model/tts/ZeroTtsOnnxEngine.kt`

```kotlin
private fun resolveModelPath(directory: File, baseName: String): String {
    val int8File = File(directory, baseName.replace(".onnx", ".int8.onnx"))
    return if (int8File.isFile) int8File.absolutePath
    else File(directory, baseName).absolutePath
}
```

Áp dụng cho 3 session transformer. Giữ nguyên đường dẫn cố định cho codec.

### 5. [ ] Tải lên HuggingFace (thay thế bản cũ)

- Xóa bản `legado-tts-zerotts-base.zip` cũ (~903 MB) trên `Drduc/Legadofork`
- Tải lên bản mới (~500 MB) với cùng tên file
- Cập nhật `hf-artifacts-manifest.json` với checksum và kích thước mới

## Các Tệp Cần Tạo/Sửa

| Tệp | Loại | Mục đích |
|---|---|---|
| `tools/zerotts-quant/pyproject.toml` | [MỚI] | Cấu hình project Python |
| `tools/zerotts-quant/quantize_zerotts.py` | [MỚI] | Script lượng tử hóa chính |
| `tools/zerotts-quant/validate_quality.py` | [MỚI] | So sánh chất lượng FP32 vs INT8 |
| `scripts/repackage_zerotts.py` | [SỬA] | Hỗ trợ đóng gói INT8 |
| `app/.../model/tts/ZeroTtsOnnxEngine.kt` | [SỬA] | Auto-detect INT8 model |
| `supabase/artifacts/hf-artifacts-manifest.json` | [SỬA] | Cập nhật checksum/size |

## Tiêu Chí Kiểm Tra

- [ ] Script Python chạy thành công tạo 3 file `.int8.onnx`
- [ ] Tổng dung lượng zip ≤ 550 MB
- [ ] Import thành công trên emulator
- [ ] Synthesis thành công, giọng nói rõ ràng không méo tiếng

---
Phase tiếp theo: [Phase 03 - Tối Ưu Nâng Cao](./phase-03-advanced.md)
