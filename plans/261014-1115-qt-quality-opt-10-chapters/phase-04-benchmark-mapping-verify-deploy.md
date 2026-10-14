# Phase 04: 10-Chapter Benchmark, Mapping Verification & LDPlayer Deployment

Status: ⬜ PENDING
Dependencies: Phase 01, Phase 02, Phase 03

## Objective
Chạy kiểm thử benchmark tự động trên toàn bộ 10 chương thực tế `数据修仙，我在五行观证长生`, kiểm tra chi tiết diff chất lượng, đo đạc tốc độ sau khi khôi phục thuật toán gốc, xác minh tính năng tra cứu từ điển / mapping text, build APK và deploy lên LDPlayer Reader.

## Tasks
1. [ ] Chạy lại test `testTranslateAndBenchmark10RealChapters` trên JVM:
   - Đo thời gian dịch 10 chương với thuật toán QT gốc FMM.
   - So sánh với thời gian cũ (8.5s): mục tiêu đạt $\le 0.5\text{s}$ cho toàn bộ 10 chương (tốc độ $\ge 20,000\text{ chars/s}$).
2. [ ] Kiểm tra phân tích chất lượng:
   - Xác nhận sạch bóng 100% các từ `Quốc khánh Trung Quốc`, `tu hành phát triển tâm linh`, `1 tệ`, `Hội An`, `tôi`, `bạn`.
   - Xác nhận các từ tiên hiệp xuất hiện tự nhiên: `Tụ khí đan`, `Tông môn`, `Sơn môn`, `Phường thị`, `Tu hành`, `Mười một mười hai tuổi`.
3. [ ] Xác minh Text Mapping:
   - Viết unit test kiểm tra `translateMapped`: đảm bảo ánh xạ `segment.source` $\leftrightarrow$ `segment.text` khớp tuyệt đối từng vị trí.
4. [ ] Build APK debug x86_64 và cài đặt lên LDPlayer.
5. [ ] Mở Reader trên LDPlayer, chuyển qua các chương 1, 2, 3, 5, 6, 10, thử nghiệm chạm tra từ điển trên từ `Luyện Khí`, `Tụ khí đan`, `Tông môn`, và chụp ảnh màn hình nghiệm thu.
6. [ ] Cập nhật `project_progress.json`, `.brain/session.json`, hoàn thành Milestone P79.

## Files to Modify
- `app/src/test/java/io/legado/app/data/repository/QuickTranslationRepositoryTest.kt`
- `project_progress.json`
- `.brain/session.json`

## Test Criteria
- Build APK thành công 100%.
- Tốc độ dịch trung bình: $\le 30\text{ ms / chương}$ (nhanh gấp 10-50 lần so với trước).
- Bản dịch chuẩn phong vị tiên hiệp cổ điển, zero từ hiện đại, text mapping chuẩn 100%.
