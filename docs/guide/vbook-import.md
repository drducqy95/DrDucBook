# Nhập extension VBook

Ứng dụng hỗ trợ registry URL, file JSON registry và gói plugin ZIP.

## Registry URL

1. Mở **Quản lý nguồn > VBook**.
2. Chọn nhập từ URL.
3. Dán URL dạng `https://www.vbookext.me/api/registry/<id>.json`.
4. Xem thông tin, quyền và script trước khi xác nhận cài.

## File JSON hoặc ZIP

- JSON phải có cấu trúc registry VBook tương thích.
- ZIP chỉ được chứa manifest và script trong whitelist.
- Đường dẫn `../`, đường dẫn tuyệt đối, URL nội bộ và API script bị cấm đều bị từ chối.
- Plugin trùng ID được xử lý theo phiên bản; ứng dụng không tạo bản ghi trùng im lặng.

## Media

Extension có thể trả video/audio trực tiếp, HLS, subtitle và audio track. Iframe ngoài ứng dụng được đánh dấu không hỗ trợ tải ngoại tuyến.

> Ảnh minh họa: `docs/assets/guide/vbook-import-review.png`
