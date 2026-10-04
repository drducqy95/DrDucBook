# Phase 06: Book Translation URL Normalization & Pinyin Repair (52shuku.net)

Status: ⬜ Pending
Dependencies: None

## Objective
Kiểm tra và xử lý triệt để các lỗi liên quan đến trường hợp truyện từ `52shuku.net`:
```json
{Https://www.52shuku.net/yanqing/03_b/bjYOd.html",
  "totalChapterNum": 575,
  "type": 8,
  "wordCount": "消费任务已完成，触发2倍返利，星雨华府1号别... (所属栏目：言情小说 获赞：1905 )"
}
```
1. **Lỗi định dạng URL / Scheme**: URL có thể chứa ký tự thừa `{`, `"`, hoặc viết hoa `Https://` khiến các bộ parser URL hoặc matcher không nhận diện được.
2. **Lỗi `wordCount` bất thường**: Nguồn truyện lấy nhầm đoạn tóm tắt văn bản ("消费任务已完成...") vào trường `wordCount`, gây lag khi dịch động giao diện sách (`TranslateDynamicBookUiUseCase`).
3. **Lỗi dịch tên riêng Pinyin thay vì Hán-Việt**:
   - Như hiển thị trong ảnh của người dùng (`media_1791102541464.png`), nhân vật chính `云子衿` bị dịch thành `Yun Zijin` (Pinyin), `秦思桐` thành `Qin Sitong`, trong khi tiêu đề và chuẩn dịch truyện tiếng Việt yêu cầu Hán-Việt (`Vân Tử Khâm`, `Tần Tư Đồng`).
   - Prompt dịch thuật AI (`media_1791102556244.png`) thiếu chỉ dẫn bắt buộc phiên âm tên riêng Trung Quốc sang Hán-Việt chuẩn cho độc giả Việt Nam.

## Requirements

### Functional
- [ ] **Bộ chuẩn hóa URL sách (`BookUrlNormalizer`)**:
  - Tự động làm sạch các URL đầu vào: loại bỏ dấu ngoặc nhọn đầu/cuối, dấu ngoặc kép, chuẩn hóa scheme `https://` và `http://` thành chữ thường.
  - Sử dụng trong `Book.kt`, `SearchBook.kt`, `WebBook.kt`, và `NetworkUtils.kt`.
- [ ] **Làm sạch `wordCount` & metadata sách**:
  - Trong `TranslateDynamicBookUiUseCase`: Kiểm tra nếu `wordCount` dài quá 30 ký tự hoặc chứa cấu trúc đoạn văn bản giới thiệu/chương, tự động cắt tỉa hoặc không gửi vào batch dịch UI để tránh nghẽn luồng dịch.
  - Trong `Book.getDisplayWordCount()`: Hiển thị gọn gàng, loại bỏ các đoạn giới thiệu bị map nhầm vào `wordCount`.
- [ ] **Sửa lỗi tên Pinyin trong Dịch AI & QT**:
  - Bổ sung chỉ thị chuẩn hóa Hán-Việt trong system prompt dịch thuật AI (`aiPromptPresetGateway` / `TranslateChapterUseCase`):
    - *"QUY TẮC BẮT BUỘC VỀ TÊN RIÊNG: Mọi tên nhân vật, địa danh, thế lực tiếng Trung phải được chuyển ngữ sang âm HÁN-VIỆT chuẩn (Ví dụ: 云子衿 -> Vân Tử Khâm, KHÔNG ĐƯỢC dịch thành Pinyin như Yun Zijin; 秦思桐 -> Tần Tư Đồng, KHÔNG ĐƯỢC dịch thành Qin Sitong)."*
    - **Áp dụng bắt buộc cho TẤT CẢ các luồng dịch sang tiếng Việt**: Tiêm chỉ dẫn này vào cả Prompt Preset mặc định lẫn Custom Prompt của người dùng trong `TranslateChapterUseCase` (thông qua `AiTranslationRefinePipeline.buildSystemPrompt`), đảm bảo người dùng có bật custom prompt hay không thì quy tắc Hán-Việt vẫn được thực thi nghiêm ngặt.
  - Trong `QuickTranslationRepository` & `QuickDictionarySelectionResolver`: Ưu tiên bảng từ điển Hán-Việt cho các thực thể tên nhân vật trong Story Memory.

### Non-Functional
- [ ] Không làm suy giảm tốc độ dịch đã tối ưu trong P57-P58.
- [ ] Đảm bảo chất lượng bản dịch tự nhiên và đúng phong văn tiểu thuyết tiếng Việt.

## Implementation Steps
1. [ ] Tạo `io/legado/app/utils/UrlSanitizer.kt`:
   - Hàm `sanitizeBookUrl(url: String): String`.
2. [ ] Cập nhật `TranslateDynamicBookUiUseCase.kt`:
   - Thêm bộ lọc an toàn cho `wordCount` (bỏ qua nếu dài > 40 ký tự hoặc chứa cấu trúc đoạn tóm tắt).
3. [ ] Cập nhật `Book.getDisplayWordCount()`:
   - Làm sạch hoặc trả về rỗng nếu `wordCount` bị nhồi nội dung tóm tắt văn bản.
4. [ ] Cập nhật System Prompt cho AI Translation trong `TranslateChapterUseCase.kt` & `AiTranslationRefinePipeline`:
   - Tiêm quy tắc bắt buộc dùng âm Hán-Việt cho tên người/địa danh vào prompt hệ thống.
5. [ ] Viết unit test cho `UrlSanitizerTest` và `TranslateDynamicBookUiUseCaseTest`.

## Files to Create/Modify
- `app/src/main/java/io/legado/app/utils/UrlSanitizer.kt`
- `app/src/main/java/io/legado/app/domain/usecase/TranslateDynamicBookUiUseCase.kt`
- `app/src/main/java/io/legado/app/domain/usecase/TranslateChapterUseCase.kt`
- `app/src/main/java/io/legado/app/data/repository/QuickTranslationRepository.kt`
- `app/src/test/java/io/legado/app/utils/UrlSanitizerTest.kt`

## Test Criteria
- [ ] URL dạng `{Https://www.52shuku.net/yanqing/03_b/bjYOd.html"` được làm sạch thành `https://www.52shuku.net/yanqing/03_b/bjYOd.html`.
- [ ] `wordCount` có nội dung dài được lọc sạch, không gây lag UI.
- [ ] Thử nghiệm dịch đoạn văn chứa `云子衿` và `秦思桐` -> Sinh ra output `Vân Tử Khâm` và `Tần Tư Đồng`, không còn `Yun Zijin` hay `Qin Sitong`.

---
Next Phase: [Phase 07 - Verification & Testing](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/plans/261004-1530-ux-auth-tools-link-sync-fix/phase-07-verification-and-testing.md)
