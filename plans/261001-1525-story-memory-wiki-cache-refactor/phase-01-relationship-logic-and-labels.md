# Phase 01: Khắc phục Lỗi Logic & Nhãn Hiển Thị Bảng Relationship

Status: ✅ Complete
Dependencies: None

## 1. Mục tiêu
Sửa dứt điểm lỗi logic ở bảng Relationship trong Bộ nhớ dịch (Story Memory): hiện tại các trường của quan hệ đang bị gán nhãn và xử lý như "Từ nguồn (Raw) - Bản dịch (Target)", cùng với các controls viết hoa/gợi ý dịch thuật vốn chỉ dành cho từ vựng/thuật ngữ.

## 2. Phân tích nguyên nhân chi tiết
- Trong `AiTranslationStoryRelationship`:
  - `source`: Tên Nhân vật / Thực thể A (ví dụ: "Triệu Kỳ" hoặc "赵奇")
  - `target`: Tên Nhân vật / Thực thể B (ví dụ: "Trí não" hoặc "智脑")
  - `relationship`: Bản chất quan hệ (ví dụ: "Chủ nhân - Hệ thống phụ trợ")
  - `description`: Mô tả chi tiết bối cảnh quan hệ
- Trong `BookStoryMemoryScreen.kt:305-320`:
  - `StoryMemoryEditorDialog` tái sử dụng chung `EditorField(draft.primary, ...)` và gán nhãn `R.string.story_memory_raw` ("Tên hoặc thuật ngữ gốc").
  - `EditorField(draft.secondary, ...)` được gán nhãn `R.string.story_memory_target` ("Bản dịch chuẩn").
  - `TranslationCaseControls` (viết hoa/thường) và các chip gợi ý dịch NMT/AI xuất hiện ngay cả khi đang chỉnh sửa một Mối quan hệ!
- Trong `BookStoryMemoryScreen.kt:399`:
  - **⚠️ GAP phát hiện:** `AiTranslationStoryMemoryKind.RELATIONSHIP -> Unit` — block kind-specific hoàn toàn RỖNG! Không render bất kỳ field nào đặc thù cho Relationship.
- Trong danh sách hiển thị thẻ quan hệ (`BookStoryMemoryViewModel.kt:179`):
  - Tiêu đề thẻ hiển thị `${relationship.source} → ${relationship.target}` nhưng không thể hiện rõ đâu là chiều quan hệ hoặc vai trò hai bên.

## 3. Các bước thực hiện
1. **Tạo tài nguyên chuỗi rõ ràng (strings.xml / values-vi & values):**
   - `story_memory_rel_entity_source`: "Nhân vật / Thực thể 1" ("Entity 1 / Subject")
   - `story_memory_rel_entity_target`: "Nhân vật / Thực thể 2" ("Entity 2 / Object")
   - `story_memory_rel_type`: "Mối quan hệ" ("Relationship")
   - `story_memory_rel_description`: "Chi tiết mối quan hệ" ("Relationship details")
2. **Verify field mapping trong `BookStoryMemoryViewModel.kt`:**
   - Kiểm tra `toDraft()` extension cho Relationship: phải mapping đúng `primary=source`, `secondary=target`, `type=relationship`, `description=description`.
   - Kiểm tra `toRelationship()` extension: phải tái tạo ngược mapping draft -> relationship data class.
   - Nếu mapping sai → sửa lại cho đúng ngữ nghĩa.
3. **Cập nhật `StoryMemoryEditorDialog` trong `BookStoryMemoryScreen.kt`:**
   - Thay thế `AiTranslationStoryMemoryKind.RELATIONSHIP -> Unit` bằng form chuyên dụng:
     - Trường 1: `draft.primary` với nhãn `story_memory_rel_entity_source` (Hỗ trợ gợi ý từ danh sách Entity đã có trong sách).
     - Trường 2: `draft.secondary` với nhãn `story_memory_rel_entity_target` (Hỗ trợ gợi ý từ danh sách Entity đã có trong sách).
     - Trường 3: `draft.type` với nhãn `story_memory_rel_type` (Gợi ý chip các loại quan hệ phổ biến: Sư đồ, Huynh đệ, Vợ chồng/Đạo lữ, Kẻ thù, Đồng minh, Chủ tớ, Môn đồ...).
     - Trường 4: `draft.description` với nhãn `story_memory_rel_description`.
   - Ẩn hoàn toàn `TranslationCaseControls`, `Provider Suggestions`, `Sense Key` khi kind == RELATIONSHIP.
   - Cụ thể: Wrap toàn bộ block lines 309-360 trong `if (draft.kind != RELATIONSHIP)` guard, sau đó thêm block RELATIONSHIP riêng.
4. **Cải tiến hiển thị thẻ quan hệ trong danh sách `BookStoryMemoryScreen.kt` & `StoryWikiScreen.kt`:**
   - Hiển thị badge loại quan hệ rõ ràng: `[Sư đồ] Mặc đại phu ⇄ Hàn Lập` hoặc `Triệu Kỳ ➔ Trí não (Chủ tớ)`.

## 4. Files ảnh hưởng
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-vi/strings.xml`
- `app/src/main/java/io/legado/app/ui/translation/memory/BookStoryMemoryScreen.kt`
- `app/src/main/java/io/legado/app/ui/translation/memory/BookStoryMemoryViewModel.kt`
- `app/src/main/java/io/legado/app/ui/translation/memory/StoryWikiScreen.kt`

## 5. Tiêu chuẩn nghiệm thu (Test Criteria)
- [ ] Mở dialog chỉnh sửa một Relationship trong sách *Chủ Thần Đại Đạo*: 4 trường hiển thị đúng "Nhân vật/Thực thể 1", "Nhân vật/Thực thể 2", "Mối quan hệ", "Chi tiết".
- [ ] Không còn thấy các nút gợi ý dịch thuật hay nút chuyển chữ hoa/chữ thường trong dialog quan hệ.
- [ ] Không còn `Sense Key` field trong dialog quan hệ.
- [ ] Thẻ danh sách quan hệ hiển thị rõ ràng, trực quan, không gây hiểu lầm thành từ điển nguồn - đích.
- [ ] Lưu/sửa relationship qua dialog hoạt động đúng (verify toDraft ↔ toRelationship round-trip).
