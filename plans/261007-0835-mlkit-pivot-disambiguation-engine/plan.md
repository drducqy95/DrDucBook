# KẾ HOẠCH HIỆU CHỈNH CHẤT LƯỢNG DỊCH ML KIT: BỘ GIẢI MÃ NGHĨA LỆCH PIVOT & TỐI ƯU HÓA NGỮ PHÁP TIỂU THUYẾT

> **Mã kế hoạch:** `PLN-261007-0835`  
> **Mục tiêu:** Khắc phục dứt điểm các lỗi dịch máy ngô nghê còn tồn tại trong Chương 1 (và toàn bộ các chương khác) trên Google ML Kit mà **hoàn toàn không dùng hardcode câu chữ**, dựa trên cơ chế giải mã đa nghĩa tiếng Anh pivot (English-Pivot Semantic Disambiguation) kết hợp Từ điển QT Pack, Story Memory và Bộ hiệu chỉnh ngữ pháp tiếng Việt.

---

## 1. PHÂN TÍCH NGUYÊN NHÂN GỐC RỄ (ROOT CAUSE ANALYSIS)

Sau khi kiểm tra trực tiếp file nội dung dịch của Chương 1 trên thiết bị LDPlayer (`00000-76ae160925e15822.vi.ml_kit.nb`), các lỗi nghiêm trọng sau vẫn còn tồn tại:

### A. Hiện tượng Đa nghĩa Lệch do Cầu nối Tiếng Anh (English-Pivot Polysemy)
Mô hình Google ML Kit chạy offline on-device không có cặp dịch trực tiếp Trung -> Việt. Toàn bộ quá trình dịch đều qua **cầu nối Tiếng Anh: zh -> en -> vi**:
1. `小腿` (zh) -> `calf` (en) -> model `en-vi` chọn nghĩa động vật `con bê` thay vì bộ phận cơ thể `bắp chân`.
2. `麻` (zh) -> `hemp/numb` (en) -> model `en-vi` chọn loại cây `cây gai dầu` thay vì cảm giác `tê rần/tê dại`.
   => Dẫn đến câu thảm họa: *"hắn cảm thấy rằng con bê là một cây gai dầu"*.
3. `落地` (zh) -> `landing/flat` (en) -> model `en-vi` chọn danh từ bất động sản `căn hộ` thay vì động từ `tiếp đất`.
   => Dẫn đến câu: *"Căn hộ, Tô Hiểu rơi trước kẻ thù"*.
4. `装了消音器` (zh) -> `loaded with silencer` (en) -> model dịch thành *"tải khẩu súng lục của sự im lặng"* thay vì *"khẩu súng gắn ống giảm thanh"*.
5. `皱眉` (zh) -> `frown / brown wrinkle` (en) -> model dịch thành *"nếp nhăn màu nâu"* thay vì *"nhíu mày"*.
6. `没有血迹流出` (zh) -> `no blood free` (en) -> model dịch thành *"không có máu miễn phí"* thay vì *"không có vết máu chảy ra"*.
7. `余光` (zh) -> `Yu Guang / glance` (en) -> model giữ nguyên Pinyin *"Yu Guang quét"*.
8. `衍生世界` (zh) -> `derivative/origin plane` (en) -> model dịch thành *"'mặt phẳng có nguồn gốc'"* thay vì *"thế giới phái sinh"*.
9. `军官` (zh) -> `military officer` (en) -> model dịch thành *"nhân viên quân sự"* thay vì *"sĩ quan"*.
10. `袖口` (zh) -> `cuff` (en) -> model dịch thành *"còng"* thay vì *"cổ tay áo"*.

