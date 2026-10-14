# Phase 02: Cải Tiến Ngữ Pháp Câu Chữ Đem/Tương (`将/把`), Trợ Từ `着`, và Đại Từ Xưng Hô

Status: ⬜ Pending
Dependencies: phase-01-benchmarking-and-lexicon-cleanup

## 1. Mục Tiêu
1. **Khắc phục câu chữ Đem/Tương (`将` / `把` + O + V):**
   - Khi `将` hoặc `把` đứng trước cụm danh từ tân ngữ và theo sau là động từ (câu xử trí): Dịch là `đem` (hoặc `lấy`), tuyệt đối KHÔNG dịch thành `sẽ`.
   - Ví dụ:
     * `一刀将仇人的喉咙斩开` -> `một đao đem cổ họng của cừu nhân chém ra` (thay vì "một đao sẽ cổ họng của cừu nhân chém ra").
     * `将身形隐匿` -> `đem thân hình ẩn nấp` (thay vì "sẽ thân hình núp ở...").
     * `将相貌很好的隐藏` -> `đem tướng mạo che giấu rất tốt` (thay vì "sẽ tướng mạo rất tốt che giấu").
     * `将手中的长刀甩出` -> `đem trường đao trong tay ném ra` (thay vì "sẽ trường đao trong tay ném ra").
2. **Khắc phục trợ từ thái động `着` / `著`:**
   - Khi sau động từ có `着`: không được dịch chèn chữ "đang" chen ngang giữa động từ và tân ngữ (`tiêu xài đang thời gian` -> `vung phí thời gian` / `tiêu xài thời gian`).
3. **Triệt tiêu dứt điểm xưng hô "bạn/tôi" trong câu ngắn:**
   - Trong `QuickTranslationRepository` và `VietnameseTranslationPostProcessor`: đảm bảo chế độ xưng hô convert chuẩn mặc định luôn ánh xạ `你` -> `ngươi`, `我` -> `ta`, `他` -> `hắn`, `她` -> `nàng`, ngay cả khi xử lý các câu đơn lẻ không có hội thoại trước đó.
   - Sửa lỗi fail tại assertion line 143 của `LuanHoiNhacVienChapter1BenchmarkTest`.

## 2. Nhiệm Vụ Cụ Thể
1. [ ] Thêm Rule nhận diện câu chữ Đem/Tương trong `QuickTranslationRepository.kt` hoặc `VietnameseTranslationPostProcessor.kt`.
2. [ ] Bổ sung template grammar `QT_TEMPLATE` hoặc logic nhận diện trong `postProcessToken`.
3. [ ] Xử lý trật tự `[Động từ] + 着 + [Tân ngữ]` để loại bỏ "đang" chen ngang.
4. [ ] Củng cố bộ xưng hô mặc định trong `resolvedPronounMode` để không bao giờ fallback về "bạn".

## 3. Tiêu Chí Nghiệm Thu
- [ ] Không còn bất kỳ cụm từ "sẽ [tân ngữ] chém/núp/ném/che giấu" nào xuất hiện trong 10 chương test.
- [ ] Test `testTextSelectionMappingAndDictionaryAddition` pass 100% không còn dính từ "bạn".
