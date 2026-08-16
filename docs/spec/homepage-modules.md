# Quy chuẩn cấu hình Mô-đun Trang chủ (Homepage Modules)

[[toc]]

Trường `homepageModules` của nguồn sách cho phép nhà phát triển khai báo các mô-đun nội dung hiển thị của nguồn đó trên trang chủ. Các mô-đun này được định nghĩa thông qua một mảng JSON, hỗ trợ cấu hình bố cục và nguồn dữ liệu tùy biến linh hoạt.

## 1. Cấu trúc dữ liệu (Data Structure)

`homepageModules` là một mảng JSON chứa nhiều đối tượng định nghĩa mô-đun.

### Các trường chung của mô-đun

| Trường | Kiểu dữ liệu | Bắt buộc | Mô tả |
|:-----------------|:---------|:---|:-------------------------------------------|
| **key**          | `String` | Có | Định danh duy nhất của mô-đun. Khuyến nghị sử dụng các ký tự `[a-z0-9_]`. Dùng để lưu trữ cài đặt sắp xếp/hiển thị-ẩn của người dùng. |
| **type**         | `Enum`   | Có | Loại mô-đun. Định nghĩa phương thức hiển thị (render) và logic tương tác. |
| **title**        | `String` | Có | Tiêu đề mặc định của mô-đun. Người dùng có thể tùy chỉnh ghi đè trên thiết bị cục bộ. |
| **kindTitle**    | `String` | Không | Dùng để so khớp với tiêu đề thể loại trong quy tắc "Khám phá" của nguồn sách. Sau khi khớp thành công, hệ thống tự động kế thừa URL và quy tắc của thể loại đó. |
| **url**          | `String` | Không | Chỉ định rõ ràng URL giao diện dữ liệu. Mức độ ưu tiên cao hơn `kindTitle`. Hỗ trợ thay thế biến. |
| **args**         | `String` | Không | Tham số bổ sung. Trong loại mô-đun `buttonGroup`, đây là một chuỗi mảng JSON. |
| **layoutConfig** | `Object` | Không | Đối tượng cấu hình bố cục, dùng để điều chỉnh số cột, số hàng, biểu tượng (icon), v.v. |

## 2. Các loại mô-đun (Module Types)

### Loại danh sách và xoay vòng (Carousel/List)

| Loại | Mô tả | Đặc điểm |
|:----------|:------|:----------------------|
| `banner`  | Hình ảnh xoay vòng vuốt ngang (Carousel) | Thích hợp để hiển thị các đề xuất chất lượng cao có trọng số lớn, sử dụng ảnh bìa kích thước lớn |
| `ranking` | Danh sách bảng xếp hạng | Hiển thị danh sách dọc, có đi kèm số thứ tự xếp hạng |
| `card`    | Thẻ đề xuất (Card) | Luồng thẻ trượt ngang, hiển thị đồng thời ảnh bìa, tiêu đề và giới thiệu ngắn |

### Loại lưới (Grid)

| Loại | Mô tả | Đặc điểm |
|:---------------|:------|:-----------------|
| `grid`         | Lưới tiêu chuẩn | Hình thức hiển thị phổ biến nhất, hỗ trợ tùy chỉnh số hàng và số cột |
| `gridRanking`  | Bảng xếp hạng dạng lưới | Hiển thị bảng xếp hạng gồm nhiều hàng nhiều cột, hỗ trợ lật trang ngang |
| `infiniteGrid` | Lưới vô tận | Luồng lưới cuộn dọc, hỗ trợ tải nội dung vô hạn |
| `waterfall`    | Bố cục dạng thác nước (Waterfall) | Luồng sách được sắp xếp so le theo chiều dọc, hỗ trợ tải vô hạn |

### Loại tính năng (Function)

| Loại | Mô tả | Đặc điểm |
|:--------------|:------|:-------------------------------------------|
| `buttonGroup` | Nhóm nút phím tắt | Được hiển thị dưới dạng một nhóm các nút tròn/biểu tượng, hỗ trợ tự động điền đầy chiều rộng và tự chia cột. Thường dùng để đặt lối vào của các tính năng hoặc thể loại phổ biến |

## 3. Cấu hình bố cục (LayoutConfig)

Thông qua đối tượng `layoutConfig`, bạn có thể kiểm soát tinh vi biểu hiện của mô-đun.

| Thuộc tính | Kiểu dữ liệu | Loại áp dụng | Giá trị mặc định | Mô tả |
|:----------|:---------|:------------------------------------|:----|:----------------------------------------|
| `columns` | `Int`    | `grid`, `waterfall`, `infiniteGrid` | 3   | Số cột hiển thị trên mỗi hàng |
| `icon`    | `String` | `buttonGroup`                       | -   | URL biểu tượng thống nhất mặc định của nhóm nút |
| `icons`   | `Object` | `buttonGroup`                       | -   | Bảng ánh xạ biểu tượng. Ví dụ: `{"Bảng xếp hạng": "http://path/to/icon"}` |

## 4. Logic liên kết dữ liệu (Data Binding)

1. **Tự động so khớp**: Nếu cung cấp `kindTitle`, hệ thống sẽ duyệt qua danh sách trả về từ `exploreKinds()` của nguồn sách. Nếu `title` của thể loại nào trùng khớp hoàn toàn, mô-đun đó sẽ tự động sử dụng `url` của thể loại đó.
2. **Chỉ định tĩnh**: Nếu cung cấp `url`, hệ thống sẽ trực tiếp yêu cầu URL đó.
3. **Logic hạ cấp**: Nếu `kindTitle` không khớp và không có `url`, mô-đun sẽ quay trở lại (fallback) sử dụng URL khám phá chính `exploreUrl` của nguồn sách.

## 5. Ví dụ JSON hoàn chỉnh

```json
[
  {
    "key": "top_banner",
    "type": "banner",
    "title": "Cực lực đề xuất",
    "kindTitle": "Đề xuất trang chủ"
  },
  {
    "key": "quick_nav",
    "type": "buttonGroup",
    "title": "Điều hướng thể loại",
    "args": "[\"Võ hiệp\", \"Tiên hiệp\", \"Đô thị\", \"Lịch sử\"]",
    "layoutConfig": {
      "icon": "https://example.com/icons/default.png",
      "icons": {
        "Võ hiệp": "https://example.com/icons/wuxia.png"
      }
    }
  },
  {
    "key": "hot_rank",
    "type": "ranking",
    "title": "Bảng xếp hạng độ hot",
    "kindTitle": "Bảng xếp hạng",
    "layoutConfig": {
      "rows": 5
    }
  },
  {
    "key": "explore_waterfall",
    "type": "waterfall",
    "title": "Khám phá thêm",
    "kindTitle": "Tất cả",
    "layoutConfig": {
      "columns": 2
    }
  }
]
```
