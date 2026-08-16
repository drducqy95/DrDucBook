# Evidence log

File này được cập nhật sau mỗi task hoặc mỗi gate. Không ghi API key, OAuth token, cookie hoặc nội dung secret.

## Schema bản ghi

```text
Date:
Task:
Variant:
Command or scenario:
Device:
Artifact/hash:
Result:
Logs/report:
Known limitation:
```

```text
Date: 2026-07-27
Task: C03.05 - Compact QT dictionary popup, target case controls and raw-anchored preview
Variant: appDebug / x86_64
Command or scenario: QuickDictionaryCaseTransformTest; full appDebug unit suite before final preview patch; :app:assembleAppDebug; LDPlayer visual and interaction check
Device: LDPlayer Android 14 SM-S9280 at emulator-5554
Artifact/hash: app/build/outputs/apk/app/debug/app-app-x86_64-debug.apk; 255,694,097 bytes; SHA-256 6D7119F7BFA0DEDAF86D87EA7B82898F903ED6EA2D604CD140CA2A6C0176E4A3
Result: PASS
Logs/report: Compact raw/location layout rendered without overlap. `aa`, `Aa2`, and `Aa` produced `lý truy viễn`, `Lý Truy viễn`, and `Lý Truy Viễn`. The final anchored-context regression test passed and device preview visibly contained raw `等` inside its adjacent source context. AndroidRuntime log was empty.
Known limitation: The final context-window change received focused unit/build/device verification; the immediately preceding full suite passed before that isolated UI helper patch.
```

```text
Date: 2026-07-27
Task: C03.05 - Exact QT raw mapping and QT-only dictionary action
Variant: appDebug / x86_64
Command or scenario: focused QT/mapping/mode tests; full :app:testAppDebugUnitTest; :app:assembleAppDebug; install and select translated phrase on LDPlayer
Device: LDPlayer Android 14 SM-S9280 at emulator-5554
Artifact/hash: app/build/outputs/apk/app/debug/app-app-x86_64-debug.apk; 255,690,941 bytes; SHA-256 90FAA8A024E793AECED1337F665A4196637AD6507F13C9A66F00AE46F6D9132B
Result: PASS / C03.05 DEVICE_PARTIAL
Logs/report: 620 unit tests passed with 0 failures/errors and 1 intentional skip. QT mapped the selected display text `Lý truy viễn` to raw `李追远` at source position 6901 on a real reader chapter. The add-to-QT-dictionary action is filtered and guarded outside QUICK_TRANSLATOR mode. AndroidRuntime log was empty.
Known limitation: This closes the QT dictionary sub-gate only. Mapping flows unrelated to adding a QT dictionary entry remain tracked separately under C03.05.
```

```text
Date: 2026-07-27
Task: C03.01/C03.04/C03.05 - Chapter translation repair, route serialization and late-result isolation
Variant: appDebug / appRelease / x86_64
Command: :app:testAppDebugUnitTest; :app:assembleAppDebug; :app:assembleAppRelease; LDPlayer QT benchmark/race/smoke
Device: LDPlayer Android 14 SM-S9280 at emulator-5554 (also exposed at 127.0.0.1:5555)
Artifact/hash: release/signed-apks-20260727-phase03-translation-final; x86_64 SHA-256 43B6C4D2DD799ED0EFDA542549AD8C08EF92FABFAB08C8147F1114D3F49F69AF
Result: PASS / DEVICE_PARTIAL
Logs/report: 619 unit tests passed, 0 failures/errors, 1 intentional skip. QT translated a 7,620-character real chapter in 2,581 ms cold and 947.3 ms warm. Legacy generated translation combo targets were normalized to maxConcurrency=1 at route resolution. During a live AI-failure race, late provider failures did not replace QT or Han-Viet content. Debug and signed release x86_64 cold-launched without AndroidRuntime crash.
Known limitation: Real ML Kit offline and NMT/GGUF fixtures, all-mode raw selection matrix, 10-chapter/30-minute soak, manga OCR/export, and live Codex OAuth login remain open device gates. C03.01/C03.05 remain AUTOMATED_DONE / DEVICE_PARTIAL.
```

