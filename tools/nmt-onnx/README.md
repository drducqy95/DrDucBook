# HachimiMT-60-QT ONNX Conversion Pipeline

This toolchain exports, quantizes, validates, and packages the HuggingFace neural machine translation model [`ngocdang83/HachimiMT-60-QT`](https://huggingface.co/ngocdang83/HachimiMT-60-QT) (MarianMT Chinese -> Vietnamese Sino-Vietnamese / Convert model) for embedded on-device inference in Legado using ONNX Runtime Mobile.

---

## 1. Architecture & Tensor Contract

### MarianMT Model Specifications
- **Base Architecture**: MarianMT Seq2Seq (Encoder-Decoder)
- **Model Dimension ($d_{\text{model}}$)**: 576
- **Encoder Layers**: 6
- **Decoder Layers**: 2
- **Attention Heads**: 8
- **Head Dimension**: $576 / 8 = 72$ (unlike standard Hachimi which uses 64)
- **Pinned HuggingFace Commit**: `5588b84c0496ea582bb02c13e29af9b368a48150`
- **License / Attribution**: CC-BY-4.0 (`ngocdang83`)

### 5 ONNX Subgraphs
1. `tokenizer.onnx`: ONNX Runtime Extensions SentencePiece tokenizer (`SentencepieceTokenizer` node).
2. `encoder_model.onnx`: INT8 dynamic quantized Marian encoder.
3. `decoder_model_merged.onnx`: INT8 dynamic quantized Marian decoder with merged KV cache logic (`use_cache_branch`).
4. `target_tokenizer.onnx`: Target SPM token-to-ID vocabulary mapper.
5. `detokenizer.onnx`: ONNX Runtime Extensions SentencePiece detokenizer (`SentencepieceDecoder` node).

---

## 2. Parity & Validation Results

Evaluated against the unquantized PyTorch baseline on a 30-pair diverse Chinese novel test set (`testdata/golden_test_set.json`):
- **chrF score**: **100.00%**
- **BLEU score**: **100.00%**
- **Speedup**: ~2.8x faster on CPU with dynamic INT8 quantization.
- **Model Size**:
  - Float32 encoder + decoder: ~323 MB
  - INT8 quantized package: ~168 MB ZIP (`dist/hachimi-mt60-qt-zh-vi-onnx.zip`)

---

## 3. Decoding Policy Constraints

> [!IMPORTANT]
> **Zero N-Gram Repetition Rule (`noRepeatNgramSize = 0`)**
> Sino-Vietnamese / Convert translations frequently feature repeating characters in character names (e.g. "Bạch Phàm Phàm"), sound effects ("Ha ha ha"), and kinship terms ("tằng tằng"). Setting `noRepeatNgramSize > 0` causes critical term dropping in QT models. Legado automatically overrides and forces `noRepeatNgramSize = 0` when this model is active.

---

## 4. Pipeline Execution Scripts

### Prerequisites
Python 3.10+ virtual environment with dependencies:
```bash
cd tools/nmt-onnx
pip install transformers optimum[onnxruntime] onnx onnxruntime onnxruntime-extensions sacrebleu
```

### Full Pipeline Workflow
1. **Export PyTorch MarianMT to ONNX**:
   ```powershell
   python convert_hachimi_qt.py
   ```
2. **Dynamic INT8 Quantization**:
   ```powershell
   python quantize.py
   ```
3. **Verify Parity with Golden Test Set**:
   ```powershell
   python validate_hachimi_qt.py
   ```
4. **Package Distribution ZIP**:
   ```powershell
   .\package_hachimi_qt.ps1
   ```

Outputs will be placed into `dist/hachimi-mt60-qt-zh-vi-onnx.zip` along with its SHA256 checksum file.
