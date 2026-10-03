# Kế hoạch Trinity: Khắc phục 4 vấn đề (Icon chức năng, Kho nguồn trực tuyến, Treo lưu credential, Hoàn thiện Command Code chuẩn 9Router/OmniRoute)

> **Mã kế hoạch:** `P49-FIX-ICONS-HUB-CREDS-COMMANDCODE`  
> **Ngày lập:** 02/10/2026 (Cập nhật hoàn thiện Command Code theo 9Router & OmniRoute)  
> **Thiết bị kiểm thử:** Huawei Nova (HBN-LX9, Serial `2FK0224429001286`)

---

## 1. BỐI CẢNH & PHÂN TÍCH NGUYÊN NHÂN GỐC RỄ (ROOT CAUSE ANALYSIS)

Dựa trên kiểm tra logcat thực tế, mã nguồn, cấu trúc database trên thiết bị Huawei debug, kết hợp tham chiếu kiến trúc của **9Router** và **OmniRoute**:

### Vấn đề 1: Khôi phục Avatar App cũ & Nâng cấp Icon chức năng thanh điều hướng
- **Hiện trạng:** Ở commit `15148ae`, hệ thống đã thay đổi avatar/launcher của ứng dụng (trong `mipmap-anydpi-v26` và các file `ic_launcher_fg_*.xml`). User yêu cầu khôi phục lại avatar gốc của ứng dụng (trước commit `15148ae`). Đồng thời, các icon chức năng ở thanh điều hướng dưới đáy (Giá sách, Khám phá, Workspace, Của tôi, Tải xuống, Trang chủ) hiện đang dùng các icon mặc định cơ bản của Android Material cũ (`LibraryBooks`, `Explore`, `DashboardCustomize`, `Person`) trông đơn điệu và chưa thẩm mỹ.
- **Giải pháp:**
  1. Khôi phục hoàn toàn bộ launcher/avatar cũ từ commit `7b07ae4`.
  2. Nâng cấp bộ icon chức năng cho Bottom Navigation / Rail trong `AppIcons.kt` với thiết kế vector Material 3 Expressive tinh tế, sắc nét, có phân biệt rõ ràng giữa 2 trạng thái: **Selected (Filled/Đậm nét)** và **Unselected (Outlined/Thanh mảnh)**.

### Vấn đề 2: Click vào "Kho nguồn trực tuyến" trong Workspace không có hoạt động xảy ra
- **Nguyên nhân gốc rễ:** Trong `MainNavigator.kt`, hàm `navigateToRoute(backStack, route)` điều phối tất cả các route của Navigation 3 thông qua mệnh đề `when (route)`. `MainRouteBookSourceHub` đã được định nghĩa trong `MainNavKey.kt` và khai báo UI trong `MainNavGraph.kt`, **nhưng hoàn toàn bị bỏ quên trong `when (route)` của `MainNavigator.kt`**!
- **Hệ quả:** Khi user bấm vào thẻ "Kho nguồn trực tuyến" ở Workspace, lệnh `onNavigateToBookSourceHub()` gọi `MainNavigator.navigateToRoute(backStack, MainRouteBookSourceHub)`, nhưng do không có nhánh nào khớp trong `when (route)`, `backStack.add(route)` không bao giờ được gọi $\rightarrow$ UI không phản hồi, không mở màn hình.
- **Giải pháp:** Bổ sung nhánh xử lý `MainRouteBookSourceHub` vào `MainNavigator.kt` để thêm route vào `backStack` khi click.

### Vấn đề 3: Các Provider khi lưu Credential bị treo (Freeze/Hang)
- **Nguyên nhân gốc rễ:** Trong `AiRouterRepository.kt:141-157`, khi gọi `saveCredential`:
  ```kotlin
  if (shouldProbe) {
      probeApiCredentialCapabilities(entity, secret)
  }
  ```
  Hàm `probeApiCredentialCapabilities` thực hiện:
  1. Gọi mạng `delegate.fetchModels(provider)` để dò tìm model.
  2. Duyệt qua **tất cả** model của provider đó.
  3. Với **mỗi model**, thực hiện **3 request HTTP live AI generation tuần tự** (`CHAT`, `TRANSLATE_CHAPTER`, `REWRITE_TEXT`), mỗi request có timeout lên đến 60 giây!
  Nếu provider có 5 model $\rightarrow$ 15 request tuần tự qua mạng.
  Trong khi đó, `AiRouterViewModel.launchMutation` và `AiProviderEditViewModel` chạy trên coroutine với trạng thái `saving = true` chờ hàm này hoàn thành $\rightarrow$ UI bị khóa cứng, hiển thị vòng quay vô tận, treo hàng phút khiến user tưởng app bị đơ.
