# Các ví dụ về Nguồn sách (Source Examples)

[[toc]]

Tài liệu này cung cấp các ví dụ JSON hoàn chỉnh của ba loại nguồn sách điển hình, minh họa tương ứng cho cách sử dụng CSS+Regex, XPath+Regex và JSONPath.

## 1. CSS + Regex

Ví dụ nguồn sách sử dụng bộ chọn CSS JSOUP và biểu thức chính quy (Regex).

```json
{
    "bookSourceComment": "",
    "bookSourceGroup": "CSS; Regex",
    "bookSourceName": "Nguồn sách ví dụ",
    "bookSourceType": 0,
    "bookSourceUrl": "https://www.example.com",
    "bookUrlPattern": "",
    "customOrder": 0,
    "enabled": true,
    "enabledExplore": false,
    "exploreUrl": "",
    "lastUpdateTime": 0,
    "loginUrl": "",
    "ruleBookInfo": {
        "author": "##:author\"[^\"]+\"([^\"]*)##$1###",
        "coverUrl": "##og:image\"[^\"]+\"([^\"]*)##$1###",
        "intro": "##:description\"[^\"]+\"([\\w\\W]*?)\"/##$1###",
        "kind": "##:category\"[^\"]+\"([^\"]*)##$1###",
        "lastChapter": "##_chapter_name\"[^\"]+\"([^\"]*)##$1###",
        "name": "##:book_name\"[^\"]+\"([^\"]*)##$1###",
        "tocUrl": ""
    },
    "ruleContent": {
        "content": "@css:.chapter-content p@textNodes##Quảng cáo|Tuyên bố trang web.*|<!\\[CDATA\\[|\\]\\]>",
        "nextContentUrl": ""
    },
    "ruleExplore": {},
    "ruleSearch": {
        "author": "@css:p:eq(2)>a@text",
        "bookList": "@css:li.clearfix",
        "bookUrl": "@css:.name>a@href",
        "coverUrl": "@css:img@src",
        "intro": "@css:.note.clearfix p@text",
        "kind": "@css:.note_text,p:eq(4)@text",
        "lastChapter": "@css:p:eq(3)@text",
        "name": "@css:.name@text"
    },
    "ruleToc": {
        "chapterList": "-:<li><a[^\"]+\"([^\"]*)\">([^<]*)",
        "chapterName": "$2",
        "chapterUrl": "$1",
        "nextTocUrl": ""
    },
    "searchUrl": "/search?q={{key}}&page={{page}}",
    "weight": 0
}
```

**Điểm mấu chốt:**

- Quy tắc trang chi tiết sử dụng Regex OnlyOne (`##...###`) để trích xuất thông tin từ thẻ meta.
- Quy tắc nội dung chương sử dụng bộ chọn CSS để lấy các nút văn bản, sau đó dùng Regex thay thế (tẩy lọc) để loại bỏ quảng cáo.
- Quy tắc mục lục sử dụng Regex AllInOne (bắt đầu bằng dấu `:`), tiền tố `-` để đảo ngược danh sách.

## 2. XPath + Regex

Ví dụ nguồn sách sử dụng XPath và biểu thức chính quy (Regex).

```json
{
    "bookSourceComment": "",
    "bookSourceGroup": "XPath; Regex",
    "bookSourceName": "Nguồn di động ví dụ",
    "bookSourceType": 0,
    "bookSourceUrl": "https://m.example.com",
    "bookUrlPattern": "",
    "customOrder": 0,
    "enabled": true,
    "enabledExplore": false,
    "exploreUrl": "",
    "lastUpdateTime": 0,
    "loginUrl": "",
    "ruleBookInfo": {
        "author": "//*[@property=\"og:novel:author\"]/@content",
        "coverUrl": "//*[@property=\"og:image\"]/@content",
        "intro": "//*[@property=\"og:description\"]/@content",
        "kind": "//*[@property=\"og:novel:category\"]/@content",
        "lastChapter": "//*[@id=\"latest-chapter\"]//li[1]/a/text()",
        "name": "//*[@property=\"og:novel:book_name\"]/@content",
        "tocUrl": "//a[text()=\"Đọc\"]/@href"
    },
    "ruleContent": {
        "content": "//*[@id=\"content\"]",
        "nextContentUrl": ""
    },
    "ruleExplore": {},
    "ruleSearch": {
        "author": "//dd[2]/text()",
        "bookList": "//*[@id=\"search-result\"]/dl",
        "bookUrl": "//dt/a/@href",
        "coverUrl": "//img/@src",
        "kind": "//dd[2]/span/text()",
        "lastChapter": "",
        "name": "//h3/a/text()"
    },
    "ruleToc": {
        "chapterList": ":href=\"(/read[^\"]*html)\">([^<]*)",
        "chapterName": "$2",
        "chapterUrl": "$1",
        "nextTocUrl": "//*[@id=\"chapter-list\"]/*[position()>1]/@value"
    },
    "searchUrl": "/search,{\n  \"method\": \"POST\",\n  \"body\": \"q={{key}}\"\n}",
    "weight": 0
}
```