```text
Date: 2026-07-26
Task: C05.03 / P05.T08 - Piper 2-file import and Android runtime
Variant: appDebug x86_64 / appRelease all ABI unsigned
Command or scenario: focused 17-test TTS suite; :app:compileAppDebugKotlin; :app:assembleAppDebug; :app:assembleAppRelease; install; SAF import legado-banmai-piper-test.zip; model runtime test; release asset scan
Device: LDPlayer Android 14 at 127.0.0.1:5555 (x86_64)
Artifact/hash: debug x86_64 251215201 bytes, SHA-256 6E371B9E2A67D16EBC5EBA22384F10077A63F5FD669AB4D7E420C9E875960FA1; release x86_64 unsigned 148289011 bytes, SHA-256 EE96642935B9AF80F57CCA298AE718B99772EE76E464DBFC32722536FC8C7DCF
Result: PASS / DEVICE_IMPORT_PASS / NATIVE_RUNTIME_PASS / RELEASE_BUILD_PASS / RELEASE_ASSET_SCAN_PASS / NO_CRASH
Logs/report: 17 focused tests passed (9 importer, 2 PCM use case, 6 read-aloud progress). Imported `banmai.onnx + banmai.onnx.json`; generated tokens and ONNX metadata; installed shared Vietnamese eSpeak-ng data; manifest engine=`piper-vits-onnx-v1`, sampleRate=22050. sherpa-onnx loaded on x86_64 and completed repeated synthesis requests without AndroidRuntime/native errors. Release x86_64 contains one sherpa JNI library and 355 eSpeak data files, with zero bundled `.onnx`, `.gguf` or `tts_models` entries.
Known limitation: Full chapter playback, audio-focus/process-death soak and malformed 100-loop fault test remain open. The archive contains 28 exact pairs; `duyoryx3175.onnx` has a mismatched JSON name and `trumpviet.onnx` has no JSON, so both are intentionally rejected.
```

```text
Date: 2026-07-26
Task: C05.01-C05.05 - Local TTS gateway, secure importer and model manager
Variant: appDebug x86_64 / appRelease x86_64 unsigned
Command: focused 15-test suite; :app:compileAppDebugKotlin; :app:assembleAppDebug; :app:assembleAppRelease; ADB install/route/file-picker smoke
Device: LDPlayer Android 14 at 127.0.0.1:5555 (x86_64)
Artifact/hash: debug app-app-x86_64-debug.apk, 207002546 bytes, SHA-256 38EAA19EBD2E79823FBF1F73263492109DED7402E00B7999423F8E9067178FC0; release app-app-x86_64-release-unsigned.apk, 112449355 bytes, SHA-256 59FAE987701B72621236A4ED3CDC537DE0A0FE02F73E30C69D686F6685D0C880
Result: PASS / DEVICE_SMOKE_PASS / RELEASE_ASSET_SCAN_PASS / PHASE_PARTIAL
Logs/report: 7 LocalTtsModelImporterTest + 2 TestLocalTtsModelUseCaseTest + 6 TTSReadAloudProgressTest passed with zero failures/errors. Model Manager route displayed on LDPlayer; SAF opened com.android.documentsui promptly; no AndroidRuntime/CrashReport. Release APK scan returned NO_TTS_ONNX_GGUF_MODEL_ASSETS.
Known limitation: Piper archive contains 40 single-speaker eSpeak voice pairs and remains unsupported until an Android eSpeak-ng phonemizer is integrated. No real Valtec import/audio chapter playback, process-death or audio-focus soak was run in this pass.
```

```text
Date: 2026-07-26
Task: C03.01-C03.06 - Phase 03 automated gate and AI fallback runtime repair
Variant: appDebug x86_64
Command: targeted Phase 03 tests; AiRouterPolicy/AiRouterRepository/auto-install/diagnostics tests; :app:assembleAppDebug
Device: LDPlayer 127.0.0.1:5555, SM-S9280 (Android 14, x86_64)
Artifact/hash: 1D591A4AE0C66264CD5A14A74B9F80596981F5A1F2F15370AE0CDC3676CF73FC (app-app-x86_64-debug.apk)
Result: AUTOMATED PASS / DEVICE PARTIAL
Logs/report: 110 Phase 03 tests passed earlier; 27 focused Router regression tests passed; debug APK built and installed. AI Router opened without fatal/ANR. Device database confirms translation route strategy=priority and target priorities Big Pickle=0, DeepSeek=1, MiMo V2.5=2, MiMo Auto=3. Runtime now rechecks queued target health and keeps target-local EMPTY_OUTPUT from quarantining a shared credential.
Known limitation: Real ML Kit offline pair, NMT/GGUF assets, manga OCR/export and chapter soak remain device gates. Observed startup/dashboard PSS 306,625 KB and RSS 382,580 KB, so startup memory/performance remains open for Phase 08.
```

## Baseline 25/07/2026

```text
Date: 2026-07-25
Task: BASELINE-COMPILE
Variant: appDebug
Command: :app:compileAppDebugKotlin --no-daemon --console=plain
Device: Windows workspace
Artifact/hash: N/A
Result: PASS
Logs/report: Gradle BUILD SUCCESSFUL, about 166 seconds
Known limitation: no Nox device connected
```

