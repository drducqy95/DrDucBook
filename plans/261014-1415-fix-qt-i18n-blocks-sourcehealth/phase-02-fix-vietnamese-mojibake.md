# Phase 02: Sửa Lỗi Tiếng Việt Mojibake, Chuẩn Hóa Unicode & Hoàn Thiện i18n Toàn Diện



> **Thuộc kế hoạch:** [`plan.md`](./plan.md)  

> **Phase ID:** `phase-02`  

> **Trạng thái:** ⬜ Pending  

> **Phụ thuộc:** Phase 01



---



## 1. Mục Tiêu (Objectives)

1. **Khử triệt để lỗi hiển thị tiếng Việt Mojibake:**

   - Xóa bỏ hiện tượng xuất hiện dấu hỏi `?` vô lý nằm giữa tên riêng phiên âm nước ngoài (ví dụ: `Noah? Pat`, `Harry? Potter`), ký tự lỗi giải mã `` (`\uFFFD`), ký tự điều khiển ẩn, hoặc dấu thanh tiếng Việt bị tách rời (NFD) trên các thiết bị Huawei/HONOR.

2. **Hoàn thiện Việt hóa (i18n Parity) 100% trên toàn bộ giao diện:**

   - Loại bỏ toàn bộ các vị trí hiển thị trực tiếp mã `Enum.name` tiếng Anh (`HEALTHY`, `BROKEN_RULE`, `AUTH_REQUIRED`, `CAPTCHA_REQUIRED`, `RATE_LIMITED`, `reachability`, `toc`, `QUICK`, `STANDARD`, `FULL`...) trên `SourceHealthScreen`, `DownloadCenterScreen`, `ReadAloudConfigSheet`.

   - Đồng bộ tuyệt đối khóa tài nguyên giữa `app/src/main/res/values/strings.xml` và `app/src/main/res/values-vi/strings.xml`.



---



## 2. Phân Tích Nguyên Nhân Gốc Rễ (Root Cause Analysis)



1. **Nguyên nhân lỗi `Noah? Pat` và Mojibake trong nội dung chương:**

   - Trong nguyên tác tiếng Trung, tên nhân vật phương Tây có dấu chấm giữa (`·` — `\u00B7` hoặc `•` — `\u2022`, ví dụ `诺亚·帕特`). Khi trang nguồn dùng bảng mã `GBK`/`GB2312` cũ hoặc chuyển mã lỗi, dấu `·` bị biến thành dấu `?` ASCII (`诺亚?帕特`). Khi đi qua bộ dịch QT hoặc AI, nó biến thành `Noah? Pat` hoặc `Noah ? Pat`.

   - Khi nhận luồng chữ từ một số model AI hoặc đọc file TXT/EPUB ngoài, chuỗi tiếng Việt có thể chứa tổ hợp dấu rời (`Normalizer.Form.NFD`) hoặc lỗi viết hoa giữa chừng (`fixAbnormalMixedCase`), gây lỗi hiển thị trên font tùy chỉnh.

2. **Nguyên nhân lộ mã kỹ thuật thô trên UI:**

   - Trong `SourceHealthScreen.kt`, các thành phần hiển thị trạng thái nguồn (`BookSourceHealthStatus`), bước kiểm tra (`stageKey` trong `SourceCheckStageResult`), và chế độ kiểm tra (`SourceCheckProfile`) chưa có hàm ánh xạ đầy đủ sang `@StringRes` tiếng Việt.



---



## 3. Các Bước Triển Khai Chi Tiết (Implementation Steps)



### Task 02.1: Xử Lý Dứt Điểm Mojibake & Chuẩn Hóa Unicode NFC

- **Files:**

  - `app/src/main/java/io/legado/app/domain/model/VietnameseTranslationPostProcessor.kt`

  - `app/src/main/java/io/legado/app/data/repository/QuickTranslationRepository.kt`

- **Chi tiết thực hiện:**

  1. Ở bước đầu vào (`normalizeRawSource`) của cả QT và AI/MLKit:

     - Chuẩn hóa chuỗi bằng `Normalizer.normalize(input, Normalizer.Form.NFC)`.

     - Chuyển các dấu `?` hoặc `\uFFFD` nằm kẹp trực tiếp giữa hai ký tự chữ Hán (`(?<=[\p{IsHan}])[?\uFFFD]+(?=[\p{IsHan}])`) thành dấu chấm giữa chuẩn `·` trước khi tra từ điển tên riêng, giúp `诺亚?帕特` tự động khớp chính xác với mục từ `诺亚·帕特=Noah Pat`!

  2. Ở bước hậu xử lý (`VietnameseTranslationPostProcessor.cleanRogueNameQuestionMarks`):

     - Tự động làm sạch mẫu `([A-ZÀ-Ỹ][a-zà-ỹ]+)\s*\?\s*([A-ZÀ-Ỹ][a-zà-ỹ]+)` thành `$1 $2` để khử hoàn toàn lỗi `Noah? Pat` nếu còn sót.

     - Loại bỏ toàn bộ ký tự `\uFFFD`, ký tự zero-width (`\u200B`..`\u200D`, `\uFEFF`) và chuẩn hóa lại `NFC` trước khi lưu cache / hiển thị.



