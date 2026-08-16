# Phase 08 — Tích hợp, migration, hardening và release gate

> Trạng thái 2026-07-27: implementation và runtime gate đã hoàn tất trên Room 105/LDPlayer.
> Strict lint toàn repo còn bị chặn bởi backlog 1030 lỗi chưa có baseline. Xem
> [execution/PHASE-08-REPORT.md](./execution/PHASE-08-REPORT.md) để biết kết quả và artifact.

## 1. Kết quả phải đạt

Các phase được chọn cho release hoạt động cùng nhau trên dữ liệu hiện có, không mất dữ liệu/secret, không phá navigation/Reader/source/media và có bằng chứng unit, migration, instrumentation, performance, security, accessibility và Nox end-to-end.

Phase này bắt đầu từ đầu dự án dưới dạng continuous gate nhưng chỉ đóng sau cùng.

## 2. Phạm vi

### Trong phạm vi

- Baseline fixtures và regression matrix.
- Room schema/migration theo từng phase.
- Cross-feature integration.
- Process death, storage full, network change và cancellation.
- Security/fuzz/redaction.
- Performance/memory/battery benchmarks.
- Accessibility/localization/adaptive layouts.
- Feature flag, rollout, rollback và release notes.
- APK ABI/universal build và Nox/device verification.

### Ngoài phạm vi

- Tuyên bố hỗ trợ provider/source/plugin không qua test.
- Bypass DRM/captcha/quota/provider policy.
- Dọn/refactor toàn codebase không liên quan.
- Push GitHub hoặc phát hành store nếu chưa có lệnh riêng của user.

## 3. Baseline và fixture

### P8.1 — Golden fixture pack

Lưu fixture test nhỏ, hợp pháp và không chứa secret:

- Database schema 98 có Book, source, cookie, translation cache, AI provider/model/route/credential metadata, chat/memory và authoring project.
- Provider mock: OpenAI Chat/Responses, Anthropic, OAuth refresh, SSE partial/error, free/no-auth.
- ML Kit state mock và GGUF invalid/small test metadata.
- VBook single plugin/registry cho novel/comic/video/audio/TTS cùng malformed variants.
- Source healthy/auth/captcha/rule/HTTP/offline.
- Browser local test pages cho login/cookie/upload/download/popup/DOM translation.
- Media direct MP3/MP4, HLS, DASH, subtitles và expiring URL mock.
- EPUB reflow/fixed/drop-cap/image/font và authoring project recovery.
- TTS valid/invalid package; native audio fixture nếu license cho phép.

Không commit model lớn, API key, user database, copyrighted book/media hoặc generated APK vào repository.

## 4. Chiến lược migration

### P8.2 — Quy tắc version

- Codebase tại thời điểm viết dùng Room version 98; implementer phải lấy version hiện hành làm baseline khi bắt đầu phase.
- Mỗi feature schema dùng một migration nhỏ riêng, không dồn nhiều subsystem vào một version.
- AutoMigration chỉ dùng khi thay đổi thuần additive và không cần transform/secret migration.
- Manual migration bắt buộc cho:
  - API key plaintext → secretRef/credential.
  - Translation revision/final state.
  - Agent proposal/trace/skill/plugin.
  - Source health history.
  - Media download persistent task.
- Authoring project hiện file-backed: dùng document schema version + upgrader atomic, không ép vào Room nếu chưa cần query quan hệ.

### P8.3 — Migration safety

- Backup DB/file project trước migration có transform.
- Transaction và idempotency: retry sau crash không tạo duplicate credential/revision/task.
- Không xóa column/dữ liệu legacy trong cùng release chuyển đổi; chỉ clear secret plaintext sau encrypted write verify.
- Schema JSON mới phải được export vào `app/schemas`.
- `MigrationTest` phải mở fixture version trước, chạy migration và kiểm tra row/value/index/foreign key.
- Test downgrade không bắt buộc hỗ trợ, nhưng release notes phải cảnh báo restore APK cũ không đọc được DB mới.

## 5. Cross-feature integration

### P8.4 — AI flow

- AI Settings policy → AI Router route → Chat/Agent/Translation/Writing/Ebook.
- OAuth login/probe → target → chat request → attempt history.
- Agent bubble context → read tool → proposal → confirmed mutation → UI state refresh.
- LocalAI/ML Kit missing model phải trả action phù hợp, không generic config error.

