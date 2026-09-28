# Kế hoạch nhúng WebDAV Google Drive cục bộ bằng Go + Gomobile/JNI

## 1. Tóm tắt quyết định

Mục tiêu là cho phép DrDucBook đọc thư viện từ Google Drive thông qua một máy chủ WebDAV chỉ lắng nghe trên 127.0.0.1, được nhúng trong ứng dụng Android. Lõi WebDAV viết bằng Go và đóng gói thành Android Archive (AAR) bằng Gomobile; Kotlin điều phối xác thực, vòng đời dịch vụ, giao diện và trạng thái ứng dụng.

Phương án MVP được đề xuất:

- Không nhúng Alist hoặc rclone nguyên khối.
- Dùng golang.org/x/net/webdav cho HTTP/WebDAV và Google Drive REST API cho lớp filesystem.
- Chỉ hỗ trợ đọc: OPTIONS, PROPFIND, GET, HEAD và HTTP Range.
- Chỉ bind vào 127.0.0.1; không mở WebDAV ra mạng LAN.
- Chạy máy chủ trong DriveWebDavService, một Foreground Service riêng, không gộp vào Ktor WebService hiện có.
- Kotlin giữ quyền kiểm soát Google authorization, token hiện hành, Android Keystore/DataStore, notification và lifecycle.
- Go chỉ nhận access token ngắn hạn qua API bridge và chịu trách nhiệm HTTP server, duyệt thư mục, metadata, đọc/seek/range và refresh phiên HTTP khi token thay đổi.
- Tái sử dụng client WebDAV hiện tại của DrDucBook để đọc thư viện; không viết lại luồng RemoteBookWebDav trong MVP.
- Có UI Compose thiết lập/quản lý WebDAV local: chọn Google Drive hoặc public link, chọn thư mục gốc, start/stop/reconnect và mở catalog.
- Sau khi đăng nhập Google Drive hoặc xác thực public link, tự động tạo/cập nhật một managed Drive library source, chọn làm nguồn hiện hành và nạp catalog vào luồng RemoteBook hiện có.
- Bổ sung pipeline convert model ngocdang83/HachimiMT-60-QT từ Transformers/Marian sang gói ONNX tương thích runtime NMT hiện tại, đồng thời cho phép chọn model NMT trong Translation settings.
- Không thêm Room migration nếu MVP chỉ cần một cấu hình Drive đang hoạt động; lưu cấu hình không nhạy cảm bằng DataStore và thông tin nhạy cảm bằng cơ chế bảo vệ phù hợp.

Điểm cần quyết định trước khi khóa thiết kế token là có yêu cầu lưu refresh_token lâu dài hay không. MVP ưu tiên silent re-authorization bằng Google Play Services để lấy access token mới, tránh đưa client_secret vào APK. Nếu bắt buộc dùng refresh token thực sự, phải xác nhận OAuth client type, token exchange backend và chính sách bảo mật trước khi triển khai.

## 2. Phạm vi và tiêu chí thành công

### Trong phạm vi MVP

1. Người dùng cấp quyền đọc một Google Drive hoặc thư mục Drive.
2. Ứng dụng lấy access token hiện hành bằng Google authorization flow.
3. Foreground Service khởi chạy Go WebDAV trên một cổng localhost khả dụng.
4. Ứng dụng tạo hoặc hiển thị URL WebDAV nội bộ, ví dụ http://127.0.0.1:<port>/.
5. Luồng nhập/đọc sách hiện có truy cập được thư mục và file trên Drive qua client WebDAV.
6. Khi access token hết hạn, Kotlin thực hiện silent authorization; Go nhận token mới mà không phải đăng nhập lại nếu Google session còn hiệu lực.
7. Dừng service giải phóng cổng, goroutine và notification một cách an toàn.
8. Có unit test cho Go filesystem, bridge/controller và các nhánh token/service quan trọng.
9. Người dùng có màn hình thiết lập WebDAV local để kết nối bằng Google account hoặc public link.
10. Kết nối thành công tự động tạo/cập nhật managed source, chọn source đó và mở danh mục thư viện.
11. Từ catalog, người dùng có thể duyệt thư mục, xem file sách, tải file vào storage, nhập vào bookshelf và mở reader.
12. Có artifact ONNX tái lập được cho HachimiMT-60-QT, kiểm tra được checksum/license/provenance và chạy được qua NmtOnnxService trên thiết bị.
13. Người dùng có thể chọn model NMT đã cài; runtime nạp đúng model, giữ model cũ hoạt động và áp dụng decode profile phù hợp từng model.

### Ngoài phạm vi MVP

- Ghi file, upload, delete, rename, mkdir hoặc đồng bộ hai chiều.
- Chia sẻ WebDAV cho thiết bị khác trong LAN.
- Web UI của Alist, database hoặc cơ chế quản trị kiểu Alist; UI Compose trong app vẫn thuộc MVP.
- Hỗ trợ toàn bộ Google Drive API surface như permissions, comments hoặc revisions.
- Tích hợp nhiều nhà cung cấp cloud trong cùng lõi Go.
- Lưu client secret trong ứng dụng.
- Chạy service tự động sau boot nếu chưa có một use case được người dùng khởi động rõ ràng.
- Huấn luyện hoặc fine-tune lại HachimiMT-60-QT; plan chỉ convert/quantize/đóng gói model đã có.
- Đưa model ONNX lớn trực tiếp vào APK; model được phân phối như external asset và import vào app-private storage.

### Definition of Done

- Build debug thành công trên mọi ABI được dự án hỗ trợ.
- Có thể cấp quyền một lần, khởi động service, duyệt thư mục và mở một file lớn từ Drive.
- File đọc được qua Range/seek, không tải toàn bộ file vào RAM.
- Google account/public link thành công đều tạo được source ổn định và tự mở đúng catalog tương ứng.
- Catalog hỗ trợ folder/file; file được chọn tải về, nhập thành sách local/remote và mở được bằng reader hiện có.
- Gói HachimiMT-60-QT ONNX có manifest, attribution CC-BY-4.0, checksum và kết quả parity/reference test.
- Chuyển model NMT trong settings không làm crash process riêng; model chưa cài hiển thị trạng thái và hướng dẫn tải/import rõ ràng.
- Token hết hạn được xử lý theo một trong hai đường: silent re-authorization thành công hoặc hiển thị trạng thái cần cấp quyền lại; không crash service.
- Dừng/khởi động lại service nhiều lần không để lại goroutine, cổng hoặc notification mồ côi.
- Không lộ access token trong log, exception message, URI hoặc UI.
- Có kiểm thử offline cho hành vi chính và kiểm thử thiết bị tối thiểu cho lifecycle, notification, network và ABI.

## 3. Khảo sát hiện trạng codebase

### WebDAV client và remote book

Các thành phần hiện có cần tận dụng:

- app/src/main/java/io/legado/app/lib/webdav/WebDav.kt: client WebDAV Kotlin hiện tại; cần xác nhận đầy đủ các phương thức list/read/auth mà remote flow đang dùng.
- app/src/main/java/io/legado/app/lib/webdav/Authorization.kt: mô hình thông tin xác thực WebDAV hiện có; có thể dùng để tạo cấu hình endpoint localhost.
- app/src/main/java/io/legado/app/lib/webdav/WebDavFile.kt: đại diện file/thư mục từ WebDAV; là điểm tương thích quan trọng với server mới.
- RemoteBookWebDav.kt, RemoteBookRepository.kt, RemoteBookViewModel.kt: luồng nhập sách từ WebDAV hiện có; nên giữ làm consumer của endpoint nội bộ thay vì tạo một đường đọc Drive song song.
- RemoteBookScreen.kt: đã có UI catalog WebDAV, breadcrumb, tìm kiếm/sắp xếp, chọn file, tải vào bookshelf, xử lý archive và mở sách.
- Server.kt, ServerDao.kt và ImportBookConfig.remoteServerId: đã có mô hình lưu server WebDAV và source đang chọn; đây là điểm tích hợp chính cho managed Drive source.

Kết luận: nếu server localhost tuân thủ đúng các response WebDAV mà client hiện tại yêu cầu, phần lớn luồng catalog, tải sách và mở reader có thể được tái sử dụng.

Lưu ý về thuật ngữ “nguồn sách”: BookSource trong codebase là nguồn web rule-based, có search/detail/toc/content rules; còn thư viện file Drive phù hợp với Server + RemoteBookWebDav. MVP sẽ tạo managed Drive library source hiển thị trong UI nguồn thư viện/server selector và liên kết tới Server row, không chèn một BookSource giả thiếu rule. Nếu sản phẩm bắt buộc source phải xuất hiện trong màn hình BookSource rule-based, đó là một adapter/scope riêng cần chốt ở Phase 0.

### Google authorization và backup

Các thành phần cần dùng làm nguồn tham chiếu, không nên sao chép mù quáng:

- GoogleDriveAuthorizationBridge.kt: cầu nối Google Play Services hiện có; cần mở rộng hoặc trừu tượng hóa để cấp access token cho service.
- GoogleDriveAppDataBackupRepository.kt: luồng Google Drive đã có thể cho biết cách app tạo client, xử lý lỗi và chọn account.
- GoogleDriveAppDataContract.kt: hợp đồng dữ liệu/trạng thái authorization hiện có.
- CloudConsentScopes.kt: nơi kiểm tra scope hiện tại và quyết định có cần scope đọc Drive riêng cho tính năng WebDAV hay không.

