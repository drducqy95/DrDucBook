# Phase 01: Benchmarking & Lexicon Cleanup (Khắc Phục Từ Điển & Ngữ Cảnh Thô)

Status: ⬜ Pending
Dependencies: None

## 1. Mục Tiêu
Khắc phục dứt điểm các lỗi từ vựng dị hợm, ngô nghê, phản ngữ cảnh trong bộ từ điển cơ sở (`qt_clean_cvdict_base_v3.tsv`, `qt_clean_lexicon_v1.tsv`, `qt_clean_function_words_v3.tsv`):
- `老天` -> Dịch chuẩn convert: `ông trời` (loại bỏ dứt điểm từ "Chúa Trời").
- `发出` + âm thanh / tiếng động -> Dịch chuẩn: `phát ra` (thay vì "ban hành").
- `下落` + tốc độ / động thái rơi -> Dịch chuẩn: `rơi xuống` / `hạ lạc` (thay vì "tung tích").
- `车门` -> Dịch chuẩn: `cửa xe` (thay vì "môn xe").
- `装有` -> Dịch chuẩn: `gắn / lắp / trang bị` (thay vì "vận chuyển").
- `追加` -> Dịch chuẩn: `tăng thêm / bổ sung` (thay vì "thêm cái gì đó bổ sung").
- `放弃` -> Dịch chuẩn: `từ bỏ / bỏ dở` (thay vì "thả vứt bỏ").
- `消音器` -> Dịch chuẩn: `ống giảm thanh` (thay vì "ống giảm thanh giới").
- `许多年轻` -> Dịch chuẩn: `nhiều người trẻ tuổi` (thay vì "người tuổi trẻ rất nhiều năm").
- `微弱的枪响` -> Dịch chuẩn: `tiếng súng yếu ớt` (thay vì "súng vang lên yếu").

## 2. Nhiệm Vụ Cụ Thể
1. [ ] Rà soát và cập nhật trực tiếp trong `qt_clean_cvdict_base_v3.tsv` và `qt_clean_lexicon_v1.tsv`.
2. [ ] Bổ sung các cặp cụm từ ghép đặc thù của truyện để không bị phân mảnh thành từng từ đơn rời rạc.
3. [ ] Cập nhật bài test `LuanHoiNhacVienChapter1BenchmarkTest.kt` và `LuanHoiNhacVien10ChaptersBenchmarkTest.kt` với các assertion kiểm tra các lỗi từ điển trên.

## 3. Tiêu Chí Nghiệm Thu
- [ ] Không còn xuất hiện "Chúa Trời" trong bản dịch Chương 1.
- [ ] Không còn xuất hiện "ban hành một trận tiếng nghẹn ngào" mà chuyển thành "phát ra một trận tiếng nghẹn ngào".
- [ ] Không còn xuất hiện "môn xe mở ra" mà chuyển thành "cửa xe mở ra".
- [ ] Không còn xuất hiện "tốc độ của tung tích" mà chuyển thành "tốc độ rơi xuống".
