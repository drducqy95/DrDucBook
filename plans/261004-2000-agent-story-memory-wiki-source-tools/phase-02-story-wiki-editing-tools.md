# Phase 02: Công cụ Hiệu chỉnh Wiki Truyện & Phân tích, Tổng hợp Niên biểu Đại sự ký (Story Wiki & Chronicle Synthesis Tools)

## 1. Mục tiêu
1. Cung cấp bộ công cụ toàn diện để Agent và Chatbot có thể trực tiếp thêm, sửa, xóa và tra cứu các mục trong Wiki truyện (Story Wiki): Thực thể (nhân vật, tông môn, địa danh), Thế giới quan (cảnh giới, pháp bảo, quy tắc), và Sơ đồ quan hệ nhân vật.
2. **Khả năng Đọc, Phân tích và Viết lại Niên biểu Đại sự ký (Story Chronicle):** Bổ sung công cụ cho phép Agent đọc dữ liệu timeline các chương, phân tích dòng chảy cốt truyện và **viết lại thành bản Niên biểu tổng hợp mạch lạc, hợp lý** theo các giai đoạn/thời kỳ/đại sự kiện bước ngoặt (ví dụ: *Quyển 1: Thiếu Niên Xuất Sơn*, *Quyển 2: Ma Thú Sơn Mạch*, *Ba Năm Hẹn Ước*...), chứ không phải chỉ chép nguyên timeline vụn vặt từng chương.

---

## 2. Thiết kế chi tiết các công cụ

### 2.1. Nhóm Công cụ Đọc, Phân tích & Viết lại Niên biểu Đại sự ký (Chronicle Synthesis)

#### a) `get_story_chronicle` (Đọc Niên biểu truyện)
- **Đầu vào:**
  - `bookUrl` (String, tùy chọn) hoặc `bookName` (String, tùy chọn).
  - `includeRawTimelines` (Boolean, mặc định false): Nếu true, trả về cả timeline chi tiết từng chương làm dữ liệu gốc để phân tích.
- **Quy trình xử lý:**
  1. Xác định cuốn sách theo `bookUrl` hoặc `bookName`.
  2. Tải snapshot ký ức cốt truyện của cuốn sách.
  3. Đọc dữ liệu Niên biểu đã tổng hợp (`StoryChronicle`) nếu có.
  4. Nếu chưa có Niên biểu tổng hợp, gom nhóm các sự kiện timeline và trả về dữ liệu chuẩn bị cho bước phân tích.
- **Rủi ro:** `AgentActionRisk.READ` (An toàn, tự duyệt).

#### b) `synthesize_story_chronicle` (Phân tích & Viết lại Niên biểu Đại sự ký)
- **Đầu vào:**
  - `bookUrl` (String, tùy chọn) hoặc `bookName` (String, tùy chọn).
  - `directive` (String, tùy chọn): Chỉ dẫn định hướng cách viết niên biểu (ví dụ: *"Tổng hợp theo các mốc đột phá cảnh giới và biến cố tông môn"*, *"Phân chia theo từng quyển hoặc từng map/thế giới"*).
  - `startChapter` (Int, tùy chọn, mặc định 0).
  - `endChapter` (Int, tùy chọn).
- **Quy trình xử lý:**
  1. Đọc toàn bộ timeline các chương trong khoảng chỉ định kèm danh sách nhân vật, sự kiện và tiêu đề chương thực tế.
  2. Gửi dữ liệu vào AI prompt chuyên sâu về **Biên niên sử tiểu thuyết (Story Chronicler)**:
     - Yêu cầu AI không sao chép nguyên văn timeline từng chương.
     - Phân tích và phân đoạn cốt truyện thành các **Thời kỳ / Giai đoạn (Eras / Story Arcs)** hợp lý.
     - Mỗi giai đoạn gồm:
       - `eraTitle`: Tên giai đoạn (ví dụ: "Thời kỳ Ô Thản Thành: Thiếu niên trầm luân & Khởi sắc").
       - `chapterRange`: Khoảng chương thực tế (ví dụ: "Chương 1 - Chương 53").
       - `eraSummary`: Bối cảnh và tóm lược biến chuyển cốt truyện của giai đoạn.
       - `milestoneEvents`: Các đại sự kiện mang tính bước ngoặt (Turning Points), biến cố lớn, kết quả trận chiến.
       - `keyCharacters`: Các nhân vật trọng tâm xuất hiện trong giai đoạn này.
  3. Tự động lưu bản Niên biểu tổng hợp này vào cơ sở dữ liệu (`ai_memory` với prefix `chronicle_`) và đồng bộ vào Story Wiki.
  4. Trả về cấu trúc JSON Niên biểu hoàn chỉnh đã được tinh chỉnh cho người dùng và Agent.
- **Rủi ro:** `AgentActionRisk.WRITE`.
- **Nhóm quyền:** `ChatbotToolCategory.DICTIONARY_AND_MEMORY`.

#### c) `save_story_chronicle` (Lưu hoặc chỉnh sửa thủ công một mốc Niên biểu)
- **Đầu vào:**
  - `bookUrl` (hoặc `bookName`).
  - `eraTitle` (String, bắt buộc): Tên giai đoạn/thời kỳ.
  - `chapterRange` (String, bắt buộc): Khoảng chương (ví dụ: "Chương 1 - Chương 50").
  - `eraSummary` (String, bắt buộc): Tóm tắt nội dung giai đoạn.
  - `milestoneEvents` (List<String>, tùy chọn): Các sự kiện bước ngoặt.
  - `keyCharacters` (List<String>, tùy chọn): Nhân vật chính trong giai đoạn.
