# Chi tiết Cú pháp Quy tắc (Rule Syntax)

[[toc]]

Legado hỗ trợ nhiều cú pháp quy tắc khác nhau, có thể được sử dụng kết hợp trong các trường khác nhau của nguồn sách. Tài liệu này giới thiệu chi tiết về cách viết và ngữ cảnh sử dụng của từng loại cú pháp.

## 1. Quy tắc mặc định của JSOUP (Default)

Là cú pháp bộ chọn CSS (CSS Selector) với dấu phân cách `@`, đây là loại quy tắc được sử dụng phổ biến nhất trong Legado.

### Cấu trúc cơ bản

Mỗi đoạn quy tắc có thể được chia thành 3 phần, kết nối với nhau bằng dấu `.`:

```
Kiểu.Tên.Vị_trí
```

| Phần | Mô tả | Ví dụ |
|:---|:-----------------|:-------------------------------------|
| Kiểu | Loại bộ chọn | `class`, `id`, `tag`, `text`, `children` |
| Tên | Giá trị khớp | `odd`, `content`, `a` |
| Vị trí | Chỉ mục (Bắt đầu từ 0, số âm biểu thị đếm ngược từ cuối) | `0`, `-1` |

**Ví dụ:**

```
class.odd.0@tag.a.0@text    // Lấy văn bản của thẻ <a> đầu tiên bên dưới phần tử đầu tiên có class="odd"
tag.div.-1@text              // Lấy văn bản của thẻ <div> cuối cùng
```

### Phần cuối cùng dùng để lấy nội dung

| Giá trị | Mô tả |
|:------------|:------------|
| `text`      | Nội dung văn bản |
| `textNodes` | Danh sách các nút văn bản (Text Nodes) |
| `ownText`   | Văn bản của chính phần tử đó (không bao gồm các phần tử con) |
| `href`      | Địa chỉ liên kết |
| `src`       | Địa chỉ hình ảnh/tài nguyên |
| `html`      | Nội dung HTML |
| `all`       | Toàn bộ nội dung |

### Ký hiệu loại trừ `!`

Sử dụng `!` để loại trừ các phần tử không cần thiết, nếu loại trừ nhiều phần tử thì phân cách bằng dấu `:`:

```
tag.div!0:2@text   // Loại trừ div thứ 1 và thứ 3
```

### Đảo ngược danh sách `-`

Thêm dấu `-` vào ngay trước danh sách để đảo ngược danh sách, phù hợp cho trường hợp danh sách mục lục của trang web đang bị sắp xếp ngược:

```
-tag.dd@tag.a@text   // Lấy văn bản của thẻ a dưới tất cả thẻ dd theo thứ tự đảo ngược
```

### Thay thế bằng biểu thức chính quy `##`

Thêm `##Biểu thức chính quy##Nội dung thay thế` ở cuối quy tắc:

```
tag.p@text##Văn bản cần loại bỏ##Văn bản thay thế
tag.p@text##Văn bản cần loại bỏ      // Có thể lược bỏ dấu ## thứ hai nếu nội dung thay thế là rỗng
```

### Cách viết kiểu mảng `[index]`

Hỗ trợ chọn chỉ mục tương tự như mảng:

| Cách viết | Mô tả |
|:-----------|:--------------|
| `[0]`      | Phần tử đầu tiên |
| `[-1]`     | Phần tử cuối cùng |
| `[0:3]`    | 3 phần tử đầu tiên |
| `[2:5]`    | Phần tử thứ 3 đến thứ 5 |
| `[0:10:2]` | Lấy xen kẽ cách quãng (bước nhảy là 2) |
| `[!0:2]`   | Loại trừ phần tử thứ 1 và thứ 3 |
| `[-1:0]`   | Đảo ngược danh sách |

### Ký hiệu liên kết

| Ký hiệu | Mô tả | Ví dụ |
|:-------|:---------------|:------------------------------|
| `||` | Ưu tiên lấy kết quả có giá trị đầu tiên | `tag.a@text||tag.span@text` |
| `&&`   | Hợp nhất tất cả các giá trị lấy được | `tag.a@text&&tag.span@text`   |
| `%%`   | Lấy giá trị luân phiên (lần lượt lấy từ mỗi danh sách) | `tag.a@href%%tag.a@text`      |

::: tip Giới hạn sử dụng
Ký hiệu liên kết chỉ có thể được sử dụng giữa các quy tắc cùng loại, không bao gồm JS và Regex.
:::

## 2. Bộ chọn CSS của JSOUP (CSS Selector)

Cú pháp bộ chọn CSS tiêu chuẩn bắt đầu bằng `@css:`.

```
@css:Bộ chọn@Nội dung cần lấy
```

**Ví dụ:**

```
@css:.book-list li@text           // Lấy văn bản của tất cả các li trong class="book-list"
@css:#content p@html              // Lấy mã HTML của tất cả các p trong id="content"
@css:[property=og:image]@content  // Lấy thuộc tính content của thẻ meta
```

