# Phase 03: Xianxia Core Terminology & Compound Lexicon

Status: ⬜ PENDING
Dependencies: Phase 01, Phase 02

## Objective
Bổ sung và chuẩn hóa toàn bộ các thực thể tiên hiệp, danh xưng, địa danh, đan dược và công pháp xuất hiện trong 10 chương đầu, đảm bảo chuẩn âm Hán Việt và không bị băm từ.

## Tasks
1. [ ] Đan dược & Linh thảo:
   - `聚气丹` $\rightarrow$ `Tụ Khí Đan`
   - `小聚气丹` $\rightarrow$ `Tiểu Tụ Khí Đan`
   - `辟谷丹` $\rightarrow$ `Bích Cốc Đan` (hoặc `Tịch Cốc Đan`)
   - `狐仙草` $\rightarrow$ `Hồ Tiên Thảo`
   - `紫猴花` $\rightarrow$ `Tử Hầu Hoa`
   - `墨玉莲` $\rightarrow$ `Mặc Ngọc Liên`
2. [ ] Địa danh & Công trình:
   - `小荒山` $\rightarrow$ `Tiểu Hoang Sơn`
   - `大荒` $\rightarrow$ `Đại Hoang`
   - `杂事殿` $\rightarrow$ `Tạp Sự Điện`
   - `藏经阁` $\rightarrow$ `Tàng Kinh Các`
   - `山门` $\rightarrow$ `Sơn Môn`
   - `宗门` $\rightarrow$ `Tông Môn`
   - `坊市` $\rightarrow$ `Phường Thị`
3. [ ] Công pháp, Đạo thuật & Khái niệm:
   - `木灵内观经` $\rightarrow$ `Mộc Linh Nội Quan Kinh`
   - `万叶飞花` $\rightarrow$ `Vạn Diệp Phi Hoa`
   - `未入门` $\rightarrow$ `Chưa nhập môn`
   - `闭环` $\rightarrow$ `Vòng lặp khép kín` / `Bế hoàn`
   - `客官` $\rightarrow$ `Khách quan`
   - `月华` $\rightarrow$ `Nguyệt hoa`
   - `灵根资质` $\rightarrow$ `Linh căn tư chất`
   - `不知名的符文` $\rightarrow$ `Phù văn không rõ tên` / `Phù văn vô danh`
   - `念决：“疾！”` $\rightarrow$ `Niệm quyết: "Tật!"`
4. [ ] Chuẩn hóa tiêu đề chương:
   - Định dạng chuẩn: `Chương X: [Tên Chương Viết Hoa Chuẩn]` (ví dụ: `Chương 1: Chip Sinh Vật`, `Chương 3: Tạp Sự Điện`, `Chương 5: Nghiên Cứu Tụ Khí Đan`).

## Files to Modify
- `app/src/main/assets/quick_clean/qt_clean_corrections_v10.tsv` (hoặc tạo v12)
- `app/src/main/java/io/legado/app/data/repository/QuickTranslationRepository.kt`

## Test Criteria
- Toàn bộ danh từ riêng, công pháp và đan dược 10 chương đầu hiển thị chính xác 100% theo phong vị Hán Việt tiên hiệp.
