# Chi tiết Tham số URL (URL Options)

[[toc]]

Các URL trong nguồn sách Legado hỗ trợ đính kèm tham số yêu cầu thông qua đối tượng JSON để kiểm soát phương thức yêu cầu, mã hóa, Headers, WebView và các hành vi khác.

## 1. Cú pháp cơ bản

URL và các tham số được kết nối với nhau bằng dấu phẩy `,`:

```
URL,{Tham số JSON}
```

**Yêu cầu GET đơn giản:**

```
https://www.example.com/api/list
```

**Yêu cầu kèm tham số:**

```
https://www.example.com/api/list,{
    "charset": "gbk",
    "headers": {"User-Agent": "Mozilla/5.0 ..."}
}
```

## 2. Danh sách trường đầy đủ của UrlOption

| Trường | Kiểu dữ liệu | Bắt buộc | Mô tả |
|:----------|:----------|:---|:----------------------------------|
| `method`  | `String`  | Không | Phương thức yêu cầu, `GET` (mặc định) hoặc `POST` |
| `charset` | `String`  | Không | Bảng mã phản hồi, mặc định là `utf-8` |
| `headers` | `Object`  | Không | HTTP Headers tùy chỉnh |
| `body`    | `String`  | Không | Phần thân (body) của yêu cầu POST |
| `webView` | `Boolean` | Không | Có sử dụng WebView để tải trang hay không |
| `js`      | `String`  | Không | JavaScript được thực thi khi phân tích URL |
| `type`    | `String`  | Không | Kiểu tệp (dành cho loại nguồn tệp) |
| `retry`   | `Int`     | Không | Số lần thử lại, mặc định là 0 |
| `proxy`   | `String`  | Không | Địa chỉ proxy, xem tại [Cấu hình Request Headers](./request-headers) |

## 3. Yêu cầu GET

### Định dạng đơn giản

```
https://www.example.com/api/list
```

### Kèm theo Headers

```
https://www.example.com/api/list,{
    "headers": {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Accept-Language": "zh-CN,zh;q=0.9"
    }
}
```

### Chỉ định bảng mã

```
https://www.example.com/list,{
    "charset": "gbk"
}
```

### Sử dụng WebView

```
https://www.example.com/book/123,{
    "webView": true
}
```

### Xây dựng động bằng JS

```javascript
var ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
var headers = {"User-Agent": ua};
var option = {
    "charset": "gbk",
    "headers": headers,
    "webView": true
};
"https://www.example.com," + JSON.stringify(option)
```

## 4. Yêu cầu POST

### Định dạng đơn giản

```
https://www.example.com/search,{
    "method": "POST",
    "body": "keyword=系统&page=1"
}
```

### Kèm theo Headers và bảng mã

```
https://www.example.com/search,{
    "charset": "gbk",
    "method": "POST",
    "body": "searchkey={{key}}&page={{page}}",
    "headers": {
        "User-Agent": "Mozilla/5.0 ..."
    }
}
```

### Xây dựng động bằng JS

```javascript
var body = "key=" + key + "&page=" + page;
var option = {
    "method": "POST",
    "body": String(body),
    "headers": {"User-Agent": "Mozilla/5.0 ..."}
};
"https://www.example.com/search," + JSON.stringify(option)
```

::: warning Kiểu dữ liệu của body
Trường `body` phải luôn bảo đảm có kiểu dữ liệu là `String` của JavaScript. Đối với các biến thu được thông qua tính toán, hãy cố gắng sử dụng `String()` để ép kiểu.
:::

## 5. Chế độ WebView

Sau khi thiết lập `"webView": true`, Legado sẽ sử dụng WebView tích hợp sẵn để tải trang web, phù hợp cho các trang web yêu cầu thực thi JavaScript để hiển thị nội dung.

### Tải trang bằng WebView

```
https://www.example.com/book/123,{
    "webView": true
}
```

### WebView + Dò tìm (sniff) nội dung

Thêm `{"webView": true}` vào liên kết chương, phối hợp với `sourceRegex` trong phần nội dung chương để dò tìm các tài nguyên đa phương tiện:

```json
{
    "ruleToc": {
        "chapterUrl": "href##$##{\"webView\":true}"
    },
    "ruleContent": {
        "content": "<js>result</js>",
        "sourceRegex": ".*\\.(mp3|mp4).*"
    }
}
```

**Các bước dò tìm (sniffing):**

1. Thêm `,{"webView":true}` vào sau liên kết chương.
2. Nhập liên kết chương (không mang tham số webView) vào một trình duyệt có tính năng dò tìm phương tiện (sniffing).
3. Sau khi phương tiện bắt đầu phát, sử dụng tính năng dò tìm của trình duyệt để xem liên kết tài nguyên.
4. Điền biểu thức chính quy (Regex) của liên kết tài nguyên vào trường Regex tài nguyên, ví dụ: `.*\.(mp3|mp4).*`.
5. Phần nội dung chương điền `<js>result</js>`.

## 6. Biến mẫu (Template variables)

Có thể sử dụng các biến mẫu sau trong URL:

| Biến | Mô tả | Vị trí áp dụng |
|:--------------------------------|:-----------|:--------------|
| `{{key}}`                       | Từ khóa tìm kiếm | URL tìm kiếm |
| `{{page}}`                      | Số trang (Bắt đầu từ 1) | URL tìm kiếm, URL khám phá |
| `{{page - 1 == 0 ? "" : page}}` | Không hiển thị số trang ở trang đầu tiên | URL tìm kiếm, URL khám phá |
| `<,{{page}}>`                   | Không hiển thị số trang ở trang đầu tiên (cách viết rút gọn) | URL tìm kiếm, URL khám phá |

### Ví dụ tính toán số trang

```
/search?key={{key}}&start={{(page-1)*20}}&limit=20
/search?key={{key}}&page={{page - 1 == 0 ? "" : page}}
```

## 7. URL tương đối

URL hỗ trợ đường dẫn tương đối, hệ thống sẽ tự động ghép nối dựa trên URL gốc của nguồn:

```
/search?key={{key}}     // Tương đối so với bookSourceUrl
/api/list               // Tương đối so với bookSourceUrl
```
