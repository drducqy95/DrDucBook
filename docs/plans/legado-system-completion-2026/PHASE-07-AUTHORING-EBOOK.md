# Phase 07 — Sáng tác và Ebook Editor

## 1. Mục tiêu

Biến Writing/Ebook Editor thành workspace có cấu trúc, preview/export ổn định, AI hỗ trợ nhưng không ghi đè dữ liệu người dùng.

## 2. Phạm vi ảnh hưởng

`domain/model/AuthoringProject`, `AuthoringProjectRepository`, Writing/Ebook Editor UI/ViewModel, project JSON schema, assets, shared layout renderer, export writer, Reader drop-cap và VBook content lock.

Phụ thuộc Phase 01 cho AI suggestion, Phase 03 cho raw/cache/final và Phase 00 cho mapping/import reliability.

## 3. Task

### C07.01 — Pre-writing workspace

- **Mục tiêu:** outline, character, setting, chapter plan và AI suggestion có trạng thái riêng.
- **Ví dụ:** AI đề xuất dàn ý nhưng user sửa trước khi accept; job bị cancel giữa chừng.
- **Thông qua:** suggestion không tự ghi; user accept/reject/edit được; project JSON backup/restore pass.

### C07.02 — Block document model

- **Mục tiêu:** lưu ebook như document blocks thay vì một chuỗi text duy nhất.
- **Thực hiện:** schema version, paragraph/image/heading/dropcap/spacer blocks, geometry/layer metadata, asset reference và migration JSON.
- **Thông qua:** decode project cũ; block reorder/edit/delete không mất asset; schema invalid có recovery.

### C07.03 — Canvas, layer và chapter manager

- **Mục tiêu:** editor có fixed-layout canvas, layer panel, chapter CRUD/reorder.
- **Ví dụ:** kéo ảnh, sửa text, đổi z-order, đổi chapter khi đang dirty.
- **Thông qua:** phone/tablet layout không overlap; unsaved dialog đúng; selection và undo/redo không sai block.

### C07.04 — Shared renderer và preview

- **Mục tiêu:** Reader preview và exporter dùng cùng layout policy.
- **Thông qua:** HTML/EPUB/PDF/CBZ/TXT giữ title, images, drop-cap, line-break và style; golden render không lệch ngoài tolerance.

### C07.05 — Validation và export

- **Mục tiêu:** phát hiện block hỏng, asset thiếu, chapter rỗng trước export.
- **Ví dụ:** image path mất, duplicate chapter ID, unsupported font, content quá lớn.
- **Thông qua:** validation trả lỗi theo block/chapter; export atomic; file output đọc được bằng instrumentation test.

### C07.06 — VBook lock và drop-cap

- **Mục tiêu:** giữ chính sách khóa sách VBook ngoài khi chưa unlock; Legado source và user-written source luôn được phép.
- **Thông qua:** clone/export VBook bị chặn cả qua UI lẫn use case; unlock code đúng mới mở; Reader và Ebook Editor render drop-cap thống nhất.

## 4. Điều kiện đóng

Authoring unit/UI/instrumentation/render/export pass; project cũ không mất dữ liệu; AI không tự ghi đè; VBook lock không thể bypass bằng gateway trực tiếp.

