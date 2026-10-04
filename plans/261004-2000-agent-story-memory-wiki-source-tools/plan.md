# Master Plan: Bổ sung công cụ Tạo Bộ nhớ dịch, Hiệu chỉnh Wiki truyện, Niên biểu Đại sự ký & Hoàn thiện công cụ Tạo / Sửa lỗi nguồn cho Agent & Chatbot

## 1. Mục tiêu và Bối cảnh (Objectives & Context)
Người dùng yêu cầu bổ sung các năng lực trọng yếu cho hệ sinh thái AI Agent & Chatbot và chuẩn hóa trải nghiệm hiển thị:
1. **Bộ nhớ dịch cốt truyện (Story Translation Memory):**
   - Cho phép Agent/Chatbot tự động tạo/phân tích ký ức cốt truyện từ chương sách hoặc văn bản thô, truy vấn snapshot bộ nhớ, và đồng bộ (retrofit) thuật ngữ vào bản dịch cũ.
   - **Chuẩn hóa hiển thị số chương & Bản dịch tiêu đề timeline:** Sửa đổi hiển thị ở Bộ nhớ dịch và Story Wiki để viết **đúng số chương thực tế và hiển thị bản dịch tiêu đề tiếng Việt** (truy xuất từ `BookChapter.title` qua `bookChapterDao`, dịch offline tự động nếu chứa ký tự CJK gốc, tái cấu trúc số chương tự nhiên như "Chương 458: ...", "Chương 12: ...", "Tiết tử: ...") thay vì hiển thị số thứ tự cứng nhắc 1, 2, 3, 4 theo index hay để lọt tiêu đề chữ Hán thô ráp.
2. **Hiệu chỉnh Wiki truyện & Niên biểu Đại sự ký (Story Wiki & Chronicle Synthesis):**
   - Bổ sung công cụ cho phép Agent **đọc, phân tích và viết lại niên biểu (Chronicle Synthesis)** một cách hợp lý, logic theo các giai đoạn/thời kỳ/đại sự kiện bước ngoặt của tác phẩm, thay vì chỉ lưu trữ thô ráp các timeline vụn vặt từng chương.
   - Bổ sung công cụ quản lý thực thể (nhân vật, môn phái, địa danh), thế giới quan (cảnh giới, pháp bảo, quy tắc) và đồ thị quan hệ nhân vật.
3. **Hoàn thiện công cụ Tạo & Sửa lỗi nguồn sách (Source Creation & Repair):**
   - Nâng cấp `repair_book_source` để sửa trực tiếp các selector rules thực tế (`ruleSearch`, `ruleToc`, `ruleContent`, `ruleBookInfo`, `header`, `loginUrl`, `weight`).
   - Bổ sung `save_book_source` tạo/lưu nguồn sách trực tiếp (kèm validation).
   - Bổ sung `test_book_source_rule` cho phép Agent chạy thử nghiệm bóc tách trên URL mẫu để tự động kiểm tra tính chính xác của rule trước khi lưu.
4. **Phân quyền và Đồng bộ hóa 2 chiều:**
   - Đăng ký `AgentPermissionBroker` theo mô hình bảo mật rủi ro, phân loại danh mục trong `ChatbotToolPermissionConfig`, đồng bộ tức thì lên Bảng điều khiển Agent và Chatbot UI.

---

## 2. Danh sách các Phase thực thi chi tiết

| Phase | Tên Phase | Nội dung chính |
| :--- | :--- | :--- |
| **Phase 01** | `phase-01-story-memory-tools.md` | Bổ sung các công cụ: `create_story_memory`, `get_story_memory`, `retrofit_story_translations`. **Chuẩn hóa hiển thị đúng số chương và hiển thị bản dịch tiêu đề tiếng Việt (không để raw CJK)** trong Bộ nhớ dịch & Timeline. |
| **Phase 02** | `phase-02-story-wiki-editing-tools.md` | Bổ sung công cụ quản lý Wiki (`upsert_story_wiki_entity`, `delete_story_wiki_entity`, `upsert_story_wiki_world`, `upsert_story_wiki_relationship`...). **Bổ sung công cụ Đọc, Phân tích & Viết lại Niên biểu Đại sự ký (`synthesize_story_chronicle`, `get_story_chronicle`, `save_story_chronicle`)**. |
| **Phase 03** | `phase-03-source-repair-and-creation-tools.md` | Nâng cấp `repair_book_source` hỗ trợ sửa selector rules; bổ sung `save_book_source` tạo/cập nhật nguồn trực tiếp; bổ sung `test_book_source_rule` test thử nghiệm rule bóc tách. |
| **Phase 04** | `phase-04-permissions-and-ui-integration.md` | Đăng ký rủi ro (`AgentActionRisk`) & Capabilities trong `AgentPermissionBroker`, mở rộng danh mục trong `ChatbotToolPermissionConfig` & cập nhật Bảng điều khiển Agent (`AgentDashboardScreen`). |
| **Phase 05** | `phase-05-verification-and-testing.md` | Viết Unit Test cho tất cả công cụ mới, kiểm tra biên dịch Kotlin, đóng gói APK, cài đặt lên LDPlayer (`emulator-5554`) và kiểm thử trực tiếp bằng câu lệnh Chatbot. |

---

## 3. Kiến trúc kỹ thuật và Tính tương thích (Architecture & Compatibility)
- **Tận dụng usecase sẵn có:** `TranslationStoryMemoryUseCase` làm trung tâm lưu trữ và xử lý Room DB / QuickDictionary.
- **Mô hình Niên biểu tổng hợp (Story Chronicle Model):** Tổ chức dữ liệu niên biểu theo dạng `StoryChroniclePeriod` (gồm tên giai đoạn, khoảng chương thực tế, tóm lược biến cố lớn, nhân vật trọng tâm, các mốc bước ngoặt) lưu vào `ai_memory` với key `chronicle_period_{id}`, hiển thị mạch lạc trên tab Niên biểu của Story Wiki.
- **Chuẩn hóa định dạng số chương & Dịch tiêu đề:** Truy xuất `BookChapter.title` thực tế từ `bookChapterDao` và áp dụng `formatVietnameseChapterTitle`, tự động dịch qua `quickTranslationGateway.translate` nếu có CJK, phân tích số chương tự nhiên (ví dụ: "Chương 458: ...", "Chương 12: ...") thay vì số thứ tự index cứng nhắc `timeline.chapterIndex + 1`. Tuyệt đối không để sót tiêu đề raw tiếng Trung.
- **Bảo mật rủi ro (Risk-Based Governance):** Các công cụ chỉ đọc (`get_story_memory`, `get_story_wiki`, `get_story_chronicle`, `test_book_source_rule`): `AgentActionRisk.READ` (tự duyệt). Các công cụ ghi/xóa: `AgentActionRisk.WRITE` hoặc `DELETE`.
