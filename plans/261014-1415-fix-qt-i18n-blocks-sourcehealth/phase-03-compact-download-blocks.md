# Phase 03: Compact Download & Package Info Blocks (Đưa Cài Đặt TTS Ra UI Chính & Chuẩn Hóa Card TTS/NMT/Valtec/Zero)

Status: ⬜ Pending
Dependencies: None

## Objective
1. **Chuyển vị trí tải Model TTS và cài đặt TTS về ngoài UI chính của Cài đặt (`ConfigNavScreen`)**:
   - Bổ sung thẻ mục **"Cài đặt TTS"** (hoặc "Quản lý & Cài đặt TTS") trực tiếp trên màn hình Cài đặt chính (`ConfigNavScreen.kt`), người dùng không phải vào sâu trong "Cài đặt đọc" (`ReadConfigScreen`) để tìm kiếm.
   - Kết nối trực tiếp route `MainRouteSettingsTtsModels` từ màn hình chính để người dùng vừa cài đặt engine vừa duyệt/tải các gói model TTS tiện lợi ngay tại chỗ.
2. **Chuẩn hóa hiển thị gọn gàng toàn bộ các họ Model TTS (Piper, Valtec, ZeroTTS) & Model NMT theo mockup**:
   - Loại bỏ 100% các chuỗi kỹ thuật rườm rà: mã hash SHA-256 (`SHA 0826c36e241e`), tên file zip cồng kềnh, phân loại trùng lặp (`Dịch · translation`).
   - Tái cấu trúc giao diện theo phong cách Card UI chuẩn mockup:
     + Leading badge bo góc mềm (`[ VI ]`, `[ ZH ]`).
     + Title to rõ, format sạch sẽ.
     + Tag pill engine thực tế (`[Piper]`, `[Valtec]`, `[ZeroTTS]`, `[ONNX]`). Tuyệt đối loại bỏ tag ví dụ `VMODEL`.
     + Subtitle gạch chấm ngắn gọn `${Ngôn ngữ} • ${Đặc tính/Thể loại} • ${Dung lượng}`.
     + Trailing Action: Nút tải hình tròn (`FilledTonalIconButton`).

---

## Chi tiết Quy chuẩn các Họ Model TTS & NMT trong Card Layout

### 1. Họ Model Piper TTS (`ENGINE_PIPER_VITS`)
- **Đặc điểm:** Từng voice đóng gói kèm ONNX + JSON riêng biệt (~31 - 58 MB).
- **Quy cách Card:**
  ```
  +-------------------------------------------------------------+
  | [ VI ]   Ban Mai  [Piper]                               (↓) |
  |          Tiếng Việt • Nữ • 31.4 MB                          |
  +-------------------------------------------------------------+
  | [ VI ]   Miền Nam  [Piper]                              (↓) |
  |          Tiếng Việt • Đa giọng • 38.2 MB                    |
  +-------------------------------------------------------------+
  | [ VI ]   Minh Quang  [Piper]                            (↓) |
  |          Tiếng Việt • Nam • 31.4 MB                         |
  +-------------------------------------------------------------+
  ```

### 2. Họ Model Valtec TTS (`ENGINE_VALTEC` - VITS ONNX)
- **Đặc điểm:** Gói ZIP trọn bộ chứa text_encoder, flow, decoder, duration_predictor (~160 MB).
- **Quy cách Card:**
  ```
  +-------------------------------------------------------------+
  | [ VI ]   Valtec Vietnamese  [Valtec]                    (↓) |
  |          Tiếng Việt • Đa giọng VITS • 160 MB                |
  +-------------------------------------------------------------+
  ```

### 3. Họ Model ZeroTTS (`ENGINE_ZEROTTS` - Base Model & Voice Prompts)
- **Đặc điểm:** 
  + Gói Base nền tảng: `ZeroTTS Base (Mai Chi)` dung lượng ~443 MB.
  + Các gói voice prompt siêu nhẹ (~31 KB): Mai Chi, Bảo Trang, Kim Oanh, Gia Huy, Hữu Đức, Quang Minh, Tiến Đạt, Hà My.
- **Quy cách Card:**
  ```
  +-------------------------------------------------------------+
  | [ VI ]   ZeroTTS Base (Mai Chi)  [ZeroTTS]              (↓) |
  |          Tiếng Việt • Kể chuyện (Base) • 443 MB             |
  +-------------------------------------------------------------+
  | [ VI ]   Bảo Trang  [ZeroTTS]                           (↓) |
  |          Tiếng Việt • Nữ, tin tức • 31 KB                   |
  +-------------------------------------------------------------+
  | [ VI ]   Hữu Đức  [ZeroTTS]                             (↓) |
  |          Tiếng Việt • Nam, điềm đạm • 31 KB                 |
  +-------------------------------------------------------------+
  | [ VI ]   Tiến Đạt  [ZeroTTS]                            (↓) |
  |          Tiếng Việt • Nam, sôi nổi • 31 KB                  |
  +-------------------------------------------------------------+
  ```