**Điểm mấu chốt:**

- Trang chi tiết sử dụng XPath để trích xuất thông tin từ thẻ meta `og:novel`.
- Mục lục sử dụng Regex AllInOne để trích xuất liên kết và tiêu đề.
- Trang tiếp theo của mục lục sử dụng điều kiện vị ngữ `position()` của XPath.
- Tìm kiếm sử dụng phương thức POST.

## 3. JSONPath

Ví dụ nguồn sách sử dụng JSON API.

```json
{
    "bookSourceComment": "",
    "bookSourceGroup": "JSON",
    "bookSourceName": "Nguồn API ví dụ",
    "bookSourceType": 0,
    "bookSourceUrl": "http://api.example.com",
    "customOrder": 0,
    "enabled": true,
    "enabledExplore": false,
    "header": "{\n  \"User-Agent\": \"Mozilla/5.0\"\n}",
    "lastUpdateTime": 0,
    "ruleBookInfo": {},
    "ruleContent": {
        "content": "$.chapter.body"
    },
    "ruleExplore": {},
    "ruleSearch": {
        "author": "$.author",
        "bookList": "$..books[*]",
        "bookUrl": "/book/detail?id={$._id}",
        "coverUrl": "$.cover",
        "intro": "$.shortIntro",
        "kind": "$.minorCate",
        "lastChapter": "$.lastChapter",
        "name": "$.title"
    },
    "ruleToc": {
        "chapterList": "$.chapterInfo.chapters.[*]",
        "chapterName": "$.title",
        "chapterUrl": "$.link"
    },
    "searchUrl": "/book/search?query={{key}}&start={{(page-1)*20}}&limit=20",
    "weight": 0
}
```

**Điểm mấu chốt:**

- Quy tắc trang chi tiết để trống (`ruleBookInfo: {}`), hệ thống lấy thông tin trực tiếp từ kết quả tìm kiếm.
- URL mục lục được ghép nối bằng biểu thức JSONPath `{$._id}`.
- Danh sách sách sử dụng tìm kiếm đệ quy `$..books[*]`.
- URL tìm kiếm sử dụng `{{(page-1)*20}}` để tính toán khoảng lệch (offset).

## 4. Mẹo gỡ lỗi

### Xử lý lỗi JS

Bao bọc logic JS bằng khối `try-catch` để thuận tiện cho việc gỡ lỗi:

```javascript
(function(result){
    try{
        // Xử lý result
        return result;
    }
    catch(e){
        return "" + e;  // Trả về thông báo lỗi
    }
})(result);
```

### Lối vào gỡ lỗi

| Mục gỡ lỗi | Ví dụ nhập liệu |
|:----|:--------------------------------------------------|
| Tìm kiếm | `Hệ thống` |
| Khám phá | `Bảng xếp hạng::https://www.example.com/rank?page={{page}}` |
| Trang chi tiết | `https://www.example.com/book/12345` |
| Trang mục lục | `++https://www.example.com/read/12345` |
| Trang nội dung | `--https://www.example.com/chapter/12345/67890` |

Xem phương pháp gỡ lỗi chi tiết tại [Gỡ lỗi nguồn sách (Debug)](./debug).