Không được giả định rằng scope backup hiện tại đủ cho việc đọc toàn bộ Drive. Cần xác minh scope tối thiểu, consent behavior và chính sách verification của Google trước khi chốt.

### Foreground Service và notification

- BaseService.kt là cơ sở để giữ cách xử lý lifecycle và coroutine/service state thống nhất.
- WebService.kt là Ktor HTTP service hiện có nhưng không nên gộp trách nhiệm với Go WebDAV.
- AndroidManifest.xml là nơi thêm service, foreground service type, permission và exported policy.
- Notification channel/setup hiện nằm trong App.kt; cần tái sử dụng convention sẵn có.

Kết luận: tạo DriveWebDavService riêng giúp tránh coupling giữa Ktor web server và native Go server, đồng thời dễ kiểm soát quyền, notification và restart.

### DI, Navigation và Compose

- di/appModule.kt là nơi đăng ký repository/use case/viewmodel.
- MainNavKey.kt và MainNavGraph.kt là nơi thêm route settings/Drive WebDAV nếu feature có màn hình riêng.
- ConfigNavScreen.kt và ConfigTag.kt là điểm tích hợp cấu hình hiện tại.

Feature mới phải theo MVI/UDF của repository:

- DriveWebDavContract.kt: UiState, Intent, Effect và trạng thái dialog/sheet nếu cần.
- DriveWebDavViewModel.kt: điều phối authorization, start/stop service và expose status.
- DriveWebDavScreen.kt: stateless Compose UI.
- DriveWebDavRouteScreen.kt (tùy chọn): xử lý activity result/lifecycle và wiring ViewModel.

### Native build precedent

tools/cloudflared-android/build.ps1 và tools/cloudflared-android/README.md là precedent cho native binary, ABI và đóng gói. Cần tận dụng convention build hiện có nhưng không giả định cloudflared có thể được dùng như template hoàn chỉnh cho Gomobile AAR.

### Build constraints đã biết

- minSdk: 26.
- compileSdk/targetSdk: 37.
- Java toolchain: 21; CI hiện dùng JDK 17 theo tài liệu repository.
- ABI hiện có: armeabi-v7a, arm64-v8a, x86_64.
- R8 và resource shrinking bật ở release; có biến thể noR8 để chẩn đoán.
- App có native packaging hiện hữu và cần kiểm tra khả năng tương thích với .so do Gomobile sinh ra.
- Database hiện ở version 111; không nâng version cho MVP chỉ để lưu một config active nếu DataStore đủ dùng.

### NMT/ONNX hiện trạng đã có

- Android manifest đã tách NmtOnnxService sang process riêng nmt_onnx; app process giao tiếp qua Messenger/ResultReceiver.
- NmtTranslationRepository và NmtTranslationGateway đã có contract dịch, progress, cancel và close.
- HachimiOnnxTranslator đang mở năm graph ONNX cố định: encoder_model.onnx, decoder_model_merged.onnx, tokenizer.onnx, target_tokenizer.onnx và detokenizer.onnx.
- HachimiOnnxModelRegistry/HachimiOnnxModelImporter đang hard-code một MODEL_ID hachimi_onnx, một thư mục cài đặt và một manifest.
- TranslationConfigScreen đã có tải/import ZIP, trạng thái cài model và nhóm cấu hình decode NMT; hiện chưa có model selector.
- ExternalAssetCatalog, asset delivery manifest và AssetDeliveryImportRepository đang pin một artifact Hachimi ONNX. Artifact hiện tại có provenance HachimiMT-60-zh-vi, vì vậy không được âm thầm thay thế nó bằng QT model; cần thêm artifact/model ID mới.
- HachimiOnnxRuntimeCoordinator đã có generation/mutex để thay model an toàn; cần mở rộng generation theo model hoặc đóng runtime khi modelId đổi.

Kết luận: phần Android runtime và delivery có thể tái sử dụng. Công việc mới tập trung vào conversion pipeline, manifest/catalog nhiều model, model-specific decode policy và UI chọn model.

## 4. Kiến trúc đích

Sơ đồ luồng:

    DriveWebDav setup UI / Remote book flow
                    |
                    v
    DriveWebDavViewModel --> DriveWebDavController/Repository
                    |                  |
                    |                  +--> Google Authorization Bridge
                    |                  |       - chọn account
                    |                  |       - silent authorization
                    |                  |       - access token hiện hành
                    |                  |
                    |                  +--> DriveLinkResolver
                    |                  |       - Google account/root folder
                    |                  |       - public folder/file link
                    |                  |       - provider/resource validation
                    |                  |
                    |                  +--> ManagedSourceRegistry
                    |                          - sourceKey ổn định
                    |                          - Server row / active source
                    |                          - route tới catalog
                    |
                    |                  +--> Android Service Controller
                    |                          - start/stop/bind service
                    |                          - trạng thái cổng/local URL
                    v
    DriveWebDavService (Foreground Service, dataSync)
                    |
                    +--> GoBridge (Gomobile-generated Java API)
                    |       - Start(config, accessToken)
                    |       - UpdateAccessToken(token)
                    |       - Stop()
                    |       - Status()/Error()
                    |
                    v
    Go WebDAV server :<port> trên 127.0.0.1
                    |
                    v
    Google Drive REST API

### Nguyên tắc phân ranh giới

Kotlin/Java chịu trách nhiệm:

- Google account selection, consent và authorization result.
- Lấy/re-lấy access token.
- Lưu config không nhạy cảm và trạng thái cần phục hồi.
- Chọn port, start/stop/restart service.
- Notification, permission, lifecycle, UI, navigation và DI.
- Không để access token xuất hiện trong log hoặc state hiển thị.

Go chịu trách nhiệm:

- HTTP listener và WebDAV methods.
- Drive filesystem adapter.
- API pagination, metadata mapping, file read/seek/range.
- Giới hạn concurrency, timeout và đóng server.
- Nhận token từ Kotlin và trả lỗi có mã hóa/phân loại, không trả secret.

Không để Go tự quản lý Android lifecycle, notification hoặc Google Play Services. Không để Compose gọi trực tiếp JNI.

### Hai chế độ đầu vào

- AUTHENTICATED_DRIVE: user chọn Google account, cấp scope, chọn My Drive/root folder hoặc folder ID; Go dùng access token hiện hành để duyệt và đọc.
- PUBLIC_LINK: user dán public link; Kotlin/Go resolver chuẩn hóa URL, nhận diện provider và resource ID, kiểm tra resource có thực sự public rồi mới tạo source. Không yêu cầu Google login nếu resource public và provider cho phép list/download không cần token.

Public link không đồng nghĩa mọi trang share đều là endpoint tải hoặc API catalog. Link Google Drive folder cần khả năng list metadata; link file đơn lẻ có thể tạo catalog một phần tử; trang HTML yêu cầu login, quota confirmation hoặc permission riêng phải chuyển sang trạng thái AUTH_REQUIRED/UNSUPPORTED thay vì scraping không ổn định.

## 5. Thiết kế lõi Go

### Cấu trúc package dự kiến

Đề xuất đặt mã nguồn ở một thư mục native riêng:

    native/go-webdav/
    ├── go.mod
    ├── go.sum
    ├── bind/
    │   └── bridge.go
    ├── server/
    │   ├── server.go
    │   ├── config.go
    │   └── errors.go
    ├── drive/
    │   ├── client.go
    │   ├── filesystem.go
    │   ├── metadata.go
    │   └── reader.go
    └── internal/...

Tên thư mục có thể điều chỉnh theo convention build, nhưng cần tách bind khỏi implementation để API Gomobile nhỏ và ổn định.

### API public cho Gomobile

API nên dùng các kiểu mà Gomobile hỗ trợ ổn định: string, int, int64, bool, error đơn giản và các object nhỏ có getter. Tránh expose trực tiếp context.Context, channel, interface phức tạp, io.Reader, http.Handler hoặc Go struct chứa map/slice không cần thiết.

Hợp đồng khái niệm:

    type Config struct {
        Port              int
        RootFolderID      string
        RequestTimeoutSec int
        ReadOnly          bool
    }

    type Server struct { /* private implementation */ }

    func NewServer(config *Config) (*Server, error)
    func (s *Server) Start(accessToken string) error
    func (s *Server) UpdateAccessToken(accessToken string) error
    func (s *Server) Stop() error
    func (s *Server) IsRunning() bool
    func (s *Server) Port() int
    func (s *Server) LastError() string

Đây là API định hướng; khi triển khai cần kiểm tra chữ ký thực tế mà gomobile bind chấp nhận. Nếu gomobile không bind tốt struct/config theo mong muốn, dùng constructor với primitive hoặc JSON config string có schema versioned.

### WebDAV methods

MVP chỉ cho phép:

- OPTIONS: advertises capability.
- PROPFIND: list directory/file metadata, hỗ trợ depth mà client hiện tại cần.
- GET: stream file; hỗ trợ Range nếu adapter/client yêu cầu.
- HEAD: metadata và kích thước.

Các method thay đổi dữ liệu phải trả 405 Method Not Allowed hoặc mã phù hợp. Không được âm thầm giả lập ghi bằng cache local.

### Google Drive filesystem adapter

Adapter cần map:

- thư mục Drive -> os.FileInfo/WebDAV collection;
- file Drive -> metadata gồm name, size, modified time, content type;
- path WebDAV -> Drive file ID, tránh dùng tên làm định danh duy nhất;
- root folder tùy chọn -> Drive folder ID được cấu hình.

Yêu cầu kỹ thuật:

