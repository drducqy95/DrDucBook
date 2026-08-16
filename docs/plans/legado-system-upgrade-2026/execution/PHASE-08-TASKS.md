# Phase 08 — Integration, Migration & Release — Kế hoạch triển khai

Spec gốc: [../PHASE-08-INTEGRATION-MIGRATION-RELEASE.md](../PHASE-08-INTEGRATION-MIGRATION-RELEASE.md)
Wave: **5** (cuối cùng, sau tất cả phase)
Ước lượng: 3–5 ngày

---

## Trạng thái thực thi 2026-07-27

Tài liệu task ban đầu bên dưới dùng giả định Room 98→102. Baseline thực tế khi triển khai là
Room **105** với chuỗi 98→105 đã tồn tại và được kiểm chứng bằng golden fixture.

| Hạng mục | Kết quả |
|---|---|
| Room/golden fixture/migration | DONE — LDPlayer pass 3/3 |
| Cross-feature integration | DONE — LDPlayer pass 5/5 |
| Security | DONE — pass 10/10 |
| Feature flags | DONE — SharedPreferences + Lab + runtime gates |
| Tài liệu 7 tính năng | DONE |
| Unit test | DONE — 603 pass, 1 skip |
| Debug/no-R8/release R8 | DONE |
| Ký APK release | DONE — 4 ABI, v2/v3 verified |
| Strict lint toàn repo | DONE — split module/resource gate; không dùng baseline |
| Performance | Release cold ổn định 2.224s; debug cold 9.706s, hot 13ms |

Chi tiết và hash artifact: [PHASE-08-REPORT.md](./PHASE-08-REPORT.md).

---

## 1. Mục tiêu

Room migration chain an toàn cho tất cả entity mới, integration test cross-feature,
security test, performance benchmark, feature flags, documentation, và release gate.

---

## 2. Trạng thái hiện tại

### Đã có (baseline ban đầu, đã được thay thế bởi trạng thái trên)

| Artifact | Trạng thái | Ghi chú |
|---|:---:|---|
| `AppDatabase.kt` | DONE | Room, version 98 baseline |
| `DatabaseMigrations.kt` | DONE | Existing migrations |
| `all_global_errors.jsonl` | DONE | Khởi tạo, trống |
| `MigrationTest.kt` (androidTest) | PARTIAL | Có file nhưng cần fixtures cho entity mới |
| `project_progress.json` | OUTDATED | Đang track task dịch cũ, cần reset |

### Chưa có

| Artifact | Ưu tiên |
|---|:---:|
| Room migration 98→99 (BookSourceHealth) | **CRITICAL** |
| Room migration 99→100 (TranslationCache revision) | **CRITICAL** |
| Room migration 100→101 (MediaDownload entities) | HIGH |
| Room migration 101→102 (Agent entities nếu cần) | MEDIUM |
| Golden fixture pack | HIGH |
| Integration tests (5 files) | HIGH |
| Security tests (3 files) | HIGH |
| Feature flags | HIGH |
| Documentation guides (7 files) | MEDIUM |
| Performance benchmark | MEDIUM |

---

## 3. Task chi tiết

### P08.T01 — Room migration chain `[DONE — actual 98→105]`

**Files:**
- `data/AppDatabase.kt` `[MODIFY]`
- `data/DatabaseMigrations.kt` `[MODIFY]`

**Migrations:**

| Version | Entity | Change type |
|:---:|---|---|
| 98 → 99 | `BookSourceHealth` | Additive (new table) |
| 99 → 100 | `TranslationCache` | Additive columns: `status`, `finalizedAt`, `actor`, `parentRevisionId` |
| 100 → 101 | `MediaDownloadTaskEntity`, `MediaDownloadItemEntity` | Additive (2 new tables) |
| 101 → 102 | API key migration | Manual: plaintext → encrypted, idempotent |

**Rules:**
1. Mỗi migration nhỏ, focused, deterministic
2. Additive migrations dùng `ALTER TABLE ADD COLUMN` với default
3. New table: `CREATE TABLE IF NOT EXISTS`
4. Migration 102: `UPDATE AiProviderProfile SET apiKey = '' WHERE secretRef IS NOT NULL AND apiKey != ''` — idempotent

**Tiêu chí pass:**
- Fresh install → version 102 trực tiếp
- Upgrade 98→99→100→101→102 tuần tự → không crash
- Rerun migration → idempotent (không duplicate)
- Data integrity: Book, BookChapter, BookSource, Cookie intact

---

### P08.T02 — Golden fixture pack `[DONE]`

**Files:**
- `app/src/androidTest/assets/test_db_v98.db` `[NEW]`
- `app/src/androidTest/assets/test_db_migration_fixture.json` `[NEW]`

**Yêu cầu:**
1. SQLite database at version 98 với sample data:
   - 5 books (text, audio, local, VBook, manga)
   - 10 chapters per book
   - 3 book sources (online, local, VBook)
   - AI profiles + routes + credentials
   - Translation cache entries
   - Cookies
2. JSON fixture: expected state sau migration 98→102
3. Fixture regeneration script (optional)

