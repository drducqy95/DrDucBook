# Phase 01: QT Quality, Output Speed & Fast Mapping Acceleration (Tổng hợp phân tích Chương 1 - 6 trên LDPlayer)

Status: ⬜ Pending
Dependencies: None

## Objective
Nâng cao toàn diện chất lượng dịch QuickTranslator thực tế dựa trên phân tích tổng hợp 6 chương thực tế (`数据修仙，我在五行观证长生`):
- Xóa sạch 100% tàn dư chữ `đích` (424 lỗi) và `liễu` (54 lỗi).
- Sửa lỗi cụm số `四周` $\to$ `4 tuần` (6 lỗi) và các lỗi từ vựng ngô nghê (`放空` $\to$ `chạy xe không`, `一块` $\to$ `1 tệ`).
- Khắc phục các lỗi gãy tên riêng tiên hiệp (`庶务殿` $\to$ `Sở Vụ Điện`, `木灵内观经` $\to$ `Mộc Linh Nội Quan Kinh`, `万叶飞花` $\to$ `Vạn Diệp Phi Hoa`, `小聚气丹` $\to$ `Tiểu Tụ Khí Đan`, `许有德` $\to$ `Hứa Hữu Đức`, `陈师兄` $\to$ `Trần sư huynh`, `月儿` $\to$ `vầng trăng`).
- Đảo ngữ chuẩn xác các cấu trúc định ngữ, trạng ngữ vị trí (`ở phía sau`, `ở bên trong`, `ở bên dưới`) và cấu trúc so sánh nhất (`最...`).
- Tối ưu hóa tốc độ xuất output QT nhanh hơn 3-5 lần (Lazy on-demand Trie traversal).
- Giảm độ trễ mapping "Thêm từ điển" trong Reader xuống dưới 50ms (Localize 500 ký tự & Fast-path Trie probe).

---

## Dữ liệu phân tích thực tế từ 6 chương (Chương 1 đến 6 trên LDPlayer)

| Chương | Tiêu đề chương | Số ký tự gốc | Số ký tự dịch | Lỗi `đích` | Lỗi `liễu` | Lỗi `4 tuần` | Lỗi tiêu biểu |
|---|---|---|---|---|---|---|---|
| **Chương 1** | 生物芯片 (Sinh vật Chip) | 4.870 | 17.968 | 87 | 12 | 0 | `chạy xe không tâm thần`, `tiến giai thổ nạp ở phía sau pháp` |
| **Chương 2** | 入门和贡献点 (Nhập môn & Điểm cống hiến) | 4.790 | 17.745 | 82 | 11 | 2 | `chơi đến 1 tệ`, `nửa tháng ở trước cuối năm` |
| **Chương 3** | 庶务殿 (Sở Vụ Điện) | 4.438 | 16.123 | 84 | 5 | 1 | `công việc vặt điện`, `mộc linh bên trong xem kinh`, `vạn Diệp Phi hoa` |
| **Chương 4** | 藏经阁 (Tàng Kinh Các) | 2.576 | 9.596 | 51 | 14 | 1 | `vách tường 4 tuần`, `hứa có đức`, `1 tệ giảm cỡ nhỏ lương khô` |
| **Chương 5** | 研究聚气丹 (Nghiên cứu Tụ Khí Đan) | 2.314 | 8.647 | 54 | 4 | 0 | `khí đan tiểu tụ` (7 lần), `tụ khí Đan Đan phương` |
| **Chương 6** | 诡 (Quỷ) | 2.697 | 10.041 | 66 | 8 | 2 | `4 tuần đích sương mù`, `Nằm ở trần ở chính giữa sư huynh`, `Nguyệt Nhi` |
| **TỔNG CỘNG** | **6 chương thực tế** | **21.685** | **80.120** | **424** | **54** | **6** | Hàng loạt lỗi gãy cấu trúc và ngô nghê |

---

## Root Causes Identified & Technical Architecture Fixes

