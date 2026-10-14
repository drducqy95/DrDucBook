# Phase 03: Eliminate Pronoun Mutation & Guarantee 1-1 Text Mapping

Status: ⬜ PENDING
Dependencies: Phase 01, Phase 02

## Objective
Xóa bỏ hoàn toàn cơ chế biến đổi xưng hô hiện đại (`bạn`, `tôi`), trả lại đại từ nguyên bản từ từ điển (`ta`, `ngươi`, `hắn`, `nàng`). Đảm bảo mỗi token nguồn tiếng Trung ánh xạ trực tiếp và chuẩn xác 1-1 sang token tiếng Việt mà không bị méo mó bởi regex hậu xử lý.

## Tasks
1. [ ] Xóa bỏ toàn bộ các hàm biến đổi xưng hô:
   - `applyPronounProfile()`
   - `applyModernPronounRules()`
   - `applyAncientPronounRules()`
   - `applyModernKinshipRules()`
   - `normalizeNarratorThirdPerson()`
   - `PronounContextTracker`
2. [ ] Đảm bảo đại từ lấy trực tiếp từ `Pronouns.txt` / `VietPhrase.txt`:
   - `我` $\rightarrow$ `ta` (100% tự nhiên, không bao giờ bị đổi thành `tôi`).
   - `你` $\rightarrow$ `ngươi` (100% tự nhiên, không bao giờ bị đổi thành `bạn`).
   - `他` $\rightarrow$ `hắn`.
   - `她` $\rightarrow$ `nàng`.
   - `他们` $\rightarrow$ `bọn họ` / `họ`.
3. [ ] Đảm bảo tính toàn vẹn của Text Mapping (1-1 Token Mapping):
   - Loại bỏ các bước `postProcessFullText` hoặc regex làm thay đổi độ dài chuỗi mà không cập nhật span.
   - Mỗi segment được trả về trực tiếp với:
     * `source`: Đoạn ký tự tiếng Trung gốc.
     * `translated`: Đoạn từ dịch tiếng Việt tương ứng.
     * `sourceRange`: `[startOffset, endOffset]`.
     * `targetRange`: `[transStartOffset, transEndOffset]`.
   - Tính năng chạm/bôi đen trên màn hình Reader sẽ ánh xạ chính xác 100% vào từ điển mà không bị lệch vị trí.

## Files to Modify
- `app/src/main/java/io/legado/app/data/repository/QuickTranslationRepository.kt`
- `app/src/main/java/io/legado/app/domain/model/MappedTranslation.kt`

## Test Criteria
- 0 lần xuất hiện các từ `tôi`, `bạn` trong các đoạn văn kiếm hiệp/tiên hiệp.
- Unit test mapping text: Bôi đen bất kỳ từ nào trên văn bản dịch đều truy ngược chính xác 100% cụm từ tiếng Trung tương ứng.
