# Tài liệu Hướng dẫn

[[toc]]

## Tính năng nâng cấp 2026

- [AI Router và cấu hình model](./ai-router.md)
- [AI Agent, tool và quyền xác nhận](./ai-agent.md)
- [Google ML Kit và model dịch ngoại tuyến](./mlkit-models.md)
- [Nhập extension VBook](./vbook-import.md)
- [Browser, đăng nhập và dịch trang](./browser.md)
- [Model TTS cục bộ](./local-tts-model.md)
- [Biên tập và xuất ebook](./ebook-editor.md)

## Dành cho người mới

### 1. Tại sao không có nội dung nào sau khi cài đặt lần đầu?

Ứng dụng này là một công cụ đọc tổng hợp nội dung, không trực tiếp cung cấp nội dung. Bạn cần nhập (import) các tệp sách cục bộ (local) hoặc cấu hình nguồn nội dung (content source) trước khi có thể sử dụng.

### 2. Phải làm sao khi nội dung chính bị thiếu chữ, mất đoạn hoặc lỗi dàn trang?

Nguyên nhân có thể do quy tắc thay thế/làm sạch (replace/purify rules) gây ra. Vui lòng tắt tính năng thay thế/làm sạch và làm mới (refresh) trang. Nếu nội dung trở lại bình thường, điều đó cho thấy quy tắc làm sạch đã bị cấu hình sai. Nếu sau khi tắt vẫn gặp sự cố, vui lòng nhấp vào liên kết nguồn để kiểm tra xem có giống với bản gốc hay không. Nếu không giống, hãy gửi phản hồi (feedback) cho chúng tôi.

## Liên quan đến Nguồn nhập (Import Sources)

### 1. Làm cách nào để nhập tệp nguồn cục bộ?

1. Mở ứng dụng này
2. Nhấp vào "**Của tôi**" —— "**Quản lý nguồn**"
3. Nhấp vào góc trên bên phải, chọn "**Nhập cục bộ**"
4. Chọn đường dẫn chứa tệp nguồn cần nhập
5. Nhấp vào tệp nguồn đó để hoàn tất việc nhập

::: tip Lưu ý
Tệp nguồn có phần mở rộng là `.txt` và `.json`. Trong một số trường hợp, tệp `.json` có thể không nhập được, bạn cần đổi phần mở rộng thành `.txt` để có thể nhập thành công.
:::

### 2. Làm cách nào để tạo nguồn mới?

1. Sao chép mã (code) của nguồn
2. Nhấp vào "**Của tôi**" —— "**Quản lý nguồn**"
3. Nhấp vào biểu tượng "**⁝**" ở góc trên bên phải —— "**+ Tạo nguồn mới**"
4. Sau khi vào màn hình mới, nhấp vào biểu tượng "**⁝**" ở góc trên bên phải —— "**Dán nguồn**"
5. Sau khi dán xong, nhấp vào nút lưu ở phía trên

::: tip Lưu ý
Nếu mã nguồn có lỗi hoặc sao chép không đầy đủ, hệ thống sẽ hiển thị lỗi định dạng. Vui lòng sao chép lại.
:::

### 3. Có phải nguồn báo "Hỏng" khi kiểm tra (verify) nghĩa là không thể sử dụng được nữa?

Việc kiểm tra (verify) chỉ mang tính chất tham khảo. Trạng thái báo hỏng (vô hiệu) không có nghĩa là nguồn đó hoàn toàn không thể sử dụng được.

## Liên quan đến Sách cục bộ / Sách từ xa WebDAV

### 1. Hiện tại hỗ trợ những định dạng sách cục bộ nào?

Hiện tại ứng dụng hỗ trợ định dạng TXT và EPUB.

### 2. Làm cách nào để nhập sách cục bộ / sách từ xa qua WebDAV?