### 1. Tàn dư chữ `đích` (的) - 424 lần xuất hiện
- **Căn nguyên:** Trong `QuickTranslationRepository.kt`, khi nạp pack thì `templates` (`LuatNhan.txt`) bị gán `null` khi `qt2025Runtime != null`. Do đó 287 luật đảo ngữ định ngữ bị bỏ qua hoàn toàn. Ngoài ra, `matchDynamicAttributiveDe` bị rụng slot candidate khiến cấu trúc đảo ngữ không khớp, rơi về `ChinesePhienAmWords.txt` dịch thành `đích`.
- **Giải pháp:**
  + Kích hoạt quy tắc `LuatNhan.txt` và đảo ngữ định ngữ `A 的 B` $\to$ `B của A` hoặc `B A` ngay cả khi đang dùng `qt2025Runtime`.
  + Thêm bộ quy tắc bắt các mẫu định ngữ phổ biến: `[Tính từ/Cụm tính từ] + 的 + [Danh từ]` $\to$ `[Danh từ] + [Tính từ/Cụm tính từ]` (ví dụ `淡青色的气流` $\to$ `khí lưu màu xanh nhạt`, `极其复杂的结构` $\to$ `kết cấu cực kỳ phức tạp`).
  + Các chữ `的` ngữ khí cuối câu $\to$ dịch thành `vậy / đấy` hoặc lược bỏ sạch chữ `đích`.

### 2. Tàn dư chữ `liễu` (了) - 54 lần xuất hiện
- **Căn nguyên:** Trợ từ hoàn thành/ngữ khí `了` rơi về từ điển gốc gán `liễu`.
- **Giải pháp:**
  + `Động từ + 了` $\to$ dịch thành `[Động từ] rồi` hoặc chỉ giữ `[Động từ]` nếu đã có từ chỉ thời gian/quá khứ (ví dụ `找了一整天了` $\to$ `đã tìm cả ngày rồi`).
  + `Tính từ + 了` $\to$ `[Tính từ] rồi` (ví dụ `夜深了` $\to$ `đêm đã khuya rồi`, `越来越浓了` $\to$ `càng ngày càng đậm rồi`).
  + Lược bỏ sạch các chữ `liễu` phản cảm cuối câu.

### 3. Lỗi cụm số đếm `四周` (bốn phía) $\to$ `4 tuần`
- **Căn nguyên:** `NUMBER_UNIT_TEMPLATES` gán `"周" to "{n} tuần"` và `NUMBER_RULE` nhận điểm thưởng `QT2025_RUNTIME_SCORE_BONUS`, đè bẹp từ ghép `四周 = bốn phía`.
- **Giải pháp:**
  + Loại trừ `四周` khỏi `NUMBER_UNIT_TEMPLATES`.
  + Thiết lập cơ chế: Bất kỳ từ ghép nào có trong Trie từ điển (`四周 = bốn phía/xung quanh`) đều có độ ưu tiên cao hơn quy tắc phân rã số đếm rời `[四] + [周]`.

### 4. Từ vựng chuyên ngành Tiên hiệp & Sửa lỗi dịch ngô nghê
- **Sửa lỗi từ ngô nghê:**
  + `放空` $\to$ `thả lỏng / làm trống tâm thần` (không dịch `chạy xe không`).
  + `一块` trong ngữ cảnh `玩到一块` $\to$ `chơi cùng nhau`, trong `一块压缩干粮` $\to$ `một khối lương khô nén` (không dịch `1 tệ`).
- **Từ điển Tiên hiệp & Nhân danh/Địa danh:**
  + `庶务殿` $\to$ `Sở Vụ Điện` (không dịch `công việc vặt điện`).
  + `木灵内观经` $\to$ `Mộc Linh Nội Quan Kinh` (không dịch `mộc linh bên trong xem kinh`).
  + `万叶飞花` $\to$ `Vạn Diệp Phi Hoa` (không dịch `vạn Diệp Phi hoa`).
  + `小聚气丹` $\to$ `Tiểu Tụ Khí Đan` (không dịch `khí đan tiểu tụ`).
  + `许有德` $\to$ `Hứa Hữu Đức` (không dịch `hứa có đức`).
  + `陈师兄` $\to$ `Trần sư huynh` (không tách chữ `Trần` ra trước `sư huynh`).
  + `月儿` trong ngữ cảnh `月儿已上树梢` $\to$ `vầng trăng / trăng` (không dịch `Nguyệt Nhi`).
  + `零` $\to$ `Zero / Linh` (khi xưng hô AI chip).

