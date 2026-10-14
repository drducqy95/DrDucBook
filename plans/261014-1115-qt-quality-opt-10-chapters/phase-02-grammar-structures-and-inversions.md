# Phase 02: Grammar & Structural Inversion Refinement

Status: ⬜ PENDING
Dependencies: Phase 01

## Objective
Khắc phục các cấu trúc ngữ pháp đảo ngữ, trợ từ kết cấu `的`, cấu trúc `在...下`, và câu tồn hiện / bổ ngữ thường gặp trong tiểu thuyết tiếng Hán.

## Tasks
1. [ ] Cải tiến cấu trúc `在...下`:
   - `在芯片的改良下` $\rightarrow$ `dưới sự cải tiến của chip` (thay vì `tại tâm cải thiện mảnh dưới`).
   - `在...的帮助下` $\rightarrow$ `dưới sự trợ giúp của...`.
   - `在...的操控下` $\rightarrow$ `dưới sự thao túng/điều khiển của...`.
2. [ ] Khắc phục đảo ngữ `A 的 B`:
   - `现在的洛言` $\rightarrow$ `Lạc Ngôn hiện tại` (thay vì `Bây giờ của Lạc Ngôn`).
   - `洛言的呼吸` $\rightarrow$ `hơi thở của Lạc Ngôn` (thay vì `thở của Lạc Ngôn`).
   - `小狐狸的龇牙` $\rightarrow$ `tiểu hồ ly nhe răng` (thay vì `tiểu hồ ly của nhe răng`).
3. [ ] Mở rộng xử lý câu chữ `将/把`:
   - Hỗ trợ cụm giới từ / bổ ngữ trước tân ngữ: `他将背在背上的包裹解开` $\rightarrow$ `Hắn đem bọc hành lý cõng trên lưng tháo ra` (thay vì `Hắn sẽ lưng vải gói ở trên lưng tháo ra`).
4. [ ] Khắc phục lặp từ điều kiện / phó từ:
   - `如果想要...` $\rightarrow$ loại bỏ hiện tượng lặp `nếu như muốn nếu muốn`.
   - `...不已` $\rightarrow$ `郁闷不已` $\rightarrow$ `không khỏi buồn bực / nản lòng` (không để sót đuôi `không thể không`).

## Files to Modify
- `app/src/main/java/io/legado/app/data/repository/QuickTranslationRepository.kt`
- `app/src/main/assets/quick_clean/qt_clean_rules_v8.tsv`

## Test Criteria
- Không còn cụm từ đảo ngữ `Bây giờ của Lạc Ngôn`, `tại ... dưới`, `nếu như muốn nếu muốn`.
- Các câu có `将/把` cõng/mang bổ ngữ dịch mượt mà với `đem` / `lấy`.
