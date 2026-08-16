# Rule thay thế và làm sạch

Rule thay thế dùng để xóa quảng cáo, sửa lỗi chữ, chuẩn hóa ký tự hoặc làm sạch nội dung trước khi đọc/dịch.

## Thành phần rule

- Tên rule.
- Nhóm rule.
- Mẫu cần tìm.
- Nội dung thay thế.
- Phạm vi áp dụng: toàn cục, theo nguồn hoặc theo truyện.
- Tùy chọn dùng regex.

## Ví dụ

Xóa dòng quảng cáo cố định:

```text
Truyện được đăng tại example
```

Thay nhiều khoảng trắng bằng một khoảng trắng:

```regex
\s+
```

## Lưu ý

- Nếu phần thay thế để trống, app sẽ xóa nội dung khớp.
- Rule quá rộng có thể xóa nhầm nội dung truyện.
- Nên thử trên một chương trước khi bật toàn cục.
- Nếu thấy mất chữ hoặc sai đoạn, tắt rule làm sạch rồi tải lại chương để kiểm tra.

