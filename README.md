# Legado with MD3

## 📖 Giới thiệu

**Legado with MD3** là phiên bản được cấu trúc lại (refactor) theo phong cách Material Design 3, dựa trên dự án mã nguồn mở [阅读 (Legado)](https://github.com/gedoor/legado).

Dự án này không chỉ vẽ lại toàn bộ giao diện người dùng (UI) mà còn bổ sung nhiều tính năng độc quyền của nhánh này. Đồng thời, dự án đang trong quá trình chuyển đổi dần từ framework View truyền thống sang Jetpack Compose, với mục tiêu mang đến một trải nghiệm đọc hiện đại, mượt mà và đồng nhất hơn.

> [!CAUTION]
> **Lưu ý:**
> Do hệ thống chủ đề (theme) đã được cấu trúc lại bằng engine Monet, các chủ đề của phiên bản chính thức sẽ không còn sử dụng được trong phiên bản này.
> **Đối với thiết bị chạy dưới Android 12:**
> Tạm thời không thể sử dụng tính năng chủ đề tùy chỉnh và trích xuất màu động (dynamic color). Hạn chế này sẽ được khắc phục sau khi hoàn tất việc chuyển đổi sang Jetpack Compose (do thời gian và công sức của nhà phát triển có hạn, quá trình chuyển đổi này sẽ kéo dài một khoảng thời gian khá lâu).

---

## ✨ Tính năng của nhánh

So với phiên bản chính thức, dự án này sở hữu các tính năng độc quyền sau:

* **Chủ đề hoàn toàn mới:** Giao diện thiết kế Material Design 3 mới mẻ, hỗ trợ **cử chỉ quay lại dự đoán (predictive back gesture)** và **hiệu ứng chuyển cảnh phần tử dùng chung (shared element transitions)**.
* **Giao diện đọc:** Giao diện đọc và cấu hình menu được cá nhân hóa cao hơn.
* **Lịch sử đọc:** Cung cấp lịch sử đọc chi tiết, hỗ trợ thống kê theo **dòng thời gian (timeline)** và theo **chương (chapter)**.
* **Nâng cao trải nghiệm:** Trải nghiệm giao diện hoàn thiện hơn cho các tính năng **Đọc truyện tranh**, **Sách nói** và **Khám phá**.
* **Bố cục giá sách:** Bổ sung nhiều tùy chọn bố cục giá sách, với giao diện được tối ưu hóa đặc biệt cho **máy tính bảng (tablet)**.
* **Tính năng tiện ích:** Thêm tính năng ghi chú sách, nhóm đồng hành thông minh (tự động phân loại đã đọc/chưa đọc) và hỗ trợ **lật trang** lên/xuống bằng **tay cầm (gamepad)**.

---

## 🛠️ Tính năng cốt lõi

1. **Hỗ trợ đa định dạng:** Hỗ trợ đọc tệp TXT, EPUB cục bộ, quét thông minh các tệp trên máy.
2. **Tùy chỉnh linh hoạt:** Thay đổi phông chữ, hình nền, khoảng cách dòng, khoảng cách đoạn, in đậm, chuyển đổi giữa chữ Hán giản thể/phồn thể, v.v.
3. **Lọc và thay thế:** Loại bỏ quảng cáo mạnh mẽ, cho phép thay thế nội dung trong văn bản.
4. **Chế độ lật trang:** Tùy ý chuyển đổi giữa nhiều chế độ như trượt đè (cover), mô phỏng lật sách (curl), trượt ngang (slide), cuộn dọc (scroll), v.v.
5. **Mã nguồn mở hoàn toàn:** Không có quảng cáo, liên tục được cập nhật và tối ưu hóa.

---

## 💬 Giao lưu

* **Nhóm Telegram:** [Legado with MD3](https://t.me/materado)
* **Phát hành phiên bản:**
Phiên bản mới nhất sẽ tự động được gửi vào nhóm, hoặc bạn có thể tải xuống tại [GitHub Releases](https://github.com/HapeLee/legado-with-MD3/releases).

---

## ❤️ Lời cảm ơn

Xin cảm ơn các dự án mã nguồn mở xuất sắc sau đây đã mang lại nguồn cảm hứng và hỗ trợ kỹ thuật:

* [gedoor/legado](https://github.com/gedoor/legado) (Người "cha đẻ" tuyệt vời nhất của dự án này)
* [Luoyacheng/legado](https://github.com/Luoyacheng/legado) (Cung cấp thêm nhiều tính năng mở rộng)
* [komikku-app/komikku](https://github.com/komikku-app/komikku) (Truyền cảm hứng về giao diện và cung cấp một số widget Compose xuất sắc)
* [FoedusProgramme/Gramophone](https://github.com/FoedusProgramme/Gramophone) (Truyền cảm hứng về giao diện và phương pháp trích xuất màu từ ảnh bìa cho hệ thống View)
* [jordond/MaterialKolor](https://github.com/jordond/MaterialKolor) (Giải pháp trích xuất màu xuất sắc dựa trên Jetpack Compose)
* [Calvin-LL/Reorderable](https://github.com/Calvin-LL/Reorderable) (Giải pháp kéo thả sắp xếp xuất sắc dựa trên Jetpack Compose)
* Cùng nhiều dự án mã nguồn mở khác...

## ⚠️ Thỏa thuận người dùng và Tuyên bố miễn trừ trách nhiệm

> **【Lưu ý đặc biệt】**
> Trước khi tải xuống, cài đặt hoặc sử dụng phần mềm này, vui lòng đọc kỹ và hiểu rõ toàn bộ nội dung của thỏa thuận và tuyên bố miễn trừ trách nhiệm này. Việc bạn tải xuống, cài đặt hoặc sử dụng phần mềm đồng nghĩa với việc bạn đã đọc, hiểu và đồng ý chấp nhận mọi nội dung của tuyên bố này.

### I. Giải thích về tính chất của phần mềm

1. Phần mềm này là một công cụ duyệt nội dung trang web cục bộ có thể định cấu hình bởi người dùng, cung cấp các chức năng kỹ thuật như truy cập trang web, phân tích cú pháp nội dung (parsing), trích xuất văn bản, dàn trang đọc và quản lý dữ liệu.
2. Ở trạng thái mặc định, phần mềm này không cài đặt sẵn, không tích hợp sẵn và không cung cấp bất kỳ nội dung trang web, tài nguyên dữ liệu hay quy tắc phân tích (rule) nào của bên thứ ba.
3. Nhà phát triển phần mềm này không cung cấp bất kỳ dịch vụ vận hành, lưu trữ, xuất bản hay phát tán nội dung nào.
4. Dựa trên nhu cầu cá nhân, người dùng có thể tự cấu hình hoặc nhập (import) các quy tắc từ bên thứ ba để duyệt và xử lý cá nhân hóa các nội dung trang web công khai.

### II. Quy định về hành vi của người dùng và quy tắc

1. Người dùng có thể tự tạo, chỉnh sửa, nhập hoặc sử dụng các quy tắc phân tích do bên thứ ba chia sẻ (sau đây gọi tắt là "quy tắc").
2. Các quy tắc liên quan chỉ được sử dụng để xác định phương thức thu thập, trích xuất và hiển thị nội dung trang web. Người dùng phải tự đánh giá và chịu trách nhiệm về nguồn gốc, tính hợp pháp, tính chính xác và khả năng áp dụng của chúng.
3. Khi người dùng sử dụng quy tắc để truy cập các trang web của bên thứ ba, các yêu cầu mạng liên quan sẽ được gửi trực tiếp từ thiết bị của người dùng đến trang web đích và nhận dữ liệu. Phần mềm này chỉ cung cấp khả năng phân tích và hiển thị cục bộ, không sửa đổi, chỉnh sửa hay phân phối lại nội dung của trang web bên thứ ba.
4. Người dùng phải tuân thủ luật pháp và quy định của nước sở tại, các yêu cầu về an ninh mạng cũng như thỏa thuận dịch vụ và quy định bản quyền của các trang web liên quan. Nghiêm cấm sử dụng phần mềm này để thực hiện các hành vi vi phạm sở hữu trí tuệ, phát tán trái phép, thu thập dữ liệu trái phép, phá hoại dịch vụ mạng hoặc các hành vi vi phạm pháp luật khác.

### III. Tuyên bố về nội dung và cộng đồng bên thứ ba

1. Mọi nền tảng chia sẻ quy tắc, diễn đàn, nhóm giao lưu, trang web hoặc cộng đồng khác do bên thứ ba thành lập hoặc duy trì đều là các nền tảng hoạt động độc lập và không có quan hệ trực thuộc với nhà phát triển phần mềm này.
2. Nhà phát triển không tham gia vào các hành vi tạo, xuất bản, vận hành, bảo trì và phát tán quy tắc, nội dung hoặc cộng đồng của bên thứ ba, đồng thời không có nghĩa vụ chủ động kiểm duyệt các nội dung đó.
3. Rủi ro phát sinh do người dùng sử dụng các quy tắc hoặc truy cập các trang web của bên thứ ba, bao gồm nhưng không giới hạn ở tranh chấp bản quyền, rủi ro bảo mật dữ liệu, rủi ro truy cập mạng hoặc các rủi ro pháp lý khác, sẽ do đối tượng thực hiện hành vi chịu trách nhiệm tương ứng trước pháp luật.

### IV. Chính sách quyền riêng tư và dữ liệu

1. Các chức năng chính của phần mềm này hoạt động trên thiết bị cục bộ của người dùng. Phần mềm không thiết lập máy chủ nội dung riêng để cung cấp dịch vụ nội dung trang web.
2. Phần mềm này không chủ động thu thập, tải lên hay lưu trữ nội dung đọc, danh sách quy tắc, lịch sử duyệt web hoặc bất kỳ dữ liệu cá nhân riêng tư nào khác của người dùng.
3. Bản dựng hiện tại không tích hợp SDK thống kê, phân tích sự cố hoặc hiệu năng Firebase. Dịch vụ cloud chỉ hoạt động sau khi người dùng chủ động đăng nhập hoặc bật chức năng sao lưu, đồng bộ tương ứng.
4. Một số quyền mạng, bộ nhớ lưu trữ hoặc quyền đồng bộ hóa chỉ được sử dụng để thực hiện các chức năng như sao lưu cục bộ, đồng bộ hóa WebDAV hoặc đồng bộ hóa dữ liệu giữa các thiết bị do người dùng chủ động kích hoạt.

### V. Bảo vệ Sở hữu Trí tuệ

1. Nhà phát triển tôn trọng và bảo vệ quyền và lợi ích hợp pháp của chủ sở hữu quyền sở hữu trí tuệ, đồng thời phản đối mọi hành vi vi phạm bản quyền, quyền thương hiệu hoặc các quyền hợp pháp khác.
2. Người dùng phải đảm bảo rằng hành vi sử dụng phần mềm này để thu thập, xử lý hoặc truy cập các nội dung liên quan đều tuân thủ các luật, quy định hiện hành và các yêu cầu về quyền lợi.
3. Nếu chủ sở hữu quyền cho rằng một số quy tắc của bên thứ ba có nghi ngờ vi phạm, họ có thể sử dụng các biện pháp pháp lý để yêu cầu bảo vệ quyền lợi từ bên lưu trữ thực tế của nội dung đó.
4. Chủ sở hữu quyền cũng có thể gửi thông báo hợp lệ cho nhà phát triển, bao gồm chứng minh danh tính, bằng chứng sở hữu, thông tin quy tắc cụ thể và các giải trình liên quan. Trong khả năng kỹ thuật hợp lý, nhà phát triển sẽ thực hiện các biện pháp xử lý cần thiết đối với các quy tắc bị nghi ngờ vi phạm.
