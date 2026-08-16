# Kế hoạch nâng cấp tổng thể Legado MD3

Ngày chốt phạm vi: 18/07/2026  
Codebase: `legado-with-MD3-main`  
Mục tiêu: hoàn thiện hệ dịch QT/AI, UI tiếng Việt, nguồn VBook đa phương tiện, tải xuống ngoại tuyến, giá sách, browser, AI Router, Ebook Editor, Sáng tác AI và Chatbot có kiểm soát.

## 1. Quyết định phạm vi đã chốt

1. **Ebook Editor là module đầy đủ chức năng**, không phải trình sửa văn bản tối giản. Sách được sao lưu thành dự án độc lập trước khi chỉnh sửa; dự án không tự ý ghi ngược vào sách nguồn.
2. **Module Sáng tác là mô hình AI–người dùng đồng biên tập**. AI có thể lập kế hoạch, viết và rà soát; người dùng được sửa trực tiếp, can thiệp vào đề cương hoặc yêu cầu viết lại ở mọi thời điểm.
3. **Chatbot được quyền đọc và phân tích toàn bộ dữ liệu nằm trong phạm vi ứng dụng**, nhưng mọi thao tác thêm, sửa hoặc xóa đều phải hiển thị nội dung thay đổi cụ thể và chỉ thực thi sau khi người dùng đồng ý.
4. UI động và metadata chưa chốt dùng **QT làm bộ dịch hiển thị mặc định**. Chuỗi tĩnh của ứng dụng vẫn dùng Android resources; không gọi máy dịch cho chuỗi tĩnh.
5. Entity Analyzer chỉ đọc nội dung chương đã tải/cached. Không tải ngầm chương từ mạng.
6. Không nhúng nguyên dự án 9router hoặc ainovel-cli vào APK. Chỉ tái tạo các cơ chế phù hợp bằng Kotlin, Room, coroutine và kiến trúc hiện có.

## 2. Hiện trạng codebase và khoảng thiếu

### 2.1 Thành phần đã có thể tái sử dụng

- Clean Architecture, Koin, Room và Navigation 3 đã là nền chung.
- QT2020, kho từ điển mmap/qdict, dictionary manager, prompt editor, Entity Analyzer và lối tắt Reader đã có.
- `TranslateChapterUseCase`, `QuickTranslationGateway`, `TranslationCacheGateway` và cache dịch UI đã có.
- Media3 ExoPlayer và OkHttp data source đã được khai báo; audio player/service cũ đang hoạt động.
- `BookType.video` và `Book.isVideo` đã tồn tại.
- `VbookPluginImporter`, `VbookPluginAdapter`, Rhino safe context và public-only network guard đã tồn tại.
- Giá sách đã có BookGroup, list/grid, số cột, kích thước bìa và một số kiểu sắp xếp.
- `WebViewActivity`, cookie bridge và source-login browser đã tồn tại.
- AI provider/model profile, nhiều API key phân tách bằng dấu phẩy, retry/backoff, streaming và ba protocol handler đã tồn tại.
- AI Chat đã có conversation branching, memory, tool-calling, đọc chương cached, artifact và xác nhận một số tool ghi.
- Ebook exporter đã hỗ trợ EPUB2/EPUB3/TXT/HTML/PDF/CBZ.

### 2.2 Khoảng thiếu quan trọng

- `BookSourceType` chưa có video; `@IntDef`, `allBookType`, mapping source → book và VBook importer chưa nối `video` nhất quán.
- VBook importer mới nhận ZIP cục bộ; chưa có registry client, catalog UI, capability probing, update và compatibility report.
- Chưa có player video thống nhất, media session thống nhất, download task bền vững cho media, import/export media đầy đủ.
- Browser hiện là Activity View cũ và thiên về source verification, chưa phải browser người dùng đầy đủ đặt trong Khám phá.
- Metadata chưa có trạng thái “chốt”; cache dịch UI chưa có hợp đồng version theo revision từ điển đủ rõ.
- AI key rotation mới nằm trong từng provider; chưa có route nhiều provider/model, health/circuit breaker, quota-aware fallback và audit.
- Chatbot chưa có tool tìm qua nguồn sách, thêm sách, CRUD từ điển, sửa metadata và permission broker bao phủ mọi mutation.
- Chưa có Ebook Editor và Sáng tác.
- Màn Khám phá và quản lý tải xuống chưa đạt bố cục tham chiếu.

### 2.3 Kết quả phân tích registry VBook tại thời điểm chốt

Registry `https://www.vbookext.me/api/registry/vbook-fd1246b6.json` có 136 mục:

- 34 `chinese_novel`
- 59 `novel`
- 17 `comic`
- 16 `video`
- 8 `tts`
- 2 `translate`

Registry không có type `audiobook` riêng. Vì vậy không được suy luận audiobook chỉ từ trường `type`; cần capability probing từ metadata/script/result. Một nguồn audio có thể vẫn được công bố dưới type `novel`.

## 3. Nguyên tắc triển khai chung

