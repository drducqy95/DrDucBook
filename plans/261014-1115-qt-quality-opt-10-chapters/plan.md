# Plan: Xóa Bỏ Hạ Tầng Xưng Hô, Xóa QT Clean, Dùng Thuần .txt (QT Chỉ Dùng FMM, Bỏ Beam Search; NMT Giữ Nguyên Beam Search K) & Đo Lại Chất Lượng - Tốc Độ Xuất (Milestone P79)

Created: 2026-10-14T11:53:00+07:00
Status: 🟡 In Progress / Planning
Milestone: P79
Target Story: `数据修仙，我在五行观证长生` (10 chương đầu: `00000` - `00009`)

---

## 1. Mục Tiêu & Ranh Giới Kỹ Thuật (Clear Technical Boundaries)

Người dùng yêu cầu phân định ranh giới rõ ràng giữa 2 động cơ dịch:

### 1.1. Đối với Quick Translator (QT):
1. **Xóa bỏ toàn bộ hạ tầng cơ chế xưng hô**:
   - Loại bỏ `applyPronounProfile`, `applyModernPronounRules`, `applyAncientPronounRules`, `applyWesternPronounRules`, `normalizeNarratorThirdPerson`, `PronounContextTracker`.
   - Dọn dẹp các lựa chọn xưng hô trong `ReadBookViewModel`, `TranslateChapterUseCase`, `TranslationService`, `PreferKey.quickTranslationPronounMode`.
   - Trả lại đại từ nguyên bản từ từ điển (`我=ta`, `你=ngươi`, `他=hắn`, `她=nàng`). Tuyệt đối không còn bất kỳ dòng code nào cưỡng chế biến đổi `ta` $\rightarrow$ `tôi`, `ngươi` $\rightarrow$ `bạn`.
2. **Xóa bỏ toàn bộ dữ liệu "QT Clean" (TSV/CSV)**:
   - Xóa bỏ `qt_clean_cvdict_base_v3.tsv` (nguồn gốc các lỗi ngô nghê `Quốc khánh Trung Quốc`, `tu hành phát triển tâm linh`, `1 tệ`, `Hội An`).
   - Xóa bỏ toàn bộ các file rule và correction của QT Clean (`qt_clean_rules_v1.tsv` - `v8.tsv`, `qt_clean_corrections_v4.tsv` - `v10.tsv`, `qt_clean_function_words_v3.tsv`).
3. **Dùng thuần túy dữ liệu từ điển .txt**:
   - Dùng trực tiếp dữ liệu từ điển chuẩn nguyên bản của QuickTranslator: `VietPhrase.txt` (760,000+ cụm từ), `Names.txt` / `Names2.txt`, `Pronouns.txt`, `ChinesePhienAmWords.txt`, `LuatNhan.txt` (hoặc mmap binary index `.qtdict` được compile nguyên bản 100% từ các file `.txt` này).
4. **CHỈ XOÁ BỎ BEAM SEARCH VỚI QT**:
   - Loại bỏ `beamTranslationPlan()` trong `QuickTranslationRepository.kt` và slider `quick_translation_beam_width` trong `TranslationConfigScreen.kt`.
   - QT khôi phục hoàn toàn thuật toán gốc: **Forward Maximum Matching (Greedy Longest Match - FMM)** tuyến tính $O(N)$ với độ trễ $\le 30\text{ ms/chương}$.
   - Khôi phục ánh xạ **1-1 Text Mapping** hoàn hảo giữa ký tự nguồn tiếng Trung và từ dịch tiếng Việt (zero post-processing regex distortion).

### 1.2. Đối với NMT (Neural Machine Translation - Hachimi ONNX Marian):
1. **BẢO LƯU TOÀN VẸN BEAM SEARCH CHO NMT**:
   - Tiếp tục áp dụng Beam Search trong `HachimiOnnxTranslator.kt` (`decodeBeamSearch`).
   - Cho phép người dùng điều chỉnh kích thước Beam $K$ (`nmtBeamWidth`, dải giá trị $1 \le K \le 8$, mặc định $K=4$) qua slider trong `TranslationConfigScreen.kt`.