- Dùng Drive API files.list với query trong parent, trashed=false và fields tối thiểu.
- Bật pagination; không giả định một thư mục trả về đủ trong một response.
- Escape/sanitize tên file khi tạo path XML/WebDAV.
- Cache metadata ngắn hạn nếu cần giảm request, nhưng phải có giới hạn và invalidation rõ ràng.
- Với file lớn, dùng HTTP range hoặc reader seekable; không đọc toàn bộ file vào bộ nhớ.
- Áp dụng timeout, retry có backoff cho lỗi tạm thời; không retry vô hạn.
- Xử lý shortcut, Google Docs native document và file không thể tải theo policy riêng; MVP có thể trả lỗi rõ ràng cho loại chưa hỗ trợ.

### Token trong Go

Go không nên tự lưu refresh token. Mỗi request Drive cần dùng access token hiện tại được Kotlin cấp. Khi nhận 401:

1. Trả lỗi có phân loại AUTH_EXPIRED về client hoặc service controller.
2. Kotlin thực hiện silent authorization ngoài Go.
3. Kotlin gọi UpdateAccessToken sau khi lấy token mới.
4. Request mới tiếp tục; request đang lỗi có thể retry một lần sau khi token được cập nhật.

Nếu cần concurrency-safe token update, dùng một token provider nội bộ với mutex/atomic; không để token nằm trong log, LastError hoặc debug dump.

## 6. Chiến lược OAuth và tự refresh token

### Phương án khuyến nghị cho MVP

Kotlin dùng Google Play Services authorization bridge để lấy access token hiện hành, ưu tiên silent flow khi account đã được cấp quyền. requestOfflineAccess/server auth code chỉ được dùng nếu thiết kế backend/token exchange được xác định rõ.

Luồng dự kiến:

1. Người dùng bấm “Kết nối Google Drive”.
2. App yêu cầu scope tối thiểu cần cho việc đọc Drive.
3. App lưu account identifier và trạng thái consent, không lưu access token lâu dài trong DataStore.
4. Khi start service, app lấy access token hiện hành và truyền vào Go.
5. Khi service hoặc client phát hiện token hết hạn, controller gọi lại silent authorization.
6. Nếu silent flow thất bại do revoke/consent/account change, dừng retry và gửi Effect yêu cầu người dùng cấp quyền lại.

### Refresh token thực sự là decision gate

Access token và refresh token không tương đương. Một refresh token persistent thường cần authorization code exchange và client credentials phù hợp. Không nhúng client_secret vào APK. Trước khi chọn đường này cần trả lời:

- OAuth client là Android client hay web/server client?
- Có backend đáng tin cậy để exchange code và lưu refresh token không?
- Scope nào được chấp thuận và có yêu cầu Google verification không?
- Refresh token được lưu ở đâu, mã hóa/rotate/revoke thế nào?
- Khi nhiều tài khoản Google cùng tồn tại, token nào thuộc root folder nào?

Nếu chưa có câu trả lời, không ghi trong kế hoạch triển khai rằng app tự refresh bằng refresh token. Cách diễn đạt đúng cho MVP là tự động lấy access token mới bằng silent authorization khi Google session còn hiệu lực.

### Bảo mật

- Không log Authorization header, access token, refresh token, server auth code hoặc raw exception chứa chúng.
- Không truyền token qua URL, Intent extras không cần thiết, notification hoặc WebDAV credentials.
- Dọn token khỏi object Go khi service dừng nếu có thể; tránh giữ bản sao ngoài thời gian cần thiết.
- Dùng HTTPS tới Google API; localhost WebDAV chỉ bind loopback.
- Kiểm tra một ứng dụng khác trên thiết bị không thể truy cập endpoint nếu thiết kế yêu cầu riêng tư tuyệt đối. Nếu threat model cần mạnh hơn, thêm secret phiên ngẫu nhiên trong header/password và kiểm tra ở client/server.
- Không expose port ra 0.0.0.0.

## 7. Foreground Service trên Android

### Service contract

Tạo DriveWebDavService riêng, kế thừa base service phù hợp trong repository. Service cần:

- nhận action rõ ràng: START, STOP, UPDATE_TOKEN hoặc dùng một controller nội bộ;
- khởi tạo Go server ở background thread/coroutine, không block main thread;
- gọi startForeground() sớm với notification hợp lệ;
- khai báo foreground service type dataSync nếu phù hợp với target SDK và policy hiện hành;
- hiển thị trạng thái tối thiểu: đang chạy, port nội bộ, lỗi cần cấp quyền lại;
- stop foreground và đóng Go server idempotently;
- xử lý onTaskRemoved, process recreation và service restart theo policy đã chọn;
- không tự khởi động sau boot trong MVP nếu không có use case người dùng rõ ràng.

### Quyền và manifest

Cần cập nhật có kiểm soát:

- android.permission.FOREGROUND_SERVICE;
- permission/type cụ thể cần cho dataSync theo target SDK hiện tại;
- declaration của service với exported=false nếu không có lý do phải export;
- notification permission behavior trên Android 13+;
- network permission nếu chưa có;
- proguard/R8 keep rules cho class Java do Gomobile sinh ra nếu release shrinker loại nhầm.

### Cổng và trạng thái

- Mặc định để hệ thống chọn port khả dụng (0) rồi trả port thực tế về Kotlin, trừ khi client hiện tại bắt buộc port cố định.
- Persist port chỉ để hiển thị/khôi phục nếu cần, không giả định port cũ luôn rảnh.
- Service state nên phân biệt Stopped, Starting, Running, AuthRequired, Error và Stopping.
- Khi start thất bại, notification và UI phải trả nguyên nhân phân loại, không đưa stack trace/token vào giao diện.

## 8. Tích hợp với client WebDAV và luồng đọc sách

1. Tạo một cấu hình WebDAV nội bộ từ 127.0.0.1:<port>.
2. Nếu client hiện tại yêu cầu username/password, dùng cơ chế session secret nội bộ hoặc mở rộng model để hỗ trợ localhost an toàn; không hard-code credential dùng chung.
3. Cho phép người dùng chọn root folder Drive nếu cần.
4. Giữ RemoteBookWebDav làm consumer chính, chỉ bổ sung factory/provider tạo endpoint nội bộ.
5. Xác nhận behavior của PROPFIND, URL encoding, trailing slash, MIME type, file size và Range bằng integration test.
6. Đảm bảo khi service dừng, remote flow nhận lỗi kết nối rõ ràng và không treo coroutine.

Không nên tạo một DriveBookRepository riêng trong MVP nếu kết quả cuối cùng vẫn là duyệt file từ WebDAV. Một đường duy nhất giảm trùng lặp parser, cache và hành vi UI.

## 8A. UI thiết lập và tự động nạp managed source

### Mục tiêu UX bắt buộc

UI phải cho phép user hoàn tất toàn bộ flow trong app, không yêu cầu copy URL localhost hoặc tự mở màn hình server WebDAV cũ:

1. Mở màn hình “Thư viện Drive/WebDAV local” từ Settings hoặc mục nhập sách từ xa.
2. Chọn một trong hai chế độ: Google account hoặc Public link.
3. Cấu hình resource: account/folder gốc cho chế độ Google; URL public cho chế độ link.
4. Đặt tên source, tùy chọn thư mục gốc và xem trạng thái kiểm tra kết nối.
5. Bấm kết nối; app tự start Foreground Service, tạo/cập nhật managed source, chọn source hiện hành và mở catalog.
6. Từ catalog, user duyệt folder, chọn file, tải về, nhập vào bookshelf và mở reader.
7. Khi quay lại, source đã tạo xuất hiện trong danh sách để reconnect, đổi source, refresh hoặc disconnect.

Không mặc định tải toàn bộ thư viện sau khi kết nối. “Tự động nạp vào nguồn” nghĩa là tự tạo source và nạp catalog; việc tải file phải do user chọn hoặc bấm “Tải tất cả” sau một bước xác nhận dung lượng/network.

### Màn hình Compose đề xuất

Mở rộng feature với các file/route sau:

- DriveWebDavSetupContract.kt: state kết nối, mode, input link, account, root folder, source name, auto-open-catalog, loading/error/dialog.
- DriveWebDavSetupViewModel.kt: xử lý intent kết nối, dán link, chọn folder, start/stop, reconnect, refresh và mở catalog.
- DriveWebDavSetupScreen.kt: UI thiết lập stateless theo MVI/UDF.
- DriveWebDavSourceListScreen.kt hoặc sheet nhỏ: danh sách managed sources, source đang active, status và thao tác edit/delete/reconnect.
- DriveWebDavRouteScreen.kt: nhận Activity Result của Google authorization và folder picker; route/entry provider điều hướng tới RemoteBookScreen.

UI tối thiểu gồm:

- Segmented control/radio chọn Google Drive hoặc Public link.
- Nút “Đăng nhập/kết nối Google Drive”, hiển thị account label không nhạy cảm.
- Text field “Dán link thư viện công khai”, nút paste và preview provider/resource.
- Folder picker hoặc root folder ID/name cho tài khoản Google.
- Text field tên source, mặc định lấy tên account/folder/provider.
- Card trạng thái: Chưa kết nối, Đang xác thực, Đang khởi động, Đã sẵn sàng, Cần cấp quyền, Link không hợp lệ, Offline, Lỗi.
- Nút Start/Stop/Reconnect/Open catalog.
- Danh sách source đã tạo và last connected/last catalog load.

