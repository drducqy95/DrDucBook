Phụ lục Bộ mẫu Kỹ năng Gemini Web** để bạn lưu trực tiếp vào dự án **`DrDucBook`**.



---



# 📁 FILE 1: `plans/261014-1415-fix-qt-i18n-blocks-sourcehealth/plan.md`



```markdown

# Plan: Khôi Phục QT Gốc (Từ Điển + Luật Nhân), Sửa Lỗi Tiếng Việt Mojibake/i18n, Thu Gọn Khối Tải Xuống, Chuẩn Hóa Source Health, Sửa Lỗi TTS, Khóa Chặt AI Model/Combo & Tối Ưu Hóa Gemini Web (4 Model Thực Tế + Cấp Độ Tư Duy + Lồng Kỹ Năng Gemini Web Vào Prompt) — Milestone P77



> **Mã kế hoạch:** `PLN-261014-1415`  

> **Đường dẫn:** `plans/261014-1415-fix-qt-i18n-blocks-sourcehealth/plan.md`  

> **Milestone:** `P77`  

> **Ngày tạo:** 2026-10-14T14:15:00+07:00  

> **Ngày cập nhật:** 2026-10-14T16:15:00+07:00  

> **Trạng thái:** 🟡 Sẵn sàng triển khai (Ready for Execution)  

> **Thiết bị mục tiêu:** HONOR LLY-LX2 (Android 14) / Huawei Nova HBN-LX9 / LDPlayer `emulator-5554`



---



## 1. Tổng Quan & Mục Tiêu Trọng Tâm (Unified Executive Summary)



Kế hoạch Milestone P77 hợp nhất toàn bộ các yêu cầu sửa lỗi cốt lõi và nâng cấp kiến trúc của dự án `DrDucBook` vào một lộ trình 5 Phase đồng bộ:



1. **Phase 01 — Khôi Phục Thuật Toán QT Về QT Gốc (Từ Điển + Luật Nhân) & Mapping 1:1 (`phase-01-qt-speed-and-mapping.md`):**

   - Đưa thuật toán dịch nhanh về đúng bản chất **QuickTranslator (QT) gốc**: sử dụng thuật toán **Khớp từ dài nhất từ trái sang phải (Linear FMM — Forward Maximum Matching)** kết hợp **Bộ quy tắc `LuatNhan` (Luật Nhân)** và **Từ điển đa tầng** (`Book/StoryMemory Dict` > `User QuickDict` > `Names` > `VietPhrase` > `ChinesePhienAmWords`).

   - Loại bỏ `beamTranslationPlan` (multi-hypothesis beam search) và các bộ đoán ngữ pháp/202 regex hậu xử lý tự chế gây biến dạng câu, làm chậm tốc độ và làm trượt vị trí ánh xạ từ.

   - Đạt tốc độ trả output tức thì (**< 50ms/chương**), đảm bảo **80–90% chất lượng bản dịch quyết định bởi độ phủ của bộ từ điển & Luật Nhân**, và giữ ánh xạ `MappedDisplayText` chính xác tuyệt đối **1:1** cho tính năng bôi đen tra từ (`QuickDictionarySelectionResolver`).

2. **Phase 02 — Sửa Lỗi Tiếng Việt Mojibake & Hoàn Thiện Quốc Tế Hóa i18n (`phase-02-fix-vietnamese-mojibake.md`):**

   - Khử triệt để lỗi hiển thị tiếng Việt Mojibake, dấu `?` kẹt giữa tên riêng phiên âm (`Noah? Pat`), lỗi giải mã bảng mã `GB18030`/`UTF-8` và chuẩn hóa tổ hợp dấu Unicode `NFC`.

   - Việt hóa 100% các nhãn trạng thái, bước kiểm tra (`stageKey`) và thông báo còn hiển thị mã Enum kỹ thuật thô trên `SourceHealthScreen`, `DownloadCenterScreen`, `ReadAloud`; đồng bộ tuyệt đối khóa tài nguyên giữa `values/strings.xml` và `values-vi/strings.xml`.

3. **Phase 03 — Thu Gọn Các Khối Giao Diện Tải Xuống & Khối Nội Dung (`phase-03-compact-download-blocks.md`):**

   - Tái thiết kế các khối/thẻ tải xuống trong `DownloadCenterScreen`, `BookCacheManageScreen`, `DownloadCacheConfigScreen`, `TranslationConfigScreen`, `MlKitModelsScreen` và `ReadAloudConfigSheet` theo chuẩn `CompactDownloadBlock` (`54.dp`–`72.dp`), đưa các nút hành động thành `IconButton(32.dp)` gọn trên cùng 1 hàng.

   - Thu gọn các khối đoạn văn rỗng phát sinh sau khi lọc nội dung quảng cáo trong `BookContentProcessEngine`.

4. **Phase 04 — Nâng Cao Độ Chính Xác Kiểm Tra Sức Khỏe Nguồn (`phase-04-source-health-accuracy.md`):**

   - Chuẩn hóa `SourceCheckClassifier` và các Probe Adapters (`Book`, `RSS`, `VBook`) để không báo lỗi giả khi nguồn thiếu optional stage hoặc khi thiết bị ngoại tuyến.

   - Sửa lỗi xóa nguồn đa loại (`book:`, `rss:`, `vbook:`), dọn dẹp sạch bản ghi mồ côi trong `BookSourceHealthDao` và `SourceCheckDao`, đồng bộ 100% bộ đếm `SourceHealthSummaryUi` với bộ lọc `SourceHealthFilter`.

5. **Phase 05 — Tối Ưu Hóa Gemini Web (4 Model Thực Tế + Cấp Độ Tư Duy + Lồng Kỹ Năng Gemini Web Vào Prompt), Khóa Chặt AI Model/Combo, Sửa Lỗi TTS & Kiểm Thử (`phase-05-verification-testing.md`):**

   - **Đồng bộ 4 Model thực tế & Cấp độ tư duy của Gemini Web:** Cập nhật đúng 4 model thực tế hiển thị trên giao diện Gemini Web hiện hành (`3.5 Flash-Lite` — *Câu trả lời nhanh nhất*, `3.8 Flash` — *Trợ giúp toàn diện*, `3.1 Pro` — *Suy luận nâng cao*, `4 Argon` — *Nhiệm vụ phức tạp*) kèm khả năng chọn **Cấp độ tư duy (`Cấp độ tư duy ▸`: Tự động / Thấp / Trung bình / Cao)** và bóc tách sạch khối suy luận nội bộ (`thought`).

   - **Tận dụng tính năng “Kỹ năng” (Tuỳ chỉnh $\rightarrow$ Kỹ năng) của Gemini Web lồng vào Prompt:** Cho phép người dùng khai báo tên **Kỹ năng** đã tạo trước trên Gemini Web (ví dụ: Kỹ năng dịch Tiên Hiệp/Cổ Phong, Kỹ năng Biên tập/Rewrite); tự động lồng chỉ thị kích hoạt Kỹ năng vào đầu prompt trong `GeminiWebHandler` và rút gọn các quy tắc lặp lại trong System Prompt để giảm 70%–80% độ dài prompt hệ thống, tăng tốc độ phản hồi và tối ưu chất lượng đầu ra.

   - **Khóa chặt lựa chọn Model/Combo AI — Chống tự nhảy về Free Fallback:** Vá triệt để 5 lỗ hổng trong `AiRouterViewModel`, `AiRouterRepository`, `RepairAiRouteBindingsUseCase` và `AiComboModelPickerSheet` khiến model đơn lẻ hoặc combo do người dùng chọn bị ghi đè về `"Free fallback"`.

   - **Sửa lỗi chạy TTS (`ReadAloud` / `LocalTts` / `ZeroTTS`):** Khắc phục lỗi định tuyến service, race condition `onDestroy()` khi đổi engine, bắt buộc `Dispatchers.Main` cho `ExoPlayer`, bỏ qua đoạn văn chỉ có dấu câu `***`, và tự động fallback an toàn cho tệp model INT8 / `.npy` voice embeddings.



---



## 2. Cấu Trúc Chi Tiết Các Phase (Phase Directory Matrix)



| Phase | Tệp Kế Hoạch Chi Tiết | Nội Dung Trọng Tâm | Các File Codebase Chính | Trạng Thái |

|---|---|---|---|---|

| **01** | [`phase-01-qt-speed-and-mapping.md`](./phase-01-qt-speed-and-mapping.md) | Khôi phục thuật toán QT Gốc (FMM + `LuatNhan` + Từ điển), bỏ Beam Search/Heuristic gây nhiễu, sửa Mapping 1:1 | `QuickTranslationRepository.kt`, `MappedDisplayText.kt`, `QuickDictionarySelectionResolver.kt`, `TranslateDynamicUiTextUseCase.kt` | ⬜ Pending |

| **02** | [`phase-02-fix-vietnamese-mojibake.md`](./phase-02-fix-vietnamese-mojibake.md) | Khử lỗi tiếng Việt Mojibake, chuẩn hóa NFC, sửa lỗi charset và hoàn thiện i18n `strings.xml` / `strings-vi.xml` | `VietnameseTranslationPostProcessor.kt`, `SourceHealthScreen.kt`, `strings.xml`, `strings-vi.xml` | ⬜ Pending |

| **03** | [`phase-03-compact-download-blocks.md`](./phase-03-compact-download-blocks.md) | Thu gọn giao diện các khối tải xuống (`CompactDownloadBlock` `54.dp`–`72.dp`) & thu gọn khối nội dung rỗng | `DownloadCenterScreen.kt`, `BookCacheManageScreen.kt`, `DownloadCacheConfigScreen.kt`, `TranslationConfigScreen.kt`, `MlKitModelsScreen.kt`, `ReadAloudConfigSheet.kt`, `BookContentProcessEngine.kt` | ⬜ Pending |

| **04** | [`phase-04-source-health-accuracy.md`](./phase-04-source-health-accuracy.md) | Nâng cao độ chính xác Source Health, xóa nguồn đa loại (`Book`/`RSS`/`VBook`) & đồng bộ bộ lọc/bộ đếm | `SourceCheckClassifier.kt`, `SourceHealthProbeRepositories.kt`, `BookSourceHealthRepository.kt`, `SourceCheckRepository.kt`, `SourceHealthViewModel.kt`, `SourceHealthScreen.kt` | ⬜ Pending |

| **05** | [`phase-05-verification-testing.md`](./phase-05-verification-testing.md) | (1) Gemini Web `3.5 Flash-Lite` / `3.8 Flash` / `3.1 Pro` / `4 Argon` + Cấp độ tư duy + Lồng Kỹ năng Gemini Web vào Prompt; (2) Khóa chặt AI Model/Combo; (3) Sửa lỗi TTS; (4) Kiểm thử toàn diện | `GeminiWebHandler.kt`, `AiProviderCatalog.kt`, `AiModels.kt`, `AiTranslationRefinePipeline.kt`, `TranslationConfigScreen.kt`, `TranslationPromptConfigScreen.kt`, `AiRouterViewModel.kt`, `AiRouterRepository.kt`, `RepairAiRouteBindingsUseCase.kt`, `ReadAloud.kt`, `BaseReadAloudService.kt`, `LocalTtsReadAloudService.kt`, `ZeroTtsOnnxEngine.kt` | ⬜ Pending |



---



## 3. Tiêu Chí Nghiệm Thu Tổng Thể (Master Acceptance Criteria)

- [ ] **Phase 01 (QT Gốc & Mapping):** Tốc độ dịch chương < 50ms, chuẩn `VietPhrase` + `LuatNhan` (đạt 80–90% chất lượng qua từ điển), bôi đen từ bất kỳ trên màn hình đọc trích xuất đúng 100% chữ Hán gốc.

- [ ] **Phase 02 (Mojibake & i18n):** Không còn dấu `?` lỗi giữa tên riêng hay ký tự lạ; 100% nhãn trên `SourceHealthScreen` và toàn app hiển thị tiếng Việt chuẩn.

- [ ] **Phase 03 (Compact Download Blocks):** Các khối tải xuống trong `DownloadCenterScreen`, `BookCacheManageScreen`, `TranslationConfigScreen`, `ReadAloudConfigSheet` thu gọn chuẩn `54.dp`–`72.dp`.

- [ ] **Phase 04 (Source Health):** Bộ đếm khớp 100% với bộ lọc; xóa nguồn lỗi xóa sạch cả `Book`/`RSS`/`VBook` và lịch sử kiểm tra.

- [ ] **Phase 05.A (Gemini Web Models, Thinking Levels & Native Skills):**

  - Chọn và chạy đúng 4 model thực tế (`3.5 Flash-Lite`, `3.8 Flash`, `3.1 Pro`, `4 Argon`) kèm các mức **Cấp độ tư duy** (`Tự động`, `Thấp`, `Trung bình`, `Cao`).

  - Lồng thành công **Kỹ năng (Skill) đã tạo trên Gemini Web** vào prompt (kèm chế độ rút gọn System Prompt thừa), tách sạch khối `thought` khỏi văn bản đầu ra.

- [ ] **Phase 05.B (AI Model/Combo Lock):** Sau khi chọn Model lẻ hoặc Combo ở bất kỳ tác vụ AI nào, tuyệt đối giữ nguyên lựa chọn đó, không bao giờ tự ý nhảy về `"Free fallback"`.

- [ ] **Phase 05.C (TTS):** Đọc thành tiếng mượt mà trên cả System TTS và ZeroTTS, vượt qua dòng `***` và đổi giọng không bị dừng/crash.



```



