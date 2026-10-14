# Phase 04: Nâng Cao Độ Chính Xác Kiểm Tra Sức Khỏe Nguồn (Source Health Accuracy & Multi-Kind Cleanup)



> **Thuộc kế hoạch:** [`plan.md`](./plan.md)  

> **Phase ID:** `phase-04`  

> **Trạng thái:** ⬜ Pending  

> **Phụ thuộc:** Phase 02, Phase 03



---



## 1. Mục Tiêu (Objectives)

1. **Nâng cao độ chính xác của `SourceCheckClassifier` & Probe Adapters (`Book`, `RSS`, `VBook`):**

   - Không đánh dấu lỗi giả (`BROKEN_RULE` / `DEGRADED`) khi nguồn hợp lệ nhưng chỉ thiết kế chuyên cho Tìm kiếm (không có Khám phá) hoặc chỉ chuyên cho Khám phá (không có Tìm kiếm).

   - Nhận diện chuẩn xác trạng thái `CAPTCHA_REQUIRED`, `AUTH_REQUIRED`, `RATE_LIMITED` và bảo vệ trạng thái nguồn khi thiết bị mất mạng (`UNKNOWN_OFFLINE`).

2. **Sửa triệt để lỗi xóa nguồn đa loại (`book:`, `rss:`, `vbook:`) và dọn sạch bản ghi mồ côi:**

   - Khi xóa đơn lẻ hoặc xóa hàng loạt nguồn lỗi trên `SourceHealthScreen`, xóa chính xác nguồn gốc trong `BookSourceDao`, `RssSourceDao`, `VBook` store và xóa sạch lịch sử trong `BookSourceHealthDao`, `SourceCheckDao`, `CacheDao`.

3. **Đồng bộ 100% giữa bộ đếm `SourceHealthSummaryUi`, bộ lọc `SourceHealthFilter` và trạng thái chọn nhiều (`isSelectionMode`).**



---



## 2. Phân Tích Nguyên Nhân Gốc Rễ (Root Cause Analysis)



1. **Báo lỗi giả trên các nguồn chỉ có 1 chiều chức năng (Search-only hoặc Explore-only):**

   - Nhiều nguồn sách cộng đồng chỉ định nghĩa `searchUrl` mà để trống `exploreUrl` (hoặc ngược lại). Nếu adapter vẫn tạo stage cố định và trả về `FAILED` thay vì `SKIPPED`, hoặc khi `SourceCheckClassifier.aggregate()` thấy tất cả stage được khai báo đều `PASSED` còn lại `SKIPPED` mà không quy về `HEALTHY`, nguồn tốt sẽ bị báo lỗi oan.

2. **Xóa sót dữ liệu do lệch định dạng khóa `SourceKey` (`book:<url>` vs `<url>`):**

   - `BookSourceHealthRepository.observeRows()` hợp nhất 3 loại nguồn từ `SourceDomainIndexGateway` với khóa định danh có tiền tố (`book:https://...`, `rss:https://...`, `vbook:plugin_id`), trong khi một số bản ghi trong `book_source_health` hoặc `book_sources` lại dùng URL gốc không tiền tố.

   - Khi `deleteSources()` chỉ truyền một dạng chuỗi vào DAO, các bản ghi mang dạng khóa còn lại sẽ trở thành bản ghi mồ côi (orphan records), khiến nguồn đã xóa vẫn hiện trên danh sách hoặc làm lệch bộ đếm lỗi.

3. **Lệch bộ đếm giữa `SourceHealthSummaryUi` và `SourceHealthFilter.ERROR`:**

   - Nếu công thức đếm `summary.error` chỉ đếm một vài mã lỗi trong khi `SourceHealthFilter.ERROR` lại lọc tất cả các trạng thái khác `HEALTHY` / `UNKNOWN_OFFLINE`, con số trên thẻ tổng kết sẽ không khớp với số lượng nguồn hiển thị khi người dùng bấm vào bộ lọc `Có lỗi`.



---



## 3. Các Bước Triển Khai Chi Tiết (Implementation Steps)



### Task 04.1: Chuẩn Hóa Probe Adapters & `SourceCheckClassifier.kt`

- **Files:**

  - `app/src/main/java/io/legado/app/domain/sourcehealth/SourceCheckClassifier.kt`

  - `app/src/main/java/io/legado/app/data/repository/sourcehealth/SourceHealthProbeRepositories.kt`

