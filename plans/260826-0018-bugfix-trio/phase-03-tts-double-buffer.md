# Phase 03: TTS WebService Double Buffering

Status: ⬜ Pending
Dependencies: None (parallel với Phase 01, 02)
Estimated: 45 min

## Objective
Loại bỏ khoảng trống 250-900ms giữa các chunk TTS khi đọc sách qua WebService. Triển khai double buffering để chunk tiếp theo đã sẵn sàng phát ngay khi chunk hiện tại kết thúc.

## Root Cause Analysis

### Flow hiện tại (tuần tự, gây khoảng trống)
```
[Chunk N đang phát]
       │
       ▼
[audio.onended fires]  ← SILENCE BẮT ĐẦU
       │
       ├─► Promise resolve
       ├─► Loop advance to N+1
       ├─► playWebTtsChunk(N+1) called
       ├─► await getWebTtsChunkSynthesis(N+1)     [0-500ms nếu prefetch chậm]
       ├─► await updateWebTtsPosition(...)         [nextTick + scroll]
       ├─► new Audio(url + '?t=' + Date.now())     [Cache-busting, ép tải lại]
       ├─► audio.play()
       │     ├─► HTTP GET request
       │     ├─► Download WAV
       │     └─► Decode + buffer
       ▼
[Chunk N+1 bắt đầu phát]  ← SILENCE KẾT THÚC (250-900ms+)
```

### 5 Nguyên nhân cụ thể

