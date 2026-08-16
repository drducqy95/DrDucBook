# P00.T01 - Baseline inventory

Thoi diem kiem ke: 2026-07-29  
Pham vi: code, test va tai lieu trong workspace hien tai  
Nguyen tac: code/test la nguon su that; claim trong docs cu chi la bang chung tham khao.

## 1. Build va nhan dien

| Hang muc | Repo truth | Trang thai cho plan |
|---|---|---|
| Namespace | `io.legado.app` (`app/build.gradle.kts`) | Phai doi o P01 |
| Application ID | `io.legato.kazusa` | Phai doi thanh `com.drducbook.app` |
| Debug ID | Them `.debug` | Can giu tach biet khi doi package |
| SDK | min 26, target/compile 37 | Baseline |
| JVM toolchain | JDK 21 trong Gradle | Baseline |
| Build variants | debug, noR8, release + ABI splits | Bat buoc regression |
| Git metadata | Workspace khong co `.git` | Khong the dua vao `git status/diff/commit` lam bang chung |

Rui ro coexistence: ReaderProvider hien dung `${applicationId}.readerProvider`, WebService dung HTTP 1122 va WebSocket 1123. DrDucBook phai dung authority va cap cong moi; khong duoc khai bao authority legacy neu can cai song song.

## 2. Quy mo code va data

- Android main source: 1.615 Kotlin files va 10 Java files.
- Local unit tests: 154 files; Android tests: 23 files.
- Modules: `:app`, `:modules:book`, `:modules:rhino`; Vue frontend o `modules/web`.
- Room `AppDatabase` version 105, 53 entities, 39 DAO accessors, 56 auto-migration declarations.
- Database name van la `legado.db`; application sandbox moi se tu tach DB khoi app cu.
- `allowMainThreadQueries()` van duoc bat; khong thuoc scope P00 nhung la performance risk cho P11 audit.

## 3. Architecture va UI

- Data/Domain/UI layers, Koin va Navigation 3 da duoc su dung cho man hinh Compose moi.
- Codebase van hybrid Compose + View; Reader, source editor va mot so Activity van dung View/XML.
- Top-level navigation hien co 9 destination: Home, Bookshelf, Explore, Browser, AiAgent, Writing, EbookEditor, Rss, My.
- Theme hien co 14 mode, Material/Miuix, custom colors, font scale, launcher alias, light/dark background va read-menu custom icons.
- Chua co `AppearanceProfile`, icon-slot registry chung, asset lifecycle hoac theme package DrDucBook.

## 4. Browser, source va cookie

- Browser Compose co 8 Kotlin files, multi-tab persistence, address/search, back/forward, desktop mode, page translation, download va guarded WebView.
- Browser da co `sourceProbeUrl` va `SyncLoginAndProbe`, nhung source context chi la URL string, khong co `SourceKey` hay domain index.
- Browser inject cookie tu app store truoc load va ghi cookie WebView ve `CookieStore` sau page finish/dispose.
- Sau login, Browser goi `BookSourceHealthWorker.runNow(context)`, nghia la check tat ca enabled sources thay vi source dang login.
- Cookie database chi luu `url + cookie string`; cookie attribute/path/expiry/sameSite/hostOnly khong duoc model hoa va value la plaintext.
- `applyToWebView` xoa session cookies toan cuc truoc khi inject, co nguy co lam mat session tab/domain khac.
- Chua co browser bookmark entity; `Bookmark` hien tai la bookmark noi dung sach.

## 5. Tinh trang nguon

- Ton tai hai he thong song song:
  - `BookSourceHealthWorker` Quick probe search/explore, WorkManager 24h, luu `BookSourceHealth`.
  - `CheckSourceService` deep check search/explore/info/TOC/content, foreground service.
- `BookSourceHealth` co status, latency, HTTP status, failure step va consecutive failures.
- `CheckSourceService` ghi ket qua loi vao group/comment/respondTime cua `BookSource`; dieu nay tron diagnostics voi user data.
- Health hien chi co first-class table/dashboard cho BookSource; RSS/VBook chua co stage history chung.
- Browser login khong co targeted check, history theo stage, pause/resume persisted hoac domain-level rate limiting.

## 6. Agent

- `AiToolRepository` co 41 `AiToolDefinition` va 42 `TOOL_*` constants.
- Permission broker, audit/run trace, proposal, skill/version lifecycle va VBook draft/install da ton tai.
- Agent tao duoc skill/VBook draft nhung chua co general custom JavaScript tool manifest/runtime/registry.
- Security validator cho plugin draft/path/URL da co test; phai duoc tai su dung, khong nhan doi.

## 7. Sang tac va Ebook

