# Phase 01: QT Trie Dynamic Term Matcher Gateway

Status: ⬜ Pending
Dependencies: None

## Objective
Mở rộng `QuickTranslationGateway` và `QuickTranslationRepository` để cung cấp hàm truy vấn Trie động (`findMatchingTerms` / `findNamedEntities`) cho phép trích xuất nhanh toàn bộ thuật ngữ và tên riêng từ QT Pack (Names, VietPhrase) cho bất kỳ đoạn văn CJK nào mà không cần hardcode.

## Requirements

### Functional
- [ ] Thêm phương thức `findMatchingTerms(text: String, projectTerms: List<DictPair> = emptyList(), types: Set<QuickDictionaryType>? = null, minLength: Int = 2): List<DictPair>` vào `QuickTranslationGateway`.
- [ ] Triển khai hàm trong `QuickTranslationRepository` bằng cách tận dụng `pack().baseTrie.allMatchesByStart(text)` và `projectRuntime.trie.allMatchesByStart(text)`.
- [ ] Lọc ưu tiên các thực thể có độ dài $\ge 2$, thuộc loại `QuickDictionaryType.NAME` hoặc `VIETPHRASE`, loại bỏ các mục trùng lặp hoặc mục đơn lẻ không mang nghĩa danh từ.
- [ ] Hỗ trợ cache ngắn hạn theo hash đoạn văn để không tốn chi phí duyệt trie lặp lại khi phân mảnh đoạn văn.

### Non-Functional
- [ ] Performance: Duyệt trie cực nhanh (< 2ms cho đoạn văn 500 ký tự CJK).
- [ ] Memory: Bounded allocations, không tạo thừa list không cần thiết.

## Implementation Steps
1. [ ] Khai báo `findMatchingTerms` trong `QuickTranslationGateway.kt`.
2. [ ] Viết implementation trong `QuickTranslationRepository.kt` sử dụng Trie sẵn có.
3. [ ] Viết unit test trong `QuickTranslationRepositoryTest.kt` kiểm tra độ chính xác khi quét đoạn văn CJK.

## Files to Create/Modify
- `app/src/main/java/io/legado/app/domain/gateway/QuickTranslationGateway.kt` - [Interface declaration]
- `app/src/main/java/io/legado/app/data/repository/QuickTranslationRepository.kt` - [Trie scan implementation]
- `app/src/test/java/io/legado/app/data/repository/QuickTranslationRepositoryTest.kt` - [Unit tests]

## Test Criteria
- [ ] Quét đoạn văn chứa "苏晓进入了轮回乐园" trả về ít nhất: `苏晓` -> `Tô Hiểu`, `轮回` -> `Luân Hồi`, `乐园` -> `Lạc Viên`.
- [ ] Tôn trọng project terms / Room terms truyền vào nếu có đè lên base pack.

---
Next Phase: [Phase 02 - Story Memory & Dynamic QT Pipeline in ML Kit](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/plans/261007-0720-mlkit-dynamic-dict-grammar-engine/phase-02-story-memory-and-qt-pipeline.md)