- **Giải pháp:**
  1. Tách biệt hoàn toàn việc lưu trữ DB với việc kiểm tra probe mạng. Lưu credential vào Room DB và Secret Store ngay lập tức với trạng thái `ACTIVE` và trả về UI tức thì ($\le 50$ms).
  2. Chuyển probe sang chế độ chạy ngầm bất đồng bộ (background coroutine) hoặc kích hoạt thủ công khi user bấm "Kiểm tra kết nối".

### Vấn đề 4: Hoàn thiện Provider Command Code (Tham chiếu 9Router & OmniRoute)
- **Phân tích đối chiếu 9Router & OmniRoute:**
  1. **Trạng thái Credential & Direct Fallback:** Credential khi lưu phải lập tức là `ACTIVE` để `isRouterEligible` chấp thuận, inject API key vào request thay vì chuỗi rỗng `""`.
  2. **URL Endpoint chuẩn hóa:**
     - Endpoint thực tế của Command Code là `/alpha/generate`.
     - Trong 9Router & OmniRoute, URL được chuẩn hóa tự động: Nếu user nhập `https://api.commandcode.ai` hoặc `https://api.commandcode.ai/`, hệ thống tự ghép thêm `/alpha/generate`.
     - Nếu `baseUrl` để trống, tự động fallback về `https://api.commandcode.ai/alpha/generate`.
  3. **CLI Identity Headers:**
     - Command Code kiểm tra nghiêm ngặt danh tính CLI. 9Router & OmniRoute bắt buộc gửi:
       - `x-command-code-version: 0.25.7`
       - `x-cli-environment: cli`
       - `User-Agent: command-code-cli/0.25.7`
       - `x-session-id: <uuid>`
       - `Authorization: Bearer <apiKey>`
       - `Accept: text/event-stream`
       - `Content-Type: application/json`
     - Hiện tại trong code của app, nếu tạo provider thủ công không qua catalog, các header này có thể bị thiếu. Cần đưa các header này làm mặc định bắt buộc trong `commandCodeHeaders`.
  4. **Cấu trúc Request Envelope (Body):**
     - Trong `buildCommandCodeRequestBody`: Trường `config.environment` đang để `"android"`. Trong 9Router, trường này bắt buộc là `"cli"` để khớp với header `x-cli-environment: cli`, tránh bị server Command Code từ chối (unsupported request shape).
  5. **Model Catalog & Discovery (`fetchModels`):**
     - Hiện tại `CommandCodeHandler.fetchModels` trả về `emptyList()`.
     - Đưa Command Code vào danh sách `curatedEntries` của `AiProviderCatalog` với đầy đủ danh mục model nổi tiếng (DeepSeek V4 Pro, DeepSeek Chat, DeepSeek R1 Reasoner, Claude 3.7 Sonnet, Claude 3.5 Sonnet, GPT-4o, Gemini 2.0 Flash).
     - Trong `CommandCodeHandler.fetchModels`, trả về danh sách model định sẵn này thay vì rỗng, giúp UI hiển thị danh sách model đẹp mắt khi chọn.
  6. **NDJSON Stream Event Parsing:**
     - Bổ sung parser cho các sự kiện `finish`, `usage` (token count) bên cạnh `text-delta`, `reasoning-delta`, `tool-call`, `error`.

---

## 2. KẾ HOẠCH TRIỂN KHAI CHI TIẾT (5 PHASES)

### Phase 1: Khôi phục Avatar App cũ & Nâng cấp Icon chức năng Navigation
- **File tác động:**
  - `app/src/main/res/drawable/ic_launcher*`
  - `app/src/main/res/mipmap-anydpi-v26/*`
  - `app/src/main/java/io/legado/app/ui/widget/components/icon/AppIcons.kt`
  - `app/src/main/java/io/legado/app/ui/main/MainScreen.kt`
- **Nội dung thực hiện:**
  - Khôi phục các asset launcher về nguyên trạng commit `7b07ae4` (xóa các file `ic_launcher_fg_*.xml` phụ).
  - Tái thiết kế hàm `AppIcons.mainDestination`:
    - **Giá sách:** Outlined Book Open $\leftrightarrow$ Filled Book Collection hiện đại.
    - **Khám phá:** Outlined Compass Modern $\leftrightarrow$ Filled Compass Active.
    - **Workspace:** Outlined Grid Dashboard $\leftrightarrow$ Filled Rounded Squares.
    - **Của tôi:** Outlined User Profile $\leftrightarrow$ Filled Person Solid.
    - **Downloads:** Outlined Arrow Download $\leftrightarrow$ Filled Download Badge.
    - **Trang chủ:** Outlined Smart Home $\leftrightarrow$ Filled Home Modern.
  - Đảm bảo hiển thị đồng bộ cả trên Material 3 Expressive và Miuix Engine.

### Phase 2: Kích hoạt điều hướng "Kho nguồn trực tuyến" trong MainNavigator
- **File tác động:**
  - `app/src/main/java/io/legado/app/ui/main/MainNavigator.kt`
