# Phase 04: Tích hợp Phân quyền, Danh mục & Bảng điều khiển Agent (Permissions & UI Integration)

## 1. Mục tiêu
1. Đăng ký mức độ rủi ro (`AgentActionRisk`) chuẩn xác cho tất cả các công cụ mới trong `AgentPermissionBroker`.
2. Mở rộng enum `ChatbotToolCategory` trong `ChatbotToolPermissionConfig`, gom nhóm hợp lý và đổi tên hiển thị trực quan: *"Từ điển, Wiki & Ký ức truyện"*.
3. Đảm bảo trạng thái công tắc tự động duyệt đồng bộ tức thì 2 chiều giữa Bảng điều khiển Agent (`AgentDashboardScreen`), Bảng cài đặt quyền (`ChatbotToolSettingsSheet`) và Chatbot AI.

---

## 2. Thiết kế chi tiết

### 2.1. Đăng ký trong `AgentPermissionBroker.kt`
- **Nhóm Ghi (`AgentActionRisk.WRITE`):**
  - `create_story_memory`
  - `retrofit_story_translations`
  - `synthesize_story_chronicle`
  - `save_story_chronicle`
  - `upsert_story_wiki_entity`
  - `upsert_story_wiki_world`
  - `upsert_story_wiki_relationship`
  - `save_book_source`
- **Nhóm Xóa (`AgentActionRisk.DELETE`):**
  - `delete_story_wiki_entity`
  - `delete_story_wiki_world`
  - `delete_story_wiki_relationship`
- **Nhóm Chỉ đọc (`AgentActionRisk.READ` - Mặc định tự duyệt):**
  - `get_story_memory`
  - `get_story_chronicle`
  - `get_story_wiki`
  - `test_book_source_rule`

### 2.2. Mở rộng `ChatbotToolCategory.kt`
Cập nhật danh mục `tools` của enum:
1. **`BOOK_SOURCE_AND_PLUGINS` ("Nguồn sách & Plugin VBook"):**
   - Bổ sung: `"save_book_source"`.
2. **`DICTIONARY_AND_MEMORY` ("Từ điển, Wiki & Ký ức truyện"):**
   - Đổi tiêu đề: *"Từ điển, Wiki & Ký ức truyện"*.
   - Đổi mô tả: *"Tạo bộ nhớ dịch, biên soạn niên biểu truyện, quản lý nhân vật, thế giới quan và từ điển thuật ngữ."*
   - Danh sách công cụ thuộc nhóm:
     - `save_memory`, `delete_memory`
     - `save_book_dictionary_term`, `delete_book_dictionary_term`, `clear_book_dictionary`
     - `save_dictionary_entry`, `delete_dictionary_entry`
     - `create_story_memory`, `retrofit_story_translations`
     - `synthesize_story_chronicle`, `save_story_chronicle`
     - `upsert_story_wiki_entity`, `delete_story_wiki_entity`
     - `upsert_story_wiki_world`, `delete_story_wiki_world`
     - `upsert_story_wiki_relationship`, `delete_story_wiki_relationship`

### 2.3. Cập nhật Giao diện & Hiển thị
- **`ChatbotToolSettingsSheet.kt`:**
  - Hiển thị đầy đủ danh sách các công cụ mới thuộc nhóm *"Từ điển, Wiki & Ký ức truyện"* và *"Nguồn sách & Plugin VBook"*.
- **`AgentDashboardScreen.kt`:**
  - Cập nhật số lượng công cụ tính toán tự động (`toolCount`, `enabledToolCount`, `readToolCount`, `approvalToolCount`).
  - Hiển thị nhãn trực quan: `· Tự duyệt` (khi nhóm quyền được bật) và `· Cần duyệt` (khi nhóm quyền tắt).

---

## 3. Tiêu chí kiểm thử
- Bật/tắt công tắc *"Từ điển, Wiki & Ký ức truyện"* trên Bảng điều khiển Agent -> Cả 11 công cụ ghi/xóa thuộc nhóm memory và wiki đều chuyển trạng thái tức thì.
- Gửi yêu cầu qua Chatbot khi đã bật quyền: Agent thực thi tạo memory, tổng hợp niên biểu và sửa wiki hoàn toàn tự động không cần xác nhận lại.
