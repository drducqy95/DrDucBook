# Plan: Story Memory, Wiki & Translation Cache System Overhaul (P45)

Created: 2026-10-01T15:25:00+07:00
Updated: 2026-10-01T15:54:00+07:00
Status: ✅ Completed (100%)
Corpus: `drducqy95/DrDucBook` (Legado Material 3 fork)
Device target: Huawei Nova HBN-LX9 / Android 14

## Overview
Giải quyết triệt để 7 bài toán cốt lõi về **Cache bản dịch (QT, NMT, AI)**, **Bộ nhớ dịch (Translation Memory / Story Memory)** và **Story Wiki** trong app debug (đã kiểm chứng trực tiếp trên dữ liệu thật của sách *Chủ Thần Đại Đạo*):
1. **Chất lượng output kém ở các chương dịch trước:** Do các chương đầu dịch khi Story Memory còn rỗng, bản dịch cũ bị kẹt trong cache tĩnh `.nb` mà không có cơ chế retrofit/tự động tái tinh chỉnh khi Memory đã giàu lên.
2. **Bộ nhớ dịch chưa liên thông đúng giữa QT, NMT, AI:** Luồng trích xuất hiện tại là 1 chiều từ AI Refiner; QT & NMT chỉ nhận dictionary dạng thô, không nhận được mạng lưới quan hệ xưng hô; `memoryDictionary` getter bỏ sót World Building terms; chỉnh sửa ở Story Memory không tự đồng bộ sang QuickDictionary và ngược lại.
3. **Story Wiki phân cấp theo từng truyện (Book-First Master-Detail):** Tách màn hình Story Wiki thành danh sách các truyện có Wiki -> Bấm vào truyện nào mới mở toàn bộ các Tab (Nhân vật, Thế giới, Dòng thời gian, Đồ thị quan hệ) của truyện đó.
4. **Hệ thống dữ liệu Wiki nhân vật chuyên sâu (Chuẩn Thư Viện Anime - Hàn Lập):** Mở rộng Schema thực thể với Cảnh giới, Môn phái, Pháp bảo, Công pháp, Thần thông, Tính cách, Xuất thân, Niên biểu và liên kết 2 chiều với World Building. Lưu trữ qua field `metadata` (JSON stringified) để backward-compat.
5. **Tối ưu tốc độ thêm từ điển QT & làm rõ đích đến trong Bộ nhớ dịch:** Loại bỏ vòng lặp sliding window quét quadratic đệ quy toàn bộ chương trong `QuickDictionarySelectionResolver`; bổ sung `StoryMemoryCategory` enum + selector phân loại rõ ràng (Nhân vật, Môn phái, Pháp bảo, Công pháp, Thuật ngữ...) khi tick lưu vào bộ nhớ dịch.
6. **Khắc phục lỗi logic bảng Relationship:** Sửa đổi nhãn và luồng dữ liệu từ "Từ nguồn - Bản dịch" sai lệch thành "Nhân vật/Thực thể 1" - "Nhân vật/Thực thể 2" - "Mối quan hệ" - "Mô tả", ẩn các control dịch thuật không liên quan. Fix `RELATIONSHIP -> Unit` rỗng.
7. **Tính năng xây dựng Bộ nhớ dịch chuyên dụng (AI riêng biệt & Thủ công):** Tạo `AiTaskType.EXTRACT_STORY_MEMORY` chuyên biệt (không phụ thuộc tóm tắt chương) để phân tích bộ nhớ dịch theo khoảng chương hoặc văn bản dán vào; bổ sung công cụ quản lý/import/export và tính năng retrofit/làm mới bản dịch các chương cũ (bảo toàn user-locked segments).

**Bổ sung (Refined):**
8. **Tối ưu hiển thị UI form QT:** Chuyển FilterChip → ExposedDropdownMenuBox cho Type/Scope/Provider/MemoryCategory, tiết kiệm ~250-300dp scroll.

---

## Phases Roadmap

| Phase | Tên Phase | Trọng tâm | Trạng thái | Tiến độ |
|---|---|---|---|---|
| **01** | `phase-01-relationship-logic-and-labels` | Fix Relationship editor (`→ Unit` rỗng), nhãn, field mapping & list display | ✅ Complete | 100% |
| **02** | `phase-02-quick-dict-performance-optimization` | Fix `expandedForFallback()` + `StoryMemoryCategory` enum + selector | ✅ Complete | 100% |
| **02b** | `phase-02b-quick-dict-ui-compaction` | **[MỚI]** FilterChip → Dropdown, thu gọn layout ~250-300dp | ✅ Complete | 100% |
| **03** | `phase-03-cross-provider-memory-synchronization` | Fix `memoryDictionary` getter + Bidirectional sync + SyncUseCase | ✅ Complete | 100% |
| **04** | `phase-04-story-wiki-hierarchical-book-first` | Book-first Wiki, BackHandler navigation, deep-link | ✅ Complete | 100% |
| **05** | `phase-05-rich-character-dossier-system` | Character dossier, `metadata` field, CharacterProfileDetails | ✅ Complete | 100% |
| **06** | `phase-06-ai-manual-memory-builder-and-cache-retrofit` | AI Memory Extractor + Retrofit cache (preserve locked chunks) | ✅ Complete | 100% |
| **07** | `phase-07-verification-and-testing` | 7 test suites, build debug APK, deploy Huawei kiểm chứng | ✅ Complete | 100% |

---

## Gap Analysis Summary (Đã khắc phục)
- ✅ GAP 1: Phase 01 — `RELATIONSHIP -> Unit` rỗng + field mapping verification
- ✅ GAP 2: Phase 02 — `expandedForFallback()` fix code cụ thể
- ✅ GAP 3: Phase 02 — `memoryCategory` + `memoryDescription` + `StoryMemoryCategory` enum
- ✅ GAP 4: Phase 03 — `memoryDictionary` getter bỏ sót World Building
- ✅ GAP 5: Phase 03 — `SyncStoryMemoryWithQuickDictionaryUseCase.kt` + DI registration
- ✅ GAP 6: Phase 04 — BackHandler navigation approach
- ✅ GAP 7: Phase 05 — `metadata` field storage strategy
- ✅ GAP 8: Phase 07 — UI tests + backward compat tests + locked chunks test
- ✅ Phase 02b CREATED — UI Compaction (Dropdown)

---

## Quick Commands
- Bắt đầu duyệt plan và triển khai: `/code phase-01`
- Kiểm tra tiến độ: `/next`
- Lưu context dài hạn: `/save-brain`
