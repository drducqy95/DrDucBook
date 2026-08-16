# Quy chuẩn cấu hình Sách liên quan (Related Books)

[[toc]]

Trường `ruleBookInfo.relatedBooks` của nguồn sách cho phép nhà phát triển khai báo một nhóm các mô-đun sách liên quan để hiển thị một thanh trượt ngang dạng danh mục "Sách liên quan" dưới đáy trang chi tiết sách. Tính năng này hỗ trợ cấu hình nhiều mô-đun, mỗi mô-đun có tiêu đề và nguồn dữ liệu độc lập, chẳng hạn như "Tác phẩm cùng tác giả", "Người đọc sách này cũng đọc", v.v.

## 1. Vị trí trường

Trường `relatedBooks` mới được thêm vào trong tab **Trang chi tiết** của trình chỉnh sửa nguồn sách.

```
Trang chi tiết → relatedBooks
```

Đường dẫn JSON tương ứng: `ruleBookInfo.relatedBooks`

## 2. Cấu trúc dữ liệu

`relatedBooks` là một mảng JSON chứa nhiều đối tượng định nghĩa mô-đun.

### Các trường của mô-đun

| Trường | Kiểu dữ liệu | Bắt buộc | Mô tả |
|:----------|:---------|:---|:------------------------------------------|
| **key**   | `String` | Không | Định danh duy nhất của mô-đun. Khuyến nghị sử dụng các ký tự `[a-z0-9_]`. Nếu không cung cấp, hệ thống sẽ sử dụng `title` làm định danh. |
| **title** | `String` | Có | Tiêu đề mô-đun, hiển thị phía trên thanh trượt ngang. Ví dụ: "Tác phẩm cùng tác giả". |
| **url**   | `String` | Có | URL của giao diện dữ liệu. Hỗ trợ thay thế bằng biến mẫu. |

## 3. Nguyên lý hoạt động

1. Khi người dùng mở trang chi tiết của một cuốn sách nào đó, nếu nguồn sách đó có cấu hình `relatedBooks`, hệ thống sẽ phân tích cú pháp mảng JSON.
2. Đối với mỗi mô-đun, hệ thống sẽ thay thế các biến mẫu trong URL bằng các giá trị thực tế của cuốn sách hiện tại, sau đó thực hiện yêu cầu (request) gửi đi.
3. Dữ liệu phản hồi trả về sẽ được phân tích cú pháp bằng **Quy tắc Khám phá** (`ruleExplore`) của nguồn sách để lấy danh sách sách.
4. Các cuốn sách sau khi phân tích sẽ được hiển thị dưới dạng thanh trượt ngang nằm giữa các nút thao tác và phần giới thiệu tóm tắt của cuốn sách tại trang chi tiết.
5. Cuốn sách hiện đang xem sẽ tự động được lọc khỏi kết quả hiển thị để tránh bị lặp lại.
6. Nếu một mô-đun nào đó yêu cầu thất bại hoặc trả về danh sách rỗng, mô-đun đó sẽ bị bỏ qua trong im lặng mà không ảnh hưởng đến các mô-đun khác hoặc các tính năng của trang chi tiết.

::: warning Chú ý
Việc phân tích cú pháp sách liên quan tái sử dụng **Quy tắc Khám phá** (`ruleExplore`), chứ không phải **Quy tắc Trang chi tiết** (`ruleBookInfo`). Vui lòng đảm bảo quy tắc khám phá của nguồn sách đã được cấu hình chính xác.
:::

## 4. Cú pháp URL

URL hỗ trợ cú pháp JS tương tự như `exploreUrl`, bao gồm tiền tố `@js:`, thẻ `<js></js>` và biểu thức nội bộ `{{...}}`.

### Các đối tượng khả dụng trong ngữ cảnh JS

| Đối tượng | Mô tả | Thuộc tính ví dụ |
|:---------|:----------|:--------------------------------------------------------------------------------------|
| `book`   | Đối tượng sách hiện tại | `book.name`, `book.author`, `book.kind`, `book.bookUrl`, `book.tocUrl`, `book.origin` |
| `source` | Đối tượng nguồn sách hiện tại | `source.bookSourceUrl`, `source.getVariable()` v.v. |
| `cookie` | Bộ lưu trữ Cookie | `cookie.getKey(domain, key)` |
| `page`   | Số trang (Cố định là 1) | `page` |
| `java`   | Công cụ mở rộng JS | `java.ajax()`, `java.log()` v.v. |

### Cú pháp mẫu đơn giản

Đối với các URL đơn giản, bạn có thể sử dụng trực tiếp cú pháp `{{book.ten_thuoc_tinh}}`, giá trị sẽ tự động được mã hóa URL (URL encode):

```
https://example.com/search?keyword={{book.author}}&name={{book.name}}
```

### Biểu thức `@js:`

Đối với các URL cần xử lý logic, hãy sử dụng tiền tố `@js:`:

```
@js:"https://example.com/api/related?author=" + java.net.URLEncoder.encode(book.author, "UTF-8")
```

## 5. Ví dụ JSON hoàn chỉnh

