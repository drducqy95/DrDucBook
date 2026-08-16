# Biểu thức Đường dẫn XPath

[[toc]]

XPath là một ngôn ngữ dùng để định vị các nút (nodes) trong tài liệu XML/HTML. Nguồn sách Legado hỗ trợ sử dụng các quy tắc XPath để trích xuất nội dung trang web, các quy tắc bắt đầu bằng `//` sẽ được tự động nhận diện là biểu thức XPath.

_Lưu ý: Tất cả mã trong tài liệu này đều được xác minh thông qua trình duyệt Chrome (phiên bản 123.0.6312.86)_

## 1. Trục (Axes)

Đặc tả XPath định nghĩa 13 loại trục (axes), được sử dụng để định vị các nút trên cây phần tử tương đối so với phần tử hiện tại.

| Trục (Axis) | Mô tả | Viết tắt |
|:---------------------|:---------------------------|:-----|
| `attribute`          | Thuộc tính của phần tử | `@` |
| `self`               | Chính phần tử đó | `.` |
| `parent`             | Phần tử cha của phần tử hiện tại | `..` |
| `child`              | Phần tử con của phần tử hiện tại | — |
| `ancestor`           | Tất cả tổ tiên trực tiếp của phần tử hiện tại | — |
| `ancestor-or-self`   | Phần tử hiện tại và tất cả tổ tiên trực tiếp của nó | — |
| `descendant`         | Tất cả các phần tử con đệ quy của phần tử hiện tại | — |
| `descendant-or-self` | Phần tử hiện tại và tất cả các phần tử con đệ quy của nó | — |
| `following`          | Tất cả các phần tử xuất hiện sau phần tử hiện tại (không quan tâm cấp độ, không bao gồm hậu duệ trực tiếp) | — |
| `following-sibling`  | Tất cả các phần tử cùng cấp (anh em) xuất hiện sau phần tử hiện tại | — |
| `preceding`          | Tất cả các phần tử xuất hiện trước phần tử hiện tại (không quan tâm cấp độ, không bao gồm tổ tiên trực tiếp) | — |
| `preceding-sibling`  | Tất cả các phần tử cùng cấp (anh em) xuất hiện trước phần tử hiện tại | — |
| `namespace`          | Không hỗ trợ | — |

**Cú pháp**: `Ten_truc::Bieu_thuc`

```js
> $x('//body/ancestor-or-self::*')
< [body, html]
```

## 2. Định dạng đường dẫn

XPath lựa chọn các phần tử thông qua "biểu thức đường dẫn" (Path Expression), có định dạng tương tự như đường dẫn hệ thống tệp.

| Khái niệm | Mô tả |
|:-----|:----------------------------------|
| `/`  | Dấu phân cách bên trong đường dẫn |
| Đường dẫn tuyệt đối | Bắt đầu bằng `/`, theo sau là phần tử gốc, ví dụ: `/step/step/...` |
| Đường dẫn tương đối | Các cách viết khác ngoài đường dẫn tuyệt đối, ví dụ: `step/step` |
| `.`  | Phần tử hiện tại |
| `..` | Phần tử cha của phần tử hiện tại |

### Cú pháp lựa chọn

| Ký hiệu | Mô tả |
|:-----------|:------------|
| `/`        | Lựa chọn phần tử gốc |
| `//`       | Lựa chọn một phần tử nhất định ở bất kỳ vị trí nào |
| `nodename` | Lựa chọn phần tử có tên chỉ định |
| `@`        | Lựa chọn một thuộc tính nhất định |

## 3. Ví dụ

Ví dụ dưới đây dựa trên đoạn mã HTML sau:

```html
<!DOCTYPE html>
<html>
    <head>
        <meta charset="utf-8" />
        <title>Tiêu đề</title>
        <meta property="author" content="Tác giả" />
    </head>
    <body>
        <div>
            <title lang="eng">Harry Potter</title>
            <p>29.39</p>
            <p>usd</p>
        </div>
        <div>
            <title lang="cn">Cpp高级编程</title>
            <p>39.95</p>
            <p>rmb</p>
        </div>
        <div id="list">
            <dl>
                <dd><a href="/1">Một</a></dd>
                <dd><a href="/2">Hai</a></dd>
                <dd><a href="/3">Ba</a></dd>
            </dl>
        </div>
    </body>
</html>
```

| Biểu thức | Kết quả | Mô tả |
|:-----------------------|:---------------|:--------------|
| `$x('/')`              | `[document]`   | Lựa chọn phần tử gốc |
| `$x('/html')`          | `[html]`       | Lựa chọn theo đường dẫn tuyệt đối |
| `$x('html/head/meta')` | `[meta, meta]` | Lựa chọn theo đường dẫn tương đối |
| `$x('//p')`            | `[p, p, p, p]` | Lựa chọn tất cả các phần tử p |
| `$x('html/body//a')`   | `[a, a, a]`    | Lựa chọn tất cả các phần tử a bên dưới body |
| `$x('//@lang')`        | `[lang, lang]` | Lựa chọn thuộc tính |
| `$x('//meta/..')`      | `[head]`       | Lựa chọn phần tử cha |

