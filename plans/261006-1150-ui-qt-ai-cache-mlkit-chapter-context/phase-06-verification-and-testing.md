# Phase 06: Kiểm Thử Tự Động, Biên Dịch & Xác Thực Toàn Diện Trên Thiết Bị

Status: ✅ Complete
Dependencies: `phase-01` đến `phase-05`

## 1. Mục tiêu (Objective)
1. Viết bộ Unit Test hoàn chỉnh kiểm thử tất cả các usecase, model, repository, và viewmodel mới được bổ sung.
2. Kiểm tra biên dịch mã nguồn Kotlin (`compileAppDebugKotlin`).
3. Đóng gói APK Debug (`assembleAppDebug`).
4. Cài đặt và xác thực thực tế trên thiết bị giả lập LDPlayer (`emulator-5554` / `127.0.0.1:5555`).

## 2. Kế hoạch Kiểm Thử (Test Suites)

### 2.1. Chi Tiết Các Bộ Unit Test (Khắc phục Gap #11)
1. **Dynamic UI Translation & AI Cache Precedence Test (`TranslateDynamicUiTextUseCaseTest`):**
   - Kiểm tra khi chưa có AI cache: `executeChapterTitle` và `executeChapterTitles` dịch 100% bằng QT.
   - Kiểm tra khi có `PROVIDER_APP_AI` cache: trả về ngay bản dịch AI mà không gọi QT.
   - Kiểm tra khi có `PROVIDER_LOCAL_AI` cache: trả về bản dịch Local AI thay thế QT.
   - Kiểm tra batch hỗn hợp: kết hợp chính xác các chương có cache AI và chưa có cache AI theo đúng thứ tự ban đầu.
   - Kiểm tra `executeBookName` và `executeAuthorName` giữ Title Case và tái cấu trúc số chương.
2. **Ebook Export Data Test (`ExportBookServiceTest`):**
   - Xuất sách bản dịch (`ContentSource.Translation`): Tiêu đề từng chương và metadata (tên sách, tác giả, mô tả) được điền bằng bản dịch tiếng Việt thay vì raw CJK.
   - Edge case: Sách hoặc chương không chứa CJK (đã là tiếng Việt/Anh) thì giữ nguyên, không gọi translator dư thừa.
   - Edge case: Lỗi dịch lẻ tẻ ở một chương không làm hỏng cả tiến trình xuất.
3. **ML Kit Chapter Translation Pipeline Test (Khắc phục Gap #11):**
   - `MlKitPronounNeutralizerTest`:
     - Chế độ `ANCIENT`: "Anh ấy nhìn cô ấy và nói với họ" -> "Hắn nhìn nàng và nói với bọn họ".
     - Chế độ `MODERN`: "Anh ấy nhìn cô ấy" -> "Hắn nhìn cô".
     - Chế độ `WESTERN`: "Anh ấy nhìn cô ấy" -> "Chàng nhìn nàng".
     - Chế độ `OFF`: Giữ nguyên đại từ của ML Kit.
     - Kiểm tra bảo toàn câu thoại trong ngoặc kép hoặc gạch đầu dòng.
   - `MlKitDictionaryEnforcerTest`:
     - Kiểm tra Greedy Longest Match: Thuật ngữ dài (`漩涡鸣人` -> `Uzumaki Naruto`) được ưu tiên trước thuật ngữ ngắn (`鸣人` -> `Naruto`).
     - Kiểm tra Story Translation Memory: Thực thể có alias cũ được thay thế bằng canonical target.
     - Khi từ điển rỗng: Trả về bản dịch gốc mà không phát sinh lỗi.
4. **ML Kit Multilingual Suggestions Test (`MlKitSuggestionTest`):**
   - Kiểm tra `executeSuggestion` với `zh -> ja`: Trả về kết quả tiếng Nhật, không chạy bộ lọc tiếng Việt.
   - Kiểm tra `executeSuggestion` với `zh -> en`: Trả về kết quả tiếng Anh (ví dụ "Caesar" cho "凯撒").
   - Kiểm tra `executeSuggestion` với `zh -> ko`: Trả về kết quả tiếng Hàn.
   - Khi thiếu model: Trả về `MlKitMissingLanguageModelException` thông báo rõ mã ngôn ngữ cần tải.
5. **Chapter Context Copy Test (`ChapterContextCopyTest`):**
   - Kiểm tra `listProviderCachesForChapter` trả về danh sách các provider cache hiện có trên đĩa.
   - Kiểm tra sao chép an toàn khi văn bản vượt quá 100,000 ký tự.

### 2.2. Build & Static Analysis
Chạy lệnh kiểm tra biên dịch và thực thi unit tests:
```powershell
.\gradlew.bat :app:compileAppDebugKotlin
.\gradlew.bat test --tests "io.legado.app.domain.usecase.TranslateDynamicUiTextUseCaseTest"
.\gradlew.bat test --tests "io.legado.app.domain.model.MlKitPronounNeutralizerTest"
.\gradlew.bat test --tests "io.legado.app.domain.usecase.MlKitDictionaryEnforcerTest"
.\gradlew.bat test --tests "io.legado.app.domain.usecase.MlKitSuggestionTest"
.\gradlew.bat assembleAppDebug
```

### 2.3. On-Device Verification (LDPlayer emulator-5554)
- Cài đặt APK:
  ```powershell
  adb -s emulator-5554 install -r app/build/outputs/apk/app/debug/app-app-universal-debug.apk
  ```
- Khởi chạy và kiểm thử thực tế trên giao diện:
  1. Mở một cuốn sách đã có bản dịch AI: Kiểm tra Mục lục TOC xem tiêu đề chương có hiển thị bản dịch AI thay thế cho QT không.
  2. Xuất Ebook dạng Dịch (EPUB): Mở file EPUB xuất ra kiểm tra tiêu đề chương và metadata đã là tiếng Việt chưa.
  3. Dịch 1 chương bằng ML Kit: Kiểm tra từ điển bộ nhớ dịch + từ điển QT dự án được áp dụng, và xưng hô chuyển sang trung tính (hắn/nàng/bọn họ).
  4. Mở bôi đen từ vựng (ví dụ "漩涡鸣人" hoặc "凯撒") -> Bấm "Thêm từ điển QT" -> Chọn chip "Trung - Nhật" / "Trung - Anh" -> Kiểm tra đề xuất ML Kit tự động điền vào ô target.
  5. Mở `TranslationProgressSheet` -> Bấm "Sao chép ngữ cảnh chương" -> Thử sao chép bản Raw và bản Dịch AI -> Dán vào một ứng dụng khác (hoặc app note) để xác thực Clipboard.

## 3. Deliverables
- Toàn bộ test cases pass 100%.
- APK debug build thành công, không có cảnh báo compile lỗi hay lint blocker.
- Báo cáo kết quả trực quan trên LDPlayer.
