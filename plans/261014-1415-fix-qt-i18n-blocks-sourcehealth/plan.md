# Plan: Khôi Phục QT Gốc (Từ Điển + Luật Nhân), Sửa Lỗi Tiếng Việt Mojibake/i18n, Thu Gọn Khối Tải Xuống, Chuẩn Hóa Source Health, Sửa Lỗi TTS, Khóa Chặt AI Model/Combo & Tối Ưu Hóa Gemini Web (4 Model Thực Tế + Cấp Độ Tư Duy + Lồng Kỹ Năng Gemini Web Vào Prompt) — Milestone P77



> **Mã kế hoạch:** `PLN-261014-1415`  

> **Đường dẫn:** `plans/261014-1415-fix-qt-i18n-blocks-sourcehealth/plan.md`  

> **Milestone:** `P77`  

> **Ngày tạo:** 2026-10-14T14:15:00+07:00  

> **Ngày cập nhật:** 2026-10-14T16:15:00+07:00  

> **Trạng thái:** ✅ Hoàn thành (Completed)  

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

| **01** | [`phase-01-qt-speed-and-mapping.md`](./phase-01-qt-speed-and-mapping.md) | Khôi phục thuật toán QT Gốc (FMM + `LuatNhan` + Từ điển), bỏ Beam Search/Heuristic gây nhiễu, sửa Mapping 1:1 | `QuickTranslationRepository.kt`, `MappedDisplayText.kt`, `QuickDictionarySelectionResolver.kt`, `TranslateDynamicUiTextUseCase.kt` | ✅ Done |
| **02** | [`phase-02-fix-vietnamese-mojibake.md`](./phase-02-fix-vietnamese-mojibake.md) | Khử lỗi tiếng Việt Mojibake, chuẩn hóa NFC, sửa lỗi charset và hoàn thiện i18n `strings.xml` / `strings-vi.xml` | `VietnameseTranslationPostProcessor.kt`, `SourceHealthScreen.kt`, `strings.xml`, `strings-vi.xml` | ✅ Done |
| **03** | [`phase-03-compact-download-blocks.md`](./phase-03-compact-download-blocks.md) | Thu gọn giao diện các khối tải xuống (`CompactDownloadBlock` `54.dp`–`72.dp`) & thu gọn khối nội dung rỗng | `DownloadCenterScreen.kt`, `BookCacheManageScreen.kt`, `DownloadCacheConfigScreen.kt`, `TranslationConfigScreen.kt`, `MlKitModelsScreen.kt`, `ReadAloudConfigSheet.kt`, `BookContentProcessEngine.kt` | ✅ Done |
| **04** | [`phase-04-source-health-accuracy.md`](./phase-04-source-health-accuracy.md) | Nâng cao độ chính xác Source Health, xóa nguồn đa loại (`Book`/`RSS`/`VBook`) & đồng bộ bộ lọc/bộ đếm | `SourceCheckClassifier.kt`, `SourceHealthProbeRepositories.kt`, `BookSourceHealthRepository.kt`, `SourceCheckRepository.kt`, `SourceHealthViewModel.kt`, `SourceHealthScreen.kt` | ✅ Done |
| **05** | [`phase-05-verification-testing.md`](./phase-05-verification-testing.md) | (1) Gemini Web `3.5 Flash-Lite` / `3.8 Flash` / `3.1 Pro` / `4 Argon` + Cấp độ tư duy + Lồng Kỹ năng Gemini Web vào Prompt; (2) Khóa chặt AI Model/Combo; (3) Sửa lỗi TTS; (4) Kiểm thử toàn diện | `GeminiWebHandler.kt`, `AiProviderCatalog.kt`, `AiModels.kt`, `AiTranslationRefinePipeline.kt`, `TranslationConfigScreen.kt`, `TranslationPromptConfigScreen.kt`, `AiRouterViewModel.kt`, `AiRouterRepository.kt`, `RepairAiRouteBindingsUseCase.kt`, `ReadAloud.kt`, `BaseReadAloudService.kt`, `LocalTtsReadAloudService.kt`, `ZeroTtsOnnxEngine.kt` | ✅ Done |

---

## 3. Tiêu Chí Nghiệm Thu Tổng Thể (Master Acceptance Criteria)
- [x] **Phase 01 (QT Gốc & Mapping):** Tốc độ dịch chương < 50ms, chuẩn `VietPhrase` + `LuatNhan` (đạt 80–90% chất lượng qua từ điển), bôi đen từ bất kỳ trên màn hình đọc trích xuất đúng 100% chữ Hán gốc.
- [x] **Phase 02 (Mojibake & i18n):** Không còn dấu `?` lỗi giữa tên riêng hay ký tự lạ; 100% nhãn trên `SourceHealthScreen` và toàn app hiển thị tiếng Việt chuẩn.
- [x] **Phase 03 (Compact Download Blocks):** Các khối tải xuống trong `DownloadCenterScreen`, `BookCacheManageScreen`, `TranslationConfigScreen`, `ReadAloudConfigSheet` thu gọn chuẩn `54.dp`–`72.dp`.
- [x] **Phase 04 (Source Health):** Bộ đếm khớp 100% với bộ lọc; xóa nguồn lỗi xóa sạch cả `Book`/`RSS`/`VBook` và lịch sử kiểm tra.
- [x] **Phase 05.A (Gemini Web Models, Thinking Levels & Native Skills):**
  - Chọn và chạy đúng 4 model thực tế (`3.5 Flash-Lite`, `3.8 Flash`, `3.1 Pro`, `4 Argon`) kèm các mức **Cấp độ tư duy** (`Tự động`, `Thấp`, `Trung bình`, `Cao`).
  - Lồng thành công **Kỹ năng (Skill) đã tạo trên Gemini Web** vào prompt (kèm chế độ rút gọn System Prompt thừa), tách sạch khối `thought` khỏi văn bản đầu ra.
- [x] **Phase 05.B (AI Model/Combo Lock):** Sau khi chọn Model lẻ hoặc Combo ở bất kỳ tác vụ AI nào, tuyệt đối giữ nguyên lựa chọn đó, không bao giờ tự ý nhảy về `"Free fallback"`.
- [x] **Phase 05.C (Hệ Thống TTS):** Khắc phục hoàn toàn race condition `onDestroy()` khi chuyển đổi giữa System TTS và ZeroTTS; bọc ExoPlayer trong `Dispatchers.Main.immediate`, bỏ qua các đoạn văn chỉ toàn dấu câu `***`, `---`, `...` và tự động fallback tệp embedding `.npy` có sẵn.