- **Chi tiết thực hiện:**

  1. Trong `SourceHealthProbeRepositories.kt`:

     - Đối với `BookSource`: nếu `exploreUrl.isNullOrBlank()` thì stage `explore` được đánh dấu `SKIPPED`; nếu `searchUrl.isNullOrBlank()` thì stage `search` được đánh dấu `SKIPPED`. Miễn là ít nhất 1 trong 2 nhánh (`search` hoặc `explore`) cùng các stage phụ thuộc (`detail`, `toc`, `content` theo độ sâu profile) đạt `PASSED` và không có stage nào `FAILED`, nguồn đạt `HEALTHY`.

  2. Trong `SourceCheckClassifier.kt`:

     - Mở rộng nhận diện `CAPTCHA_REQUIRED` cho các phản hồi chứa dấu hiệu Cloudflare Turnstile, `cf-chl-bypass`, `Just a moment...`, `Attention Required! | Cloudflare`, `人机验证`, `安全验证`.

     - Tạo hàm vị từ chuẩn duy nhất `fun BookSourceHealthStatus.isAttentionRequired(): Boolean` trả về `true` cho: `DEGRADED`, `AUTH_REQUIRED`, `CAPTCHA_REQUIRED`, `RATE_LIMITED`, `BROKEN_RULE`, `NETWORK_ERROR`, `TLS_ERROR`, `CONTENT_EMPTY`, `HTTP_ERROR`, `UNSUPPORTED`.



### Task 04.2: Xóa Nguồn Đa Loại & Dọn Sạch Bản Ghi Mồ Côi (`BookSourceHealthRepository` & `SourceCheckRepository`)

- **Files:**

  - `app/src/main/java/io/legado/app/data/repository/BookSourceHealthRepository.kt`

  - `app/src/main/java/io/legado/app/data/repository/sourcehealth/SourceCheckRepository.kt`

  - `app/src/main/java/io/legado/app/data/dao/BookSourceHealthDao.kt`

  - `app/src/main/java/io/legado/app/data/dao/SourceCheckDao.kt`

- **Chi tiết thực hiện:**

  - Xây dựng hàm xóa thống nhất `deleteSourcesCompletely(keysOrUrls: Collection<String>)`:

    - Với mỗi phần tử, tách rõ `prefix` (`book`, `rss`, `vbook`) và `rawId` (URL hoặc pluginId), tạo tập hợp `allCandidateKeys = setOf(rawId, "book:$rawId", "rss:$rawId", "vbook:$rawId")`.

    - Thực thi xóa nguồn trong `BookSourceDao` (nếu là `book` hoặc không tiền tố), `RssSourceDao` (nếu là `rss`), và `VBook` repository (nếu là `vbook`).

    - Thực thi xóa toàn bộ `allCandidateKeys` trong `BookSourceHealthDao`, `SourceCheckDao` (`deleteRunsBySourceUrls`) và `CacheDao`.



### Task 04.3: Đồng Bộ State Chọn Nhiều & Bộ Lọc Trong `SourceHealthViewModel.kt`

- **Files:**

  - `app/src/main/java/io/legado/app/ui/book/source/health/SourceHealthViewModel.kt`

  - `app/src/main/java/io/legado/app/ui/book/source/health/SourceHealthScreen.kt`

- **Chi tiết thực hiện:**

  - Sử dụng hàm `status.isAttentionRequired()` thống nhất cho cả việc tính `SourceHealthSummaryUi.error` lẫn việc lọc danh sách khi `filter == SourceHealthFilter.ERROR`.

  - Sau khi `deleteSources` hoàn tất:

    - Cập nhật `selectedUrls = selectedUrls - deletedUrls`.

    - Nếu `selectedUrls.isEmpty()`, tự động chuyển `isSelectionMode = false`.

    - Nếu `selectedSourceUrl in deletedUrls`, đặt `selectedSourceUrl = null` và `selectedRunId = null` để đóng BottomSheet chi tiết an toàn.



---



## 4. Danh Sách Tệp Sửa Đổi (Files to Modify)

- `app/src/main/java/io/legado/app/domain/sourcehealth/SourceCheckClassifier.kt`

- `app/src/main/java/io/legado/app/data/repository/sourcehealth/SourceHealthProbeRepositories.kt`

- `app/src/main/java/io/legado/app/data/repository/sourcehealth/SourceCheckRepository.kt`

- `app/src/main/java/io/legado/app/data/repository/BookSourceHealthRepository.kt`

- `app/src/main/java/io/legado/app/ui/book/source/health/SourceHealthViewModel.kt`

- `app/src/main/java/io/legado/app/ui/book/source/health/SourceHealthScreen.kt`

- `app/src/test/java/io/legado/app/ui/book.source/health/SourceHealthStateTest.kt`

- `app/src/test/java/io/legado/app/domain/sourcehealth/SourceCheckClassifierTest.kt`

- `app/src/test/java/io/legado/app/data/repository/sourcehealth/SourceCheckRepositoryTest.kt`



---



## 5. Tiêu Chí Nghiệm Thu (Pass Criteria)

- [ ] **PASS-01:** Con số trên thẻ `Có lỗi` trong `SourceHealthSummaryUi` khớp 100% với số lượng nguồn hiển thị khi chọn chip lọc `Có lỗi`.

- [ ] **PASS-02:** Xóa nguồn lỗi (đơn lẻ hoặc hàng loạt) xóa sạch cả nguồn `Book`, `RSS`, `VBook` và toàn bộ lịch sử chạy, cập nhật UI tức thì.

- [ ] **PASS-03:** `SourceHealthStateTest`, `SourceCheckClassifierTest`, `SourceCheckRepositoryTest` pass 100%.