---

### P08.T03 — Migration tests `[PARTIAL → DONE]`

**File:** `app/src/androidTest/java/io/legado/app/MigrationTest.kt` `[MODIFY]`

**Test cases:**

| Test | Mô tả |
|---|---|
| `migrate_98_to_99_creates_book_source_health` | BookSourceHealth table tồn tại, insert/query OK |
| `migrate_99_to_100_adds_translation_revision` | TranslationCache có status/finalizedAt/actor/parentRevisionId |
| `migrate_100_to_101_creates_media_download` | MediaDownloadTask/Item tables tồn tại |
| `migrate_101_to_102_clears_plaintext_keys` | apiKey rỗng, secretRef intact |
| `migrate_98_to_latest_full_chain` | Upgrade đầy đủ, data fixture preserved |
| `fresh_install_at_latest` | Schema matches Room generated |
| `idempotent_migration_rerun` | Rerun không lỗi, data unchanged |
| `data_integrity_book_chapter_source` | Book/Chapter/Source/Cookie intact xuyên chain |

---

### P08.T04 — Cross-feature integration tests `[DONE]`

**Files (5):**

#### [NEW] `app/src/androidTest/java/io/legado/app/integration/AiAgentIntegrationTest.kt`
- Agent nhận message → resolve route → call model mock → parse tool → proposal → execute → trace
- End-to-end: "Tìm sách X" → search → card → confirm add → bookshelf verify

#### [NEW] `app/src/androidTest/java/io/legado/app/integration/SourceBrowserIntegrationTest.kt`
- Source health detect error → Browser open source URL → cookie sync → recheck → healthy
- Browser page translation: load → translate → toggle → DOM intact

#### [NEW] `app/src/androidTest/java/io/legado/app/integration/VbookMediaIntegrationTest.kt`
- Import VBook plugin → explore → play media → PiP → download → offline play
- VBook media parser → ResolveBookMediaUseCase → ExoPlayer factory

#### [NEW] `app/src/androidTest/java/io/legado/app/integration/TranslationAuthoringIntegrationTest.kt`
- Dịch chapter → chốt final → clone vào writing project → verify content
- Re-dịch → final không đổi → stale warning

#### [NEW] `app/src/androidTest/java/io/legado/app/integration/TtsMediaAudioFocusTest.kt`
- TTS đang đọc → start media → TTS pause → media stop → TTS resume
- Phone call → TTS + media pause → call end → resume

---

### P08.T05 — Security tests `[DONE]`

**Files (3):**

#### [NEW] `app/src/test/java/io/legado/app/security/AgentPermissionSecurityTest.kt`
- Tool gọi mutation thiếu token → reject
- Token expired/hash mismatch/replay → reject
- Path traversal qua tool args → sanitize
- Private IP/loopback/internal URL → chặn

#### [NEW] `app/src/test/java/io/legado/app/security/ImportArchiveSecurityTest.kt`
- ZIP path traversal (`../../../etc/passwd`) → chặn
- ZIP bomb (huge decompression ratio) → limit
- Oversize single file → limit
- TTS model gói có symlink → chặn
- VBook plugin gói có script ngoài whitelist → chặn

#### [NEW] `app/src/test/java/io/legado/app/security/DiagnosticRedactionTest.kt`
- API key trong log → masked
- OAuth token trong log → masked
- API key trong export/backup → masked
- Request header Authorization → masked
- Agent context không chứa password/secret

---

### P08.T06 — Feature flags `[DONE]`

**File:** `constant/FeatureFlags.kt` `[NEW]` hoặc `constant/PreferKey.kt` `[MODIFY]`

**Flags:**

| Flag | Default | Ghi chú |
|---|:---:|---|
| `FEATURE_AI_ROUTER_V2` | `true` | New dashboard UI |
| `FEATURE_AGENT_MUTATION` | `false` | Agent write tools |
| `FEATURE_AGENT_SKILL` | `false` | Skill/plugin system |
| `FEATURE_AGENT_PLUGIN` | `false` | Plugin install |
| `FEATURE_CHAT_BUBBLE` | `true` | Bubble overlay |
| `FEATURE_MANGA_TRANSLATION` | `false` | Manga OCR/translate |
| `FEATURE_BROWSER_PAGE_TRANSLATION` | `true` | Page translate |
| `FEATURE_SOURCE_DAILY_HEALTH` | `true` | WorkManager health |
| `FEATURE_MEDIA_DOWNLOAD` | `false` | Persistent download |
| `FEATURE_EBOOK_FIXED_LAYOUT` | `false` | Fixed-layout editor |

**Rules:**
1. Core schema (Room, entities) KHÔNG phụ thuộc UI flag
2. Flag chỉ gate UI entry + service registration
3. SharedPreferences backed, overridable from Lab/Debug settings

---

### P08.T07 — Documentation guides `[DONE]`

**Files (7):**

