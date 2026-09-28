# Phase 03: Tối Ưu Nâng Cao (ORT Format + Streaming Vocoder)

Trạng thái: ⬜ Chờ
Phụ thuộc: Phase 02
Dự kiến cải thiện: Thời gian nạp model giảm ~80%, phát âm thanh sớm hơn ~2 lần

## Mục Tiêu

Áp dụng các kỹ thuật tối ưu nâng cao: chuyển đổi sang định dạng FlatBuffers cho tốc độ nạp siêu nhanh, kích hoạt streaming vocoder decode để phát âm thanh sớm hơn, và hoàn thiện cơ chế tự động dò luồng CPU.

## Các Bước Triển Khai

### 1. [ ] Chuyển đổi ONNX sang định dạng ORT (FlatBuffers)

**Tệp mới**: Thêm bước chuyển đổi vào `tools/zerotts-quant/quantize_zerotts.py`

```bash
python -m onnxruntime.tools.convert_onnx_models_to_ort \
    --optimization_level basic \
    text_encoder.int8.onnx prefix_step.int8.onnx \
    local_frame_decode.int8.onnx moss_audio_tokenizer_decode_full.onnx
```

Lợi ích:
- **Tốc độ nạp tăng ~80%** nhờ zero-copy memory mapping (FlatBuffers vs Protobuf)
- Giảm bộ nhớ heap cần thiết khi parse model lớn
- Hỗ trợ tệp `.ort` song song với `.onnx` (Android engine auto-detect)

**Tệp sửa**: `app/.../model/tts/ZeroTtsOnnxEngine.kt`
```kotlin
private fun resolveModelPath(directory: File, baseName: String): String {
    // Ưu tiên: .ort > .int8.onnx > .onnx
    val ortFile = File(directory, baseName.replace(".onnx", ".ort"))
    if (ortFile.isFile) return ortFile.absolutePath
    val int8File = File(directory, baseName.replace(".onnx", ".int8.onnx"))
    if (int8File.isFile) return int8File.absolutePath
    return File(directory, baseName).absolutePath
}
```

### 2. [ ] Kích hoạt Streaming Vocoder Decode (phát âm sớm)

**Tệp sửa**: `app/.../model/tts/ZeroTtsOnnxEngine.kt`

Hiện tại: Engine phải đợi sinh đủ tất cả frames (~50 frames cho 4s) rồi mới gọi `moss_audio_tokenizer_decode_full.onnx` 1 lần cuối cùng.

**Giải pháp**: Sử dụng `moss_audio_tokenizer_decode_step.onnx` (đã có sẵn trong gói model, 351KB) để giải mã từng cụm 5-10 frames ngay trong vòng lặp autoregressive:

```kotlin
// Ý tưởng: Mỗi khi tích lũy đủ CHUNK_SIZE frames, giải mã ra audio chunk
private const val STREAMING_CHUNK_SIZE = 8 // = 640ms âm thanh

// Trong vòng lặp:
if (frames.size % STREAMING_CHUNK_SIZE == 0) {
    val chunkAudio = decodeAudioChunk(frames.takeLast(STREAMING_CHUNK_SIZE))
    onAudioChunkReady(chunkAudio) // Callback để phát ngay
}
```

**Lợi ích**: Người dùng nghe thấy âm thanh sau ~640ms thay vì phải chờ 4-5 giây cho cả câu.

> **Lưu ý**: Cần thiết kế callback interface `onAudioChunkReady` trong `LocalTtsSynthesisEngine` và cập nhật `LocalTtsSynthesis.kt` để hỗ trợ streaming playback.

### 3. [ ] Hoàn thiện cơ chế Auto-detect CPU Threads

**Tệp sửa**: `app/.../model/tts/ZeroTtsOnnxEngine.kt`

```kotlin
companion object {
    private const val IDLE_UNLOAD_MS = 3 * 60 * 1000L
    private const val DEFAULT_MAX_FRAMES = 1500
    
    /** Tự dò số luồng tối ưu dựa trên cấu hình CPU thiết bị */
    internal fun optimalThreadCount(): Int {
        val available = Runtime.getRuntime().availableProcessors()
        // Trên thiết bị có big.LITTLE: dùng nửa nhân (= số big cores)
        // Tối thiểu 2, tối đa 4 để cân bằng tốc độ vs tiêu thụ pin
        return (available / 2).coerceIn(2, 4)
    }
}
```

## Các Tệp Cần Tạo/Sửa

| Tệp | Loại | Mục đích |
|---|---|---|
| `tools/zerotts-quant/quantize_zerotts.py` | [SỬA] | Thêm bước chuyển đổi .ort |
| `scripts/repackage_zerotts.py` | [SỬA] | Đóng gói .ort nếu có |
| `app/.../model/tts/ZeroTtsOnnxEngine.kt` | [SỬA] | Auto-detect .ort, streaming decode, optimalThreadCount |
| `app/.../model/tts/LocalTtsSynthesisEngine.kt` | [SỬA] | Thêm streaming callback interface |
| `app/.../model/tts/LocalTtsSynthesis.kt` | [SỬA] | Hỗ trợ streaming playback |
| `app/.../model/tts/LocalTtsModelImporter.kt` | [SỬA] | Whitelist `.ort` extension |

## Tiêu Chí Kiểm Tra

- [ ] Biên dịch thành công
- [ ] Model .ort nạp nhanh hơn .onnx (đo log thời gian tạo session)
- [ ] Streaming: Âm thanh đầu tiên phát ra dưới 1 giây sau khi gọi TTS
- [ ] Không hồi quy chất lượng giọng nói

---
Kết thúc plan. Sau Phase 03 → test tổng thể trên thiết bị.
