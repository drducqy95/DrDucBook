# Phase 05 — Cài đặt TTS, import model ONNX và hướng dẫn cấu hình

## 1. Kết quả phải đạt

Người dùng nhìn thấy mục TTS rõ ràng trong Cài đặt, chọn được engine hệ thống/HTTP/local, import và quản lý model ONNX, chọn voice, nghe thử, điều chỉnh tốc độ/pitch/khoảng nghỉ và hiểu chính xác cấu trúc gói model cần nạp.

## 2. Phạm vi

### Trong phạm vi

- Compose MVI TTS Settings và Local TTS Model Manager.
- Chọn System TTS, HTTP TTS hoặc Local ONNX.
- Import model ZIP bằng SAF; tùy chọn download từ manifest URL có checksum.
- Validate, install atomic, list, select voice, test và delete.
- Hướng dẫn cấu trúc package/config/license ngay trong app.
- Speech rate, pitch, paragraph interval, timer và test phrase.
- Service routing sang đúng engine/model/voice.
- Error/actionable recovery và storage cleanup.

### Ngoài phạm vi

- Huấn luyện/chuyển đổi model trên điện thoại.
- Tự động tải hoặc cập nhật model không có xác nhận.
- Bundled release model không đáp ứng license thương mại/phân phối.
- Thay thế toàn bộ HTTP TTS rule engine hiện có.

## 3. UX và workflow

### P5.1 — TTS Settings

- Mục cấp cao trong `Cài đặt > Đọc thành tiếng`, không đặt trong AI Router.
- Engine dropdown: System Android TTS, HTTP TTS profile hoặc Local ONNX.
- Khi chọn Local: model dropdown có search, voice dropdown, `Nghe thử`, `Quản lý model` và `Hướng dẫn nạp model`.
- Controls: rate, pitch, paragraph interval, timer và follow-system.
- Hiển thị trạng thái service, lỗi gần nhất và lối tắt sửa.

### P5.2 — Model manager

- Card model: name, language, engine, size, sample rate, voice count, license và installed date.
- Actions: test, set default, inspect files, delete.
- Import bằng SAF hoặc URL manifest chứa URL ZIP, SHA-256, size và license.
- Progress theo bytes và giai đoạn extract/validate/install/test.
- Cancel xóa staging, không ảnh hưởng model đang dùng.
- Delete model đang dùng yêu cầu chọn fallback hoặc xác nhận chuyển về System TTS.

## 4. Cấu trúc gói model được hỗ trợ

ZIP Valtec VITS ONNX phải chứa ở root hoặc một thư mục cha duy nhất:

```text
text_encoder.onnx
duration_predictor.onnx
flow.onnx
decoder.onnx
tts_config.json
LICENSE            # tùy chọn nhưng khuyến nghị
```

`tts_config.json` tối thiểu:

```json
{
  "sample_rate": 24000,
  "speakers": {
    "Giọng nữ 1": 0,
    "Giọng nam 1": 1
  }
}
```

Yêu cầu:

- `sample_rate` là số nguyên dương trong giới hạn engine hỗ trợ.
- `speakers` là object không rỗng; ID là integer duy nhất.
- Bốn file ONNX phải đọc được và có kích thước hợp lý.
- `tts-model.json` do ứng dụng sinh sau validate; user không phải viết.
- License/attribution được hiển thị và lưu trong manifest local.

## 5. Validation và runtime

### P5.3 — Import security

- Chặn absolute path, `..`, symlink và file ngoài whitelist.
- Giữ giới hạn entry count, per-entry bytes và total extracted bytes.
- Tính SHA-256 trên file bắt buộc.
- Extract vào staging; chỉ rename/copy atomic sau validate.
- Model ID từ checksum; cùng checksum không cài trùng.
- Nếu update cùng ID, giữ backup cho tới khi runtime test pass.

### P5.4 — Runtime probe

- Load model và synthesize câu test ngắn.
- Kiểm tra PCM sample rate, số frame > 0 và không NaN/overflow.
- Phát preview qua audio focus tạm thời.
- Nếu probe fail: rollback model cũ, giữ diagnostic redacted.
- Engine/service giải phóng session khi stop hoặc đổi model.

### P5.5 — Service integration

