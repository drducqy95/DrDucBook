# Phase 02: Purge QT Clean Datasets & Wire Pure TXT Dictionaries

Status: ⬜ PENDING
Dependencies: Phase 01

## Objective
Xóa bỏ hoàn toàn các file dữ liệu và cơ chế nạp của "QT Clean" (`qt_clean_*.tsv`), chuyển sang sử dụng thuần túy dữ liệu từ điển `.txt` nguyên bản của QuickTranslator (`VietPhrase.txt`, `Names.txt`, `Pronouns.txt`, `ChinesePhienAmWords.txt`, `LuatNhan.txt` hoặc binary index `.qtdict` được compile nguyên bản 100% từ chúng).

## Tasks
1. [ ] Xóa bỏ toàn bộ các file `qt_clean_*.tsv` trong `app/src/main/assets/offline/`:
   - `qt_clean_cvdict_base_v3.tsv`
   - `qt_clean_rules_v1.tsv` đến `qt_clean_rules_v8.tsv`
   - `qt_clean_corrections_v4.tsv` đến `qt_clean_corrections_v10.tsv`
   - `qt_clean_function_words_v3.tsv`
   - `qt_clean_lexicon_v1.tsv`
   - `qt_clean_names_core_v2.tsv`
   - `qt_clean_names_wikidata_v2.tsv`
   - `qt_clean_vietphrase_v2.tsv`
   - `qt_clean_webnovel_v4.tsv`
   - `qt_clean_phonetics_unihan17.tsv`
2. [ ] Trong `QuickTranslationRepository.kt`:
   - Xóa bỏ các hằng số và code nạp:
     * `RULE_ASSETS`, `CLEAN_BASE_ASSET`, `CLEAN_CORRECTION_ASSETS`, `CLEAN_NAME_ASSETS`
     * `MAX_TEMPLATE_RULES`, `MAX_POST_RULES`
   - Cấu hình nạp trực tiếp tập từ điển thuần `.txt` / `.qtdict`:
     * Ưu tiên 1: `Project Terms` (User book dictionary).
     * Ưu tiên 2: `Names.txt` / `Names2.txt` (Tên riêng từ điển chuẩn QuickTranslator).
     * Ưu tiên 3: `VietPhrase.txt` (760,000+ cụm từ nguyên bản).
     * Ưu tiên 4: `Pronouns.txt` (Phụ từ, đại từ: `我=ta`, `你=ngươi`).
     * Ưu tiên 5: `LuatNhan.txt` (Luật nhân số từ).
     * Ưu tiên 6: `ChinesePhienAmWords.txt` (Phiên âm Hán Việt đơn tự).
3. [ ] Đảm bảo dữ liệu từ điển đồng nhất trên cả môi trường Debug và Release.

## Test Criteria
- 100% không còn file hay code tham chiếu đến `qt_clean_*`.
- Dữ liệu tra cứu:
  * `十一` $\rightarrow$ `mười một` (không còn `Quốc khánh Trung Quốc`).
  * `修行` $\rightarrow$ `tu hành` (không còn `tu hành phát triển tâm linh`).
  * `老会安` $\rightarrow$ `lão hội an` (không còn ghép sai địa danh `Hội An`).
  * `聚气丹` $\rightarrow$ `tụ khí đan` (không còn `tập hợp khí đan`).
