# Phase 04: Chatbot Tool Permissions & Pre-Approval Settings

Status: ⬜ Pending
Dependencies: None

## Objective
Giải quyết triệt để vấn đề:
- Lỗi không hiển thị hoặc bị trôi mất màn hình xác nhận duyệt quyền công cụ chatbot, dẫn đến việc chatbot bị tắc nghẽn (hang) hoặc không hoàn thành tác vụ.
- **Giải pháp theo yêu cầu người dùng**: Bổ sung màn hình Cài đặt công cụ cho chatbot, người dùng bật/tắt sẵn các nhóm quyền theo danh sách. Các quyền đã bật sẽ được **mặc định tự động duyệt (pre-approved)**, không cần hỏi lại người dùng trong suốt quá trình trò chuyện.

## Requirements

### Functional
- [ ] Thiết kế `ChatbotToolPermissionConfig` (lưu trữ cấu hình trong SharedPreferences):
  - Nhóm các quyền thao tác của Chatbot thành các quyền cụ thể:
    1. **Tìm kiếm & Đọc tài liệu** (`search_internet`, `fetch_web_page`, `read_bookshelf`, `get_chapter_content`) - Mặc định BẬT, an toàn.
    2. **Quản lý Giá sách & Tiến độ** (`add_book_to_shelf`, `remove_book_from_shelf`, `update_read_progress`, `manage_bookmarks`) - BẬT/TẮT.
    3. **Quản lý Từ điển & Ghi chú** (`add_quick_dictionary_term`, `update_story_memory`, `manage_replace_rules`) - BẬT/TẮT.
    4. **Quản lý Nguồn sách & VBook** (`create_vbook_plugin_draft`, `save_book_source`, `modify_book_source`) - BẬT/TẮT.
    5. **Tệp tin & Export** (`export_ebook`, `write_local_file`) - BẬT/TẮT.
  - Cung cấp tùy chọn tổng quát: "Tự động duyệt tất cả công cụ đã bật" (mặc định BẬT).
- [ ] Cập nhật `AgentPermissionBroker.kt`:
  - Trong `requiresApproval(toolName)`:
    - Nếu tool thuộc nhóm quyền đã được người dùng BẬT tự động duyệt trong `ChatbotToolPermissionConfig`, trả về `false` (không cần hiển thị proposal/xác nhận lại).
  - Tích hợp kiểm tra quyền nhanh qua `isToolAutoApproved(toolName)`.
- [ ] Xây dựng UI Cài đặt công cụ Chatbot:
  - Tạo BottomSheet hoặc màn hình con `ChatbotToolSettingsSheet.kt`.
  - Có thể mở từ:
    1. Menu tùy chọn góc trên trong `AiChatScreen` ("Cài đặt công cụ").
    2. Mục "Trợ lý AI / Chatbot" trong `AiConfigScreen`.
  - Hiển thị danh sách từng quyền với tiêu đề tiếng Việt, mô tả rõ ràng và Switch bật/tắt trực quan.

### Non-Functional
- [ ] Đảm bảo cơ chế bảo mật (Security Policy) vẫn chặn các thao tác độc hại hoặc ngoài danh sách công cụ đã khai báo.
- [ ] Trạng thái bật/tắt được cập nhật tức thì (reactive qua StateFlow) mà không cần khởi động lại ứng dụng.

## Implementation Steps
1. [ ] Tạo `io/legado/app/help/config/ChatbotToolPermissionConfig.kt`:
   - Định nghĩa các nhóm quyền `ChatbotToolCategory`.
   - Lưu trữ và đọc trạng thái bật/tắt của từng tool hoặc category bằng SharedPreferences.
   - Hàm `isToolAutoApproved(toolName: String): Boolean`.
2. [ ] Đăng ký `ChatbotToolPermissionConfig` trong `di/appModule.kt` (single).
3. [ ] Sửa đổi `AgentPermissionBroker.kt`:
   - Trong `requiresApproval(toolName)`: kiểm tra `ChatbotToolPermissionConfig.isToolAutoApproved(toolName)`. Nếu true -> trả về `false` ngay (auto-approved).
4. [ ] Tạo `ChatbotToolSettingsSheet.kt`:
   - Giao diện Material 3 với các Switch cho từng nhóm quyền.
5. [ ] Tích hợp vào `AiChatScreen.kt` & `AiChatViewModel.kt`:
   - Thêm nút mở `ChatbotToolSettingsSheet` trên thanh tiêu đề `AiChatScreen`.
6. [ ] Thêm test cases trong `AgentPermissionBrokerTest.kt`.

## Files to Create/Modify
- `app/src/main/java/io/legado/app/help/config/ChatbotToolPermissionConfig.kt`
- `app/src/main/java/io/legado/app/di/appModule.kt`
- `app/src/main/java/io/legado/app/domain/agent/AgentPermissionBroker.kt`
- `app/src/main/java/io/legado/app/ui/ai/chat/ChatbotToolSettingsSheet.kt`
- `app/src/main/java/io/legado/app/ui/ai/chat/AiChatScreen.kt`
- `app/src/main/java/io/legado/app/ui/ai/chat/AiChatViewModel.kt`
- `app/src/test/java/io/legado/app/domain/agent/AgentPermissionBrokerTest.kt`

## Test Criteria
- [ ] Bật quyền "Quản lý từ điển / Ghi chú", Chatbot gọi tool lưu từ điển hoặc bộ nhớ -> Thực thi ngay lập tức mà không hiện popup hỏi lại.
- [ ] Tắt một nhóm quyền -> Chatbot từ chối hoặc yêu cầu xác nhận theo đúng cấu hình.

---
Next Phase: [Phase 05 - External URL Auto-detection](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/plans/261004-1530-ux-auth-tools-link-sync-fix/phase-05-external-url-auto-detect-booksources.md)
