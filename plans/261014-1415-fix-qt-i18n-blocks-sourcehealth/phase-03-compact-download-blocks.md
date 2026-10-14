# Phase 03: Thu Gọn Giao Diện Các Khối Tải Xuống & Khối Nội Dung (Compact Download & Content Blocks)



> **Thuộc kế hoạch:** [`plan.md`](./plan.md)  

> **Phase ID:** `phase-03`  

> **Trạng thái:** ⬜ Pending  

> **Phụ thuộc:** Phase 02



---



## 1. Mục Tiêu (Objectives)

1. **Tối ưu hóa mật độ hiển thị (Compact UI Density) cho toàn bộ các khối tải xuống (Download Blocks):**

   - Giảm 45%–55% chiều cao dọc của mỗi thẻ tải xuống (từ `140.dp`–`200.dp` xuống còn **`54.dp`–`72.dp`**) trên tất cả các màn hình:

     - `DownloadCenterScreen.kt` (Trung tâm tải xuống hợp nhất: Sách, File, Media, Xuất EPUB/TXT).

     - `BookCacheManageScreen.kt` & `DownloadCacheConfigScreen.kt` (Quản lý bộ nhớ đệm chương sách).

     - `TranslationConfigScreen.kt` & `MlKitModelsScreen.kt` (Các khối tải mô hình NMT ONNX `HachimiMT`, `NLLB-200`, `LocalAI GGUF`, `ML Kit`).

     - `ReadAloudConfigSheet.kt` (Các khối tải gói `ZeroTTS Base` và 8 gói `ZeroTTS Voice Addons`).

2. **Thu gọn các khối nội dung rỗng trên trình đọc (`BookContentProcessEngine.kt`):**

   - Không để lại các khoảng trống trắng lớn trên trang đọc sau khi quy tắc `BookContentProcess` xóa các đoạn văn quảng cáo/rác.



---



## 2. Quy Chuẩn Thiết Kế `CompactDownloadBlock` (Design Specifications)



### 2.1. Cấu Trúc Hàng Ngang Đơn Nhất (Single-Row Compact Layout)

```text

+-----------------------------------------------------------------------+

| [Thumb] Tên Sách / Tên Gói Model (1 dòng, Ellipsis)     [Pause] [Del] |

|  36x48  124/500 chương • 24.8% • Đang tải                             |

|         [=================>------------------------] (Progress 3.dp)  |

+-----------------------------------------------------------------------+



```



### 2.2. Thông Số Kích Thước Chuẩn (Metrics)



* **Khoảng cách giữa các thẻ (Item Vertical Spacing):** `4.dp` (thay vì `10.dp`–`12.dp`).

* **Padding bên trong thẻ (Card Inner Padding):** `horizontal = 12.dp, vertical = 8.dp` (thay vì `16.dp`).

* **Kích thước Thumbnail / Icon bên trái:**

* Bìa sách: `36.dp x 48.dp`, bo góc `6.dp`.

* Icon loại tệp / Model AI / Giọng đọc TTS: Box `36.dp x 36.dp`, bo góc `8.dp`, icon bên trong `20.dp`.





* **Cột thông tin ở giữa (`Modifier.weight(1f)`):**

