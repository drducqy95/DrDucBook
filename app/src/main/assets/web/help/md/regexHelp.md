# Hướng dẫn regex

Regex dùng để tìm, thay thế hoặc trích xuất văn bản theo mẫu. Đây là công cụ mạnh nhưng dễ bắt nhầm nếu viết quá rộng.

## Ký hiệu cơ bản

- `.` khớp một ký tự bất kỳ.
- `*` lặp 0 hoặc nhiều lần.
- `+` lặp 1 hoặc nhiều lần.
- `?` lặp 0 hoặc 1 lần, hoặc chuyển sang chế độ ngắn nhất khi đặt sau lượng từ.
- `\d` khớp chữ số.
- `\s` khớp khoảng trắng.
- `\w` khớp chữ, số hoặc gạch dưới.
- `[abc]` khớp một trong các ký tự bên trong.
- `[^abc]` khớp ký tự không nằm trong nhóm.
- `( ... )` tạo nhóm bắt.
- `|` nghĩa là hoặc.

## Ví dụ

Xóa khoảng trắng dư:

```regex
\s+
```

Tìm dòng bắt đầu bằng tiêu đề chương:

```regex
^\s*(Chương|Chapter)\s+\d+
```

Lấy nội dung giữa hai mốc:

```regex
start([\s\S]*?)end
```

## Lưu ý khi dùng trong app

- Nếu muốn xóa cụm, để phần thay thế trống.
- Nếu muốn dùng nhóm bắt trong thay thế, kiểm tra cú pháp của màn đang dùng.
- Với nội dung nhiều dòng, dùng mẫu tương thích nhiều dòng như `[\s\S]*?`.
- Luôn thử trên đoạn nhỏ trước khi áp dụng toàn bộ nguồn hoặc toàn bộ sách.