Tuân thủ pattern Compose của repository: route thu thập StateFlow bằng collectAsStateWithLifecycle, effect bằng LaunchedEffect, UI không gọi JNI/DAO/authorization trực tiếp, state list dùng ImmutableList và UiState có @Stable. Dùng MainActivity/Navigation3 cho route mới, AppScaffold/setting components hiện có và Scaffold insets cho edge-to-edge.

### Luồng Google account → source → catalog

```text
User chọn Google Drive
        |
        v
Authorization bridge: account + scope + access token hiện hành
        |
        v
Drive root/folder resolver: xác nhận folder ID và quyền list/read
        |
        v
Start DriveWebDavService(mode=AUTHENTICATED_DRIVE)
        |
        v
Nhận loopback URL + session auth (nếu bật)
        |
        v
ManagedSourceRegistry.ensure(sourceKey)
        |
        +--> upsert Server row
        +--> set ImportBookConfig.remoteServerId
        +--> persist active source metadata
        |
        v
Mở RemoteBookScreen với serverId/root path
        |
        v
PROPFIND catalog -> chọn file -> download/import -> mở reader
```

Chi tiết:

1. Authorization bridge trả account identifier, scope result và access token; không đưa token vào UiState.
2. Resolver gọi một request metadata/list nhỏ để xác nhận root/folder trước khi báo thành công.
3. Service khởi động server read-only và trả port thực tế.
4. Tạo source key ổn định từ provider + account identifier đã băm/ẩn danh + root folder ID; không dùng raw email hoặc token làm key.
5. Registry tìm source cũ theo source key. Nếu có thì cập nhật endpoint/last status; nếu chưa có thì tạo mới.
6. Server row được tạo/cập nhật để RemoteBookRepository.createWebDav(serverId) có thể dùng lại. URL động của localhost không được coi là định danh source.
7. Set ImportBookConfig.remoteServerId và route sang catalog; catalog tự load ở root folder.
8. Khi user chọn file, giữ metadata source path/serverId trong Book.origin bằng format hiện có để refresh/re-download sau này.

### Luồng public link → source → catalog

1. User dán URL public.
2. DriveLinkResolver canonicalize URL, loại query tracking, nhận diện provider và tách resource ID.
3. Với Google Drive, hỗ trợ tối thiểu folder share URL và file share/download URL. Kiểm tra link thật sự public bằng metadata/list hoặc HEAD/GET có giới hạn.
4. Nếu là public folder, tạo root catalog và list các file con. Nếu là public file, tạo catalog một phần tử hoặc mở thẳng bước download.
5. Nếu provider yêu cầu API key để list public metadata, key phải là build config/public quota key và cần rate limit; không coi API key là secret. Nếu không có cách list an toàn, chỉ hỗ trợ direct public file hoặc báo unsupported.
6. Start service với mode PUBLIC_LINK và public resource descriptor; Go không nhận token.
7. Tạo source key từ provider + canonical resource ID + root path. Hai lần dán cùng link phải upsert cùng source, không tạo bản ghi trùng.
8. Set active source, mở catalog và cho phép download/import/read như chế độ Google account.
9. Nếu link không public, link hết hạn, HTML preview không có direct resource, file quá lớn cần confirmation hoặc quota bị chặn, hiển thị lỗi phân loại và gợi ý đăng nhập Google/đổi link; không loop retry.

### ManagedSourceRegistry và quan hệ với BookSource

Đề xuất thêm domain model/repository nhỏ:

- ManagedDriveSource: sourceId/sourceKey, displayName, provider, mode, resourceId/rootPath, serverId, enabled, lastStatus, lastErrorCode, lastConnectedAt.
- ManagedSourceRegistry: ensure/upsert, list, selectActive, remove, reconnect và update endpoint.
- DriveWebDavConnectionUseCase: resolve input, authorize nếu cần, start service, upsert source, chọn source và trả catalog target.

MVP ưu tiên dùng Server hiện có làm endpoint persistence vì RemoteBook flow đã dùng ServerDao và ImportBookConfig.remoteServerId. Có thể lưu sourceKey/provider/mode/root metadata trong registry DataStore hoặc phần metadata JSON versioned của Server.config; không lưu access/refresh token trong Server.config. Nếu sau này cần nhiều source, registry phải phân biệt source key ổn định với port/session endpoint tạm thời.

Không tạo BookSource rule-based giả chỉ để biến thư mục Drive thành “nguồn sách”. BookSource hiện tại cần các rule search/book info/toc/content, trong khi Drive catalog là danh sách file. UI có thể gọi rõ là “Nguồn thư viện Drive” và hiển thị trong source selector/remote import. Nếu yêu cầu sản phẩm bắt buộc xuất hiện trong màn hình BookSource tiêu chuẩn, phải thêm adapter `BookSourceType.file` hoặc loại provider riêng, cùng contract search/catalog/download, và đưa vào scope/estimate riêng sau spike.

### Catalog, tải về và đọc

- Catalog dùng lại RemoteBookScreen/RemoteBookViewModel/RemoteBookWebDav.
- Folder mở bằng PROPFIND và breadcrumb; file được lọc theo bookFileRegex/archiveFileRegex hiện có.
- Nút “Thêm vào bookshelf” tải file qua WebDAV, dùng LocalBook.importFiles, gắn origin webDav:: với serverId/path và lưu Book.
- File đã có trên bookshelf hiển thị trạng thái; click mở sách hiện có; nút update/re-import gọi lại download flow.
- Archive giữ flow hiện tại: tải archive, hiển thị entry phù hợp, import entry rồi mở reader.
- File tải xong phải dùng storage URI/persisted tree hiện có; nếu thiếu quyền lưu, phát Activity Effect yêu cầu chọn book folder.
- Không auto-download toàn bộ catalog; nếu bổ sung “Tải tất cả”, cần ước lượng dung lượng, confirm Wi-Fi/storage và dùng hàng đợi download hiện có.
- Khi service/token mất kết nối, catalog hiện trạng được giữ để hiển thị nhưng thao tác list/download phải trả lỗi reconnect/auth required, không làm hỏng sách local đã nhập.

### Idempotency và lỗi

- Bấm Connect nhiều lần không tạo nhiều service/source/server row.
- Cùng account + root hoặc cùng public resource phải resolve tới cùng source key.
- Đổi root folder tạo source key khác; không đổi nhầm source cũ.
- Disconnect chỉ dừng service và gỡ active selection theo lựa chọn; không xóa Book đã tải nếu user chưa xác nhận.
- Delete source phải cảnh báo rằng sách local vẫn giữ nguyên nhưng không còn tự refresh từ remote.
- Mọi failure cần mã phân loại: AUTH_REQUIRED, LINK_INVALID, LINK_NOT_PUBLIC, PROVIDER_UNSUPPORTED, ROOT_NOT_FOUND, SERVICE_START_FAILED, CATALOG_LOAD_FAILED, DOWNLOAD_FAILED, STORAGE_PERMISSION_REQUIRED.

## 9. Compose, ViewModel, DI và navigation

### Contract

DriveWebDavContract.kt dự kiến cho trạng thái runtime/service; phần setup nên dùng contract riêng DriveWebDavSetupContract.kt nếu màn hình có nhiều input và Activity Result:

- @Stable data class DriveWebDavUiState:
  - connectionState;
  - accountLabel không nhạy cảm;
  - rootFolderName/rootFolderId nếu cần;
  - localUrl hoặc port khi service đang chạy;
  - loading, errorMessage, activeDialog.
- sealed interface DriveWebDavIntent:
  - ConnectGoogleDrive;
  - Disconnect;
  - StartService;
  - StopService;
  - RefreshStatus;
  - SelectRootFolder nếu scope MVP bao gồm chọn folder.
- sealed interface DriveWebDavEffect:
  - RequestAuthorization;
  - RequestPublicLinkResolution;
  - RequestRootFolderPicker;
  - OpenManagedSourceCatalog(serverId, rootPath);
  - ShowToast;
  - NavigateToWebDavImport nếu cần;
  - OpenSystemSettings chỉ khi thực sự cần.

### ViewModel

DriveWebDavViewModel:

- extend ViewModel trực tiếp;
- expose StateFlow và SharedFlow(extraBufferCapacity = 16);
- có một onIntent() duy nhất;
- gọi repository/controller, không gọi JNI trực tiếp trong composable;
- collect service status bằng flow nếu có, hoặc polling có backoff giới hạn;
- không đưa access token vào UiState.

### Screen và route

DriveWebDavScreen/DriveWebDavSetupScreen là stateless, dùng component UI hiện có như AppScaffold, GlassMediumFlexibleTopAppBar, AppAlertDialog, NormalCard, SettingCard và callback navigation. RouteScreen hoặc entry provider xử lý activity result từ Google authorization, public-link paste/share intent và folder picker.

Màn hình setup phải có đủ hai nhánh input, không chỉ hiển thị port WebDAV: Google account login, public URL, root folder, source name, trạng thái, thao tác start/stop/reconnect và nút mở catalog. Sau Effect OpenManagedSourceCatalog, route truyền serverId/rootPath vào luồng RemoteBook hiện có; không để user nhập lại localhost URL.

Nếu feature chỉ là một phần của settings, có thể nhúng screen vào settings route hiện có; nếu có lifecycle/service state đáng kể, route riêng sẽ dễ kiểm thử hơn. Quyết định này nên được chốt sau khi xác định UX final, không ảnh hưởng lõi Go.

### DI

Đăng ký các thành phần:

