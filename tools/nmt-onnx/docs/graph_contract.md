# Graph Contract & Specification: HachimiMT-60-QT vs Standard HachimiMT

## 1. Overview

Tài liệu này định nghĩa chi tiết hợp đồng đồ thị ONNX (Graph Contract) cho mô hình `ngocdang83/HachimiMT-60-QT` khi chuyển đổi sang định dạng ONNX INT8 và tích hợp vào `HachimiOnnxTranslator` trong DrDucBook (Legado MD3).

---

## 2. Model Graph Architecture Comparison

| Thuộc tính | HachimiMT-60 (Standard) | HachimiMT-60-QT | Ghi chú kiến trúc |
|---|---|---|---|
| **Upstream Repo** | `ngocdang83/HachimiMT-60` | `ngocdang83/HachimiMT-60-QT` | Pinned commit: `5588b84c0496ea582bb02c13e29af9b368a48150` |
| **Model Type** | MarianMT (Seq2Seq) | MarianMT (Seq2Seq) | Cấu trúc Marian Transformer |
| **Parameters** | ~56.4M | ~56.4M | |
| **d_model** | 512 | **576** | Kích thước ẩn (hidden dimension) |
| **Encoder Layers** | 8 | 8 | |
| **Encoder Heads** | 8 | 8 | |
| **Decoder Layers** | **2** | **2** | Marian decoder chỉ có 2 layers |
| **Decoder Heads** | **8** | **8** | Số attention heads |
| **Head Dimension** | **64** (`512 / 8`) | **72** (`576 / 8`) | **CỰC KỲ QUAN TRỌNG**: `createEmptyCache()` phải dùng 72L cho QT |
| **Vocab Size** | 24,000 | 24,000 | SentencePiece vocabulary |
| **BOS / Decoder Start** | 1 | 1 | `DECODER_START_TOKEN_ID = 1` |
| **EOS Token ID** | 2 | 2 | `EOS_TOKEN_ID = 2` |
| **PAD Token ID** | 0 | 0 | Token đệm |
| **Special Tokens** | `{0, 1, 2, 3}` | `{0, 1, 2, 3}` | `SPECIAL_TOKEN_IDS` |
| **noRepeatNgramSize** | **2** (mặc định) | **0** (bắt buộc) | QT cấm no-repeat bigram |

---

## 3. Five Required ONNX Graphs

Hệ thống yêu cầu 5 đồ thị ONNX riêng biệt nằm trong thư mục `filesDir/nmt_models/<model_id>/`:

### 3.1. `tokenizer.onnx` (Source Tokenizer)
- **Engine**: ONNX Runtime Extensions (`OrtxPackage`)
- **Input**:
  - Name: `"text"`
  - Type: String tensor (1D, shape `[1]`)
- **Outputs**:
  - Name: `"input_ids"` (int64, shape `[1, sequence_length]`)
  - Name: `"attention_mask"` (int64, shape `[1, sequence_length]`)

### 3.2. `target_tokenizer.onnx` (Target Tokenizer)
- **Engine**: ONNX Runtime Extensions (`OrtxPackage`)
- **Purpose**: Tokenize target text for lexical constraints (glossary mapping)
- **Input**:
  - Name: `"text"`
  - Type: String tensor (1D, shape `[1]`)
- **Outputs**:
  - Name: `"input_ids"` (int64, shape `[1, constraint_tokens]`)
  - Name: `"attention_mask"` (int64, shape `[1, constraint_tokens]`)

### 3.3. `encoder_model.onnx` (Marian Encoder)
- **Inputs**:
  - Name: `"input_ids"` (int64, shape `[batch_size, sequence_length]`)
  - Name: `"attention_mask"` (int64, shape `[batch_size, sequence_length]`)
- **Output**:
  - Name: `"last_hidden_state"` (float32, shape `[batch_size, sequence_length, 576]`)

