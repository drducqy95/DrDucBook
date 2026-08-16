# Hướng dẫn rule nguồn sách

Rule nguồn sách cho app biết cách tìm truyện, đọc thông tin, lấy mục lục và tải nội dung chương từ một website hoặc API.

## Luồng xử lý nguồn sách

1. Tìm kiếm hoặc mở trang khám phá.
2. Trích danh sách truyện.
3. Mở trang thông tin sách.
4. Trích tên, tác giả, bìa, mô tả, trạng thái và URL mục lục.
5. Trích danh sách chương.
6. Mở từng chương và trích nội dung.
7. Làm sạch nội dung bằng rule thay thế nếu có.

## Nhóm rule chính

- Rule tìm kiếm: tạo URL tìm kiếm và đọc danh sách kết quả.
- Rule khám phá: đọc danh mục, bảng xếp hạng hoặc truyện mới.
- Rule thông tin sách: đọc dữ liệu trên trang chi tiết.
- Rule mục lục: đọc danh sách chương, tên chương và URL chương.
- Rule nội dung: đọc nội dung chương, ảnh chương hoặc dữ liệu đặc biệt.
- Rule đăng nhập/header/cookie: dùng khi nguồn cần phiên truy cập.

## Cú pháp thường dùng

App hỗ trợ nhiều cách trích dữ liệu, tùy màn và kiểu nguồn:

- CSS selector cho HTML.
- XPath cho HTML phức tạp.
- JSON path cho API JSON.
- Regex cho văn bản.
- JavaScript cho xử lý đặc biệt.

Ưu tiên cách đơn giản và ổn định nhất. Không nên dùng JavaScript nếu selector hoặc JSON path đã đủ.

## Ghép URL

Nhiều nguồn trả URL tương đối. Hãy kiểm tra:

- URL bắt đầu bằng `http` thường dùng trực tiếp.
- URL bắt đầu bằng `/` cần ghép với domain.
- URL không có dấu `/` đầu có thể cần ghép với thư mục hiện tại.

Nếu bìa, chương hoặc truyện liên quan không mở được, lỗi thường nằm ở bước chuẩn hóa URL.

## Chống lỗi nguồn

- Thêm User-Agent hoặc header khi nguồn chặn client lạ.
- Tăng thời gian chờ khi nguồn chậm.
- Dùng khoảng nghỉ tải xuống để tránh bị chặn IP.
- Kiểm tra captcha, chuyển hướng, Cloudflare hoặc trang lỗi.
- Khi nguồn đổi giao diện, sửa rule theo HTML/JSON mới.

## Gỡ lỗi

Hãy kiểm tra từng tầng:

1. URL tạo ra có đúng không.
2. HTTP status có thành công không.
3. Nội dung trả về có đúng trang mong muốn không.
4. Rule danh sách có bắt đúng vùng không.
5. Rule trường con có lấy đúng dữ liệu không.
6. Kết quả cuối có bị rule thay thế hoặc cache cũ ảnh hưởng không.