---



# 📁 FILE 2: `plans/261014-1415-fix-qt-i18n-blocks-sourcehealth/phase-01-qt-speed-and-mapping.md`



```markdown

# Phase 01: Đưa Thuật Toán QT Về QT Gốc (Từ Điển + Luật Nhân), Tăng Tốc Độ Output & Chuẩn Hóa Text Mapping 1:1



> **Thuộc kế hoạch:** [`plan.md`](./plan.md)  

> **Phase ID:** `phase-01`  

> **Trạng thái:** ⬜ Pending  

> **Phụ thuộc:** Không



---



## 1. Mục Tiêu (Objectives)

1. **Đưa thuật toán dịch nhanh về đúng bản chất QuickTranslator (QT) gốc:**

   - Sử dụng thuật toán **Khớp từ dài nhất từ trái sang phải (Linear FMM — Forward Maximum Matching)** kết hợp với **Bộ quy tắc `LuatNhan` (Luật Nhân)** và **Hệ thống Từ điển đa tầng** (`StoryMemory / BookDict` > `User QuickDict` > `Names` > `VietPhrase` > `ChinesePhienAmWords`).

   - Loại bỏ cơ chế duyệt đa giả thuyết `beamTranslationPlan` (`QtHypothesis`, `QtHypNode`) và các bộ suy diễn ngữ pháp tự chế / 202 regex hậu xử lý nặng nề đang làm chậm tốc độ và làm biến dạng câu.

2. **Đảm bảo chất lượng 80–90% qua bộ từ điển & Luật Nhân với tốc độ tức thì:**

   - Rút ngắn thời gian dịch một chương 3.000–5.000 chữ Hán xuống **< 50ms**.

3. **Khôi phục ánh xạ văn bản (`MappedDisplayText`) chính xác tuyệt đối 1:1:**

   - Mọi từ/cụm từ tiếng Việt hiển thị trên màn hình đọc đều trỏ chính xác về khoảng ký tự chữ Hán gốc (`rawStart..rawEnd`), giúp `QuickDictionarySelectionResolver` trích xuất đúng 100% chữ Hán gốc trong `< 1ms` khi người dùng bôi đen thêm từ điển.



---



## 2. Phân Tích Nguyên Nhân Gốc Rễ (Root Cause Analysis)



### 2.1. Tại sao thuật toán QT hiện tại bị chậm và biến dạng nghĩa?

- Trong `QuickTranslationRepository.kt`, các phiên bản trước đã chồng thêm:

  1. `beamTranslationPlan` (duyệt top-K giả thuyết `QtHypothesis` / `QtHypNode` với `beamWidth = 1..5`).

  2. Các hàm tự đoán ngữ pháp ngoài phạm vi Luật Nhân (`renderAttributiveDe`, `bestGrammarMatch`, `Qt2025Runtime` với 287 quy tắc tự chế).

  3. Vòng lặp 202 biểu thức chính quy hậu xử lý (`qt_clean_rules_v8.tsv`) trong `QuickTranslationTextPostProcessor`.

- **Hậu quả:**

  - Các quy tắc tự đoán định ngữ và regex hậu xử lý can thiệp thô bạo vào kết quả đã tra từ `VietPhrase`, gây ra hiện tượng đảo từ sai ranh giới mệnh đề, xóa nhầm từ hoặc ghép các từ không liên quan.

  - Tiêu tốn hàng trăm mili-giây CPU và tạo áp lực cấp phát bộ nhớ (GC thrashing) không cần thiết.



### 2.2. Tại sao bôi đen chữ để thêm từ điển (`QuickDictionarySelectionResolver`) bị lệch hoặc trống?

- Khi `beamTranslationPlan` và 202 regex hậu xử lý thay đổi trật tự từ hoặc cắt/ghép chuỗi sau khi đã sinh văn bản, các tọa độ `displayStart..displayEnd` trong `MappedDisplayText.spans` bị trượt khỏi vị trí thực tế.

- Khi người dùng bôi đen một cụm từ trên màn hình đọc, `QuickDictionarySelectionResolver` không tìm được `MappedSpan` khớp chính xác, phải rơi vào nhánh tìm kiếm xấp xỉ theo dòng/đoạn dẫn đến trả về cả đoạn văn dài hoặc để trống trường chữ Hán gốc (Raw).



---



## 3. Thiết Kế Kiến Trúc QT Gốc (Classic FMM + `LuatNhan` Engine)



### 3.1. Sơ Đồ Luồng Xử Lý Tuyến Tính 3 Bước (3-Stage Linear Pipeline)

```text

[Văn bản CJK Gốc (Raw Text)]

   │

   ▼

[Bước 1: Tiền Xử Lý & Bảo Vệ Token (Pre-Clean & Protected Spans)]

   • Loại bỏ các từ nằm trong danh sách IgnoreWord (Pre-clean) nhưng vẫn giữ nguyên bản đồ chỉ số gốc (raw offset map).

   • Nhận diện và khóa cứng các ProtectedToken: URL, thẻ HTML/XHTML, EPUB Block Markers (drop-cap, img, css alignment).

   │

   ▼

[Bước 2: Duyệt Tuyến Tính Khớp Luật Nhân & Khớp Từ Điển Dài Nhất (Linear FMM + LuatNhan)]

   • Cho con trỏ i chạy từ 0 đến rawText.length - 1:

     1. Nếu vị trí i là ProtectedToken hoặc ký tự phi CJK (Latin/Số/Xuống dòng):

        - Ghi nguyên vẹn vào MappedTextBuilder, tạo MappedSpan(i, i + len, dispStart, dispEnd), tăng i += len.

     2. Kiểm tra khớp mẫu LuatNhan tại vị trí i (qua chỉ mục ký tự đầu O(1) LeadFilter / Prefix Trie):

        - Mẫu LuatNhan có dạng: "A{0}B", "{0}的{1}", "不{0}了", "被{0}所{1}", "第{0}章"...

        - Nếu khớp một mẫu LuatNhan hợp lệ (các biến {0}, {1} không vượt qua dấu câu hay từ giới hạn):

          + Dịch đệ quy từng đoạn con trong biến {0}, {1} bằng chính hàm FMM Từ điển.

          + Ráp theo mẫu đích tiếng Việt của LuatNhan (VD: "{1} của {0}" hoặc "không {0} nữa").

          + Ghi nhận đầy đủ các MappedSpan con của từng từ bên trong {0}, {1} và phần khung LuatNhan vào MappedTextBuilder!

          + Nhảy con trỏ: i = match.endIndex.

     3. Nếu không khớp LuatNhan, thực hiện Khớp Từ Điển Dài Nhất (Longest Prefix Match - FMM) từ độ dài maxLen về 1:

        - Sử dụng bitmask lengthsMaskByFirstChar[rawText[i]] để bỏ qua ngay các độ dài không tồn tại.

        - Tra cứu theo thứ tự ưu tiên tuyệt đối:

          (a) Book / StoryMemory Dictionary (Từ điển riêng của bộ truyện)

          (b) User QuickDictionary (Từ điển người dùng lưu trong Room DB)

          (c) Names (Từ điển Tên riêng nhân vật / địa danh)

          (d) VietPhrase (Từ điển cụm từ cốt lõi — quyết định 80-90% chất lượng bản dịch)

          (e) ChinesePhienAmWords (Từ điển âm Hán-Việt cho chữ đơn)

        - Lấy nghĩa đầu tiên (first meaning trước dấu '/' hoặc '|').

        - Tự động chèn 1 dấu cách phân tách từ nếu ký tự trước đó là chữ/số.

        - Ghi trực tiếp MappedSpan(rawStart = i, rawEnd = i + L, displayStart, displayEnd).

        - Nhảy con trỏ: i += L.

   │

   ▼