### 3.4. `decoder_model_merged.onnx` (Merged Autoregressive Decoder)
- **Inputs**:
  - Name: `"input_ids"` (int64, shape `[1, 1]` - token đang giải mã tại bước hiện tại)
  - Name: `"encoder_hidden_states"` (float32, shape `[1, src_len, 576]` từ encoder)
  - Name: `"encoder_attention_mask"` (int64, shape `[1, src_len]` từ tokenizer)
  - Name: `"use_cache_branch"` (bool, 1D: `[false]` ở step 0, `[true]` ở step > 0)
  - **Past KV Cache Tensors** (4 tensors per layer × 2 layers = 8 tensors):
    - `past_key_values.0.decoder.key`: shape `[1, 8, past_seq_len, 72]`
    - `past_key_values.0.decoder.value`: shape `[1, 8, past_seq_len, 72]`
    - `past_key_values.0.encoder.key`: shape `[1, 8, src_len, 72]`
    - `past_key_values.0.encoder.value`: shape `[1, 8, src_len, 72]`
    - `past_key_values.1.decoder.key`: shape `[1, 8, past_seq_len, 72]`
    - `past_key_values.1.decoder.value`: shape `[1, 8, past_seq_len, 72]`
    - `past_key_values.1.encoder.key`: shape `[1, 8, src_len, 72]`
    - `past_key_values.1.encoder.value`: shape `[1, 8, src_len, 72]`
- **Outputs**:
  - Name: `"logits"` (float32, shape `[1, 1, 24000]`)
  - **Present KV Cache Tensors**:
    - `present.0.decoder.key`, `present.0.decoder.value`
    - `present.0.encoder.key`, `present.0.encoder.value`
    - `present.1.decoder.key`, `present.1.decoder.value`
    - `present.1.encoder.key`, `present.1.encoder.value`

### 3.5. `detokenizer.onnx` (Target Detokenizer)
- **Engine**: ONNX Runtime Extensions (`OrtxPackage`)
- **Input**:
  - Name: `"ids"` (int64, shape `[generated_tokens]`)
- **Output**:
  - Name: `"text"` (String tensor, fallbackIndex 0)

---

## 4. Critical Kotlin Runtime Refactoring Requirement

Trong file `HachimiOnnxTranslator.kt`:
```kotlin
// HIỆN TẠI (Hardcoded cho model standard):
private const val HEAD_DIMENSION = 64L

// REFACTOR BẮT BUỘC TRONG PHASE 12:
// Phải lấy từ NmtModelDescriptor hoặc model_manifest.json:
val headDimension = activeModelDescriptor.headDimension // 64L hoặc 72L
val attentionHeads = activeModelDescriptor.attentionHeads // 8L
val decoderLayers = activeModelDescriptor.decoderLayers // 2

private fun createEmptyCache(): Map<String, OnnxTensor> =
    (0 until decoderLayers).flatMap { layer ->
        listOf("decoder", "encoder").flatMap { attention ->
            listOf("key", "value").map { kind ->
                val name = "past_key_values.$layer.$attention.$kind"
                val buffer = ByteBuffer.allocateDirect(0)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                name to OnnxTensor.createTensor(
                    environment,
                    buffer,
                    longArrayOf(1, attentionHeads, 0, headDimension)
                )
            }
        }
    }.toMap()
```

---

## 5. Decoding Strategy & No-Repeat Warning

1. **Greedy Search Allocation-Free**:
   - `HachimiGreedySelector` quét trực tiếp trên direct `FloatBuffer` của logits mà không cấp phát đối tượng phụ.
2. **noRepeatNgramSize Rule**:
   - Đối với model Hachimi thường: `noRepeatNgramSize = 2` (chặn lặp 2 từ liền kề).
   - Đối với model Hachimi-QT: `noRepeatNgramSize = 0` (tắt hoàn toàn).
   - Nếu bật `noRepeatNgramSize = 2` trên bản QT, các cụm từ Hán Việt như *"khanh khanh ngã ngã"*, *"nguyên nguyên bổn bổn"*, *"hốt ẩn hốt hiện"*, *"từng tí từng tí"* sẽ bị triệt tiêu từ thứ hai, dẫn đến câu văn què quặt, cụt từ.
