# Phase 09: NMT Contract Audit & Golden Test Set (Track B - NMT)

Status: ⬜ Pending
Dependencies: None (Can run in parallel with Track A)

## Objective
Khảo sát và chốt revision của upstream model `ngocdang83/HachimiMT-60-QT` trên HuggingFace, kiểm tra và xác nhận hợp đồng tensor/graph của `HachimiOnnxTranslator` hiện tại trong app, và xây dựng bộ câu kiểm thử chuẩn (Golden Test Set) bao gồm các phong cách dịch convert QT/Hán Việt, câu ngắn, câu dài, tên riêng, số và dấu câu.

## Scope
| In Scope | Out of Scope |
|---|---|
| Ghim commit hash (revision) của `ngocdang83/HachimiMT-60-QT` | Export mô hình sang ONNX (Phase 10) |
| Audit chính xác input/output tensor names và dimension | INT8 Quantization (Phase 11) |
| Xác nhận các tham số decoder: 2 layers, 8 heads, 64 dim, DECODER_START=1, EOS=2 | Runtime Kotlin refactoring (Phase 12) |
| Xây dựng bộ test set 30 câu đối chiếu chất lượng | |
| Xác nhận opset ONNX Runtime Android đang hỗ trợ | |

## Requirements
### Functional
- [ ] REQ-09.1: Ghim commit hash cố định của repository `ngocdang83/HachimiMT-60-QT` trên Hugging Face nhằm đảm bảo tính lặp lại (reproducibility).
- [ ] REQ-09.2: Đối chiếu hợp đồng đồ thị ONNX (Graph Contract) của `HachimiOnnxTranslator.kt`:
  - `tokenizer.onnx`: Input `"text"` -> Outputs `"input_ids"`, `"attention_mask"`
  - `target_tokenizer.onnx`: Input `"text"` -> Outputs `"input_ids"`, `"attention_mask"`
  - `encoder_model.onnx`: Inputs `"input_ids"`, `"attention_mask"` -> Output `"last_hidden_state"`
  - `decoder_model_merged.onnx`:
    - Inputs: `"input_ids"`, `"encoder_hidden_states"`, `"encoder_attention_mask"`, `"use_cache_branch"`
    - Past key/values: `past_key_values.$layer.$attention.$kind` với `layer` in 0..1 (2 decoder layers), `attention` in `["decoder", "encoder"]`, `kind` in `["key", "value"]`
    - Outputs: `"logits"`, `"present.$layer.decoder.key"`, `"present.$layer.decoder.value"`, `"present.$layer.encoder.key"`, `"present.$layer.encoder.value"`
  - `detokenizer.onnx`: Input `"ids"` -> Output `"text"`
  - Hằng số Token: `DECODER_START_TOKEN_ID = 1`, `EOS_TOKEN_ID = 2`, `SPECIAL_TOKEN_IDS = setOf(0, 1, 2, 3)`
- [ ] REQ-09.3: Xác nhận sự khác biệt quan trọng của mô hình QT:
  - `noRepeatNgramSize` **bắt buộc mặc định là 0** (tắt no-repeat bigram), vì từ vựng Hán Việt / QT có rất nhiều từ láy, lặp từ tự nhiên; bật no-repeat bigram sẽ làm hỏng câu dịch.
- [ ] REQ-09.4: Xây dựng file `tools/nmt-onnx/testdata/golden_test_set.json` gồm tối thiểu 30 cặp câu tiếng Trung -> tiếng Việt (chuẩn văn phong QT/tiên hiệp/đô thị).

### Non-Functional
- [ ] NF-09.1: Test set bao quát các trường hợp: câu hội thoại, câu miêu tả chiêu thức, tên riêng Trung Quốc, từ ngữ Hán Việt đặc trưng.

## Implementation Steps
### Step 1: Clone Snapshot & Ghim Revision
1. [ ] Kiểm tra model card `ngocdang83/HachimiMT-60-QT` trên HuggingFace
2. [ ] Ghi nhận commit SHA mới nhất vào `tools/nmt-onnx/MODEL_INFO.json`

### Step 2: Lập Bộ Câu Golden Test Set
3. [ ] Tạo `tools/nmt-onnx/testdata/golden_test_set.json`:
   - 10 câu ngắn (tiêu đề, câu đối thoại, thành ngữ)
   - 10 câu trung bình (miêu tả hành động, cảnh giới tu chân)
   - 10 câu dài phức tạp (nhiều mệnh đề, tên riêng lồng ghép)

### Step 3: Tạo Tài Liệu Graph Contract
4. [ ] Tạo file `tools/nmt-onnx/docs/graph_contract.md` ghi nhận chi tiết:
   - Các tên tensor, kiểu dữ liệu, tensor rank, dimensions
   - Sự khác nhau giữa mô hình gốc HachimiMT-60 và bản QT

## Files to Create/Modify
- `tools/nmt-onnx/MODEL_INFO.json` — Model metadata & pinned commit hash
- `tools/nmt-onnx/testdata/golden_test_set.json` — 30 benchmark test pairs
- `tools/nmt-onnx/docs/graph_contract.md` — ONNX input/output contract reference

## Test Criteria
- [ ] PASS-09.1: Commit hash được ghim và có thể tải reproducible từ HuggingFace.
- [ ] PASS-09.2: Tài liệu contract khớp 100% với mã nguồn Kotlin tại `HachimiOnnxTranslator.kt`.
- [ ] PASS-09.3: Bộ câu kiểm thử được duyệt đầy đủ các thể loại ngữ cảnh truyện.

---
Next Phase: `phase-10-nmt-export-prototype.md`
