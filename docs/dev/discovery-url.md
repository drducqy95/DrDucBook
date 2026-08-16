# Quy chuẩn cấu hình URL Khám phá (Discovery URL)

[[toc]]

Trường "URL Khám phá" của nguồn sách hỗ trợ định nghĩa nhiều lối vào trang khám phá thông qua một mảng JSON. Mỗi lối vào bao gồm tiêu đề, URL và kiểu bố cục tùy chọn. Điều này cho phép một nguồn cung cấp nhiều phím tắt để truy cập nhanh các thể loại hoặc bảng xếp hạng khác nhau.

## 1. Vị trí trường

Trong tab **Khám phá** của trình chỉnh sửa nguồn sách:

```
Khám phá → URL Khám phá
```

## 2. Cấu trúc dữ liệu

URL Khám phá là một mảng JSON chứa nhiều đối tượng lối vào.

### Các trường của lối vào

| Trường | Kiểu dữ liệu | Bắt buộc | Mô tả |
|:--------|:---------|:---|:--------------------------|
| `title` | `String` | Có | Tiêu đề lối vào, hiển thị trên danh sách trang khám phá |
| `url`   | `String` | Có | URL đích, hỗ trợ biến phân trang `{{page}}` |
| `style` | `Object` | Không | Cấu hình bố cục Flexbox |

### Thuộc tính bố cục style

| Thuộc tính | Kiểu dữ liệu | Mô tả |
|:--------------------------|:----------|:---------------|
| `layout_flexGrow`         | `Int`     | Tỷ lệ giãn nở (flex-grow) |
| `layout_flexShrink`       | `Int`     | Tỷ lệ co lại (flex-shrink) |
| `layout_alignSelf`        | `String`  | Căn chỉnh trên trục phụ (align-self) |
| `layout_flexBasisPercent` | `Int`     | Phần trăm kích thước cơ bản, -1 là tự động |
| `layout_wrapBefore`       | `Boolean` | Có bắt buộc xuống dòng hay không |

## 3. Ví dụ JSON hoàn chỉnh

```json
[
  {
    "title": "Bảng độ hot",
    "url": "https://example.com/rank/hot?page={{page}}"
  },
  {
    "title": "Bảng sách mới",
    "url": "https://example.com/rank/new?page={{page}}"
  },
  {
    "title": "Bảng hoàn thành",
    "url": "https://example.com/rank/finish?page={{page}}"
  }
]
```

## 4. Hai loại định dạng

URL Khám phá hỗ trợ hai định dạng nhập liệu:

| Định dạng | Mô tả | Ngữ cảnh áp dụng |
|:--------|:-----------------------------------------|:----------|
| Chuỗi văn bản | Điền trực tiếp URL, ví dụ: `https://example.com/explore` | Trang khám phá đơn lẻ |
| Mảng JSON | Định nghĩa nhiều lối vào | Nhiều lối vào thể loại/bảng xếp hạng |

## 5. Biến phân trang

Có thể sử dụng biến `{{page}}` trong URL để thực hiện phân trang, Legado sẽ tự động tăng số trang lên:

```
https://example.com/rank?page={{page}}
```

## 6. Mối quan hệ với các mô-đun trang chủ

Các lối vào được định nghĩa trong URL Khám phá có thể được tham chiếu bởi trường `kindTitle` của [mô-đun trang chủ](../spec/homepage-modules). Khi trường `kindTitle` của mô-đun trang chủ khớp hoàn toàn với `title` của một lối vào khám phá nhất định, mô-đun sẽ tự động sử dụng URL và quy tắc của lối vào đó.