```json
[
  {
    "key": "same_author",
    "title": "Tác phẩm cùng tác giả",
    "url": "https://example.com/search?keyword={{book.author}}&type=author&page=1"
  },
  {
    "key": "readers_also_read",
    "title": "Người đọc sách này cũng đọc",
    "url": "https://example.com/api/related?book={{book.bookUrl}}&limit=20"
  },
  {
    "key": "same_genre",
    "title": "Đề xuất cùng loại",
    "url": "https://example.com/category/{{book.kind}}?page=1"
  }
]
```

Cấu hình trên sẽ hiển thị ba hàng trượt ngang dưới đáy trang chi tiết:

```
┌─────────────────────────────────────────────┐
│ [Khu vực nút thao tác]                      │
├─────────────────────────────────────────────┤
│ Tác phẩm cùng tác giả                       │
│ [Ảnh bìa 1] [Ảnh bìa 2] [Ảnh bìa 3] [Ảnh bìa 4] →             │
│                                             │
│ Người đọc sách này cũng đọc                 │
│ [Ảnh bìa 1] [Ảnh bìa 2] [Ảnh bìa 3] [Ảnh bìa 4] →             │
│                                             │
│ Đề xuất cùng loại                           │
│ [Ảnh bìa 1] [Ảnh bìa 2] [Ảnh bìa 3] [Ảnh bìa 4] →             │
├─────────────────────────────────────────────┤
│ [Khu vực tóm tắt sách]                      │
└─────────────────────────────────────────────┘
```

## 6. Các ví dụ về URL

### Tìm sách liên quan theo tác giả

```
https://example.com/search?keyword={{book.author}}&type=author
```

### Tìm tác phẩm cùng hệ liệt theo tên sách

```
https://example.com/search?keyword={{book.name}}&type=related
```

### Tìm sách cùng thể loại

```
https://example.com/category/{{book.kind}}?page=1
```

### Sử dụng biểu thức JS

**Ghép nối đơn giản:**

```
@js:"https://example.com/api/related?author=" + java.net.URLEncoder.encode(book.author, "UTF-8") + "&book_id=" + book.bookUrl.split("/").pop()
```

**Kèm logic điều kiện:**

```
@js:
var base = "https://example.com/api/related";
if (book.kind && book.kind.contains("玄幻")) {
  base + "?genre=fantasy&author=" + java.net.URLEncoder.encode(book.author, "UTF-8")
} else {
  base + "?author=" + java.net.URLEncoder.encode(book.author, "UTF-8")
}
```

## 7. Logic hiển thị

| Điều kiện | Hành vi |
|:----------------------|:-----------------|
| `relatedBooks` để trống hoặc không cấu hình | Không hiển thị mô-đun sách liên quan |
| Định dạng JSON bị lỗi | Bỏ qua trong im lặng, không ảnh hưởng đến nội dung khác của trang chi tiết |
| Yêu cầu URL của một mô-đun nào đó thất bại | Bỏ qua mô-đun đó, các mô-đun khác hiển thị bình thường |
| Một mô-đun nào đó trả về danh sách rỗng | Bỏ qua mô-đun đó, các mô-đun khác hiển thị bình thường |
| Tất cả các mô-đun đều không có kết quả | Không hiển thị khu vực sách liên quan |
| Kết quả có bao gồm cuốn sách hiện tại | Tự động lọc bỏ cuốn sách hiện tại |
| Sách cục bộ (không có nguồn) | Không hiển thị mô-đun sách liên quan |
| Khi chuyển đổi nguồn | Làm trống danh sách sách liên quan, tải lại dữ liệu từ nguồn mới |

## 8. Thực tiễn tốt nhất (Best Practices)

1. **Thiết lập số lượng mô-đun hợp lý**: Khuyến nghị từ 1-3 mô-đun, việc có quá nhiều hàng trượt ngang sẽ ảnh hưởng đến trải nghiệm giao diện người dùng.
2. **Sử dụng tiêu đề có ý nghĩa**: Tiêu đề nên mô tả rõ ràng nguồn gốc đề xuất, ví dụ "Tác phẩm cùng tác giả" sẽ có tính định hướng tốt hơn là từ "Đề xuất" chung chung.
3. **Ưu tiên sử dụng tác giả hoặc thể loại**: Tìm kiếm theo tác giả là phương thức liên kết phổ biến nhất.
4. **Tránh truy vấn quá rộng**: Nếu kết quả URL trả về không liên quan nhiều đến cuốn sách hiện tại, trải nghiệm người dùng sẽ bị giảm sút.
5. **Đảm bảo tính tương thích của quy tắc khám phá**: Dữ liệu do URL trả về phải có khả năng được quy tắc `ruleExplore` phân tích cú pháp chính xác.
6. **Sử dụng mẫu cho kịch bản đơn giản, sử dụng JS cho kịch bản phức tạp**: Thực hiện thay thế đơn giản với cú pháp mẫu `{{book.author}}`; sử dụng biểu thức `@js:` khi cần các logic phức tạp như câu lệnh điều kiện.
7. **Kiểm tra các trường hợp biên**: Kiểm tra xem URL có hoạt động bình thường khi tên tác giả chứa các ký tự đặc biệt hay không.
8. **Kiểm soát số lượng trả về**: Khuyến nghị phía máy chủ (server) giới hạn số lượng kết quả trả về (ví dụ khoảng 10-20 cuốn).