### 1.3. Đo lại chất lượng và tốc độ xuất sau khi hoàn tất:
- Chạy lại bài kiểm thử benchmark tự động trên 10 chương thực tế `00000` đến `00009`.
- Lập bảng đối chiếu chi tiết:
  * Tốc độ và thời gian xuất (latency ms, throughput chars/s).
  * Chất lượng văn phong (xác nhận 100% sạch bóng `tôi`, `bạn`, `Quốc khánh`, `phát triển tâm linh`, `1 tệ`, `Hội An`; các từ `tụ khí đan`, `tông môn`, `sơn môn`, `phường thị` chuẩn phong vị Hán Việt tiên hiệp).
- Build APK x86_64, cài đặt lên LDPlayer và chụp màn hình Reader nghiệm thu thực tế.

---

## 2. Các Giai Đoạn Thực Hiện (Phases)

| Phase | Tên Giai Đoạn | Nhiệm Vụ Trọng Tâm | Status |
|---|---|---|---|
| **Phase 01** | **Eliminate Pronoun Infrastructure** | Xóa bỏ toàn bộ code hậu xử lý xưng hô (`applyPronounProfile`, `applyModernPronounRules`, `PronounContextTracker`, v.v.) trong `QuickTranslationRepository` và dọn dẹp các tham chiếu trong `ReadBookViewModel`, `TranslateChapterUseCase`, `PreferKey`. Trả lại đại từ nguyên bản từ từ điển (`ta`, `ngươi`, `hắn`, `nàng`). | ⬜ PENDING |
| **Phase 02** | **Purge QT Clean Datasets & Wire Pure TXT Dictionaries** | Xóa bỏ các asset `qt_clean_*.tsv`, loại bỏ hoàn toàn các biến `RULE_ASSETS`, `CLEAN_BASE_ASSET`; cấu hình nạp thuần túy tập từ điển `.txt` (`VietPhrase.txt`, `Names.txt`, `Pronouns.txt`, `ChinesePhienAmWords.txt`, `LuatNhan.txt` / `.qtdict` tương ứng). | ⬜ PENDING |
| **Phase 03** | **Classic QT Algorithm (FMM), Remove QT Beam Search & Guard NMT Beam Search (K)** | Xóa bỏ Beam Search khỏi QT (xóa `beamTranslationPlan` và slider QT beam width). Cài đặt thuật toán FMM tuyến tính $O(N)$ cho QT để đạt độ trễ $\le 30\text{ ms/chương}$ và text mapping 1-1. **Bảo lưu nguyên vẹn Beam Search cho NMT** với khả năng điều chỉnh $K \in [1, 8]$. | ⬜ PENDING |
| **Phase 04** | **10-Chapter Benchmark, Quality/Speed Audit & LDPlayer Deploy** | Chạy benchmark 10 chương thực tế, đo latency ms và chars/s, đối chiếu diff chất lượng văn bản, build APK, cài lên LDPlayer và chụp màn hình nghiệm thu. | ⬜ PENDING |

---

## 3. Tiêu Chuẩn Nghiệm Thu (Acceptance Criteria)
- **Đại từ**: 100% xuất hiện `ta`, `ngươi`, `hắn`, `nàng`. Tuyệt đối không còn `bạn`, `tôi`.
- **Từ vựng**: 0% xuất hiện `Quốc khánh Trung Quốc`, `tu hành phát triển tâm linh`, `1 tệ`, `Hội An`; các từ `tụ khí đan`, `tông môn`, `sơn môn`, `phường thị`, `tu hành` xuất hiện chuẩn xác tự nhiên.
- **Tốc độ QT**: Độ trễ trung bình $\le 30\text{ ms / chương}$ (nhanh hơn 10 - 50 lần so với phiên bản QT Clean cũ).
- **Text Mapping QT**: Tọa độ và chỉ mục tra cứu từ điển khi chạm / bôi đen trên Reader chuẩn xác 100%.
- **NMT Beam Search**: NMT tiếp tục hỗ trợ Beam Search đầy đủ, người dùng điều chỉnh được $K$ từ 1 đến 8 trong cài đặt dịch.
- **Ứng dụng**: Build APK thành công, chạy mượt mà trên LDPlayer.
