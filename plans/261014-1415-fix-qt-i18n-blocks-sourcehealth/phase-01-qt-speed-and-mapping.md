# Phase 01: Đưa Thuật Toán QT Về QT Gốc (Từ Điển + Luật Nhân), Tăng Tốc Độ Output & Chuẩn Hóa Text Mapping 1:1



> **Thuộc kế hoạch:** [`plan.md`](./plan.md)  

> **Phase ID:** `phase-01`  

> **Trạng thái:** ⬜ Pending  

> **Phụ thuộc:** Không



---



## 1. Mục Tiêu (Objectives)

1. **Đưa thuật toán dịch nhanh về đúng bản chất QuickTranslator (QT) gốc:**

   - Sử dụng thuật toán **Khớp từ dài nhất từ trái sang phải (Linear FMM — Forward Maximum Matching)** kết hợp với **Bộ quy tắc `LuatNhan` (Luật Nhân)** và **Hệ thống Từ điển đa tầng** (`StoryMemory / BookDict` > `User QuickDict` > `Names` > `VietPhrase` > `ChinesePhienAmWords`).

   - Loại bỏ cơ chế duyệt đa giả thuyết `beamTranslationPlan` (`QtHypothesis`, `QtHypNode`) và các bộ suy diễn ngữ pháp tự chế / 202 regex hậu xử lý nặng nề đang làm chậm tốc độ và làm biến dạng câu.

2. **Đảm bảo chất lượng 80–90% qua bộ từ điển & Luật Nhân với tốc độ tức thì:**

   - Rút ngắn thời gian dịch một chương 3.000–5.000 chữ Hán xuống **< 50ms**.

3. **Khôi phục ánh xạ văn bản (`MappedDisplayText`) chính xác tuyệt đối 1:1:**

   - Mọi từ/cụm từ tiếng Việt hiển thị trên màn hình đọc đều trỏ chính xác về khoảng ký tự chữ Hán gốc (`rawStart..rawEnd`), giúp `QuickDictionarySelectionResolver` trích xuất đúng 100% chữ Hán gốc trong `< 1ms` khi người dùng bôi đen thêm từ điển.



---



## 2. Phân Tích Nguyên Nhân Gốc Rễ (Root Cause Analysis)



### 2.1. Tại sao thuật toán QT hiện tại bị chậm và biến dạng nghĩa?

- Trong `QuickTranslationRepository.kt`, các phiên bản trước đã chồng thêm:

  1. `beamTranslationPlan` (duyệt top-K giả thuyết `QtHypothesis` / `QtHypNode` với `beamWidth = 1..5`).

  2. Các hàm tự đoán ngữ pháp ngoài phạm vi Luật Nhân (`renderAttributiveDe`, `bestGrammarMatch`, `Qt2025Runtime` với 287 quy tắc tự chế).

  3. Vòng lặp 202 biểu thức chính quy hậu xử lý (`qt_clean_rules_v8.tsv`) trong `QuickTranslationTextPostProcessor`.

- **Hậu quả:**

  - Các quy tắc tự đoán định ngữ và regex hậu xử lý can thiệp thô bạo vào kết quả đã tra từ `VietPhrase`, gây ra hiện tượng đảo từ sai ranh giới mệnh đề, xóa nhầm từ hoặc ghép các từ không liên quan.

  - Tiêu tốn hàng trăm mili-giây CPU và tạo áp lực cấp phát bộ nhớ (GC thrashing) không cần thiết.



### 2.2. Tại sao bôi đen chữ để thêm từ điển (`QuickDictionarySelectionResolver`) bị lệch hoặc trống?

- Khi `beamTranslationPlan` và 202 regex hậu xử lý thay đổi trật tự từ hoặc cắt/ghép chuỗi sau khi đã sinh văn bản, các tọa độ `displayStart..displayEnd` trong `MappedDisplayText.spans` bị trượt khỏi vị trí thực tế.