- DriveAuthorizationGateway/bridge adapter;
- DriveLinkResolver và PublicDriveRepository/adapter;
- ManagedSourceRegistry;
- DriveWebDavConnectionUseCase;
- DriveWebDavServiceController;
- DriveWebDavRepository hoặc DriveWebDavController;
- DriveWebDavViewModel.

Chỉ expose interface ở domain nếu thành phần đó có behavior cần mock/test hoặc được dùng bởi nhiều UI. Không tạo thêm abstraction chỉ để bọc một call JNI đơn lẻ.

## 10. Đóng gói Gomobile và build

### Toolchain

Cần pin và ghi rõ:

- Go version;
- gomobile version/commit;
- Android SDK/NDK version;
- JDK/Gradle compatibility;
- module versions trong go.mod;
- checksum của dependencies.

Build pipeline dự kiến:

1. go mod download/verify.
2. Cài hoặc kiểm tra gomobile và gobind.
3. gomobile init với Android NDK phù hợp.
4. gomobile bind -target=android cho package bind.
5. Sinh AAR và kiểm tra các ABI.
6. Đưa AAR vào dependency nội bộ hoặc publish vào local Maven directory của app.
7. Chạy Kotlin compile và test.
8. Assemble debug/release/noR8 theo ma trận ABI.

Không commit artifact build lớn nếu repository không có convention đó. Commit source Go, script reproducible, metadata version và hướng dẫn setup. Nếu CI không có Go/Gomobile, thêm bước cài toolchain hoặc build AAR ở một job riêng rồi cache artifact có checksum.

### AAR integration

Có hai phương án cần đánh giá ở Phase 0:

- Gomobile AAR được build trước và app dùng files/local Maven artifact.
- Gradle task gọi script Gomobile trong quá trình build.

Khuyến nghị bắt đầu bằng script reproducible + local generated artifact để giảm coupling Gradle; sau khi ổn định mới tích hợp CI task. Cần xác nhận license và cách phân phối các dependency Go.

### ABI và kích thước

Ước lượng ban đầu, cần đo bằng artifact thật:

- split APK: tăng khoảng 5–10 MB cho mỗi ABI là khả năng cần dự phòng;
- universal APK: tăng khoảng 15–30 MB;
- native RSS/runtime: khoảng 10–30 MB tùy dependency và build flags.

Đây là budget planning, không phải số đo cuối. Sau prototype phải đo size, APK Analyzer, startup time, RSS và cold start service. Có thể giảm kích thước bằng cách tránh dependency nặng, build tags và chỉ bind API cần thiết; không dùng strip/obfuscation nếu làm hỏng symbol cần cho Gomobile.

### R8/keep rules

Kiểm tra:

- class Java package sinh bởi Gomobile;
- native method registration;
- consumer ProGuard rules trong AAR;
- reflection/serialization nếu bridge dùng;
- crash khi chỉ release minified.

Release smoke test bắt buộc có một lần start/stop service và một request WebDAV thật.

## 11. Kế hoạch triển khai theo phase

### Phase 0 — Chốt quyết định và spike (2–4 ngày)

- Xác nhận OAuth scope, account model và decision gate refresh token.
- Kiểm tra version Go/Gomobile/NDK khả dụng trên máy dev và CI.
- Tạo Go hello-world bind AAR, gọi được từ một test/Activity tối thiểu.
- Đo kích thước AAR và kiểm tra ABI hiện hữu.
- Kiểm tra WebDav.kt cần chính xác method/headers/status nào.
- Chốt port strategy và threat model localhost.

Kết quả: ADR ngắn, AAR spike chạy được, danh sách blocker được giải quyết.

### Phase 1 — Go Drive filesystem offline (4–7 ngày)

- Tạo module Go và package layout.
- Implement Drive client với interface mockable.
- Implement metadata/path mapping, pagination, root folder.
- Implement reader seek/range và error mapping.
- Viết unit test với fake Drive HTTP server.
- Không tích hợp Android ở phase này.

Kết quả: filesystem adapter đọc được fixture và file lớn theo range.

### Phase 2 — WebDAV server read-only (3–5 ngày)

- Gắn adapter vào x/net/webdav hoặc handler tương đương.
- Implement OPTIONS, PROPFIND, GET, HEAD.
- Reject write methods.
- Bind loopback, port 0/port configured, graceful shutdown.
- Viết WebDAV protocol tests và test bằng client tương thích.

Kết quả: server Go chạy độc lập, test được bằng HTTP/WebDAV client.

### Phase 3 — Gomobile bridge/AAR (3–5 ngày)

- Thiết kế public API bind ổn định.
- Build cho armeabi-v7a, arm64-v8a, x86_64.
- Tạo script build/checksum/documentation.
- Tạo adapter Kotlin mỏng, mapping lifecycle/error.
- Kiểm tra thread safety và update token.

Kết quả: Kotlin start/update-token/stop được native server.

### Phase 4 — Android service và authorization (4–7 ngày)

- Implement DriveWebDavService và notification.
- Thêm manifest/permission/foreground type.
- Tích hợp Google authorization bridge.
- Implement silent re-authorization và auth-required state.
- Test process death, rotation, stop/restart, token expiry.

Kết quả: service sống qua màn hình tắt trong điều kiện hỗ trợ và xử lý token rõ ràng.

### Phase 5 — Tích hợp remote book và UI (4–7 ngày)

- Tạo controller/repository và DI.
- Tạo setup contract/ViewModel/screen/route theo MVI/UDF.
- Tạo UI chọn Google account/public link, root folder, source name, start/stop/reconnect và source list.
- Tạo DriveLinkResolver cho Google Drive folder/file public link và kiểm tra public access.
- Tạo ManagedSourceRegistry/connection use case; upsert Server row, active server ID và source metadata idempotently.
- Tự động route tới RemoteBookScreen sau khi source sẵn sàng, không bắt user cấu hình localhost thủ công.
- Tạo endpoint config dùng lại remote WebDAV flow.
- Thêm chọn root folder nếu vẫn trong MVP.
- Kiểm thử chọn file, tải/import, archive, bookshelf và mở reader; giữ origin/serverId để refresh.
- Hiển thị lỗi thân thiện, không lộ secret.

Kết quả: người dùng kết nối Drive hoặc public link, source được tạo/nạp tự động, catalog mở ngay, file được tải/import và đọc bằng flow hiện có.

### Phase 6 — Kiểm thử thiết bị, hiệu năng và bảo mật (4–7 ngày)

- Matrix Android API gần min, target và một thiết bị arm64 thật.
- Debug/release/noR8, từng ABI và universal nếu phát hành.
- Kiểm thử file nhỏ/lớn, nhiều thư mục, pagination, offline, 401/403/429/5xx.
- Đo startup, memory, battery/network behavior.
- Kiểm tra logcat, notification, process recreation và port collision.
- Review token handling và threat model localhost.

Kết quả: report kiểm thử và danh sách issue trước release.

### Phase 7 — CI/release và tài liệu (2–4 ngày)

- Pin toolchain, cache Go modules và Gomobile.
- Tích hợp build artifact vào CI.
- Cập nhật README/developer setup/license notices.
- Thêm migration/rollback note nếu sau này chọn Room.
- Chuẩn bị release checklist và known limitations.

Kết quả: build tái lập được và người phát triển khác có thể setup.

## 12. Danh sách file dự kiến

### Tạo mới

- native/go-webdav/go.mod
- native/go-webdav/go.sum
- native/go-webdav/bind/bridge.go
- native/go-webdav/server/...
- native/go-webdav/drive/...
- tools/go-webdav-android/build.ps1
- tools/go-webdav-android/README.md
- app/src/main/java/io/legado/app/.../DriveWebDavService.kt
- app/src/main/java/io/legado/app/.../DriveWebDavContract.kt
- app/src/main/java/io/legado/app/.../DriveWebDavViewModel.kt
- app/src/main/java/io/legado/app/.../DriveWebDavScreen.kt
- app/src/main/java/io/legado/app/.../DriveWebDavRouteScreen.kt nếu cần
- app/src/main/java/io/legado/app/.../DriveWebDavSetupContract.kt nếu tách setup khỏi runtime state
- app/src/main/java/io/legado/app/.../DriveLinkResolver.kt
- app/src/main/java/io/legado/app/.../ManagedDriveSource.kt
- app/src/main/java/io/legado/app/.../ManagedSourceRegistry.kt
- app/src/main/java/io/legado/app/.../DriveWebDavConnectionUseCase.kt
- unit/integration tests tương ứng

### Có thể sửa

- app/src/main/AndroidManifest.xml
- app/build.gradle.kts hoặc dependency management tương ứng
- app/proguard-rules.pro/consumer rules nếu cần
- app/src/main/java/io/legado/app/di/appModule.kt
- MainNavKey.kt, MainNavGraph.kt
- ConfigNavScreen.kt, ConfigTag.kt
- GoogleDriveAuthorizationBridge.kt hoặc adapter mới quanh nó
- RemoteBookWebDav.kt/factory config nếu cần endpoint localhost
- RemoteBookViewModel.kt/RemoteBookRepository.kt để nhận managed server/root target và xử lý public-link mode
- RemoteBookScreen.kt nếu cần nhận catalog target từ setup route hoặc đổi nhãn source/catalog
- Server.kt/ServerDao.kt chỉ khi cần query sourceKey/versioned metadata; ưu tiên không đổi schema Room nếu JSON/DataStore đủ
- ImportBookConfig.kt để chọn active managed server
- string resources tương ứng cho setup/status/error/source actions
- notification setup nếu convention hiện tại yêu cầu