### Task 02.2: Bổ Sung & Đồng Bộ Khóa Ngôn Ngữ Trong `strings.xml` và `strings-vi.xml`

- **Files:**

  - `app/src/main/res/values/strings.xml`

  - `app/src/main/res/values-vi/strings.xml`

- **Chi tiết thực hiện:**

  - Bổ sung đầy đủ các khóa sau vào cả 2 tệp (đảm bảo không thiếu khóa nào ở `values/strings.xml` mặc định):

    - **Trạng thái Sức khỏe nguồn (`BookSourceHealthStatus`):**

      - `source_health_status_healthy`: `Khỏe mạnh` / `Healthy`

      - `source_health_status_degraded`: `Suy giảm` / `Degraded`

      - `source_health_status_auth_required`: `Cần đăng nhập` / `Login Required`

      - `source_health_status_captcha_required`: `Cần giải Captcha` / `Captcha Required`

      - `source_health_status_rate_limited`: `Giới hạn tần suất` / `Rate Limited`

      - `source_health_status_broken_rule`: `Lỗi quy tắc` / `Broken Rule`

      - `source_health_status_network_error`: `Lỗi mạng` / `Network Error`

      - `source_health_status_tls_error`: `Lỗi chứng chỉ TLS` / `TLS Error`

      - `source_health_status_content_empty`: `Nội dung trống` / `Empty Content`

      - `source_health_status_http_error`: `Lỗi HTTP` / `HTTP Error`

      - `source_health_status_unknown_offline`: `Ngoại tuyến` / `Offline`

      - `source_health_status_unsupported`: `Không hỗ trợ` / `Unsupported`

    - **Các bước kiểm tra (`SourceCheckStage`):**

      - `source_health_stage_reachability`: `Kết nối cơ sở`

      - `source_health_stage_search`: `Tìm kiếm`

      - `source_health_stage_explore`: `Khám phá`

      - `source_health_stage_detail`: `Thông tin truyện`

      - `source_health_stage_toc`: `Mục lục`

      - `source_health_stage_content`: `Nội dung chương`

      - `source_health_stage_media`: `Đa phương tiện`

      - `source_health_stage_feed`: `Nguồn cấp RSS`

      - `source_health_stage_list`: `Danh sách bài viết`

      - `source_health_stage_article`: `Chi tiết bài viết`

      - `source_health_stage_manifest`: `Cấu hình Plugin`

      - `source_health_stage_track`: `Luồng phát Media`

    - **Chế độ kiểm tra (`SourceCheckProfile`) & Trạng thái lượt chạy (`SourceCheckRunStatus`):**

      - `source_health_profile_quick`: `Kiểm tra nhanh`

      - `source_health_profile_standard`: `Tiêu chuẩn`

      - `source_health_profile_full`: `Toàn diện`

      - `source_health_run_running`: `Đang kiểm tra`

      - `source_health_run_completed`: `Hoàn tất`

      - `source_health_run_failed`: `Thất bại`

      - `source_health_run_canceled`: `Đã hủy`

      - `source_health_run_interrupted`: `Bị gián đoạn`



### Task 02.3: Áp Dụng Mapper Tiếng Việt Lên `SourceHealthScreen.kt` & Các Màn Hình Liên Quan

- **File:** `app/src/main/java/io/legado/app/ui/book/source/health/SourceHealthScreen.kt`

- **Chi tiết thực hiện:**

  - Viết các hàm `@Composable fun BookSourceHealthStatus.localizedLabel(): String`, `fun localizeStageKey(stageKey: String): String`, `fun SourceCheckProfile.localizedLabel(): String` và `fun SourceCheckRunStatus.localizedLabel(): String`.

  - Thay thế toàn bộ các chỗ gọi `.name` hoặc hiển thị trực tiếp `stageKey` / `failureStep` thô bằng các hàm mapper trên.



---



## 4. Danh Sách Tệp Sửa Đổi (Files to Modify)

- `app/src/main/java/io/legado/app/domain/model/VietnameseTranslationPostProcessor.kt`

- `app/src/main/java/io/legado/app/data/repository/QuickTranslationRepository.kt`

- `app/src/main/java/io/legado/app/ui/book/source/health/SourceHealthScreen.kt`

- `app/src/main/res/values/strings.xml`

- `app/src/main/res/values-vi/strings.xml`

- `app/src/test/java/io/legado/app/domain/model/VietnameseTranslationPostProcessorTest.kt`



---



## 5. Tiêu Chí Nghiệm Thu (Pass Criteria)

- [ ] **PASS-01:** Các chuỗi văn bản có chứa `诺亚?帕特` hoặc dấu `?` lỗi giữa tên riêng được dịch sạch thành `Noah Pat`.

- [ ] **PASS-02:** Toàn bộ giao diện `SourceHealthScreen` hiển thị 100% tiếng Việt chuẩn, không còn bất kỳ chữ `BROKEN_RULE`, `HEALTHY`, `reachability`, `toc`, `QUICK`... nào.

- [ ] **PASS-03:** Biên dịch `:app:compileAppDebugKotlin` thành công, không có lỗi thiếu hoặc trùng lặp resource.
