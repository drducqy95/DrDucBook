# JavaScript trong rule

Một số rule cho phép dùng JavaScript để xử lý dữ liệu trước hoặc sau khi trích xuất. Chỉ nên dùng JS khi selector, XPath hoặc regex không đủ.

## Biến thường gặp

Tùy vị trí rule, app có thể cung cấp các biến như:

- `result`: dữ liệu hiện tại.
- `baseUrl`: URL nền.
- `src`: URL hoặc chuỗi nguồn đang xử lý.
- `java`: cầu nối tới một số hàm mở rộng của app.
- `cookie`: cookie hiện có nếu nguồn dùng phiên đăng nhập.

Tên biến cụ thể phụ thuộc màn rule. Hãy kiểm tra log gỡ lỗi khi không chắc.

## Ví dụ ngắn

```javascript
result.replace(/\s+/g, " ").trim()
```

```javascript
baseUrl + result
```

```javascript
JSON.parse(result).data.items
```

## Nguyên tắc an toàn

- Không đặt token, mật khẩu hoặc cookie thật vào rule chia sẻ công khai.
- Tránh vòng lặp vô hạn hoặc xử lý quá nặng trong JS.
- Nếu dữ liệu là JSON, ưu tiên parse JSON thay vì regex phức tạp.
- Nếu chỉ cần nối URL hoặc xóa khoảng trắng, dùng rule đơn giản trước.

## Gỡ lỗi

Khi JS lỗi, app thường dừng ở rule hiện tại. Hãy kiểm tra:

- Dấu ngoặc, dấu nháy và dấu chấm phẩy.
- Kiểu dữ liệu của `result`.
- Dữ liệu rỗng do bước trước chưa trích được.
- Trang nguồn trả captcha, chuyển hướng hoặc nội dung khác với dự kiến.

