# Phase 05: Kiểm thử Tự động & Xác minh Thực tế trên Thiết bị (Verification & Testing)

## 1. Mục tiêu
Thực hiện toàn bộ quy trình kiểm thử chất lượng từ tầng Unit Test, kiểm tra tương thích Gradle đến kiểm thử trực tiếp trên giả lập LDPlayer (`emulator-5554`).

## 2. Kế hoạch kiểm thử chi tiết

### 2.1. Unit Test Suite (JVM)
- **`AiToolRepositoryTest`:**
  - Test đăng ký và gọi công cụ `create_story_memory`, `get_story_memory`, `retrofit_story_translations`.
  - Test các công cụ wiki: `upsert_story_wiki_entity`, `delete_story_wiki_entity`, `upsert_story_wiki_world`, `upsert_story_wiki_relationship`, `get_story_wiki`.
  - Test nâng cấp `repair_book_source` với việc sửa `ruleSearch`, `ruleToc`, `ruleContent`.
  - Test `save_book_source` và `test_book_source_rule`.
- **`AgentPermissionBrokerTest`:**
  - Kiểm tra mức độ rủi ro (`AgentActionRisk`) của tất cả các công cụ mới (đúng phân loại `WRITE`, `DELETE`, `READ`).
  - Kiểm tra cơ chế tự duyệt (`isToolAutoApproved`) khi nhóm quyền tương ứng được bật trong `ChatbotToolPermissionConfig`.
- **`AgentDashboardStateMapperTest`:**
  - Kiểm tra số lượng công cụ tính toán (`toolCount`, `readToolCount`, `approvalToolCount`).
  - Kiểm tra mapping danh mục quyền sang UI.

### 2.2. Biên dịch & Đóng gói (Build & Package)
1. Chạy biên dịch nhanh Kotlin:
   ```powershell
   .\gradlew.bat :app:compileAppDebugKotlin
   ```
2. Chạy toàn bộ Unit Tests liên quan:
   ```powershell
   .\gradlew.bat test --tests "io.legado.app.data.repository.AiToolRepositoryTest"
   .\gradlew.bat test --tests "io.legado.app.domain.agent.AgentPermissionBrokerTest"
   ```
3. Đóng gói APK Debug:
   ```powershell
   .\gradlew.bat :app:assembleAppDebug
   ```

### 2.3. Kiểm thử Thực tế trên LDPlayer (`emulator-5554`)
1. Cài đặt APK: `adb -s emulator-5554 install -r app\build\outputs\apk\app\debug\app-app-x86_64-debug.apk`.
2. Kiểm tra giao diện:
   - Mở *Bảng điều khiển Agent* -> Kiểm tra tổng số công cụ (tăng từ 47 lên 57+ công cụ).
   - Kiểm tra bảng *Quyền công cụ* -> Kiểm tra nhóm *"Từ điển, Wiki & Ký ức truyện"* hiển thị đầy đủ danh sách các công cụ mới.
3. Kiểm thử kịch bản Chatbot:
   - **Kịch bản 1 (Tạo ký ức dịch):** Gửi yêu cầu *"Hãy tạo bộ nhớ dịch cho đoạn văn sau: 萧炎望着眼前的云岚宗，眼中闪过一丝冷意..."* -> Chatbot gọi công cụ `create_story_memory` -> Phản hồi tóm tắt thực thể đã lưu.
   - **Kịch bản 2 (Hiệu chỉnh Wiki):** Gửi yêu cầu *"Hãy thêm nhân vật Huân Nhi vào wiki truyện Đấu Phá Thương Khung với danh xưng Tiêu Huân Nhi"* -> Chatbot gọi công cụ `upsert_story_wiki_entity` -> Mở màn hình Story Wiki kiểm tra thấy nhân vật Huân Nhi xuất hiện.
   - **Kịch bản 3 (Sửa lỗi nguồn):** Gửi yêu cầu kiểm tra và sửa nguồn sách bị lỗi rule -> Chatbot gọi `repair_book_source` với selector mới -> Trả về kết quả re-check thành công.
