# Regex mục lục file TXT

Đối với sách cục bộ định dạng TXT, Legado sử dụng biểu thức chính quy để tự động nhận dạng tiêu đề chương và tạo mục lục. Khi các quy tắc mặc định không đáp ứng được nhu cầu, bạn có thể tự định nghĩa Regex mục lục cho riêng mình.

[[toc]]

## Khu vực Menu

| Nút | Mô tả |
|------------|-------------------------|
| **Thêm quy tắc mục lục mới** | Tạo Regex mục lục tùy chỉnh, đáp ứng nhu cầu cá nhân |
| **Nhập quy tắc mặc định** | Khôi phục hoặc cập nhật các quy tắc mục lục mặc định tích hợp sẵn của Legado |
| **Nhập từ mạng**   | Nhập các quy tắc mục lục do người khác chia sẻ từ internet |
| **Chia nhỏ chương quá dài** | Sau khi kích hoạt, một chương đơn lẻ vượt quá khoảng 30.000 chữ sẽ tự động được chia nhỏ thành nhiều chương |

::: tip Chú ý
Việc nhập các quy tắc mặc định sẽ không ghi đè lên các quy tắc do người dùng tự định nghĩa, nhưng sẽ đặt lại các sửa đổi của người dùng đối với các quy tắc tích hợp sẵn.
:::

## Khu vực Thao tác

Các nút trên giao diện được chia thành ba nhóm:

- **Nút ①** (Nút chọn duy nhất): Khi được chọn, nó biểu thị sách hiện tại sẽ áp dụng quy tắc mục lục này. Nếu mục lục do Legado tự động nhận diện không lý tưởng, bạn có thể chọn thủ công các quy tắc khác. Nút này **chỉ có hiệu lực đối với cuốn sách hiện tại**.
- **Nhóm nút ②**:
    - Công tắc bên trái: Sau khi bật, quy tắc này sẽ được thử khớp khi tự động nhận diện mục lục, **có hiệu lực đối với tất cả sách định dạng TXT**
    - Nút ở giữa: Chỉnh sửa quy tắc hiện tại
    - Nút bên phải: Xóa quy tắc hiện tại (các quy tắc tích hợp sau khi bị xóa có thể khôi phục thông qua tính năng "Nhập quy tắc mặc định")
- **Nút ③** (Xác nhận): Sau khi thực hiện các thao tác trên giao diện hiện tại, bạn cần nhấp vào nút xác nhận để lựa chọn có hiệu lực