* **Cục bộ (Local)**: Trên trang giá sách, nhấp vào biểu tượng "**⁝**" ở góc trên bên phải, chọn "**Thêm sách cục bộ**". Sau khi cấp các quyền liên quan, bạn có thể nhập sách. Bạn cũng có thể dùng ứng dụng này để mở sách trực tiếp từ Trình quản lý tệp (File Manager).
* **Từ xa (Remote)**: Trên trang chính, nhấp vào biểu tượng "**⁝**" ở góc trên bên phải, chọn "**Sách WebDAV**". Sau khi cấu hình chính xác, bạn sẽ thấy các cuốn sách từ xa đã được tải lên, chỉ cần nhấp vào nút "**Thêm vào giá sách**" để nhập.

### 3. Làm cách nào để tải sách cục bộ lên WebDAV từ xa?

Nhấn giữ vào một cuốn sách cục bộ để vào trang chi tiết sách, nhấp vào biểu tượng "**⁝**" ở góc trên bên phải, chọn "**Tải lên WebDAV**". Đợi vài giây, sách sẽ được tải lên máy chủ từ xa.

Hoặc bạn có thể vào trang bộ nhớ cache (lưu trữ ngoại tuyến) của sách, nhấp vào "**⁝**" ở góc trên bên phải, chọn "**Xuất sang WebDAV**". Khi xuất sách, nó sẽ đồng thời được tải lên máy chủ từ xa.

### 4. Tại sao khi nhập tệp TXT lại báo lỗi "LoadTocError" hoặc "List of empty"?

* Trước tiên, vui lòng vào phần chi tiết ứng dụng (trong cài đặt điện thoại) để xác nhận xem bạn đã cấp quyền "Đọc/Ghi bộ nhớ điện thoại" cho ứng dụng hay chưa.
* Hệ thống không thể tự động nhận diện mục lục, có thể do quy tắc mục lục (TOC rule) tương ứng chưa được bật. Vui lòng nhấp vào nút đổi nguồn ở góc trên bên phải để thay đổi quy tắc mục lục theo cách thủ công.

Nếu đã thử mọi quy tắc mà vẫn không thể nhận diện, vui lòng tạo một Issue trên GitHub và đính kèm tệp TXT liên quan.

### 5. Làm cách nào để tải sách về thiết bị (cục bộ)?

Sau khi thêm sách trực tuyến (online) vào giá sách, trên trang giá sách, hãy nhấp vào góc trên bên phải và chọn "**Lưu cache ngoại tuyến (Offline cache)**".

### 6. Làm cách nào để tùy chỉnh tên tệp TXT hoặc EPUB được xuất ra?

* Nhấp vào "**Lưu cache ngoại tuyến**" —— "**Tên tệp xuất**"
* Cách sử dụng:
* Tên tệp xuất hỗ trợ cú pháp JS (JavaScript)
* Các biến có sẵn: `name` (tên sách) và `author` (tác giả)
* Ví dụ: `name + " - " + author`



::: tip Lưu ý
Việc ghép các biến như `name`, `author` với chuỗi ký tự văn bản cần được thực hiện trong môi trường ngữ cảnh JSON, nghĩa là bạn phải sử dụng `{}` để bọc các biến và chuỗi lại.
:::

### 7. Phải làm sao nếu mở tệp TXT cục bộ hiển thị toàn ký tự rác (lỗi font)?

Có thể định dạng mã hóa (encoding) đã bị nhận diện sai. Bạn nên sử dụng các phần mềm chỉnh sửa văn bản để chuyển đổi tệp đó sang định dạng UTF-8 trước.

### 8. Phải làm sao nếu nội dung chính bị nhận diện nhầm thành tiêu đề?

Chỉ cần nhấp vào góc trên bên phải để thay đổi quy tắc mục lục là được.

## Liên quan đến Giao diện Sách

### 1. Làm cách nào để làm mới (refresh) giá sách?

Vuốt xuống trên giao diện giá sách để làm mới.

### 2. Các con số ở góc trên bên phải của cuốn sách trong giá sách có ý nghĩa gì?

