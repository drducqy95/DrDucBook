# Phase 02: Chuẩn Hóa Payload `inner[]`, Browser Headers & Tăng Cường RPC Fidelity

> **Thuộc kế hoạch:** `PLN-261014-1845` — Gemini Web Advanced Enhancements  
> **Trạng thái:** 🟡 Sẵn sàng triển khai  
> **Cập nhật:** 2026-10-14T18:52:00 — Bổ sung 7 khoảng trống từ phân tích chi tiết  
> **Các file tác động chính:**
> - `app/src/main/java/io/legado/app/data/repository/ai/GeminiWebHandler.kt`
> - `app/src/main/java/io/legado/app/data/repository/ai/GeminiWebSessionManager.kt`

---

## 1. Sửa Lỗi Payload `inner[]` Array (CẤP THIẾT)

### 1.1. Thêm phần tử thứ 81 — `inner[80] = 1` (G02)

**Vấn đề:** DrDucBook tạo mảng 80 phần tử (`for (i in 0 until 80)`), nhưng `gemini-web-to-api` tạo mảng **81 phần tử** và set `inner[80] = 1`.

**Sửa trong `buildFReqPayload()`:**
```kotlin
// TRƯỚC: for (i in 0 until 80)
// SAU:
for (i in 0 until 81) {
    innerArray.add(JsonNull.INSTANCE)
}
// ... (giữ nguyên các set khác) ...
innerArray.set(79, JsonPrimitive(geminiModelCode))
innerArray.set(80, JsonPrimitive(1))  // ← BỔ SUNG
```

### 1.2. Bật `isTemporary` flag — `inner[45] = 1` (G03)

**Vấn đề:** Không có flag này, mỗi request dịch truyện sẽ tạo 1 cuộc trò chuyện mới trong lịch sử Gemini Web của người dùng → nhanh chóng rác hàng ngàn cuộc chat vô nghĩa.

**Sửa:**
```kotlin
// Luôn bật isTemporary cho tác vụ dịch truyện
innerArray.set(45, JsonPrimitive(1))
```

### 1.3. Chuẩn hóa `inner[6]` và `inner[41]` (G08)

**Phát hiện sai lệch:**
- `inner[6]`: Go = `[1]`, Kotlin = `[0]`
- `inner[41]`: Go = `[1]`, Kotlin = `[2]`

**Giải pháp:** Chuẩn hóa theo Go (giá trị mới hơn, khả năng tương thích cao hơn):
```kotlin
innerArray.set(6, JsonArray().apply { add(1) })   // 1 thay vì 0
innerArray.set(41, JsonArray().apply { add(1) })   // 1 thay vì 2
```

### 1.4. UUID uppercase cho `inner[59]` (G06 nhỏ)

**Sửa:**
```kotlin
innerArray.set(59, JsonPrimitive(UUID.randomUUID().toString().uppercase()))
```

---

## 2. Bổ Sung Browser Headers (G04, G06, G07)

### 2.1. Trích xuất `sessionId` và `pushId` từ HTML init

Bổ sung vào `GeminiWebSessionManager.fetchSessionData()`:
```kotlin
private val SESSION_ID_REGEX = Regex(""""FdrFJe":"([^"]+)"""")
private val PUSH_ID_REGEX = Regex(""""qKIAYe":"([^"]+)"""")
```

Mở rộng `GeminiWebSession`:
```kotlin
data class GeminiWebSession(
    // ... existing fields ...
    val sessionId: String = "",
    val pushId: String = "feeds/mcudyrk2a4khkz",
    val generationId: String = UUID.randomUUID().toString().uppercase(),
)
```

### 2.2. Xây dựng header JSPB chuẩn

Hàm mới trong `GeminiWebHandler.kt`:
```kotlin
private fun buildJspbHeaders(
    modelNumber: Int,
    requestId: String,
    generationId: String
): Map<String, String> {
    // x-goog-ext-525001261-jspb: mảng 17 phần tử
    val modelHeader = Array<Any?>(17) { null }
    modelHeader[0] = 1
    modelHeader[7] = 0
    modelHeader[8] = listOf(4, 5, 6, 8)
    modelHeader[11] = 1  // capacity (default)
    modelHeader[14] = modelNumber
    modelHeader[15] = 1
    modelHeader[16] = generationId
    
    return mapOf(
        "x-goog-ext-525001261-jspb" to COMPACT_GSON.toJson(modelHeader),
        "x-goog-ext-525005358-jspb" to COMPACT_GSON.toJson(listOf(requestId, 1)),
        "x-goog-ext-73010989-jspb" to "[0]",
        "x-goog-ext-73010990-jspb" to "[0,0,0]",
    )
}
```

