# Biến và Hàm JS

[[toc]]

Legado sử dụng [Rhino v1.8.0](https://github.com/mozilla/rhino) làm công cụ JavaScript (JS Engine), hỗ trợ gọi các lớp và phương thức Java bên trong quy tắc nguồn sách. Tài liệu này liệt kê tất cả các biến tích hợp, thuộc tính đối tượng và hàm mở rộng có sẵn.

## 1. Tổng quan về công cụ Rhino

| Hàm tạo (Constructor) | Hàm | Lớp được gọi | Mô tả |
|:---------------|:------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------|:--------------------------|
| `JavaImporter` | `importClass` `importPackage` | [ImporterTopLevel](https://github.com/mozilla/rhino/blob/master/rhino/src/main/java/org/mozilla/javascript/ImporterTopLevel.java)         | Nhập (import) các lớp Java vào JavaScript |
| —              | `getClass`                    | [NativeJavaTopPackage](https://github.com/mozilla/rhino/blob/master/rhino/src/main/java/org/mozilla/javascript/NativeJavaTopPackage.java) | Mặc định nhập các lớp Java vào JavaScript |
| `JavaAdapter`  | —                             | [JavaAdapter](https://github.com/mozilla/rhino/blob/master/rhino/src/main/java/org/mozilla/javascript/JavaAdapter.java)                   | Kế thừa lớp Java |

- [Rhino Runtime](https://github.com/mozilla/rhino/blob/master/rhino/src/main/java/org/mozilla/javascript/ScriptRuntime.java) tải chậm (lazy load) các lớp và phương thức Java được nhập.
- [Bảng tương thích ECMAScript](https://mozilla.github.io/rhino/compat/engines.html)

::: warning Lưu ý quan trọng
- Biến `java` đã bị Legado sửa đổi, để gọi các gói bên dưới `java.*`, vui lòng sử dụng `Packages.java.*`.
- Có thể gọi các lớp và phương thức tích hợp sẵn của Legado bằng cách sử dụng `@js`, `<js>`, `{{}}` trong quy tắc nguồn sách.
- Vì lý do bảo mật, một số lượt gọi lớp Java đã bị chặn, xem chi tiết tại [RhinoClassShutter](https://github.com/HapeLee/legado-with-MD3/blob/master/modules/rhino/src/main/java/com/script/rhino/RhinoClassShutter.kt).
- Các lớp và phương thức Java được hỗ trợ gọi có thể khác nhau tùy thuộc vào từng quy tắc nguồn sách khác nhau.
- Biến được khai báo bằng `const` không hỗ trợ phạm vi khối (block scope), việc sử dụng chúng trong vòng lặp có thể dẫn đến lỗi giá trị không thay đổi, vui lòng sử dụng `var` để thay thế.
:::

## 2. Biến tích hợp (Built-in Variables)

Các biến sau đây tự động khả dụng trong môi trường thực thi JS của quy tắc nguồn.

| Biến | Kiểu dữ liệu | Phạm vi | Mô tả |
|:-----------------|:---------------|:----|:-------------------------------------------------------------------------------------------------------------------------------|
| `java`           | `Object`       | Toàn cục | Đối tượng công cụ mở rộng, cung cấp các phương thức yêu cầu mạng, mã hóa/giải mã, thao tác tệp, v.v. |
| `baseUrl`        | `String`       | Toàn cục | URL của yêu cầu hiện tại |
| `result`         | `Any`          | Toàn cục | Kết quả thực thi của quy tắc ở bước trước đó |
| `book`           | `Book`         | Toàn cục | [Đối tượng sách](https://github.com/HapeLee/legado-with-MD3/blob/master/app/src/main/java/io/legado/app/data/entities/Book.kt) |
| `rssArticle`     | `RssArticle`   | Toàn cục | [Đối tượng bài viết RSS](https://github.com/HapeLee/legado-with-MD3/blob/master/app/src/main/java/io/legado/app/data/entities/RssArticle.kt) |
| `chapter`        | `BookChapter`  | Toàn cục | [Đối tượng chương](https://github.com/HapeLee/legado-with-MD3/blob/master/app/src/main/java/io/legado/app/data/entities/BookChapter.kt) |
| `source`         | `BaseSource`   | Toàn cục | [Đối tượng nguồn sách](https://github.com/HapeLee/legado-with-MD3/blob/master/app/src/main/java/io/legado/app/data/entities/BaseSource.kt) |
| `cookie`         | `CookieStore`  | Toàn cục | [Đối tượng thao tác Cookie](https://github.com/HapeLee/legado-with-MD3/blob/master/app/src/main/java/io/legado/app/help/http/CookieStore.kt) |
| `cache`          | `CacheManager` | Toàn cục | [Đối tượng thao tác bộ nhớ đệm](https://github.com/HapeLee/legado-with-MD3/blob/master/app/src/main/java/io/legado/app/help/CacheManager.kt) |
| `title`          | `String`       | Toàn cục | Tiêu đề chương hiện tại |
| `src`            | `String`       | Toàn cục | Mã nguồn (HTML/JSON) trả về từ yêu cầu |
| `nextChapterUrl` | `String`       | Toàn cục | URL của chương tiếp theo |

## 3. Các phương thức của đối tượng java

Đối tượng `java` là đối tượng công cụ cốt lõi được Legado hiển thị cho môi trường JS, tổng hợp các phương thức từ nhiều lớp mở rộng khác nhau.

### 3.1 Phần mở rộng RSS ([RssJsExtensions](https://github.com/HapeLee/legado-with-MD3/blob/master/app/src/main/java/io/legado/app/ui/rss/read/RssJsExtensions.kt))

::: warning Giới hạn phạm vi
Chỉ có thể sử dụng trong quy tắc `shouldOverrideUrlLoading` của nguồn đăng ký (RSS). Quy tắc chặn chuyển hướng URL không thể thực hiện các thao tác tốn thời gian.
:::

```js
java.searchBook(bookName: String)  // Gọi tính năng tìm kiếm của Legado
java.addBook(bookUrl: String)      // Thêm sách vào giá sách
```

### 3.2 Phân tích URL ([AnalyzeUrl](https://github.com/HapeLee/legado-with-MD3/blob/master/app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeUrl.kt))

Được gọi thông qua `java.`, chỉ có hiệu lực trong quy tắc `Kịch bản JS kiểm tra đăng nhập`.

| Phương thức | Giá trị trả về | Mô tả |
|:---------------------------------------------------|:--------------|:--------------------|
| `initUrl()`                                        | —             | Phân tích lại URL |
| `getHeaderMap().putAll(source.getHeaderMap(true))` | —             | Thiết lập lại headers đăng nhập |
| `getStrResponse(jsStr, sourceRegex)`               | `StrResponse` | Trả về kết quả truy cập dưới dạng văn bản (text) |
| `getResponse()`                                    | `Response`    | Trả về kết quả truy cập dưới dạng đối tượng Response |

### 3.3 Phân tích quy tắc ([AnalyzeRule](https://github.com/HapeLee/legado-with-MD3/blob/master/app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeRule.kt))

```js
// Lấy văn bản / Danh sách văn bản
java.getString(ruleStr: String?, mContent: Any? = null, isUrl: Boolean = false)
java.getStringList(ruleStr: String?, mContent: Any? = null, isUrl: Boolean = false)

// Thiết lập nội dung cần phân tích
java.setContent(content: Any?, baseUrl: String? = null)

// Lấy đối tượng Element / Danh sách Element
java.getElement(ruleStr: String)
java.getElements(ruleStr: String)

// Tìm kiếm lại sách / Lấy lại URL mục lục (chỉ dùng trước khi làm mới mục lục)
java.reGetBook()
java.refreshTocUrl()

// Lưu và truy xuất biến
java.get(key)
java.put(key, value)
```

### 3.4 Công cụ mở rộng ([JsExtensions](https://github.com/HapeLee/legado-with-MD3/blob/master/app/src/main/java/io/legado/app/help/JsExtensions.kt))

#### Phân tích liên kết

| Phương thức | Giá trị trả về | Mô tả |
|:---------------------------|:---------|:----------------------|
| `java.toURL(url)`          | `JsURL`  | Phân tích chuỗi thành đối tượng URL |
| `java.toURL(url, baseUrl)` | `JsURL`  | Phân tích URL tương đối dựa trên baseUrl |
| `java.getWebViewUA()`      | `String` | Lấy User-Agent của WebView |

#### Yêu cầu mạng

| Phương thức | Giá trị trả về | Mô tả |
|:--------------------------------------------------------------|:----------------------|:------------------------------------------------------------------|
| `java.ajax(urlStr)`                                           | `String`              | Yêu cầu GET, trả về phần thân phản hồi (response body) |
| `java.ajaxAll(urlList)`                                       | `Array<StrResponse>`  | Yêu cầu mạng hàng loạt |
| `java.connect(urlStr)`                                        | `StrResponse`         | Trả về kết quả chứa các phương thức: `body()` `code()` `message()` `headers()` `raw()` `toString()` |
| `java.post(url, body, headerMap)`                             | `Connection.Response` | Yêu cầu POST |
| `java.get(url, headerMap)`                                    | `Connection.Response` | Yêu cầu GET |
| `java.head(url, headerMap)`                                   | `Connection.Response` | Yêu cầu HEAD |
| `java.webView(html, url, js)`                                 | `String?`             | Sử dụng WebView để truy cập mạng |
| `java.webViewGetOverrideUrl(html, url, js, overrideUrlRegex)` | `String?`             | Sử dụng WebView để lấy URL chuyển hướng |
| `java.webViewGetSource(html, url, js, sourceRegex)`           | `String?`             | Sử dụng WebView để dò tìm URL tài nguyên |
| `java.startBrowser(url, title)`                               | —                     | Mở liên kết bằng trình duyệt tích hợp sẵn |
| `java.startBrowserAwait(url, title, refetchAfterSuccess)`     | `StrResponse`         | Mở liên kết bằng trình duyệt tích hợp và đợi kết quả trả về |

#### Gỡ lỗi và Thông báo

| Phương thức | Mô tả |
|:-------------------------------------|:---------|
| `java.log(msg)`                      | Ghi log (nhật ký) |
| `java.logType(var)`                  | In ra kiểu dữ liệu của biến |
| `java.getVerificationCode(imageUrl)` | Hiển thị hộp thoại nhập mã xác thực (CAPTCHA) |
| `java.longToast(msg)`                | Hiển thị thông báo Toast trong thời gian dài |
| `java.toast(msg)`                    | Hiển thị thông báo Toast nhanh (thời gian ngắn) |

#### Nhập kịch bản (Import Script)

| Phương thức | Mô tả |
|:----------------------------------|:-------------------------------------------|
| `java.importScript(url)`          | Tải và nhúng script từ mạng |
| `java.importScript(relativePath)` | Tải script từ đường dẫn tương đối (hỗ trợ thư mục cache `android/data/{package}/cache`) |
| `java.importScript(absolutePath)` | Tải script từ đường dẫn tuyệt đối |

#### Bộ nhớ đệm tệp (File Cache)

```js
java.cacheFile(url)              // Lưu tệp tải về từ mạng vào bộ nhớ đệm
java.cacheFile(url, saveTime)    // Lưu tệp vào bộ nhớ đệm với thời gian lưu trữ chỉ định
eval(String(java.cacheFile(url)))  // Tải vào bộ nhớ đệm và thực thi
cache.delete(java.md5Encode16(url))  // Làm mất hiệu lực của tệp trong bộ nhớ đệm
```

#### Mã hóa và Chuyển đổi

| Phân loại | Phương thức | Mô tả |
|:----------|:-------------------------------------------|:---------------------|
| URI       | `java.encodeURI(str, enc?)`                | Mã hóa URI, mặc định là UTF-8 |
| Base64    | `java.base64Decode(str, charset?)`         | Giải mã Base64 thành chuỗi |
| Base64    | `java.base64DecodeToByteArray(str, flags)` | Giải mã Base64 thành mảng byte ByteArray |
| Base64    | `java.base64Encode(str, flags)`            | Mã hóa Base64 |
| ByteArray | `java.strToBytes(str, charset?)`           | Chuyển đổi chuỗi thành mảng byte ByteArray |
| ByteArray | `java.bytesToStr(bytes, charset?)`         | Chuyển đổi mảng byte ByteArray thành chuỗi |
| Hex       | `java.hexDecodeToByteArray(hex)`           | Giải mã chuỗi Hex thành mảng byte ByteArray |
| Hex       | `java.hexDecodeToString(hex)`              | Giải mã chuỗi Hex thành chuỗi thông thường |
| Hex       | `java.hexEncodeToString(utf8)`             | Chuyển đổi chuỗi thành chuỗi Hex |

#### Định danh và Định dạng

| Phương thức | Giá trị trả về | Mô tả |
|:---------------------------------------|:----------|:--------------|
| `java.randomUUID()`                    | `String`  | Tạo mã UUID ngẫu nhiên |
| `java.androidId()`                     | `String`  | Lấy ID Android của thiết bị |
| `java.t2s(text)`                       | `String`  | Chuyển chữ Phồn thể sang Giản thể |
| `java.s2t(text)`                       | `String`  | Chuyển chữ Giản thể sang Phồn thể |
| `java.timeFormatUTC(time, format, sh)` | `String?` | Định dạng thời gian UTC |
| `java.timeFormat(time)`                | `String`  | Định dạng thời gian |
| `java.htmlFormat(str)`                 | `String`  | Định dạng mã HTML |

#### Thao tác tệp (File Operations)

::: tip Giới hạn đường dẫn
Tất cả các thao tác đọc, ghi và xóa tệp đều sử dụng đường dẫn tương đối và chỉ có thể thực hiện trên các tệp nằm trong thư mục bộ nhớ đệm của ứng dụng: `android/data/{package}/cache/`.
:::

| Phương thức | Giá trị trả về | Mô tả |
|:----------------------------|:---------|:------------|
| `downloadFile(url)`         | `String` | Tải tệp xuống, trả về đường dẫn của tệp |
| `unArchiveFile(zipPath)`    | `String` | Giải nén tệp, trả về thư mục giải nén |
| `unzipFile(zipPath)`        | `String` | Giải nén tệp ZIP |
| `unrarFile(zipPath)`        | `String` | Giải nén tệp RAR |
| `un7zFile(zipPath)`         | `String` | Giải nén tệp 7Z |
| `getTxtInFolder(unzipPath)` | `String` | Đọc toàn bộ nội dung các tệp văn bản trong thư mục |
| `readTxtFile(path)`         | `String` | Đọc tệp văn bản |
| `deleteFile(path)`          | —        | Xóa tệp |

#### Mở liên kết ngoài (External Link Redirection)

| Phương thức | Mô tả |
|:------------------------------|:-------------------------|
| `java.openUrl(url)`           | Mở liên kết ngoài (giao thức HTTP hoặc URL scheme) |
| `java.openUrl(url, mimeType)` | Mở liên kết ngoài với kiểu MIME chỉ định, ví dụ: `video/*` |

### 3.5 Mã hóa và Giải mã (JsEncodeUtils)

Cung cấp các hàm giúp gọi nhanh các thuật toán mã hóa trong môi trường JavaScript, được hiện thực hóa bởi thư viện [hutool-crypto](https://www.hutool.cn/docs/#/crypto/概述) (phiên bản hiện tại là 5.8.22).

::: warning Kiểu dữ liệu đầu vào
Nếu tham số đầu vào không phải là chuỗi Utf8String, bạn có thể gọi `java.hexDecodeToByteArray` hoặc `java.base64DecodeToByteArray` để chuyển đổi nó thành `ByteArray` trước.
:::

#### Mã hóa đối xứng (Symmetric Encryption)

```js
// Tạo đối tượng Cipher, key/iv hỗ trợ mảng byte ByteArray hoặc chuỗi Utf8String
java.createSymmetricCrypto(transformation, key, iv)

// data hỗ trợ ByteArray, chuỗi mã hóa Base64String, chuỗi HexString hoặc InputStream
cipher.decrypt(data)          // Giải mã thành mảng byte ByteArray
cipher.decryptStr(data)       // Giải mã thành chuỗi
cipher.encrypt(data)          // Mã hóa thành mảng byte ByteArray
cipher.encryptBase64(data)    // Mã hóa thành chuỗi Base64
cipher.encryptHex(data)       // Mã hóa thành chuỗi Hex
```

#### Mã hóa không đối xứng (Asymmetric Encryption)

```js
java.createAsymmetricCrypto(transformation)
  .setPublicKey(key)
  .setPrivateKey(key)

cipher.decrypt(data, usePublicKey: Boolean? = true)
cipher.decryptStr(data, usePublicKey: Boolean? = true)
cipher.encrypt(data, usePublicKey: Boolean? = true)
cipher.encryptBase64(data, usePublicKey: Boolean? = true)
cipher.encryptHex(data, usePublicKey: Boolean? = true)
```

#### Chữ ký số (Signature)

```js
java.createSign(algorithm)
  .setPublicKey(key)
  .setPrivateKey(key)

sign.sign(data)
sign.signHex(data)
```

#### Hàm băm (Digest) và HMAC

| Phương thức | Giá trị trả về | Mô tả |
|:----------------------------------------|:----------|:-------------|
| `java.digestHex(data, algorithm)`       | `String?` | Băm dữ liệu (Hex) |
| `java.digestBase64Str(data, algorithm)` | `String?` | Băm dữ liệu (Base64) |
| `java.md5Encode(str)`                   | `String`  | Băm MD5 (32 ký tự) |
| `java.md5Encode16(str)`                 | `String`  | Băm MD5 (16 ký tự) |
| `java.HMacHex(data, algorithm, key)`    | `String`  | Tạo mã HMAC (Hex) |
| `java.HMacBase64(data, algorithm, key)` | `String`  | Tạo mã HMAC (Base64) |

## 4. Đối tượng book (Sách)

Truy xuất bằng cú pháp `book.thuoc_tinh` bên trong JS hoặc cặp ngoặc `{{}}`.

| Thuộc tính | Kiểu dữ liệu | Mô tả |
|:---------------------|:----------|:--------------------|
| `bookUrl`            | `String`  | URL trang chi tiết (đối với nguồn sách cục bộ là đường dẫn tệp đầy đủ) |
| `tocUrl`             | `String`  | URL trang mục lục |
| `origin`             | `String`  | URL của nguồn sách |
| `originName`         | `String`  | Tên nguồn sách hoặc tên tệp sách cục bộ |
| `name`               | `String`  | Tên cuốn sách |
| `author`             | `String`  | Tên tác giả |
| `kind`               | `String`  | Thông tin thể loại |
| `customTag`          | `String`  | Thông tin thể loại (do người dùng chỉnh sửa) |
| `coverUrl`           | `String`  | URL ảnh bìa |
| `customCoverUrl`     | `String`  | URL ảnh bìa (do người dùng chỉnh sửa) |
| `intro`              | `String`  | Nội dung giới thiệu |
| `customIntro`        | `String`  | Nội dung giới thiệu (do người dùng chỉnh sửa) |
| `charset`            | `String`  | Bảng mã tùy chỉnh (chỉ áp dụng cho sách cục bộ) |
| `type`               | `Int`     | Kiểu nguồn: 0 (Văn bản), 1 (Âm thanh) |
| `group`              | `Int`     | Số chỉ mục nhóm tự định nghĩa |
| `latestChapterTitle` | `String`  | Tiêu đề chương mới nhất |
| `latestChapterTime`  | `Long`    | Thời gian cập nhật chương mới nhất |
| `lastCheckTime`      | `Long`    | Thời gian cập nhật thông tin sách gần nhất |
| `lastCheckCount`     | `Int`     | Số lượng chương mới phát hiện trong lần cập nhật gần nhất |
| `totalChapterNum`    | `Int`     | Tổng số chương trong mục lục |
| `durChapterTitle`    | `String`  | Tên chương hiện tại |
| `durChapterIndex`    | `Int`     | Chỉ mục (index) của chương hiện tại |
| `durChapterPos`      | `Long`    | Tiến độ đọc hiện tại |
| `durChapterTime`     | `Long`    | Thời gian đọc gần nhất |
| `canUpdate`          | `Boolean` | Có cập nhật khi làm mới kệ sách hay không |
| `order`              | `Int`     | Thứ tự sắp xếp thủ công |
| `originOrder`        | `Int`     | Thứ tự sắp xếp theo nguồn |
| `variable`           | `String?` | Biến tùy chỉnh của sách |

## 5. Đối tượng chapter (Chương)

| Thuộc tính | Kiểu dữ liệu | Mô tả |
|:--------------|:----------|:-----------|
| `url`         | `String`  | Địa chỉ chương |
| `title`       | `String`  | Tiêu đề chương |
| `baseUrl`     | `String`  | Dùng để ghép nối URL tương đối |
| `bookUrl`     | `String`  | Địa chỉ sách |
| `index`       | `Int`     | Số thứ tự chương |
| `resourceUrl` | `String`  | URL thực tế của tệp âm thanh |
| `tag`         | `String`  | Nhãn (tag) |
| `start`       | `Long`    | Vị trí bắt đầu chương |
| `end`         | `Long`    | Vị trí kết thúc chương |
| `variable`    | `String?` | Biến tùy chỉnh |

## 6. Đối tượng source (Nguồn sách)

| Phương thức | Giá trị trả về | Mô tả |
|:--------------------------------------|:----------|:----------|
| `source.getKey()`                     | `String`  | Lấy URL của nguồn |
| `source.setVariable(variable)`        | —         | Thiết lập biến nguồn |
| `source.getVariable()`                | `String?` | Lấy biến nguồn |
| `source.getLoginHeader()`             | `String?` | Lấy headers đăng nhập |
| `source.getLoginHeaderMap().get(key)` | `String?` | Lấy một giá trị cụ thể trong headers đăng nhập |
| `source.putLoginHeader(header)`       | —         | Lưu headers đăng nhập |
| `source.removeLoginHeader()`          | —         | Xóa headers đăng nhập |
| `source.getLoginInfo()`               | `String?` | Lấy thông tin đăng nhập |
| `source.getLoginInfoMap().get(key)`   | `String?` | Lấy giá trị của một khóa trong thông tin đăng nhập |
| `source.removeLoginInfo()`            | —         | Xóa thông tin đăng nhập |

## 7. Đối tượng cookie

| Phương thức | Giá trị trả về | Mô tả |
|:------------------------------------|:----------|:------------|
| `cookie.getCookie(url)`             | `String`  | Lấy toàn bộ Cookie |
| `cookie.getKey(url, key)`           | `String?` | Lấy một giá trị cụ thể của Cookie |
| `cookie.setCookie(url, cookie)`     | —         | Thiết lập Cookie |
| `cookie.replaceCookie(url, cookie)` | —         | Thay thế Cookie |
| `cookie.removeCookie(url)`          | —         | Xóa Cookie |

## 8. Đối tượng cache (Bộ nhớ đệm)

Đơn vị `saveTime`: Giây, có thể lược bỏ. Lưu vào cơ sở dữ liệu và tệp bộ nhớ đệm (dung lượng lên đến 50MB), nếu nội dung lớn vui lòng sử dụng phương thức `getFile` / `putFile`.

| Phương thức | Giá trị trả về | Mô tả |
|:---------------------------------------|:----------|:--------|
| `cache.put(key, value, saveTime?)`     | —         | Lưu vào cơ sở dữ liệu |
| `cache.get(key)`                       | `String?` | Đọc từ cơ sở dữ liệu |
| `cache.delete(key)`                    | —         | Xóa bộ nhớ đệm trong cơ sở dữ liệu |
| `cache.putFile(key, value, saveTime?)` | —         | Lưu nội dung tệp vào bộ nhớ đệm |
| `cache.getFile(key)`                   | `String?` | Đọc nội dung tệp từ bộ nhớ đệm |
| `cache.putMemory(key, value)`          | —         | Lưu vào bộ nhớ RAM (Memory) |
| `cache.getFromMemory(key)`             | `Any?`    | Đọc từ bộ nhớ RAM |
| `cache.deleteMemory(key)`              | —         | Xóa bộ nhớ đệm RAM |
