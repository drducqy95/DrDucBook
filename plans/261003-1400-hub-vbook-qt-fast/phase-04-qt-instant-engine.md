# Phase 04: Cải Tiến Thuật Toán Quick Translator (Chuẩn QT Gốc & vBook: LuatNhan, Name, Vietphrase, Pronoun, PhienAm)

Status: ⬜ Pending
Dependencies: None

## 1. Mục tiêu
Tái cấu trúc thuật toán Quick Translator theo đúng cơ chế của **Quick Translator PC gốc và vBook**:
- Sử dụng đầy đủ và đúng thứ tự ưu tiên 5 bộ quy tắc: **LuatNhan, Name, Vietphrase, Pronoun, PhienAm**.
- Loại bỏ triệt để các cỗ máy phân tích ngữ pháp nặng nề gây chậm (Jieba segmenter, quy hoạch động Viterbi DP, pronoun tracking đệ quy, segment remapping chuỗi).
- Đạt tốc độ trả output **ngay lập tức (< 2ms / chương)** với độ phức tạp tuyến tính $O(N)$ và zero memory garbage.

---

## 2. Thứ Tự Ưu Tiên & Cơ Chế 5 Bộ Quy Tắc (Chuẩn QT Gốc / vBook)

Quét văn bản tuần tự 1 lượt từ trái qua phải $O(N)$:

### A. Ký tự không phải chữ Hán (Non-CJK)
- Dấu câu (chấm, phẩy, ngoặc kép, hỏi, than, chấm lửng, gạch ngang), số, chữ Latinh/English, ký tự xuống dòng (`\n`, `\r\n`):
  -> **Giữ nguyên vẹn 100%**, đưa thẳng vào output `StringBuilder`.

### B. Ký tự chữ Hán (CJK)
Tại vị trí `offset`, tìm kiếm khớp theo đúng thứ tự phân tầng QT gốc:

#### 1. Luật Nhân (`LuatNhan.txt` - Grammar Rules / Templates)
- Kiểm tra các mẫu luật nhân cố định tại vị trí hiện tại (ví dụ: `{0}的{1}` -> `{1} của {0}`, `{0}了` -> `đã {0}`, `{0}们` -> `những {0}`, `{0}着` -> `đang {0}`,...).
- Sử dụng cấu trúc `indexedTemplatesAt(text, offset)` đã được lập chỉ mục theo ký tự mỏ neo đầu tiên.
- Khớp các slot `{0}`, `{1}` với các từ vựng trong Trie một cách nhanh chóng mà không cần chạy toàn văn bản.
- Nếu khớp luật nhân: Áp dụng quy tắc hoán vị, ghi vào output, nhảy cóc con trỏ `offset = match.endExclusive`.

#### 2. Tên Riêng (`Name.txt` / `Names.txt`)
- Ưu tiên cao nhất trong các từ vựng cụm từ:
  - **Project Name** (Tên riêng dự án / truyện hiện tại do người dùng đặt) -> Ưu tiên số 1!
  - **Bundled Name** (Tên riêng hệ thống từ điển gốc: nhân vật, địa danh, bang phái, quốc gia).
- Longest match: Nếu có tên riêng khớp tại `offset`, ưu tiên chọn Name trước Vietphrase thông thường.

#### 3. Cụm Từ Vietphrase (`VietPhrase.txt`) & Đại Từ (`Pronouns.txt`)
- Tra cứu cụm từ tiền tố dài nhất (Greedy Longest Match) trong Trie:
  - **Project Vietphrase** (Cụm từ người dùng tùy chỉnh cho truyện)
  - **Bundled Pronouns** (`Pronouns.txt` - Đại từ nhân xưng: `我=ta/tôi`, `你=ngươi/chàng`, `他=hắn`, `她=nàng`, `他们=bọn hắn`,...)
  - **Bundled Vietphrase** (`VietPhrase.txt` - Kho cụm từ 700.000+ từ vựng tiếng Trung -> tiếng Việt).
