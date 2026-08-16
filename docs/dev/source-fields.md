# Tra nhanh các trường trong Nguồn sách (Source Fields)

[[toc]]

Tài liệu này liệt kê tất cả các trường có sẵn trong từng phần của JSON nguồn (Tìm kiếm, Khám phá, Trang chi tiết, Mục lục, Nội dung).

## 1. Các trường cơ bản

| Trường | Bắt buộc | Mô tả |
|:--------------------|:---|:------------------------------------------|
| `bookSourceUrl`     | Có | URL của nguồn, là định danh duy nhất, không được trùng lặp. Nếu trùng với nguồn khác sẽ bị ghi đè |
| `bookSourceName`    | Có | Tên nguồn, có thể trùng lặp |
| `bookSourceGroup`   | Không | Nhóm nguồn, dùng để phân loại và sắp xếp |
| `bookSourceType`    | Không | Loại nguồn: `0` (Văn bản, mặc định), `1` (Âm thanh) |
| `bookUrlPattern`    | Không | Regex của URL sách, dùng để tự động nhận diện nguồn khi thêm liên kết trang web |
| `header`            | Không | Chuỗi JSON cấu hình request headers, xem tại [Cấu hình Request Headers](./request-headers) |
| `loginUrl`          | Không | URL đăng nhập, xem tại [Xác thực và Đăng nhập](./authentication) |
| `exploreUrl`        | Không | Địa chỉ khám phá, xem tại [Cấu hình URL Khám phá](./discovery-url) |
| `searchUrl`         | Không | Địa chỉ tìm kiếm |
| `weight`            | Không | Trọng số nguồn, dùng để sắp xếp thứ tự ưu tiên khi tìm kiếm |
| `enabled`           | Không | Có bật nguồn này hay không |
| `enabledExplore`    | Không | Có bật trang khám phá của nguồn này hay không |
| `customOrder`       | Không | Thứ tự sắp xếp tùy chỉnh |
| `lastUpdateTime`    | Không | Thời gian cập nhật cuối cùng |
| `bookSourceComment` | Không | Ghi chú nguồn sách |

## 2. Các trường tìm kiếm (`ruleSearch`)

| Trường | Mô tả |
|:--------------|:-----------------------------------|
| `url`         | Địa chỉ tìm kiếm. `{{key}}` đại diện cho từ khóa, `{{page}}` đại diện cho số trang |
| `bookList`    | Quy tắc danh sách sách |
| `name`        | Quy tắc tên sách |
| `author`      | Quy tắc tác giả |
| `kind`        | Quy tắc thể loại/phân loại |
| `wordCount`   | Quy tắc số chữ |
| `lastChapter` | Quy tắc chương mới nhất |
| `intro`       | Quy tắc giới thiệu/tóm tắt |
| `coverUrl`    | Quy tắc ảnh bìa |
| `bookUrl`     | Quy tắc URL trang chi tiết |

## 3. Các trường khám phá (`ruleExplore`)

| Trường | Mô tả |
|:--------------|:---------------------------------|
| `url`         | Địa chỉ khám phá. `{{page}}` đại diện cho số trang, hỗ trợ định dạng mảng JSON |
| `bookList`    | Quy tắc danh sách sách |
| `name`        | Quy tắc tên sách |
| `author`      | Quy tắc tác giả |
| `kind`        | Quy tắc thể loại/phân loại |
| `wordCount`   | Quy tắc số chữ |
| `lastChapter` | Quy tắc chương mới nhất |
| `intro`       | Quy tắc giới thiệu/tóm tắt |
| `coverUrl`    | Quy tắc ảnh bìa |
| `bookUrl`     | Quy tắc URL trang chi tiết |

## 4. Các trường trang chi tiết (`ruleBookInfo`)

| Trường | Mô tả |
|:---------------|:-----------------------------------------|
| `bookInfoInit` | Quy tắc tiền xử lý (chỉ hỗ trợ Regex AllInOne hoặc JS) |
| `name`         | Quy tắc tên sách |
| `author`       | Quy tắc tác giả |
| `kind`         | Quy tắc thể loại/phân loại |
| `wordCount`    | Quy tắc số chữ |
| `lastChapter`  | Quy tắc chương mới nhất |
| `intro`        | Quy tắc giới thiệu/tóm tắt |
| `coverUrl`     | Quy tắc ảnh bìa |
| `tocUrl`       | Quy tắc URL mục lục (chỉ hỗ trợ một URL duy nhất) |
| `canReName`    | Cho phép sửa đổi tên sách và tác giả |
| `relatedBooks` | Cấu hình sách liên quan, xem tại [Cấu hình sách liên quan](../spec/related-books) |

### Quy tắc tiền xử lý (`bookInfoInit`)