### Không nên sửa trong MVP

- AppDatabase.kt và migration chỉ để lưu một active config.
- WebService.kt, trừ khi có lý do hạ tầng dùng chung đã được chứng minh.
- toàn bộ RemoteBook architecture để tạo một đường đọc Drive thứ hai.
- các dependency nhạy cảm như jsoup/hutool; không liên quan và bị pin bởi repository.

## 13. Ma trận kiểm thử

### Go unit tests

- path -> Drive ID với tên Unicode, space, slash encoding;
- root folder và folder không tồn tại;
- pagination và duplicate names;
- metadata file/folder, modified time, size;
- range 0–N, suffix range, range ngoài kích thước;
- token update concurrent với request;
- Google account/root folder resolver và source key ổn định;
- public Google Drive folder/file link parsing, canonicalization và public-access validation;
- 401/403/404/429/5xx mapping;
- graceful stop khi request đang chạy;
- reject write methods.

### JVM/Kotlin tests

- state transition ViewModel;
- authorization success/cancel/revoke;
- service controller start/stop/update token idempotent;
- setup state cho Google/public-link mode, link invalid/not-public và auth-required;
- ManagedSourceRegistry upsert idempotency, active source selection và port refresh;
- không đưa token vào UiState/effect/log adapter;
- port/status/error mapping;
- service restart sau process recreation theo policy.

### Android/instrumentation

- notification channel và startForeground đúng thời điểm;
- foreground service type/permission trên API mục tiêu;
- start từ UI foreground và behavior khi app background;
- token expiry rồi silent authorization;
- setup UI từ Google login/public link đến open catalog;
- auto-created source xuất hiện trong source/server selector và được chọn đúng;
- mở thư mục và file qua RemoteBookWebDav;
- tải/import file, archive entry, mở reader và lưu remote origin/serverId;
- stop service khi import xong hoặc người dùng tắt;
- process death/Doze trong giới hạn test được;
- release R8/native loading trên từng ABI.

### Manual acceptance

- Google account đã cấp quyền: reconnect không hiện consent lại ngoài dự kiến;
- revoke quyền trên Google: app chuyển sang auth required, không loop;
- dán lại cùng Google/public link không tạo duplicate source;
- public folder hiển thị catalog, public file hiển thị singleton catalog hoặc download target;
- link share yêu cầu login/quota/HTML preview bị từ chối rõ ràng, không scraping vô hạn;
- file lớn mở/đọc được mà RAM không tăng theo toàn bộ file;
- đổi root folder không đọc nhầm dữ liệu ngoài root;
- cổng localhost không truy cập được từ thiết bị khác trong LAN;
- lỗi mạng được hiển thị ngắn gọn và có đường retry.

## 14. Ước lượng effort và chi phí kỹ thuật

Ước lượng cho một kỹ sư đã quen Android/Go, chưa tính thời gian chờ Google OAuth verification hoặc thay đổi policy nền tảng:

- spike và quyết định: 2–4 ngày;
- Go Drive/WebDAV core: 7–12 ngày;
- Gomobile/API/AAR: 3–5 ngày;
- Android service + auth: 4–7 ngày;
- UI/setup/managed-source/remote integration: 6–10 ngày;
- public-link resolver và catalog edge cases: 3–6 ngày;
- test/performance/security/CI: 7–10 ngày.

Tổng sau khi bổ sung UI thiết lập, auto source và public-link flow: khoảng **30–51 engineer-days**, sai số planning ban đầu **±30%**. Nếu chỉ hỗ trợ Google account + một loại Google public link và không cần nhiều provider, có thể nằm ở gần cận dưới. Nếu phải có backend để exchange authorization code và quản lý refresh token, cộng thêm phần thiết kế/vận hành backend và review bảo mật; đó là một scope riêng, không nên ẩn trong estimate Android.

## 15. Rủi ro và phương án giảm thiểu

| Rủi ro | Mức độ | Giảm thiểu |
|---|---:|---|
| Gomobile API không bind được kiểu Go dự kiến | Cao | Spike API tối thiểu ở Phase 0; chỉ expose primitive/object đơn giản |
| Native AAR làm tăng kích thước APK | Trung bình | Dùng dependency Go tối thiểu, đo từng ABI, xem xét split APK |
| R8 loại class/native registration | Cao | Kiểm tra release sớm, thêm keep/consumer rules theo artifact thật |
| Google authorization không cung cấp refresh token theo kỳ vọng | Cao | Chốt decision gate; MVP dùng silent auth; không nhúng client secret |
| Android hạn chế background FGS start | Cao | Chỉ start từ user action hợp lệ, kiểm tra target SDK/policy hiện tại |
| Doze/network làm request chậm hoặc bị ngắt | Trung bình | timeout, retry bounded, foreground notification, không hứa đảm bảo tuyệt đối |
| WebDAV client hiện tại cần method/response khác x/net/webdav | Cao | Protocol contract test trước khi tích hợp UI |
| Public share URL chỉ là HTML preview, không có API/list/download ổn định | Cao | resolver theo provider; hỗ trợ folder/file contract rõ ràng; báo unsupported thay vì scraping |
| Tạo source trùng hoặc mất liên kết sau restart vì port thay đổi | Cao | sourceKey ổn định theo resource; port chỉ là endpoint tạm; upsert Server/registry idempotently |
| Nhầm managed Drive source với BookSource rule-based | Trung bình | dùng Server + RemoteBook làm canonical MVP; chỉ thêm BookSource adapter khi có yêu cầu sản phẩm rõ |
| Google Drive native docs/shortcut không đọc được | Trung bình | tài liệu hóa limitation, mapping lỗi rõ ràng, bổ sung hỗ trợ sau MVP |
| Localhost endpoint bị app khác trên máy truy cập | Trung bình | loopback, session secret nếu threat model cần, không mở LAN |
| Process chết làm mất server state | Trung bình | service state persisted tối thiểu, restart có kiểm soát, UI phản ánh disconnected |
| API/policy Google thay đổi | Trung bình | cô lập authorization bridge, pin scope và test contract |

## 16. Các câu hỏi cần chốt trước khi code

1. Người dùng chỉ cần đọc file/folder hay cần upload/sửa/xóa ngay từ phiên bản đầu?
2. Scope Drive mong muốn là toàn bộ Drive, drive.file, hay một thư mục do người dùng chọn?
3. Có backend riêng để exchange server auth code và lưu refresh token không?
4. Tính năng có cần chạy tự động sau reboot không, hay chỉ chạy khi người dùng bật?
5. Endpoint localhost có cần password/session secret để chống app khác trên cùng thiết bị không?
6. Người dùng có cần nhiều account/root folder hay chỉ một active connection?
7. Port cố định có phải compatibility requirement của WebDav.kt không?
8. Có phát hành universal APK hay chỉ split APK theo ABI?
9. “Nạp vào nguồn sách” có bắt buộc xuất hiện trong BookSource rule-based hay chấp nhận managed Drive source trong danh sách WebDAV/remote library?
10. Public link cần hỗ trợ folder catalog, file đơn lẻ, hay cả hai? Có provider nào ngoài Google Drive cần cam kết không?
11. Sau khi tạo source, app chỉ tự mở catalog hay có thêm tùy chọn tải toàn bộ thư viện?

Nếu câu 3 chưa có câu trả lời tích cực, không đưa persistent refresh token vào MVP.

## 17. Bổ sung NMT: convert HachimiMT-60-QT sang ONNX và chọn model

### 17.1. Đặc tính model nguồn và quyết định phạm vi

Nguồn cần convert: https://huggingface.co/ngocdang83/HachimiMT-60-QT

Thông tin cần khóa vào manifest/provenance sau khi pin revision:

- Model là Marian encoder-decoder cho Chinese → Vietnamese, khoảng 56.96M tham số và checkpoint F32.
- License trên model card là CC-BY-4.0; gói phân phối phải kèm attribution/NOTICE và không được xóa license upstream.
- Model card có quick start bằng MarianMTModel/Transformers và có sẵn một hướng CTranslate2 INT8 để tham khảo hiệu năng CPU; CTranslate2 không thay thế artifact ONNX mà chỉ là reference/benchmark.
- Đây là bản QT/convert register có chủ đích. Model card cảnh báo không dùng no_repeat_ngram_size vì có thể làm hỏng tên riêng; decode profile của model này phải mặc định tắt no-repeat bigram.
- Target language của NMT hiện chỉ là Vietnamese; không mở rộng UI thành nhiều ngôn ngữ khi chưa có checkpoint tương ứng.

Mặc định giữ model Hachimi zh-vi hiện tại và thêm HachimiMT-60-QT thành model thứ hai. Không overwrite thư mục/model ID cũ, vì người dùng có thể đang dùng model cũ và artifact hiện tại có provenance khác.

### 17.2. Hợp đồng artifact ONNX

Artifact QT phải tương thích với contract mà HachimiOnnxTranslator đang dùng:

- encoder_model.onnx;
- decoder_model_merged.onnx;
- tokenizer.onnx cho source tokenizer;
- target_tokenizer.onnx cho lexical constraints/target terms;
- detokenizer.onnx;
- model_manifest.json;
- NOTICE.txt/LICENSE/README tùy license và provenance.

Manifest phải có schemaVersion, modelId, displayName, sourceRepo, sourceRevision, sourceLanguage, targetLanguage, style/register, license, attribution, requiredFiles, per-file SHA-256, export tool versions, opset, quantization type, decoder graph contract và recommended decode defaults.

