# Cấu hình module trang khám phá

Module trang khám phá quyết định app hiển thị danh mục, bảng xếp hạng, truyện mới hoặc các trang con từ nguồn sách như thế nào.

## Cấu trúc chung

Mỗi module thường có:

- Tên hiển thị.
- URL hoặc rule tạo URL.
- Loại dữ liệu trả về.
- Rule danh sách truyện.
- Rule tên, tác giả, bìa, mô tả, URL chi tiết.

## Gợi ý thiết kế

- Mỗi danh mục nên là một module riêng để dễ lọc và tải lại.
- Tên module nên ngắn, rõ nghĩa, ví dụ: Truyện mới, Đang hot, Hoàn thành.
- Nếu nguồn có nhiều trang, cấu hình rule phân trang hoặc biến trang.
- Nếu nguồn thay đổi URL, chỉ sửa module liên quan thay vì sửa toàn bộ nguồn.

## Lỗi thường gặp

- Module rỗng: URL sai, nguồn chặn, selector danh sách không khớp hoặc thiếu header.
- Có danh sách nhưng thiếu bìa: rule ảnh tương đối chưa ghép domain.
- Nhấn truyện không mở chi tiết: URL chi tiết không đúng hoặc chưa chuẩn hóa.
- Dữ liệu bị lặp: rule danh sách bắt nhầm vùng cha quá rộng.

## Kiểm tra

Sau khi sửa module, mở màn khám phá, chọn đúng nguồn và tải lại danh mục. Nếu vẫn lỗi, dùng gỡ lỗi nguồn để xem HTML/JSON thật sự trả về.