- Nguyên tắc khớp dài nhất: Cụm từ 4 chữ > 3 chữ > 2 chữ.
- Xử lý đa nghĩa: Lấy nghĩa chuẩn đầu tiên (phân tách bởi `/` hoặc `|`, ví dụ: `"ngươi/chàng/anh/hắn"` -> `"ngươi"`), đúng hành vi của Quick Translator gốc.
- Ghi vào output kèm khoảng trắng phân cách từ phù hợp.
- Nhảy cóc con trỏ: `offset += termLength`.

#### 4. Phiên Âm Hán-Việt (`PhienAm.txt` / `HanViet.txt`)
- Khi không có bất kỳ Luật nhân, Name, Vietphrase hay Pronoun nào khớp với cụm từ bắt đầu tại `offset`:
  - Tra âm Hán-Việt cho 1 chữ đơn tại `offset`:
    - Ưu tiên: `customPhonetics[char]` -> `pack.phonetics[char]`.
    - Nếu có âm Hán-Việt: Ghi âm Hán-Việt vào output (viết thường hoặc theo ngữ cảnh).
    - Nếu không tìm thấy: Ghi nguyên chữ Hán gốc.
  - Nhảy cóc con trỏ: `offset += 1`.

---

## 3. Sinh DisplaySourceSegment Đồng Thời ($O(1)$)
- Trong chế độ `translateMapped` (phục vụ tính năng bấm vào từ để tra Quick Dictionary):
  - Do con trỏ quét tuyến tính từ trái qua phải, ta đã biết chính xác `sourceStart = offset`, `sourceEnd = offset + len`.
  - Toạ độ hiển thị tương ứng chính là `displayStart = output.length` (trước khi ghi) và `displayEnd = output.length` (sau khi ghi).
  - Ghi nhận trực tiếp `DisplaySourceSegment(sourceStart, sourceEnd, displayStart, displayEnd)` với độ phức tạp $O(1)$.
  - **Loại bỏ hoàn toàn** hàm `remapProcessedSegments` với hàng loạt vòng lặp tìm kiếm chuỗi `indexOf` đắt đỏ.

---

## 4. Loại Bỏ Các Gánh Nặng Gây Chậm (Eliminations)
1. ❌ **Loại bỏ Jieba Tokenizer** khỏi luồng dịch QT: Trie Vietphrase/Name đã tự động gom từ theo tiền tố dài nhất, việc chạy Jieba trước là hoàn toàn dư thừa và gây nghẽn CPU.
2. ❌ **Loại bỏ Viterbi DP (`bestTranslationPlan`)**: Không duyệt ngược toàn bộ văn bản và không cấp phát hàng nghìn object `TranslationCandidate` trên heap.
3. ❌ **Loại bỏ Pronoun Tracker & Gender Inference đệ quy**: Sử dụng trực tiếp nghĩa đại từ từ từ điển `Pronouns.txt` chuẩn QT gốc.
4. ❌ **Loại bỏ String Remapping**: Tọa độ tra từ điển điểm chạm được ghi nhận ngay lập tức.

---

## 5. Implementation Steps
1. [ ] Cấu trúc lại phương thức `translateClassic` trong `QuickTranslationRepository.kt`:
   - Duyệt tuyến tính $O(N)$ bằng `StringBuilder`.
   - Kết hợp tuần tự 5 tầng: Non-CJK -> LuatNhan -> Name -> Pronoun/Vietphrase -> PhienAm.
   - Lấy nghĩa đầu tiên (`substringBefore('/')`).
2. [ ] Triển khai `translateClassicMapped` trong `QuickTranslationRepository.kt`:
   - Ghi nhận `DisplaySourceSegment` trực tiếp trong vòng lặp 1 pass duy nhất.
3. [ ] Chuyển hướng các hàm `translate` và `translateMapped` chính sang `translateClassic` và `translateClassicMapped`.
4. [ ] Giữ nguyên các hàm viết hoa đầu câu (`capitalizeSentenceStarts`) nhẹ nhàng để định dạng chữ đầu câu đẹp mắt.
5. [ ] Kiểm tra đo đạc thời gian dịch một chương mẫu: Đảm bảo thời gian hoàn thành dưới 2ms.

## Files to Modify
- `app/src/main/java/io/legado/app/data/repository/QuickTranslationRepository.kt`