Chỉ hỗ trợ Regex AllInOne (bắt đầu bằng dấu `:`) hoặc JS. Giá trị trả về từ JS phải là một đối tượng JSON:

```javascript
(function(){
    return {
        a: "Tên sách",
        b: "Tác giả",
        c: "Thể loại",
        d: "Số chữ",
        e: "Chương mới nhất",
        f: "Giới thiệu",
        g: "URL ảnh bìa",
        h: "URL mục lục"
    };
})()
```

Khi đó, các trường quy tắc sẽ điền key tương ứng: `name` → `a`, `author` → `b`, tương tự cho các trường khác.

### Logic của `canReName`

| Điều kiện | Hành vi |
|:-----------------|:--------|
| Quy tắc không rỗng VÀ tên sách ở trang chi tiết không rỗng | Sử dụng tên sách ở trang chi tiết |
| Ngược lại | Sử dụng tên sách ở trang tìm kiếm |
| Quy tắc không rỗng VÀ tác giả ở trang chi tiết không rỗng | Sử dụng tác giả ở trang chi tiết |
| Ngược lại | Sử dụng tác giả ở trang tìm kiếm |

## 5. Các trường mục lục (`ruleToc`)

| Trường | Mô tả |
|:--------------|:---------------------------------------------------|
| `chapterList` | Quy tắc danh sách chương. Ký tự đầu tiên là dấu `-` có thể đảo ngược danh sách chương |
| `chapterName` | Quy tắc tên chương |
| `chapterUrl`  | Quy tắc URL chương |
| `isVip`       | Định danh VIP. Trả về `null`, `false`, `0`, `""` sẽ được coi là không phải VIP |
| `updateTime`  | Thông tin cập nhật chương (có thể dùng `java.timeFormat(timestamp)` để chuyển đổi dấu thời gian) |
| `nextTocUrl`  | Quy tắc trang tiếp theo của mục lục. Hỗ trợ một URL duy nhất hoặc mảng các URL, dừng lại khi JS trả về `[]`, `null`, hoặc `""` |

## 6. Các trường nội dung (`ruleContent`)

| Trường | Mô tả |
|:-----------------|:---------------------------------------|
| `content`        | Quy tắc nội dung chương |
| `nextContentUrl` | Quy tắc URL trang tiếp theo của nội dung. Hỗ trợ một URL duy nhất hoặc mảng các URL |
| `sourceRegex`    | Regex tài nguyên, dùng để dò tìm (sniff) tài nguyên đa phương tiện |
| `webJs`          | WebView JS, dùng để mô phỏng nhấp chuột và các thao tác khác. Bắt buộc phải có giá trị trả về (khác rỗng nghĩa là thực thi thành công) |

### WebView JS (`webJs`)

Dùng để mô phỏng nhấp chuột và các thao tác khác, giá trị trả về khác rỗng biểu thị thực thi thành công (nếu không sẽ bị lặp vô hạn), giá trị trả về được dùng cho Regex tài nguyên hoặc nội dung.

**Ví dụ:**

```javascript
getDecode();$('#content').html();
```

### Regex tài nguyên (`sourceRegex`)

Dùng để dò tìm (sniff) tài nguyên đa phương tiện do WebView tải lên. Sử dụng kết hợp với `{"webView": true}` trong URL chương.

Thông thường cấu hình `.*\.(mp3|mp4).*` là đủ để khớp các định dạng phương tiện phổ biến.

## 7. Cấu trúc JSON hoàn chỉnh

```json
{
    "bookSourceUrl": "https://www.example.com",
    "bookSourceName": "Nguồn sách ví dụ",
    "bookSourceGroup": "Nhóm",
    "bookSourceType": 0,
    "bookUrlPattern": "",
    "header": "",
    "loginUrl": "",
    "searchUrl": "/search?key={{key}}&page={{page}}",
    "exploreUrl": "",
    "enabled": true,
    "enabledExplore": false,
    "weight": 0,
    "ruleSearch": {
        "bookList": "",
        "name": "",
        "author": "",
        "kind": "",
        "wordCount": "",
        "lastChapter": "",
        "intro": "",
        "coverUrl": "",
        "bookUrl": ""
    },
    "ruleExplore": {
        "bookList": "",
        "name": "",
        "author": "",
        "bookUrl": ""
    },
    "ruleBookInfo": {
        "name": "",
        "author": "",
        "kind": "",
        "intro": "",
        "coverUrl": "",
        "tocUrl": ""
    },
    "ruleToc": {
        "chapterList": "",
        "chapterName": "",
        "chapterUrl": "",
        "nextTocUrl": ""
    },
    "ruleContent": {
        "content": "",
        "nextContentUrl": "",
        "sourceRegex": "",
        "webJs": ""
    }
}
```
