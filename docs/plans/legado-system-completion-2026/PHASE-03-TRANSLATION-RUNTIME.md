# Phase 03 — QT, ML Kit, NMT và translation pipeline

## 1. Mục tiêu

Hoàn thiện chất lượng và độ ổn định của QT/ML Kit/NMT/AI translation, bảo toàn cấu trúc văn bản, không trả CJK ngoài vùng được bảo vệ và hỗ trợ revision có kiểm soát.

## 2. Phạm vi ảnh hưởng

`QuickTranslationRepository`, Aho-Corasick/Jieba/POS/grammar runtime, `MlKitTranslationRepository`, `HachimiOnnxTranslator` và importer, `LocalGgufHandler`, `TranslateChapterUseCase`, `TranslationCache*`, Reader, quick dictionary, manga reader/export.

Phụ thuộc Phase 00 và route của Phase 01.

## 3. Task

### C03.01 — QT runtime chất lượng và tốc độ

- **Mục tiêu:** dùng Aho-Corasick để tìm cụm, Jieba/POS để bổ trợ khi cần và grammar rule động theo từ loại.
- **Thực hiện:** không để fallback Jieba che dictionary term; giữ whitespace/paragraph/placeholder/URL; bounded cache và lazy tokenizer.
- **Ví dụ:** cụm dài có term project dictionary bên trong; câu có CRLF, emoji, full-width punctuation và HTML.
- **Thông qua:** golden/property tests bảo toàn cấu trúc; cold ≤4 giây, warm ≤1 giây cho 3.520 ký tự trên LDPlayer; peak memory không OOM.

### C03.02 — ML Kit prerequisite và residual CJK

- **Mục tiêu:** dịch thành công sau khi đủ model và không để output CJK không được bảo vệ.
- **Thực hiện:** resolve source/target code, kiểm tra model hai chiều, retry download; scan code point gồm supplementary Han; repair residual bằng QT/phonetic/AI route theo policy.
- **Ví dụ:** thiếu model zh hoặc vi; tải partial rồi retry; output còn một tên riêng CJK được đánh dấu protected.
- **Thông qua:** thiếu model có CTA; offline sau download pass; zero unprotected CJK trong golden output; không xóa model đã tải khi retry lỗi.

### C03.03 — NMT/LocalAI runtime

- **Mục tiêu:** import, load, generate, cancel, unload NMT/GGUF an toàn.
- **Thực hiện:** magic/checksum/ABI/storage probe, native mutex, generation-bound model swap, empty output typed error.
- **Ví dụ:** cancel khi đang load; provider trả empty; đổi model trong khi translation job đang chạy.
- **Thông qua:** session kế tiếp vẫn load được; invalid package không tạo model ready; fallback route nhận lỗi đúng loại.

### C03.04 — Translation revision và cache

- **Mục tiêu:** machine draft không ghi đè user edit/final.
- **Thực hiện:** metadata file-backed có status, raw hash, actor, parent revision, created/finalized time; raw đổi thì stale; unlock tạo revision mới.
- **Ví dụ:** retry provider sau khi user sửa chunk; raw chapter cập nhật sau khi final.
- **Thông qua:** precedence `FINAL > USER_EDITED > MACHINE_DRAFT > RAW`; history restore được; background retry không sửa final.

### C03.05 — Raw/display mapping

- **Mục tiêu:** chọn ở output dịch vẫn lấy đúng raw range.
- **Thực hiện:** `MappedDisplayText` segment map cho QT/Hán-Việt; chunk provenance cho AI/NMT/ML Kit; alternatives + confidence cho alignment không chắc.
- **Ví dụ:** output dài/ngắn hơn raw, câu lặp, line break khác, selected text có khoảng trắng đầu/cuối.
- **Thông qua:** exact mapping test 100% với deterministic engine; low confidence mở chooser và không tự thêm dictionary.

### C03.06 — Manga pipeline

- **Mục tiêu:** OCR → reading order → translate → overlay → cache/export.
- **Thực hiện:** image hash, tiled load, OCR block polygon/confidence/orientation, bubble grouping, background estimation, font fit và manifest.
- **Ví dụ:** trang dọc có nhiều bubble, OCR confidence thấp, user chỉnh thứ tự.
- **Thông qua:** overlay không phá ảnh gốc; cache invalid khi image/model/prompt đổi; export có manifest và test instrumentation.

## 4. Điều kiện đóng

QT/ML Kit/NMT/AI translation pass unit, golden, cancellation, soak và LDPlayer; revision không mất dữ liệu; mapping không ghi nhầm raw; manga happy/error/empty/export pass.

## 5. Trạng thái thực thi 2026-07-26

