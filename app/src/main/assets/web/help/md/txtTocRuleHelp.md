# Quy tắc mục lục TXT

Quy tắc mục lục TXT dùng để nhận diện tiêu đề chương trong file văn bản cục bộ. Nếu nhận diện sai, app có thể chia chương thiếu, gộp chương hoặc xem dòng nội dung như tiêu đề.

## Khu vực menu

- Thêm rule: tạo quy tắc mới khi rule có sẵn không phù hợp.
- Nhập rule mặc định: khôi phục hoặc cập nhật bộ rule đi kèm app.
- Nhập từ mạng: tải bộ rule chia sẻ qua URL.
- Chia chương dài: tự tách chương quá dài thành nhiều phần nhỏ để đọc mượt hơn.

## Chọn rule cho sách hiện tại

Nút chọn ở từng rule cho phép dùng rule đó riêng cho cuốn sách đang mở. Cách này phù hợp khi chỉ một file TXT bị nhận diện sai.

## Bật rule toàn cục

Khi bật một rule toàn cục, app sẽ thử rule đó cho các file TXT khác. Chỉ nên bật các rule đủ ổn định, tránh rule quá rộng làm nhận nhầm dòng nội dung.

## Viết regex tiêu đề chương

Một rule tốt nên:

- Bắt đầu từ đầu dòng.
- Cho phép khoảng trắng đầu dòng nếu file có thụt dòng.
- Nhận diện từ khóa chương/phần/tiết hoặc dạng số rõ ràng.
- Không bắt các câu văn bình thường.

Ví dụ:

```regex
^\s*(Chương|Chapter)\s+\d+[:.\s-].*
```

## Kiểm tra

Sau khi đổi rule, tải lại mục lục của file TXT. Nếu mục lục vẫn sai, thử rule hẹp hơn hoặc tạo rule riêng cho sách hiện tại.