- `ui/authoring` co 14 Kotlin files, MVI screens, project editor, Ebook editor/preview, validation va export.
- Export hien ho tro EPUB3, PDF va TXT; TXT co lossy warning.
- Project repository luu JSON/assets trong app files; JSON loi co the bi bo qua thay vi quarantine/recovery co thong bao.
- Backup legacy hien khong bao gom authoring projects/assets.

## 8. Media

- Co `ResolvedMedia`, `MediaResolverRepository`, `MediaPlaybackService`, `MediaDownloadService`, Room task/item va Compose download/player UI.
- Media3 da ho tro Direct/HLS/DASH/local, MediaSession, notification, background/PiP va progress.
- HLS download hien co master/child, AES-128 va checkpoint support.
- Gap chinh: normal Legado source resolver co duong resolve chapter absolute URL truc tiep thay vi luon chay content rule; ket qua dang `HD + m3u8 URL` co the bi hien nhu text.
- Playlist nguoi dung dua la HLS master 1920x800, child co cross-domain segments va nhieu discontinuity; day la fixture muc tieu P09.

## 9. WebService va web frontend

- Android Ktor server co 27 HTTP route va 3 WebSocket route.
- Default HTTP 1122, WebSocket `port + 1`; CORS hien `anyHost()` va legacy routes khong co pairing/session.
- Vue project `legado-web`, Vue 3.5.12, Vite 5.4.8, Element Plus 2.8.5; 17 Vue files va 25 TypeScript files.
- Web co Bookshelf, Reader, Book/RSS source editor va debug; theme reader dung 7 preset/hard-coded images/colors.
- Chua co instance identity API, WebService policy, Export backend gate, auto-translation jobs hay private background store.

## 10. Backup, Auth va cloud

- Backup local/WebDAV ghi cac JSON legacy: bookshelf, bookmark, groups, Book/RSS sources, RSS stars, replace/read records, subscriptions, TTS, dict, homepage, highlights va configs.
- Backup khong bao gom Agent data moi, source-health history, media tasks, authoring assets, browser bookmarks hoac appearance profile.
- Khong co Firebase Auth, Google Credential Manager login, Drive API/appDataFolder hoac snapshot conflict engine.
- Firebase Analytics/Performance va google-services plugin da co; `google-services.json` khong phai client cho `com.drducbook.app`.
- Catalog co 9 dong URL Google Drive truc tiep va 0 Hugging Face URL; `LocalAiModelCatalog` co 3 model Drive, `ExternalAssetCatalog` co package/model/voice Drive.

## 11. Tai lieu va test truth

- Docs upgrade cu ghi Room version 98; code hien la 105.
- Completion docs dung nhieu muc `DONE`, `AUTOMATED_DONE`, `DEVICE_PARTIAL`; phai giu phan biet nay trong plan moi.
- Media/Agent/TTS co coverage kha tot, nhung Browser-cookie-source targeted flow, full source health unification, auth/Drive/HF proxy va side-by-side install chua co evidence.
- Baseline build/test thuc te duoc chay va ghi rieng o P00.T05; inventory khong suy dien pass tu su ton tai cua file.

## 12. Boundary va owner du kien

| Subsystem | Owner boundary |
|---|---|
| Identity/coexistence | Gradle + manifest + compatibility module |
| Navigation/Workspace | `ui/main`, `ui/workspace`, Navigation 3 |
| Appearance | appearance domain/repository + theme bridges |
| Browser/source | Browser MVI + source context/bookmark domain |
| Cookie | SourceCookieGateway/CookieVault + adapters |
| Source health | SourceCheckEngine + probe adapters + dashboard |
| WebService | Ktor v1 compatibility + authenticated v2 + Vue |
| Authoring/Ebook | project repository + authoring UI + export service |
| Agent | tool registry/permission/sandbox/lifecycle |
| Media | resolver/playback/download services + media UI |
| Cloud target | HF + Supabase Edge Functions/Auth/Postgres/Storage; Google Drive appDataFolder optional backup |

## 13. Ket luan P00.T01

Codebase da co nhieu implementation cua plan cu, nhung cac hop dong can thiet cho DrDucBook (package/coexistence, appearance profile, source-aware Browser, CookieVault, unified health, constrained WebService, custom JS tools, HF/Supabase Auth/Storage sync va Drive backup) chua ton tai. P00 phai khoa security, compatibility corpus va ADR truoc khi bat dau P01.

### Plan change 2026-07-29

Theo quyet dinh moi cua nguoi dung, backend tai khoan/metadata/private delivery chuyen sang Supabase. Firebase Analytics/Performance va `google-services.json` o tren van la repo truth hien tai, nhung se bi go bo. Package cu tren Google Drive phai migrate sang HF; Google Drive `appDataFolder` van duoc giu nhu dich sao luu/dong bo snapshot tuy chon ben canh Supabase.