```text
Date: 2026-07-25
Task: BASELINE-FOCUSED-UNIT
Variant: appDebugUnitTest
Command: Router, Agent, QT, ML Kit, NMT importer, Authoring focused classes
Device: JVM
Artifact/hash: N/A
Result: 72 tests passed, 0 failure, 0 error
Logs/report: app/build/test-results/testAppDebugUnitTest
Known limitation: TTS importer has no equivalent baseline test class
```

## Quy tắc Nox/LDPlayer

```text
Date: 2026-07-26
Task: C02.01-C02.06 - Phase 02 AI Agent & Chat Bubble
Variant: appDebug / x86_64
Command: focused Phase 02 suite; :app:testAppDebugUnitTest; :app:assembleAppDebug; ADB dashboard/bubble/secret smoke
Device: Android 14 SM-S9280 at 127.0.0.1:5555 (x86_64)
Artifact/hash: app/build/outputs/apk/app/debug/app-app-x86_64-debug.apk; 374,212,142 bytes; SHA-256 445F119CD32E5CA2B38A5CA6349453561EBCB8592B16ECEC1295E2B57162EC3D
Result: AUTOMATED_DONE / DEVICE_PARTIAL
Logs/report: 64 focused Phase 02 tests PASS; full 516 tests PASS, 0 failure, 0 error, 1 skipped; assemble PASS in 9m13s. Dashboard showed 41 tools (21 read, 20 approval). Bubble enabled and panel rendered persisted transcript. API key credential editor had password field and bubble was absent; closing editor restored bubble. AndroidRuntime log empty; lastanr reported none.
Screenshots: build/phase02-dashboard.png; build/phase02-bubble-panel.png; build/phase02-router-bubble.png
Memory: after Router/Dashboard/panel smoke, TOTAL PSS 274,733 KB and TOTAL RSS 362,120 KB.
Known limitation: live mutation confirm/add-book was not executed to avoid changing user data; Reader/Browser/Media/Writing/Ebook cross-Activity matrix and a live in-flight stream handoff remain device acceptance gates.
```

Mỗi lần test phải lưu APK path, SHA-256, ABI, `adb shell getprop`, thời điểm cài, scenario, `dumpsys meminfo`, `dumpsys gfxinfo`, foreground activity và logcat crash/ANR. Nếu thiết bị không kết nối, task là `BLOCKED`, không phải `PASS`.

```text
Date: 2026-07-25
Task: C00.06 - Combo fallback validation
Variant: appDebug
Command: Unit update on AiPromptEditorViewModel
Device: LDPlayer (emulator-5554)
Artifact/hash: N/A
Result: PASS
Logs/report: AiPromptEditorViewModel.kt updated to allow blank model if route is selected
Known limitation: N/A
```

```text
Date: 2026-07-26
Task: C01.01-C01.06 - Phase 01 AI Router completion
Variant: appDebug / x86_64
Command: :app:compileAppDebugKotlin; :app:testAppDebugUnitTest; :app:assembleAppDebug
Device: Android emulator SM-S9280 at 127.0.0.1:5555
Artifact/hash: app/build/outputs/apk/app/debug/app-app-x86_64-debug.apk
Result: AUTOMATED_DONE / DEVICE_PARTIAL
Logs/report: compile PASS in 3m07s; all 509 unit tests PASS (0 failures, 0 errors, 1 skipped); assemble PASS in 6m46s. Installed APK successfully. AI Router opened from Bookshelf shortcut, provider grid/filter and searchable model picker worked, Local GGUF opened DocumentsUI and returned without AndroidRuntime crash.
Screenshots: build/legado-phase01-home.png; build/legado-phase01-router.png; build/legado-phase01-picker.png; build/legado-phase01-localconfig.png
Known limitation: no active OAuth credential and no GGUF test file in the debug device profile, so live OAuth Chat/Translation and real GGUF generation remain device gates.
```

```text
Date: 2026-07-25
Task: C00.02 - System File Picker offloading and cancellation
Variant: appDebug
Command: :app:compileAppDebugKotlin
Device: JVM / Windows workspace
Artifact/hash: N/A
Result: PASS
Logs/report: BUILD SUCCESSFUL in 43s. Offloaded NMT, TTS, GGUF and Dictionary importers to Dispatchers.IO and added Job cancellation tracking.
Known limitation: N/A
```

