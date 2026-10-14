# Phase 01: Pure TXT Dictionaries Architecture

Status: ⬜ PENDING
Dependencies: None

## Objective
Chuyển đổi nguồn cấp dữ liệu cho Quick Translator: hoàn toàn không sử dụng dữ liệu "QT Clean" (`qt_clean_cvdict_base_v3.tsv`, `qt_clean_rules_v8.tsv`, `qt_clean_corrections_...`), mà sử dụng trực tiếp và thuần túy nguồn từ điển `.txt` chuẩn của QuickTranslator (`VietPhrase.txt`, `Names.txt`, `ChinesePhienAmWords.txt`, `Pronouns.txt`, `LuatNhan.txt`) hoặc index binary `.qtdict` được compile nguyên bản từ các file này.

## Tasks
1. [ ] Cấu hình nạp từ điển:
   - Ưu tiên 1: `Project Terms` (User Names / Vietphrase trong Book/Project Dictionary).
   - Ưu tiên 2: `Names.txt` / `Names2.txt` (Tên riêng từ điển chuẩn QuickTranslator).
   - Ưu tiên 3: `VietPhrase.txt` (Kho từ vựng 760,000+ cụm từ gốc, chuẩn xác cho truyện tiên hiệp/kiếm hiệp).
   - Ưu tiên 4: `Pronouns.txt` / Phụ từ (`我=ta`, `你=ngươi`, `他=hắn`, `她=nàng`).
   - Ưu tiên 5: `LuatNhan.txt` (Luật nhân số đếm/lượng từ).
   - Ưu tiên 6: `ChinesePhienAmWords.txt` (Phiên âm Hán Việt cho từng chữ Hán đơn lẻ).
2. [ ] Loại bỏ hoàn toàn references và logic load các file:
   - `qt_clean_cvdict_base_v3.tsv`
   - `qt_clean_rules_v1.tsv` - `qt_clean_rules_v8.tsv`
   - `qt_clean_corrections_v4.tsv` - `qt_clean_corrections_v10.tsv`
   - `qt_clean_function_words_v3.tsv`
3. [ ] Đảm bảo cả biến thể debug và release đều truy cập được tập từ điển `.txt` / `.qtdict` chuẩn này.

## Files to Modify
- `app/src/main/java/io/legado/app/data/repository/QuickTranslationRepository.kt`
- `app/src/main/java/io/legado/app/data/repository/QuickDictionaryPackStore.kt`

## Test Criteria
- Kiểm tra dữ liệu nạp vào: 0 mục nào đến từ `qt_clean_cvdict_base_v3.tsv`.
- `修行` tra cứu trả về `tu hành`.
- `十一` tra cứu trả về `mười một`.
- `聚气丹` tra cứu trả về `tụ khí đan`.