- Kiểm tra trực tuyến: [Try jsoup online](https://try.jsoup.org/)

## 3. Biểu thức đường dẫn JSONPath

Cú pháp trích xuất dữ liệu JSON bắt đầu bằng `@json:` hoặc `$.`.

```
@json:Biểu thức
$.Biểu thức
```

**Ví dụ:**

```
$.data.books[*]           // Lấy tất cả các sách
$.data.books[0].title     // Lấy tiêu đề của cuốn sách đầu tiên
$..books[*]               // Tìm kiếm đệ quy tất cả các mảng books
$.info.Datas              // Lấy Datas nằm trong info
```

- Kiểm tra trực tuyến: [Jayway JsonPath Evaluator](http://jsonpath.herokuapp.com/)

## 4. Biểu thức đường dẫn XPath

Biểu thức đường dẫn XML/HTML bắt đầu bằng `@XPath:` hoặc `//`.

```
@XPath:Biểu thức
//Biểu thức
```

**Ví dụ:**

```
//div[@class="book-list"]/ul/li      // Lấy các mục danh sách (li) dưới class cụ thể
//*[@property="og:novel:author"]/@content  // Lấy giá trị thuộc tính content của thẻ meta
//a[text()="阅读"]/@href             // Lấy href của liên kết có văn bản là "阅读" (Đọc)
```

Chi tiết cú pháp xem tại [Biểu thức đường dẫn XPath](./xpath).

## 5. Kịch bản JavaScript (JS)

Sử dụng JavaScript bên trong `<js></js>` hoặc `@js:`.

| Hình thức | Vị trí sử dụng | Mô tả |
|:------------|:----------------|:-----------------------|
| `@js:`      | Chỉ có thể đặt ở cuối cùng của các quy tắc khác | Lấy kết quả của quy tắc phía trước làm biến `result` |
| `<js></js>` | Bất kỳ vị trí nào | Có thể được sử dụng làm dấu phân cách giữa các quy tắc khác |

**Ví dụ:**

```
@css:.book@text@js:result.replace("cũ", "mới")   // Lấy văn bản trước rồi xử lý bằng JS
tag.li<js></js>//a                                // JS đóng vai trò làm dấu phân cách
@js:java.base64Encode(key)                        // Biểu thức JS thuần túy
```

### Sử dụng trong danh sách Tìm kiếm/Khám phá

Bắt đầu bằng dấu `+` để sử dụng quy tắc AllInOne, được áp dụng trong danh sách tìm kiếm, danh sách khám phá và mục lục.

## 6. Biểu thức chính quy (Regex)

Trong Legado có ba cách sử dụng Regex:

### Khớp toàn bộ (AllInOne)

- Chỉ có thể sử dụng trong danh sách tìm kiếm, danh sách khám phá, tải trước trang chi tiết và danh sách mục lục.
- Bắt buộc phải bắt đầu bằng dấu `:`

```
:<Biểu thức chính quy>
```

**Ví dụ (trang mục lục):**

```
-:<li><a[^\"]+\"([^\"]*)\">([^<]*)   // Trích xuất liên kết và tiêu đề, tiền tố - biểu thị đảo ngược thứ tự
```

### Khớp đơn nhất (OnlyOne)

Định dạng: `##Biểu thức chính quy##Nội dung thay thế###`

- Chỉ có thể sử dụng bên ngoài danh sách tìm kiếm, danh sách khám phá, tải trước trang chi tiết và danh sách mục lục.
- Chỉ lấy kết quả khớp đầu tiên và thực hiện thay thế.

**Ví dụ (trang chi tiết):**

```
##:book_name"[^"]+"([^"]+)"##$1###    // Trích xuất tên sách
```

### Tẩy lọc / Quy tắc thay thế (Thay thế lặp)

Định dạng: `##Biểu thức chính quy##Nội dung thay thế`

- Đi sau các quy tắc khác để thực hiện tìm kiếm và thay thế lặp lại trên kết quả.
- Sử dụng độc lập tương đương với `all##Biểu thức chính quy##Nội dung thay thế`.

**Ví dụ (trang nội dung):**

```
@css:.content@html##<script>.*?</script>##    // Loại bỏ tất cả các thẻ script
```

## 7. Biến mẫu `{{}}`

### Trong URL tìm kiếm và URL khám phá

Bên trong `{{}}` chỉ có thể sử dụng JavaScript:

```
/search?key={{key}}&page={{page}}
/search?key={{java.base64Encode(key)}}&page={{(page-1)*20}}
```

### Trong các quy tắc khác

Bên trong `{{}}` có thể sử dụng bất kỳ quy tắc nào, mặc định là JS. Nếu sử dụng các quy tắc khác thì cần thêm cờ tiêu đề:

| Loại quy tắc | Cờ tiêu đề |
|:---------|:-----------------|
| JS (mặc định) | Không cần cờ |
| Default  | `@@`             |
| XPath    | `@xpath:` hoặc `//` |
| JSONPath | `@json:` hoặc `$.`  |
| CSS      | `@css:`          |

### Cú pháp cũ `{}`

Cú pháp cũ được giữ lại từ Yuedu 2.0 (阅读 2.0), chỉ có thể sử dụng JSONPath, khuyến nghị nên tránh sử dụng.

## 8. Lưu và truy xuất biến

### `@put` và `@get`

Chỉ có thể sử dụng bên trong các quy tắc không phải là JS:

```
@put:{bid:"//*[@bid-data]/@bid-data"}    // Lưu trữ biến
@get:bid                                  // Đọc biến
```

### `java.put` và `java.get`

Chỉ có thể sử dụng bên trong JS (không thể sử dụng `@get` trong JS):

```js
java.put('key', 'value')   // Lưu trữ
java.get('key')             // Đọc
```