* **Dòng 1 (Title):** `MaterialTheme.typography.bodyMedium`, `fontWeight = FontWeight.SemiBold`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`.

* **Dòng 2 (Unified Subtitle):** `MaterialTheme.typography.labelSmall`, màu `onSurfaceVariant`, `maxLines = 1`, gộp gọn: `Đã tải X/Y chương (hoặc MB/MB) • Phần trăm (%) • Trạng thái`.

* **Dòng 3 (Thin Progress Bar):** `LinearProgressIndicator` có `Modifier.fillMaxWidth().height(3.dp)`, chỉ hiển thị khi tác vụ đang `RUNNING` / `PAUSED` / `PENDING` / `ERROR`. Khi đã `COMPLETED`, tự động ẩn thanh progress để thẻ thu gọn còn `54.dp`.





* **Cột nút hành động bên phải (Inline Compact IconButtons):**

* Sử dụng `IconButton(modifier = Modifier.size(32.dp))` với `Icon(modifier = Modifier.size(18.dp))` đặt trên cùng hàng ngang với tiêu đề/phụ đề.

* Xóa bỏ hoàn toàn hàng `Row` chứa các `Button` / `OutlinedButton` chiếm chỗ ở đáy thẻ.







---



## 3. Các Bước Triển Khai Chi Tiết (Implementation Steps)



### Task 03.1: Refactor `DownloadCenterScreen.kt` & `BookCacheManageScreen.kt`



* **Files:**

* `app/src/main/java/io/legado/app/ui/download/DownloadCenterScreen.kt`

* `app/src/main/java/io/legado/app/ui/book/cache/BookCacheManageScreen.kt`

* `app/src/main/java/io/legado/app/ui/config/download/DownloadCacheConfigScreen.kt`





* **Chi tiết thực hiện:**

1. Thu gọn phần Header thống kê ở đầu `DownloadCenterScreen` và `BookCacheManageScreen` thành một thanh `Surface` mỏng (`padding = 8.dp`), hiển thị các con số thống kê trên 1 dòng ngang cùng các chip lọc compact.

2. Áp dụng bố cục `CompactDownloadBlock` cho tất cả các loại item trong danh sách tải xuống:

* Tác vụ tải chương sách (`CacheBook`).

* Tác vụ tải tệp/ebook (`DownloadService`).

* Tác vụ tải Media Video/Audio/Manga (`MediaDownload`).

* Tác vụ xuất sách (`ExportBook`).











### Task 03.2: Thu Gọn Các Khối Tải Model AI / NMT / ML Kit / ZeroTTS



* **Files:**

* `app/src/main/java/io/legado/app/ui/config/translation/TranslationConfigScreen.kt`

* `app/src/main/java/io/legado/app/ui/config/translation/mlkit/MlKitModelsScreen.kt`

* `app/src/main/java/io/legado/app/ui/book/read/ReadAloudConfigSheet.kt`





* **Chi tiết thực hiện:**

1. Trong `TranslationConfigScreen.kt` và `MlKitModelsScreen.kt`:

* Thu gọn các thẻ tải gói mô hình offline (`HachimiMT-60-QT`, `NLLB-200`, `HY-MT 1.8B GGUF`, và các gói ngôn ngữ ML Kit) về dạng dòng compact `56.dp`, đặt nút `Tải xuống / Đang dùng / Xóa` ngay bên phải cùng hàng.





2. Trong `ReadAloudConfigSheet.kt`:

* Thu gọn khối tải gói `ZeroTTS Base` và danh sách 8 gói giọng đọc `ZeroTTS Voice Addons` thành các dòng compact (`48.dp`–`56.dp`), hiển thị tên giọng + dung lượng + trạng thái đã cài + nút tải/chọn trên một dòng duy nhất.











### Task 03.3: Thu Gọn Khối Rỗng Sau Khi Xóa Nội Dung Trong `BookContentProcessEngine.kt`



* **File:** `app/src/main/java/io/legado/app/domain/model/BookContentProcessEngine.kt`

* **Chi tiết thực hiện:**

* Sau khi thực thi các quy tắc `BookContentProcess` (đặc biệt là quy tắc xóa đoạn văn rác hoặc thay thế bằng chuỗi rỗng), chuẩn hóa chuỗi kết quả bằng cách thu gọn các chuỗi xuống dòng liên tiếp (`\n(?:[ \t\u3000]*\n){2,}` $\rightarrow$ `\n\n`) và cắt khoảng trắng thừa ở đầu/cuối chương, tránh tạo ra các khối trống rỗng trên trang đọc.







---



## 4. Danh Sách Tệp Sửa Đổi (Files to Modify)



* `app/src/main/java/io/legado/app/ui/download/DownloadCenterScreen.kt`

* `app/src/main/java/io/legado/app/ui/book/cache/BookCacheManageScreen.kt`

* `app/src/main/java/io/legado/app/ui/config/download/DownloadCacheConfigScreen.kt`

* `app/src/main/java/io/legado/app/ui/config/translation/TranslationConfigScreen.kt`

* `app/src/main/java/io/legado/app/ui/config/translation/mlkit/MlKitModelsScreen.kt`

* `app/src/main/java/io/legado/app/ui/book/read/ReadAloudConfigSheet.kt`

* `app/src/main/java/io/legado/app/domain/model/BookContentProcessEngine.kt`

* `app/src/test/java/io/legado/app/domain/model/BookContentProcessEngineTest.kt`



---



## 5. Tiêu Chí Nghiệm Thu (Pass Criteria)



* [ ] **PASS-01:** Chiều cao mỗi thẻ tải xuống không vượt quá `72.dp` khi đang tải và `58.dp` khi hoàn tất; một màn hình hiển thị được tối thiểu 6–8 tác vụ tải xuống.

* [ ] **PASS-02:** Các nút icon thao tác nhanh (`Pause`, `Resume`, `Retry`, `Delete`) phản hồi tức thì, không gây tràn hay cắt chữ tiêu đề.

* [ ] **PASS-03:** `BookContentProcessEngineTest` pass 100%, không còn dòng trống thừa sau khi xóa khối văn bản.