[Bước 3: Chuẩn Hóa Dấu Câu & Viết Hoa Đầu Câu (Zero-Drift Formatting)]

   • Chuyển dấu câu toàn giác CJK (，。！？：“”‘’【】（）) sang dấu câu chuẩn tiếng Việt (, . ! ? : " " ' ' [ ] ( )).

   • Thực hiện ngay trong MappedTextBuilder để không làm dịch chuyển bất kỳ offset nào của MappedSpan!

   • Viết hoa chữ cái đầu đoạn và chữ cái đầu tiên sau dấu kết thúc câu (. ! ? : ") hoặc sau dấu mở ngoặc kép (chỉ đổi char case 1-1 nên độ dài chuỗi và MappedSpan giữ nguyên 100%).



```



---



## 4. Các Bước Triển Khai Chi Tiết (Implementation Steps)



### Task 01.1: Refactor `QuickTranslationRepository.kt` về Thuật Toán QT Gốc (FMM + `LuatNhan`)



* **File:** `app/src/main/java/io/legado/app/data/repository/QuickTranslationRepository.kt`

* **Chi tiết thực hiện:**

1. Trong `translate()` và `translateMapped()`:

* Ngừng gọi `beamTranslationPlan()`. Chuyển cả hai hàm sang sử dụng chung một lõi thực thi tuyến tính `translateLinearQt(rawText, lookup, buildMapping = true/false)`.





2. Tối ưu hóa bộ khớp `LuatNhan`:

* Nạp đầy đủ các quy tắc `LuatNhan` từ tài nguyên đóng gói và từ bảng `QuickDictionary` của người dùng.

* Kiểm soát chặt chẽ ranh giới của các biến `{0}`, `{1}` trong `LuatNhan`: một slot `{0}` không bao giờ được nuốt dấu câu (`，。！？；：“”`) hoặc ký tự xuống dòng `\n`.

* Khi dịch bên trong slot `{0}`, truyền trực tiếp `MappedTextBuilder` vào hàm dịch đệ quy để từng từ đơn/từ ghép bên trong `{0}` đều sinh ra `MappedSpan` riêng biệt của chính nó.





3. Trong `cleanQuickDictionaryTarget(rawTarget)`:

* Khi một mục từ trong `VietPhrase.txt` hoặc `Names.txt` có nhiều nghĩa phân cách bởi `/` hoặc `|` (ví dụ: `修炼=tu luyện/tu hành`), luôn lấy **nghĩa đầu tiên** (`tu luyện`) theo đúng chuẩn QuickTranslator truyền thống, loại bỏ logic tự ý đảo chọn nghĩa gây lệch văn phong.











### Task 01.2: Bảo Toàn 100% Tọa Độ `MappedDisplayText` & Tối Ưu `QuickDictionarySelectionResolver.kt`



* **Files:**

* `app/src/main/java/io/legado/app/domain/model/MappedDisplayText.kt`

* `app/src/main/java/io/legado/app/ui/quickdict/QuickDictionarySelectionResolver.kt`





* **Chi tiết thực hiện:**

1. Xây dựng lớp hỗ trợ nội bộ `MappedTextBuilder` trong `QuickTranslationRepository.kt`:

* Mọi thao tác `appendToken(rawStart, rawEnd, translatedText)` đều tự động xử lý khoảng trắng ngăn cách từ và ghi nhận chính xác `[displayStart, displayEnd)` sau khi đã tính khoảng trắng.

* Việc viết hoa chữ cái đầu câu (`Character.toUpperCase`) được thực hiện tại chỗ trên mảng ký tự hoặc `StringBuilder.setCharAt(idx, upperChar)` để **không làm thay đổi bất kỳ chỉ số độ dài nào của `MappedSpan**`.





2. Trong `QuickDictionarySelectionResolver.kt`:

* Khi người dùng bôi đen vùng `[selStart, selEnd)` trên văn bản hiển thị:

* Tìm tất cả `MappedSpan` trong `mappedDisplayText.spans` có phần giao dương (`maxOf(span.displayStart, selStart) < minOf(span.displayEnd, selEnd)`).

* Nếu các span tìm được liên tiếp nhau trong văn bản gốc (hoặc nằm trong cùng một câu), lấy `rawStart = spans.minOf { it.rawStart }` và `rawEnd = spans.maxOf { it.rawEnd }` làm kết quả chính xác tuyệt đối (`rawText.substring(rawStart, rawEnd)`).

* Tạo thêm danh sách `alternatives` từ các cụm từ lân cận (±1 đến ±2 token) để người dùng có thể mở rộng/thu hẹp nhanh chỉ với 1 chạm.















### Task 01.3: Tinh Giản `QuickTranslationTextPostProcessor` & Cập Nhật `TranslateDynamicUiTextUseCase`



* **Files:**

* `app/src/main/java/io/legado/app/data/repository/QuickTranslationRepository.kt`

* `app/src/main/java/io/legado/app/domain/usecase/TranslateDynamicUiTextUseCase.kt`





* **Chi tiết thực hiện:**

1. Trong `QuickTranslationTextPostProcessor`:

* Gỡ bỏ việc quét 202 regex `qt_clean_rules_v8.tsv` trên văn bản chương truyện để bảo toàn 100% nghĩa từ điển và tọa độ `MappedSpan`.

* Chỉ giữ lại hàm chuẩn hóa chương mục (`Đệ X Chương` $\rightarrow$ `Chương X`) cho tiêu đề chương và hàm dọn khoảng trắng dấu câu cơ bản khi gọi `translate()` không cần mapping.





2. Trong `TranslateDynamicUiTextUseCase.kt`:

* Đảm bảo `needsQuickTranslation()` kiểm tra nhanh sự tồn tại của ký tự CJK (`\u4E00`..`\u9FFF`, `\u3400`..`\u4DBF`), bỏ qua ngay lập tức các chuỗi tiếng Việt/Anh thuần túy.





3. Tăng `QT_CACHE_REVISION` để làm mới toàn bộ cache chương QT cũ.







---



## 5. Danh Sách Tệp Sửa Đổi (Files to Modify)



* `app/src/main/java/io/legado/app/data/repository/QuickTranslationRepository.kt`

* `app/src/main/java/io/legado/app/domain/model/MappedDisplayText.kt`

* `app/src/main/java/io/legado/app/ui/quickdict/QuickDictionarySelectionResolver.kt`

* `app/src/main/java/io/legado/app/domain/usecase/TranslateDynamicUiTextUseCase.kt`

* `app/src/test/java/io/legado/app/data/repository/QuickTranslationRepositoryTest.kt`

* `app/src/test/java/io/legado/app/ui/quickdict/QuickDictionarySelectionResolverTest.kt`



---



## 6. Tiêu Chí Nghiệm Thu (Pass Criteria)



* [ ] **PASS-01 (Tốc độ):** Dịch một chương 3.000–5.000 chữ Hán hoàn tất trong **< 50ms** trên thiết bị và JVM test.

* [ ] **PASS-02 (Chất lượng QT Gốc):** Câu dịch giữ đúng nghĩa `VietPhrase`, `Names` và cấu trúc `LuatNhan` (đạt 80–90% độ mượt thuần từ điển), không bị mất chữ hay đảo lộn câu vô lý.

* [ ] **PASS-03 (Mapping 1:1):** Bôi đen bất kỳ từ đơn, từ ghép hay cụm từ nằm trong cấu trúc `LuatNhan` trên màn hình đọc đều lấy ra đúng 100% chữ Hán gốc tương ứng, không bao giờ bị trống trường Raw hay lấy tràn cả đoạn văn.



```



---



# 📁 FILE 3: `plans/261014-1415-fix-qt-i18n-blocks-sourcehealth/phase-02-fix-vietnamese-mojibake.md`



```markdown

# Phase 02: Sửa Lỗi Tiếng Việt Mojibake, Chuẩn Hóa Unicode & Hoàn Thiện i18n Toàn Diện



> **Thuộc kế hoạch:** [`plan.md`](./plan.md)  

> **Phase ID:** `phase-02`  

> **Trạng thái:** ⬜ Pending  

> **Phụ thuộc:** Phase 01



---



## 1. Mục Tiêu (Objectives)

1. **Khử triệt để lỗi hiển thị tiếng Việt Mojibake:**

   - Xóa bỏ hiện tượng xuất hiện dấu hỏi `?` vô lý nằm giữa tên riêng phiên âm nước ngoài (ví dụ: `Noah? Pat`, `Harry? Potter`), ký tự lỗi giải mã `` (`\uFFFD`), ký tự điều khiển ẩn, hoặc dấu thanh tiếng Việt bị tách rời (NFD) trên các thiết bị Huawei/HONOR.

2. **Hoàn thiện Việt hóa (i18n Parity) 100% trên toàn bộ giao diện:**

   - Loại bỏ toàn bộ các vị trí hiển thị trực tiếp mã `Enum.name` tiếng Anh (`HEALTHY`, `BROKEN_RULE`, `AUTH_REQUIRED`, `CAPTCHA_REQUIRED`, `RATE_LIMITED`, `reachability`, `toc`, `QUICK`, `STANDARD`, `FULL`...) trên `SourceHealthScreen`, `DownloadCenterScreen`, `ReadAloudConfigSheet`.

   - Đồng bộ tuyệt đối khóa tài nguyên giữa `app/src/main/res/values/strings.xml` và `app/src/main/res/values-vi/strings.xml`.



---



## 2. Phân Tích Nguyên Nhân Gốc Rễ (Root Cause Analysis)



1. **Nguyên nhân lỗi `Noah? Pat` và Mojibake trong nội dung chương:**

   - Trong nguyên tác tiếng Trung, tên nhân vật phương Tây có dấu chấm giữa (`·` — `\u00B7` hoặc `•` — `\u2022`, ví dụ `诺亚·帕特`). Khi trang nguồn dùng bảng mã `GBK`/`GB2312` cũ hoặc chuyển mã lỗi, dấu `·` bị biến thành dấu `?` ASCII (`诺亚?帕特`). Khi đi qua bộ dịch QT hoặc AI, nó biến thành `Noah? Pat` hoặc `Noah ? Pat`.

   - Khi nhận luồng chữ từ một số model AI hoặc đọc file TXT/EPUB ngoài, chuỗi tiếng Việt có thể chứa tổ hợp dấu rời (`Normalizer.Form.NFD`) hoặc lỗi viết hoa giữa chừng (`fixAbnormalMixedCase`), gây lỗi hiển thị trên font tùy chỉnh.

2. **Nguyên nhân lộ mã kỹ thuật thô trên UI:**

   - Trong `SourceHealthScreen.kt`, các thành phần hiển thị trạng thái nguồn (`BookSourceHealthStatus`), bước kiểm tra (`stageKey` trong `SourceCheckStageResult`), và chế độ kiểm tra (`SourceCheckProfile`) chưa có hàm ánh xạ đầy đủ sang `@StringRes` tiếng Việt.



---



## 3. Các Bước Triển Khai Chi Tiết (Implementation Steps)



### Task 02.1: Xử Lý Dứt Điểm Mojibake & Chuẩn Hóa Unicode NFC

- **Files:**

  - `app/src/main/java/io/legado/app/domain/model/VietnameseTranslationPostProcessor.kt`

  - `app/src/main/java/io/legado/app/data/repository/QuickTranslationRepository.kt`

- **Chi tiết thực hiện:**

  1. Ở bước đầu vào (`normalizeRawSource`) của cả QT và AI/MLKit:

     - Chuẩn hóa chuỗi bằng `Normalizer.normalize(input, Normalizer.Form.NFC)`.

     - Chuyển các dấu `?` hoặc `\uFFFD` nằm kẹp trực tiếp giữa hai ký tự chữ Hán (`(?<=[\p{IsHan}])[?\uFFFD]+(?=[\p{IsHan}])`) thành dấu chấm giữa chuẩn `·` trước khi tra từ điển tên riêng, giúp `诺亚?帕特` tự động khớp chính xác với mục từ `诺亚·帕特=Noah Pat`!

  2. Ở bước hậu xử lý (`VietnameseTranslationPostProcessor.cleanRogueNameQuestionMarks`):

     - Tự động làm sạch mẫu `([A-ZÀ-Ỹ][a-zà-ỹ]+)\s*\?\s*([A-ZÀ-Ỹ][a-zà-ỹ]+)` thành `$1 $2` để khử hoàn toàn lỗi `Noah? Pat` nếu còn sót.

     - Loại bỏ toàn bộ ký tự `\uFFFD`, ký tự zero-width (`\u200B`..`\u200D`, `\uFEFF`) và chuẩn hóa lại `NFC` trước khi lưu cache / hiển thị.



### Task 02.2: Bổ Sung & Đồng Bộ Khóa Ngôn Ngữ Trong `strings.xml` và `strings-vi.xml`

- **Files:**

  - `app/src/main/res/values/strings.xml`

  - `app/src/main/res/values-vi/strings.xml`

- **Chi tiết thực hiện:**

  - Bổ sung đầy đủ các khóa sau vào cả 2 tệp (đảm bảo không thiếu khóa nào ở `values/strings.xml` mặc định):

    - **Trạng thái Sức khỏe nguồn (`BookSourceHealthStatus`):**

      - `source_health_status_healthy`: `Khỏe mạnh` / `Healthy`

      - `source_health_status_degraded`: `Suy giảm` / `Degraded`

      - `source_health_status_auth_required`: `Cần đăng nhập` / `Login Required`

      - `source_health_status_captcha_required`: `Cần giải Captcha` / `Captcha Required`

      - `source_health_status_rate_limited`: `Giới hạn tần suất` / `Rate Limited`

      - `source_health_status_broken_rule`: `Lỗi quy tắc` / `Broken Rule`

      - `source_health_status_network_error`: `Lỗi mạng` / `Network Error`

      - `source_health_status_tls_error`: `Lỗi chứng chỉ TLS` / `TLS Error`

      - `source_health_status_content_empty`: `Nội dung trống` / `Empty Content`

      - `source_health_status_http_error`: `Lỗi HTTP` / `HTTP Error`

      - `source_health_status_unknown_offline`: `Ngoại tuyến` / `Offline`

      - `source_health_status_unsupported`: `Không hỗ trợ` / `Unsupported`

    - **Các bước kiểm tra (`SourceCheckStage`):**

      - `source_health_stage_reachability`: `Kết nối cơ sở`

      - `source_health_stage_search`: `Tìm kiếm`

      - `source_health_stage_explore`: `Khám phá`

      - `source_health_stage_detail`: `Thông tin truyện`

      - `source_health_stage_toc`: `Mục lục`

      - `source_health_stage_content`: `Nội dung chương`

      - `source_health_stage_media`: `Đa phương tiện`

      - `source_health_stage_feed`: `Nguồn cấp RSS`

      - `source_health_stage_list`: `Danh sách bài viết`

      - `source_health_stage_article`: `Chi tiết bài viết`

      - `source_health_stage_manifest`: `Cấu hình Plugin`

      - `source_health_stage_track`: `Luồng phát Media`

    - **Chế độ kiểm tra (`SourceCheckProfile`) & Trạng thái lượt chạy (`SourceCheckRunStatus`):**

      - `source_health_profile_quick`: `Kiểm tra nhanh`

      - `source_health_profile_standard`: `Tiêu chuẩn`

      - `source_health_profile_full`: `Toàn diện`

      - `source_health_run_running`: `Đang kiểm tra`

      - `source_health_run_completed`: `Hoàn tất`

      - `source_health_run_failed`: `Thất bại`

      - `source_health_run_canceled`: `Đã hủy`

      - `source_health_run_interrupted`: `Bị gián đoạn`



### Task 02.3: Áp Dụng Mapper Tiếng Việt Lên `SourceHealthScreen.kt` & Các Màn Hình Liên Quan

- **File:** `app/src/main/java/io/legado/app/ui/book/source/health/SourceHealthScreen.kt`

- **Chi tiết thực hiện:**

  - Viết các hàm `@Composable fun BookSourceHealthStatus.localizedLabel(): String`, `fun localizeStageKey(stageKey: String): String`, `fun SourceCheckProfile.localizedLabel(): String` và `fun SourceCheckRunStatus.localizedLabel(): String`.

  - Thay thế toàn bộ các chỗ gọi `.name` hoặc hiển thị trực tiếp `stageKey` / `failureStep` thô bằng các hàm mapper trên.



---



## 4. Danh Sách Tệp Sửa Đổi (Files to Modify)

- `app/src/main/java/io/legado/app/domain/model/VietnameseTranslationPostProcessor.kt`

- `app/src/main/java/io/legado/app/data/repository/QuickTranslationRepository.kt`

- `app/src/main/java/io/legado/app/ui/book/source/health/SourceHealthScreen.kt`

- `app/src/main/res/values/strings.xml`

- `app/src/main/res/values-vi/strings.xml`

- `app/src/test/java/io/legado/app/domain/model/VietnameseTranslationPostProcessorTest.kt`



---



## 5. Tiêu Chí Nghiệm Thu (Pass Criteria)

- [ ] **PASS-01:** Các chuỗi văn bản có chứa `诺亚?帕特` hoặc dấu `?` lỗi giữa tên riêng được dịch sạch thành `Noah Pat`.

- [ ] **PASS-02:** Toàn bộ giao diện `SourceHealthScreen` hiển thị 100% tiếng Việt chuẩn, không còn bất kỳ chữ `BROKEN_RULE`, `HEALTHY`, `reachability`, `toc`, `QUICK`... nào.

- [ ] **PASS-03:** Biên dịch `:app:compileAppDebugKotlin` thành công, không có lỗi thiếu hoặc trùng lặp resource.



```



---



# 📁 FILE 4: `plans/261014-1415-fix-qt-i18n-blocks-sourcehealth/phase-03-compact-download-blocks.md`



```markdown

# Phase 03: Thu Gọn Giao Diện Các Khối Tải Xuống & Khối Nội Dung (Compact Download & Content Blocks)



> **Thuộc kế hoạch:** [`plan.md`](./plan.md)  

> **Phase ID:** `phase-03`  

> **Trạng thái:** ⬜ Pending  

> **Phụ thuộc:** Phase 02



---



## 1. Mục Tiêu (Objectives)

1. **Tối ưu hóa mật độ hiển thị (Compact UI Density) cho toàn bộ các khối tải xuống (Download Blocks):**

   - Giảm 45%–55% chiều cao dọc của mỗi thẻ tải xuống (từ `140.dp`–`200.dp` xuống còn **`54.dp`–`72.dp`**) trên tất cả các màn hình:

     - `DownloadCenterScreen.kt` (Trung tâm tải xuống hợp nhất: Sách, File, Media, Xuất EPUB/TXT).

     - `BookCacheManageScreen.kt` & `DownloadCacheConfigScreen.kt` (Quản lý bộ nhớ đệm chương sách).

     - `TranslationConfigScreen.kt` & `MlKitModelsScreen.kt` (Các khối tải mô hình NMT ONNX `HachimiMT`, `NLLB-200`, `LocalAI GGUF`, `ML Kit`).

     - `ReadAloudConfigSheet.kt` (Các khối tải gói `ZeroTTS Base` và 8 gói `ZeroTTS Voice Addons`).

2. **Thu gọn các khối nội dung rỗng trên trình đọc (`BookContentProcessEngine.kt`):**

   - Không để lại các khoảng trống trắng lớn trên trang đọc sau khi quy tắc `BookContentProcess` xóa các đoạn văn quảng cáo/rác.



---



## 2. Quy Chuẩn Thiết Kế `CompactDownloadBlock` (Design Specifications)



### 2.1. Cấu Trúc Hàng Ngang Đơn Nhất (Single-Row Compact Layout)

```text

+-----------------------------------------------------------------------+

| [Thumb] Tên Sách / Tên Gói Model (1 dòng, Ellipsis)     [Pause] [Del] |

|  36x48  124/500 chương • 24.8% • Đang tải                             |

|         [=================>------------------------] (Progress 3.dp)  |

+-----------------------------------------------------------------------+



```



### 2.2. Thông Số Kích Thước Chuẩn (Metrics)



* **Khoảng cách giữa các thẻ (Item Vertical Spacing):** `4.dp` (thay vì `10.dp`–`12.dp`).

* **Padding bên trong thẻ (Card Inner Padding):** `horizontal = 12.dp, vertical = 8.dp` (thay vì `16.dp`).

* **Kích thước Thumbnail / Icon bên trái:**

* Bìa sách: `36.dp x 48.dp`, bo góc `6.dp`.

* Icon loại tệp / Model AI / Giọng đọc TTS: Box `36.dp x 36.dp`, bo góc `8.dp`, icon bên trong `20.dp`.





* **Cột thông tin ở giữa (`Modifier.weight(1f)`):**

* **Dòng 1 (Title):** `MaterialTheme.typography.bodyMedium`, `fontWeight = FontWeight.SemiBold`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`.

* **Dòng 2 (Unified Subtitle):** `MaterialTheme.typography.labelSmall`, màu `onSurfaceVariant`, `maxLines = 1`, gộp gọn: `Đã tải X/Y chương (hoặc MB/MB) • Phần trăm (%) • Trạng thái`.

* **Dòng 3 (Thin Progress Bar):** `LinearProgressIndicator` có `Modifier.fillMaxWidth().height(3.dp)`, chỉ hiển thị khi tác vụ đang `RUNNING` / `PAUSED` / `PENDING` / `ERROR`. Khi đã `COMPLETED`, tự động ẩn thanh progress để thẻ thu gọn còn `54.dp`.





* **Cột nút hành động bên phải (Inline Compact IconButtons):**

* Sử dụng `IconButton(modifier = Modifier.size(32.dp))` với `Icon(modifier = Modifier.size(18.dp))` đặt trên cùng hàng ngang với tiêu đề/phụ đề.

* Xóa bỏ hoàn toàn hàng `Row` chứa các `Button` / `OutlinedButton` chiếm chỗ ở đáy thẻ.







---



## 3. Các Bước Triển Khai Chi Tiết (Implementation Steps)



### Task 03.1: Refactor `DownloadCenterScreen.kt` & `BookCacheManageScreen.kt`



* **Files:**

* `app/src/main/java/io/legado/app/ui/download/DownloadCenterScreen.kt`

* `app/src/main/java/io/legado/app/ui/book/cache/BookCacheManageScreen.kt`

* `app/src/main/java/io/legado/app/ui/config/download/DownloadCacheConfigScreen.kt`





* **Chi tiết thực hiện:**

1. Thu gọn phần Header thống kê ở đầu `DownloadCenterScreen` và `BookCacheManageScreen` thành một thanh `Surface` mỏng (`padding = 8.dp`), hiển thị các con số thống kê trên 1 dòng ngang cùng các chip lọc compact.

2. Áp dụng bố cục `CompactDownloadBlock` cho tất cả các loại item trong danh sách tải xuống:

* Tác vụ tải chương sách (`CacheBook`).

* Tác vụ tải tệp/ebook (`DownloadService`).

* Tác vụ tải Media Video/Audio/Manga (`MediaDownload`).

* Tác vụ xuất sách (`ExportBook`).











### Task 03.2: Thu Gọn Các Khối Tải Model AI / NMT / ML Kit / ZeroTTS



* **Files:**

* `app/src/main/java/io/legado/app/ui/config/translation/TranslationConfigScreen.kt`

* `app/src/main/java/io/legado/app/ui/config/translation/mlkit/MlKitModelsScreen.kt`

* `app/src/main/java/io/legado/app/ui/book/read/ReadAloudConfigSheet.kt`





* **Chi tiết thực hiện:**

1. Trong `TranslationConfigScreen.kt` và `MlKitModelsScreen.kt`:

* Thu gọn các thẻ tải gói mô hình offline (`HachimiMT-60-QT`, `NLLB-200`, `HY-MT 1.8B GGUF`, và các gói ngôn ngữ ML Kit) về dạng dòng compact `56.dp`, đặt nút `Tải xuống / Đang dùng / Xóa` ngay bên phải cùng hàng.





2. Trong `ReadAloudConfigSheet.kt`:

* Thu gọn khối tải gói `ZeroTTS Base` và danh sách 8 gói giọng đọc `ZeroTTS Voice Addons` thành các dòng compact (`48.dp`–`56.dp`), hiển thị tên giọng + dung lượng + trạng thái đã cài + nút tải/chọn trên một dòng duy nhất.











### Task 03.3: Thu Gọn Khối Rỗng Sau Khi Xóa Nội Dung Trong `BookContentProcessEngine.kt`



* **File:** `app/src/main/java/io/legado/app/domain/model/BookContentProcessEngine.kt`

* **Chi tiết thực hiện:**

* Sau khi thực thi các quy tắc `BookContentProcess` (đặc biệt là quy tắc xóa đoạn văn rác hoặc thay thế bằng chuỗi rỗng), chuẩn hóa chuỗi kết quả bằng cách thu gọn các chuỗi xuống dòng liên tiếp (`\n(?:[ \t\u3000]*\n){2,}` $\rightarrow$ `\n\n`) và cắt khoảng trắng thừa ở đầu/cuối chương, tránh tạo ra các khối trống rỗng trên trang đọc.







---



## 4. Danh Sách Tệp Sửa Đổi (Files to Modify)



* `app/src/main/java/io/legado/app/ui/download/DownloadCenterScreen.kt`

* `app/src/main/java/io/legado/app/ui/book/cache/BookCacheManageScreen.kt`

* `app/src/main/java/io/legado/app/ui/config/download/DownloadCacheConfigScreen.kt`

* `app/src/main/java/io/legado/app/ui/config/translation/TranslationConfigScreen.kt`

* `app/src/main/java/io/legado/app/ui/config/translation/mlkit/MlKitModelsScreen.kt`

* `app/src/main/java/io/legado/app/ui/book/read/ReadAloudConfigSheet.kt`

* `app/src/main/java/io/legado/app/domain/model/BookContentProcessEngine.kt`

* `app/src/test/java/io/legado/app/domain/model/BookContentProcessEngineTest.kt`



---



## 5. Tiêu Chí Nghiệm Thu (Pass Criteria)



* [ ] **PASS-01:** Chiều cao mỗi thẻ tải xuống không vượt quá `72.dp` khi đang tải và `58.dp` khi hoàn tất; một màn hình hiển thị được tối thiểu 6–8 tác vụ tải xuống.

* [ ] **PASS-02:** Các nút icon thao tác nhanh (`Pause`, `Resume`, `Retry`, `Delete`) phản hồi tức thì, không gây tràn hay cắt chữ tiêu đề.

* [ ] **PASS-03:** `BookContentProcessEngineTest` pass 100%, không còn dòng trống thừa sau khi xóa khối văn bản.



```



---



# 📁 FILE 5: `plans/261014-1415-fix-qt-i18n-blocks-sourcehealth/phase-04-source-health-accuracy.md`



```markdown

# Phase 04: Nâng Cao Độ Chính Xác Kiểm Tra Sức Khỏe Nguồn (Source Health Accuracy & Multi-Kind Cleanup)



> **Thuộc kế hoạch:** [`plan.md`](./plan.md)  

> **Phase ID:** `phase-04`  

> **Trạng thái:** ⬜ Pending  

> **Phụ thuộc:** Phase 02, Phase 03



---



## 1. Mục Tiêu (Objectives)

1. **Nâng cao độ chính xác của `SourceCheckClassifier` & Probe Adapters (`Book`, `RSS`, `VBook`):**

   - Không đánh dấu lỗi giả (`BROKEN_RULE` / `DEGRADED`) khi nguồn hợp lệ nhưng chỉ thiết kế chuyên cho Tìm kiếm (không có Khám phá) hoặc chỉ chuyên cho Khám phá (không có Tìm kiếm).

   - Nhận diện chuẩn xác trạng thái `CAPTCHA_REQUIRED`, `AUTH_REQUIRED`, `RATE_LIMITED` và bảo vệ trạng thái nguồn khi thiết bị mất mạng (`UNKNOWN_OFFLINE`).

2. **Sửa triệt để lỗi xóa nguồn đa loại (`book:`, `rss:`, `vbook:`) và dọn sạch bản ghi mồ côi:**

   - Khi xóa đơn lẻ hoặc xóa hàng loạt nguồn lỗi trên `SourceHealthScreen`, xóa chính xác nguồn gốc trong `BookSourceDao`, `RssSourceDao`, `VBook` store và xóa sạch lịch sử trong `BookSourceHealthDao`, `SourceCheckDao`, `CacheDao`.

3. **Đồng bộ 100% giữa bộ đếm `SourceHealthSummaryUi`, bộ lọc `SourceHealthFilter` và trạng thái chọn nhiều (`isSelectionMode`).**



---



## 2. Phân Tích Nguyên Nhân Gốc Rễ (Root Cause Analysis)



1. **Báo lỗi giả trên các nguồn chỉ có 1 chiều chức năng (Search-only hoặc Explore-only):**

   - Nhiều nguồn sách cộng đồng chỉ định nghĩa `searchUrl` mà để trống `exploreUrl` (hoặc ngược lại). Nếu adapter vẫn tạo stage cố định và trả về `FAILED` thay vì `SKIPPED`, hoặc khi `SourceCheckClassifier.aggregate()` thấy tất cả stage được khai báo đều `PASSED` còn lại `SKIPPED` mà không quy về `HEALTHY`, nguồn tốt sẽ bị báo lỗi oan.

2. **Xóa sót dữ liệu do lệch định dạng khóa `SourceKey` (`book:<url>` vs `<url>`):**

   - `BookSourceHealthRepository.observeRows()` hợp nhất 3 loại nguồn từ `SourceDomainIndexGateway` với khóa định danh có tiền tố (`book:https://...`, `rss:https://...`, `vbook:plugin_id`), trong khi một số bản ghi trong `book_source_health` hoặc `book_sources` lại dùng URL gốc không tiền tố.

   - Khi `deleteSources()` chỉ truyền một dạng chuỗi vào DAO, các bản ghi mang dạng khóa còn lại sẽ trở thành bản ghi mồ côi (orphan records), khiến nguồn đã xóa vẫn hiện trên danh sách hoặc làm lệch bộ đếm lỗi.

3. **Lệch bộ đếm giữa `SourceHealthSummaryUi` và `SourceHealthFilter.ERROR`:**

   - Nếu công thức đếm `summary.error` chỉ đếm một vài mã lỗi trong khi `SourceHealthFilter.ERROR` lại lọc tất cả các trạng thái khác `HEALTHY` / `UNKNOWN_OFFLINE`, con số trên thẻ tổng kết sẽ không khớp với số lượng nguồn hiển thị khi người dùng bấm vào bộ lọc `Có lỗi`.



---



## 3. Các Bước Triển Khai Chi Tiết (Implementation Steps)



### Task 04.1: Chuẩn Hóa Probe Adapters & `SourceCheckClassifier.kt`

- **Files:**

  - `app/src/main/java/io/legado/app/domain/sourcehealth/SourceCheckClassifier.kt`

  - `app/src/main/java/io/legado/app/data/repository/sourcehealth/SourceHealthProbeRepositories.kt`

- **Chi tiết thực hiện:**

  1. Trong `SourceHealthProbeRepositories.kt`:

     - Đối với `BookSource`: nếu `exploreUrl.isNullOrBlank()` thì stage `explore` được đánh dấu `SKIPPED`; nếu `searchUrl.isNullOrBlank()` thì stage `search` được đánh dấu `SKIPPED`. Miễn là ít nhất 1 trong 2 nhánh (`search` hoặc `explore`) cùng các stage phụ thuộc (`detail`, `toc`, `content` theo độ sâu profile) đạt `PASSED` và không có stage nào `FAILED`, nguồn đạt `HEALTHY`.

  2. Trong `SourceCheckClassifier.kt`:

     - Mở rộng nhận diện `CAPTCHA_REQUIRED` cho các phản hồi chứa dấu hiệu Cloudflare Turnstile, `cf-chl-bypass`, `Just a moment...`, `Attention Required! | Cloudflare`, `人机验证`, `安全验证`.

     - Tạo hàm vị từ chuẩn duy nhất `fun BookSourceHealthStatus.isAttentionRequired(): Boolean` trả về `true` cho: `DEGRADED`, `AUTH_REQUIRED`, `CAPTCHA_REQUIRED`, `RATE_LIMITED`, `BROKEN_RULE`, `NETWORK_ERROR`, `TLS_ERROR`, `CONTENT_EMPTY`, `HTTP_ERROR`, `UNSUPPORTED`.



### Task 04.2: Xóa Nguồn Đa Loại & Dọn Sạch Bản Ghi Mồ Côi (`BookSourceHealthRepository` & `SourceCheckRepository`)

- **Files:**

  - `app/src/main/java/io/legado/app/data/repository/BookSourceHealthRepository.kt`

  - `app/src/main/java/io/legado/app/data/repository/sourcehealth/SourceCheckRepository.kt`

  - `app/src/main/java/io/legado/app/data/dao/BookSourceHealthDao.kt`

  - `app/src/main/java/io/legado/app/data/dao/SourceCheckDao.kt`

- **Chi tiết thực hiện:**

  - Xây dựng hàm xóa thống nhất `deleteSourcesCompletely(keysOrUrls: Collection<String>)`:

    - Với mỗi phần tử, tách rõ `prefix` (`book`, `rss`, `vbook`) và `rawId` (URL hoặc pluginId), tạo tập hợp `allCandidateKeys = setOf(rawId, "book:$rawId", "rss:$rawId", "vbook:$rawId")`.

    - Thực thi xóa nguồn trong `BookSourceDao` (nếu là `book` hoặc không tiền tố), `RssSourceDao` (nếu là `rss`), và `VBook` repository (nếu là `vbook`).

    - Thực thi xóa toàn bộ `allCandidateKeys` trong `BookSourceHealthDao`, `SourceCheckDao` (`deleteRunsBySourceUrls`) và `CacheDao`.



### Task 04.3: Đồng Bộ State Chọn Nhiều & Bộ Lọc Trong `SourceHealthViewModel.kt`

- **Files:**

  - `app/src/main/java/io/legado/app/ui/book/source/health/SourceHealthViewModel.kt`

  - `app/src/main/java/io/legado/app/ui/book/source/health/SourceHealthScreen.kt`

- **Chi tiết thực hiện:**

  - Sử dụng hàm `status.isAttentionRequired()` thống nhất cho cả việc tính `SourceHealthSummaryUi.error` lẫn việc lọc danh sách khi `filter == SourceHealthFilter.ERROR`.

  - Sau khi `deleteSources` hoàn tất:

    - Cập nhật `selectedUrls = selectedUrls - deletedUrls`.

    - Nếu `selectedUrls.isEmpty()`, tự động chuyển `isSelectionMode = false`.

    - Nếu `selectedSourceUrl in deletedUrls`, đặt `selectedSourceUrl = null` và `selectedRunId = null` để đóng BottomSheet chi tiết an toàn.



---



## 4. Danh Sách Tệp Sửa Đổi (Files to Modify)

- `app/src/main/java/io/legado/app/domain/sourcehealth/SourceCheckClassifier.kt`

- `app/src/main/java/io/legado/app/data/repository/sourcehealth/SourceHealthProbeRepositories.kt`

- `app/src/main/java/io/legado/app/data/repository/sourcehealth/SourceCheckRepository.kt`

- `app/src/main/java/io/legado/app/data/repository/BookSourceHealthRepository.kt`

- `app/src/main/java/io/legado/app/ui/book/source/health/SourceHealthViewModel.kt`

- `app/src/main/java/io/legado/app/ui/book/source/health/SourceHealthScreen.kt`

- `app/src/test/java/io/legado/app/ui/book.source/health/SourceHealthStateTest.kt`

- `app/src/test/java/io/legado/app/domain/sourcehealth/SourceCheckClassifierTest.kt`

- `app/src/test/java/io/legado/app/data/repository/sourcehealth/SourceCheckRepositoryTest.kt`



---



## 5. Tiêu Chí Nghiệm Thu (Pass Criteria)

- [ ] **PASS-01:** Con số trên thẻ `Có lỗi` trong `SourceHealthSummaryUi` khớp 100% với số lượng nguồn hiển thị khi chọn chip lọc `Có lỗi`.

- [ ] **PASS-02:** Xóa nguồn lỗi (đơn lẻ hoặc hàng loạt) xóa sạch cả nguồn `Book`, `RSS`, `VBook` và toàn bộ lịch sử chạy, cập nhật UI tức thì.

- [ ] **PASS-03:** `SourceHealthStateTest`, `SourceCheckClassifierTest`, `SourceCheckRepositoryTest` pass 100%.



```



---



# 📁 FILE 6: `plans/261014-1415-fix-qt-i18n-blocks-sourcehealth/phase-05-verification-testing.md`



```markdown

# Phase 05: Tối Ưu Hóa Gemini Web (4 Model Thực Tế + Cấp Độ Tư Duy + Lồng Kỹ Năng Gemini Web Vào Prompt), Khóa Chặt AI Model/Combo, Sửa Lỗi Hệ Thống TTS & Kiểm Thử Toàn Diện



> **Thuộc kế hoạch:** [`plan.md`](./plan.md)  

> **Phase ID:** `phase-05`  

> **Trạng thái:** ⬜ Pending  

> **Phụ thuộc:** Phases 01, 02, 03, 04



---



## 1. Mục Tiêu (Objectives)

1. **Đồng bộ chính xác 4 Model thực tế & Cấp độ tư duy của Gemini Web (`GEMINI_WEB`):**

   - Cập nhật đúng 4 dòng model thực tế hiển thị trên giao diện Gemini Web hiện hành:

     - **`3.5 Flash-Lite`** — *"Câu trả lời nhanh nhất"*

     - **`3.8 Flash`** — *"Trợ giúp toàn diện"*

     - **`3.1 Pro`** — *"Suy luận nâng cao"*

     - **`4 Argon`** — *"Nhiệm vụ phức tạp"*

   - Hỗ trợ chọn **Cấp độ tư duy (`Cấp độ tư duy ▸`: `Tự động`, `Thấp`, `Trung bình`, `Cao`)** và bóc tách sạch khối suy luận nội bộ (`thought`) khỏi kết quả đầu ra.

2. **Tận dụng tính năng “Kỹ năng” (Tuỳ chỉnh $\rightarrow$ Kỹ năng) của Gemini Web lồng vào Prompt:**

   - Cho phép người dùng khai báo và kích hoạt **Kỹ năng (Skill) đã tạo trước trên Gemini Web** (ví dụ: Kỹ năng dịch truyện Tiên Hiệp/Cổ Phong, Kỹ năng biên tập văn học).

   - Tự động lồng chỉ thị kích hoạt Kỹ năng vào đầu prompt trong `GeminiWebHandler`, đồng thời rút gọn phần System Prompt trùng lặp của ứng dụng để giảm 70%–80% độ dài prompt hệ thống, tăng tốc độ phản hồi và tối đa hóa hiệu quả dịch thuật.

3. **Khóa chặt lựa chọn Model/Combo AI của người dùng:** Vá dứt điểm 5 lỗ hổng trong codebase khiến sau khi chọn Model lẻ hoặc Combo ở tác vụ AI, hệ thống lại tự ý nhảy về `"Free fallback"`.

4. **Khắc phục triệt để các lỗi chạy TTS (`ReadAloud` / `LocalTts` / `ZeroTTS`).**

5. **Thực thi toàn bộ Unit Tests & Nghiệm thu thực tế trên thiết bị.**



---



## 2. Thiết Kế Chi Tiết: 4 Model Thực Tế, Cấp Độ Tư Duy & Lồng Kỹ Năng Gemini Web Vào Prompt



### 2.1. Bảng Danh Mục 4 Model Thực Tế & Cấp Độ Tư Duy (`AiProviderCatalog.kt` & `GeminiWebHandler.kt`)



| Model ID (DrDucBook) | Tên Hiển Thị Trên UI | Mô Tả Gốc Trên Gemini Web | Cấp Độ Tư Duy (`GeminiWebThinkingLevel`) | Context / Output | Vai Trò & Khuyên Dùng |

|---|---|---|---|---|---|

| `gemini-web-3.5-flash-lite` | `3.5 Flash-Lite (Câu trả lời nhanh nhất · Free)` | Câu trả lời nhanh nhất | `NONE` (Trả lời trực tiếp) | `1,000,000` / `16,384` | Tốc độ nhanh nhất, mở cho cả gói Free & Pro |

| `gemini-web-3.8-flash` | `3.8 Flash (Trợ giúp toàn diện)` | Trợ giúp toàn diện | `FAST` (Tiêu chuẩn) | `1,000,000` / `32,768` | Dịch chương toàn diện, cân bằng tốc độ và chất lượng |

| `gemini-web-3.1-pro` | `3.1 Pro (Suy luận nâng cao)` | Suy luận nâng cao | `ADVANCED` (Suy luận nâng cao) | `1,000,000` / `64,000` | Dịch chương độ khó cao, phân tích ngữ cảnh & thực thể |

| `gemini-web-4-argon` | `4 Argon (Nhiệm vụ phức tạp · Tư duy: Tự động)` | Nhiệm vụ phức tạp | `AUTO` (Tự động điều tiết) | `1,000,000` / `64,000` | Model thế hệ 4 mạnh nhất cho các nhiệm vụ phức tạp |

| `gemini-web-4-argon-low` | `4 Argon (Cấp độ tư duy: Thấp)` | Nhiệm vụ phức tạp · Tư duy Thấp | `LOW` (Suy luận ngắn) | `1,000,000` / `64,000` | Dùng 4 Argon dịch chương nhanh, giảm thời gian chờ |

| `gemini-web-4-argon-medium` | `4 Argon (Cấp độ tư duy: Trung bình)` | Nhiệm vụ phức tạp · Tư duy Trung bình | `MEDIUM` (Cân bằng) | `1,000,000` / `64,000` | Dùng 4 Argon phân tích xưng hô, thuật ngữ & mạch truyện |

| `gemini-web-4-argon-high` | `4 Argon (Cấp độ tư duy: Cao)` | Nhiệm vụ phức tạp · Tư duy Cao | `HIGH` (Suy luận sâu tối đa) | `1,000,000` / `64,000` | Dùng 4 Argon cho AI Rewrite (Cổ phong), tóm tắt & đại cục |



#### Ánh xạ tương thích ngược tự động (Legacy Alias Mapping trong `GeminiWebHandler.kt`):

- `gemini-web-flash-lite` $\rightarrow$ ánh xạ tự động sang `gemini-web-3.5-flash-lite`.

- `gemini-web-default` / `gemini-web-flash` $\rightarrow$ ánh xạ tự động sang `gemini-web-3.8-flash`.

- `gemini-web-pro` $\rightarrow$ ánh xạ tự động sang `gemini-web-3.1-pro`.

- `gemini-web-thinking` $\rightarrow$ ánh xạ tự động sang `gemini-web-4-argon`.



### 2.2. Cơ Chế Tận Dụng “Kỹ Năng” Của Gemini Web (Tuỳ Chỉnh $\rightarrow$ Kỹ Năng) Lồng Vào Prompt

Khi người dùng đã tạo trước một **Kỹ năng (Skill)** trong mục **Tuỳ chỉnh $\rightarrow$ Kỹ năng** trên giao diện Gemini Web (ví dụ: đặt tên Kỹ năng là `"DrDucBook Dịch Truyện"` hoặc `"Dịch Giả Tiên Hiệp Cổ Phong"`):



1. **Cách khai báo Kỹ năng trong `DrDucBook` (Hỗ trợ đồng thời 2 cách):**

   - **Cách 1 — Qua trường cấu hình `geminiWebSkillName` trong `AiRuntimeOptions` / Màn hình Cấu hình Prompt:**

     - Thêm trường `geminiWebSkillName: String? = null` và `geminiWebSkillCompactMode: Boolean = true` vào `AiRuntimeOptions`.

     - Trên `TranslationConfigScreen` / `TranslationPromptConfigScreen` (và `PerBookTranslationPromptSection`), thêm ô nhập: **"Tên Kỹ năng Gemini Web (Tuỳ chỉnh -> Kỹ năng)"** cùng nút **"Sao chép mẫu Kỹ năng chuẩn"** (để người dùng dán vào Gemini Web khi tạo Kỹ năng).

   - **Cách 2 — Qua Tag trực tiếp trong ô Prompt tùy chỉnh (Per-Book hoặc Global Prompt):**

     - Nếu người dùng viết ở dòng đầu của Prompt tùy chỉnh cú pháp: `[SKILL: Tên Kỹ Năng]` hoặc `@Skill("Tên Kỹ Năng")`, hệ thống tự động trích xuất tên Kỹ năng đó.

2. **Cách `GeminiWebHandler.buildFullPrompt()` lồng Kỹ năng & Tối ưu hóa Prompt:**

   - Khi có `skillName` được cấu hình:

     - **Bước 1 (Skill Activation Header):** Đặt khối kích hoạt Kỹ năng ngay đầu prompt gửi tới Gemini Web:

       ```xml

       <gemini_skill_activation>

       [Kích hoạt Kỹ năng: "{skillName}"]

       Hãy sử dụng Kỹ năng "{skillName}" đã được thiết lập của tôi và tuân thủ nghiêm ngặt toàn bộ quy tắc dịch thuật, văn phong, đại từ xưng hô cùng định dạng đầu ra trong Kỹ năng đó.

       </gemini_skill_activation>

       ```

     - **Bước 2 (Compact Prompt Optimization):** Nếu `geminiWebSkillCompactMode == true`, hệ thống tự động **bỏ qua phần văn bản hướng dẫn dịch thuật chung dài dòng của `DEFAULT_PROMPT` / `Hard Rules 1..14`** (do đã nằm sẵn trong Kỹ năng trên Gemini Web của người dùng), và chỉ đính kèm:

       1. Từ điển khóa của chương (`<locked_dictionary>`).

       2. Ngữ cảnh nhân vật & cặp đại từ xưng hô `SELF/OTHER` của chương (`<relationship_context>`, `<character_pronouns>`).

       3. Ràng buộc định dạng JSON trả về (`{"translations":[{"i":0,"t":"..."}]}`) và danh sách đoạn văn gốc (`<segments>`).

     - **Lợi ích:** Giảm 70%–80% token hệ thống lặp lại ở mỗi request, giúp `4 Argon`, `3.1 Pro`, `3.8 Flash` và `3.5 Flash-Lite` phản hồi nhanh hơn rõ rệt và bám sát 100% phong cách trong Kỹ năng của người dùng!



### 2.3. Các Task Thực Thi Chi Tiết Cho Gemini Web

- [ ] **Task 05.1 (`AiProviderCatalog.kt`, `AiModels.kt`, `GeminiWebHandler.kt`):**

  - Cập nhật danh sách model của `gemini_web` trong `AiProviderCatalog.kt` và `GeminiWebHandler.fetchModels()` theo đúng bảng 7 mục ở Mục 2.1 (`3.5 Flash-Lite`, `3.8 Flash`, `3.1 Pro`, `4 Argon` + 3 mức tư duy `Thấp`, `Trung bình`, `Cao`).

  - Trong `GeminiWebHandler.kt`: xây dựng `resolveGeminiWebModelSpec(modelId, runtimeOptions)` để gửi đúng header `x-goog-ext-525001261-jspb` và tham số cấp độ tư duy trong RPC `StreamGenerate`.

  - Trong `GeminiStreamAccumulator`: tách riêng phần suy luận nội bộ (`thought`) sang `AiStreamEvent.ReasoningDelta`, chỉ phát phần văn bản trả lời chính thức (`content`) vào `AiStreamEvent.TextDelta`.

  - Giữ cơ chế tự động hạ cấp mượt trong phiên nếu tài khoản Free gọi vào model gói Pro (`4 Argon` $\rightarrow$ `3.1 Pro` $\rightarrow$ `3.8 Flash` $\rightarrow$ `3.5 Flash-Lite`).

- [ ] **Task 05.2 (`GeminiWebHandler.kt`, `AiTranslationRefinePipeline.kt`, `TranslationPromptConfigScreen.kt`):**

  - Bổ sung `geminiWebSkillName: String? = null` và `geminiWebSkillCompactMode: Boolean = true` vào `AiRuntimeOptions`.

  - Hỗ trợ trích xuất tag `[SKILL: ...]` / `@Skill(...)` và lồng khối `<gemini_skill_activation>` vào đầu `GeminiWebHandler.buildFullPrompt()`.

  - Thêm ô nhập tên Kỹ năng Gemini Web và nút **"Sao chép mẫu Kỹ năng chuẩn"** trên giao diện cấu hình dịch thuật.



---



## 3. Khóa Chặt Lựa Chọn Model/Combo AI — Chống Tự Ý Đổi Về Free Fallback



### 3.1. Tóm tắt 5 lỗ hổng trong Codebase cần vá

1. **Lỗ hổng 1 (`ensureGeneratedPresetRouteBinding` & `RepairAiRouteBindingsUseCase`):** Chỉ kiểm tra `existing.runtimeOptions.routeProfileId.isNullOrBlank()`, nên khi người dùng chọn **Model đơn lẻ** (`modelProfileId` có giá trị nhưng `routeProfileId == null`), hàm tưởng là preset chưa cấu hình và **ghi đè cả `modelProfileId` lẫn `routeProfileId` về Free Fallback (`route_free_*`)**.

2. **Lỗ hổng 2 (`AiRouterRepository.resolveRoute`):** Khi `request.routeProfileId == null` (chế độ chọn Model đơn lẻ), hàm lại gọi `dao.getActiveRoute(taskType)` (lấy `route_free_*` đang có `isDefault = 1` trong bảng `ai_route_profiles`) thay vì chạy trực tiếp `preset.modelProfileId` mà người dùng đã chọn.

3. **Lỗ hổng 3 (`AiComboModelPickerSheet`):** Khi chọn một Combo từ màn hình cấu hình Dịch/Prompt, code chỉ lưu `routeProfileId` vào `AiTaskPreset` mà không cập nhật `isDefault = true` cho route đó trong bảng `ai_route_profiles`.

4. **Lỗ hổng 4 (`ensureGeneratedFreeRoutes`):** Khi rebuild danh sách model của Free Route, cần đảm bảo không bao giờ ghi đè `makeDefault = true` nếu người dùng đã có route default khác.

5. **Lỗ hổng 5 (Không xóa `routeProfileId` cũ khi chuyển từ Combo sang Model đơn lẻ):** Khi preset trước đó đang gắn `routeProfileId = "route_free_translate_chapter"`, nếu người dùng chuyển sang chọn 1 Model đơn lẻ mà không set `routeProfileId = null`, hệ thống vẫn ưu tiên đọc `routeProfileId` cũ.



### 3.2. Các Task Thực Thi Khóa Chặt Lựa Chọn Model/Combo

- [ ] **Task 05.3 (`AiRouterViewModel.kt` & `RepairAiRouteBindingsUseCase.kt`):**

  - Trong `ensureGeneratedPresetRouteBinding()`:

    ```kotlin

    val existing = profileGateway.getTaskPreset(taskType)

    if (existing != null && (existing.modelProfileId.isNotBlank() || !existing.runtimeOptions.routeProfileId.isNullOrBlank())) {

        return

    }

    ```

  - Trong `RepairAiRouteBindingsUseCase.kt`: Không bao giờ tự động sửa `routeProfileId` hoặc `modelProfileId` của một preset đã tồn tại về `route_free_*`.

- [ ] **Task 05.4 (`AiRouterRepository.kt`):**

  - Trong `resolveRoute()` / `execute()`: Nếu `request.routeProfileId.isNullOrBlank()` nhưng `request.modelProfileId.isNotBlank()` (hoặc `preset.modelProfileId.isNotBlank()` và `preset.runtimeOptions.routeProfileId == null`), thực thi trực tiếp model `modelProfileId` đó (với toàn bộ pool credentials của provider tương ứng), **không gọi `dao.getActiveRoute(taskType)`**.

- [ ] **Task 05.5 (`TranslationConfigScreen.kt`, `TranslationPromptConfigViewModel.kt`, `AiConfigViewModel.kt`):**

  - Khi người dùng chọn **Model đơn lẻ** trong `AiComboModelPickerSheet`: cập nhật `modelProfileId = selectedModel.id` và đặt `runtimeOptions = runtimeOptions.copy(routeProfileId = null)`.

  - Khi người dùng chọn **Combo (Route)** trong `AiComboModelPickerSheet`: cập nhật `runtimeOptions = runtimeOptions.copy(routeProfileId = selectedRoute.id)` **đồng thời** gọi `aiRouterRepository.setDefaultRoute(taskType, selectedRoute.id)`.



---



## 4. Khắc Phục Toàn Diện Lỗi Chạy Hệ Thống TTS (`ReadAloud` / `LocalTts` / `ZeroTTS`)



- [ ] **Task 05.6 — Sửa định tuyến Service & Chặn Race Condition `onDestroy` (`ReadAloud.kt` & `BaseReadAloudService.kt`):**

  - Trong `ReadAloud.upReadAloudClass()`:

    - Nếu `AppConfig.ttsFollowSys == true` $\rightarrow$ trả về `TTSReadAloudService::class.java`.

    - Nếu `ttsEngine` là `LocalTtsEngineRef` (hoặc khớp với ID mô hình trong `LocalTtsModelRegistry`) $\rightarrow$ trả về `LocalTtsReadAloudService::class.java`.

    - Nếu `ttsEngine.toLongOrNull() != null` và tồn tại trong `httpTTSDao` $\rightarrow$ trả về `HttpReadAloudService::class.java`.

    - Mặc định fallback an toàn về `TTSReadAloudService::class.java`.

  - Trong `BaseReadAloudService.kt`:

    - Lưu tham chiếu `@Volatile var activeServiceInstance: BaseReadAloudService? = null` khi `onCreate()`.

    - Trong `onDestroy()`, chỉ gán `isRun = false` và phát `EventBus(EventBus.ALOUD_STATE, Status.STOP)` khi `activeServiceInstance === this`. Điều này chấm dứt hoàn toàn lỗi khi chuyển đổi qua lại giữa System TTS và ZeroTTS làm service cũ hủy muộn tắt luôn service mới.

- [ ] **Task 05.7 — Bắt buộc `Dispatchers.Main` cho `ExoPlayer` & Bỏ qua đoạn văn chỉ có dấu câu (`LocalTtsReadAloudService.kt` & `ZeroTtsOnnxEngine.kt`):**

  - Trong `LocalTtsReadAloudService.kt` và `HttpReadAloudService.kt`: bọc mọi thao tác lên `exoPlayer` (`stop()`, `clearMediaItems()`, `setMediaItem()`, `addMediaItem()`, `prepare()`, `play()`) trong `withContext(Dispatchers.Main.immediate)`.

  - Trước khi đưa đoạn văn (`paragraph`) vào `LocalTtsSynthesis.synthesize()`: làm sạch các marker định dạng ẩn và kiểm tra `cleanParagraph.any { it.isLetterOrDigit() }`. Nếu đoạn văn chỉ toàn dấu câu (`***`, `---`, `...`, `===`) hoặc khoảng trắng, tự động gọi `nextParagraph()` trên Main thread thay vì dừng dịch vụ.

  - Trong `ZeroTtsOnnxEngine.kt`: tự động dò tìm tệp `.onnx` (ưu tiên bản INT8, fallback bản FP32) và kiểm tra tệp giọng đọc `.npy` trong thư mục `voices/`. Nếu `voiceId` được chọn chưa có tệp `.npy` trên đĩa, tự động fallback về tệp `.npy` đầu tiên sẵn có trong thư mục model.



---



## 5. Quy Trình Kiểm Thử Tự Động & Nghiệm Thu (Verification & Build Gate)



### 5.1. Các Lệnh Kiểm Thử Bắt Buộc

```powershell

# 1. Kiểm tra biên dịch Kotlin toàn dự án

.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain



# 2. Chạy Unit Tests cho Phase 01 (QT Gốc + Mapping) & Phase 02 (Mojibake)

.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.QuickTranslationRepositoryTest" --tests "io.legado.app.ui.quickdict.QuickDictionarySelectionResolverTest" --tests "io.legado.app.domain.model.VietnameseTranslationPostProcessorTest" --no-daemon --console=plain



# 3. Chạy Unit Tests cho Phase 03 (Blocks) & Phase 04 (Source Health)

.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.BookContentProcessEngineTest" --tests "io.legado.app.ui.book.source.health.SourceHealthStateTest" --tests "io.legado.app.domain.sourcehealth.SourceCheckClassifierTest" --tests "io.legado.app.data.repository.sourcehealth.SourceCheckRepositoryTest" --no-daemon --console=plain



# 4. Chạy Unit Tests cho Phase 05 (Gemini Web, AI Route Lock & Session Manager)

.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.ai.WebSessionManagerTest" --tests "io.legado.app.ui.ai.router.AiRouterAutoInstallPolicyTest" --tests "io.legado.app.domain.usecase.RepairAiRouteBindingsUseCaseTest" --no-daemon --console=plain



```



### 5.2. Checklist Nghiệm Thu Cuối Cùng (Final Acceptance Checklist)



* [ ] **Phase 01:** Dịch chương QT < 50ms, chuẩn `VietPhrase` + `LuatNhan`, bôi đen từ bất kỳ lấy đúng 100% chữ Hán gốc.

* [ ] **Phase 02:** Không còn dấu `?` lỗi giữa tên riêng hay ký tự Mojibake; 100% nhãn trên `SourceHealthScreen` hiển thị tiếng Việt.

* [ ] **Phase 03:** Toàn bộ khối tải xuống thu gọn `54.dp`–`72.dp`, hiển thị 6–8 mục trên 1 màn hình.

* [ ] **Phase 04:** Bộ đếm `SourceHealthSummaryUi` khớp 100% với bộ lọc; xóa nguồn lỗi xóa sạch cả `Book`/`RSS`/`VBook` và lịch sử check.

* [ ] **Phase 05.A (Gemini Web):** Chọn được đủ 4 model thực tế (`3.5 Flash-Lite`, `3.8 Flash`, `3.1 Pro`, `4 Argon`) + `Cấp độ tư duy` (`Tự động`, `Thấp`, `Trung bình`, `Cao`), lồng thành công **Kỹ năng Gemini Web** vào prompt và tách sạch khối `thought`.

* [ ] **Phase 05.B (AI Model/Combo Lock):** Chọn Model lẻ hoặc Combo xong giữ nguyên bền vững 100%, không bao giờ tự nhảy về `"Free fallback"`.

* [ ] **Phase 05.C (TTS):** Đọc thành tiếng mượt mà trên cả System TTS và ZeroTTS, tự động vượt qua dòng `***` và đổi giọng không bị dừng/crash.



```



---



# 📌 PHỤ LỤC: MẪU “KỸ NĂNG” (SKILL TEMPLATE) ĐỂ TẠO TRƯỚC TRÊN GEMINI WEB (MỤC `TUỲ CHỈNH` $\rightarrow$ `KỸ NĂNG` $\rightarrow$ `TẠO`)[cite: 3]



Bạn có thể copy trực tiếp nội dung dưới đây để tạo sẵn **2 Kỹ năng cốt lõi** trên tài khoản Gemini Web của mình[cite: 3]:



### 1. Kỹ năng 1: Dịch Thuật Chương Truyện Chuẩn Văn Học

* **Tên Kỹ năng (đặt trên Gemini Web):** `DrDucBook Dịch Truyện`

* **Nội dung hướng dẫn Kỹ năng:**

```text

Bạn là Dịch Giả Tiểu Thuyết Trung-Việt cao cấp của hệ thống DrDucBook. Nhiệm vụ của bạn là dịch các phân đoạn truyện tiếng Trung sang tiếng Việt giàu nhạc điệu, đúng văn phong thể loại (Cổ phong / Tiên hiệp / Huyền huyễn / Đô thị / Kỳ huyễn phương Tây) và tuân thủ tuyệt đối tính nhất quán của bộ truyện.



QUY TẮC BẮT BUỘC:

1. TỪ ĐIỂN & THỰC THỂ KHÓA (<locked_dictionary>):

   - Bắt buộc sử dụng chính xác 100% cách dịch tên nhân vật, địa danh, cảnh giới, chiêu thức đã khai báo trong <locked_dictionary>.

   - Đối với tên nước ngoài phiên âm Hán tự (Nhật/Hàn/Phương Tây), chuyển về tên Romaji/Latin chuẩn xác, tuyệt đối không dịch Hán-Việt từng chữ rời rạc.

2. ĐẠI TỪ XƯNG HÔ (<relationship_context> & <character_pronouns>):

   - Chuyển đổi toàn bộ đại từ "我 / 你 / 他 / 她 / 咱们" sang cặp đại từ tiếng Việt tương ứng với quan hệ nhân vật (SELF / OTHER: ta - ngươi, huynh - muội, đệ - tỷ, bản tọa, tại hạ, lão phu, hắn, nàng, y, anh - em, tôi - cậu...).

   - Không bao giờ dịch máy "của tôi / của bạn" trong bối cảnh cổ trang/tiên hiệp hoặc đối thoại thân mật.

3. NGỮ PHÁP & VĂN PHONG THUẦN VIỆT:

   - Xóa bỏ cấu trúc calque tiếng Trung: không lạm dụng từ "của" (的) trước động từ/tính từ; đảo định ngữ về đúng trật tự tiếng Việt; xử lý mượt cấu trúc "把" (đem/lấy) và "被" (bị/được).

   - Giữ nguyên nhịp điệu câu văn, sắc thái biểu cảm và thuật ngữ tu luyện/hệ thống RPG.

4. BẢO TOÀN ĐOẠN VĂN & ĐỊNH DẠNG ĐẦU RA:

   - Dịch đủ 100% số đoạn theo đúng chỉ số "i", không gộp đoạn, không cắt bớt nội dung, không để sót bất kỳ ký tự chữ Hán (CJK) nào trong bản dịch.

   - Khi prompt yêu cầu định dạng JSON {"translations":[{"i":0,"t":"..."}]}, chỉ trả về duy nhất chuỗi JSON hợp lệ, không thêm lời dẫn hay giải thích bên ngoài.



```



### 2. Kỹ năng 2: Biên Tập & Viết Lại Chương Truyện (AI Rewrite)



* **Tên Kỹ năng (đặt trên Gemini Web):** `DrDucBook Biên Tập Cổ Phong`

* **Nội dung hướng dẫn Kỹ năng:**



```text

Bạn là Biên Tập Viên Văn Học Việt Nam chuyên hiệu đính và viết lại (Rewrite) các chương tiểu thuyết dịch thô/convert sang văn phong tiểu thuyết xuất bản mượt mà, trau chuốt và giàu cảm xúc.



QUY TẮC BẮT BUỘC:

1. Giữ nguyên 100% cốt truyện, tình tiết, tên riêng nhân vật, chiêu thức, cảnh giới và cặp đại từ xưng hô hiện có.

2. Tái cấu trúc các câu văn dịch thô/convert bị ngược trật tự từ thành câu tiếng Việt tự nhiên, uyển chuyển, giàu hình ảnh.

3. Bảo toàn số lượng đoạn văn và đối thoại; chỉ xuất trực tiếp nội dung chương đã biên tập hoàn chỉnh, không thêm lời bình luận ở đầu hoặc cuối.



```



---



Bạn có muốn tôi bắt đầu viết code thực thi ngay cho từng Phase (từ **Phase 01** đến **Phase 05**) theo đúng bản kế hoạch chi tiết này không?
Phân tích nội dung trên và kiểm tra hoàn thiện hệ thống plan
