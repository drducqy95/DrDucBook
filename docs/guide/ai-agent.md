# AI Agent, tool và quyền xác nhận

AI Agent có thể tìm web, tìm nguồn sách, thêm sách, đọc/sửa từ điển, làm việc với dự án sáng tác và quản lý skill/plugin.

## Cấu hình

1. Thiết lập provider/model trong **AI Router**.
2. Vào **AI Agent**, chọn model lẻ hoặc combo fallback.
3. Bật bong bóng chat nếu muốn gọi Agent từ màn hình khác.

## Quyền tool

- Tool chỉ đọc được chạy trực tiếp.
- Tool ghi, xóa, tải dữ liệu hoặc cài plugin luôn tạo bản xem trước và yêu cầu xác nhận.
- Mã xác nhận có thời hạn, gắn với đúng tham số và chỉ dùng một lần.
- Agent chặn URL loopback/mạng nội bộ và đường dẫn file vượt khỏi phạm vi cho phép.

Các nhóm tool nguy hiểm, skill và plugin có cờ riêng trong **Cài đặt > Thử nghiệm**. Khi cờ tắt, tool không được gửi cho model và cũng bị chặn nếu bị gọi trực tiếp.

## Bộ nhớ và lịch sử

Agent lưu memory có chủ đích, hỗ trợ tìm kiếm FTS và ghi trace đã che credential. Xóa cuộc trò chuyện không tự xóa dữ liệu sách hoặc từ điển.

> Ảnh minh họa: `docs/assets/guide/ai-agent-permission.png`