- Mọi màn mới dùng Compose + MVI/UDF, `@Stable` UiState và immutable collections.
- Composable không truy cập DB/network trực tiếp; logic nằm ở use case/gateway/repository.
- Không tạo hệ thống thứ hai nếu codebase đã có lõi tương đương: mở rộng audio service, browser/cookie, exporter, AI gateway và BookGroup hiện tại.
- Mọi migration Room phải có test từ DB version liền trước và fixture chứa dữ liệu thực tế.
- Tác vụ dài dùng foreground service hoặc WorkManager tùy yêu cầu thời gian thực; luôn có checkpoint và khả năng tiếp tục.
- Dữ liệu do user sửa/chốt có ưu tiên cao nhất và không bị refresh nguồn, dịch lại hoặc AI ghi đè.
- Plugin không được cấp quyền tùy ý tới filesystem, private IP, Java reflection hay Android context.
- Không coi “build thành công” là hoàn tất; mỗi phase có unit test, integration test, Nox runtime test và tiêu chí đo.

---

## Phase 0 — Đóng baseline, schema và bộ đo

**Mục tiêu:** tạo mốc an toàn trước khi mở rộng database và media.

### Task

- **P0.1 — Inventory tự động**
  - Liệt kê Room entities/DAO, navigation routes, DI bindings, BookType/BookSourceType, exporter, AI tools và hardcoded UI strings.
  - Sinh báo cáo capability hiện tại để so sánh sau từng phase.
- **P0.2 — Golden fixtures**
  - Bộ sách text/comic/audio/video local; sách nguồn; EPUB có ảnh/font/CSS; MP3/M4A/MP4; HLS master/media playlist.
  - Bộ plugin VBook đại diện: novel, comic, video direct MP4, video HLS, TTS, plugin cần header/cookie.
- **P0.3 — Benchmark harness**
  - QT cold/warm lookup, dịch đoạn/chương, import 100 nghìn/1 triệu/5 triệu dòng.
  - Startup, peak RSS, time-to-first-frame media, download resume, AI time-to-first-token.
- **P0.4 — Database safety**
  - Snapshot database trước migration.
  - Migration test không mất Book, BookGroup, Chapter, cache dịch, AI profiles và dictionary entries.
- **P0.5 — Release gates**
  - `:app:compileAppDebugKotlin`, unit tests, lint phần thay đổi, assemble universal/ABI, smoke test Nox.

### Hoàn tất khi

- Có fixtures tái lập được mọi lỗi chính và báo cáo benchmark baseline lưu trong `build/reports/`.

---

## Phase 1 — Ổn định QT, NMT và cache dịch

**Trạng thái:** phần lõi đã triển khai; phase này còn regression gate và hợp nhất chính sách metadata/UI.

### Task đã hoàn thành

- **P1.1** Nạp 5 nguồn thô QT2020 và các gói cần thiết vào debug assets.
- **P1.2** Dùng qdict/mmap, tránh materialize từ điển lớn lên heap.
- **P1.3** Sửa pipeline regex/placeholder làm mất đoạn, xuống dòng và khoảng trắng.
- **P1.4** Sửa viết hoa đầu câu/đầu đoạn sau QT.
- **P1.5** Prompt Editor cho AI provider, preview/validate/reset/import/export và workflow tham khảo DrDuc AI Trans.
- **P1.6** Entity Analyzer trên chương cached và import từ ứng viên vào dictionary sau xác nhận.
- **P1.7** Bộ bất biến bố cục: tokenizer tách `ParagraphToken`, `WhitespaceToken`,
  `ProtectedToken`, `TextToken`; QT/Hán-Việt chỉ biến đổi `TextToken`. Đã có round-trip
  randomized và Android instrumentation cho CRLF, Unicode separator, HTML lặp, URL, placeholder.
- **P1.8** Taxonomy lỗi AI xuyên suốt gateway → retry → dịch chương → Reader: tách cấu hình,
  xác thực, rate-limit, quota, timeout, network, protocol, empty output, parse, cancel, server và
  unknown; giữ số attempt thực, dừng lỗi vĩnh viễn, UI hiển thị provider/model/attempt cùng hướng xử lý.
- **P1.9** Cache theo revision có phạm vi Global/Universe/Project: mỗi mutation chỉ tăng các scope
  thực sự bị tác động; cache QT/UI/Reader mang vector revision hiệu lực và tự dịch lại khi vector
  thay đổi, không xóa hoặc làm mất hiệu lực cache của project khác. Đã có unit test cho cách ly hai
  project, universe theo ngữ cảnh và thao tác chuyển entry giữa hai scope.
- **P1.10** Import từ điển lớn theo streaming UTF-8/UTF-16 strict, progress byte+dòng, cancel theo
  batch, staging/index validation/metadata commit cuối cùng và rollback cả khi lỗi encoding, cancel
  hoặc phát hiện bộ staging dở dang lúc khởi động. Chính sách import của user là chỉ thêm khóa mới:
  bỏ qua duplicate từ built-in, Room, pack hiện có và trong cùng tệp; không ghi đè dữ liệu cũ.