- **Quy trình xử lý:** Lưu mốc giai đoạn vào DB và làm mới hiển thị Story Wiki.
- **Rủi ro:** `AgentActionRisk.WRITE`.
- **Nhóm quyền:** `ChatbotToolCategory.DICTIONARY_AND_MEMORY`.

---

### 2.2. Nhóm Công cụ Hiệu chỉnh Thực thể, Thế giới quan & Quan hệ Wiki

#### a) `upsert_story_wiki_entity` (Tạo/cập nhật Thực thể)
- **Đầu vào:**
  - `bookUrl` (String, tùy chọn) hoặc `bookName`.
  - `raw` (String, bắt buộc): Tên gốc Hán/CJK.
  - `target` (String, bắt buộc): Tên dịch tiếng Việt chuẩn.
  - `senseKey` (String, tùy chọn).
  - `kind` (String, tùy chọn: `CHARACTER`, `LOCATION`, `FACTION`, `OBJECT`, `TERM`, mặc định `CHARACTER`).
  - `aliases` (List<String>, tùy chọn): Danh xưng, biệt hiệu.
  - `summary` (String, tùy chọn): Tiểu sử/mô tả.
  - `gender` (String, tùy chọn: `male`, `female`, `unknown`).
  - `traits` (List<String>, tùy chọn): Đặc điểm tính cách.
- **Quy trình xử lý:** Lưu vào Room DB và đồng bộ ngay vào từ điển dịch (`QuickDictionary`).
- **Rủi ro:** `AgentActionRisk.WRITE`.
- **Nhóm quyền:** `ChatbotToolCategory.DICTIONARY_AND_MEMORY`.

#### b) `delete_story_wiki_entity` (Xóa Thực thể)
- **Đầu vào:** `bookUrl`, `raw`, `senseKey`.
- **Rủi ro:** `AgentActionRisk.DELETE`.
- **Nhóm quyền:** `ChatbotToolCategory.DICTIONARY_AND_MEMORY`.

#### c) `upsert_story_wiki_world` (Tạo/cập nhật Thế giới quan)
- **Đầu vào:**
  - `bookUrl` (hoặc `bookName`).
  - `term` (String, bắt buộc): Thuật ngữ gốc.
  - `vietnamese` (String, bắt buộc): Tên tiếng Việt.
  - `category` (String, tùy chọn: `REALM`, `SECT`, `LOCATION`, `ITEM`, `RULE`, `OTHER`).
  - `description` (String, tùy chọn).
  - `rules` (List<String>, tùy chọn): Thuộc tính, điều kiện cảnh giới.
- **Rủi ro:** `AgentActionRisk.WRITE`.
- **Nhóm quyền:** `ChatbotToolCategory.DICTIONARY_AND_MEMORY`.

#### d) `delete_story_wiki_world` (Xóa Thế giới quan)
- **Đầu vào:** `bookUrl`, `term`, `category`.
- **Rủi ro:** `AgentActionRisk.DELETE`.
- **Nhóm quyền:** `ChatbotToolCategory.DICTIONARY_AND_MEMORY`.

#### e) `upsert_story_wiki_relationship` (Tạo/cập nhật Quan hệ nhân vật)
- **Đầu vào:**
  - `bookUrl` (hoặc `bookName`).
  - `source` (String, bắt buộc): Nhân vật A.
  - `target` (String, bắt buộc): Nhân vật B.
  - `relationship` (String, bắt buộc): Loại quan hệ (Sư đồ, Huynh đệ, Kẻ thù...).
  - `description` (String, tùy chọn): Chi tiết quan hệ.
  - `weight` (Int, mặc định 1): Trọng số gắn kết.
- **Rủi ro:** `AgentActionRisk.WRITE`.
- **Nhóm quyền:** `ChatbotToolCategory.DICTIONARY_AND_MEMORY`.

#### f) `delete_story_wiki_relationship` (Xóa Quan hệ nhân vật)
- **Đầu vào:** `bookUrl`, `source`, `target`, `relationship`.
- **Rủi ro:** `AgentActionRisk.DELETE`.
- **Nhóm quyền:** `ChatbotToolCategory.DICTIONARY_AND_MEMORY`.

#### g) `get_story_wiki` (Tra cứu thông tin Wiki tổng hợp)
- **Đầu vào:** `bookUrl` (hoặc `bookName`), `query` (tùy chọn), `tab` (tùy chọn: `ENTITY`, `WORLD`, `GRAPH`, `CHRONICLE`, `TIMELINE`).
- **Rủi ro:** `AgentActionRisk.READ` (Tự duyệt).

---

## 3. Tiêu chí kiểm thử
- Gửi yêu cầu qua Chatbot: *"Hãy phân tích các chương đã đọc và viết lại niên biểu cho truyện Đấu Phá Thương Khung"* -> Chatbot gọi `synthesize_story_chronicle`, đọc dữ liệu timeline, phân tích thành các thời kỳ lớn mạch lạc có mốc chương chính xác và lưu vào Story Wiki.
- Mở tab Niên biểu trên Story Wiki -> Hiển thị danh sách các thời kỳ/đại sự kiện mạch lạc, đẹp mắt, có số chương thực tế thay vì từng mẩu vụn 1, 2, 3, 4.
- Thêm/sửa nhân vật, thế giới quan, quan hệ -> Dữ liệu cập nhật ngay tức thì trên Story Wiki.
