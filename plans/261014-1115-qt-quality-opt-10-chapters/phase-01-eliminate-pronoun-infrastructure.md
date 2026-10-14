# Phase 01: Eliminate Pronoun Infrastructure

Status: ⬜ PENDING
Dependencies: None

## Objective
Xóa bỏ hoàn toàn hạ tầng cơ chế xưng hô (Pronoun infrastructure): loại bỏ toàn bộ các hàm cưỡng chế biến đổi `ta` thành `tôi` và `ngươi` thành `bạn` trong `QuickTranslationRepository.kt`, dọn dẹp các thiết lập xưng hô trong ViewModel và UseCase, trả lại xưng hô thuần túy từ từ điển (`我=ta`, `你=ngươi`, `他=hắn`, `她=nàng`).

## Tasks
1. [ ] Trong `QuickTranslationRepository.kt`:
   - Xóa bỏ các hàm:
     * `applyPronounProfile()`
     * `resolvePronounStyle()`
     * `applyModernPronounRules()`
     * `applyAncientPronounRules()`
     * `applyWesternPronounRules()`
     * `applyModernKinshipRules()`
     * `normalizeNarratorThirdPerson()`
     * Inner class `PronounContextTracker`, `ModernThirdPersonRole`, `PronounHints`
   - Bỏ toàn bộ việc can thiệp vào các đại từ; kết quả dịch đại từ được lấy trực tiếp từ `Pronouns.txt` / `VietPhrase.txt`.
2. [ ] Trong `QuickTranslationGateway.kt`:
   - Loại bỏ hoặc no-op tham số `pronounMode: QuickTranslationPronounMode?`.
3. [ ] Trong `ReadBookViewModel.kt`, `TranslateChapterUseCase.kt`, `ReadBook.kt`, `TranslationService.kt`:
   - Dọn dẹp việc truyền và lưu trữ `quickTranslationPronounMode`.
4. [ ] Trong `TranslationConfigScreen.kt`:
   - Xóa bỏ các import và code thừa liên quan đến `QuickTranslationPronounMode`.

## Test Criteria
- Không còn bất kỳ hàm hay class nào thực hiện logic biến đổi `ta` $\rightarrow$ `tôi`, `ngươi` $\rightarrow$ `bạn`.
- Kiểm thử dịch các câu chứa `我`, `你`, `他`, `她`: kết quả 100% xuất ra `ta`, `ngươi`, `hắn`, `nàng`.