- **P1.11** Bucket builder chuyển sang direct buffer ngoài Java heap, runtime lookup dùng mmap và
  cache bounded. Performance gate 5.000.000 entry đạt: import 202.568 ms, index 262.443.354 byte,
  retained Java heap sau GC 0 byte trong phép đo và 100 warm lookup 27 ms.

### Task còn lại

- Không còn task mở trong Phase 1; chuyển sang các phase tính năng theo thứ tự phụ thuộc.

### Test bắt buộc

- Golden chapters chứa dialogue, thơ, dòng rỗng, full-width punctuation, HTML, emoji và regex chồng lấn.
- Property test: bảo toàn cấu trúc whitespace/paragraph.
- Fault injection: file dictionary hỏng, encoding sai, hết dung lượng, cancel giữa batch, provider trả SSE lỗi.

---

## Phase 2 — Dịch UI bằng QT, chốt metadata và Việt hóa

### P2.1 — Chính sách dịch UI

- Chuỗi tĩnh: chỉ dùng `strings.xml`/`values-vi`.
- Source label, category label và metadata remote chưa chốt: chỉ qua `QuickTranslationGateway`.
- Không gọi NMT/LLM cho dynamic UI dù task preset dịch chương đang chọn AI.
- UI luôn giữ raw identity cho URL, DB key, search source và navigation.

### P2.2 — Metadata Resolver duy nhất

Tạo thứ tự ưu tiên:

1. User override.
2. Locked snapshot.
3. QT-translated dynamic value theo dictionary revision.
4. Raw source value.

Mọi Bookshelf, Explore, BookInfo, Search và Chatbot dùng cùng resolver; cấm mỗi màn tự dịch.

### P2.3 — Chốt metadata

- Thêm entity `BookMetadataLock` khóa theo `bookUrl`:
  - name, author, kind, intro, cover, originName và các trường user chọn;
  - `lockedAt`, `updatedByUserAt`, raw/source revision để audit.
- Nút “Chốt thông tin” tại BookInfo/Bookshelf action.
- Khi khóa:
  - snapshot đúng nội dung đang hiển thị;
  - refresh source vẫn được cập nhật chapter state nhưng không đổi metadata hiển thị;
  - dictionary/provider/cache revision không làm đổi snapshot.
- User sửa trường nào thì ghi vào override/snapshot trường đó sau xác nhận lưu.
- Mở khóa có preview so sánh snapshot với dữ liệu nguồn mới trước khi áp dụng.

### P2.4 — Retranslate có kiểm soát

- Sách chưa chốt tự lazy-retranslate sau dictionary revision.
- Màn đang mở nhận StateFlow invalidation, không cần kill app.
- Sách đã chốt không retranslate metadata; nội dung chương vẫn tuân theo chế độ dịch riêng.

### P2.5 — Việt hóa toàn bộ

- Hoàn thành target screens đã phát hiện: Welcome, Read Record, TOC và word count.
- Quét hardcoded Chinese/English trong Compose/ViewModel/dialog/toast/error.
- Phân loại ngoại lệ hợp lệ: tên sản phẩm, protocol, MIME, model ID, định dạng file.
- Thêm lint/script CI báo chuỗi UI literal mới.

### Nghiệm thu

- Thay dictionary làm metadata sách chưa khóa đổi đúng; sách khóa giữ nguyên byte-for-byte.
- AI provider bị tắt vẫn không ảnh hưởng dịch UI QT.
- `values-vi` không thiếu key; không còn chữ Trung trong các luồng người dùng chính ngoài nội dung sách gốc.

---

## Phase 3 — Tái bố cục Khám phá và Trung tâm tải xuống

### P3.1 — Hợp nhất luồng Khám phá

- Tách paging/fetch sách khỏi `ExploreShowViewModel` thành use case/pager tái sử dụng.
- `ExploreViewModel` quản lý một state thống nhất: source đang chọn, domain, category chips, books, paging, layout và lỗi.
- Không nhúng ViewModel con hoặc gọi repository từ Composable.

### P3.2 — Bố cục theo ảnh tham chiếu

- Header gọn: tên nguồn, domain, nút nguồn, chế độ hiển thị Gốc/QT, search và browser shortcut.
- Category là hàng chip cuộn ngang; chip chọn được giữ khi quay lại tab.
- Nội dung mặc định là grid bìa 3 cột trên điện thoại; tên tối đa 2 dòng, tác giả 1 dòng.
- Giữ long press/very-long press, shelf state, paging, pull-to-refresh và shared cover transition.
- Tablet dùng adaptive grid, không ép 3 cột.
- Source dropdown vẫn có sửa, refresh và quản lý nguồn.

### P3.3 — Persistent download task model

Mở rộng cache queue hiện có bằng bảng lịch sử bền vững:

- `DownloadTask`: id, contentType, bookUrl, title, cover, createdAt, startedAt, completedAt, status, total, completed, bytes, error.
- `DownloadTaskItem`: chapter/episode/segment, URL đã chuẩn hóa, checksum, temp/final path, retry state.
- Queue runtime vẫn tối ưu trong memory nhưng mọi transition quan trọng được checkpoint.

