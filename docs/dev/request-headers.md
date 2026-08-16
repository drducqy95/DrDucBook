# Cấu hình Request Headers (HTTP Headers)

[[toc]]

Trường cấu hình HTTP Request Headers của nguồn sách được sử dụng để kiểm soát các hành vi của yêu cầu HTTP, bao gồm thiết lập proxy, tùy chỉnh Headers và chèn các tham số động.

## 1. Định dạng cơ bản

HTTP Headers được viết dưới dạng một đối tượng JSON, các khóa (key) **phân biệt chữ hoa - chữ thường**.

| Đúng | Sai |
|:-------------|:-------------|
| `User-Agent` | `user-agent` |
| `Referer`    | `referer`    |

## 2. Cấu hình Proxy

Hỗ trợ ba giao thức proxy: HTTP, SOCKS4 và SOCKS5.

| Giao thức | Định dạng | Ví dụ |
|:----------|:-----------------------------|:--------------------------------------------------|
| SOCKS5    | `socks5://host:port`         | `{"proxy": "socks5://127.0.0.1:1080"}`            |
| SOCKS4    | `socks4://host:port`         | `{"proxy": "socks4://127.0.0.1:1080"}`            |
| HTTP      | `http://host:port`           | `{"proxy": "http://127.0.0.1:1080"}`              |
| HTTP (Có xác thực) | `http://host:port@user@pass` | `{"proxy": "http://127.0.0.1:1080@admin@secret"}` |

## 3. Đính kèm tham số JS vào URL

Đính kèm đối tượng JSON vào sau URL để thực thi JavaScript động xử lý yêu cầu khi phân tích URL.

**Cú pháp:**

```
URL,{"js":"Mã JavaScript"}
```

**Ví dụ:**

```
https://www.example.com,{"js":"java.headerMap.put('xxx', 'yyy')"}
https://www.example.com,{"js":"java.url=java.url+'yyyy'"}
```

## 4. Tùy chỉnh Headers cho liên kết hình ảnh

Đính kèm các HTTP Headers tùy chỉnh vào liên kết hình ảnh trong nội dung chương, phù hợp cho các trường hợp hình ảnh yêu cầu Referer hoặc Cookie mới có thể tải được.

```js
let options = {
  "headers": {
    "User-Agent": "xxxx",
    "Referrer": baseUrl,
    "Cookie": "aaa=vbbb;"
  }
};
'<img src="' + src + "," + JSON.stringify(options) + '">'
```

## 5. Chặn chuyển hướng (Redirect Interception)

Chặn chuyển hướng thông qua phương thức `java.get` / `java.post` để lấy URL chuyển hướng cuối cùng. Phương pháp này phù hợp cho các trang web tự động chuyển hướng kết quả tìm kiếm.

```js
// Chữ ký phương thức
java.get(urlStr: String, headers: Map<String, String>)
java.post(urlStr: String, body: String, headers: Map<String, String>)
```

**Ví dụ**: Lấy URL thực tế trong kịch bản chuyển hướng tìm kiếm:

```js
(() => {
  if (page == 1) {
    let url = 'https://www.example.com/search,' + JSON.stringify({
      "method": "POST",
      "body": "show=title&tempid=1&keyboard=" + key
    });
    return source.put('surl', String(java.connect(url).raw().request().url()));
  } else {
    return source.get('surl') + '&page=' + (page - 1)
  }
})()
```