### P8.5 — Source/browser/media flow

- Source daily error → notification → diagnostic → Browser login/captcha → cookie sync → re-probe healthy.
- VBook registry import → capability → search/book → media resolve → player → download → offline/export.
- Agent source search dùng cùng gateway, không bypass source enable/health/auth policy.

### P8.6 — Translation/authoring flow

- Chapter raw → machine → user edit → final → Reader.
- Ebook clone đúng revision → block edit → preview → export → import/read lại.
- Writing preproduction → AI suggestion via Router → apply revision → Ebook export.
- Drop cap round-trip không thay text/raw hash ngoài style metadata.

### P8.7 — TTS/media coexistence

- Audio focus giữa TTS, audiobook và video.
- Starting media pauses TTS theo policy; resume không tự động nếu user đã stop.
- Notification/media session không chồng action sai service.
- Bubble/Agent không chiếm audio focus khi chỉ chat text.

## 6. Security hardening

### P8.8 — Secret và privacy

- Quét source/log/report/backup cho API key, bearer token, OAuth refresh token, cookie, password và full Authorization header.
- Screenshot/context Agent tắt ở secret screens.
- Export diagnostics redact query/header/providerData.
- Backup Agent memory/chat có cảnh báo privacy và tùy chọn exclude.

### P8.9 — Fuzz/threat tests

- VBook ZIP/JSON/script: traversal, zip bomb, huge response, reflection, filesystem và private IP.
- Browser: unsafe bridge, file access, SSL, mixed content, malicious download filename và intent URL.
- EPUB/TTS/media import: traversal, decompression limit, malformed manifest và path escape.
- Agent: prompt injection trong web/book/source content không được tự tăng quyền tool.
- Permission token replay/tamper/expiry và bulk target substitution.
- AI SSE/JSON: malformed event, duplicate tool call, partial stream và oversized output.

## 7. Hiệu năng và độ tin cậy

### P8.10 — Benchmark

- Startup cold/warm trước/sau khi thêm coordinator/registry.
- AI Router grid với 99 provider và hàng nghìn model.
- Agent chat 1.000 messages/memory search/trace list.
- Bookshelf 10.000 books; source list 1.000 entries.
- Manga page lớn, OCR/overlay và scroll/zoom memory.
- Media time-to-first-frame, seek, HLS buffering và download resume.
- Ebook 500 chapters/5.000 blocks; preview/export memory/time.
- TTS import/synthesis latency và model memory.

Ngưỡng release phải được ghi từ baseline thực tế. Không chấp nhận OOM, retained Activity/WebView/player/native handle hoặc ANR.

### P8.11 — Fault injection

- Kill process trong OAuth, download, TTS import, Ebook autosave, Agent proposal và source worker.
- Storage full giữa atomic write/export.
- Network đổi Wi-Fi/mobile/offline giữa stream/download/probe.
- Clock/timezone đổi ảnh hưởng token expiry/schedule.
- Cancel coroutine/tool/model/native generation.
- DB locked/corrupt fixture và project recovery.

## 8. Accessibility, localization và UX quality

### P8.12 — Accessibility

- TalkBack semantics/contentDescription cho provider icon, bubble, media controls và canvas selection.
- Touch target tối thiểu, keyboard/d-pad alternative cho drag/reorder.
- Font scale 1.0–2.0 không cắt action quan trọng.
- Contrast, reduced motion và screen rotation.
- Bubble không che navigation/Reader controls; có cách tắt rõ ràng.

### P8.13 — Localization/adaptive

- New strings có `values` và `values-vi`; không hard-code user text trong composable/viewmodel.
- Provider/model IDs, protocol và file names giữ nguyên kỹ thuật.
- Phone 360 dp, tablet 600/840 dp, portrait/landscape và edge-to-edge.
- Miuix/Material 3 branch chỉ khi component thực sự cần; behavior/state phải giống nhau.

## 9. Rollout và rollback

### P8.14 — Feature flags

Flags riêng:

- New AI Router UI.
- Agent mutation/skill/plugin.
- Chat bubble.
- Manga translation.
- Browser page translation.
- Source daily health.
- Unified media download.
- Fixed-layout Ebook Editor.

Core schema không được phụ thuộc việc UI flag bật/tắt. Flag tắt phải giữ đường đọc dữ liệu mới và tránh data loss.

### P8.15 — Release stages

