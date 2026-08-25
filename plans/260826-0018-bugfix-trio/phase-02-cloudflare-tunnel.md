# Phase 02: Cloudflare Tunnel Auto-Restart & Stability

Status: ⬜ Pending
Dependencies: None (parallel với Phase 01)
Estimated: 30 min

## Objective
Cloudflare tunnel tự ngắt khi mạng chập chờn (Error 1033) và không tự phục hồi. Cần thêm auto-restart và xử lý network recovery.

## Root Cause Analysis

### 6 Nguyên nhân gây Error 1033

| # | Nguyên nhân | Code Location |
|---|---|---|
| 1 | **No Auto-Restart** | [`CloudflareTunnelManager.kt:174-182`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/app/src/main/java/io/legado/app/service/CloudflareTunnelManager.kt#L174-L182): `waitFor()` → `fail()` sets `ERROR/OFF` permanently |
| 2 | **Network Flap Kills Tunnel** | [`MyViewModel.kt:100`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/app/src/main/java/io/legado/app/ui/main/my/MyViewModel.kt#L100): `if (address.isEmpty()) CloudflareTunnelManager.stop()` — never restarts |
| 3 | **Stale DNS** | `CLOUDFLARED_ANDROID_DNS` set at process start → unreachable after network switch |
| 4 | **No Keepalive** | `--protocol http2` TCP → silent NAT/firewall drop during idle |
| 5 | **Quick Tunnel Ephemeral** | `trycloudflare.com` tunnels auto-close after idle |
| 6 | **Android Process Killer** | Android 12+ Phantom Process Killer / EMUI PowerGenie kills child `libcloudflared.so` |

### Most Impactful Fix
Nguyên nhân #2 (`MyViewModel` kill tunnel on network drop) là **quan trọng nhất** — WiFi chập chờn 1 giây cũng đủ kill tunnel vĩnh viễn.

## Implementation Steps
1. [x] Phân tích root cause (6 nguyên nhân)
2. [ ] **Add `RECONNECTING` phase** to `CloudflareTunnelPhase` enum
3. [ ] **Add auto-restart with exponential backoff** to `CloudflareTunnelManager`
4. [ ] **Remove aggressive tunnel kill** in `MyViewModel`
5. [ ] **Add `retryIfFailed()` method** for network recovery
6. [ ] **Reset retry counter on successful connection**

## Files to Modify

### A. [`CloudflareTunnelModels.kt`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/app/src/main/java/io/legado/app/domain/webservice/CloudflareTunnelModels.kt)

Add `RECONNECTING` phase:
```diff
 enum class CloudflareTunnelPhase {
     STOPPED,
     STARTING,
+    RECONNECTING,
     CONNECTED,
     ERROR,
 }
```

Update `requiresPairing` to include `RECONNECTING`:
```diff
 val requiresPairing: Boolean
     get() = mode != CloudflareTunnelMode.OFF &&
         pairingEnabled &&
         phase != CloudflareTunnelPhase.STOPPED &&
-        phase != CloudflareTunnelPhase.ERROR
+        phase != CloudflareTunnelPhase.ERROR &&
+        phase != CloudflareTunnelPhase.RECONNECTING
```

---

### B. [`CloudflareTunnelManager.kt`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/app/src/main/java/io/legado/app/service/CloudflareTunnelManager.kt)

**B1. Store last start params for retry:**
```kotlin
private var lastStartContext: Context? = null
private var lastStartMode: CloudflareTunnelMode? = null
private var lastPublicUrl: String = ""
private var lastToken: String = ""
private var retryCount = 0
private const val MAX_RETRIES = 5
private val RETRY_DELAYS = longArrayOf(2000, 4000, 8000, 16000, 32000)
```

**B2. On process exit (non-user-stop), auto-retry:**
```kotlin
// In the launch block, after waitFor():
val exitCode = started.waitFor()
synchronized(lock) {
    if (process === started) {
        process = null
        if (retryCount < MAX_RETRIES && !userStopped) {
            retryCount++
            val delay = RETRY_DELAYS.getOrElse(retryCount - 1) { 32000 }
            _state.value = _state.value.copy(
                phase = CloudflareTunnelPhase.RECONNECTING,
                detail = "Reconnecting in ${delay / 1000}s (attempt $retryCount/$MAX_RETRIES)…",
            )
            scope.launch {
                kotlinx.coroutines.delay(delay)
                restartFromLastParams()
            }
        } else {
            val contextMsg = diagnosticLines.takeLast(3).joinToString(" | ")
            fail("Cloudflare Tunnel stopped (code $exitCode): $contextMsg")
        }
    }
}
```

**B3. Reset retry counter on successful connection:**
```kotlin
private fun handleOutput(started: Process, line: String) {
    // ... existing code ...
    if (connected) {
        retryCount = 0  // ← Reset on success
        _state.value = _state.value.copy(
            phase = CloudflareTunnelPhase.CONNECTED,
            publicUrl = quickUrl ?: _state.value.publicUrl,
            detail = "Connected through Cloudflare.",
        )
    }
}
```

**B4. Add `retryIfFailed()` for network recovery:**
```kotlin
fun retryIfFailed(context: Context) {
    val current = _state.value
    if (current.phase != CloudflareTunnelPhase.ERROR) return
    retryCount = 0  // Fresh start on network recovery
    when (current.mode) {
        CloudflareTunnelMode.QUICK -> {
            // Re-read port from WebService
            val port = lastLocalPort ?: return
            startQuick(context, port)
        }
        CloudflareTunnelMode.NAMED -> {
            if (lastToken.isNotBlank() && lastPublicUrl.isNotBlank()) {
                startNamed(context, lastToken, lastPublicUrl)
            }
        }
        CloudflareTunnelMode.OFF -> Unit
    }
}
```

**B5. Add `userStopped` flag to distinguish user stop vs crash:**
```kotlin
private var userStopped = false

fun stop() {
    userStopped = true
    val stopped = synchronized(lock) { process.also { process = null } }
    stopped?.destroy()
    // ... existing cleanup ...
    userStopped = false  // Reset after cleanup
}

// In start(): reset flag
private fun start(...) {
    userStopped = false
    retryCount = 0
    // ... existing code ...
}
```

---

### C. [`MyViewModel.kt`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/app/src/main/java/io/legado/app/ui/main/my/MyViewModel.kt)

Remove aggressive kill on network drop, add recovery:
```diff
 FlowEventBus.with<String>(EventBus.WEB_SERVICE)
     .collect { address ->
-        if (address.isEmpty()) CloudflareTunnelManager.stop()
+        if (address.isNotEmpty()) {
+            // Network recovered — try restarting tunnel if it failed
+            CloudflareTunnelManager.retryIfFailed(context)
+        }
         _uiState.update { state ->
             state.copy(
                 isWebServiceRun = address.isNotEmpty(),
                 webServiceAddress = address,
             )
         }
     }
```

---

### D. UI Handling of `RECONNECTING` Phase

Check [`MyScreen.kt`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/app/src/main/java/io/legado/app/ui/main/my/MyScreen.kt) to ensure `RECONNECTING` phase displays properly with the tunnel detail message.

## Edge Cases
- **WiFi off/on nhanh**: Tunnel process vẫn đang chạy → `cloudflared` tự reconnect nội bộ, không cần can thiệp
- **WiFi chuyển mạng**: DNS cũ thất bại → `cloudflared` exit → auto-retry với DNS mới
- **User bấm Stop**: `userStopped = true` → không auto-retry
- **5 lần retry thất bại**: Chuyển sang ERROR, user phải restart manual

## Test Criteria
- [ ] Compile success
- [ ] Start Quick Tunnel → toggle WiFi off 3 giây → on lại → tunnel tự reconnect
- [ ] Start tunnel → kill cloudflared process → auto-retry lên tới 5 lần
- [ ] User bấm Stop → không auto-retry
- [ ] `RECONNECTING` phase hiển thị đúng trên MyScreen

---
Next Phase: [Phase 03 — TTS Double Buffering](phase-03-tts-double-buffer.md)
