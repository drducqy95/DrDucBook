# Biên tập và xuất ebook

Ebook Editor hỗ trợ tạo dự án mới, clone sách đã tải, chỉnh block, xem trước và xuất TXT/EPUB.

## Nội dung

Các block gồm đoạn văn, tiêu đề, ảnh, trích dẫn, danh sách, code, đường phân cách và ngắt trang. Lịch sử undo/redo và thứ tự đọc được lưu trong dự án.

## Reflow và fixed-layout

- **REFLOW** phù hợp truyện chữ và thay đổi theo màn hình người đọc.
- **FIXED_PAGE** cho phép đặt hình học block theo trang, phù hợp nội dung minh họa.

Fixed-layout được quản lý bằng cờ trong **Cài đặt > Thử nghiệm**. Dự án fixed-layout cũ vẫn mở và xuất được khi cờ tắt, nhưng không thể chuyển dự án mới sang chế độ này.

## Clone và giới hạn nguồn

Sách từ nguồn Legado và nội dung người dùng tự viết luôn được phép. Sách tải từ extension VBook ngoài bị khóa clone/xuất cho đến khi nhập đúng mã mở khóa trong cài đặt xuất.

## Xuất

Chạy kiểm tra dự án trước khi xuất. TXT cảnh báo mất bố cục fixed-layout; EPUB giữ metadata, ảnh và CSS dàn trang.

> Ảnh minh họa: `docs/assets/guide/ebook-editor-fixed-layout.png`
