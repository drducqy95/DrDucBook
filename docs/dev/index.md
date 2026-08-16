# Tài liệu Phát triển

Chào mừng bạn đến với tài liệu phát triển Legado. Tài liệu này cung cấp thông tin tham khảo cho các nhà phát triển nguồn sách và những người đóng góp.

::: tip Khởi đầu nhanh
Nếu đây là lần đầu tiên bạn tiếp cận việc phát triển nguồn sách Legado, khuyến nghị bạn nên đọc theo thứ tự sau:

1. [Chi tiết cú pháp quy tắc](./syntax) — Tìm hiểu tất cả các cú pháp quy tắc được hỗ trợ
2. [Chi tiết tham số URL](./url-options) — Nắm vững cấu hình tham số yêu cầu
3. [Tra nhanh các trường trong nguồn](./source-fields) — Xem tất cả các trường có sẵn trong các phần
4. [Các ví dụ về nguồn sách](./examples) — Tham khảo các file JSON nguồn hoàn chỉnh
:::

## Bắt đầu

Học phát triển nguồn sách từ con số 0.

| Tài liệu | Mô tả |
|:--------------------------|:-------------------------------------------------------------|
| [Chi tiết cú pháp quy tắc](./syntax)        | JSOUP Default/CSS, JSONPath, XPath, Regex AllInOne/OnlyOne/Tẩy lọc, ký hiệu liên kết |
| [Chi tiết tham số URL](./url-options) | Yêu cầu GET/POST, chế độ WebView, biến mẫu, chi tiết các trường UrlOption |
| [Tra nhanh các trường trong nguồn](./source-fields)  | Mô tả tất cả các trường cho các phần: Tìm kiếm, Khám phá, Trang chi tiết, Mục lục, Nội dung |
| [Các ví dụ về nguồn sách](./examples)         | 3 định dạng nguồn JSON hoàn chỉnh: CSS+Regex, XPath+Regex, JSONPath |

## Tham khảo

Tham khảo cú pháp và API cốt lõi.

| Tài liệu | Mô tả |
|:-----------------------|:--------------------------------------|
| [Hướng dẫn quy tắc nguồn](./rule)        | Cờ quy tắc, jsLib, giới hạn tốc độ yêu cầu, loại nguồn, phân tích phông chữ, giải mã hình ảnh |
| [Biến và hàm JS](./js)       | API hoàn chỉnh của Rhino engine: biến tích hợp, mã hóa/giải mã, yêu cầu mạng, tương tác Java, v.v. |
| [Biểu thức đường dẫn XPath](./xpath) | 13 loại trục (axes), điều kiện vị ngữ, ký tự đại diện, hàm tích hợp |
| [Biểu thức chính quy](./regex)       | Ký tự đại diện, tập hợp ký tự, khẳng định (assertions), các ví dụ Regex thông dụng |

## Quy chuẩn cấu hình

Quy chuẩn chi tiết cho các trường JSON nguồn.

| Tài liệu | Mô tả |
|:-----------------------------------|:----------------------------------------|
| [Cấu hình Request Headers](./request-headers)         | Thiết lập proxy, tùy chỉnh Headers, tham số động URL, chặn chuyển hướng |
| [Xác thực và Đăng nhập](./authentication)          | CookieJar, biểu mẫu UI đăng nhập, kịch bản đăng nhập URL, kiểm tra đăng nhập |
| [Cấu hình URL Khám phá](./discovery-url)       | Định dạng JSON lối vào trang khám phá, biến phân trang, mối quan hệ với mô-đun trang chủ |
| [Cấu hình mô-đun trang chủ](../spec/homepage-modules) | Quy chuẩn trường `homepageModules`: danh sách, carousel, bảng xếp hạng, v.v. |
| [Cấu hình sách liên quan](../spec/related-books)    | Quy chuẩn trường `ruleBookInfo.relatedBooks`: đề xuất sách liên quan |

## Tính năng mở rộng

| Tài liệu | Mô tả |
|:--------------------------------|:-----------------------|
| [Gỡ lỗi nguồn sách (Debug)](./debug)                  | Phương pháp gỡ lỗi cho các trang tìm kiếm, khám phá, chi tiết, mục lục, nội dung |
| [Quy tắc từ điển](./dict-rule)             | Cấu hình quy tắc từ điển/dịch thuật cho menu lựa chọn văn bản |
| [Quy tắc đọc trực tuyến TTS](./tts-rule)            | Tùy chỉnh giao diện TTS trực tuyến (hỗ trợ kiểm soát tốc độ đọc) |
| [Regex mục lục file TXT](./txt-toc)           | Quy tắc nhận diện chương tùy chỉnh cho sách định dạng TXT |
| [Tham khảo kiểu MIME](../spec/mime-types) | Bảng đối chiếu phần mở rộng tệp và kiểu MIME hỗ trợ |

## Tài nguyên bên ngoài

- [Legado GitHub](https://github.com/HapeLee/legado-with-MD3) — Mã nguồn dự án và theo dõi lỗi (Issues)
- [legado-with-MD3 Wiki](https://github.com/HapeLee/legado-with-MD3/wiki) — Tài liệu do cộng đồng duy trì