| # | Nguyên nhân | File:Line | Impact |
|---|---|---|---|
| 1 | **Sequential Audio Creation** | [`BookChapter.vue:906`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/modules/web/src/views/BookChapter.vue#L906) | Audio() chỉ tạo SAU onended → 250-900ms gap |
| 2 | **Cache-Busting Timestamp** | [`BookChapter.vue:905`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/modules/web/src/views/BookChapter.vue#L905) | `?t=Date.now()` ép browser tải lại audio |
| 3 | **Silent WAV Network Roundtrip** | [`WebServiceTtsController.kt:198-204`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/app/src/main/java/io/legado/app/web/WebServiceTtsController.kt#L198-L204) | 100ms silent WAV download + decode + play → thêm 200ms overhead |
| 4 | **Serial Prefetch Chaining** | [`BookChapter.vue:778-785`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/modules/web/src/views/BookChapter.vue#L778-L785) | Prefetch chạy tuần tự qua `previousTask.then()` |
| 5 | **System TTS Cold-Start** | [`WebServiceTtsController.kt:240-276`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/app/src/main/java/io/legado/app/web/WebServiceTtsController.kt#L240-L276) | New TextToSpeech() per chunk → 200-1500ms init |

## Implementation Steps
1. [x] Phân tích root cause (5 nguyên nhân)
2. [ ] **Backend: Add `silent` flag** to `WebServiceTtsSynthesisResponse`
3. [ ] **Frontend: Skip silent chunk network roundtrip**
4. [ ] **Frontend: Remove cache-busting timestamp**
5. [ ] **Frontend: Implement double buffering** (preload next Audio during current playback)
6. [ ] **Frontend: Parallel prefetch** (remove serial chaining)
7. [ ] **Frontend: Increase prefetch ahead** to 3

## Files to Modify

### A. Backend — Add `silent` Flag

#### [`WebServiceModels.kt`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/app/src/main/java/io/legado/app/domain/webservice/WebServiceModels.kt)
```diff
 data class WebServiceTtsSynthesisResponse(
     val audioUrl: String,
     val engine: String,
     val language: String,
     val expiresAt: Long,
+    val silent: Boolean = false,
 )
```

#### [`KtorServer.kt`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/app/src/main/java/io/legado/app/web/KtorServer.kt) (line 546-552)
```diff
 val file = WebServiceTtsController.synthesize(request.text, request.language, request.bookUrl)
 call.respond(
     WebServiceTtsSynthesisResponse(
         audioUrl = "/api/v2/tts/audio/${file.id}",
         engine = WebServiceTtsController.capabilities(request.bookUrl).first,
         language = file.language,
         expiresAt = file.expiresAt,
+        silent = file.silent,
     )
 )
```

#### [`WebServiceTtsController.kt`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/app/src/main/java/io/legado/app/web/WebServiceTtsController.kt)
Add `silent` field to `TtsFile`:
```diff
- data class TtsFile(val id: String, val file: File, val language: String, val expiresAt: Long, val contentType: String)
+ data class TtsFile(val id: String, val file: File, val language: String, val expiresAt: Long, val contentType: String, val silent: Boolean = false)
```

Mark silent chunks:
```diff
 if (cleanText.isBlank() || cleanText.matches(Regex("^[\\s\\p{P}\\p{S}]+$"))) {
     writeSilentWav(file)
     val expiresAt = System.currentTimeMillis() + TTL_MILLIS
-    files[id] = TtsFile(id, file, requestedLocale.toLanguageTag(), expiresAt, "audio/wav")
+    files[id] = TtsFile(id, file, requestedLocale.toLanguageTag(), expiresAt, "audio/wav", silent = true)
```
(Same pattern for all other `writeSilentWav()` call sites in the method)

---

### B. Frontend — TypeScript Type Update

#### [`webService.ts`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/modules/web/src/api/webService.ts) (line 387-392)
```diff
 export type WebServiceTtsSynthesisResponse = {
   audioUrl: string
   engine: string
   language: string
   expiresAt: number
+  silent?: boolean
 }
```

---

### C. Frontend — Double Buffering & Optimizations

#### [`BookChapter.vue`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/modules/web/src/views/BookChapter.vue)

**C1. Increase prefetch ahead (line 656):**
```diff
-const WEB_TTS_PREFETCH_AHEAD = 2
+const WEB_TTS_PREFETCH_AHEAD = 3
```

**C2. Parallel prefetch (lines 768-788):**
```diff
 const prefetchWebTtsChunks = (chunks: WebTtsChunk[], token: number) => {
-  let previousTask: Promise<WebServiceTtsSynthesisResponse | null> = Promise.resolve(null)
   chunks.forEach(chunk => {
     if (token !== ttsPlaybackToken) return
     const key = webTtsChunkCacheKey(chunk)
     const existingTask = webTtsPrefetchCache.get(key)
     if (existingTask) {
-      previousTask = existingTask.catch(() => null)
       return
     }
-    const task = previousTask
-      .catch(() => null)
-      .then(() => {
-        if (token !== ttsPlaybackToken) return null
-        return synthesizeWebTtsChunk(chunk).catch(() => null)
-      })
+    const task = (token !== ttsPlaybackToken)
+      ? Promise.resolve(null)
+      : synthesizeWebTtsChunk(chunk).catch(() => null)
     webTtsPrefetchCache.set(key, task)
-    previousTask = task
   })
   trimWebTtsPrefetchCache()
 }
```

**C3. Skip silent chunk & remove cache-busting & add double buffer (lines 879-959):**

Refactor `playWebTtsChunk` to:
1. Check `result.silent` → skip audio download, use local `setTimeout(100)` instead
2. Remove cache-busting `?t=Date.now()`
3. After `audio.play()`, immediately pre-create next chunk's `Audio()` and call `.load()` (double buffer)

```typescript
// Simplified new flow:
const playWebTtsChunk = async (chunk, token, displayIndex, paragraphCount, prefetchChunks, afterCurrentReady, nextChunkResult) => {
  // ... get synthesis result ...
  
  // SKIP silent chunks — no network audio needed
  if (result.silent) {
    await updateWebTtsPosition(displayIndex, chunk.startParagraph, paragraphCount, token)
    await new Promise(resolve => setTimeout(resolve, 100))
    return
  }
  
  // Remove cache-busting — audio IDs are already unique
  const audioUrl = withWebSession(new URL(resolveWebServiceUrl(result.audioUrl)))
  // NO: audioUrl.searchParams.set('t', String(Date.now()))
  
  // If we have a pre-buffered audio from previous iteration, use it
  const audio = nextChunkResult?.preloadedAudio 
    ?? new Audio(audioUrl.toString())
  audio.preload = 'auto'
  // ... existing sync logic ...
  
  // Start playing
  audio.play()
  
  // WHILE current audio plays, preload next chunk's audio (DOUBLE BUFFER)
  let nextPreloaded: HTMLAudioElement | null = null
  if (prefetchChunks.length > 0) {
    const nextResult = await getWebTtsChunkSynthesis(prefetchChunks[0])
    if (nextResult?.audioUrl && !nextResult.silent) {
      const nextUrl = withWebSession(new URL(resolveWebServiceUrl(nextResult.audioUrl)))
      nextPreloaded = new Audio(nextUrl.toString())
      nextPreloaded.preload = 'auto'
      nextPreloaded.load() // Start downloading in background
    }
  }
  
  // Wait for current audio to finish
  await new Promise(resolve => { audio.onended = resolve; audio.onerror = resolve })
  
  // nextPreloaded is ready for the next iteration → near-zero gap
}
```

**C4. Update `speakCurrentChapter` loop to pass pre-buffered audio:**
```typescript
let nextPreloadedAudio: HTMLAudioElement | null = null

for (let chunkIndex = 0; chunkIndex < chunks.length; chunkIndex += 1) {
  // ... existing prefetch logic ...
  const preloadedForThis = nextPreloadedAudio
  nextPreloadedAudio = null  // Will be set by playWebTtsChunk
  
  const { preloaded } = await playWebTtsChunk(
    chunks[chunkIndex],
    token,
    displayIndex,
    chapterParagraphs.length,
    prefetchChunks,
    prefetchNextChapter,
    preloadedForThis,  // Pass in pre-buffered audio
  )
  nextPreloadedAudio = preloaded  // Save for next iteration
}
```

## Expected Improvement

| Metric | Before | After |
|---|---|---|
| Gap between chunks | 250-900ms | < 50ms |
| Silent chunk latency | 300ms (download + decode) | 100ms (local setTimeout) |
| Prefetch parallelism | Serial (1 at a time) | Parallel (up to 3) |
| Audio re-downloads | Every time (cache-bust) | Cached by browser |

## Test Criteria
- [ ] Compile success (Kotlin backend)
- [ ] `pnpm build` success (Web frontend)
- [ ] Web TTS: play chapter → listen for gaps between paragraphs → < 100ms gap
- [ ] Web TTS: silent/punctuation chunks → no audible "hiccup"
- [ ] Web TTS: multiple chapters → smooth transition
- [ ] Web TTS: stop/resume still works correctly

---
Next Phase: [Phase 04 — Verification & Testing](phase-04-testing.md)