Tên model ID đề xuất: hachimi_mt60_qt_zh_vi. Tên artifact delivery đề xuất: translation-hachimi-qt-onnx. Tên cụ thể chỉ được khóa sau khi artifact thật được build và đo size/hash; không ghi hash dự đoán vào source.

### 17.3. Pipeline convert tái lập được

Tạo pipeline ngoài Android source, không convert thủ công trên máy cá nhân:

1. Pin Python, PyTorch, Transformers, Optimum ONNX, ONNX, ONNX Runtime, ONNX Runtime Extensions, SentencePiece, safetensors và huggingface_hub trong requirements lock/pyproject.
2. Tải snapshot đúng revision của ngocdang83/HachimiMT-60-QT bằng allowlist file cần thiết; ghi source revision và SHA-256 vào build report.
3. Load AutoTokenizer và MarianMTModel/AutoModelForSeq2SeqLM, chạy reference inference bằng Transformers trên bộ câu kiểm thử Chinese → Vietnamese.
4. Export encoder và decoder bằng Optimum ONNX exporter cho Marian seq2seq, có past key/value ở decoder. Opset và dynamic axes phải được chọn theo ONNX Runtime Android version đang dùng và được kiểm tra bằng onnx.checker.
5. Post-process/merge decoder để đạt contract decoder_model_merged.onnx hiện tại: input token, encoder hidden, attention mask, use_cache_branch, empty past ở bước đầu và cache output có tên/shape tương thích. Nếu Optimum sinh decoder/decoder-with-past với tên khác, viết adapter export hoặc cập nhật runtime theo model manifest; không sửa tên bằng cách không kiểm tra graph.
6. Sinh tokenizer.onnx và target_tokenizer.onnx từ tokenizer/SentencePiece của checkpoint bằng ONNX Runtime Extensions hoặc pipeline preprocessing tương thích. Graph phải nhận input text và trả input_ids/attention_mask đúng tên mà runtime hiện có sử dụng.
7. Sinh detokenizer.onnx từ target tokenizer/SentencePiece, nhận ids và trả text. Kiểm tra Unicode, dấu câu, khoảng trắng, token special và tên riêng.
8. Export FP32 trước để làm golden reference. Sau khi parity đạt, tạo INT8 release candidate bằng ONNX Runtime/Optimum quantization phù hợp cho Marian encoder-decoder. Giữ FP32 artifact làm fallback/debug nếu INT8 làm giảm chất lượng hoặc gây lỗi graph trên Android.
9. Đóng gói đúng whitelist, tạo model_manifest.json/NOTICE.txt, tính per-file SHA-256 và ZIP SHA-256, sau đó chạy validation từ đầu trong môi trường sạch.

Không dùng CTranslate2 artifact để đổi tên thành ONNX. CTranslate2 chỉ dùng làm benchmark/reference phụ; runtime Android của plan vẫn là ONNX Runtime + ONNX Runtime Extensions.

### 17.4. Validation bắt buộc trước khi đưa vào asset catalog

Validation có ba lớp:

#### Python/reference parity

- So sánh output Transformers FP32 và ONNX FP32 trên câu ngắn, câu dài, dấu câu, xuống dòng, tên riêng, thuật ngữ từ điển và mẫu webnovel.
- So sánh token IDs/stop token/độ dài output trong phạm vi decoder strategy tương đương; không yêu cầu text giống tuyệt đối nếu Android runtime greedy còn reference dùng beam.
- Đo sai khác FP32 → INT8, BLEU/chrF hoặc bộ đánh giá nội bộ và đánh giá thủ công các tên riêng/pronoun/register.
- Kiểm tra model QT giữ convert register, không tự động bật no-repeat bigram trong profile mặc định.

#### Graph/runtime validation

- onnx.checker cho mọi graph.
- Kiểm tra input/output names, dtypes, dynamic dimensions, past key/value layer count, attention heads/head dimension và special token IDs.
- Tạo OrtSession bằng đúng onnxruntime/onnxruntime-extensions version của app.
- Test memory map, external data nếu có, graph optimization BASIC/ALL và low-RAM session profile.

#### Android device validation

- Chạy qua NmtOnnxService thật trên arm64 ít nhất một thiết bị và một emulator x86_64 nếu artifact hỗ trợ.
- Kiểm tra import atomic, switch model khi request đang chạy, process death/rebind, cancel, timeout và OOM recovery.
- Đo cold load, time-to-first-segment, tokens/sec, RSS/native memory và dung lượng model.
- Regression test model cũ sau khi runtime được parameter hóa.


### 17.5. Catalog nhiều model và runtime selection

Thay registry cố định bằng catalog descriptor:

    data class NmtModelDescriptor(
        val id: String,
        val displayName: String,
        val sourceRepo: String,
        val sourceRevision: String,
        val sourceLanguage: String,
        val targetLanguage: String,
        val style: String,
        val artifactId: String,
        val requiredFiles: List<String>,
        val license: String,
        val attribution: String,
        val recommendedNoRepeatNgramSize: Int,
        val supportsSourcePrompt: Boolean,
    )

Catalog MVP có tối thiểu:

- hachimi_onnx hoặc ID tương thích với thư mục cũ: model Hachimi zh-vi hiện tại;
- hachimi_mt60_qt_zh_vi: HachimiMT-60-QT ONNX, QT/convert register, no-repeat mặc định bằng 0.

Thay đổi code dự kiến:

- HachimiOnnxModelRegistry: nhận modelId, cài ở filesDir/nmt_models/<modelId>, kiểm tra manifest/required files và trả descriptor.
- HachimiOnnxModelImporter: chuyển thành importer generic theo manifest; vẫn nhận ZIP cũ để backward compatibility hoặc migrate ID cũ không phá dữ liệu.
- HachimiOnnxTranslator: translate nhận modelId; Runtime cache key gồm modelId + generation; Runtime.load(context, descriptor) lấy filenames/decoder constants từ manifest hoặc descriptor thay vì hard-code một model.
- HachimiOnnxRuntimeCoordinator: đóng runtime cũ dưới mutex trước khi switch model; không để hai bộ graph cùng giữ native memory nếu thiết bị low-RAM.
- NmtDecodeConfig: thêm modelId hoặc NmtModelSelection, version hóa JSON IPC để NmtOnnxService biết model nào cần nạp. Không truyền đường dẫn tùy ý từ UI vào process.
- TranslateChapterUseCase.currentNmtDecodeConfig(): lấy TranslationConfig.nmtModelId và model profile rồi truyền xuống cả luồng dịch chapter và luồng dynamic/preview.
- providerConfigurationRevision(): bao gồm modelId, artifact generation và decode config để cache translation không dùng nhầm output của model khác.
- Attribution trong NmtTranslationResult: lấy từ descriptor; không hard-code attribution HachimiMT-60 cho mọi model.

### 17.6. Model option trong Translation settings

Mở rộng TranslationConfigScreen hiện có, không tạo một màn hình model độc lập nếu không cần:

- Thêm PreferKey.nmtModelId và TranslationConfig.nmtModelId, default giữ model đang cài/đang dùng để không phá upgrade.
- Khi provider là NMT, thêm DropdownListSettingItem/selector hiển thị tên model, style, ngôn ngữ và trạng thái installed/missing.
- Khi user chọn model chưa cài, hiển thị action Download/Import model tương ứng và không đổi active model cho tới khi import/validation thành công.
- Cho phép mở metadata: upstream repo/revision, license, size, checksum và attribution.
- Decode settings hiển thị recommended value theo selected model. Với HachimiMT-60-QT, no-repeat bigram mặc định tắt; nếu user bật thủ công phải có cảnh báo model card không khuyến nghị.
- Giữ target language ở Vietnamese cho mọi model NMT hiện có.
- Nếu một model thay đổi source prompt support hoặc token budget, clamp settings theo descriptor và hiển thị summary; không để config cũ gây crash.
- Khi đổi model trong lúc NMT request chạy, giữ request hiện tại chạy tới kết thúc hoặc cancel an toàn, sau đó invalidate runtime generation; không hot-swap session giữa các decoder step.

Nếu tách model picker thành route/sheet behavior-heavy, dùng NmtModelPickerContract.kt, NmtModelPickerViewModel.kt và NmtModelPickerScreen.kt theo MVI/UDF; state dùng ImmutableList/@Stable, effects dùng SharedFlow, route do MainActivity/TranslationConfig host xử lý. Nếu chỉ là một dropdown + import action, giữ thay đổi surgical trong TranslationConfigScreen nhưng không đưa file I/O/JNI vào composable.

### 17.7. External asset delivery và provenance

- Thêm artifact mới vào ExternalAssetCatalog với ID translation-hachimi-qt-onnx; giữ artifact cũ độc lập.
- Mở rộng AssetDeliveryImportRepository để route artifact ID tới generic NmtOnnxModelImporter và chọn modelId từ manifest.
- Cập nhật scripts/build-hf-asset-manifest.ps1, supabase/artifacts/hf-artifacts-manifest.json và mirror path trong repository Drduc/Legadofork.
- Provenance phải ghi rõ upstream ngocdang83/HachimiMT-60-QT, revision, công cụ export, quantization, license và source checksum; không gắn nhầm provenance HachimiMT-60-zh-vi.
- Chỉ đưa artifact vào release catalog sau khi local source archive có size/hash đã verify. Nếu chưa có archive, để delivery class pending và không hiển thị nút tải production như artifact đã sẵn sàng.
- ZIP không được chứa token/credential; importer tiếp tục chống path traversal, duplicate required files, file quá lớn và cài atomic.
- Thêm NOTICE/attribution trong app hoặc asset metadata theo yêu cầu CC-BY-4.0.