- Khi người dùng bôi đen một cụm từ trên màn hình đọc, `QuickDictionarySelectionResolver` không tìm được `MappedSpan` khớp chính xác, phải rơi vào nhánh tìm kiếm xấp xỉ theo dòng/đoạn dẫn đến trả về cả đoạn văn dài hoặc để trống trường chữ Hán gốc (Raw).



---



## 3. Thiết Kế Kiến Trúc QT Gốc (Classic FMM + `LuatNhan` Engine)



### 3.1. Sơ Đồ Luồng Xử Lý Tuyến Tính 3 Bước (3-Stage Linear Pipeline)

```text

[Văn bản CJK Gốc (Raw Text)]

   │

   ▼

[Bước 1: Tiền Xử Lý & Bảo Vệ Token (Pre-Clean & Protected Spans)]

   • Loại bỏ các từ nằm trong danh sách IgnoreWord (Pre-clean) nhưng vẫn giữ nguyên bản đồ chỉ số gốc (raw offset map).

   • Nhận diện và khóa cứng các ProtectedToken: URL, thẻ HTML/XHTML, EPUB Block Markers (drop-cap, img, css alignment).

   │

   ▼

[Bước 2: Duyệt Tuyến Tính Khớp Luật Nhân & Khớp Từ Điển Dài Nhất (Linear FMM + LuatNhan)]

   • Cho con trỏ i chạy từ 0 đến rawText.length - 1:

     1. Nếu vị trí i là ProtectedToken hoặc ký tự phi CJK (Latin/Số/Xuống dòng):

        - Ghi nguyên vẹn vào MappedTextBuilder, tạo MappedSpan(i, i + len, dispStart, dispEnd), tăng i += len.

     2. Kiểm tra khớp mẫu LuatNhan tại vị trí i (qua chỉ mục ký tự đầu O(1) LeadFilter / Prefix Trie):

        - Mẫu LuatNhan có dạng: "A{0}B", "{0}的{1}", "不{0}了", "被{0}所{1}", "第{0}章"...

        - Nếu khớp một mẫu LuatNhan hợp lệ (các biến {0}, {1} không vượt qua dấu câu hay từ giới hạn):

          + Dịch đệ quy từng đoạn con trong biến {0}, {1} bằng chính hàm FMM Từ điển.

          + Ráp theo mẫu đích tiếng Việt của LuatNhan (VD: "{1} của {0}" hoặc "không {0} nữa").

          + Ghi nhận đầy đủ các MappedSpan con của từng từ bên trong {0}, {1} và phần khung LuatNhan vào MappedTextBuilder!

          + Nhảy con trỏ: i = match.endIndex.

     3. Nếu không khớp LuatNhan, thực hiện Khớp Từ Điển Dài Nhất (Longest Prefix Match - FMM) từ độ dài maxLen về 1:

        - Sử dụng bitmask lengthsMaskByFirstChar[rawText[i]] để bỏ qua ngay các độ dài không tồn tại.

        - Tra cứu theo thứ tự ưu tiên tuyệt đối:

          (a) Book / StoryMemory Dictionary (Từ điển riêng của bộ truyện)

          (b) User QuickDictionary (Từ điển người dùng lưu trong Room DB)

          (c) Names (Từ điển Tên riêng nhân vật / địa danh)

          (d) VietPhrase (Từ điển cụm từ cốt lõi — quyết định 80-90% chất lượng bản dịch)

          (e) ChinesePhienAmWords (Từ điển âm Hán-Việt cho chữ đơn)

        - Lấy nghĩa đầu tiên (first meaning trước dấu '/' hoặc '|').

        - Tự động chèn 1 dấu cách phân tách từ nếu ký tự trước đó là chữ/số.

        - Ghi trực tiếp MappedSpan(rawStart = i, rawEnd = i + L, displayStart, displayEnd).

        - Nhảy con trỏ: i += L.

   │

   ▼

[Bước 3: Chuẩn Hóa Dấu Câu & Viết Hoa Đầu Câu (Zero-Drift Formatting)]

   • Chuyển dấu câu toàn giác CJK (，。！？：“”‘’【】（）) sang dấu câu chuẩn tiếng Việt (, . ! ? : " " ' ' [ ] ( )).

   • Thực hiện ngay trong MappedTextBuilder để không làm dịch chuyển bất kỳ offset nào của MappedSpan!

   • Viết hoa chữ cái đầu đoạn và chữ cái đầu tiên sau dấu kết thúc câu (. ! ? : ") hoặc sau dấu mở ngoặc kép (chỉ đổi char case 1-1 nên độ dài chuỗi và MappedSpan giữ nguyên 100%).



```



