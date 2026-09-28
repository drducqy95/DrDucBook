# Phase 01: Tối Ưu Runtime Android (Không Cần Sửa Model)

Trạng thái: ⬜ Chờ
Phụ thuộc: Không (có thể bắt đầu ngay)
Dự kiến tăng tốc: **3–5 lần** so với hiện tại

## Mục Tiêu

Khắc phục các lỗi kiến trúc và cấu hình runtime đang làm engine ZeroTTS chạy chậm gấp nhiều lần so với năng lực thực tế, mà không cần thay đổi bất kỳ tệp mô hình ONNX nào.

## Yêu Cầu

### Chức năng
- [ ] Engine phải tái sử dụng ONNX session giữa các câu văn liên tiếp khi đọc sách
- [ ] XNNPACK phải được kích hoạt nếu thiết bị hỗ trợ
- [ ] Số luồng CPU phải tự dò theo nhân hiệu năng cao (Big Cores) của từng thiết bị
- [ ] Các tensor tham số cố định không được tạo lại ở mỗi bước vòng lặp

### Phi chức năng
- [ ] Hiệu năng: Giảm thời gian từ lúc gọi TTS đến khi phát âm thanh ít nhất 3 lần
- [ ] Bộ nhớ: Giảm áp lực GC (Garbage Collector) trong vòng lặp autoregressive

## Các Bước Triển Khai

### 1. [ ] Sửa vòng đời session - Cache engine giữa các câu

**Tệp**: `app/src/main/java/io/legado/app/model/tts/LocalTtsSynthesis.kt`

Hiện tại (dòng 44–54): Mỗi câu tạo `ZeroTtsOnnxEngine(model)` mới → synthesize → `engine.close()` ngay lập tức, phí 8–12 giây tải lại 903 MB.

**Giải pháp**: Xây dựng `engineCache: MutableMap<String, LocalTtsSynthesisEngine>` cấp `object`:
```kotlin
private val engineCache = mutableMapOf<String, LocalTtsSynthesisEngine>()

// Thay thế:
val engine = engineCache.getOrPut(model.id) {
    when (model.engine) {
        ENGINE_ZEROTTS -> ZeroTtsOnnxEngine(model)
        ENGINE_PIPER_VITS -> PiperOnnxTtsEngine(context, model)
        ENGINE_VALTEC_VITS -> ValtecOnnxTtsEngine(model)
        else -> error("LOCAL_TTS_ENGINE_UNSUPPORTED")
    }
}
// KHÔNG gọi engine.close() - để cơ chế IDLE_UNLOAD_MS (3 phút) tự dọn dẹp
```

### 2. [ ] Kích hoạt XNNPACK + ALL_OPT + Auto-detect CPU threads

**Tệp**: `app/src/main/java/io/legado/app/model/tts/ZeroTtsOnnxEngine.kt` (dòng 76–79)

```kotlin
// TRƯỚC:
val options = OrtSession.SessionOptions().apply {
    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
    setIntraOpNumThreads(2)
}

// SAU:
val numThreads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)
val options = OrtSession.SessionOptions().apply {
    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    setIntraOpNumThreads(numThreads)
    addConfigEntry("session.intra_op.allow_spinning", "0") // Tiết kiệm pin
    try {
        addXnnpack(mapOf("intra_op_num_threads" to numThreads.toString()))
    } catch (_: Throwable) {
        android.util.Log.w("ZeroTtsOnnx", "XNNPACK không khả dụng trên thiết bị này")
    }
}
```

- `ALL_OPT`: Kích hoạt gộp toán tử (operator fusion), gấp hằng số (constant folding), loại bỏ cast thừa
- `XNNPACK`: Thư viện tính toán ARM NEON được tinh chỉnh tay, đã có sẵn trong `onnxruntime-android:1.27.0`
- `allow_spinning = "0"`: Ngăn luồng CPU quay vòng lãng phí pin khi chờ

### 3. [ ] Đưa tensor hằng số ra ngoài vòng lặp autoregressive

**Tệp**: `app/src/main/java/io/legado/app/model/tts/ZeroTtsOnnxEngine.kt` — `Sessions` class và `decodeSingleFrame()`

Hiện tại: 8 tensor tham số (`text_temperature`, `audio_topk`, `audio_topp`, `audio_repetition_penalty`, `cfg_scale`, etc.) được tạo mới và giải phóng **50+ lần** trong mỗi câu tổng hợp.

**Giải pháp**: Chuyển thành trường (field) của class `Sessions`, tạo 1 lần khi khởi tạo session, giải phóng khi `close()`:
```kotlin
private class Sessions(...) : AutoCloseable {
    // Các tensor hằng số - tạo 1 lần duy nhất
    private val textTempTensor = OnnxTensor.createTensor(environment, floatArrayOf(1.0f))
    private val textTopkTensor = OnnxTensor.createTensor(environment, longArrayOf(50L))
    private val audioTempTensor = OnnxTensor.createTensor(environment, floatArrayOf(0.8f))
    private val audioTopkTensor = OnnxTensor.createTensor(environment, longArrayOf(25L))
    private val audioToppTensor = OnnxTensor.createTensor(environment, floatArrayOf(0.95f))
    private val audioRepPenTensor = OnnxTensor.createTensor(environment, floatArrayOf(1.2f))
    private val cfgScaleTensor = OnnxTensor.createTensor(environment, floatArrayOf(1.0f))

    override fun close() {
        // Đóng tất cả tensor hằng số ở đây
        runCatching { textTempTensor.close() }
        runCatching { textTopkTensor.close() }
        // ... và 4 session ONNX
    }
}
```

**Hiệu quả**: Cắt giảm ~350 lượt gọi JNI create/close và giảm áp lực GC.

### 4. [ ] Tối ưu quản lý bộ nhớ đệm KV-Cache

**Tệp**: `app/src/main/java/io/legado/app/model/tts/ZeroTtsOnnxEngine.kt` — `copyTensor()` và `runAutoregressiveLoop()`

Hiện tại: `copyTensor()` sao chép toàn bộ `packed_kv` (kích thước tăng dần mỗi frame: `9 × 2 × 1 × 12 × seqLen × 64 × 4` bytes) sang Java heap rồi tạo `OnnxTensor` mới qua JNI ở **mỗi bước lặp**.

**Giải pháp**: Sử dụng `DirectByteBuffer` tái sử dụng với kích thước đủ lớn cho trường hợp worst-case (`maxFrames`), tránh cấp phát/giải phóng liên tục trên Java heap.

## Các Tệp Cần Sửa

| Tệp | Mục đích sửa |
|---|---|
| `app/.../model/tts/LocalTtsSynthesis.kt` | Cache engine giữa các câu |
| `app/.../model/tts/ZeroTtsOnnxEngine.kt` | XNNPACK, ALL_OPT, tensor hằng số, KV-cache |

## Tiêu Chí Kiểm Tra

- [ ] Biên dịch thành công: `.\gradlew.bat :app:compileAppDebugKotlin`
- [ ] Unit test TTS pass: `.\gradlew.bat :app:testDebugUnitTest --tests "io.legado.app.model.tts.*"`
- [ ] Logcat: câu thứ 2 trở đi không còn log "Creating textEncoder session..." (tái sử dụng session)
- [ ] Logcat: thời gian synthesis giảm ≥ 3 lần so với trước

---
Phase tiếp theo: [Phase 02 - Lượng Tử Hóa Model](./phase-02-quantize.md)
