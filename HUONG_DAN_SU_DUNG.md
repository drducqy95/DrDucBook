# DrDucBook – Cẩm Nang Hướng Dẫn Sử Dụng Toàn Diện

> **Phiên bản tài liệu:** 2.0 (Cập nhật kiến trúc Material Design 3, AI Router, Agent Wiki & Room DB v105)  
> **Ứng dụng:** DrDucBook (Legado Material Design 3 Fork)  
> **Mô hình hoạt động:** Local-First, bảo mật dữ liệu, tích hợp AI & NMT ngoại tuyến.

---

## MỤC LỤC TỔNG QUAN

1. [QUY TRÌNH & THỨ TỰ CÀI ĐẶT CHUẨN (TỪ A ĐẾN Z)](#1-quy-trình--thứ-tự-cài-đặt-chuẩn-từ-a-đến-z)
   - [Bước 1: Cài đặt file APK tương thích thiết bị](#bước-1-cài-đặt-file-apk-tương-thích-thiết-bị)
   - [Bước 2: Cấp quyền hệ thống và chống tắt ứng dụng ngầm](#bước-2-cấp-quyền-hệ-thống-và-chống-tắt-ứng-dụng-ngầm)
   - [Bước 3: Đăng nhập tài khoản & Thiết lập đồng bộ đám mây](#bước-3-đăng-nhập-tài-khoản--thiết-lập-đồng-bộ-đám-mây)
   - [Bước 4: Nạp nguồn truyện (BookSource & VBook Extension)](#bước-4-nạp-nguồn-truyện-booksource--vbook-extension)
   - [Bước 5: Cấu hình bộ máy Dịch thuật (QT, NMT, AI)](#bước-5-cấu-hình-bộ-máy-dịch-thuật-qt-nmt-ai)
   - [Bước 6: Thiết lập giọng đọc TTS (ZeroTTS ONNX / HTTP TTS)](#bước-6-thiết-lập-giọng-đọc-tts-zerotts-onnx--http-tts)
   - [Bước 7: Thiết lập thư mục lưu trữ & Sao lưu tự động](#bước-7-thiết-lập-thư-mục-lưu-trữ--sao-lưu-tự-động)
2. [HƯỚNG DẪN CHI TIẾT TỪNG TRANG & CHỨC NĂNG CHÍNH](#2-hướng-dẫn-chi-tiết-từng-trang--chức-năng-chính)
   - [1. Trang Chủ (Home)](#1-trang-chủ-home)
   - [2. Tủ Sách (Bookshelf)](#2-tủ-sách-bookshelf)
   - [3. Khám Phá (Explore / Discovery)](#3-khám-phá-explore--discovery)
   - [4. Tải Xuống (Downloads & Bộ nhớ đệm)](#4-tải-xuống-downloads--bộ-nhớ-đệm)
   - [5. Không Gian Làm Việc (Workspace)](#5-không-gian-làm-việc-workspace)
     - [Trình duyệt bảo mật tích hợp (Browser)](#trình-duyệt-bảo-mật-tích-hợp-browser)
     - [Trợ lý AI Chat & Bóng nổi (Chat Bubble)](#trợ-lý-ai-chat--bóng-nổi-chat-bubble)
     - [Agent Dashboard & Quản lý quyền rủi ro](#agent-dashboard--quản-lý-quyền-rủi-ro)
     - [Story Wiki, Niên Biểu (Chronicle) & Tạo ảnh minh họa AI](#story-wiki-niên-biểu-chronicle--tạo-ảnh-minh-họa-ai)
     - [Không gian sáng tác (Writing) & Biên tập Ebook (Ebook Editor)](#không-gian-sáng-tác-writing--biên-tập-ebook-ebook-editor)
     - [Đọc tin tức RSS](#đọc-tin-tức-rss)
     - [Media Player & Audiobook](#media-player--audiobook)
   - [6. Cá Nhân & Cài Đặt Hệ Thống (My / Settings)](#6-cá-nhân--cài-đặt-hệ-thống-my--settings)
3. [HƯỚNG DẪN CHUYÊN SÂU MÀN HÌNH ĐỌC SÁCH (READER)](#3-hướng-dẫn-chuyên-sâu-màn-hình-đọc-sách-reader)
   - [Bố cục điều hướng & Cử chỉ thông minh](#bố-cục-điều-hướng--cử-chỉ-thông-minh)
   - [Tính năng dịch tức thời & Sửa đổi bản dịch (Revision)](#tính-năng-dịch-tức-thời--sửa-đổi-bản-dịch-revision)
   - [Tra cứu từ điển tức thời (Quick Dictionary)](#tra-cứu-từ-điển-tức-thời-quick-dictionary)
   - [Đọc thành tiếng (Read Aloud TTS) đồng bộ theo dòng](#đọc-thành-tiếng-read-aloud-tts-đồng-bộ-theo-dòng)
   - [Tóm tắt chương bằng AI & Lọc làm sạch văn bản](#tóm-tắt-chương-bằng-ai--lọc-làm-sạch-văn-bản)
4. [BẢNG XỬ LÝ SỰ CỐ NHANH (TROUBLESHOOTING)](#4-bảng-xử-lý-sự-cố-nhanh-troubleshooting)

---

## 1. QUY TRÌNH & THỨ TỰ CÀI ĐẶT CHUẨN (TỪ A ĐẾN Z)

Để ứng dụng vận hành mượt mà, không bị xung đột bộ nhớ và tận dụng tối đa các tính năng AI/NMT/TTS ngoại tuyến, người dùng nên thực hiện cài đặt theo đúng **7 bước tuần tự** dưới đây:

```
[Bước 1: Cài APK đúng ABI]
          ↓
[Bước 2: Cấp quyền hệ thống & Tắt tối ưu hóa pin]
          ↓
[Bước 3: Đăng nhập Supabase Cloud / Google Drive]
          ↓
[Bước 4: Nạp Nguồn truyện (Legado / VBook Hub)]
          ↓
[Bước 5: Cấu hình Bộ máy Dịch thuật (QT / NMT / AI Router)]
          ↓
[Bước 6: Tải Model TTS (ZeroTTS ONNX / Voice Addons)]
          ↓
[Bước 7: Thiết lập Thư mục Sao lưu SAF & Lịch tự động]
```

### Bước 1: Cài đặt file APK tương thích thiết bị
1. **Kiểm tra kiến trúc vi xử lý (ABI) của điện thoại/máy tính bảng:**
   - Đa số thiết bị Android hiện đại (từ Android 10 trở lên) sử dụng chip 64-bit: Chọn file `*-arm64-v8a-release.apk`.
   - Thiết bị cũ hơn hoặc chạy Android 32-bit: Chọn file `*-armeabi-v7a-release.apk`.
   - Nếu không chắc chắn: Chọn file `*-universal-release.apk`.
2. **Cài đặt APK:** Nhấn vào file APK đã tải về, cho phép "Cài đặt ứng dụng từ nguồn không xác định" khi được trình duyệt hoặc trình quản lý tệp yêu cầu.

### Bước 2: Cấp quyền hệ thống và chống tắt ứng dụng ngầm
Vì DrDucBook thực thi các tác vụ tính toán nặng (dịch thuật NMT, suy luận âm thanh TTS ONNX, đồng bộ đám mây và tải media nền), hệ điều hành Android có thể đóng tiến trình nếu không được cấp quyền đúng:
1. **Thông báo (Notification):** Cấp quyền khi ứng dụng yêu cầu lần đầu để theo dõi tiến độ tải chương, thanh điều khiển phát TTS và trạng thái đồng bộ.
2. **Tắt Tối ưu hóa pin (Disable Battery Optimization):**
   - Vào cài đặt hệ thống của điện thoại: *Cài đặt → Ứng dụng → DrDucBook → Pin / Tiết kiệm pin → Chọn "Không hạn chế" (Unrestricted / No restrictions)*.
   - Trên các máy Xiaomi/HyperOS/MIUI: Bật thêm quyền "Tự khởi chạy" (Autostart).
   - Trên máy Huawei/Honor: Vào *Khởi chạy ứng dụng* → Tắt quản lý tự động → Bật cả 3 mục: Tự khởi chạy, Khởi chạy phụ và Chạy nền.
3. **Quyền truy cập bộ nhớ qua Storage Access Framework (SAF):**
   - Ứng dụng sử dụng chuẩn bảo mật Android hiện đại (không yêu cầu quyền đọc toàn bộ thẻ nhớ MANAGE_EXTERNAL_STORAGE bừa bãi). Khi chọn thư mục sao lưu hoặc nhập sách, hãy tạo một thư mục riêng (ví dụ: `DrDucBook_Data`) và bấm **"Dùng thư mục này" (Use this folder)**.

### Bước 3: Đăng nhập tài khoản & Thiết lập đồng bộ đám mây
1. Mở ứng dụng, vào tab **Cá nhân (My)** → Chọn **Tài khoản (Account)**.
2. Đăng nhập bằng tài khoản Supabase (Email/Mật khẩu hoặc Đăng nhập qua Google OAuth).
3. Sau khi đăng nhập, hệ thống sẽ tự động liên kết trạng thái tài khoản (Free / Premium / Admin) và nạp danh mục cấu hình đám mây.
4. *(Tùy chọn nâng cao)*: Bật liên kết Google Drive để hỗ trợ sao lưu toàn bộ tập tin nhị phân (Full ZIP backup bao gồm cả sách đệm và media).

### Bước 4: Nạp nguồn truyện (BookSource & VBook Extension)
Mặc định ứng dụng không lưu trữ sẵn nội dung truyện. Bạn cần nạp các nguồn truyện cộng đồng:
1. **Nhập Nguồn Legado thông thường:**
   - Vào **Cá nhân → Nguồn sách (Book Source Manage)**.
   - Nhấn menu dấu 3 chấm góc phải trên → Chọn **Nhập nguồn từ URL** (dán đường dẫn file raw JSON) hoặc **Nhập từ tệp cục bộ**.
2. **Nhập Extension VBook (chuẩn JavaScript hiện đại):**
   - Vào menu nguồn → Chọn **Nhập Extension VBook**.
   - Dán URL danh mục (VBook Registry) hoặc nạp file `.zip`/`.js` của extension VBook.
3. **Kiểm tra tình trạng nguồn (Source Health):**
   - Chạy tính năng **Kiểm tra nguồn** để tự động kiểm tra xem nguồn nào còn hoạt động tốt về tìm kiếm, mục lục và phân tích nội dung chương.

### Bước 5: Cấu hình bộ máy Dịch thuật (QT, NMT, AI)
Vào **Cá nhân → Cài đặt Dịch (Translation Settings)** để chuẩn bị công cụ đọc truyện chữ nước ngoài (Trung/Hàn/Anh):
1. **Chế độ Dịch Nhanh (Quick Translation - QT2025):** Đã tích hợp sẵn bộ từ điển Hán Việt offline cực nhanh, không tốn tài nguyên máy, dịch ngay lập tức khi mở chương.
2. **Chế độ NMT Offline (Hachimi-QT ONNX):**
   - Vào mục **Quản lý Model NMT**, tải gói model ONNX qua tính năng phân phối tài sản tự động (Asset Delivery).
   - Bộ máy này dịch theo ngữ pháp câu tự nhiên bằng mô hình trí tuệ nhân tạo chạy hoàn toàn trên chip của thiết bị mà không cần Internet.
3. **Chế độ AI Router (Cloud LLM):**
   - Nếu muốn dịch bằng các mô hình AI cao cấp (Gemini, Claude, GPT, DeepSeek): Vào **Cá nhân → AI Router → Thêm Provider**.
   - Dán API Key hoặc sử dụng luồng đăng nhập Antigravity / Codex OAuth được tích hợp sẵn.

### Bước 6: Thiết lập giọng đọc TTS (ZeroTTS ONNX / HTTP TTS)
1. **TTS Cục bộ thông minh (ZeroTTS ONNX):**
   - Vào **Cá nhân → Cài đặt Đọc → Quản lý Model TTS**.
   - Nhấn tải **ZeroTTS Base INT8** và tải thêm các **Gói giọng đọc tiếng Việt (Voice Addons)** theo sở thích.
   - Động cơ này tự ngắt câu thông minh dưới 120 ký tự, chống tràn bộ nhớ và đọc diễn cảm tự nhiên mà không cần mạng.
2. **HTTP TTS / TTS Hệ thống:**
   - Bạn cũng có thể dùng TTS mặc định của máy (Google TTS) hoặc nhập link HTTP TTS (Azure/Edge TTS) trong mục cấu hình âm thanh.

### Bước 7: Thiết lập thư mục lưu trữ & Sao lưu tự động
1. Vào **Cá nhân → Sao lưu và Khôi phục (Backup & Restore)**.
2. Bấm **Chọn thư mục sao lưu cục bộ**, cấp quyền cho một thư mục an toàn trên máy.
3. Đặt **Mật khẩu sao lưu** (tối thiểu 8 ký tự) để mã hóa toàn bộ dữ liệu cá nhân.
4. Bật **Tự động sao lưu định kỳ** (mỗi ngày hoặc mỗi tuần).

---

## 2. HƯỚNG DẪN CHI TIẾT TỪNG TRANG & CHỨC NĂNG CHÍNH

Giao diện của DrDucBook tuân theo tiêu chuẩn **Material Design 3 (Expressive)** với thanh điều hướng chính gồm 6 phân khu:

---

### 1. Trang Chủ (Home)
Trang chủ là trung tâm thông tin tổng quan, giúp bạn nhanh chóng quay lại mạch đọc:
- **Tiếp tục đọc (Continue Reading):** Hiển thị thẻ sách lớn của cuốn truyện bạn đang đọc dở gần nhất kèm theo ảnh bìa, tên chương hiện tại, phần trăm tiến độ và nút "Đọc tiếp" một chạm.
- **Lối tắt thao tác nhanh (Quick Actions):**
  - *Nhập sách nhanh:* Mở bộ duyệt tệp để thêm sách EPUB/TXT.
  - *Bộ nhớ dịch (Story Memory):* Mở nhanh bảng thuật ngữ của cuốn sách đang đọc.
  - *Quản lý tải xuống:* Kiểm tra các chương hoặc media đang tải dở.
  - *Dịch vụ Web (WebService):* Bật nhanh máy chủ nội bộ để đọc trên máy tính.
- **Khối thống kê đọc sách:** Biểu đồ hiển thị thời gian đọc sách trong ngày, số chương đã hoàn thành và số từ đã đọc.

---

### 2. Tủ Sách (Bookshelf)
Nơi lưu trữ, quản lý và tổ chức toàn bộ thư viện sách của bạn:
- **Phân loại nhóm sách (Book Groups):**
  - Thanh tab trên cùng cho phép chuyển nhanh giữa các nhóm: *Tất cả, Đang đọc, Hoàn thành, Chưa phân loại* hoặc các nhóm do bạn tự tạo.
  - Hỗ trợ **Nhóm Riêng Tư (Private Group):** Các cuốn sách được đưa vào nhóm riêng tư sẽ được ẩn đi khi chia sẻ hoặc khóa bằng mật khẩu/sinh trắc học.
- **Chế độ hiển thị & Sắp xếp:**
  - Chuyển đổi linh hoạt giữa dạng **Lưới ảnh bìa (Grid View)** trực quan và dạng **Danh sách chi tiết (List View)**.
  - Tùy chọn sắp xếp: Theo thời gian đọc gần nhất, thời gian cập nhật chương mới, tên sách A-Z hoặc thứ tự kéo thả thủ công.
- **Thao tác quản lý hàng loạt (Batch Management):**
  - Nhấn giữ vào một cuốn sách để kích hoạt chế độ chọn nhiều cuốn.
  - Các thao tác: *Chuyển nhóm hàng loạt, Xóa sách, Làm mới chương mới (Refresh TOC), Tải trước nội dung đệm, Đổi nguồn sách hàng loạt*.
- **Làm mới tủ sách (Smart Refresh):**
  - Vuốt kéo xuống từ đầu danh sách để làm mới các nguồn mạng. Ứng dụng sẽ tự động truy vấn các nguồn truyện đang bật và hiển thị huy hiệu thông báo số chương mới được cập nhật.

---

### 3. Khám Phá (Explore / Discovery)
Trang tìm kiếm và duyệt nội dung từ hàng ngàn nguồn truyện mạng:
- **Tìm kiếm đa năng thông minh (Universal Search):**
  - Thanh tìm kiếm hỗ trợ nhập: Tên sách, Tên tác giả hoặc Từ khóa nội dung.
  - **Phạm vi tìm kiếm (Search Scope):**
    - *Toàn bộ nguồn đang bật:* Quét song song trên tất cả nguồn.
    - *Nguồn hiện tại:* Chỉ tìm trên một nguồn bạn chọn.
    - *Trong tủ sách:* Tìm kiếm các cuốn sách đã lưu.
- **Duyệt cây danh mục nguồn (Explore Directory):**
  - Hiển thị danh sách các nguồn truyện được chia theo nhóm (Tiên hiệp, Ngôn tình, Đô thị, Light Novel, Manga, Audio...).
  - Bấm vào từng nguồn để xem danh mục đề xuất, bảng xếp hạng (Top ngày, Top tháng, Đề cử) do trang gốc cung cấp.
- **Tình trạng nguồn (Source Health):**
  - Biểu tượng kiểm tra sức khỏe nguồn giúp bạn biết ngay nguồn truyện nào đang bị chết link, bị chặn IP hoặc thay đổi cấu trúc web.

---

### 4. Tải Xuống (Downloads & Bộ nhớ đệm)
Trung tâm quản lý các tiến trình tải nền và dung lượng lưu trữ:
- **Hàng đợi tải sách chữ (Book Downloads):**
  - Theo dõi danh sách các cuốn sách đang được tải chương về đọc offline.
  - Hỗ trợ tạm dừng, tiếp tục, tải lại các chương lỗi hoặc hủy tác vụ.
- **Hàng đợi tải Media (Audiobook / Video Downloads):**
  - Hiển thị tiến độ tải các tập audiobook hoặc các tệp video từ nguồn media.
  - Tự động ghép nối các phần âm thanh và lưu trữ an toàn trong thư mục quản lý riêng.
- **Quản lý bộ nhớ đệm sách (Cache Management):**
  - Thống kê chi tiết dung lượng bị chiếm dụng: *Bộ nhớ đệm văn bản, Bộ nhớ đệm ảnh bìa, Bộ nhớ đệm truyện tranh (Manga Cache)*.
  - Cho phép chọn dọn dẹp từng cuốn sách hoặc xóa toàn bộ đệm của các cuốn sách đã lâu không đọc để giải phóng dung lượng bộ nhớ trong.

---

### 5. Không Gian Làm Việc (Workspace)
Phân khu tích hợp những tính năng cao cấp dành cho dịch thuật, trợ lý AI, đa phương tiện và biên tập:

#### Trình duyệt bảo mật tích hợp (Browser)
- Mở trực tiếp các trang web nguồn để:
  - Vượt qua tường lửa bảo vệ bot (Cloudflare Turnstile / reCAPTCHA).
  - Đăng nhập tài khoản của trang nguồn để lấy Cookie đọc các chương VIP/thành viên.
  - Tự động đồng bộ Cookie và Header sang động cơ lấy dữ liệu của ứng dụng.

#### Trợ lý AI Chat & Bóng nổi (Chat Bubble)
- **AI Chat Room:** Cung cấp không gian trò chuyện chuyên sâu với các mô hình ngôn ngữ lớn (LLM). Bạn có thể yêu cầu giải thích điển tích, phân tích tính cách nhân vật, thảo luận cốt truyện hoặc dịch thử nghiệm các câu văn khó.
- **Bóng nổi trợ lý (Chat Bubble):** Khi được bật trong cài đặt, một biểu tượng bong bóng nhỏ sẽ xuất hiện trên màn hình, cho phép bạn gọi nhanh AI bất kỳ lúc nào ngay trong khi đang đọc sách mà không cần thoát ra ngoài.

#### Agent Dashboard & Quản lý quyền rủi ro
- **Giám sát tác vụ Agent:** Xem toàn bộ lịch sử các tác vụ tự động do Trợ lý Agent thực hiện (như tự quét và sửa lỗi nguồn sách, tự tổng hợp biên niên sử).
- **Hệ thống phân quyền 3 cấp độ (Agent Risk Permissions):**
  - *An toàn (Safe):* Đọc dữ liệu, tra từ điển, tính toán — Tự động cho phép.
  - *Trung bình (Moderate):* Ghi nhớ từ vựng mới, cập nhật bảng thuật ngữ — Yêu cầu quyền danh mục.
  - *Rủi ro (Dangerous):* Sửa quy tắc nguồn sách, ghi đè tệp hệ thống, xóa cache — **Bắt buộc người dùng phê duyệt thủ công**.

#### Story Wiki, Niên Biểu (Chronicle) & Tạo ảnh minh họa AI
- **Hồ sơ Thực thể & Nhân vật (Entities):** Lưu trữ danh sách nhân vật, tên tiếng Trung gốc, tên dịch chuẩn, chức vị, cảnh giới võ học, bang phái, tính cách và bảo bối sở hữu.
- **Thiết lập thế giới (World-building):** Ghi chép địa danh, hệ thống tu luyện, công pháp và tôn ti trật tự trong truyện.
- **Niên biểu dòng thời gian (Chronicle):** Tự động tổng hợp các sự kiện lịch sử xảy ra theo từng chương thực tế, gắn liền với tiêu đề chương đã được dịch.
- **Tạo ảnh nhân vật bằng AI (Story Illustration):** Tích hợp công cụ sinh ảnh AI (DALL-E / Stable Diffusion) để vẽ chân dung nhân vật hoặc phong cảnh dựa trên hồ sơ miêu tả trong Story Wiki.

#### Không gian sáng tác (Writing) & Biên tập Ebook (Ebook Editor)
- Dành cho tác giả tự viết truyện hoặc biên tập viên ebook:
  - *Lập dàn ý (Outline):* Thiết kế khung thế giới, nhân vật và tiến trình cốt truyện.
  - *Ebook Editor:* Quản lý các block nội dung theo chương, hỗ trợ đánh chữ hoa đầu đoạn trang trí (Drop Cap), chèn ảnh minh họa.
  - *Xem trước & Xuất bản:* Xem trước giao diện hiển thị trên thiết bị di động và xuất ra file chuẩn EPUB hoặc TXT.

#### Đọc tin tức RSS
- Thêm các đường dẫn RSS feed từ các trang tin tức, blog hoặc website truyện để nhận thông báo bài viết mới nhất mà không cần mở trình duyệt web.

#### Media Player & Audiobook
- Trình phát đa phương tiện tối ưu cho việc nghe sách nói hoặc xem video chuyển thể:
  - Hỗ trợ hẹn giờ tắt khi ngủ (Sleep Timer), tăng tốc độ phát (0.5x – 3.0x), tua nhanh/lùi lại 15 giây.
  - Tự động ghi nhớ vị trí giây đang phát khi thoát ứng dụng.

---

### 6. Cá Nhân & Cài Đặt Hệ Thống (My / Settings)

Khu vực cấu hình sâu toàn bộ các tham số của DrDucBook:

| Mục Cài Đặt | Các Thiết Lập Trọng Tâm |
| --- | --- |
| **Tài khoản (Account)** | Đăng nhập Supabase, quản lý quyền hạn Free/Premium/Admin, đổi mật khẩu, đồng bộ hồ sơ. |
| **Cài đặt Đọc (Read Settings)** | Cỡ chữ, phông chữ tùy chỉnh (hỗ trợ nạp file .ttf/.otf bên ngoài), khoảng cách dòng, lề trang, màu nền giấy, kiểu chuyển trang (Cuộn mượt, Lật trang 3D, Trượt ngang, Không hiệu ứng), hành vi chạm màn hình. |
| **Giao diện & Theme** | Chọn giữa **Material 3 Expressive** và **Miuix Engine**. Hỗ trợ chế độ màu tự động Monet (trích màu từ hình nền thiết bị), 14 bảng màu dựng sẵn, chế độ nền tối AMOLED thuần đen tiết kiệm pin, hiệu ứng kính mờ (Glassmorphism). |
| **Cài đặt AI & Router** | Thêm và quản lý các Provider: OpenAI, Gemini, Antigravity, Claude, DeepSeek, Local AI GGUF. Quản lý tài khoản đăng nhập OAuth Codex/Antigravity. Gán model mặc định cho từng tác vụ (Dịch, Chat, Tóm tắt, Vẽ ảnh). |
| **Cài đặt Dịch (Translation)** | Lựa chọn bộ máy dịch (QT2025, NMT Hachimi-QT ONNX, ML Kit, AI Cloud). Cấu hình bộ nhớ dịch truyện (Story Memory), quản lý từ điển nhanh (Quick Dictionary Manager), các quy tắc làm sạch văn bản tiếng Việt hậu dịch thuật (Post-Processor). |
| **Cài đặt TTS (Giọng đọc)** | Chọn động cơ TTS: TTS Hệ thống, HTTP TTS mạng hoặc Local ZeroTTS ONNX ngoại tuyến. Tải các gói giọng đọc mẫu (Voice Addons). Tùy chỉnh tốc độ đọc, cao độ và thời gian tạm dừng giữa các câu. |
| **Sao lưu & Khôi phục** | Thiết lập sao lưu Cục bộ (SAF), Đám mây Supabase (metadata logic), Google Drive Full Backup (mã hóa ZIP toàn bộ sách đệm và media), WebDAV cá nhân. Lập lịch sao lưu tự động. |
| **Dịch vụ Web (WebService)** | Bật máy chủ HTTP nội bộ nhúng trong app để biến điện thoại thành một Web Server sách. Cho phép dùng máy tính, máy tính bảng khác trong cùng mạng Wi-Fi truy cập đọc sách, sửa nguồn hoặc kết nối Cloudflare Tunnel để đọc từ xa qua Internet. |
| **Khác & Lab (Phòng thí nghiệm)** | Bật/tắt các tính năng thử nghiệm, quản lý cờ phát triển, kiểm tra toàn diện nguồn sách, xem nhật ký lỗi hệ thống (App Log) để chẩn đoán. |

---

## 3. HƯỚNG DẪN CHUYÊN SÂU MÀN HÌNH ĐỌC SÁCH (READER)

Màn hình đọc là nơi bạn dành nhiều thời gian nhất. Để làm chủ trải nghiệm đọc sách, hãy ghi nhớ các quy tắc tương tác sau:

### Bố cục điều hướng & Cử chỉ thông minh
Màn hình đọc được chia thành 3 vùng chạm mặc định:
- **Chạm vào Vùng Trung Tâm (1/3 giữa màn hình):** Bật/Tắt thanh công cụ trên và thanh điều hướng dưới.
- **Chạm vào Vùng Bên Phải (hoặc vuốt từ phải sang trái):** Lật sang trang tiếp theo.
- **Chạm vào Vùng Bên Trái (hoặc vuốt từ trái sang phải):** Quay lại trang trước đó.
- **Phím Âm Lượng:** Bạn có thể bật tính năng dùng phím Tăng/Giảm âm lượng để lật trang trong phần Cài đặt đọc.

### Tính năng dịch tức thời & Sửa đổi bản dịch (Revision)
Khi đọc các cuốn truyện convert hoặc truyện gốc tiếng Trung:
1. **Dịch 1 chạm:** Chạm giữa màn hình → Bấm biểu tượng **Dịch (Translate)** trên thanh công cụ. Toàn bộ chương hiện tại sẽ được dịch sang tiếng Việt theo bộ máy bạn đã cấu hình (QT, NMT hoặc AI).
2. **Đối chiếu câu gốc:** Nhấn giữ vào một câu văn tiếng Việt bất kỳ, ứng dụng sẽ hiển thị một cửa sổ nhỏ đối chiếu dòng chữ gốc CJK tương ứng.
3. **Sửa bản dịch (Translation Revision):** Nếu thấy một danh từ riêng hoặc một câu bị dịch sai, nhấn vào nút **Sửa bản dịch**. Bạn nhập từ dịch đúng → Ứng dụng sẽ tự động lưu từ này vào **Story Translation Memory** của cuốn sách và áp dụng ngay lập tức cho các chương sau!

### Tra cứu từ điển tức thời (Quick Dictionary)
- Khi bôi đen một từ hoặc cụm từ lạ trong văn bản, thanh công cụ ngữ cảnh nổi lên. Chọn **Tra từ điển (Quick Dict)**:
  - Ứng dụng sẽ tự động ánh xạ từ đã chọn về từ gốc trong tiếng Trung nhờ thuật toán gióng hàng thông minh (Alignment Algorithm).
  - Hiển thị đầy đủ: Phiên âm Pinyin, nghĩa Hán Việt, từ điển Thiều Chửu, từ điển Lạc Việt và ngữ nghĩa ngữ cảnh.

### Đọc thành tiếng (Read Aloud TTS) đồng bộ theo dòng
- Chạm giữa màn hình → Bấm biểu tượng **Tai nghe / Đọc to**:
  - Trình phát TTS sẽ kích hoạt, tự động làm sáng (highlight) dòng chữ đang đọc.
  - Bạn có thể chuyển nhanh sang câu tiếp theo bằng cách chạm đúp vào một dòng bất kỳ trên trang sách.
  - Thanh nổi điều khiển dưới đáy màn hình cho phép chỉnh tốc độ đọc, tạm dừng hoặc hẹn giờ tự tắt khi ngủ.

### Tóm tắt chương bằng AI & Lọc làm sạch văn bản
- **Tóm tắt chương (AI Chapter Summary):** Mở menu mở rộng (3 chấm) → Chọn **Tóm tắt bằng AI**. Mô hình AI sẽ đọc hiểu nội dung toàn bộ chương và hiển thị một bản tóm tắt ngắn gọn các sự kiện chính trong 3-5 gạch đầu dòng.
- **Làm sạch văn bản (Replace Rules):** Tự động phát hiện và xóa bỏ các đoạn quảng cáo chèn thêm của web nguồn (như "chúc các bạn đọc truyện vui vẻ tại web xyz...", "bấm vào link này để theo dõi tác giả...").

---

## 4. BẢNG XỬ LÝ SỰ CỐ NHANH (TROUBLESHOOTING)

Dưới đây là cẩm nang tra cứu và xử lý các lỗi thường gặp trong quá trình sử dụng:

| Hiện Tượng Lỗi | Nguyên Nhân Gốc | Giải Pháp Khắc Phục Chuẩn Xác |
| --- | --- | --- |
| **Không tìm kiếm được sách** | Nguồn truyện bị tắt, URL nguồn đã đổi tên miền hoặc bị nhà mạng chặn. | 1. Vào *Cá nhân → Nguồn sách*, kiểm tra xem nguồn có đang bật không.<br>2. Chạy tính năng *Kiểm tra nguồn* để tìm nguồn hoạt động tốt.<br>3. Mở nguồn bằng *Trình duyệt tích hợp* để kiểm tra xem web nguồn có bị chặn IP không. |
| **Mục lục sách bị rỗng** | Quy tắc bóc tách mục lục (TOC rule) của nguồn bị sai lệch do trang web đổi giao diện. | 1. Chọn *Đổi nguồn khác* trong trang chi tiết sách.<br>2. Dùng công cụ sửa nguồn hoặc dùng Trợ lý Agent để yêu cầu sửa lại quy tắc mục lục. |
| **Chương truyện bị trắng hoặc nội dung rác** | Trang nguồn bắt đăng nhập hoặc bật tường lửa chống bot Cloudflare. | 1. Bấm menu 3 chấm trên trang đọc → Chọn *Mở bằng trình duyệt tích hợp*.<br>2. Vượt qua captcha Cloudflare Turnstile hoặc đăng nhập tài khoản trên web.<br>3. Quay lại ứng dụng và bấm *Làm mới nội dung chương*. |
| **AI báo lỗi HTTP 400** | Sai lệch giao thức (Protocol Mismatch) hoặc Model ID không tồn tại trên Provider. | 1. Vào *Cá nhân → AI Router*, bấm vào Provider đang dùng.<br>2. Bấm nút **"Kiểm tra API Key & Lấy danh sách Model"**.<br>3. Chọn đúng tên model từ danh sách trả về, không gõ tay model sai định dạng. |
| **AI báo lỗi HTTP 429 (Resource Exhausted)** | Hết hạn mức request (RPM) hoặc tài khoản gói miễn phí bị quá tải. | 1. Đổi sang model nhẹ hơn (ví dụ: chuyển từ Gemini Pro sang Gemini Flash).<br>2. Với tài khoản Google Antigravity: Hệ thống đã tự động kích hoạt chế độ Fallback và hạn mức GOOGLE_ONE_AI, hãy thử lại sau vài phút. |
| **Dịch NMT báo thiếu bộ nhớ / Văng ứng dụng** | Dung lượng RAM của thiết bị không đủ để nạp đồng thời model NMT lớn. | 1. Đóng bớt các ứng dụng chạy ngầm trên điện thoại.<br>2. Trong *Cài đặt Dịch*, chuyển về dùng bộ máy **QT2025 (Offline)** siêu nhẹ.<br>3. Đảm bảo đã tải đúng gói model tương thích với kiến trúc chip của máy. |
| **Đọc TTS bị ngắt quãng giữa chừng** | Android đóng dịch vụ ngầm do cơ chế tiết kiệm pin LMK. | 1. Vào Cài đặt điện thoại → Ứng dụng → DrDucBook → Tắt toàn bộ chế độ tiết kiệm pin.<br>2. Cấp quyền *Thông báo* cho ứng dụng.<br>3. Nếu dùng ZeroTTS ONNX, hệ thống đã tự động chia nhỏ câu dưới 120 ký tự để chống nghẽn CPU. |
| **Sao lưu Google Drive không nạp được danh sách** | Chưa cấp quyền OAuth hoặc thiếu API Key thư mục công khai. | 1. Vào *Sao lưu và Khôi phục → Google Drive*, đăng nhập lại tài khoản Google.<br>2. Đồng ý toàn bộ các quyền truy cập tệp sao lưu khi màn hình Google yêu cầu. |
| **Khôi phục sao lưu xong chưa thấy sách** | Cơ sở dữ liệu đang trong quá trình đồng bộ hóa Room DB v105. | Chờ khoảng 10-15 giây, kéo vuốt xuống để làm mới tủ sách, hoặc thoát hẳn ứng dụng và mở lại. |

---

> [!TIP]
> **Khuyến nghị bảo mật & Sử dụng bền vững:**  
> - Luôn thực hiện xuất bản sao lưu cục bộ (Local Backup) ít nhất 1 lần/tháng và lưu file `.zip` vào thẻ nhớ ngoài hoặc máy tính.
> - Không chia sẻ file sao lưu có chứa thông tin API Key hoặc mật khẩu cá nhân lên các diễn đàn công cộng.
> - Thường xuyên kiểm tra và dọn dẹp bộ nhớ đệm (Cache) nếu bạn đọc nhiều truyện tranh (Manga) để máy luôn có dung lượng trống tối ưu.