1. Internal debug + mock servers.
2. Nox x86_64 smoke.
3. arm64 physical-device smoke, đặc biệt LocalAI/TTS/Media.
4. NoR8 build để debug native/runtime.
5. Release/R8 build với mapping/resource shrink checks.
6. Canary backup/migration trên bản sao DB thật.
7. Release notes và known limitations.

## 10. File tác động

### File hiện có cần sửa

- `app/src/main/java/io/legado/app/data/AppDatabase.kt`
- `app/src/main/java/io/legado/app/data/DatabaseMigrations.kt`
- `app/src/androidTest/java/io/legado/app/MigrationTest.kt`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/io/legado/app/App.kt`
- `app/src/main/java/io/legado/app/di/appModule.kt`
- `app/src/main/java/io/legado/app/di/appDatabaseModule.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavGraph.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavigator.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-vi/strings.xml`
- `app/schemas/io.legado.app.data.AppDatabase/*.json`

Ngoài ra, mọi file production/test đã liệt kê trong Phase 01–07 được đưa vào regression review của phase này.

### File tạo mới dự kiến

- `app/src/androidTest/java/io/legado/app/integration/AiAgentIntegrationTest.kt`
- `app/src/androidTest/java/io/legado/app/integration/SourceBrowserIntegrationTest.kt`
- `app/src/androidTest/java/io/legado/app/integration/VbookMediaIntegrationTest.kt`
- `app/src/androidTest/java/io/legado/app/integration/TranslationAuthoringIntegrationTest.kt`
- `app/src/androidTest/java/io/legado/app/integration/TtsMediaAudioFocusTest.kt`
- `app/src/test/java/io/legado/app/security/AgentPermissionSecurityTest.kt`
- `app/src/test/java/io/legado/app/security/ImportArchiveSecurityTest.kt`
- `app/src/test/java/io/legado/app/security/DiagnosticRedactionTest.kt`
- `app/src/androidTest/java/io/legado/app/benchmark/UpgradeMacrobenchmark.kt`
- `docs/guide/ai-router.md`
- `docs/guide/ai-agent.md`
- `docs/guide/mlkit-models.md`
- `docs/guide/vbook-import.md`
- `docs/guide/browser.md`
- `docs/guide/local-tts-model.md`
- `docs/guide/ebook-editor.md`

## 11. Test bắt buộc phải pass

### Full automated gates

```powershell
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:lintAppDebug --no-daemon --console=plain
.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain
.\gradlew.bat :app:assembleAppNoR8 --no-daemon --console=plain
.\gradlew.bat :app:connectedAppDebugAndroidTest --no-daemon --console=plain
```

Trước release chạy thêm task release đúng variant CI của repository. Nếu task `lintAppDebug` khác tên trong Gradle hiện hành, dùng task lint tương đương được `gradlew tasks` xác nhận và cập nhật tài liệu.

### Migration gates

- Migrate fixture 98 → latest và từng intermediate version mới.
- Giữ Book, BookChapter, BookSource, Cookie, TranslationCache, AI profiles/routes/credentials metadata, Chat/Memory và project references.
- API key plaintext được clear sau verify encrypted secret.
- Migration rerun/idempotency fixture không tạo duplicate.
- Schema export committed và Room schema validation pass.

### Cross-feature Nox/physical-device checklist

1. Upgrade APK trên dữ liệu có sẵn, không clear data.
2. AI Router Free/API/OAuth/Local route.
3. Agent bubble + proposal mutation + memory.
4. ML Kit download/offline/final translation.
5. VBook import + source health + Browser login/cookie.
6. Video/audio play + download/offline/export + audiobook import.
7. TTS model import/test/read.
8. Writing preproduction + Ebook fixed block + preview/export + Reader drop cap.
9. Process death/restart ở ít nhất Agent run, media download và Ebook autosave.
10. Logcat không có FATAL/ANR/Koin/Room migration error và không chứa secret.

## 12. Điều kiện đóng phase

- Toàn bộ automated gate của release scope pass.
- Migration trên bản sao dữ liệu thật pass và có backup/rollback procedure.
- Cross-feature checklist có bằng chứng ảnh/log/report.
- Security/redaction/fault tests không còn issue mức release-blocking.
- Documentation và known limitations khớp behavior thực tế.
- APK debug/NoR8/release cần thiết build được trên ABI mục tiêu.
