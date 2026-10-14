# Phase 04: Source Health Diagnostic Accuracy & Consistency

Status: ⬜ Pending
Dependencies: None

## Objective
Khắc phục triệt để hiện tượng báo cáo và thực tế tính năng "Sức khỏe nguồn" không đồng nhất:
- Nguồn truyện (đặc biệt là VBook và các nguồn truyện tiếng Trung/Việt) thực tế đang hoạt động hoàn hảo (đọc truyện, xem mục lục, tải chi tiết bình thường), nhưng báo cáo sức khỏe nguồn lại đánh dấu nguồn bị chết / lỗi nặng (`HTTP_ERROR: VbookPluginException: test`).
- Sửa lỗi rò rỉ thông điệp lỗi (`messageRedacted`) sang toàn bộ các stage đã vượt qua thành công (PASSED).
- Cải thiện độ tin cậy của thuật toán chẩn đoán để phản ánh chính xác 100% tình trạng nguồn.

## Root Cause Analysis from Logcat & SQLite DB
1. **Từ khóa tìm kiếm Probe bị hardcode "test" (`VbookSourceHealthProbeRepository`)**:
   - Trong `VbookSourceHealthProbeRepository.kt`, dòng 254 định nghĩa: `const val DEFAULT_SEARCH_KEYWORD = "test"`.
   - Khi chạy probe stage SEARCH, nó tìm từ khóa `"test"` trên trang truyện (ví dụ `ptwxz.com` - nguồn truyện chữ Hán).
   - Với từ khóa tiếng Anh `"test"`, trang web không có kết quả, khiến plugin Javascript VBook ném ra ngoại lệ: `return Response.error(key)` -> `VbookPluginException: test`.
2. **Bộ phân loại lỗi `SourceCheckClassifier` đánh đồng Tìm kiếm rỗng với `HTTP_ERROR`**:
   - `SourceCheckClassifier.classifyFailureMessage` thấy chuỗi `VbookPluginException: test`, không khớp với danh sách từ khóa đặc biệt nên rơi vào nhánh `else -> BookSourceHealthStatus.HTTP_ERROR`.
   - Kết quả: Nguồn truyện bị dán nhãn `HTTP_ERROR` (Lỗi HTTP / Chết liên kết), dù thực tế server trang web phản hồi 200 OK và các stage `manifest`, `scripts`, `home`, `detail`, `toc` đều vượt qua (`PASSED`).
3. **Lỗi gán nhầm `messageRedacted` cho các stage đã PASSED trong `SourceCheckRepository`**:
   - Tại `SourceCheckRepository.kt` dòng 144:
     ```kotlin
     messageRedacted = stage.messageRedacted ?: evidence?.messageRedacted
     ```
   - Với các stage PASSED, `stage.messageRedacted` vốn là `null`. Do toán tử `?:`, nó tự động lấy `evidence?.messageRedacted` (là lỗi của stage SEARCH bị hỏng).
   - Dẫn đến trong cơ sở dữ liệu `source_check_stage_results`, TẤT CẢ các stage (kể cả `home: PASSED`, `toc: PASSED`) đều bị ghi nhận `messageRedacted = "VbookPluginException: test"`, gây hiểu lầm và hoang mang cho người dùng khi xem chi tiết chẩn đoán!
4. **Hồ sơ QUICK bỏ qua các bước quan trọng khi chạy Worker định kỳ**:
   - `BookSourceHealthWorker` chạy `checkAllEnabled(SourceCheckProfile.QUICK)`. Profile `QUICK` chỉ kiểm tra reachability + search + explore, bỏ qua hoàn toàn `detail`, `toc`, `content`. Do đó, nếu search lỗi do từ khóa thì nguồn bị phán "hỏng", trong khi người đọc từ kệ sách (`toc` + `content`) vẫn đọc bình thường.

## Implementation Steps
1. [ ] **Sửa Từ khóa Probe thông minh trong `VbookSourceHealthProbeRepository.kt`**:
   - Nếu đã lấy được sách mẫu `sample` từ stage `HOME` (ví dụ `sample.name`), ưu tiên dùng chính tên cuốn sách thực tế đó để kiểm tra tìm kiếm (đảm bảo kiểm tra tìm kiếm 100% sát thực tế).
   - Nếu chưa có sách mẫu, kiểm tra `locale` hoặc `type` của nguồn: Nếu là tiếng Trung (`zh_CN` / `chinese_novel`), sử dụng từ khóa tiếng Trung phổ biến (`"剑"` / `"仙"` / `"我"`); nếu tiếng Việt dùng `"ta"` / `"yêu"`; không dùng `"test"`.
2. [ ] **Chỉnh sửa phân loại lỗi trong `SourceCheckClassifier.kt`**:
   - Khi tìm kiếm không có kết quả hoặc plugin trả về lỗi do không tìm thấy sách, phân loại chính xác thành `CONTENT_EMPTY` hoặc `DEGRADED`, TUYỆT ĐỐI KHÔNG đánh đồng thành `HTTP_ERROR`.
   - Nếu `HOME`, `DETAIL`, `TOC` đều PASSED mà chỉ SEARCH không có kết quả, đánh giá nguồn là `DEGRADED` (hoạt động nhưng tìm kiếm hạn chế), không đánh dấu chết nguồn.
3. [ ] **Sửa lỗi kế thừa message trong `SourceCheckRepository.kt`**:
   - Chỉ gán `evidence?.messageRedacted` cho các stage có trạng thái `FAILED` hoặc `RUNNING`.
   - Các stage `PASSED` và `SKIPPED` phải giữ `messageRedacted = null`, không để thông điệp lỗi của stage khác xâm lấn.
4. [ ] **Đồng bộ hóa hiển thị trên `SourceHealthScreen.kt` & ViewModel**:
   - Đảm bảo thanh trạng thái và danh sách nguồn hiển thị nhất quán giữa kết quả kiểm tra tự động và khả năng đọc truyện thực tế.

## Files to Create/Modify
- `app/src/main/java/io/legado/app/data/repository/sourcehealth/VbookSourceHealthProbeRepository.kt` - Dynamic search keyword from home sample or locale.
- `app/src/main/java/io/legado/app/domain/sourcehealth/SourceCheckClassifier.kt` - Fine-grained status classification.
- `app/src/main/java/io/legado/app/data/repository/sourcehealth/SourceCheckRepository.kt` - Fix messageRedacted leakage on passed stages.
- `app/src/test/java/io/legado/app/data/repository/sourcehealth/SourceCheckEngineTest.kt` - Unit tests cho logic phân loại và stage results.

## Test Criteria
- [ ] Chạy kiểm tra sức khỏe nguồn `ptwxz` trên LDPlayer: Tất cả các stage `home`, `search`, `detail`, `toc` đều PASSED hoặc được phân loại chính xác.
- [ ] Không còn xuất hiện `messageRedacted` trên các stage đã PASSED.
- [ ] Báo cáo sức khỏe nguồn đồng nhất 100% với khả năng đọc và duyệt truyện thực tế trên ứng dụng.
