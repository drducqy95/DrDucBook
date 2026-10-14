# Phase 02: Classic QT Algorithm Restoration (Forward Maximum Matching - FMM)

Status: ⬜ PENDING
Dependencies: Phase 01

## Objective
Khôi phục thuật toán QuickTranslator gốc tuyến tính: Greedy Longest Match (Forward Maximum Matching - FMM). Loại bỏ hoàn toàn Beam Search, Template Matches, Grammar Rules và các thuật toán chấm điểm phức tạp.

## Tasks
1. [ ] Triển khai thuật toán FMM tuyến tính:
   - Duyệt văn bản nguồn bằng con trỏ vị trí `offset`.
   - Tại vị trí `offset`:
     * Thử tìm từ dài nhất trong `Project Terms` (từ độ dài lớn nhất giảm dần).
     * Thử tìm từ dài nhất trong `Names` / `VietPhrase` / `Pronouns` / `LuatNhan`.
     * Nếu tìm thấy: emit `TranslationSegment(source, translated, startOffset, endOffset)` và nhảy `offset += match.length`.
     * Nếu không tìm thấy bất kỳ từ ghép nào:
       - Nếu là ký tự CJK: tra cứu phiên âm Hán Việt đơn tự từ `ChinesePhienAmWords.txt` (hoặc unihan fallback).
       - Nếu là ký tự Latin / số / dấu câu: giữ nguyên ký tự gốc (passthrough).
       - Nhảy `offset += 1`.
2. [ ] Loại bỏ hoàn toàn:
   - `beamTranslationPlan()` và `bestTranslationPlan()`.
   - `TemplateMatch`, `StructuredMatch`, `GrammarMatch`.
   - `lexicalFragmentationPenalty()` và các logic tính điểm heuristic.
3. [ ] Tối ưu cấu trúc tra cứu từ điển:
   - Sử dụng Trie hoặc Fast Prefix / Hash Map Lookup đạt độ trễ < 30ms cho 3,000 ký tự.

## Files to Modify
- `app/src/main/java/io/legado/app/data/repository/QuickTranslationRepository.kt`

## Test Criteria
- Thuật toán hoàn toàn là Forward Maximum Matching tuyến tính $O(N)$.
- Tốc độ dịch cho 1 chương 3,000 từ trên JVM đạt $\le 30\text{ ms}$.
- Không phát sinh garbage allocation / OutOfMemory.