## 4. Điều kiện vị ngữ (Predicate)

Điều kiện vị ngữ là điều kiện lọc bổ sung cho biểu thức đường dẫn, được viết bên trong dấu ngoặc vuông `[]`.

| Biểu thức | Kết quả | Mô tả |
|:-------------------------------|:-------------------------------|:--------|
| `html/head/meta[1]`            | `<meta charset="utf-8">`       | Lựa chọn phần tử đầu tiên |
| `html/head/meta[last()]`       | `<meta property="author" ...>` | Lựa chọn phần tử cuối cùng |
| `html/head/meta[last()-1]`     | `<meta charset="utf-8">`       | Lựa chọn phần tử kế cuối (đảo ngược vị trí thứ hai) |
| `html/head/meta[position()>1]` | `<meta property="author" ...>` | Vị trí lớn hơn 1 |
| `//title[@lang]`               | Cả hai thẻ title | Có thuộc tính cụ thể |
| `//title[@lang="eng"]`         | title tiếng Anh | Khớp giá trị thuộc tính |
| `/html/body/div[dl]`           | `<div id="list">`              | Chứa phần tử con cụ thể |
| `/html/body/div[p>35.00]`      | div chứa Cpp高级编程 | Điều kiện giá trị phần tử con |

## 5. Ký tự đại diện (Wildcards)

| Ký hiệu | Mô tả |
|:-----|:--------|
| `*`  | Khớp với bất kỳ phần tử nào |
| `@*` | Khớp với bất kỳ tên thuộc tính nào |

```js
$x('//*')          // Lựa chọn tất cả các phần tử
$x('/*/*')         // Lựa chọn tất cả các phần tử ở cấp độ thứ hai
$x('//title[@*]')  // Lựa chọn tất cả các title có thuộc tính
```

## 6. Lựa chọn nhiều đường dẫn

Sử dụng `|` để kết hợp các kết quả lựa chọn từ nhiều biểu thức:

```js
$x('//title | //a')  // Lựa chọn tất cả các phần tử title và a
```

## 7. Hàm (Functions)

Tham số của các hàm XPath có thể là các chuỗi tĩnh hoặc biểu thức, các hàm có thể được gọi lồng nhau. Chỉ mục của XPath bắt đầu từ **1**.

| Hàm | Mô tả | Ví dụ |
|:---------------------|:----------|:------------------------------------------------------|
| `boolean()`          | Chuyển đổi thành giá trị Boolean | `boolean(//title)` → `true` |
| `number()`           | Chuyển đổi thành số | `number(//p[1])` → `29.39` |
| `round()`            | Làm tròn số (4/5) | `round(//p[1])` → `29` |
| `ceiling()`          | Làm tròn lên | `ceiling(//p[1])` → `30` |
| `floor()`            | Làm tròn xuống | `floor(//p[1])` → `29` |
| `concat()`           | Ghép nối các chuỗi | `concat("cost:", //p[1], //p[2])` → `'cost:29.39usd'` |
| `contains()`         | Kiểm tra xem có chứa hay không | `contains(//p[1], "29.39")` → `true` |
| `count()`            | Đếm số lượng phần tử | `count(//p)` → `4` |
| `id()`               | Lựa chọn dựa trên ID | `id("list")` → `[dl#list]` |
| `last()`             | Trả về vị trí của phần tử cuối cùng trong tập hợp phần tử cùng cấp | `//p[last()]` |
| `name()`             | Trả về tên của phần tử | `name(//*[@id])` → `'dl'` |
| `normalize-space()`  | Loại bỏ khoảng trắng thừa ở đầu, cuối và bên trong chuỗi | `normalize-space("  test  ")` → `'test'` |
| `not()`              | Trả về giá trị phủ định của Boolean | `//title[not(@lang)]` |
| `position()`         | Trả về vị trí của phần tử | `//meta[position()=2]` |
| `starts-with()`      | Kiểm tra xem chuỗi có bắt đầu bằng chuỗi chỉ định hay không | `//title[starts-with(., "Cpp")]` |
| `string()`           | Chuyển đổi thành chuỗi | `string(//p)` → `'29.39'` |
| `string-length()`    | Trả về độ dài của chuỗi | `string-length(string(//p))` → `5` |
| `substring()`        | Trích xuất chuỗi con | `substring(string(//p), 1, 3)` → `'29.'` |
| `substring-after()`  | Lấy phần chuỗi nằm sau ký tự chỉ định | `substring-after(string(//p), ".")` → `'39'` |
| `substring-before()` | Lấy phần chuỗi nằm trước ký tự chỉ định | `substring-before(string(//p), ".")` → `'29'` |
| `sum()`              | Tính tổng các số | `sum(//p[1])` → `69.34` |
| `translate()`        | Thay thế từng ký tự trong chuỗi | `translate("aabbcc", "ac", "V8")` → `'VVbb88'` |
