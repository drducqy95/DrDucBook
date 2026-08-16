# Hướng dẫn XPath

XPath dùng để chọn phần tử trong HTML/XML theo đường dẫn. Khi CSS selector không đủ chính xác, XPath là lựa chọn tốt.

## Khái niệm cơ bản

- `/` chọn từ gốc tài liệu.
- `//` chọn ở bất kỳ vị trí nào.
- `.` là nút hiện tại.
- `..` là nút cha.
- `@attr` chọn thuộc tính.
- `[1]` chọn phần tử đầu tiên trong nhóm cùng cấp.
- `[last()]` chọn phần tử cuối.
- `contains(., "text")` chọn phần tử có chứa chuỗi.

## Ví dụ

Chọn tất cả liên kết:

```xpath
//a
```

Chọn thuộc tính `href` của liên kết:

```xpath
//a/@href
```

Chọn phần tử có class chứa `book-item`:

```xpath
//*[contains(@class, "book-item")]
```

Chọn tiêu đề trong từng item:

```xpath
.//*[contains(@class, "title")]
```

## Predicate

Điều kiện trong dấu `[]` dùng để lọc kết quả:

```xpath
//a[contains(@href, "/book/")]
```

```xpath
//div[@id="list"]//a[position() <= 10]
```

```xpath
//img[@data-src or @src]
```

## Hàm thường dùng

- `contains(a, b)`: kiểm tra chuỗi có chứa chuỗi khác.
- `starts-with(a, b)`: kiểm tra chuỗi bắt đầu bằng chuỗi khác.
- `normalize-space(a)`: xóa khoảng trắng thừa.
- `string(a)`: chuyển kết quả thành chuỗi.
- `count(nodes)`: đếm số nút.
- `not(expr)`: phủ định điều kiện.

## Mẹo dùng trong nguồn sách

- Với rule danh sách, chọn vùng item trước rồi dùng rule con tương đối bằng dấu `.`.
- Với ảnh lazy load, kiểm tra cả `data-src`, `data-original` và `src`.
- Tránh XPath bắt quá rộng như `//a` nếu trang có nhiều liên kết không phải truyện.
- Nếu trang trả JSON, không nên dùng XPath; hãy dùng JSON path hoặc JavaScript.

