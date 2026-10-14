# Phase 04: 10-Chapter Benchmark, Quality/Speed Audit & LDPlayer Deployment

Status: ⬜ PENDING
Dependencies: Phase 01, Phase 02, Phase 03

## Objective
Chạy lại toàn bộ bài benchmark 10 chương thực tế của tác phẩm `数据修仙，我在五行观证长生` sau khi đã xóa bỏ hạ tầng xưng hô, xóa QT Clean và chuyển sang thuần túy từ điển .txt với thuật toán FMM. Đo lường tốc độ, đánh giá chất lượng bản dịch, verify tính năng tra cứu từ điển trên Reader và deploy APK lên LDPlayer.

## Tasks
1. [ ] Chạy lại test `testTranslateAndBenchmark10RealChapters` trên JVM:
   - Đo thời gian dịch 10 chương với thuật toán QT gốc FMM và từ điển thuần `.txt`.
   - Lập bảng đối chiếu thời gian và tốc độ trước và sau tối ưu (kỳ vọng tổng thời gian 10 chương giảm từ 8.5s xuống $\le 0.5\text{s}$, tức $\le 30\text{ ms/chương}$).
2. [ ] Phân tích đánh giá chất lượng bản dịch 10 chương (Diff Audit):
   - Đảm bảo 100% sạch bóng: `tôi`, `bạn`, `Quốc khánh Trung Quốc`, `tu hành phát triển tâm linh`, `1 tệ`, `Hội An`, `nửa 0 phút tâm`, `bao nhiêu tuổi? Kỹ năng`.
   - Đảm bảo các từ tu tiên dịch tự nhiên chuẩn xác: `tụ khí đan`, `tông môn`, `sơn môn`, `phường thị`, `tu hành`, `mười một mười hai tuổi`.
3. [ ] Kiểm tra Text Mapping:
   - Viết unit test xác minh `translateMapped`: đảm bảo ánh xạ `segment.source` $\leftrightarrow$ `segment.text` khớp tuyệt đối từng vị trí.
4. [ ] Build APK debug x86_64 và cài đặt lên thiết bị LDPlayer.
5. [ ] Mở Reader trên LDPlayer, lật trang qua các chương 1, 2, 3, 5, 6, 10, thử nghiệm chạm tra từ điển trên các từ vựng và chụp ảnh màn hình nghiệm thu.
6. [ ] Cập nhật `project_progress.json`, `.brain/session.json`, hoàn thành Milestone P79.

## Test Criteria
- Build APK thành công 100%.
- Tốc độ dịch trung bình: $\le 30\text{ ms / chương}$ (nhanh gấp 10-50 lần).
- Bản dịch chuẩn phong vị tiên hiệp cổ điển, zero từ hiện đại, text mapping chuẩn 100%.
