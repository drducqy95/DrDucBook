# Phase 01: Dictionary Pollution Purging & Sanitation

Status: ⬜ PENDING
Dependencies: None

## Objective
Loại bỏ hoàn toàn các mục từ điển ô nhiễm nghĩa hiện đại, từ điển địa danh hoặc nghĩa máy móc làm hỏng văn phong tiên hiệp trong `qt_clean_corrections_v10.tsv` (hoặc nâng cấp lên v12) và bộ từ điển nền.

## Tasks
1. [ ] Purge `修行` $\rightarrow$ Đảm bảo `修行` chỉ dịch là `tu hành` hoặc `tu luyện`, tuyệt đối không có `phát triển tâm linh`.
2. [ ] Purge `十一` $\rightarrow$ Đảm bảo `十一` trong ngữ cảnh số đếm hoặc tuổi tác (`十一二岁`, `十一年`, v.v.) dịch là `mười một` hoặc `thập nhất`, không phải `Quốc khánh Trung Quốc`.
3. [ ] Purge `一块` $\rightarrow$ Đảm bảo `一块` trong ngữ cảnh vị trí/hành động (`玩到一块去`, `在一块`) dịch là `cùng nhau` / `một chỗ`, không phải `1 tệ`.
4. [ ] Purge `老会安` / `长老会安排` $\rightarrow$ Ngăn chặn ghép nhầm thành địa danh `Hội An`, đảm bảo tách đúng `长老会` (trưởng lão hội) + `安排` (an bài / sắp xếp).
5. [ ] Purge `多大功夫` / `没费多大功夫` $\rightarrow$ Ngăn chặn `多大` thành `bao nhiêu tuổi?` và `功夫` thành `kỹ năng`.
6. [ ] Purge `第一时间` $\rightarrow$ Sửa từ `đệ 1 giờ giữa` thành `ngay lập tức` / `ngay từ đầu` / `thời gian đầu tiên`.
7. [ ] Purge `半点分心` $\rightarrow$ Loại bỏ `nửa 0 phút tâm`, dịch chuẩn xác là `nửa điểm phân tâm` / `chút nào phân tâm`.

## Files to Modify
- `app/src/main/assets/quick_clean/qt_clean_corrections_v10.tsv` (hoặc tạo `qt_clean_corrections_v12.tsv`)
- `app/src/main/assets/quick_clean/qt_clean_rules_v8.tsv`
- `app/src/main/java/io/legado/app/data/repository/QuickTranslationRepository.kt`

## Test Criteria
- Unit test quét 10 chương: Không còn bất kỳ xuất hiện nào của `Quốc khánh`, `1 tệ`, `phát triển tâm linh`, `Hội An`, `nửa 0 phút tâm`, `bao nhiêu tuổi? Kỹ năng`.
