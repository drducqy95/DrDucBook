# Phase 04: Tái Cấu Trúc Story Wiki Theo Từng Truyện (Book-First Master-Detail)

Status: ✅ Completed
Dependencies: Phase 01

## 1. Mục tiêu
Thiết kế lại luồng màn hình của Story Wiki:
- **Tầng 1 (Wiki Home - Danh sách truyện):** Màn hình chính Wiki hiển thị danh sách/lưới các truyện có dữ liệu Wiki (Bìa, tên sách, tác giả, tóm tắt, số lượng nhân vật, thiết lập thế giới, quan hệ và số chương đã phân tích).
- **Tầng 2 (Truyện Wiki Detail - Chi tiết Wiki của riêng truyện):** Khi người dùng bấm vào một truyện cụ thể, mở màn hình Wiki chuyên sâu của riêng truyện đó, chứa đầy đủ các Tab (Nhân vật, Thế giới quan, Dòng thời gian, Đồ thị quan hệ nhân vật) như hiện tại.

## 2. Phân tích hiện trạng
- Trong `StoryWikiScreen.kt`:
  - Hiện tại toàn bộ dữ liệu đang bị gom chung trên 1 màn hình phẳng.
  - Phía trên chỉ có một ô Search và một dãy `FilterChip` liệt kê các truyện (`state.books`).
  - Nếu có nhiều truyện cùng có Wiki, các tab `ENTITY`, `WORLD_BUILDING`, `TIMELINE`, `CHARACTER_GRAPH` nằm chung ngay dưới khiến người dùng rất khó theo dõi riêng biệt từng tác phẩm, vi phạm nguyên tắc phân cấp trải nghiệm đọc tiểu thuyết (Story-centric).

## 3. Quyết định Navigation Approach

**Chọn: Internal State + BackHandler** (thay vì tách thành 2 Navigation routes riêng biệt).

Lý do:
- Wiki chỉ có 2 tầng đơn giản, không cần deep navigation stack.
- Tránh phải tạo thêm route mới trong `MainNavKey.kt` và đăng ký entry provider.
- Dùng `BackHandler` composable để intercept back gesture khi đang ở Tầng 2, chuyển về Tầng 1 thay vì thoát Wiki.
- Predictive back animation vẫn hoạt động tự nhiên nhờ `BackHandler(enabled = activeBookUrl != null)`.

## 4. Các bước thực hiện
1. **Thiết kế lại Contract & UiState (`StoryWikiContract.kt`):**
   - Bổ sung trạng thái màn hình phân cấp:
     - `activeBookUrl: String?`: Nếu `null` -> Hiển thị danh sách truyện (Tầng 1). Nếu khác `null` -> Hiển thị chi tiết Wiki của truyện đó (Tầng 2).
   - Thêm Intent:
     - `OpenBookWiki(bookUrl: String)`
     - `BackToBookList`
2. **Xây dựng Màn hình Danh sách Truyện (`StoryWikiBookListContent`):**
   - Hiển thị danh sách các thẻ truyện (`StoryWikiBookCard`):
     - Ảnh bìa sách (`AsyncImage` từ coverUrl hoặc book file).
     - Tên truyện, tên tác giả/nguồn.
     - Thống kê sinh động: Badge số lượng nhân vật (👥), thiết lập (🏰), quan hệ (🔗), tiến độ chương đã phân tích (📖 X chương).
     - Nút "Vào Wiki truyện".
   - Thanh tìm kiếm truyện nhanh trên TopBar.
3. **Xây dựng Nội dung Chi tiết Wiki Truyện (`StoryWikiBookDetailContent`):**
   - TopBar: Nút Back quay về danh sách truyện, Tiêu đề là Tên truyện đang chọn.
   - Các Tab chuyên sâu của riêng truyện đó:
     - Tab 1: **Nhân vật & Thực thể (Characters & Entities)**
     - Tab 2: **Thế giới & Thiết lập (World Building: Pháp bảo, Công pháp, Thế lực, Địa danh...)**
     - Tab 3: **Dòng thời gian (Timeline & Events theo từng chương)**
     - Tab 4: **Sơ đồ quan hệ (Character Relationship Graph)**
   - Thanh tìm kiếm lọc nội bộ trong truyện đó.
4. **BackHandler integration:**
   ```kotlin
   @Composable
   fun StoryWikiScreen(state: StoryWikiUiState, onIntent: (StoryWikiIntent) -> Unit, ...) {
       // Intercept back khi đang ở Tầng 2
       BackHandler(enabled = state.activeBookUrl != null) {
           onIntent(StoryWikiIntent.BackToBookList)
       }
       
       if (state.activeBookUrl == null) {
           StoryWikiBookListContent(state, onIntent)
       } else {
           StoryWikiBookDetailContent(state, onIntent)
       }
   }
   ```
5. **Hỗ trợ Deep-link / Mở trực tiếp từ Trình đọc:**
   - Khi người dùng đang đọc sách (ví dụ đang ở trong sách *Chủ Thần Đại Đạo*) và bấm mở Story Wiki từ thanh menu đọc:
     - Tự động mở thẳng vào Tầng 2 với `activeBookUrl = currentBook.bookUrl`, nút Back sẽ quay lại danh sách truyện.

## 5. Files ảnh hưởng
- `app/src/main/java/io/legado/app/ui/translation/memory/StoryWikiContract.kt`
- `app/src/main/java/io/legado/app/ui/translation/memory/StoryWikiScreen.kt`
- `app/src/main/java/io/legado/app/ui/translation/memory/StoryWikiViewModel.kt`
- `app/src/main/res/values/strings.xml` & `app/src/main/res/values-vi/strings.xml`

## 6. Tiêu chuẩn nghiệm thu
- [ ] Vào Story Wiki từ menu chính: Thấy danh sách các truyện có Wiki (như *Chủ Thần Đại Đạo*).
- [ ] Bấm vào truyện *Chủ Thần Đại Đạo*: Mở trang Wiki riêng với 4 tab (Nhân vật, Thế giới, Timeline, Đồ thị).
- [ ] Bấm Back ở trang chi tiết quay lại danh sách truyện mượt mà (BackHandler hoạt động).
- [ ] Bấm Back ở trang danh sách truyện thoát khỏi Wiki (không bị kẹt).
- [ ] Mở Wiki từ trang đọc sách → Nhảy thẳng vào chi tiết Wiki truyện đang đọc.