Màu đỏ nghĩa là sách có bản cập nhật mới, màu xám là không có cập nhật, còn con số hiển thị số lượng chương chưa đọc.

### 3. Làm cách nào để xem chi tiết sách?

Nhấn giữ vào cuốn sách để xem.

### 4. Làm cách nào để xóa sách hoặc chuyển sách sang giá sách khác?

Bạn có thể thao tác những việc này ngay trong trang chi tiết của cuốn sách.

### 5. Làm cách nào để cho phép hoặc cấm một cuốn sách cập nhật?

Trong trang chi tiết sách, nhấp vào góc trên bên phải —— Chọn "**Cho phép cập nhật**".

### 6. Làm cách nào để thay đổi ảnh bìa, tên sách, tác giả hoặc phần giới thiệu?

Trong trang chi tiết sách, nhấp vào nút Chỉnh sửa ở góc trên bên phải.

### 7. Làm cách nào để sử dụng phông chữ tùy chỉnh?

Tại giao diện đọc —— Chọn "**Phông chữ**" —— Nhấp vào góc trên bên phải để chọn đường dẫn chứa tệp phông chữ trong máy.

### 8. Hiện tại hỗ trợ những định dạng tệp phông chữ nào?

Hiện tại ứng dụng hỗ trợ định dạng TTF và OTF.

### 9. Phải làm sao khi sách thường xuyên báo "Đang tải (Loading)"?

Đối với sách trực tuyến, nguyên nhân thường do chất lượng nguồn kém hoặc không tương thích, bạn có thể thử đổi sang nhiều nguồn khác nhau; Đối với sách cục bộ, nguyên nhân thường do lỗi quy tắc mục lục, việc thay đổi quy tắc theo cách thủ công có thể giải quyết được.

### 10. Nội dung sách chỉ có tiêu đề, còn phần nội dung lại hiển thị đường dẫn (path) thì phải làm sao?

Tình trạng này thường do đường dẫn lưu cache gây ra. Bạn chỉ cần thay đổi đường dẫn lưu cache là được.

### 11. Khi đọc sách gặp thông báo "Mục lục trống", "Tải thất bại" thì phải làm sao?

Đối với sách trực tuyến, thường là lỗi do nguồn, bạn chỉ cần chuyển đổi hoặc cập nhật nguồn. Đối với sách cục bộ, vui lòng thử đổi quy tắc mục lục thủ công.

### 12. Chữ và dòng kẻ nền ở trang cuối mỗi chương không thẳng hàng thì phải làm sao?

Vui lòng vào "**Cài đặt**" —— tùy chọn "**Căn chỉnh đáy văn bản**", tiến hành tắt tính năng căn đáy, sau đó điều chỉnh lại bố cục.

### 13. Chương dạng hình ảnh chỉ xem được trang đầu tiên thì phải làm sao?

Trước tiên, hãy kiểm tra xem trang web gốc có hiển thị bình thường không. Nếu bình thường, vui lòng nhấp vào biểu tượng "**⁝**" ở góc trên bên phải trong giao diện đọc, chọn "**Hiệu ứng lật trang (sách này)**" và đổi hiệu ứng lật trang thành "**Cuộn (Scroll)**".

### 14. Hình ảnh bị thu nhỏ lại cho vừa một trang thì phải làm sao?

* **Giải pháp tạm thời**: Nhấn giữ vào hình ảnh để dùng hai ngón tay phóng to/thu nhỏ. Đối với chương hình ảnh, trước tiên hãy đổi hiệu ứng lật trang thành "**Cuộn**".
* **Kiểu hình ảnh**: Trong giao diện đọc, nhấp vào "**⁝**" ở góc trên bên phải, chọn "**Kiểu hình ảnh (Image Style)**" —— Chọn `full`.

## Liên quan đến Thay thế/Làm sạch

### 1. Thay thế/Làm sạch là gì?

