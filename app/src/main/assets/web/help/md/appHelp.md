# Tài liệu hướng dẫn ứng dụng

Tài liệu này tổng hợp các thao tác thường dùng trong app đọc truyện. Trước khi cập nhật, nhập nguồn, sửa từ điển hoặc đổi cấu hình lớn, nên tạo bản sao lưu để tránh mất dữ liệu.

## Bắt đầu

App là công cụ đọc, quản lý nguồn, tải chương, dịch và nghe truyện. App không cung cấp sẵn nội dung có bản quyền; người dùng tự nhập nguồn sách, sách cục bộ hoặc kết nối WebDAV.

Các bước cơ bản:

1. Vào tab cá nhân hoặc cài đặt để nhập nguồn sách.
2. Tìm truyện trong màn khám phá hoặc tìm kiếm.
3. Thêm truyện vào Giá sách.
4. Mở truyện để đọc, tải chương, dịch hoặc nghe TTS.
5. Sao lưu định kỳ nếu dùng nhiều nguồn, từ điển hoặc cấu hình dịch.

## Nguồn sách

Nguồn sách quyết định app tìm kiếm, đọc mục lục và lấy nội dung chương như thế nào. Nếu một truyện lỗi, hãy thử đổi nguồn trước khi kết luận app lỗi.

Các thao tác thường dùng:

- Nhập nguồn từ file cục bộ.
- Nhập nguồn từ URL.
- Quét mã QR nguồn.
- Bật, tắt hoặc xóa nguồn.
- Chỉnh URL, header, rule tìm kiếm, rule thông tin sách, rule mục lục và rule nội dung.
- Dùng màn gỡ lỗi nguồn để xem HTTP, HTML và kết quả rule.

Khi nguồn đổi giao diện hoặc đổi tên miền, cần sửa lại URL/rule tương ứng.

## Giá sách

Giá sách hiển thị truyện đã thêm, nhóm đọc, tiến độ, số chương chưa đọc và trạng thái cập nhật.

Thao tác chính:

- Chạm vào bìa để đọc tiếp.
- Mở thông tin sách để xem mô tả, nguồn, chương và cấu hình riêng của truyện.
- Sắp xếp, đổi nhóm, xóa khỏi giá sách hoặc bật tắt cập nhật.
- Dùng menu chọn nhiều để xuất ebook hàng loạt khi tính năng export được bật.

Nếu nhãn nhóm còn hiển thị theo ngôn ngữ nguồn truyện, đó là dữ liệu động lấy từ nguồn. UI tĩnh của app sẽ theo ngôn ngữ app; thông tin động chỉ dịch khi bật dịch UI động.

## Đọc truyện

Màn đọc hỗ trợ nhiều chế độ hiển thị:

- Raw: nội dung gốc.
- HV: phiên âm Hán Việt theo từng từ/ký tự.
- QT: bản dịch bằng Quick Translator và từ điển.
- AI: bản dịch bằng AI Provider.
- NMT: bản dịch bằng model NMT.

Thanh công cụ phía trên có lối tắt đổi chế độ khi chế độ đó khả dụng. Nếu không thấy AI hoặc NMT, hãy kiểm tra provider dịch, cache bản dịch và cài đặt chế độ dịch.

## Dịch và từ điển

App có các provider dịch:

- Google Translate cho dịch nhanh qua dịch vụ ngoài.
- AI Provider dùng prompt, từ điển và cấu hình mô hình.
- Quick Translator dùng bộ từ điển, luật xử lý số, tên riêng, cụm từ và fallback phiên âm.
- NMT dùng model cục bộ khi đã import hoặc cấu hình.

Từ điển QT có các loại:

- Name: tên riêng.
- Vietphrase: cụm từ và thuật ngữ.
- Phiên âm: dùng cho ký tự lẻ khi không ghép được cụm.
- Pronoun: đại từ và xưng hô.
- Luật Nhân: luật xử lý ngữ cảnh đặc biệt.
- Ignore: cụm cần xóa trước khi dịch.

Phạm vi áp dụng theo thứ tự ưu tiên: Project, Universe, Toàn cục, rồi từ điển tích hợp.

## Tải chương

Màn tải chương hiển thị trạng thái chương chưa tải, đã tải, đang tải và lỗi. Có thể dừng, tiếp tục hoặc xóa tác vụ tải xuống. Nếu nguồn hay chặn IP, hãy tăng khoảng nghỉ và độ giao động trong cài đặt tải xuống.

## Xuất ebook

Khi chức năng export được bật, có thể xuất từ màn thông tin sách hoặc chọn nhiều ở Giá sách. Các định dạng dự kiến gồm EPUB2, EPUB3, PDF, TXT, HTML và CBZ. Có thể chọn phạm vi chương, thêm trang giới thiệu và dùng chung thiết lập cho nhiều truyện.

## Sao lưu

Nên sao lưu các nhóm dữ liệu sau:

- Giá sách và tiến độ đọc.
- Nguồn sách, nguồn RSS, rule thay thế.
- Cài đặt app và cài đặt dịch.
- Từ điển QT, prompt AI, model TTS/NMT đã import.

Có thể dùng sao lưu cục bộ hoặc WebDAV. Khi đổi máy, hãy khôi phục bản sao lưu trước, sau đó kiểm tra lại quyền lưu trữ và đường dẫn cache.

## Khi gặp lỗi

Nếu app crash hoặc chức năng không chạy:

1. Sao chép báo cáo lỗi đầy đủ.
2. Ghi lại màn hình đang thao tác.
3. Nêu rõ truyện, nguồn, provider dịch hoặc rule liên quan.
4. Nếu là lỗi nguồn, gửi thêm log gỡ lỗi nguồn.
5. Nếu là lỗi dịch, gửi provider, ngôn ngữ đích, prompt đang dùng và đoạn văn mẫu.

