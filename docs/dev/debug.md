# Gỡ lỗi Nguồn sách (Source Debugging)

Sau khi viết xong nguồn sách, bạn có thể kiểm tra từng quy tắc xem có đúng hay không thông qua tính năng gỡ lỗi tích hợp của Legado. Nút lối vào gỡ lỗi nằm ở nút "Gỡ lỗi" (Debug) dưới cùng của trang chỉnh sửa nguồn sách.

[[toc]]

## Gỡ lỗi Tìm kiếm

Nhập từ khóa tìm kiếm để xác minh xem quy tắc tìm kiếm có trả về danh sách sách chính xác hay không.

**Ví dụ nhập liệu:**

```
Hệ thống
```

## Gỡ lỗi Khám phá

Nhập URL trang khám phá, hỗ trợ biến phân trang `{{page}}`. Định dạng là `Tiêu đề::URL`.

**Ví dụ nhập liệu:**

```
Bảng xếp hạng::https://www.example.com/rank?page={{page}}
```

## Gỡ lỗi Trang chi tiết

Nhập trực tiếp URL trang chi tiết sách để kiểm tra xem các quy tắc ở trang chi tiết có trích xuất đúng tên sách, tác giả, giới thiệu và các thông tin khác hay không.

**Ví dụ nhập liệu:**

```
https://www.example.com/book/12345
```

## Gỡ lỗi Trang mục lục

Nhập URL trang mục lục, tiền tố `++` biểu thị sử dụng phương thức khớp Regex.

**Ví dụ nhập liệu:**

```
++https://www.example.com/read/12345
```

## Gỡ lỗi Trang nội dung

Nhập URL trang nội dung, tiền tố `--` biểu thị sử dụng phương thức khớp Regex.

**Ví dụ nhập liệu:**

```
--https://www.example.com/chapter/12345/67890
```

::: tip Mẹo gỡ lỗi
- Mỗi mục gỡ lỗi chạy độc lập, giúp bạn dễ dàng tìm lỗi từng phần.
- Kết quả gỡ lỗi hiển thị trực tiếp nội dung trích xuất được để đối chiếu với kết quả kỳ vọng.
- Nếu quy tắc sử dụng JS, bạn có thể xem các dòng log do `java.log()` xuất ra khi gỡ lỗi.
:::