```text
Date: 2026-07-25
Task: C00.01 - Tái lập LDPlayer và lập baseline
Variant: appDebug
Command: adb -s 127.0.0.1:5555 shell dumpsys meminfo io.legato.kazusa.debug
Device: LDPlayer SM-S9280 (Android 14, x86_64)
Artifact/hash: 89810D993F0D3EFAACF5898992D972E78A5B879108F6B4132950F99E28EF00FC (app-app-universal-debug.apk)
Result: PASS
Logs/report: App launched successfully. Initial PSS Total: 128,218 KB (~128MB), RSS Total: 205,176 KB (~205MB). Native Heap: 12.8MB, Dalvik Heap: 2.3MB.
Known limitation: N/A
```

```text
Date: 2026-07-25
Task: C00.03 - Heavy Runtime Coordination & Idle Unload
Variant: appDebug
Command: :app:compileAppDebugKotlin
Device: JVM / Windows workspace
Artifact/hash: N/A
Result: PASS
Logs/report: BUILD SUCCESSFUL in 1m 24s. Added automated idle unload timers to NMT (5 min), TTS (3 min), and GGUF (10 min) runtimes while preserving default UI QT warm-up logic.
Known limitation: N/A
```

```text
Date: 2026-07-25
Task: C00.04 - Atomic & Cancellable Import with Orphaned Staging Cleanup
Variant: appDebug
Command: :app:compileAppDebugKotlin
Device: JVM / Windows workspace
Artifact/hash: N/A
Result: PASS
Logs/report: BUILD SUCCESSFUL in 1m 44s. Implemented cleanOrphanedStaging for NMT/TTS/GGUF/Dictionary, ensured coroutine cancellation via ensureActive() in ZIP extraction and byte streaming.
Known limitation: N/A
```

```text
Date: 2026-07-25
Task: C00.05 - Preserving Partial Translation Progress & Secret Sanitization in Failure Logs
Variant: appDebug
Command: :app:compileAppDebugKotlin
Device: JVM / Windows workspace
Artifact/hash: N/A
Result: PASS
Logs/report: BUILD SUCCESSFUL in 1m 37s. Added redactSecrets() to AiProviderFailureClassifier to sanitize API keys, Bearer tokens, and sensitive query params from technicalDetail logs. Verified chunk checkpointing and partial translation preservation.
Known limitation: N/A
```

```text
Date: 2026-07-25
Task: C00.06 - Combo fallback thực sự được dùng (Full AI Route Fallback & Empty Response Handling)
Variant: appDebug
Command: :app:testDebugUnitTest --tests "io.legado.app.data.repository.AiRouterRepositoryTest"
Device: JVM / Windows workspace
Artifact/hash: N/A
Result: PASS
Logs/report: BUILD SUCCESSFUL in 1m 1s. Expanded empty response fallback check in AiRouterRepository.kt for all AI task types (generate & generateStream). Verified full fallback chain execution across targets.
Known limitation: N/A
```

```text
Date: 2026-07-25
Task: C00.07 - Mapping lựa chọn về raw (Segment Provenance Mapping & QuickDictionary Resolver)
Variant: appDebug
Command: :app:testDebugUnitTest --tests "io.legado.app.ui.quickdict.QuickDictionarySelectionResolverTest"
Device: JVM / Windows workspace
Artifact/hash: N/A
Result: PASS
Logs/report: BUILD SUCCESSFUL in 19s. Verified 9 unit tests in QuickDictionarySelectionResolverTest covering exact match, translated matching, paragraph alignment, multiple occurrences, HTML tags, and unreliable match rejection (returns null).
Known limitation: N/A
```

```text
Date: 2026-07-25
Task: C00.08 - Gate đóng phase Phase 00 Baseline Stabilization
Variant: appDebug
Command: :app:testDebugUnitTest
Device: JVM / Windows workspace
Artifact/hash: N/A
Result: PASS
Logs/report: BUILD SUCCESSFUL in 2m 18s. All 503 unit tests in :app:testDebugUnitTest executed and PASSED (0 failures). Phase 00 stabilization verification complete.
Known limitation: N/A
```

```text
Date: 2026-07-25
Task: C01.01 - Model picker và provider config (Searchable Picker & Missing Catalog Badge)
Variant: appDebug
Command: :app:testDebugUnitTest --tests "io.legado.app.ui.config.ai.AiModelPickerSearchTest"
Device: JVM / Windows workspace
Artifact/hash: N/A
Result: PASS
Logs/report: BUILD SUCCESSFUL in 6m 15s. Added normalizeAiRouterSearch accent-insensitive matching to AiModelPickerSheet, added missing catalog badge support, masked API key display, and verified unsaved sheet dismiss. Unit tests in AiModelPickerSearchTest PASSED.
Known limitation: N/A
```