- `ReadAloud` resolve `local-tts:<modelId>:<voiceId>`.
- `LocalTtsReadAloudService` xác nhận model/voice còn tồn tại trước start.
- Rate/pitch/interval áp dụng nhất quán giữa System/HTTP/Local trong giới hạn engine.
- Media notification, pause/resume/stop và tiến độ chapter tiếp tục hoạt động.

## 6. File tác động

### File hiện có cần sửa

- `app/src/main/java/io/legado/app/ui/config/readConfig/ReadTtsConfig.kt`
- `app/src/main/java/io/legado/app/data/repository/ReadAloudSettingsRepository.kt`
- `app/src/main/java/io/legado/app/model/ReadAloud.kt`
- `app/src/main/java/io/legado/app/model/tts/LocalTtsModel.kt`
- `app/src/main/java/io/legado/app/model/tts/LocalTtsModelRegistry.kt`
- `app/src/main/java/io/legado/app/model/tts/LocalTtsModelImporter.kt`
- `app/src/main/java/io/legado/app/model/tts/ValtecOnnxTtsEngine.kt`
- `app/src/main/java/io/legado/app/model/tts/VietnameseG2p.kt`
- `app/src/main/java/io/legado/app/service/TTSReadAloudService.kt`
- `app/src/main/java/io/legado/app/service/HttpReadAloudService.kt`
- `app/src/main/java/io/legado/app/service/LocalTtsReadAloudService.kt`
- `app/src/main/java/io/legado/app/service/BaseReadAloudService.kt`
- `app/src/main/java/io/legado/app/constant/PreferKey.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavKey.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavGraph.kt`
- `app/src/main/java/io/legado/app/di/appModule.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-vi/strings.xml`

### File tạo mới dự kiến

- `app/src/main/java/io/legado/app/domain/gateway/LocalTtsModelGateway.kt`
- `app/src/main/java/io/legado/app/data/repository/LocalTtsModelRepository.kt`
- `app/src/main/java/io/legado/app/ui/config/tts/TtsSettingsContract.kt`
- `app/src/main/java/io/legado/app/ui/config/tts/TtsSettingsViewModel.kt`
- `app/src/main/java/io/legado/app/ui/config/tts/TtsSettingsScreen.kt`
- `app/src/main/java/io/legado/app/ui/config/tts/TtsModelManagerContract.kt`
- `app/src/main/java/io/legado/app/ui/config/tts/TtsModelManagerViewModel.kt`
- `app/src/main/java/io/legado/app/ui/config/tts/TtsModelManagerScreen.kt`
- `app/src/main/java/io/legado/app/ui/config/tts/TtsModelGuideSheet.kt`
- `app/src/main/java/io/legado/app/domain/usecase/TestLocalTtsModelUseCase.kt`

## 7. Test bắt buộc phải pass

### Test hiện có

```powershell
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.service.TTSReadAloudProgressTest"
```

### Test mới bắt buộc

- ZIP hợp lệ, nested single root và missing từng required file.
- Zip traversal, duplicate filename, entry/total size limits và cancel cleanup.
- `tts_config.json` invalid JSON, missing speakers, duplicate voice ID và invalid sample rate.
- Same checksum dedupe; failed update rollback model cũ.
- Engine reference parse/serialize và deleted model fallback.
- Service selection System/HTTP/Local.
- Rate/pitch/interval boundary.
- Model manager state import/test/delete/rotation/process recreation.

### Instrumentation/Nox

```powershell
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain
.\gradlew.bat :app:connectedAppDebugAndroidTest --no-daemon --console=plain
```

Manual gate:

1. Mở TTS Settings từ Cài đặt.
2. Xem hướng dẫn cấu trúc package.
3. Import model debug/fixture, chọn voice và nghe thử.
4. Đổi rate/pitch/interval và đọc một chapter.
5. Pause/resume/stop từ notification.
6. Xóa model đang dùng và xác nhận fallback an toàn.
7. Import gói lỗi phải báo đúng file/config, không để staging rác.

## 8. Điều kiện đóng phase

- TTS Settings và model manager truy cập được, không còn chức năng ẩn.
- User biết chính xác file/config cần nạp.
- Model chỉ được cài sau validate + runtime probe.
- Đọc chapter bằng local voice hoạt động và service controls không regression.
