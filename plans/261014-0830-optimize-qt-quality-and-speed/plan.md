# Plan: Tối Ưu Thuật Toán Quick Translator & Bảo Vệ Chất Lượng Convert (Truyện Thực Tế: Dữ Liệu Tu Tiên - Ngũ Hành Quan)

Created: 2026-10-14T08:32:00+07:00
Status: 🟡 In Progress
Author: Trinity Architect

---

## 1. Đối Tượng Khảo Sát & Phân Tích Thực Tế

- **Tác phẩm đang đọc trên LDPlayer Debug:** `数据修仙，我在五行观证长生` (Dữ Liệu Tu Tiên, Tôi Ở Ngũ Hành Quan Chứng Sinh Trường)
- **Tác giả:** 她说彩礼一百万
- **URL Nguồn:** `https://www.piaotia.com/bookinfo/16/16439.html`
- **Chương đang đọc:** Chương 1 (`第1章 生物芯片`)
- **Dữ liệu thực tế đối soát:** Trích xuất trực tiếp từ bộ nhớ đệm thiết bị LDPlayer (`test_real_book/00000-2305f74ce3530de3.nb`, `.vi.quick_translator.nb`, `.vi.app_ai.nb`, và `translation_dictionary.json`).

---

## 2. Đối Soát Thực Tế Đoạn Văn Chương 1

### Đoạn 1: Mở đầu cảnh tu luyện
- **Nguyên tác CJK:**
  > `山脚处，一间古朴又简洁的房间里，洛言盘坐在木床上，胸膛微微起伏，一呼一吸间都带着某种特殊的韵味。`
- **Bản QT thực tế trên app debug:**
  > `Sơn cước nơi, một giữa cổ phắc lại trong phòng ngắn gọn, Lạc Ngôn bàn ngồi trên giường gỗ, hông thang chuyển động lên xuống khẽ, một hít một thở giữa đều mang vận vị đặc thù nào đó.`
- **Bản AI thực tế trên app debug:**
  > `Dưới chân núi, trong một căn phòng cổ kính mà đơn sơ, Lạc Ngôn khog chân ngồi trên giường gỗ, lồng ngực khẽ phập phồng, mỗi một nhịp thở đều mang theo một vận vị đặc thù nào đó.`
- **Bản Convert Chuẩn Đạt Kỳ Vọng (Dễ đọc - Không việt hóa quá):**
  > `Dưới chân núi, trong một gian phòng cổ phác mà đơn giản, Lạc Ngôn ngồi xếp bằng trên giường gỗ, lồng ngực khẽ phập phồng, giữa một hít một thở đều mang theo vận vị đặc thù nào đó.`
- **Vấn đề cốt lõi:**
  1. `一间` bị dịch thành `một giữa` do từ điển bị ô nhiễm `间=giữa`.
  2. `盘坐` bị dịch thành `bàn ngồi` thay vì thuật ngữ tu tiên `ngồi xếp bằng` / `bàn tọa`.
  3. `胸膛` bị dịch thành `hông thang` thay vì `lồng ngực`.
  4. `微微起伏` bị dịch thành `chuyển động lên xuống khẽ`.

### Đoạn 2: Bảng thông số nhân vật tu tiên
- **Nguyên tác CJK:**
  > `人物：洛言`
  > `年龄：12`
  > `灵力：30`
  > `境界：炼气一层`
  > `功法：基础吐纳法（入门）`
- **Bản QT thực tế trên app debug:**
  > `Người: Lạc Ngôn`
  > `Năm: linh 12`
  > `Linh lực: 30`
  > `Ranh giới: Luyện Khí tầng một`
  > `Công pháp: Cơ sở thổ nạp pháp（cửa vào）`
- **Bản Convert Chuẩn Đạt Kỳ Vọng:**
  > `Nhân vật: Lạc Ngôn`
  > `Tuổi: 12`
  > `Linh lực: 30`
  > `Cảnh giới: Luyện Khí tầng một`
  > `Công pháp: Cơ sở thổ nạp pháp (nhập môn)`
- **Vấn đề cốt lõi:**
  1. `境界` (Cảnh giới tu vi) bị dịch thành `Ranh giới`!
  2. `入门` (Nhập môn tu luyện) bị đè bởi `translation_dictionary.json` thành `cửa vào`!
  3. `人物` bị dịch thành `Người`. `年龄` bị dịch thành `Năm: linh 12`.

### Đoạn 3: Cú pháp câu chữ Đem và băm nát từ ghép
- **Nguyên tác CJK:**
  > `在生物智能芯片的帮助下，将自身的修为数据化，他的吐纳法依然停留在入门的初级阶段。`
- **Bản QT thực tế trên app debug:**
  > `ở sinh vật trợ giúp bên dưới chip thông minh, sẽ tu vi dữ liệu hoá của bản thân, thổ của hắn nạp pháp vẫn ở lại tạm thời ở giai đoạn sơ cấp ở cửa vào.`
- **Bản Convert Chuẩn Đạt Kỳ Vọng:**
  > `Dưới sự trợ giúp của sinh vật trí năng chip, đem tu vi bản thân số liệu hóa, thổ nạp pháp của hắn vẫn dừng lại ở giai đoạn sơ cấp nhập môn.`
