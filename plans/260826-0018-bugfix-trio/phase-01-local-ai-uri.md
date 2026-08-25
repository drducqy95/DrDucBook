# Phase 01: Local AI URI Fix

Status: ⬜ Pending
Dependencies: None
Estimated: 5 min

## Objective
Fix crash khi user bấm "Tải model GGUF" trong AI Router screen.

## Root Cause Analysis

### Stack Trace
```
java.lang.IllegalArgumentException: Can't open drducbook-asset://catalog/local-ai-hy-mt2.
Caused by: ActivityNotFoundException: No Activity found to handle 
  Intent { act=android.intent.action.VIEW dat=drducbook-asset://catalog/local-ai-hy-mt2 }
```

### Why It Crashes
[`AiRouterScreen.kt:134`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/app/src/main/java/io/legado/app/ui/ai/router/AiRouterScreen.kt#L134) uses `LocalUriHandler.current.openUri(url)` which delegates to Android's `startActivity(Intent(ACTION_VIEW, uri))`.

`drducbook-asset://` is an **internal app scheme** — không có Activity nào register intent filter cho scheme này trong `AndroidManifest.xml`. Android ném `ActivityNotFoundException`.

### Correct Pattern (Already Used Elsewhere)
Tất cả screen khác (AiProviderEditScreen, TtsModelManagerScreen, ReadConfigScreen) dùng đúng pattern:
```kotlin
context.openUrl(effect.url) // from ContextExtensions.kt
```

[`ContextExtensions.kt:416-433`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/app/src/main/java/io/legado/app/utils/ContextExtensions.kt#L416-L433) intercepts `drducbook-asset://` URIs → routes to `MainActivity.createAssetDeliveryIntent()` → `AssetDeliveryRouteScreen`.

**`AiRouterScreen` là FILE DUY NHẤT** dùng `LocalUriHandler.current` thay vì `context.openUrl()`.

## Implementation Steps
1. [x] Phân tích root cause
2. [ ] Replace `LocalUriHandler.current.openUri()` → `context.openUrl()`
3. [ ] Remove unused `LocalUriHandler` import
4. [ ] Add `LocalContext` and `openUrl` imports

## Files to Modify
- [`AiRouterScreen.kt`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/app/src/main/java/io/legado/app/ui/ai/router/AiRouterScreen.kt) — Fix effect handler

## Changes

```diff
// Line 102
- val uriHandler = LocalUriHandler.current
+ val context = LocalContext.current

// Line 134
- is AiRouterEffect.OpenUrl -> uriHandler.openUri(effect.url)
+ is AiRouterEffect.OpenUrl -> context.openUrl(effect.url)
```

## Test Criteria
- [ ] Compile success: `.\gradlew.bat :app:compileAppDebugKotlin`
- [ ] AI Router → "Tải model GGUF" → Opens AssetDeliveryRouteScreen (not crash)
- [ ] Standard URL (`https://...`) still opens in external browser

---
Next Phase: [Phase 02 — Cloudflare Tunnel Auto-Restart](phase-02-cloudflare-tunnel.md)