### 2.3. Bổ sung `f.sid` và `source-path` vào URL

```kotlin
val url = buildString {
    append("https://gemini.google.com/_/BardChatUi/data/assistant.lamda.BardFrontendService/StreamGenerate")
    append("?bl=").append(encodedBl)
    append("&hl=vi")
    append("&_reqid=").append(reqId)
    append("&rt=c")
    if (session.sessionId.isNotBlank()) {
        append("&f.sid=").append(URLEncoder.encode(session.sessionId, "UTF-8"))
    }
    append("&source-path=").append(URLEncoder.encode("/app", "UTF-8"))
}
```

---

## 3. Bổ Sung Nhận Diện `<ctrl94>` / `<ctrl95>` Thinking Markers (G11)

**Vấn đề:** `gemini-web-to-api` nhận diện cả native Google control tokens `<ctrl94>thought` / `<ctrl94>` (mở) + `<ctrl95>` (đóng). DrDucBook chỉ nhận diện `<thought>` / ````thought```` do prompt-instructed.

**Sửa trong `GeminiStreamAccumulator.onTextUpdate()`:**
```kotlin
val thoughtTag = when {
    cleaned.contains("<thought>") -> "<thought>" to "</thought>"
    cleaned.contains("```thought") -> "```thought" to "```"
    cleaned.contains("<ctrl94>thought") -> "<ctrl94>thought" to "<ctrl95>"  // ← MỚI
    cleaned.contains("<ctrl94>") -> "<ctrl94>" to "<ctrl95>"               // ← MỚI
    else -> null
}
```

---

## 4. Bổ Sung Xử Lý Numbered-Field Response Format (MỚI)

**Vấn đề:** Google đang dần chuyển sang format response mới cho một số model, nơi text nằm trong `payload[2]` dạng JSON map với key `"7"` thay vì `payload[4][candidates]`.

**Bổ sung vào `parseInnerJson()`:**
```kotlin
// Trước khi kiểm tra payload[4], thử numbered-field format:
if (innerArray.size() < 5 || !innerArray[4].isJsonArray) {
    // Newer StreamGenerate format: payload[2] is a map {"7": ["text"], "44": true}
    if (innerArray.size() > 2 && innerArray[2].isJsonObject) {
        val fields = innerArray[2].asJsonObject
        val textField = fields.getAsJsonArray("7")
        if (textField != null && textField.size() > 0 && textField[0].isJsonPrimitive) {
            return textField[0].asString
        }
    }
}
```

---

## 5. Nhận Diện Model Bị Google Hạ Cấp Ngầm (G10)

**Vấn đề:** Khi hạn ngạch web của model Pro/Argon cạn kiệt, Gemini Web âm thầm trả về phản hồi từ model Flash mà không thông báo lỗi.

**Giải pháp:** Sau khi parse response, quét `responseBody` tìm Model ID đã biết và so sánh với model đã chọn:
```kotlin
// Log cảnh báo nếu phát hiện model downgrade
// (Không block user, chỉ log để tracking)
AppLog.put("Gemini Web có thể đã hạ cấp model từ $requestedModel xuống $actualModel")
```

---

## 6. Tiêu Chí Nghiệm Thu Cập Nhật

- [ ] Payload `inner[]` có đúng 81 phần tử, `inner[80] = 1`
- [ ] `inner[45] = 1` (isTemporary) được bật cho tác vụ dịch
- [ ] `inner[6] = [1]`, `inner[41] = [1]` chuẩn hóa theo protocol mới nhất
- [ ] UUID tại `inner[59]` ở dạng UPPERCASE
- [ ] Mọi request gửi đầy đủ 4 header `x-goog-ext-*-jspb`
- [ ] URL `StreamGenerate` chứa `&f.sid=` khi có sessionId
- [ ] `GeminiStreamAccumulator` nhận diện được `<ctrl94>` / `<ctrl95>` markers
- [ ] `parseInnerJson` xử lý được cả numbered-field format mới
- [ ] Compile Kotlin pass 100%, không regression trên các unit test hiện có
