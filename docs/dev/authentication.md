# Xác thực và Đăng nhập (Authentication)

[[toc]]

Legado hỗ trợ ba phương thức xác thực trang web: CookieJar, biểu mẫu UI đăng nhập và kịch bản đăng nhập qua URL.

## 1. CookieJar

Sau khi được kích hoạt, hệ thống sẽ tự động lưu giá trị `Set-Cookie` trong mỗi HTTP response header, phù hợp với các trang web yêu cầu duy trì phiên làm việc (Session) (chẳng hạn như khi tải hình ảnh mã xác thực CAPTCHA).

Chỉ cần tích chọn "Bật CookieJar" trong trình chỉnh sửa nguồn sách mà không cần cấu hình thêm gì.

## 2. Giao diện Đăng nhập (Login UI)

Định nghĩa biểu mẫu đăng nhập thông qua một mảng JSON, thay thế cho phương thức đăng nhập bằng WebView tích hợp.

### Định nghĩa trường

| Trường | Kiểu dữ liệu | Bắt buộc | Mô tả |
|:---------|:---------|:---|:-----------------------|
| `name`   | `String` | Có | Tên trường, hiển thị dưới dạng nhãn (label) của hộp nhập liệu |
| `type`   | `String` | Có | Loại trường |
| `action` | `String` | Không | Chỉ dành cho kiểu button: hành động được thực thi khi nhấp chuột |
| `style`  | `Object` | Không | Cấu hình bố cục Flexbox |

### Các giá trị của type

| Giá trị | Mô tả |
|:-----------|:---------------------------------------|
| `text`     | Hộp nhập văn bản |
| `password` | Hộp nhập mật khẩu |
| `button`   | Nút có thể nhấp, nếu `action` là URL sẽ mở trình duyệt, nếu là tên hàm sẽ gọi thực thi JS |

### Ví dụ JSON

```json
[
    { "name": "telephone", "type": "text" },
    { "name": "password", "type": "password" },
    {
        "name": "Đăng ký",
        "type": "button",
        "action": "http://www.example.com/register"
    },
    {
        "name": "Lấy mã xác thực",
        "type": "button",
        "action": "getVerificationCode()",
        "style": {
            "layout_flexGrow": 0,
            "layout_flexShrink": 1,
            "layout_alignSelf": "auto",
            "layout_flexBasisPercent": -1,
            "layout_wrapBefore": false
        }
    }
]
```

### Thuộc tính bố cục style

| Thuộc tính | Kiểu dữ liệu | Mô tả |
|:--------------------------|:----------|:---------------|
| `layout_flexGrow`         | `Int`     | Tỷ lệ giãn nở (flex-grow) |
| `layout_flexShrink`       | `Int`     | Tỷ lệ co lại (flex-shrink) |
| `layout_alignSelf`        | `String`  | Căn chỉnh trên trục phụ (align-self) |
| `layout_flexBasisPercent` | `Int`     | Phần trăm kích thước cơ bản, -1 là tự động |
| `layout_wrapBefore`       | `Boolean` | Có bắt buộc xuống dòng hay không |

::: tip Thay đổi phiên bản
Kể từ phiên bản 20221113, các nút hỗ trợ gọi các hàm được viết trong quy tắc "URL Đăng nhập", bắt buộc phải triển khai hàm `login`.
:::

## 3. URL Đăng nhập (Login URL)

Có thể điền liên kết đăng nhập hoặc mã JavaScript triển khai logic đăng nhập. Khi sử dụng kết hợp với Giao diện Đăng nhập (Login UI), bạn cần triển khai hàm `login`.

### Ví dụ JS

```js
function login() {
    java.log("Mô phỏng yêu cầu đăng nhập");
    java.log(source.getLoginInfoMap());
}
function getVerificationCode() {
    java.log("Nút UI đăng nhập: Lấy số điện thoại " + result.get("telephone"))
}
```

### Lấy thông tin đăng nhập

Trong hàm của nút đăng nhập và hàm `login`, bạn có thể lấy dữ liệu nhập vào của người dùng bằng cách sau:

```js
// Trong hàm của nút đăng nhập
result.get("telephone")

// Trong hàm login
source.getLoginInfo()
source.getLoginInfoMap().get("telephone")
```

### Các phương thức liên quan đến đăng nhập của source

| Phương thức | Giá trị trả về | Mô tả |
|:-------------------------------|:----------|:----------|
| `login()`                      | —         | Thực hiện đăng nhập |
| `getHeaderMap(hasLoginHeader)` | `Map`     | Lấy headers yêu cầu |
| `getLoginHeader()`             | `String?` | Lấy chuỗi headers đăng nhập |
| `getLoginHeaderMap()`          | `Map?`    | Lấy Map của headers đăng nhập |
| `putLoginHeader(header)`       | —         | Lưu headers đăng nhập |
| `removeLoginHeader()`          | —         | Xóa headers đăng nhập |
| `setVariable(variable)`        | —         | Thiết lập biến nguồn |
| `getVariable()`                | `String?` | Lấy biến nguồn |

### Các hàm của AnalyzeUrl

Các hàm sau đây chỉ có hiệu lực trong quy tắc `Kịch bản JS kiểm tra đăng nhập`:

| Phương thức | Giá trị trả về | Mô tả |
|:---------------------------------------------------|:--------------|:--------------------|
| `initUrl()`                                        | —             | Phân tích lại URL |
| `getHeaderMap().putAll(source.getHeaderMap(true))` | —             | Thiết lập lại headers đăng nhập |
| `getStrResponse(jsStr, sourceRegex)`               | `StrResponse` | Trả về kết quả truy cập dưới dạng văn bản (text) |
| `getResponse()`                                    | `Response`    | Trả về kết quả truy cập dưới dạng đối tượng Response |

## 4. Kịch bản JS kiểm tra đăng nhập (Login Check)

Điền mã JavaScript trong trường `Kịch bản JS kiểm tra đăng nhập` của nguồn sách để xác định trạng thái đăng nhập hiện tại. Trả về `true` biểu thị đã đăng nhập thành công, trả về bất kỳ giá trị nào khác sẽ kích hoạt quy trình đăng nhập lại.