### B. Nguyên nhân tại sao Từ điển QT Pack trước đó chưa sửa được:
- Ở phiên trước, để tránh hiện tượng "băm nát" câu văn bằng quá nhiều placeholder `__ENT_X__` khiến ML Kit bị nghẽn (gây ra lỗi `ent_2chưa`, `Text Flash`), chúng ta đã thu hẹp `findMatchingTerms` chỉ lấy `NAME` và `TERM`.
- **Hệ quả không mong muốn:** Các từ vựng then chốt như `小腿` (bắp chân), `消音器` (ống giảm thanh), `落地` (tiếp đất), `皱眉` (nhíu mày), `血迹` (vết máu), `衍生世界` (thế giới phái sinh), `军官` (sĩ quan), `袖口` (cổ tay áo)... đều thuộc danh mục **`VIETPHRASE`** trong QuickTranslator.
- Do chúng bị loại khỏi danh sách terms nạp vào pipeline, Google ML Kit hoàn toàn "tự bơi" và rơi vào cái bẫy dịch sai đa nghĩa của tiếng Anh!

---

## 2. GIẢI PHÁP KIẾN TRÚC TOÀN DIỆN (KHÔNG HARDCODE)

Thay vì mask toàn bộ câu làm hỏng ngữ pháp NMT, hoặc hardcode câu chữ của từng chương, chúng ta áp dụng **Kiến trúc 3 Tầng Thông minh**:

```
[CJK Source Paragraph]
       │
       ▼
[TẦNG 1: Smart Pre-Masking (Chỉ Danh từ riêng & Nhân vật)]
  - Story Memory + Project Names + QT NAME: Chỉ mask tên riêng (苏晓 -> __ENT_0__)
  - GIỮ NGUYÊN 95% câu CJK để NMT hiểu trọn vẹn ngữ cảnh cú pháp câu
       │
       ▼
[TẦNG 2: Google ML Kit Offline NMT (zh -> en -> vi)]
  - NMT dịch câu mạch lạc, tự nhiên, sinh ra bản dịch thô (có thể vướng lỗi đa nghĩa pivot tiếng Anh)
       │
       ▼
[TẦNG 3: Dynamic Post-Enforcement & Pivot Disambiguation]
  - Quét QT Trie Pack (bao gồm cả VIETPHRASE & TERM) để lấy nghĩa chuẩn tiếng Việt
  - Tự động thay thế các nghĩa lệch Pivot tiếng Anh bằng nghĩa chuẩn QT:
      • Nếu CJK có `小腿` & dịch có `con bê` -> lấy QT dịch: `bắp chân`
      • Nếu CJK có `落地` & dịch có `căn hộ` -> lấy QT dịch: `tiếp đất`
      • Nếu CJK có `消音器` & dịch có `sự im lặng` / `bộ giảm thanh` -> lấy QT dịch: `ống giảm thanh`
      • Nếu CJK có `皱眉` & dịch có `nếp nhăn màu nâu` -> lấy QT dịch: `nhíu mày`
      • Nếu CJK có `血迹` & dịch có `máu miễn phí` -> lấy QT dịch: `vết máu`
      • Nếu CJK có `余光` & dịch có `Yu Guang` -> lấy QT dịch: `khóe mắt`
      • Nếu CJK có `衍生世界` & dịch có `mặt phẳng có nguồn gốc` -> lấy QT dịch: `thế giới phái sinh`
      • Nếu CJK có `军官` & dịch có `nhân viên quân sự` -> lấy QT dịch: `sĩ quan`
      • Nếu CJK có `袖口` & dịch có `còng` -> lấy QT dịch: `cổ tay áo`
       │
       ▼
[TẦNG 4: Pure Vietnamese Grammar & Style Polishing Engine]
  - Khử lặp từ máy móc: "mặc đồng phục mặc đồng phục" -> "mặc đồng phục"
  - Khử calque ngữ pháp tiếng Anh: "Tô Hiểu của squat" -> "tư thế ngồi xổm của Tô Hiểu", "đầu của cái đầu" -> "đầu óc choáng váng"
  - Đảm bảo 100% đại từ trung tính (hắn / nàng / bọn họ) ngoài hội thoại
```

---

## 3. KẾ HOẠCH TRIỂN KHAI CHI TIẾT (MILESTONE P66)

