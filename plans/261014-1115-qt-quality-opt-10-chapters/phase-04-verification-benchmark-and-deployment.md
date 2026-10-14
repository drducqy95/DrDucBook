# Phase 04: 10-Chapter Benchmark, Quality Audit & LDPlayer Deployment

Status: ⬜ PENDING
Dependencies: Phase 01, Phase 02, Phase 03

## Objective
Chạy lại toàn bộ bài benchmark 10 chương thực tế, đối chiếu chất lượng bản dịch (diff trước và sau), đảm bảo không hồi quy hiệu năng (tốc độ $\le 300\text{ ms/chương}$), compile APK và deploy xác minh thực tế trên LDPlayer Reader.

## Tasks
1. [ ] Chạy lại test `testTranslateAndBenchmark10RealChapters` trên JVM.
2. [ ] Phân tích so sánh diff 10 chương giữa output cũ và output mới.
3. [ ] Kiểm tra số lượng Hanzi chưa dịch (phải bằng 0).
4. [ ] Build APK debug x86_64 và cài đặt lên LDPlayer.
5. [ ] Mở Reader trên LDPlayer, chuyển qua các chương 1, 2, 3, 5, 6, 10 và chụp màn hình xác minh.
6. [ ] Cập nhật `project_progress.json`, `.brain/session.json`, và hoàn tất Milestone P79.

## Files to Modify
- `app/src/test/java/io/legado/app/data/repository/QuickTranslationRepositoryTest.kt`
- `project_progress.json`
- `.brain/session.json`

## Test Criteria
- Build APK thành công 100%.
- Tốc độ dịch trung bình duy trì $\ge 3,500\text{ chars/sec}$.
- Màn hình LDPlayer hiển thị bản dịch mượt mà, chuẩn văn phong Hán Việt tiên hiệp.