### 5. Đảo ngữ cấu trúc trạng ngữ vị trí & So sánh nhất
- `[Địa điểm/Vật] + 里的 + [Sự vật]` $\to$ `[Sự vật] trong [Địa điểm/Vật]` (ví dụ `‘庶务殿’里的任务` $\to$ `nhiệm vụ trong Sở Vụ Điện`, không dịch `'Công việc vặt điện' nhiệm vụ ở bên trong`).
- `[Hành động] + 之后/后` $\to$ `sau khi [Hành động]` (ví dụ `睡熟之后` $\to$ `sau khi ngủ say`, không dịch `ngủ say phía sau`).
- `[Sự vật] + 之下/下` $\to$ `dưới [Sự vật]` (ví dụ `芯片的改良下` $\to$ `dưới sự cải tiến của chip`).
- `最 + [Tính từ] + 的 + [Danh từ]` $\to$ `[Danh từ] + [Tính từ] + nhất` (ví dụ `最简单的控物术` $\to$ `thuật khống vật đơn giản nhất`, không dịch `khống đơn giản nhất vật thuật`).

### 6. Tốc độ xuất output QT (Lazy On-Demand Trie FMM)
- Thay thế toàn bộ thuật toán pre-computation eager `allMatchesByStart` (tạo 5.000 bucket rác mỗi chương) bằng tra cứu Trie trực tiếp tại vị trí con trỏ `offset` khi FMM duyệt văn bản.
- Giảm 80% cấp phát bộ nhớ, tăng tốc độ xuất output lên gấp 3-5 lần (<300ms/chương trên JVM, <500ms trên Android).

### 7. Tốc độ trả kết quả Mapping khi "Thêm từ điển" (< 50ms)
- Trong `ReadBookViewModel`: Thay vì gọi `translateMapped` cho toàn bộ chương 5.000 ký tự, giới hạn phạm vi trong cửa sổ ngữ cảnh chứa từ bôi đen (~500 ký tự xung quanh).
- Trong `QuickDictionarySelectionResolver`: Cung cấp fast-path tra cứu Trie trực tiếp cho các ứng viên từ điển ngắn (1-6 ký tự), loại bỏ 240 vòng lặp gọi lại toàn bộ pipeline dịch.

---

## Implementation Steps
1. [ ] **Sửa lỗi '四周' & từ vựng ngô nghê (`放空`, `一块`) trong `QuickTranslationRepository`**.
2. [ ] **Bổ sung từ điển Tiên hiệp chuẩn (`庶务殿`, `木灵内观经`, `万叶飞花`, `小聚气丹`, `许有德`, `陈师兄`, `月儿`)**.
3. [ ] **Kích hoạt và hoàn thiện bộ lọc định ngữ `的` và trợ từ `了`, xóa sạch 424 chữ `đích` và 54 chữ `liễu`**.
4. [ ] **Thêm quy tắc đảo ngữ trạng từ vị trí (`ở phía sau`, `ở bên trong`, `ở bên dưới`) và cấu trúc so sánh nhất (`最...`)**.
5. [ ] **Chuyển đổi Trie Matching sang Lazy On-Demand FMM trong `QuickTranslationRepository`**.
6. [ ] **Tối ưu hóa Mapping Window (~500 ký tự) và Fast-Path Trie Probe trong `ReadBookViewModel` & `QuickDictionarySelectionResolver`**.
7. [ ] **Tạo Benchmark Unit Test kiểm thử trực tiếp trên cả 6 chương thực tế**.

---

## Files to Create/Modify
- `app/src/main/java/io/legado/app/data/repository/QuickTranslationRepository.kt`
- `app/src/main/java/io/legado/app/ui/quickdict/QuickDictionarySelectionResolver.kt`
- `app/src/main/java/io/legado/app/ui/book/read/ReadBookViewModel.kt`
- `app/src/test/java/io/legado/app/data/repository/QuickTranslationSixChaptersBenchmarkTest.kt`

---

## Test Criteria
- [ ] Số lượng chữ `đích` trong cả 6 chương giảm từ 424 xuống 0 (hoặc tối thiểu trong tên riêng ngoại lệ).
- [ ] Số lượng chữ `liễu` giảm từ 54 xuống 0.
- [ ] Số lượng chữ `4 tuần` giảm từ 6 xuống 0 (`四周` $\to$ `bốn phía / xung quanh`).
- [ ] Không còn các lỗi dịch ngô nghê: `chạy xe không`, `1 tệ`, `công việc vặt điện`, `mộc linh bên trong xem kinh`, `hứa có đức`, `khí đan tiểu tụ`, `trần ở chính giữa sư huynh`.
- [ ] Tốc độ dịch mỗi chương 5.000 ký tự đạt dưới 300ms.
- [ ] Thời gian mapping khi bôi đen và bấm "Thêm từ điển" đạt dưới 50ms.