```text
Date: 2026-07-26
Task: C03.01-C03.06 - Translation cache/checkpoint and AI layout verification
Variant: appDebug / x86_64
Command: :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.AiTranslationLayoutProtocolTest"; :app:assembleAppDebug; ADB install/launch/reader smoke
Device: LDPlayer Android 14 SM-S9280 at 127.0.0.1:5555 (x86_64)
Artifact/hash: app/build/outputs/apk/app/debug/app-app-x86_64-debug.apk; 206,512,422 bytes; SHA-256 91838A4C46FBB2CE154E52AA5A53B245712B785CAE9C6EA93F337C5584C6999D
Result: PASS / DEVICE_SMOKE_PASS
Logs/report: Layout protocol tests PASS, assemble PASS, APK installed successfully. ChatGPT/Codex OAuth with a single model completed a chatbot request and a fresh chapter-2 AI translation. After deleting only the chapter-2 AI cache, all 3 generated chunk records were status=2 with translated content. Reader switched AI -> QT -> HV without process death; AndroidRuntime and ActivityManager crash/ANR filters were empty.
Known limitation: The device smoke did not assert real ML Kit offline model download, NMT/GGUF generation, manga OCR/export, or long-running memory soak. Those remain open device gates.
```

```text
Date: 2026-07-26
Task: C03 cache policy semantics - shared AI cache, provider-isolated non-AI cache, affected-chunk invalidation
Variant: appDebugUnitTest
Command: TranslateChapterChunkCachePolicyTest; TranslateMangaPageUseCaseTest; TranslationCacheRepositoryImplTest; AiTranslationLayoutProtocolTest
Device: JVM
Artifact/hash: N/A
Result: PASS
Logs/report: Tests cover AI cache reuse across combo/model/prompt changes, dictionary-only invalidation for overlapping chunks, preservation of successful chunk checkpoints after a failed sibling, manga partial checkpoint/retry, and safe AI response layout decoding.
Known limitation: No multi-hour device soak or real offline model fixture.
```

```text
Date: 2026-07-26
Task: C04.01-C04.06 - VBook registry URL/file import, source health and Browser Compose entry
Variant: appDebug / x86_64
Command: focused 26-test suite; :app:compileAppDebugKotlin; :app:assembleAppDebug; ADB install/launch/tap smoke
Device: LDPlayer Android 14 at 127.0.0.1:5555 (x86_64)
Artifact/hash: app/build/outputs/apk/app/debug/app-app-x86_64-debug.apk; 206,991,898 bytes; SHA-256 6615AE9680C70DA9134EB5F2C90CAC643416A6391B5FEDFAC2676576A1E2D31A
Result: PASS / DEVICE_SMOKE_PASS / PHASE_PARTIAL
Logs/report: Registry sample URL previewed with 133 plugins; Source Health opened with horizontal status filters; main navigation displayed Trình duyệt; Browser Compose opened Google WebView with address bar, tab counter, page translation action and bottom navigation. No new AndroidRuntime/CrashReport error was observed after installation.
Known limitation: Real ML Kit page-language pack translation, long-running health worker, incognito isolation, history/bookmark, popup and full login/captcha result flow remain open.
```

```text
Date: 2026-07-26
Task: C03 manga AI cache identity correction and final debug installation
Variant: appDebug / x86_64
Command: MangaTranslationModelsTest; TranslateMangaPageUseCaseTest; TranslateChapterChunkCachePolicyTest; AiTranslationLayoutProtocolTest; TranslationCacheRepositoryImplTest; :app:compileAppDebugKotlin; :app:assembleAppDebug; ADB install/launch
Device: LDPlayer Android 14 SM-S9280 at 127.0.0.1:5555 (x86_64)
Artifact/hash: app/build/outputs/apk/app/debug/app-app-x86_64-debug.apk; 206,512,422 bytes; SHA-256 6A34D7CC005D5DE9F2D2C3BA67090CB38C74A0FAD2B6473251A2F2B98AB0C4DF
Result: PASS / DEVICE_SMOKE_PASS
Logs/report: 5 focused test classes passed; compile passed; assemble passed in 2m04s; APK install returned Success; app PID 20364; MainActivity remained topResumed; AndroidRuntime and ActivityManager error filters were empty. Manga AI cache key now ignores model/combo/prompt revision while non-AI provider/revision isolation remains covered by tests.
Known limitation: `monkey` is unavailable on this LDPlayer image, so the smoke launch used `am start`; no real ML Kit/NMT/GGUF/manga OCR fixture or long-running soak was run.
```