### P3.4 — UI Trung tâm tải xuống

- Nhóm theo Hôm nay/ngày tạo; header có số lượng.
- Card gồm cover, title, trạng thái, completed/total, progress và overflow menu.
- Filter: tất cả/đang tải/chờ/tạm dừng/hoàn tất/lỗi; content type; lịch sử.
- Actions: pause/resume/retry/delete task/delete file/open content.
- Màn chi tiết vẫn cho mở chapter/segment khi cần; card mặc định không bung toàn bộ chapter.
- UI này thay phần trình bày của `BookCacheManageScreen`, tái sử dụng use cases/cache engine hiện có.

### P3.5 — Android notification

- Foreground notification tổng hợp số task; action pause/resume/cancel.
- Notification theo task lớn chỉ khi user bật tùy chọn.
- Nhấn notification mở đúng task trong Trung tâm tải xuống.

### Nghiệm thu

- Khám phá có đúng hierarchy/source/chip/grid ở 360–600 dp và không che bottom navigation.
- Kill process giữa download rồi mở lại: task/progress có thể phục hồi.
- 1.000 task lịch sử vẫn cuộn và filter mượt.

---

## Phase 4 — Mô hình nội dung và tương thích VBook registry

Trạng thái thực thi ngày 18/07/2026:

- P4.1 đã hoàn tất phần type/mask/mapping/UI metadata và migration tự động các nguồn VBook đã cài sai kiểu.
- P4.2 đã có gateway/parser, ETag/Last-Modified, SHA-256 cache, cache offline và fallback stale; UI catalog/cài đặt từ registry còn chờ.
- P4.3 đã có capability profile theo plugin version, bằng chứng từ declared type/manifest role/script hint/runtime result; không suy loại media từ tên hoặc description.
- P4.4 đã chuẩn hóa chuỗi `chap → track` thành `ResolvedMedia` gồm variant, MIME, HLS/DASH/direct/iframe, header/Referer, expiry, subtitle và audio track; player/download engine sẽ dùng model này ở Phase 5.

### P4.1 — Hoàn thiện type mapping

- Thêm `BookSourceType.video` và cập nhật `@IntDef`.
- Thêm video vào `BookType.Type`, `allBookType`, `allBookTypeLocal` và mapping source → book.
- Migration các VBook source đã import nhầm text dựa trên plugin metadata.
- `getBookTypeName`, filter, icon, search, BookInfo và bookshelf resolver nhận Video/Sách nói.

### P4.2 — Registry client

- `VbookRegistryGateway` tải metadata bằng OkHttp với ETag/Last-Modified, timeout và cache offline.
- Parse schema mềm: bỏ qua field lạ, validate name/path/version/type/source/icon.
- UI catalog theo type/locale/author; cài, cập nhật, vô hiệu hóa và gỡ plugin.
- Không auto-download/cài toàn bộ 136 ZIP. Registry là catalog; user chọn plugin hoặc chọn batch rõ ràng.

### P4.3 — Capability model

Tạo capability độc lập với registry `type`:

- search, explore, detail, chapters/episodes;
- text, image, direct-audio, direct-video, HLS, DASH, TTS;
- header/cookie/referer/DRM/external-player requirement;
- download/export support.

Capability lấy từ metadata nếu có, sau đó probe script/result. Kết quả lưu theo plugin version.

### P4.4 — Adapter VBook

- Mở rộng role resolver cho media URL/manifest/track/subtitle/header.
- Chuẩn hóa kết quả plugin thành `ResolvedMedia` thay vì nhét JSON vào chapter content.
- Hỗ trợ direct URL, list quality, audio track, subtitle và expiring URL refresh.
- Giữ Rhino instruction/time/memory limits, zip-slip check, atomic install và public-only DNS.

### P4.5 — Compatibility matrix

- Tự động tải và test 16 plugin video + 8 TTS của snapshot registry.
- Với plugin cần tài khoản/geo/VIP, đánh dấu `BLOCKED_EXTERNAL` thay vì coi là lỗi parser.
- Mỗi plugin có report: install, search, detail, episode, resolve, play, download, lý do fail.
- Không cam kết bypass DRM, paywall, chữ ký server hay giới hạn vùng.

### Nghiệm thu

- Plugin video không còn bị import thành truyện chữ.
- Catalog hoạt động offline bằng cache gần nhất.
- Một plugin lỗi không crash app/Rhino process và không ảnh hưởng plugin khác.

---

## Phase 5 — Player thống nhất, offline, import và export media

### P5.1 — Domain media

- `ResolvedMedia`: uri, mime/container, headers, cookies, referer, duration, expiry.
- `MediaVariant`: quality/bitrate/resolution/audio language.
- `SubtitleTrack`, `AudioTrack`, `PlaybackPosition`.
- Gateway resolver nhận file/content URI/http(s)/VBook episode.

### P5.2 — Playback engine