| File | Nội dung chính |
|---|---|
| `docs/guide/ai-router.md` | Provider setup, OAuth, model picker, health dashboard |
| `docs/guide/ai-agent.md` | Agent tools, permission, memory, skill/plugin |
| `docs/guide/mlkit-models.md` | ML Kit download, offline translation, revision |
| `docs/guide/vbook-import.md` | Import link/file/registry, duplicate handling |
| `docs/guide/browser.md` | Tab, cookie, login/captcha, page translation |
| `docs/guide/local-tts-model.md` | Model package structure, import, test, voice selection |
| `docs/guide/ebook-editor.md` | Block types, reflow/fixed-layout, canvas, export |

**Format:** Markdown, screenshot placeholders, step-by-step

---

### P08.T08 — Performance benchmark `[DONE — release startup gate]`

**Metrics:**

| Benchmark | Target | Method |
|---|---|---|
| Startup cold | <3s to first frame | `am start -W` + log parse |
| Startup warm | <1s | Same |
| AI Router grid 99 providers | <16ms compose frame | Compose benchmark |
| Agent 1000 messages scroll | <16ms compose frame | Compose benchmark |
| Bookshelf 10000 books | <2s load, <16ms scroll | Room + Compose |
| Translation 500 chapters batch | Complete without OOM | Heap monitor |
| Media time-to-first-frame | <2s HLS, <1s local | ExoPlayer metrics |
| Ebook 500 chapters load | <3s | File I/O timing |

**Approach:** Macro benchmarks first; micro benchmarks for identified bottlenecks

**Kết quả release gate:** LDPlayer x86_64 cold ổn định 2.224s, debug PSS 305 MB,
không crash/ANR. Các benchmark dữ liệu tổng hợp cực lớn tiếp tục là profiling backlog,
không chặn release Phase 08.

---

### P08.T09 — Release stages `[DONE — split lint gate]`

**Stages:**

| Stage | Gate | Notes |
|---|---|---|
| Alpha | `compileAppDebugKotlin` pass | Internal testing |
| Beta | `assembleAppDebug` + unit tests pass | Selected users |
| RC | Full gate + integration + LDPlayer smoke | Release candidate |
| Release | `assembleAppRelease` + R8 + sign | Production |

**Release gate command:**
```powershell
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:lintAppDebug --no-daemon --console=plain
.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain
.\gradlew.bat :app:assembleAppNoR8 --no-daemon --console=plain
.\gradlew.bat connectedAppDebugAndroidTest --no-daemon --console=plain
.\gradlew.bat :app:assembleAppRelease --no-daemon --console=plain
```

---

### P08.T10 — State files update `[DONE]`

**Files:**
- `project_progress.json` `[OVERWRITE]` — reset với 8 milestones mới
- `legado-with-MD3-main.json` `[MODIFY]` — cập nhật tech_stack

**`project_progress.json` schema mới:**
```json
{
  "milestones": [
    {"id": "P01", "name": "AI Router & Providers", "status": "IN_PROGRESS", "progress": 50},
    {"id": "P02", "name": "AI Agent & Chat Bubble", "status": "TODO", "progress": 25},
    {"id": "P03", "name": "LocalAI, ML Kit & Translation", "status": "TODO", "progress": 30},
    {"id": "P04", "name": "VBook, Source Health & Browser", "status": "TODO", "progress": 20},
    {"id": "P05", "name": "TTS & Model Management", "status": "TODO", "progress": 15},
    {"id": "P06", "name": "Media Player & Download", "status": "TODO", "progress": 15},
    {"id": "P07", "name": "Authoring, Ebook & Drop Cap", "status": "TODO", "progress": 20},
    {"id": "P08", "name": "Integration, Migration & Release", "status": "TODO", "progress": 5}
  ]
}
```

---

## 5. Test bắt buộc

### Migration tests
- Xem P08.T03 ở trên

### Integration tests
- Xem P08.T04 ở trên

### Security tests
- Xem P08.T05 ở trên

### Full release gate
```powershell
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:lintAppDebug --no-daemon --console=plain
.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain
.\gradlew.bat :app:assembleAppNoR8 --no-daemon --console=plain
```

### LDPlayer smoke test (cross-feature)

| # | Kịch bản |
|:---:|---|
| 1 | Fresh install → mở app → không crash, edge-to-edge OK |
| 2 | Upgrade fixture v98 → latest → mở app → data intact |
| 3 | AI chat → Agent tool → permission → execute → verify |
| 4 | Source error → Browser login → cookie sync → healthy |
| 5 | VBook import → play media → PiP → download → offline |
| 6 | TTS local → phone call → pause → resume |
| 7 | Writing → clone book → AI suggest → apply |
| 8 | Ebook fixed-layout → export EPUB3 → verify |
| 9 | Feature flags: disable manga/agent-mutation → UI entries hidden |
| 10 | Logcat: no API key, no crash, no Koin error, no ANR |

---

## 6. Điều kiện đóng phase

- [x] Migration chain upgrade + fresh install đều pass
- [x] Cross-feature integration tests pass
- [x] Security tests pass (no redaction failure)
- [x] Feature flags gate UI nhưng không gate schema
- [ ] Release gate full command pass
- [x] Documentation cho 7 features đã viết
- [x] `project_progress.json` phản ánh trạng thái thật
