# Phase 03: Pure Vietnamese Grammar & Syntax Healing Engine

Status: ⬜ Pending
Dependencies: Phase 02

## Objective
Tái cấu trúc và chuẩn hóa `MlKitGrammarPostProcessor` và `MlKitPronounNeutralizer` thành bộ máy xử lý **ngữ pháp tiếng Việt thuần túy** (Grammar & Syntax Rules), hoàn toàn không chứa từ vựng hay câu văn riêng của từng truyện.

## Requirements

### Functional
- [ ] **Pronoun Neutrality (Đại từ nhân xưng):**
  - Chuyển `anh ta` / `anh ấy` / `hắn ta` -> `hắn` (hoặc theo cấu hình QuickTranslationPronounMode).
  - Chuyển `cô ta` / `cô ấy` -> `nàng` (hoặc `cô`).
  - Chuyển số nhiều: `các anh ấy`, `các cô ấy`, `bọn anh ấy` -> `bọn họ`.
  - Khử rò rỉ đại từ ngôi 1 và 2 ngoài hội thoại ("của bạn" -> "của hắn", "trước mặt bạn" -> "trước mắt hắn", "nếu bạn" -> "nếu hắn", "bạn nên chết" -> "vốn dĩ hẳn là đã chết").
  - Bảo vệ tuyệt đối hội thoại trong ngoặc `“...”`, `[...]`, `— ...`.
- [ ] **Mid-Sentence Decapitalization (Viết thường giữa câu):**
  - Mở rộng quy tắc tự động viết thường các động từ, phó từ, tính từ vô tình bị viết hoa giữa câu sau danh từ riêng/chủ ngữ (ví dụ: `Tô Hiểu Không quan tâm` -> `Tô Hiểu không quan tâm`, `Tô Hiểu Im lặng` -> `Tô Hiểu im lặng`, `hắn cố Gắng` -> `hắn cố gắng`).
  - Bao quát các trợ từ / hư từ: `Không|Chưa|Chẳng|Đừng|Im|Cứ|Đều|Cùng|Tự|Lập|Trực|Hoàn|Toàn|Nhận|Khám|Gắng|Cố` v.v.
- [ ] **Vietnamese NMT Syntactic Healing (Sửa cấu trúc ngữ pháp dịch máy):**
  - Vị trí trạng ngữ: "... một mình" sau cụm vị ngữ -> đưa lên trước hành động ("một mình ...").
  - Tính từ + động từ nguyên mẫu kiểu Anh: "rất đơn giản để làm" -> "rất dễ thực hiện".
  - Động từ liên kết & triệu chứng: "điều này là rất..." -> "điều này rất...", "có một số đau đầu" -> "hơi đau đầu".
  - Thể bị động máy móc: "... tạm thời không thể được sử dụng" -> "... tạm thời chưa thể sử dụng".
  - Cấu trúc "hãy để [ai đó]": "không thể hãy để hắn" -> "không thể làm hắn", "hãy để [ai đó]" -> "khiến cho [ai đó]".
  - Dọn dẹp khoảng trắng, dấu câu lặp, khử lặp từ ghép bị ngắt.

### Non-Functional
- [ ] Khả năng tổng quát hóa (Generality): 100% quy tắc áp dụng cho mọi thể loại văn học, không có tên nhân vật hay ngữ cảnh tác phẩm cố định trong file grammar.
- [ ] Tuân thủ Android ICU regex: Chỉ dùng bounded lookbehind `(?<![\p{L}\p{N}])` và flag `(?iu)`.

## Implementation Steps
1. [ ] Rà soát loại bỏ toàn bộ chuỗi hardcode tác phẩm khỏi `MlKitGrammarPostProcessor.kt`.
2. [ ] Hoàn thiện và tổng quát hóa các quy tắc cú pháp tiếng Việt trong `MlKitGrammarPostProcessor.kt`.
3. [ ] Bổ sung kiểm tra và xử lý đại từ trong `MlKitPronounNeutralizer.kt`.
4. [ ] Viết unit tests ngữ pháp chuyên sâu trong `MlKitGrammarPostProcessorTest.kt` và `MlKitPronounNeutralizerTest.kt`.

## Files to Create/Modify
- `app/src/main/java/io/legado/app/domain/model/MlKitGrammarPostProcessor.kt` - [Pure grammar engine]
- `app/src/main/java/io/legado/app/domain/model/MlKitPronounNeutralizer.kt` - [Pure pronoun engine]
- `app/src/test/java/io/legado/app/domain/model/MlKitGrammarPostProcessorTest.kt` - [Grammar unit tests]
- `app/src/test/java/io/legado/app/domain/model/MlKitPronounNeutralizerTest.kt` - [Pronoun unit tests]

## Test Criteria
- [ ] Tất cả unit tests ngữ pháp pass 100% trên JVM mà không cần biết nội dung truyện nào.
- [ ] Không có class initializer exception hay ICU regex pattern syntax exception trên Android.

---
Next Phase: [Phase 04 - Automated Testing, APK Build & LDPlayer Live Validation](file:///d:/Downloads/Archives/legado-with-MD3-main\legado-with-MD3-main\plans\261007-0720-mlkit-dynamic-dict-grammar-engine/phase-04-testing-and-live-validation.md)