### 4. Họ Model NMT (Dịch máy Hachimi, ONNX, ML Kit)
- **Đặc điểm:** Mô hình dịch máy thần kinh Hachimi zh-vi (~168 MB) hoặc Google ML Kit (~32 MB).
- **Quy cách Card:**
  ```
  +-------------------------------------------------------------+
  | [ ZH ]   HachimiMT-60-QT  [ONNX]                        (↓) |
  |          zh-vi • Hán Việt • 168.3 MB                        |
  +-------------------------------------------------------------+
  | [ ZH ]   Hachimi zh-vi  [NMT]                           (↓) |
  |          zh-vi • NMT chuẩn • 168.3 MB                       |
  +-------------------------------------------------------------+
  | [ ZH ]   Google ML Kit  [ML Kit]                        (↓) |
  |          zh-vi • Offline engine • 32 MB                     |
  +-------------------------------------------------------------+
  ```

---

## Implementation Steps

### 1. Đưa Thẻ "Cài đặt TTS" ra màn hình Cài đặt chính (`ConfigNavScreen.kt`)
- Trong `ConfigNavScreen.kt`:
  + Thêm tham số callback điều hướng: `onNavigateToTts: () -> Unit = {}`.
  + Thêm thẻ `ClickableSettingItem` trong nhóm cài đặt:
    ```kotlin
    ClickableSettingItem(
        title = stringResource(R.string.local_tts_settings_title), // "Cài đặt TTS"
        onClick = onNavigateToTts,
    )
    ```
- Trong `MainNavGraph.kt`:
  + Truyền `onNavigateToTts = { backStack.add(MainRouteSettingsTtsModels) }` khi đăng ký `entry<MainRouteSettings>`.
- Trong `TtsModelManagerScreen.kt`:
  + Tối ưu thanh công cụ đầu màn hình: Nút mở kho tải Catalog / AssetDelivery cho toàn bộ các model TTS (Piper, Valtec, ZeroTTS) hiển thị rõ nét trên cùng.

### 2. Tái cấu trúc `AssetDeliveryItemRow` trong `AssetDeliveryScreen.kt`
- Thay thế `ListItem` 3 tầng cũ thành Card UI chuẩn mockup:
  + Container: `Surface` / `NormalCard` bo góc 16.dp.
  + Leading badge: Khối bo góc 12.dp chứa mã ngôn ngữ `[ VI ]` hoặc `[ ZH ]`.
  + Title & Tag Row: Tên hiển thị sạch sẽ + tag pill engine thực tế (`[Piper]`, `[Valtec]`, `[ZeroTTS]`, `[ONNX]`).
  + Subtitle Row: Định dạng phân cách dấu chấm: `${Ngôn ngữ} • ${Thể loại/Đặc tính} • ${Dung lượng}`.
  + Trailing Button: Nút tròn `FilledTonalIconButton` tải xuống.
  + Ẩn hoàn toàn mã SHA-256 và tên file zip kỹ thuật.

### 3. Tinh gọn & Phân loại trong `TtsModelManagerScreen.kt`
- Loại bỏ dòng hiển thị SHA-256 checksum thô (`stringResource(R.string.local_tts_model_checksum)`).
- Hiển thị danh sách model đã cài đặt dưới dạng card tinh gọn có tag phân loại: `Valtec`, `ZeroTTS`, `Piper`.
- Thông số kỹ thuật (sample rate, số giọng, dung lượng) được gộp thành subtitle ngắn gọn có dấu chấm phân cách.

### 4. Tinh gọn các Block trong `TranslationConfigScreen.kt`
- Rút gọn subtitle các block tải mô hình NMT:
  + "Tải gói QT clean": Rút gọn thành dòng trạng thái súc tích ("Từ điển chuẩn Hán Việt/VietPhrase").
  + "Gói ngôn ngữ Google ML Kit": Rút gọn thành "Mô hình offline Google zh-vi (32 MB)".
  + "Tải model NMT (Hachimi zh-vi)", "Tải model NMT (Hachimi QT)": Rút gọn chỉ giữ lại thông số cốt lõi.

---

## Files to Create/Modify
- `app/src/main/java/io/legado/app/ui/config/ConfigNavScreen.kt` - Bổ sung thẻ "Cài đặt TTS" ra màn hình Cài đặt chính.
- `app/src/main/java/io/legado/app/ui/main/MainNavGraph.kt` - Nối đường dẫn `onNavigateToTts` tới `MainRouteSettingsTtsModels`.
- `app/src/main/java/io/legado/app/ui/assetdelivery/AssetDeliveryScreen.kt` - Tái cấu trúc card tải gói cho Piper, Valtec, ZeroTTS, NMT.
- `app/src/main/java/io/legado/app/ui/config/tts/TtsModelManagerScreen.kt` - Tinh gọn card model TTS, phân biệt Valtec, ZeroTTS, Piper, loại bỏ SHA-256.
- `app/src/main/java/io/legado/app/ui/config/translation/TranslationConfigScreen.kt` - Rút gọn phụ đề các SettingItem tải model NMT.

---

## Test Criteria
- [ ] Màn hình Cài đặt chính (`ConfigNavScreen`) có thẻ "Cài đặt TTS" ngay ngoài danh sách, bấm vào chuyển ngay tới màn hình quản lý mô hình TTS và kho tải.
- [ ] Tất cả các gói tải model TTS (Valtec, ZeroTTS, Piper) và model NMT hiển thị nhất quán theo chuẩn mockup card: có Leading badge ngôn ngữ (`VI`/`ZH`), tag engine thực tế (`Piper`/`Valtec`/`ZeroTTS`/`ONNX`), phụ đề ngắn gọn và nút tròn tải xuống.
- [ ] Tuyệt đối không còn mã SHA-256 hay tên file zip kỹ thuật trên toàn bộ các màn hình người dùng.
