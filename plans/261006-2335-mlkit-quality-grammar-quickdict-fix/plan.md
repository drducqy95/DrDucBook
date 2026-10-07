# Milestone P63: ML Kit Translation Quality, Vietnamese Grammar Healing, Quick Dictionary Mapping & Anime/Novel Jargon Normalization

**Date:** 2026-10-06  
**Status:** PLANNING  
**Target:** `com.drducbook.app.debug` (Device: LDPlayer `emulator-5554`)  
**Context:** Chapter 2 *Luân Hồi Lạc Viên* (轮回乐园) & Reader Quick Dictionary

---

## 1. Executive Summary & Problem Analysis

Qua quá trình kiểm tra thực tế trên thiết bị debug LDPlayer (`emulator-5554`) tại Chương 2 *Luân Hồi Lạc Viên* (pages 9/13 & 10/13), các vấn đề cốt lõi về bản dịch Google ML Kit và Quick Dictionary đã được trích xuất và phân tích chi tiết:

### A. Vấn đề Ngữ pháp & Cú pháp Dịch Sượng (English-Pivot Syntactic Calque)
1. **Đảo ngữ vị từ và trạng ngữ cuối câu:**
   - *Thực tế:* *"tàn sát một đất nước một mình"* $\leftarrow$ dịch từ *"slaughter a country alone"*.
   - *Tiếng Việt chuẩn:* *"một mình tàn sát cả một quốc gia / đất nước"*.
2. **Cấu trúc tính từ + to infinitive ngô nghê:**
   - *Thực tế:* *"rất đơn giản để làm"* $\leftarrow$ dịch từ *"very simple to do"*.
   - *Tiếng Việt chuẩn:* *"rất dễ thực hiện / thao tác vô cùng đơn giản"*.
3. **Thừa từ vị ngữ và cấu trúc bệnh hoán:**
   - *Thực tế:* *"Điều này là rất quan trọng"* $\leftarrow$ *"This is very important"*.
   - *Tiếng Việt chuẩn:* *"Điều này rất quan trọng"*.
   - *Thực tế:* *"có một số đau đầu"* $\leftarrow$ *"had some headache"*.
   - *Tiếng Việt chuẩn:* *"hơi đau đầu / đang có chút đau đầu"*.
4. **Cấu trúc danh từ mơ hồ và câu bị động thô:**
   - *Thực tế:* *"khác tạm thời không thể được sử dụng"* $\leftarrow$ *"others temporarily cannot be used"*.
   - *Tiếng Việt chuẩn:* *"những mục khác tạm thời chưa thể sử dụng"*.
5. **Đại từ trần thuật và ngôi kể bị lệch (Pronoun Drift):**
   - *Thực tế:* Giữa đoạn trần thuật ngôi thứ ba của Tô Hiểu lại nhảy sang *"bạn có thể đoán"*, *"trước mặt bạn"*.
   - *Tiếng Việt chuẩn:* *"có thể đoán được"*, *"trước mắt hắn / trước mắt anh"*.
6. **Lỗi viết hoa bất thường giữa câu sau tên riêng:**
   - *Thực tế:* *"Tô Hiểu Cố gắng kích hoạt..."*, *"Tô Hiểu Bây giờ ngoại trừ..."*.
   - *Nguyên nhân:* Do NMT dịch tên riêng sang tiếng Anh (hoặc token boundary) viết hoa từ kế tiếp, khi dịch sang tiếng Việt giữ nguyên Titlecase.
   - *Tiếng Việt chuẩn:* *"Tô Hiểu cố gắng..."*, *"Tô Hiểu bây giờ..."*.

### B. Vấn đề Thuật ngữ Hệ thống / Novel / Game bị Dịch Nghĩa Đen
- `哥亚王国` (Vương quốc Goa) $\rightarrow$ Bị sót nguyên chuỗi tiếng Anh: `Kingdom of the Koa King`.
- `储物空间` (không gian trữ vật / kho đồ) $\rightarrow$ Bị dịch nhầm thành *"không gian tiết kiệm"* (hiểu nhầm *save space*).
- `烙印` (lạc ấn Luân Hồi) $\rightarrow$ Bị dịch thành *"thương hiệu"* (hiểu nhầm *brand*).
- `阶位` (giai vị / đẳng cấp / cấp bậc) $\rightarrow$ Bị dịch thành *"Lớp"* (hiểu nhầm *class/classroom*).
- `难度` (độ khó thế giới) $\rightarrow$ Bị dịch thành *"khó khăn"* (*"Từ khó khăn của thế giới..."*).
- `未解锁` (chưa mở khóa) $\rightarrow$ NMT dịch nhầm thành *"được mở khóa"* do mất tiền tố phủ định.

