# Hướng dẫn sử dụng nhanh sách qua WebDAV

[[toc]]

> Trang trợ giúp này sẽ hiển thị lên trong lần đầu tiên bạn truy cập, các lần sau sẽ không xuất hiện nữa. Nếu muốn xem lại, vui lòng nhấp vào biểu tượng dấu ba chấm dọc ở góc trên bên phải " **⁝** " > Trợ giúp để xem trang này.

Ứng dụng này chủ yếu được sử dụng làm trình đọc tổng hợp nội dung, đồng thời cung cấp tính năng quản lý sách cục bộ (hỗ trợ các định dạng EPUB, TXT).

Một vấn đề thường gặp khi quản lý sách cục bộ là làm thế nào để đồng bộ tiến độ đọc và tệp sách giữa nhiều thiết bị khác nhau. Khi đổi thiết bị mới, bạn phải nhập thủ công lại sách từ thiết bị cũ, thao tác này khá rườm rà.

Bản thân ứng dụng này không cung cấp dịch vụ lưu trữ đám mây trực tiếp, nhưng hỗ trợ giao thức sao lưu WebDAV, bạn có thể tận dụng WebDAV để đồng bộ hóa sách.

## Điều kiện tiên quyết

1. **Cấu hình vị trí lưu trữ sách** (nơi lưu trữ sách tải xuống từ WebDAV): Đi tới **Cá nhân (Của tôi) / Cài đặt khác / Vị trí lưu trữ sách**, chọn thư mục lưu sách.

2. **Cấu hình sao lưu WebDAV** (nơi lưu trữ tệp sao lưu trên WebDAV): **Cá nhân (Của tôi) / Sao lưu & Khôi phục / Cài đặt WebDAV**. Tại đây cần điền đầy đủ địa chỉ máy chủ, tài khoản và mật khẩu của dịch vụ WebDAV.

## Tải sách lên WebDAV

Sau khi cấu hình xong WebDAV, khi bạn vào trang sách WebDAV từ giao diện chính và thấy trống không thì đó là điều hoàn toàn bình thường, bởi vì trên máy chủ WebDAV chưa có bất kỳ cuốn sách nào.

Hiện tại có ba cách để tải sách lên WebDAV:

### Cách 1: Tải lên sách cục bộ đã nhập thông qua ứng dụng

Nhấn giữ cuốn sách cục bộ đã nhập trên giao diện để vào chi tiết sách → nhấp vào biểu tượng ba chấm " **⁝** " ở góc trên bên phải → chọn **Tải lên WebDAV**, đợi vài giây để quá trình hoàn tất.

### Cách 2: Tải lên sách mạng đã lưu vào bộ nhớ đệm (cache) qua ứng dụng

Từ giao diện chính, nhấp vào biểu tượng ba chấm ở góc trên bên phải để vào Cài đặt khác → chọn Bộ nhớ đệm / Xuất sách, tại giao diện này nhấp tiếp vào ba chấm dọc " **⁝** " chọn **Xuất ra WebDAV** và tích chọn. Khi đó, mỗi lần xuất sách, một bản sao sẽ tự động được tải lên máy chủ WebDAV.

### Cách 3: Sử dụng phần mềm máy khách (client) của bên thứ ba để tải lên hàng loạt

Đối với phần lớn người dùng, tính năng tải lên qua ứng dụng là đã đủ dùng. Tuy nhiên, nếu bạn có số lượng sách rất lớn, phương pháp tối ưu hơn là sử dụng phần mềm máy khách (client) của dịch vụ WebDAV đó để tải lên hàng loạt.

Ví dụ, tải phần mềm client của dịch vụ WebDAV bạn đang dùng về máy tính hoặc thiết bị tương ứng, tìm thư mục `legado/books` (đây là vị trí lưu trữ sách), sau đó bạn có thể sao chép hàng loạt sách vào thư mục này để tải lên.

::: warning Chú ý
Dù sử dụng bất kỳ cách nào trong ba cách trên để tải sách lên, để chắc chắn không xảy ra lỗi, bạn nên truy cập vào trang sách WebDAV trên ứng dụng để kiểm tra xem đã thấy danh sách sách hiển thị hay chưa.
:::

## Tải sách từ WebDAV về thiết bị

Tại **Trang sách WebDAV**, duyệt qua các cuốn sách đã tải lên, tìm cuốn sách cần tải về và nhấp vào nút **Thêm vào giá sách**. Phần mềm sẽ tự động tải sách về thư mục lưu trữ cục bộ và thêm nó vào giá sách của bạn.

## Các lưu ý quan trọng

Một số dịch vụ WebDAV miễn phí có giới hạn dung lượng băng thông hoặc lưu lượng truyền tải. Dung lượng này thường đủ cho việc đồng bộ cài đặt và một lượng nhỏ sách. Vui lòng chú ý dung lượng sử dụng khi thường xuyên tải lên/tải xuống các tệp sách lớn để tránh vượt quá giới hạn gây ảnh hưởng đến việc đồng bộ.

## Câu hỏi thường gặp

### Vào trang sách WebDAV hiện thông báo "Lỗi lấy sách WebDAV, WebDAV chưa được cấu hình"

Nguyên nhân là do bạn chưa thiết lập dịch vụ đồng bộ WebDAV. Vui lòng làm theo hướng dẫn cấu hình WebDAV được nêu ở mục Điều kiện tiên quyết phía trên.

### Sách cục bộ do thiết bị A tải lên có hiển thị trên thiết bị B không?

Nếu thiết bị A và thiết bị B cấu hình cùng một tài khoản WebDAV, thiết bị B sẽ thấy sách do A tải lên tại **Trang sách WebDAV**. Tuy nhiên, sách sẽ không tự động xuất hiện trực tiếp trên giá sách của thiết bị B; bạn bắt buộc phải vào **Trang sách WebDAV** của thiết bị B, tìm cuốn sách đó và bấm thủ công **Thêm vào giá sách** để nhập sách vào.

### Tiến độ đọc và dấu trang của sách cục bộ có được đồng bộ không?

Có, các thông tin này có thể được đồng bộ bình thường.