---



## 4. Các Bước Triển Khai Chi Tiết (Implementation Steps)



### Task 01.1: Refactor `QuickTranslationRepository.kt` về Thuật Toán QT Gốc (FMM + `LuatNhan`)



* **File:** `app/src/main/java/io/legado/app/data/repository/QuickTranslationRepository.kt`

* **Chi tiết thực hiện:**

1. Trong `translate()` và `translateMapped()`:

* Ngừng gọi `beamTranslationPlan()`. Chuyển cả hai hàm sang sử dụng chung một lõi thực thi tuyến tính `translateLinearQt(rawText, lookup, buildMapping = true/false)`.





2. Tối ưu hóa bộ khớp `LuatNhan`:

* Nạp đầy đủ các quy tắc `LuatNhan` từ tài nguyên đóng gói và từ bảng `QuickDictionary` của người dùng.

* Kiểm soát chặt chẽ ranh giới của các biến `{0}`, `{1}` trong `LuatNhan`: một slot `{0}` không bao giờ được nuốt dấu câu (`，。！？；：“”`) hoặc ký tự xuống dòng `\n`.

* Khi dịch bên trong slot `{0}`, truyền trực tiếp `MappedTextBuilder` vào hàm dịch đệ quy để từng từ đơn/từ ghép bên trong `{0}` đều sinh ra `MappedSpan` riêng biệt của chính nó.





3. Trong `cleanQuickDictionaryTarget(rawTarget)`:

* Khi một mục từ trong `VietPhrase.txt` hoặc `Names.txt` có nhiều nghĩa phân cách bởi `/` hoặc `|` (ví dụ: `修炼=tu luyện/tu hành`), luôn lấy **nghĩa đầu tiên** (`tu luyện`) theo đúng chuẩn QuickTranslator truyền thống, loại bỏ logic tự ý đảo chọn nghĩa gây lệch văn phong.











### Task 01.2: Bảo Toàn 100% Tọa Độ `MappedDisplayText` & Tối Ưu `QuickDictionarySelectionResolver.kt`



* **Files:**

* `app/src/main/java/io/legado/app/domain/model/MappedDisplayText.kt`

* `app/src/main/java/io/legado/app/ui/quickdict/QuickDictionarySelectionResolver.kt`





* **Chi tiết thực hiện:**

1. Xây dựng lớp hỗ trợ nội bộ `MappedTextBuilder` trong `QuickTranslationRepository.kt`:

* Mọi thao tác `appendToken(rawStart, rawEnd, translatedText)` đều tự động xử lý khoảng trắng ngăn cách từ và ghi nhận chính xác `[displayStart, displayEnd)` sau khi đã tính khoảng trắng.

* Việc viết hoa chữ cái đầu câu (`Character.toUpperCase`) được thực hiện tại chỗ trên mảng ký tự hoặc `StringBuilder.setCharAt(idx, upperChar)` để **không làm thay đổi bất kỳ chỉ số độ dài nào của `MappedSpan**`.





2. Trong `QuickDictionarySelectionResolver.kt`:

* Khi người dùng bôi đen vùng `[selStart, selEnd)` trên văn bản hiển thị:

* Tìm tất cả `MappedSpan` trong `mappedDisplayText.spans` có phần giao dương (`maxOf(span.displayStart, selStart) < minOf(span.displayEnd, selEnd)`).

* Nếu các span tìm được liên tiếp nhau trong văn bản gốc (hoặc nằm trong cùng một câu), lấy `rawStart = spans.minOf { it.rawStart }` và `rawEnd = spans.maxOf { it.rawEnd }` làm kết quả chính xác tuyệt đối (`rawText.substring(rawStart, rawEnd)`).

* Tạo thêm danh sách `alternatives` từ các cụm từ lân cận (±1 đến ±2 token) để người dùng có thể mở rộng/thu hẹp nhanh chỉ với 1 chạm.















### Task 01.3: Tinh Giản `QuickTranslationTextPostProcessor` & Cập Nhật `TranslateDynamicUiTextUseCase`



* **Files:**

* `app/src/main/java/io/legado/app/data/repository/QuickTranslationRepository.kt`

* `app/src/main/java/io/legado/app/domain/usecase/TranslateDynamicUiTextUseCase.kt`





* **Chi tiết thực hiện:**

1. Trong `QuickTranslationTextPostProcessor`:

* Gỡ bỏ việc quét 202 regex `qt_clean_rules_v8.tsv` trên văn bản chương truyện để bảo toàn 100% nghĩa từ điển và tọa độ `MappedSpan`.

* Chỉ giữ lại hàm chuẩn hóa chương mục (`Đệ X Chương` $\rightarrow$ `Chương X`) cho tiêu đề chương và hàm dọn khoảng trắng dấu câu cơ bản khi gọi `translate()` không cần mapping.





2. Trong `TranslateDynamicUiTextUseCase.kt`:

* Đảm bảo `needsQuickTranslation()` kiểm tra nhanh sự tồn tại của ký tự CJK (`\u4E00`..`\u9FFF`, `\u3400`..`\u4DBF`), bỏ qua ngay lập tức các chuỗi tiếng Việt/Anh thuần túy.





3. Tăng `QT_CACHE_REVISION` để làm mới toàn bộ cache chương QT cũ.







---



## 5. Danh Sách Tệp Sửa Đổi (Files to Modify)



* `app/src/main/java/io/legado/app/data/repository/QuickTranslationRepository.kt`

* `app/src/main/java/io/legado/app/domain/model/MappedDisplayText.kt`

* `app/src/main/java/io/legado/app/ui/quickdict/QuickDictionarySelectionResolver.kt`

* `app/src/main/java/io/legado/app/domain/usecase/TranslateDynamicUiTextUseCase.kt`

* `app/src/test/java/io/legado/app/data/repository/QuickTranslationRepositoryTest.kt`

* `app/src/test/java/io/legado/app/ui/quickdict/QuickDictionarySelectionResolverTest.kt`



---



## 6. Tiêu Chí Nghiệm Thu (Pass Criteria)



* [ ] **PASS-01 (Tốc độ):** Dịch một chương 3.000–5.000 chữ Hán hoàn tất trong **< 50ms** trên thiết bị và JVM test.

* [ ] **PASS-02 (Chất lượng QT Gốc):** Câu dịch giữ đúng nghĩa `VietPhrase`, `Names` và cấu trúc `LuatNhan` (đạt 80–90% độ mượt thuần từ điển), không bị mất chữ hay đảo lộn câu vô lý.

* [ ] **PASS-03 (Mapping 1:1):** Bôi đen bất kỳ từ đơn, từ ghép hay cụm từ nằm trong cấu trúc `LuatNhan` trên màn hình đọc đều lấy ra đúng 100% chữ Hán gốc tương ứng, không bao giờ bị trống trường Raw hay lấy tràn cả đoạn văn.