### C. Vấn đề Quick Dictionary trên Bản Dịch ML Kit
- `ReaderContentMode.supportsQuickDictionaryEditing()` đang trả về `false` đối với `ML_KIT`.
- Khi người dùng bôi đen từ trên bản dịch ML Kit, `resolution.anchor` bị `null`, không fallback sang `resolution.alternatives.firstOrNull()`, khiến ô `Raw` trong dialog Quick Dictionary bị rỗng.
- Cần cơ chế tạo N-gram candidate tự động tại vị trí xấp xỉ (`searchWindow.approximatePosition`) và tra cứu ngược (Reverse lookup) từ Latin/Việt về CJK.

---

## 2. Detailed Task Breakdown

### Task T63.1: Sửa lỗi Mapping Quick Dictionary & Không để ô Raw rỗng
- **Mục tiêu:** Mở rộng Quick Dictionary hoạt động trên tất cả các chế độ dịch (ML Kit, Translation, AI...) và đảm bảo ô Raw luôn có nội dung khi bôi đen từ.
- **File tác động:**
  - `app/src/main/java/io/legado/app/domain/model/ReaderContentMode.kt`:
    - Cập nhật `supportsQuickDictionaryEditing()` trả về `true` cho `ML_KIT`, `TRANSLATION`, `AI`, `LOCAL_AI`, `NMT`, `GOOGLE`, `QUICK_TRANSLATOR`.
  - `app/src/main/java/io/legado/app/ui/quickdict/QuickDictionarySelectionResolver.kt`:
    - Khi `alternatives` rỗng trong ngữ cảnh dịch (`source != display`), tự động tạo ứng viên N-gram (2..4 ký tự) quanh vị trí xấp xỉ `searchWindow.approximatePosition`.
    - Bổ sung cơ chế tra cứu ngược (Reverse lookup): Khi bôi đen từ tiếng Việt/Latin (như *Uchiha*, *Madara*, *Tô Hiểu*, *Luân Hồi Lạc Viên*), tra ngược qua danh sách tên riêng hoặc từ điển QT để tìm từ CJK gốc.
  - `app/src/main/java/io/legado/app/ui/book/read/ReadBookViewModel.kt`:
    - Đổi `val anchor = resolution.anchor` thành `val anchor = resolution.anchor ?: resolution.alternatives.firstOrNull()`.
    - Đưa `SinoForeignNameDetector` vào `candidateTranslator` trong quá trình giải quyết lựa chọn.

### Task T63.2: Chuẩn hóa Tên riêng Tiếng Nhật (Romaji), Tiếng Hàn (Latin) & Anime/Fantasy
- **Mục tiêu:** Đảm bảo khi dịch hoặc đề xuất từ điển, tên riêng tiếng Nhật chuyển thành Romaji chuẩn (Hepburn title-case) như *Uchiha Madara*, *Uzumaki Naruto*, tên tiếng Hàn chuyển thành Latin, không để sót Kana/Hangul hay Pinyin biến dạng.
- **File tác động:**
  - `app/src/main/java/io/legado/app/domain/model/SinoForeignNameDetector.kt`:
    - Bổ sung từ điển thực thể phổ biến trong Anime, Manga, Light Novel:
      - `宇智波` $\rightarrow$ *Uchiha*, `宇智波斑` $\rightarrow$ *Uchiha Madara*, `宇智波佐助` $\rightarrow$ *Uchiha Sasuke*
      - `漩涡鸣人` $\rightarrow$ *Uzumaki Naruto*, `木叶` $\rightarrow$ *Konoha*, `写轮眼` $\rightarrow$ *Sharingan*, `白眼` $\rightarrow$ *Byakugan*, `轮回眼` $\rightarrow$ *Rinnegan*
      - `哥亚王国` $\rightarrow$ *Vương quốc Goa*, `海贼王` $\rightarrow$ *One Piece*, `恶魔果实` $\rightarrow$ *Trái Ác Quỷ*, `霸气` $\rightarrow$ *Bá Khí*
  - `app/src/main/java/io/legado/app/domain/model/ForeignScriptRomanizer.kt`:
    - Đảm bảo các hàm `JapaneseKanaRomajizer.toRomaji` và `KoreanHangulRomanizer.toLatin` được gọi tự động dọn dẹp các ký tự Kana/Hangul còn sót lại trong đầu ra bản dịch.

