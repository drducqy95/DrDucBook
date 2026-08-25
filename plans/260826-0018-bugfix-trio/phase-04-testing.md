# Phase 04: Verification & Testing

Status: ⬜ Pending
Dependencies: Phase 01, 02, 03
Estimated: 15 min

## Objective
Xác nhận tất cả 3 bugfix hoạt động đúng. Build release và deploy lên thiết bị.

## Implementation Steps
1. [ ] Run Kotlin compile check: `.\gradlew.bat :app:compileAppDebugKotlin`
2. [ ] Run unit tests: `.\gradlew.bat testAppDebugUnitTest`
3. [ ] Build web frontend: `cd modules/web && pnpm build`
4. [ ] Build APK release: `.\gradlew.bat assembleAppRelease`
5. [ ] Install on device: `adb -s 2FK0224429001286 install -r app/build/outputs/apk/app/release/app-app-arm64-v8a-release.apk`
6. [ ] Manual test: Bug 1 (Local AI catalog opens)
7. [ ] Manual test: Bug 2 (Tunnel auto-reconnects on WiFi toggle)
8. [ ] Manual test: Bug 3 (TTS smooth between chunks)
9. [ ] Log errors to Trinity memory
10. [ ] Git commit & push

## Test Matrix

| Bug | Test | Expected Result |
|-----|------|----------------|
| 1 | AI Router → "Tải model GGUF" | AssetDeliveryRouteScreen opens |
| 2 | Toggle WiFi off/on during tunnel | Tunnel auto-reconnects |
| 2 | Kill cloudflared process | Auto-retry with backoff |
| 2 | User bấm Stop | No auto-retry |
| 3 | Web TTS play chapter | No audible gap between paragraphs |
| 3 | Web TTS with punctuation-only lines | No hiccup |
| 3 | Web TTS stop/resume | Still works |

---
Previous Phase: [Phase 03 — TTS Double Buffering](phase-03-tts-double-buffer.md)
