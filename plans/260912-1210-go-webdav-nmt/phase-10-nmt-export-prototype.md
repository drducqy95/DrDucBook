# Phase 10: PyTorch to ONNX Export Pipeline (Track B - NMT)

Status: ⬜ Pending
Dependencies: `phase-09-nmt-contract-audit.md`

## Objective
Xây dựng pipeline Python hoàn chỉnh sử dụng HuggingFace Transformers, Optimum ONNX và ONNX Runtime Extensions để export mô hình PyTorch MarianMT `ngocdang83/HachimiMT-60-QT` sang 5 đồ thị ONNX (FP32) chuẩn hóa, khớp chính xác contract của Legado, và đối chiếu kết quả dịch tương đồng (parity) với Transformers gốc.

## Scope
| In Scope | Out of Scope |
|---|---|
| Môi trường Python ghim versions (`tools/nmt-onnx/pyproject.toml`) | INT8 Quantization (Phase 11) |
| Script export `tools/nmt-onnx/convert_hachimi_qt.py` | Đóng gói ZIP & Upload HuggingFace (Phase 11) |
| Export encoder, decoder merged (với KV cache branch), tokenizer, detokenizer | Android Kotlin integration (Phase 12) |
| Script kiểm định độ tương đồng `tools/nmt-onnx/validate_hachimi_qt.py` | |
| Kiểm tra toàn diện bằng `onnx.checker` | |

## Requirements
### Functional
- [ ] REQ-10.1: Môi trường Python:
  - Ghim phiên bản các thư viện trong `pyproject.toml`: `torch`, `transformers`, `optimum[onnxruntime]`, `onnx`, `onnxruntime-extensions`, `sentencepiece`.
- [ ] REQ-10.2: Export 5 đồ thị ONNX riêng biệt:
  1. `tokenizer.onnx`: Sử dụng `onnxruntime-extensions` đóng gói SentencePiece tokenizer tiếng Trung. Nhận input `"text"`, trả về `"input_ids"`, `"attention_mask"`.
  2. `target_tokenizer.onnx`: SentencePiece tokenizer tiếng Việt cho lexical constraint mapping.
  3. `encoder_model.onnx`: Export MarianEncoder qua Optimum, nhận `"input_ids"`, `"attention_mask"`, trả về `"last_hidden_state"`.
  4. `decoder_model_merged.onnx`: Export MarianDecoder có Past Key/Value cache. Gộp graph không-cache và có-cache qua nhánh điều kiện `"use_cache_branch"`. Phù hợp cấu trúc 2 layers, 8 heads, 64 head dimension.
  5. `detokenizer.onnx`: Sử dụng `onnxruntime-extensions` giải mã mảng token IDs `"ids"` thành chuỗi tiếng Việt `"text"`.
- [ ] REQ-10.3: Kiểm tra tính hợp lệ: Toàn bộ 5 file ONNX vượt qua `onnx.checker.check_model()` không có lỗi opset hay tensor shape mismatch.
- [ ] REQ-10.4: Đối chiếu kết quả (Parity validation):
  - Chạy suy luận trên bộ câu Golden Test Set (Phase 09) bằng cả hai cách: (A) PyTorch Transformers pipeline, (B) ONNX Runtime sessions thuần.
  - Kết quả token ID sinh ra và text dịch đạt độ tương đồng tuyệt đối hoặc tương đương (chrF > 98, BLEU > 95).

### Non-Functional
- [ ] NF-10.1: Export pipeline tự động hóa hoàn toàn bằng 1 lệnh duy nhất: `python convert_hachimi_qt.py`.
- [ ] NF-10.2: Tương thích với ONNX Opset 14 (chuẩn đang chạy ổn định trên Android ONNX Runtime 1.18+).

## Implementation Steps
### Step 1: Thiết lập môi trường Python
1. [ ] Tạo `tools/nmt-onnx/pyproject.toml`
2. [ ] Thiết lập virtual environment hoặc dùng `uv` để cài đặt dependencies

### Step 2: Xây dựng Script Export Mô Hình
3. [ ] Tạo `tools/nmt-onnx/convert_hachimi_qt.py`:
   - Tải snapshot `ngocdang83/HachimiMT-60-QT`
   - Xuất Tokenizer & Detokenizer qua `onnxruntime_extensions.tools.pre_post_processing`
   - Xuất Encoder & Decoder qua Optimum ONNX
   - Ghép decoder cache branch để tạo `decoder_model_merged.onnx`
   - Đổi tên các tensor đầu vào/ra khớp chính xác với `HachimiOnnxTranslator.kt`

### Step 3: Xây dựng Script Đối Chiếu & Kiểm Định
4. [ ] Tạo `tools/nmt-onnx/validate_hachimi_qt.py`:
   - Nạp 5 đồ thị ONNX vào OrtSession
   - Hiện thực greedy search giải mã mô phỏng `HachimiGreedySelector`
   - Chạy 30 câu trong Golden Test Set
   - So sánh output text với PyTorch gốc
   - Đánh giá trường hợp `noRepeatNgramSize = 0` và xác nhận không có hiện tượng lặp từ bất thường

## Files to Create/Modify
- `tools/nmt-onnx/pyproject.toml` — Python dependency manifest
- `tools/nmt-onnx/convert_hachimi_qt.py` — End-to-end export script
- `tools/nmt-onnx/validate_hachimi_qt.py` — Parity and benchmark test script

## Test Criteria
- [ ] PASS-10.1: 5 file `.onnx` FP32 được sinh ra thành công và vượt qua `onnx.checker`.
- [ ] PASS-10.2: `python validate_hachimi_qt.py` báo cáo độ tương đồng trên 30 câu golden test set đạt chrF > 98%.

---
Next Phase: `phase-11-nmt-quantize-package.md`