### Task T63.3: Mở rộng Thuật ngữ Hệ thống / Game / Novel trong `MlKitDictionaryEnforcer`
- **Mục tiêu:** Xử lý triệt để các lỗi dịch nghĩa đen pivot tiếng Anh của tiểu thuyết vô hạn lưu/hệ thống/khoa huyễn.
- **File tác động:**
  - `app/src/main/java/io/legado/app/domain/model/MlKitDictionaryEnforcer.kt`:
    - Mở rộng `KNOWN_PIVOT_REPLACEMENTS`:
      - `储物空间` / `储物戒` / `储物袋` $\rightarrow$ *không gian trữ vật* / *nhẫn trữ vật* / *túi trữ vật* (thay cho *"không gian tiết kiệm"*).
      - `烙印` $\rightarrow$ *lạc ấn* (thay cho *"thương hiệu"*).
      - `阶位` / `阶` $\rightarrow$ *đẳng cấp / giai vị / cấp bậc* (thay cho *"lớp / lớp học"*).
      - `难度` $\rightarrow$ *độ khó* (thay cho *"khó khăn"* trong ngữ cảnh thế giới).
      - `未解锁` $\rightarrow$ *chưa mở khóa* (thay cho *"được mở khóa"*).
      - `哥亚王国` $\rightarrow$ *Vương quốc Goa* (thay cho *"Kingdom of the Koa King"*).
      - `暗杀` / `刺杀` $\rightarrow$ *ám sát*.
      - `衍生世界` $\rightarrow$ *thế giới phái sinh*.
      - `属性` $\rightarrow$ *thuộc tính*.
      - `契约者` $\rightarrow$ *Khế ước giả*.
      - `猎杀者` $\rightarrow$ *Liệp sát giả*.
    - Mở rộng hàm `resolveSimplePinyin` bổ sung các tên nhân vật và địa danh.

### Task T63.4: Bộ Xử Lý Cải Thiện Ngữ Pháp Hậu Dịch ML Kit (`MlKitGrammarPostProcessor`)
- **Mục tiêu:** Tạo bộ lọc tinh chỉnh ngữ pháp tiếng Việt tự nhiên và trật tự từ cho bản dịch ML Kit.
- **File tác động:**
  - `app/src/main/java/io/legado/app/domain/model/MlKitGrammarPostProcessor.kt` (Tạo mới):
    1. **Sửa cấu trúc đảo trạng từ cuối câu:**
       - `... [động từ hành động] [tân ngữ] một mình` $\rightarrow$ `một mình [động từ hành động] [tân ngữ]` (ví dụ: *"tàn sát một đất nước một mình"* $\rightarrow$ *"một mình tàn sát một đất nước"*).
    2. **Sửa cấu trúc tính từ + động từ tiếng Anh:**
       - `rất đơn giản để làm` $\rightarrow$ `rất dễ thực hiện / thao tác vô cùng đơn giản`.
       - `Điều này là rất quan trọng` $\rightarrow$ `Điều này rất quan trọng`.
       - `có một số đau đầu` / `có một chút đau đầu` $\rightarrow$ `hơi đau đầu / đầu có chút nhức nhối`.
       - `khác tạm thời không thể được sử dụng` $\rightarrow$ `các mục khác tạm thời chưa thể sử dụng`.
    3. **Khử đại từ ngôi thứ 2 "bạn" lệch văn phong trần thuật:**
       - `trước mặt bạn` $\rightarrow$ `trước mắt hắn / trước mắt anh`.
       - `bạn có thể đoán` $\rightarrow$ `có thể đoán được`.
    4. **Sửa chữ hoa bất thường sau tên riêng:**
       - Quét các cụm `[Tên riêng viết hoa] [Từ chức năng/Động từ viết hoa]` (như *Cố gắng*, *Bây giờ*, *Đang*, *Đã*, *Sẽ*, *Có*...) và chuyển từ thứ hai về chữ thường (ví dụ: *Tô Hiểu Cố gắng* $\rightarrow$ *Tô Hiểu cố gắng*, *Tô Hiểu Bây giờ* $\rightarrow$ *Tô Hiểu bây giờ*).
    5. **Tích hợp với `VietnameseTranslationPostProcessor`:**
       - Đảm bảo đầu câu luôn viết hoa chuẩn mực sau dấu chấm, dấu chấm than, hỏi chấm.

