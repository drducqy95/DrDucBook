# Trợ giúp giao diện quản lý nguồn

[[toc]]

## Ký hiệu nguồn

* 🟢 Chấm xanh lá: Nguồn có "Khám phá" và tính năng này đã được bật.
* 🔴 Chấm đỏ: Nguồn có "Khám phá" nhưng chưa được bật.
* Không có ký hiệu: Nguồn này không có tính năng "Khám phá".

## Menu chức năng

Ở góc trên bên phải có menu phân nhóm, bạn có thể lọc các nguồn theo nhóm.

Menu mở rộng (dấu 3 chấm) ở góc trên bên phải bao gồm:

* Tạo nguồn mới
* Nhập từ thiết bị (Nhập cục bộ)
* Nhập từ mạng (Nhập qua URL)
* Nhập bằng mã QR
* Chia sẻ các nguồn đã chọn

## Thao tác hàng loạt

Các tùy chọn thao tác mở rộng đối với nguồn nằm trong menu ở góc dưới bên phải. Các thao tác này sẽ áp dụng cho những nguồn đã được chọn:

* Bật mục đã chọn
* Tắt mục đã chọn
* Thêm vào nhóm
* Xóa khỏi nhóm
* Bật "Khám phá"
* Tắt "Khám phá"
* Ghim lên đầu
* Chuyển xuống cuối
* Xuất mục đã chọn
* Kiểm tra mục đã chọn

## Kiểm tra nguồn

Tính năng này cho phép kiểm tra (xác thực) hàng loạt các nguồn. Do các yếu tố như kết nối mạng, kết quả kiểm tra chỉ mang tính chất tham khảo.

* "Kiểm tra thành công" có nghĩa là tất cả các hạng mục được chọn để kiểm tra đều vượt qua.
* Hệ thống có thể nhận diện chính xác các nguyên nhân khiến nguồn bị hỏng (vô hiệu) như: Tìm kiếm trống, Khám phá trống, Mục lục Tìm kiếm (hoặc Khám phá) trống, Nội dung Tìm kiếm (hoặc Khám phá) trống, Hết thời gian kiểm tra (timeout) và Lỗi thực thi JS (JavaScript). Các nguyên nhân khác còn lại sẽ được xem là do trang web đã hỏng (ngừng hoạt động).
* Khi kiểm tra tính năng tìm kiếm, hệ thống sẽ ưu tiên sử dụng *Từ khóa kiểm tra* được thiết lập sẵn trong nguồn. Nếu không có, hệ thống mới sử dụng từ khóa do người dùng nhập vào.
* Sau khi quá trình kiểm tra kết thúc, hệ thống sẽ tự động lọc và hiển thị các nguồn đã bị "Hỏng".