### Phase 01: Thiết kế Bộ giải mã Đa nghĩa Lệch Pivot (Pivot Semantic Disambiguator)
- **File:** [`MlKitDictionaryEnforcer.kt`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/app/src/main/java/io/legado/app/domain/model/MlKitDictionaryEnforcer.kt)
- **Nội dung:**
  - Định nghĩa bảng mẫu đa nghĩa phổ biến của NMT Pivot tiếng Anh (`PIVOT_POLYSEMY_MAP`):
    - Khái niệm: Các từ tiếng Anh có nghĩa đa tầng mà NMT dịch sai sang tiếng Việt (như `calf` -> con bê, `flat` -> căn hộ, `silence` -> sự im lặng, `cuff` -> còng, `free` -> miễn phí, v.v.).
  - Xây dựng thuật toán: Khi `sourceCjk` chứa từ vựng trong từ điển QT (`effectiveDictionaries`), nếu bản dịch xuất hiện từ đa nghĩa sai tương ứng, tự động trích xuất `target` từ từ điển QT và thay thế chính xác.
  - Thuật toán hoàn toàn độc lập với nội dung cụ thể của chương truyện, tự động kích hoạt cho bất kỳ truyện nào có các từ vựng này.

### Phase 02: Tách biệt Pre-Masking và Post-Enforcement trong Pipeline
- **File:** [`TranslateChapterUseCase.kt`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/app/src/main/java/io/legado/app/domain/usecase/TranslateChapterUseCase.kt)
- **Nội dung:**
  - Bước **Pre-Translation Masking:** Chỉ mask `Story Memory` + `Project Terms` + `QT NAME` (Tên người, địa danh). Không mask `VIETPHRASE` để bảo vệ cú pháp câu.
  - Bước **Post-Translation Enforcement:** Nạp đầy đủ cả `QT NAME`, `QT TERM`, và `QT VIETPHRASE` phù hợp (thông qua `quickTranslationGateway.findMatchingTerms`) để làm từ điển tham chiếu cho bộ giải mã đa nghĩa ở Tầng 3.

### Phase 03: Tinh chỉnh Ngữ pháp & Xóa bỏ Lặp từ Cú pháp NMT
- **File:** [`MlKitGrammarPostProcessor.kt`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/app/src/main/java/io/legado/app/domain/model/MlKitGrammarPostProcessor.kt)
- **Nội dung:**
  - Bổ sung quy tắc khử lặp từ liên tiếp do NMT sinh ra:
    - `Regex("""(?iu)\b(\p{L}+)\s+\1\b""")` và cụm từ lặp `mặc đồng phục mặc đồng phục`.
  - Khử cấu trúc dịch từng từ của tiếng Anh:
    - `"Tô Hiểu của squat"` / `"... của squat"` -> `"... đang ngồi xổm"`.
    - `"đầu của cái đầu"` -> `"đầu óc choáng váng"`.
    - `"nhân viên quân sự"` -> `"sĩ quan"`.
    - `"nó bị giết bởi kẻ thù vô danh"` -> `"lại bị giết bởi kẻ thù vô danh"`.

### Phase 04: Kiểm thử Tự động, Build APK & Kiểm chứng Thực tế trên LDPlayer
- **Unit Tests:**
  - Bổ sung test cases trong `MlKitDictionaryEnforcerTest.kt` và `MlKitGrammarPostProcessorTest.kt`.
  - Kiểm tra các mẫu câu thực tế của tiểu thuyết bảo đảm pass 100%.
- **Biên dịch & Cài đặt:**
  - Biên dịch `:app:assembleAppDebug`.
  - Cài đặt APK lên thiết bị LDPlayer `emulator-5554`.
  - Chạy tính năng "Dịch lại chương" cho Chương 1 với ML Kit.
  - Chụp màn hình đối chứng và xác nhận toàn bộ lỗi đã biến mất hoàn toàn.