### Task T63.5: Tích Hợp Toàn Diện Pipeline & Cơ Chế Fallback An Toàn
- **Mục tiêu:** Nối các bước pre-masking, translation, unmasking, dictionary enforcement, grammar healing và pronoun neutralization vào `TranslateChapterUseCase.translateMlKitParagraph`.
- **File tác động:**
  - `app/src/main/java/io/legado/app/domain/usecase/TranslateChapterUseCase.kt`:
    - Áp dụng `MlKitGrammarPostProcessor.healGrammar(...)` sau bước giải mã entity mask và enforce dictionary.
    - Áp dụng `VietnameseTranslationPostProcessor.capitalizeSentences(...)`.
    - Bảo đảm cơ chế an toàn: Nếu ML Kit gặp timeout/lỗi/trả về rỗng, tự động fallback sang Quick Translator (Hán-Việt + từ điển QT) mà không làm gián đoạn việc đọc sách.

### Task T63.6: Kiểm Thử Tự Động, Build APK & Xác Minh Trực Quan Trên LDPlayer
- **Mục tiêu:** Kiểm tra không có lỗi hồi quy (regression) và kiểm chứng chất lượng câu chữ thực tế trên màn hình emulator.
- **Các bước thực hiện:**
  - Viết và chạy Unit test:
    - `:app:testAppDebugUnitTest --tests "io.legado.app.domain.model.MlKitDictionaryEnforcerTest"`
    - `:app:testAppDebugUnitTest --tests "io.legado.app.domain.model.MlKitGrammarPostProcessorTest"`
    - `:app:testAppDebugUnitTest --tests "io.legado.app.ui.quickdict.QuickDictionarySelectionResolverTest"`
  - Chạy biên dịch nhanh: `.\gradlew.bat :app:compileAppDebugKotlin`.
  - Build APK: `.\gradlew.bat :app:assembleAppDebug`.
  - Cài đặt APK lên LDPlayer (`emulator-5554`) qua ADB.
  - Mở lại Chương 2 *Luân Hồi Lạc Viên*, bấm "Dịch lại chương" (Nguồn: Tiếng Trung, Đích: Tiếng Việt).
  - Chụp ảnh màn hình LDPlayer kiểm tra:
    - Câu chữ đã mượt mà, không còn *"tàn sát một đất nước một mình"*, *"rất đơn giản để làm"*, *"không gian tiết kiệm"*, *"thương hiệu"*, *"Kingdom of the Koa King"*.
    - Bôi đen từ trên màn hình để kiểm tra Quick Dictionary: ô Raw chứa đúng chữ Hán gốc, không bị rỗng.

---

## 3. Timeline & Risk Mitigation

| Rủi ro | Giải pháp phòng ngừa |
|---|---|
| **Android ICU Lookbehind Error** (ERR_0046) | Tuyệt đối không dùng lookbehind biến thiên độ dài `(?<=\s+)`. Dùng single char `(?<=\s)` hoặc quét token thủ công. |
| **Hiệu năng Regex trên đoạn dài** (ERR_0040) | Dùng cờ kiểm tra nhanh `.contains()` trước khi chạy regex (fast-path filter). |
| **Trùng lặp / Ghi đè thực thể** | Cơ chế Masking `__ENT_0__` bảo vệ các tên riêng đã dịch chuẩn trước khi đưa qua mô hình NMT. |