- **Vấn đề cốt lõi:**
  1. `将自身的修为数据化` -> Dịch `将` thành `sẽ` (`sẽ tu vi...`).
  2. `他的吐纳法` -> Bị chèn từ sở hữu `他的` vào giữa cụm `吐纳法` thành `thổ của hắn nạp pháp`!
  3. `正常来说` -> Bị dịch thành `Đang thường đến nói`!

---

## 3. Hai Nguồn Gốc Gây Ra Lỗi

### Nguồn gốc 1: Sự Ô Nhiễm Từ Điển Dự Án (`translation_dictionary.json`)
- Phân tích file `translation_dictionary.json` của truyện trên LDPlayer cho thấy có tới **1,250 cặp từ VIETPHRASE rác**:
  `入门 -> cửa vào`, `第 -> đậy`, `贡献 -> đóng góp`, `洛 -> rặc`, `随手 -> một cách tiện lợi`, `间 -> giữa`, `紧 -> khẩn`, `闭 -> bế`, `的 -> của`...
- Vì `projectTerms` có độ ưu tiên cao nhất trong thuật toán, toàn bộ từ vựng cơ bản của truyện bị các từ rác này đè bẹp, biến một bản dịch lẽ ra đúng thành bản dịch dị hợm.
- **Giải pháp:**
  1. Lọc và thanh lọc `BookDictionary`: Chỉ cho phép lưu các thực thể `NAME` và `TERM` hợp lệ. Loại bỏ triệt để việc nhồi nhét các từ đơn, hư từ hoặc cụm từ thường ngày vào từ điển truyện.
  2. Xóa bỏ hoặc vệ sinh `translation_dictionary.json` hiện tại trên thiết bị.

### Nguồn gốc 2: Thuật Toán Cú Pháp & Từ Điển Cơ Sở (`QuickTranslationRepository.kt`)
1. **Bổ sung từ điển thuật ngữ Tu Tiên (Xianxia Lexicon):**
   - `盘坐` -> `ngồi xếp bằng`
   - `胸膛` -> `lồng ngực`
   - `境界` -> `cảnh giới`
   - `入门` -> `nhập môn`
   - `下院` -> `hạ viện`
   - `外门大比` -> `ngoại môn đại bỉ`
   - `内招` -> `chiêu mộ nội bộ` / `tuyển thẳng`
   - `零` -> `Linh` (tên chip AI "Số 0" / "Linh")
   - `正常来说` -> `bình thường mà nói` / `thông thường mà nói`
2. **Xử lý Cú pháp Câu Chữ Đem (`将` / `把` + Tân ngữ + Động từ):**
   - Phân biệt `将` trước cụm danh từ tân ngữ -> dịch thành `đem` (hoặc `lấy`), không dịch thành `sẽ`.
3. **Chống băm nát từ ghép bởi đại từ sở hữu:**
   - Ngăn chặn triệt để hiện tượng tách `他的吐纳法` thành `thổ của hắn nạp pháp`.
4. **Tối ưu hóa Tốc độ ($O(N)$ Zero Allocation):**
   - Bổ sung `firstChars` bitmask và `lengthsMask` vào `FastTermMapLookup` để loại bỏ 128,000 chuỗi substring rác trên mỗi chương.

---

## 4. Kế Hoạch Triển Khai (Milestone P78)

| Task | Mô Tả Nhiệm Vụ | Trạng Thái |
| :--- | :--- | :--- |
| **T78.1** | **Vệ Sinh & Bảo Vệ BookDictionary:** Ngăn chặn nhồi nhét từ thường vào `translation_dictionary.json`, tạo bộ lọc khử nhiễm (Sanitizer) loại bỏ các cặp từ rác (`入门=cửa vào`, `第=đậy`, `间=giữa`...). | ⬜ TODO |
| **T78.2** | **Bổ Sung Từ Điển Tu Tiên Chuẩn Convert:** Cập nhật các từ vựng tu tiên cốt lõi trong `qt_clean_cvdict_base_v3.tsv` (`盘坐=ngồi xếp bằng`, `胸膛=lồng ngực`, `境界=cảnh giới`, `入门=nhập môn`, `下院=hạ viện`, `正常来说=bình thường mà nói`...). | ⬜ TODO |
| **T78.3** | **Khắc Phục Cú Pháp Chữ Đem (`将/把`) & Đại Từ Sở Hữu:** Dịch `将/把` thành `đem`, chống băm nát từ ghép (`他的吐纳法` -> `thổ nạp pháp của hắn`), triệt tiêu các câu dị hợm. | ⬜ TODO |
| **T78.4** | **Tối Ưu Tốc Độ `FastTermMapLookup` (Zero Garbage Allocations):** Thêm bộ lọc ký tự đầu và độ dài khả dụng, loại bỏ 128k allocations per chapter, đưa thời gian dịch về < 20ms. | ⬜ TODO |
| **T78.5** | **Kiểm Thử Trực Tiếp Trên LDPlayer & Benchmark Chương 1-5:** Đẩy bản cập nhật lên LDPlayer, reset cache truyện `数据修仙，我在五行观证长生`, kiểm tra hiển thị thực tế trên Reader và TOC. | ⬜ TODO |