- Mở rộng Media3 dependencies cho common/session/HLS/DASH/UI/Transformer khi cần.
- Dùng OkHttp data source để dùng chung header, cookie và proxy rules.
- Một `UnifiedPlaybackService`/MediaSession cho video và audiobook; bridge hoặc thay dần `AudioPlayService`, không để hai service tranh audio focus.
- Hỗ trợ play/pause/seek/speed/sleep timer/queue/next/previous/background/PiP/audio focus/headset.
- Video UI Compose: aspect ratio, rotate/fullscreen, brightness/volume gesture, quality, subtitle/audio track.
- Audiobook UI: chapter queue, bookmark, speed, sleep timer, resume position.

### P5.3 — Offline download

- Direct file: resumable range download, ETag/checksum và atomic rename.
- HLS/DASH: manifest snapshot, segment queue, key handling hợp lệ và resume.
- URL hết hạn: gọi plugin resolver làm mới trước khi retry.
- Storage quota, location bằng SAF/app-private, Wi-Fi/charging policy.
- Không tải nội dung DRM hoặc URL mà plugin đánh dấu không được phép.

### P5.4 — Import

- SAF import đơn file/nhiều file/thư mục cho MP3/M4A/MP4 và playlist.
- Đọc MediaMetadataRetriever/Media3 metadata, cover, duration và track.
- Gom audiobook nhiều file theo folder/disc/track; cho user sửa thứ tự trước khi nhập.
- Copy hoặc reference URI theo lựa chọn; kiểm tra persistable permission.

### P5.5 — Export

- Pass-through copy khi nguồn đã đúng MP4/MP3/M4A.
- HLS/DASH không DRM: remux sang MP4/M4A khi codec tương thích.
- Transcode video/audio qua Media3 Transformer/MediaCodec cho MP4/M4A.
- MP3 encoder là module native riêng (ví dụ libmp3lame) và phải đo kích thước APK/ABI/license trước khi chốt; không đổi đuôi giả.
- Export có metadata, cover, chapter marks khi container hỗ trợ; dùng SAF destination.
- Batch export có progress/cancel/checkpoint và báo rõ item không thể chuyển mã.

### Nghiệm thu

- File local và URL trực tiếp MP3/M4A/MP4 phát được; HLS đại diện phát và tải lại offline.
- Airplane mode mở nội dung đã tải, seek và resume đúng.
- Exported file được probe lại mime/duration/track và phát được bằng player ngoài.

---

## Phase 6 — Danh mục giá sách, bố cục và browser nội bộ

### P6.1 — Danh mục tổng mặc định

- Tạo bốn virtual root filter, không chiếm BookGroup bitmask: Truyện chữ, Truyện tranh, Video, Sách nói.
- Custom BookGroup hiện tại nằm dưới hoặc song song root filter; một sách vẫn có thể thuộc nhiều custom group.
- Migration không đổi group cũ.

### P6.2 — Bộ lọc và nhóm

- Layout list/grid, cover width, số cột theo orientation đã có thì hợp nhất vào một config sheet.
- Sort key: thời gian đọc gần nhất, tên, tác giả, số chương/episode, thời gian thêm, thời gian cập nhật.
- Group mode: không nhóm/theo tác giả/theo custom category/content type.
- Sort direction asc/desc áp dụng cho từng key; lưu theo root/custom group.
- Header nhóm sticky và hiển thị count; search chạy trên raw + display metadata.

### P6.3 — Browser Compose

- Tái sử dụng cookie bridge, download interception và WebView logic hiện có nhưng bọc trong `BrowserRouteScreen` + stateless Compose chrome.
- Chức năng cơ bản: address/search, back/forward, reload/stop, home, tab, history, bookmark, share/copy/open external, find in page, desktop mode, download.
- Cookies persistent mặc định và đồng bộ với HTTP source cookie store theo domain.
- Incognito là profile tạm, xóa storage/cookie khi đóng hết tab incognito.
- Source shortcut mở URL source với headers/cookies phù hợp; shortcut browser tự do đặt tại Khám phá.
- Chặn `file://` tùy ý, mixed-content mặc định an toàn, SSL error không có nút bỏ qua âm thầm.

### Nghiệm thu

- Bốn root category phân loại đúng BookType và không phá custom group.
- Browser đăng nhập một nguồn, đóng/mở lại vẫn giữ cookie; incognito không để lại cookie.

---

## Phase 7 — AI Router nội bộ kiểu 9router

### P7.1 — Mô hình dữ liệu

- `AiCredential`: provider, secret reference, account label, enabled, quota/reset metadata, health.
- `AiRouteTarget`: provider/model/credential pool, priority, weight, max concurrency, task capability.
- `AiRouteProfile`: ordered targets, strategy, retry budget, fallback rules; gán theo task chat/translate/author/editor.
- Secret lưu bằng Android Keystore-encrypted storage; Room chỉ chứa reference/masked value.

### P7.2 — Routing engine

