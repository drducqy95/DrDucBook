# Hướng dẫn Quy tắc Nguồn (Source Rule)

[[toc]]

Tài liệu này giới thiệu các cấu hình cốt lõi và cú pháp của quy tắc nguồn Legado. Nguồn sách thu thập nội dung từ các trang web thông qua cấu hình JSON khai báo và logic JavaScript tùy chọn.

::: tip Tài liệu liên quan
- [Biến và hàm JS](./js) — Các biến tích hợp và API mở rộng
- [Biểu thức đường dẫn XPath](./xpath) — Cú pháp XPath
- [Biểu thức chính quy](./regex) — Cú pháp Regex
- [Cấu hình Request Headers](./request-headers) — Proxy, Headers, chặn chuyển hướng
- [Xác thực và Đăng nhập](./authentication) — CookieJar, UI đăng nhập, URL đăng nhập
- [Cấu hình URL Khám phá](./discovery-url) — Định dạng JSON lối vào trang khám phá
:::

Trong bàn phím phụ trợ ❓ có thể chèn mẫu tham số URL, mở trợ giúp, hướng dẫn JS, hướng dẫn Regex, chọn tệp.

## 2. Cờ quy tắc (Rule Flags)

Khi sử dụng quy tắc bên trong `<code v-pre>{{......}}</code>`, bắt buộc phải bao gồm cờ quy tắc rõ ràng. Nội dung không có cờ quy tắc sẽ được thực thi như JavaScript.

| Cờ | Cú pháp | Điều kiện có thể lược bỏ | Phạm vi áp dụng |
|:----------|:---------|:--------------|:-----------|
| `@@`      | Quy tắc mặc định | Có thể lược bỏ khi viết trực tiếp | Tất cả các trường |
| `@XPath:` | Quy tắc XPath | Có thể lược bỏ khi bắt đầu bằng `//` | Tất cả các trường |
| `@Json:`  | Quy tắc JSON | Có thể lược bỏ khi bắt đầu bằng `$.` | Tất cả các trường |
| `:`       | Quy tắc Regex | Không thể lược bỏ | Chỉ áp dụng cho danh sách sách và danh sách mục lục |

## 3. Nhúng thư viện JS (jsLib)

Nhúng JavaScript vào công cụ Rhino, hỗ trợ hai định dạng, giúp chia sẻ và tái sử dụng các hàm.

| Định dạng | Mô tả | Ví dụ |
|:------------------|:-----------------------|:-------------------------------------------|
| Mã JavaScript | Điền trực tiếp đoạn mã JavaScript | `function myUtil(s) { return s.trim() }`   |
| JSON Map          | Bảng ánh xạ URL, tự động tái sử dụng các tệp JS đã tải xuống | `{"example":"https://example.com/lib.js"}` |

::: warning An toàn luồng (Thread Safety)
Các hàm định nghĩa ở đây có thể được gọi đồng thời bởi nhiều luồng (thread), nội dung các biến toàn cục bên trong hàm sẽ bị chia sẻ. Việc sửa đổi chúng có thể dẫn đến các vấn đề tranh chấp (race condition).

- **Không được** khai báo biến toàn cục bên trong hàm.
- **Không được** gán lại giá trị cho các biến toàn cục bên ngoài hàm, nếu không sẽ ném ra ngoại lệ `Cannot modify properties of a sealed object` (Không thể sửa đổi thuộc tính của đối tượng đã bị phong kín).
:::

## 4. Giới hạn tốc độ yêu cầu (Request Rate Limit)

Kiểm soát tần suất gửi yêu cầu đến trang web đích, hỗ trợ hai định dạng:

| Định dạng | Mô tả | Ví dụ |
|:------|:-----------|:----------------------------|
| `N`   | Khoảng thời gian truy cập (mili giây) | `1000` — mỗi lần yêu cầu cách nhau 1 giây |
| `N/M` | Số yêu cầu tối đa trong một khoảng thời gian cửa sổ | `20/60000` — tối đa 20 yêu cầu trong vòng 60 giây |

## 5. Loại nguồn: Tệp (File Source)

Áp dụng cho các trang web cung cấp tải xuống tệp (ví dụ: Tri Hiên Tàng Thư - 知轩藏书). Lấy liên kết tệp trong quy tắc "URL tải xuống" ở phần thông tin chi tiết nguồn sách.

**Nguyên lý hoạt động:**

1. Lấy thông tin tệp bằng cách phân tích liên kết tải xuống hoặc HTTP response headers của tệp.
2. Nếu lấy thông tin thất bại, hệ thống tự động ghép `Tên sách`, `Tác giả` và trường `type` trong `UrlOption` của liên kết tải xuống.
3. Bộ nhớ đệm giải nén của các tệp nén sẽ tự động được dọn dẹp sau lần khởi động ứng dụng tiếp theo, không chiếm dụng thêm không gian bộ nhớ.

## 6. Phân tích Phông chữ (Font Parsing)

Được sử dụng trong quy tắc thay thế nội dung, dựa trên dữ liệu glyph của phông chữ nguồn để tìm mã hóa tương ứng trong phông chữ đích.

```js
(function(){
  var b64 = String(src).match(/ttf;base64,([^\)]+)/);
  if (b64) {
    var f1 = java.queryTTF(b64[1]);
    var f2 = java.queryTTF("https://example.com/font/SourceHanSansCN.ttf");
    return java.replaceFont(result, f1, f2, true);
  }
  return result;
})()
```

## 7. Thao tác mua (Purchase Action)

Có thể điền trực tiếp liên kết hoặc mã JavaScript.

| Giá trị trả về | Hành vi |
|:--------------|:------------|
| Liên kết mạng | Tự động mở trình duyệt |
| `true` (trả về bởi JS) | Tự động làm mới mục lục và chương hiện tại |

## 8. Giải mã hình ảnh (Image Decryption)

Áp dụng cho các trường hợp hình ảnh cần giải mã lần hai. Điền trực tiếp mã JavaScript, trả về một mảng byte `ByteArray` sau khi giải mã.

**Các biến có sẵn:**

| Biến | Mô tả |
|:---------|:-------------------------------------------------------------------------------------------------------------------------------------|
| `java`   | Chỉ hỗ trợ các phương thức trong [JsExtensions](https://github.com/HapeLee/legado-with-MD3/blob/master/app/src/main/java/io/legado/app/help/JsExtensions.kt) |
| `result` | `ByteArray` của hình ảnh cần giải mã |
| `src`    | Liên kết hình ảnh |

**Ví dụ — Giải mã AES:**

```js
java.createSymmetricCrypto("AES/CBC/PKCS5Padding", key, iv).decrypt(result)
```

**Ví dụ — Giải mã XOR:**

```js
function decodeImage(data, key) {
  var input = new Packages.java.io.ByteArrayInputStream(data)
  var out = new Packages.java.io.ByteArrayOutputStream()
  var byte
  while ((byte = input.read()) != -1) {
    out.write(byte ^ key)
  }
  return out.toByteArray()
}

decodeImage(result, key)
```

### Giải mã ảnh bìa (Cover Decryption)

Tương tự như giải mã hình ảnh, trong đó `result` là `InputStream` của ảnh bìa cần giải mã.

```js
java.createSymmetricCrypto("AES/CBC/PKCS5Padding", key, iv).decrypt(result)
```