| Task | Trạng thái | Bằng chứng chính | Gate còn lại |
|---|---|---|---|
| C03.01 | AUTOMATED_DONE / DEVICE_PARTIAL | Aho-Corasick + Jieba lazy fallback; QT giữ literal; cold 2,581 giây và warm 0,947 giây với 7.620 ký tự trên LDPlayer | Soak 10 chương/30 phút và theo dõi peak memory |
| C03.02 | AUTOMATED_DONE / DEVICE_PARTIAL | prerequisite, supplementary Han scan và residual repair có unit test | Tải cặp model thật, tắt mạng và dịch trên LDPlayer |
| C03.03 | AUTOMATED_DONE / DEVICE_PARTIAL | importer atomic/cancellable, lifecycle GGUF/NMT và typed empty output pass | Import/generate bằng model NMT và GGUF thật |
| C03.04 | AUTOMATED_DONE / DEVICE_PARTIAL | revision/history/stale, checkpoint chunk và late-observer isolation pass; AI lỗi muộn không ghi đè QT/Hán-Việt trên LDPlayer | Chốt, mở khóa và retry revision trên một chương thật |
| C03.05 | AUTOMATED_DONE / DEVICE_PARTIAL | QT exact-candidate mapping pass trên LDPlayer; deterministic subrange mapping và low-confidence chooser pass | Các luồng mapping ngoài chức năng thêm từ điển QT vẫn cần device gate riêng |
| C03.06 | AUTOMATED_DONE / DEVICE_PARTIAL | OCR domain/use case, overlay editor và CBZ manifest pass | OCR/zoom/edit/export một trang manga thật |

AI route dùng cho dịch đã được sửa thành fallback tuần tự. Route tự sinh cũ được migrate từ
`round_robin` sang `priority`; target trả `EMPTY_OUTPUT` bị cách ly 5–30 phút theo số lỗi và
không khóa credential dùng chung. Request đang chờ kiểm tra lại sức khỏe target trước khi gọi provider.

## 6. Bổ sung kiểm chứng cache/chunk và AI layout - 2026-07-26

### C03.04 cache policy update

- AI cache is shared across model, combo, and prompt changes. Changing the selected route does not
  silently invalidate successful AI chunks.
- The full chapter content hash still changes when the story dictionary changes. Chunk dependency
  hashes include only dictionary terms that overlap the chunk, so only affected chunks become stale.
- A successful cached chunk is never replaced by an error response. A failed or missing chunk is the
  only chunk eligible for retry, unless the user explicitly requests `Dịch lại chương`.
- NMT remains sequential to limit memory pressure. QT/ML Kit/NMT/AI now use the same chunk-level
  checkpoint contract instead of special whole-chapter paths.

### C03.06 manga checkpoint update

- Manga translation cache is separated by provider and image/page identity.
- Each OCR bubble is checkpointed after success. A page retry keeps successful bubbles and retries
  only missing or stale bubbles. Dictionary dependency is calculated per bubble.
- Partial overlay updates are emitted while the page is running, so a provider failure does not
  discard already translated bubbles.

### C03 AI layout update

- AI response decoding accepts the strict marker protocol, supported marker variants, and a safe
  plain-text fallback when the provider returns exactly one non-empty line per requested paragraph.
- A response with missing paragraphs, extra lines, or unresolved CJK remains rejected and is retried
  through the configured route rather than being merged into a different chunk.

### Remaining device gates

- Real ML Kit offline model download/use, real NMT/GGUF generation, manga OCR/edit/export, and
  long-running cold/warm/memory soak still require a dedicated device fixture and are not marked as
  complete by this verification.

## 7. Bổ sung khắc phục dịch chương - 2026-07-27

### Raw/display mapping

- Reader giữ snapshot riêng cho raw, display và mapping segment. QT/Hán-Việt dùng API mapped;
  AI/NMT/Google/ML Kit dùng provenance theo chunk và đoạn văn.
- Khi raw và display khác nhau, Reader không còn dùng display text làm raw dự phòng. Mapping dưới
  confidence 0,8 phải mở danh sách ứng viên và không được tự lưu vào từ điển.
- Cache cũ không có provenance chỉ tham gia alignment với confidence thấp.

### QT runtime

- Candidate literal được tách khỏi candidate từ vựng. Chuỗi Latin, số, URL, markup, emoji và CRLF
  được giữ nguyên span, không còn bị chèn khoảng trắng giữa từng ký tự.
- Điểm cụm dài được giới hạn; nguồn term và confidence tham gia thứ tự ưu tiên. POS không chắc chắn
  là `UNKNOWN` và không được kích hoạt đảo grammar.
- Trên chương thật 7.620 ký tự của LDPlayer: cold cache ready 2.581 ms, warm render 947,3 ms.
  Output không còn cụm lạc nghĩa `Quốc hội` và không phát sinh crash.

### AI runtime và Router

- `maxInputChars`, `concurrentRequests` và `retryCount` của prompt preset là cấu hình runtime chính;
  global chỉ dùng cho preset cũ. Concurrency 1 không còn bị tự nâng lên 2.
- Combo chuẩn `Dịch AI · Free fallback` được migrate về tuần tự ngay trong `resolveRoute`, không phụ
  thuộc việc người dùng đã mở Dashboard. Route tùy chỉnh không bị sửa.