- **Nội dung thực hiện:**
  - Thêm nhánh `MainRouteBookSourceHub` vào `MainNavigator.navigateToRoute(backStack, route)`:
    ```kotlin
    MainRouteBookSourceHub -> {
        if (currentRoute == MainRouteHome) {
            backStack.add(route)
        } else {
            backStack.clear()
            backStack.add(MainRouteHome)
            backStack.add(route)
        }
    }
    ```
  - Kiểm tra xử lý back stack để nút Back / vuốt cạnh quay về Workspace chuẩn xác.

### Phase 3: Khắc phục triệt để lỗi treo khi lưu Credential & Provider
- **File tác động:**
  - `app/src/main/java/io/legado/app/data/repository/AiRouterRepository.kt`
  - `app/src/main/java/io/legado/app/ui/ai/router/AiRouterViewModel.kt`
  - `app/src/main/java/io/legado/app/ui/config/ai/AiProviderEditViewModel.kt`
- **Nội dung thực hiện:**
  - Trong `AiRouterRepository.saveCredential`:
    - Lưu entity với `status = AiCredentialStatus.ACTIVE` ngay lập tức.
    - Lưu secret vào `secretStore` và Room DAO đồng bộ, trả về `entity.toConfig(hasSecret = true)` ngay lập tức.
    - Tách phần probe mạng sang background job không chặn (hoặc chỉ chạy khi gọi `probeCredential`).
  - Trong ViewModel: Đảm bảo `saveCredential` và `saveProviderConfig` phản hồi ngay lập tức, đóng bottom sheet mượt mà không có độ trễ mạng.

### Phase 4: Hoàn thiện Provider Command Code chuẩn 9Router & OmniRoute
- **File tác động:**
  - `app/src/main/java/io/legado/app/data/repository/ai/CommandCodeHandler.kt`
  - `app/src/main/java/io/legado/app/domain/model/AiProviderCatalog.kt`
  - `app/src/main/java/io/legado/app/data/repository/AiRouterRepository.kt`
- **Nội dung thực hiện:**
  1. **Chuẩn hóa URL:** Tự động fallback về `https://api.commandcode.ai/alpha/generate` nếu `baseUrl` trống hoặc thiếu suffix `/alpha/generate`.
  2. **Bảo đảm CLI Identity Headers:** Trong `commandCodeHeaders()`, luôn tự động đính kèm:
     - `x-command-code-version: 0.25.7`
     - `x-cli-environment: cli`
     - `User-Agent: command-code-cli/0.25.7`
  3. **Chuẩn hóa Request Envelope:** Đặt `config.environment = "cli"` (khớp với 9Router) thay vì `"android"`.
  4. **Catalog & Model Discovery:**
     - Đưa `commandcode` vào `AiProviderCatalog.curatedEntries` với danh sách model đầy đủ (DeepSeek V4 Pro, DeepSeek Chat, DeepSeek Reasoner, Claude 3.7 Sonnet, Claude 3.5 Sonnet, GPT-4o, Gemini 2.0 Flash).
     - `CommandCodeHandler.fetchModels()` trả về danh sách model định sẵn này.
  5. **Direct Fallback:** Trong `AiRouterRepository.resolveDirectRequest`, tự động gán fallback base URL cho `COMMAND_CODE` và inject key từ credential `ACTIVE`.

### Phase 5: Kiểm thử, Biên dịch & Cài đặt lên thiết bị Huawei
- **Nội dung thực hiện:**
  - Chạy Unit tests kiểm tra `MainNavigatorTest`, `AiRouterRepositoryTest`, `CommandCodeHandlerTest`.
  - Biên dịch Kotlin compile check: `.\gradlew.bat :app:compileAppDebugKotlin`.
  - Build APK Debug: `.\gradlew.bat assembleAppDebug`.
  - Cài đặt APK lên Huawei Nova (`2FK0224429001286`) qua ADB: `adb install -r app/build/outputs/apk/app/debug/app-app-debug.apk`.
  - Xác nhận trực quan trên thiết bị:
    1. Avatar app trở về avatar cũ.
    2. Các icon ở thanh điều hướng dưới đáy đổi mới đẹp mắt, hiện đại.
    3. Bấm vào "Kho nguồn trực tuyến" mở ngay giao diện 2 tab (YCKCEO & Miêu Công Tử).
    4. Thêm / sửa credential lưu tức thì, không bị treo.
    5. Chat với Command Code DeepSeek V4 Pro stream phản hồi thành công, mượt mà chuẩn 9Router.

---

## 3. CHECKLIST TUÂN THỦ TRINITY PROTOCOL
- [x] Đã nghiên cứu cơ chế của 9Router & OmniRoute và tích hợp vào kế hoạch.
- [x] Đã cập nhật kế hoạch chi tiết cho cả 4 vấn đề.
- [ ] **CHỜ USER DUYỆT PLAN** trước khi bắt đầu Phase 2 (`/code`).