- Round-robin/weighted round-robin nhiều key/account trong cùng target.
- Priority fallback qua nhiều model/provider.
- Error classifier: auth không retry cùng credential; 429/quota cooldown; 5xx/timeout retry có jitter; invalid request không fallback mù.
- Circuit breaker và half-open health probe.
- Sticky route trong một chat/tool loop để giữ tính nhất quán; có thể fallback khi target chết.
- Streaming chỉ fallback trước token đầu tiên. Nếu stream đã phát token rồi bị lỗi, giữ partial output và cho user retry/continue để tránh trả trùng.

### P7.3 — Protocol normalization

- Chuẩn request nội bộ `AiRequest/AiStreamEvent` hiện có.
- Adapter OpenAI Chat, OpenAI Responses, Anthropic; mở rộng Gemini/Ollama chỉ khi có test contract.
- Tool call ID, reasoning, usage và finish reason phải round-trip.

### P7.4 — Quota, usage và quan sát

- Log metadata request: route/target/attempt/latency/status/token/cost estimate; mặc định không log prompt/response nhạy cảm.
- Dashboard health, cooldown, quota reset, token/cost và test route.
- Export diagnostic đã redact key/content.

### P7.5 — Giới hạn an toàn/pháp lý

- Chỉ dùng API key/OAuth flow được provider cho phép và tài khoản do user sở hữu.
- Không sao chép cơ chế trích token subscription không được hỗ trợ, bypass quota hoặc giả mạo client.
- “Proxy API nội bộ” nằm ở gateway trong process. Chỉ mở localhost HTTP endpoint trong developer mode nếu có use case cụ thể.

### Nghiệm thu

- Test deterministic chứng minh round-robin, cooldown, fallback order và không retry invalid request.
- Chat, dịch, tác giả dùng cùng router nhưng profile độc lập.

---

## Phase 8 — Ebook Editor đầy đủ chức năng

### P8.1 — Project và sao lưu

- “Sao lưu sang Editor” tạo `EbookProject` độc lập gồm metadata, chapters, assets, styles và source snapshot.
- Không sửa Book/chapter cache gốc. Đồng bộ ngược chỉ qua export/import do user chủ động.
- Project autosave atomic, revision log và recovery sau crash.

### P8.2 — Document model

- Cây Book → Volume/Part → Chapter → Block.
- Block: paragraph, heading, quote, list, divider, image, caption, page break, raw XHTML có kiểm soát.
- Asset store hash-based chống trùng; image alt text, crop/resize/compress, font embedding và license note.

### P8.3 — Editing đầy đủ

- Rich-text + source/XHTML mode; undo/redo đa bước.
- Tìm/thay thế literal/regex theo chapter/book, preview diff trước apply-all.
- Chapter CRUD, reorder, split/merge, multi-select và template.
- Typography: font family/size/line height/indent/alignment/margin/drop cap/theme/day-night preview.
- Metadata/cover/TOC/landmarks/language/identifier/author/publisher.
- Spell/style lint, broken image/link, duplicate id, invalid nesting và accessibility checks.

### P8.4 — Preview và đóng gói

- Live preview theo kích thước điện thoại/tablet/e-reader.
- Tái sử dụng `EbookExportWriter`; nâng exporter thành writer nhận project document model.
- EPUB2/EPUB3/TXT/HTML/PDF/CBZ; validate EPUB package, manifest, spine, nav, CSS/font/image MIME.
- Export reproducible: cùng revision + config cho output ổn định.

### P8.5 — AI hỗ trợ nhưng không tự ghi đè

- Rewrite, proofread, format cleanup, summarize, generate alt text là suggestion/diff.
- User chọn accept/reject từng hunk hoặc toàn bộ; accepted change tạo revision.

### Nghiệm thu

- Round-trip một EPUB có ảnh/font/CSS vào project, chỉnh sửa, export và mở được bằng ít nhất hai reader.
- Crash giữa autosave phục hồi revision cuối không làm hỏng project.

---

## Phase 9 — Module Sáng tác AI–người dùng

Tham khảo cơ chế hữu ích của ainovel-cli: Coordinator → Architect → Writer → Editor, rolling plan, context hierarchy, checkpoint theo step, can thiệp thời gian thực và đánh giá nhiều chiều. Không sao chép TUI/Go runtime.

### P9.1 — Dữ liệu sáng tác

- `WritingProject`, premise, genre/tone/style rules, target length.
- World bible: character, relationship, location, faction, item, timeline.
- Outline: volume/arc/chapter, beat, foreshadowing, dependency và status.
- Draft/revision/review/summary/checkpoint/user intervention.

### P9.2 — Workflow agent

- Coordinator chọn bước tiếp theo từ state đã lưu.
- Architect quản lý world/outline/rolling plan.
- Writer tạo draft theo chapter plan + context budget.
- Editor chấm consistency, character, pacing, narrative, foreshadowing, hook và style; đề xuất rewrite.
- Mỗi role gán AI route/model riêng từ Phase 7.

### P9.3 — Đồng sáng tác thực sự