- Khi toàn bộ target cooldown, Router trả `ROUTE_UNAVAILABLE` kèm tên combo, trạng thái target và
  thời gian thử lại. Parse/layout/CJK sai được ghi nhận là semantic failure.
- Bài thử race trên LDPlayer xác nhận lỗi AI đến muộn trong lúc Reader đang ở QT hoặc Hán-Việt chỉ
  cập nhật task/cache nền, không còn ghi đè nội dung hoặc hiện lỗi sai chế độ.

### Cache và ML Kit

- AI cache tiếp tục dùng chung khi đổi model, combo hoặc prompt. QT, NMT, Google, ML Kit và AI có
  namespace cache độc lập và không bị xóa khi đổi provider.
- Retry thường chỉ chạy chunk thiếu/lỗi/stale; dịch lại chương vẫn giữ bản pass cũ trên màn hình cho
  đến khi bản mới thành công.
- Dependency hash từ điển được tính theo từng chunk; ML Kit cũng tham gia invalidation vì dùng QT để
  sửa residual CJK. Repair chỉ thay CJK run còn sót, không dịch lại phần tiếng Việt đã pass.

### Bằng chứng tự động và release

- Toàn bộ `appDebugUnitTest`: 619 pass, 0 failure, 0 error, 1 test chủ động skip.
- Debug x86_64 đã cài và smoke trên LDPlayer; release x86_64 đã cài, cold launch thành công và không
  có `AndroidRuntime` crash.
- APK ký cuối: `release/signed-apks-20260727-phase03-translation-final`; zipalign pass, chữ ký v2/v3
  hợp lệ, có `.idsig` v4 riêng.

### Gate chưa đóng

- Chưa chạy đủ chọn raw trên toàn bộ RAW/Hán-Việt/QT/Google/ML Kit/NMT/AI bằng dữ liệu thật.
- Chưa chạy ML Kit offline, NMT/GGUF thật, manga OCR/export và soak 10 chương hoặc 30 phút.
- Gói debug hiện không có credential Codex OAuth sau khi tách dữ liệu thử nghiệm; routing OAuth có
  unit test nhưng cần đăng nhập lại để xác nhận live chatbot và dịch.
- Vì vậy C03.01 và C03.05 vẫn giữ `AUTOMATED_DONE / DEVICE_PARTIAL`, không đánh dấu hoàn tất giả.

## 8. Sửa mapping raw riêng cho thao tác thêm từ điển QT - 2026-07-27

- `QuickTranslationRepository.translateMapped` trả segment provenance theo đúng candidate mà DP đã
  chọn, gồm project term, candidate từ vựng, phonetic và literal passthrough. API chuỗi cũ được giữ
  nguyên và test xác nhận hai API tạo cùng output với URL, markup và CRLF.
- Reader dùng đầy đủ project dictionary và dictionary cũ của sách khi tái tạo mapping. Segment được
  rebase lên nội dung QT thật đang hiển thị để chịu được content processor xóa hoặc thay một phần
  văn bản mà không dùng tỷ lệ toàn chương.
- Cụm lặp được đối chiếu tuần tự, vì vậy lần xuất hiện thứ hai không còn map về lần đầu.
- Action `Thêm vào từ điển QT` chỉ hiển thị và được ViewModel chấp nhận khi chế độ hiện tại là
  `QUICK_TRANSLATOR`; snapshot còn sót từ RAW/Hán-Việt/AI không thể đi vào luồng lưu.
- LDPlayer xác nhận trong tab QT: chọn `Lý truy viễn` trả raw `李追远`, vị trí nguồn 6901. Không có
  `AndroidRuntime` crash sau thao tác.
- Full suite sau thay đổi: 620 test, 0 failure, 0 error, 1 skip. Debug x86_64 SHA-256:
  `90FAA8A024E793AECED1337F665A4196637AD6507F13C9A66F00AE46F6D9132B`.

## 9. Thu gọn popup và neo preview raw - 2026-07-27

- Preview nguồn chỉ giữ 14 ký tự liền kề mỗi phía, đặt raw ở giữa và dùng dấu lược khi còn nội
  dung bị ẩn. Raw luôn xuất hiện trong hai dòng preview, không còn trường hợp context phía trước
  chiếm hết giới hạn dòng làm người dùng thấy hai đoạn không liên quan.
- Ô raw chuyển sang một dòng; vị trí nguồn và URL mỗi mục tối đa một dòng có ellipsis.
- Bổ sung dải biến đổi bản dịch theo mẫu VBook: `aa`, `Aa¹`, `Aa²`, `Aa³`, `Aa`, `AA` tương ứng
  viết thường, in hoa chữ đầu 1/2/3/tất cả từ và viết hoa toàn bộ.
- LDPlayer xác nhận `aa → Lý Truy viễn → Lý Truy Viễn` cập nhật đúng state, không đổi raw/Hán-Việt;
  preview `等` hiển thị đúng trong context liền kề. APK debug cuối có SHA-256
  `6D7119F7BFA0DEDAF86D87EA7B82898F903ED6EA2D604CD140CA2A6C0176E4A3`.