### 17.8. File dự kiến tạo/sửa cho NMT

Tạo mới:

- tools/nmt-onnx/pyproject.toml hoặc requirements.lock;
- tools/nmt-onnx/convert_hachimi_qt.py;
- tools/nmt-onnx/validate_hachimi_qt.py;
- tools/nmt-onnx/package_hachimi_qt.ps1;
- tools/nmt-onnx/README.md;
- model manifest/golden test fixtures nhỏ, không commit checkpoint hoặc ZIP lớn;
- NmtModelDescriptor/NmtModelCatalog nếu chưa có package phù hợp;
- NmtModelPickerContract.kt/ViewModel.kt/Screen.kt nếu selector cần tách khỏi TranslationConfigScreen;
- tests parity/import/catalog/selection và Android instrumented test cho QT model.

Có thể sửa:

- app/src/main/java/io/legado/app/model/translation/HachimiOnnxModelRegistry.kt;
- app/src/main/java/io/legado/app/model/translation/HachimiOnnxModelImporter.kt;
- app/src/main/java/io/legado/app/model/translation/HachimiOnnxRuntimeCoordinator.kt;
- app/src/main/java/io/legado/app/data/repository/HachimiOnnxTranslator.kt;
- app/src/main/java/io/legado/app/data/repository/NmtTranslationRepository.kt;
- app/src/main/java/io/legado/app/service/NmtOnnxService.kt và NmtOnnxIpc.kt;
- app/src/main/java/io/legado/app/domain/gateway/NmtTranslationGateway.kt;
- app/src/main/java/io/legado/app/domain/usecase/TranslateChapterUseCase.kt;
- app/src/main/java/io/legado/app/ui/config/translation/TranslationConfig.kt/TranslationConfigScreen.kt;
- app/src/main/java/io/legado/app/constant/PreferKey.kt;
- app/src/main/java/io/legado/app/domain/model/ExternalAssetCatalog.kt/AssetDeliveryCatalog.kt;
- app/src/main/java/io/legado/app/data/repository/AssetDeliveryImportRepository.kt;
- scripts/build-hf-asset-manifest.ps1 và supabase/artifacts/hf-artifacts-manifest.json;
- app/src/main/res/values/strings.xml;
- app/proguard-rules.pro nếu manifest/model reflection cần keep rule mới.

### 17.9. Phase triển khai NMT

Tách phần NMT thành các phase có checkpoint độc lập để không làm ảnh hưởng model đang hoạt động:

- **NMT-0 — audit contract/profile (1–2 ngày):** chốt revision model, kiểm tra contract của `HachimiOnnxTranslator`, `NmtOnnxService`, IPC và artifact hiện tại; lập bộ câu golden Chinese → Vietnamese; xác nhận default decode và compatibility của ONNX Runtime Android.
- **NMT-1 — export prototype (2–4 ngày):** dựng môi trường conversion có lock version, export FP32 encoder/decoder/tokenizer/detokenizer, chạy `onnx.checker` và parity với Transformers; xử lý chênh lệch graph/names/shapes trước khi viết importer.
- **NMT-2 — quantize/package/reproducibility (2–4 ngày):** tạo INT8 candidate sau khi FP32 đạt parity, đo chất lượng và kích thước, sinh manifest/checksum/NOTICE, đóng gói atomic và chạy lại conversion trong môi trường sạch.
- **NMT-3 — multi-model runtime (3–5 ngày):** parameterize registry/importer/runtime/IPC/cache theo `modelId`, giữ backward compatibility với model cũ, bảo đảm switch model không làm rò native memory hoặc dùng nhầm cache.
- **NMT-4 — UI/model option/asset delivery (2–4 ngày):** thêm model selector trong Translation settings, trạng thái installed/missing, import/download/metadata, cảnh báo decode của QT và catalog asset/provenance.
- **NMT-5 — device/performance/regression (3–5 ngày):** chạy trên arm64 và emulator phù hợp, đo cold load/TTFS/tokens-per-second/RSS, kiểm tra service lifecycle, cancel/timeout/process death và regression model cũ.

Không chuyển sang phase tiếp theo nếu phase trước chưa có artifact/report đủ để truy nguyên revision, graph contract, checksum và kết quả parity.

### 17.10. Ma trận kiểm thử NMT

- **Conversion:** snapshot đúng revision, thiếu file/đổi tokenizer, export lặp lại cho cùng input, sai version tool và sai license metadata phải fail rõ ràng.
- **Graph:** `onnx.checker`, input/output names, dtype/shape động, past key/value, special token IDs, tokenizer Unicode và detokenizer whitespace.
- **Quality:** câu ngắn/dài, nhiều đoạn, dấu câu, xuống dòng, tên riêng, số/ngày tháng, thuật ngữ, văn phong webnovel; so sánh Transformers FP32 ↔ ONNX FP32 ↔ INT8 và kiểm tra riêng trường hợp no-repeat.
- **Importer/catalog:** checksum, manifest schema, thiếu/duplicate required file, path traversal, giới hạn kích thước, cài atomic, catalog có nhiều model, xóa/giữ model cũ và khôi phục sau restart.
- **Runtime/IPC:** request có `modelId`, model không tồn tại, model đang thiếu, switch khi request chạy, generation/cache isolation, cancel, timeout, service rebind/process death và lỗi native.
- **UI:** hiển thị model installed/missing, chọn model, import/download thành công/thất bại, metadata/license, default QT no-repeat bằng 0, cảnh báo khi user bật thủ công và giữ lựa chọn sau restart/upgrade.
- **Android/release:** arm64 tối thiểu một thiết bị thật, x86_64 nếu phát hành, debug/noR8/release R8, load native libraries, ABI split, low-RAM và cold-start regression.

### 17.11. Rủi ro và quyết định cần giữ trong implementation

| Rủi ro | Quyết định/biện pháp bắt buộc |
|---|---|
| Optimum sinh decoder graph khác contract hiện tại | Kiểm tra graph thật; viết adapter export hoặc đọc tên/shape từ manifest, không đổi tên mù bằng string replacement. |
| Tokenizer/detokenizer không đồng nhất với checkpoint | Pin tokenizer theo cùng revision, kiểm tra special tokens/Unicode và đưa checksum vào manifest. |
| INT8 làm giảm chất lượng hoặc lỗi trên một ABI | Giữ FP32 làm fallback/debug; chỉ đưa INT8 vào release sau parity và device validation. |
| QT bị áp `no_repeat_ngram_size` từ config cũ | Profile QT mặc định bằng 0; UI cảnh báo khi user bật; test tên riêng trước/sau. |
| Nhiều model làm tăng native memory | Chỉ giữ một runtime active trên thiết bị low-RAM; đóng session cũ dưới mutex trước khi load model mới. |
| Một luồng dịch quên truyền `modelId` | Version hóa IPC, test cả chapter/dynamic/preview và đưa modelId vào cache/provider revision. |
| Sai license/provenance khi phân phối asset | Manifest/NOTICE bắt buộc, pin source revision/hash, không dùng provenance của model Hachimi cũ cho QT. |
| Artifact lớn hoặc chưa được mirror | Không hiển thị production download khi chưa có checksum/size/mirror đã verify; hỗ trợ import local cho development. |

### 17.12. Effort increment và Definition of Done cho NMT

Phần NMT bổ sung ước lượng **13–23 engineer-days**, gồm conversion/validation, multi-model runtime, UI selector, asset delivery và device regression. Tổng kế hoạch WebDAV + NMT sau khi bổ sung scope này là khoảng **43–74 engineer-days**, chưa tính thời gian chờ review/license/model hosting hoặc Google OAuth verification.

NMT được coi là hoàn tất khi:

1. HachimiMT-60-QT đã được export và package bằng pipeline tái lập, có manifest, checksum, license/attribution và report revision.
2. FP32 đạt graph/runtime/reference parity; INT8 chỉ được phát hành khi đạt ngưỡng chất lượng và Android device validation đã chốt.
3. Người dùng có thể chọn model trong Translation settings, import/download model và xem trạng thái/metadata; lựa chọn được lưu bền vững.
4. `modelId` đi xuyên suốt từ config → use case → IPC/service → runtime → cache/result; model cũ vẫn hoạt động và không dùng nhầm output.
5. QT dùng decode profile mặc định phù hợp model card, đặc biệt không tự bật no-repeat bigram; mọi override của người dùng được hiển thị minh bạch.
6. Test conversion, importer, catalog, UI, IPC, service lifecycle và release ABI có kết quả lưu trong build/test report.

## 18. Tài liệu tham khảo chính thức

- HachimiMT-60-QT model card: https://huggingface.co/ngocdang83/HachimiMT-60-QT
- Hugging Face Optimum ONNX export guide: https://huggingface.co/docs/optimum-onnx/onnx/usage_guides/export_a_model
- ONNX Runtime Extensions: https://onnxruntime.ai/docs/extensions/



- Gomobile package documentation: https://pkg.go.dev/golang.org/x/mobile/cmd/gomobile
- Go Mobile wiki: https://go.dev/wiki/Mobile
- Android foreground service types: https://developer.android.com/develop/background-work/services/fgs/service-types
- Android foreground service background-start restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Google AuthorizationRequest.Builder: https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationRequest.Builder
- Google OAuth 2.0 for web server applications: https://developers.google.com/identity/protocols/oauth2/web-server