- User sửa trực tiếp premise/world/outline/draft bất kỳ lúc nào.
- Mọi AI output vào branch/revision hoặc suggestion; không ghi đè đoạn user đã sửa mà không xác nhận.
- Intervention classifier: tiếp tục, rule dài hạn, đổi outline, đổi world fact, rewrite chapter, đổi model/config.
- Hiển thị impact preview: chapter/arc/fact nào sẽ bị stale trước khi áp dụng thay đổi lớn.

### P9.4 — Quản lý ngữ cảnh dài

- Context tầng chapter → arc → volume → global bible.
- Summary có source revision; draft đổi thì summary phụ thuộc bị stale và được regenerate có kiểm soát.
- Entity/continuity index để phát hiện đổi tên, timeline conflict, nhân vật xuất hiện sai và foreshadow chưa xử lý.

### P9.5 — Job và recovery

- Mỗi tool/LLM step checkpoint trước và sau side effect.
- Pause/resume/cancel, retry idempotent, app restart khôi phục đúng step.
- Background generation chỉ chạy khi user bật và có foreground notification.

### P9.6 — Tích hợp Editor

- Chapter accepted được mở trong Ebook Editor.
- Editor revision trả về Sáng tác như user-authored canonical text.
- Import TXT/EPUB/project để viết tiếp; export qua Ebook Editor/Exporter.

### Nghiệm thu

- User có thể dừng sau chapter 3, sửa outline/chapter, yêu cầu AI tiếp tục và hệ thống dùng revision mới.
- Kill app giữa một LLM/tool step không tạo chapter trùng hoặc mất draft.

---

## Phase 10 — Chatbot toàn quyền đọc, mutation phải xác nhận

### P10.1 — Profile, rule, personality và memory

- `ChatbotProfile`: tên, avatar, system rules, personality, response style, tool policy và AI route.
- Memory scope: global/conversation/book/project; có UI xem/sửa/xóa/export.
- Memory retrieval theo relevance + scope + recency, không nhét toàn bộ memory vào mọi prompt.

### P10.2 — Read tools

- Giữ các tool hiện có: bookshelf search/detail, chapter list/content/window/search, bookmark, reading stats, AI artifact, memory.
- Thêm dictionary lookup/list, source list, source search/explore, book availability, editor project read và writing project read.
- Đọc chapter mặc định chỉ dùng cached content; nếu muốn tải thêm phải tạo action có xác nhận và báo dung lượng/phạm vi.

### P10.3 — Mutation tools

- Dictionary: add/update/delete/import candidate.
- Bookshelf: add book from source result, remove, change group/category, edit/lock/unlock metadata.
- Bookmark/note/artifact/memory CRUD.
- Editor/Sáng tác: tạo project, áp dụng suggestion/revision, đổi outline/rule.
- Download: tạo/pause/resume/delete task.

### P10.4 — Permission broker bắt buộc

Mọi add/edit/delete đi qua một cổng duy nhất, không phụ thuộc model có nhớ gọi confirm hay không:

1. Tool tạo `ProposedAction`, chưa mutation.
2. UI hiển thị action, đối tượng, before/after diff, số item, network/storage impact.
3. User xác nhận đúng proposal.
4. Permission token gắn proposal hash + arguments + expiry và chỉ dùng một lần.
5. Repository thực thi transaction, ghi audit và trả kết quả.

Không có “cho phép toàn bộ vĩnh viễn” cho mutation. Batch action được xác nhận theo batch cụ thể.

### P10.5 — Undo và audit

- Mutation hỗ trợ inverse operation khi có thể.
- Audit log: ai/user, tool, target, before/after hash, time, result; không lưu secret.
- Sau thành công hiển thị “Hoàn tác” trong thời hạn phù hợp.

### P10.6 — Tìm và thêm sách

- Chatbot hỏi intent, dùng các nguồn đã cài/enabled, chạy search song song có giới hạn và deduplicate theo name/author/source.
- Trả card có source/link/bookUrl/cover/latest chapter.
- “Tự động thêm” nghĩa là chatbot đề xuất AddToBookshelf; chỉ gọi mutation sau user confirm.

### P10.7 — Tóm tắt/hiểu truyện dài

- Map-reduce chapter summaries từ cached chapters, sau đó arc/book summary.
- Trích dẫn chapter/index trong câu trả lời; không bịa nội dung chưa đọc.
- Cache artifact gắn content hash; chapter đổi thì artifact stale.

### Nghiệm thu

- Read tool chạy không cần confirm; mọi mutation bị chặn ở repository nếu thiếu permission token, kể cả gọi trực tiếp trong test.
- Chatbot tìm sách qua source, trả link và chỉ thêm vào giá sách sau confirm.
- Chatbot sửa/xóa từ điển hiển thị diff chính xác và có audit/undo.

---

## Phase 11 — Hardening, hiệu năng và phát hành

### P11.1 — Security

- Fuzz VBook ZIP/JSON/script, URL parser, HLS manifest, EPUB import và AI tool arguments.
- SSRF/private IP, path traversal, zip bomb, decompression limit, malicious XHTML/JS và unsafe WebView bridge.
- Redact API keys/cookies/headers khỏi log, export diagnostic và crash report.