Tính năng Thay thế/Làm sạch giúp loại bỏ quảng cáo, lỗi chính tả, từ ngữ bị cấm, v.v. xuất hiện trong nội dung sách.

### 2. Cách điền quy tắc thay thế/làm sạch như thế nào?

1. **Dòng 1**: Tên quy tắc thay thế
2. **Dòng 2**: Phân nhóm
3. **Dòng 3**: Quy tắc thay thế, điền nội dung cần được thay thế
4. **Dòng 4**: Thay thế bằng, điền nội dung bạn muốn thay thế thành (để trống nghĩa là xóa đi)
5. **Dòng 5**: Phạm vi thay thế, có thể điền tên sách hoặc tên nguồn (để trống thì quy tắc sẽ áp dụng cho tất cả sách và nguồn)

::: tip Lưu ý
Nếu các phương pháp loại bỏ thông thường không có tác dụng, bạn cần đánh dấu vào ô "Sử dụng Biểu thức chính quy (Regex)". Đồng thời, quy tắc thay thế ở dòng 3 cũng phải được viết theo cú pháp Biểu thức chính quy.
:::

## Liên quan đến Sao lưu

### 1. Tính năng Sao lưu đám mây nằm ở đâu?

"**Của tôi**" —— "**Sao lưu & Khôi phục**" —— "**Cài đặt WebDAV**".

### 2. Làm cách nào để thực hiện sao lưu đám mây?

1. Mở Cài đặt ở thanh menu bên hông, chọn Cài đặt WebDAV.
2. Điền chính xác địa chỉ máy chủ WebDAV, tên tài khoản và mật khẩu.
3. Không cần thao tác gì thêm, theo mặc định, ứng dụng sẽ tự động sao lưu lên đám mây mỗi ngày một lần.

### 3. Giải thích về tính năng Sao lưu đám mây

Khi đã thiết lập cấu hình sao lưu đám mây chính xác, ứng dụng mặc định tự động sao lưu mỗi ngày một lần. Việc thực hiện sao lưu thủ công nhiều lần trong cùng một ngày sẽ ghi đè lên tệp sao lưu cũ của ngày hôm đó, nhưng sẽ không ghi đè lên các tệp sao lưu của những ngày khác (trước hoặc sau đó). Các tệp sao lưu tự động hàng ngày sẽ được đặt tên theo ngày tháng năm.

### 4. Sao lưu cục bộ và sao lưu đám mây lưu trữ được những nội dung gì?

Giá sách, tiến độ đọc, lịch sử tìm kiếm, nguồn đã nhập, quy tắc thay thế và các cài đặt của ứng dụng đều sẽ được sao lưu, về cơ bản bao gồm toàn bộ mọi thứ trong app.

### 5. Phải làm sao khi gặp lỗi không xác định?

Hãy thử xóa dữ liệu ứng dụng. Nếu vẫn không được, vui lòng gửi phản hồi cho chúng tôi.

## Khác

### 1. Làm cách nào để nghe sách (TTS/Audiobook)?

Bạn có thể sử dụng engine đọc văn bản (TTS) mặc định có sẵn của điện thoại, hoặc sử dụng các engine của bên thứ ba như Google hay Xiaomi.

Thao tác cụ thể: Cài đặt engine → Cài đặt hệ thống → Cài đặt nâng cao khác → Hỗ trợ tiếp cận (Accessibility) → Đầu ra TTS → Chọn engine đọc mà bạn đã cài đặt.

### 2. Làm cách nào để cài đặt hướng màn hình, thời gian sáng màn hình, v.v.?

Tại giao diện đọc —— Chọn "**Cài đặt**" (có thể vuốt lên, bên dưới còn có các cài đặt khác).

### 3. Cảm thấy điện thoại bị giật/lag khi tìm kiếm thì phải làm sao?

Vào "**Của tôi**" —— "**Cài đặt khác**" —— Giảm mức "**Số luồng cập nhật và tìm kiếm (Update and Search threads)**" xuống.