### P11.2 — Hiệu năng

- Macrobenchmark startup, Explore grid scroll, bookshelf 10.000 sách, download list 1.000 task.
- Memory benchmark dictionary 5 triệu dòng, EPUB lớn, 500-chapter author project và long chat.
- Backpressure/concurrency limit cho plugin search, download segment và AI route.

### P11.3 — Reliability

- Process-death tests cho download, editor autosave, author checkpoint và chatbot proposal.
- Storage-full, network-change, clock-change, URL expiry và DB lock fault injection.

### P11.4 — Accessibility/i18n

- TalkBack semantics, minimum touch target, dynamic font scale, contrast, RTL smoke test.
- Vietnamese terminology glossary thống nhất: chương/tập/cung, tải xuống, sách nói, chốt thông tin.

### P11.5 — Rollout

- Feature flags: VBook media, unified player, AI router, Editor, Author, Chatbot mutation.
- Canary migration trên bản sao database thật.
- Mỗi feature có kill switch và đường quay lại engine cũ trong một release chuyển tiếp.

## 4. Thứ tự phụ thuộc đề xuất

1. Phase 0 → Phase 1/2.
2. Phase 3 có thể chạy sau resolver UI của Phase 2.
3. Phase 4 bắt buộc trước media plugin; Phase 5 phụ thuộc Phase 3 download task + Phase 4 capability.
4. Phase 6 có thể chạy song song sau khi type mapping Phase 4 ổn định.
5. Phase 7 phải hoàn tất trước khi Author/Chatbot dùng fallback đa provider.
6. Phase 8 document model phải ổn định trước tích hợp Author Phase 9.
7. Phase 10 dùng Phase 7 router và tích hợp dần Phase 4/6/8/9 tools.
8. Phase 11 chạy xuyên suốt, nhưng release gate cuối chỉ mở sau khi migration và process-death tests đạt.

## 5. Mốc phát hành có thể kiểm chứng

### Milestone A — Translation/UI Stable

- P0–P3 hoàn tất.
- QT không phá bố cục; metadata lock; Việt hóa; Explore/download UI mới.

### Milestone B — Media & Sources

- P4–P6 hoàn tất.
- VBook catalog/type/capability; player/offline/import/export; bookshelf category; browser.

### Milestone C — AI Platform

- P7 hoàn tất.
- Multi-key/model/provider router có fallback, round-robin, health và audit.

### Milestone D — Creation Suite

- P8–P9 hoàn tất.
- Ebook Editor đầy đủ và Sáng tác AI–user với revision/checkpoint.

### Milestone E — Controlled Super Chatbot

- P10–P11 hoàn tất.
- Chatbot đọc rộng, mutation luôn confirm, có diff/audit/undo; toàn hệ thống đạt security/performance/release gates.

## 6. Definition of Done cho từng task

Một task chỉ được đánh dấu hoàn tất khi có đủ:

1. Contract/domain model và luồng lỗi đã xác định.
2. Implementation đúng layer + DI/navigation/migration.
3. Unit test cho logic và lỗi biên.
4. Integration/instrumentation test cho DB/filesystem/network/service khi liên quan.
5. UI test hoặc Nox evidence với trường hợp thành công, loading, empty và error.
6. Không có regression ở compile/unit suite hiện tại.
7. Tài liệu cấu hình, giới hạn và hành vi fallback được cập nhật.
8. Với mutation hoặc dữ liệu người dùng: có backup/rollback/undo hoặc giải thích rõ vì sao không thể.

## 7. Rủi ro cần theo dõi

- Plugin VBook thay đổi schema hoặc phụ thuộc API/private signature: xử lý bằng capability/report, không hardcode cam kết 100% nguồn luôn sống.
- Video streaming có DRM/geo/login: không bypass; hiển thị trạng thái hỗ trợ rõ ràng.
- MP3 transcoding tăng kích thước APK và gánh native ABI/license: phải qua spike trước khi bật mặc định.
- Dictionary/EPUB/media lớn gây OOM: streaming, mmap, bounded queue và staging là yêu cầu bắt buộc.
- Fallback AI khi stream đã phát token gây nội dung lặp: cấm silent fallback sau first token.
- Chatbot “toàn quyền” có nguy cơ phá dữ liệu: permission token ở repository là hàng rào bắt buộc, không chỉ là dialog UI.
- Sáng tác AI dài có context drift: revision-linked summary, entity index và checkpoint phải làm trước auto-run dài.

## 8. Nguồn tham khảo đã đối chiếu

- VBook registry: <https://www.vbookext.me/api/registry/vbook-fd1246b6.json>
- 9router: <https://github.com/decolua/9router>
- ainovel-cli tiếng Việt: <https://github.com/kentjuno/ainovel-cli>
- Memory phiên QT/UI/Nox: `C:/Users/vanki/.codex/memories/extensions/ad_hoc/notes/20260718-114309-legado-qt-ui-nox-session.md`
