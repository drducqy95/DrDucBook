# Bộ kế hoạch nâng cấp Legado MD3 năm 2026

Ngày xuất: 20/07/2026  
Codebase: `D:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main`  
Database tại thời điểm xuất: Room version 98  
Ngôn ngữ tài liệu: Tiếng Việt, UTF-8

## Mục đích

Bộ tài liệu này tách kế hoạch nâng cấp tổng thể thành các phase có thể giao độc lập cho kỹ sư hoặc agent. Mỗi phase ghi rõ mục tiêu, phạm vi, thứ tự thực hiện, interface/schema dự kiến, file tác động, tiêu chí nghiệm thu và test bắt buộc.

Không được đánh dấu phase hoàn tất chỉ vì compile thành công. Phase chỉ hoàn tất khi các test được liệt kê trong file phase đã pass và có smoke-test thực tế trên Nox hoặc thiết bị tương đương.

## Danh sách phase

| Thứ tự | File | Kết quả chính |
|---|---|---|
| 01 | [AI Router và provider](./PHASE-01-AI-ROUTER-PROVIDERS.md) | Dashboard AI Router cấp ứng dụng, popup provider, model search, Local/OAuth/API route hoạt động |
| 02 | [AI Agent và bong bóng Chatbot](./PHASE-02-AI-AGENT-CHAT-BUBBLE.md) | Agent loop, tool/skill/plugin, memory dài hạn, mutation approval, bong bóng chat toàn app |
| 03 | [LocalAI, ML Kit và dịch thuật](./PHASE-03-LOCALAI-MLKIT-TRANSLATION.md) | Local GGUF dùng được, quản lý gói ML Kit, chốt cache dịch, pipeline dịch truyện tranh |
| 04 | [VBook, sức khỏe nguồn và Browser](./PHASE-04-VBOOK-SOURCE-HEALTH-BROWSER.md) | Import registry/file/link, quét nguồn hằng ngày, Browser đăng nhập/captcha/dịch trang |
| 05 | [TTS và model giọng đọc](./PHASE-05-TTS-MODEL-MANAGEMENT.md) | Cài đặt TTS đầy đủ, import/test/xóa model ONNX, hướng dẫn cấu trúc gói |
| 06 | [Media Player và audiobook](./PHASE-06-MEDIA-PLAYER-DOWNLOAD-AUDIOBOOK.md) | Phát audio/video thật, seek/speed/subtitle, download/export, import sách nói |
| 07 | [Sáng tác, Ebook Editor và drop cap](./PHASE-07-AUTHORING-EBOOK-DROPCAP.md) | Tiền sáng tác, clone raw/cache/final, block tự do, preview/export, drop cap thống nhất Reader |
| 08 | [Tích hợp, migration và phát hành](./PHASE-08-INTEGRATION-MIGRATION-RELEASE.md) | Migration an toàn, security/performance/accessibility, Nox regression và release gates |

## Thứ tự phụ thuộc

1. Phase 01 phải hoàn tất trước khi Chatbot, dịch, Sáng tác và Ebook Editor phụ thuộc route đa provider.
2. Phase 02 có thể bắt đầu UI/memory sau khi contract route của Phase 01 ổn định; tool mutation chỉ mở sau permission broker hoàn tất.
3. Phase 03 phụ thuộc Phase 01 cho AI route, nhưng phần ML Kit model manager có thể triển khai song song.
4. Phase 04 độc lập với Phase 02 ở phần registry/Browser; tool Agent tìm và thêm sách chỉ tích hợp sau khi cả Phase 02 và 04 pass.
5. Phase 05 độc lập với AI Router, nhưng dùng chung navigation/settings conventions.
6. Phase 06 phụ thuộc model `ResolvedMedia` và VBook capability của Phase 04.
7. Phase 07 phụ thuộc Phase 01 cho AI suggestion và Phase 03 cho lựa chọn raw/cache/final.
8. Phase 08 chạy xuyên suốt; release gate cuối chỉ mở khi tất cả phase được chọn cho release đã pass.

## Quy tắc triển khai chung

- UI mới dùng Jetpack Compose, MVI/UDF, `@Stable` state, immutable collections và Navigation 3.
- Composable không gọi DAO/network trực tiếp; business logic đặt trong use case/gateway/repository.
- Giữ thay đổi surgical; không refactor khu vực ngoài phạm vi phase.
- Dữ liệu do user sửa hoặc chốt luôn có ưu tiên cao nhất và không bị AI/refresh ghi đè.
- Secret chỉ lưu trong encrypted secret store; không có API key/token trong log, backup hoặc Room plaintext.
- Mọi mutation từ Agent phải qua permission broker ở domain/repository, không chỉ dựa vào dialog UI.
- Migration Room phải có fixture từ schema liền trước và kiểm tra dữ liệu thật quan trọng.
- File được ghi trong mục “tạo mới dự kiến” là hợp đồng đặt tên cho implementer; nếu codebase đổi trước khi phase bắt đầu, được phép điều chỉnh tên nhưng không được thay đổi trách nhiệm kiến trúc.

## Definition of Done chung

Một phase chỉ được đóng khi:

1. Contract/domain model đã khóa và luồng lỗi có mã phân loại.
2. DI, navigation, schema/migration và localization được nối đầy đủ.
3. Unit test logic cốt lõi và lỗi biên pass.
4. Integration/instrumentation test cho DB, filesystem, network, WebView, service hoặc Media3 pass khi có liên quan.
5. `:app:compileAppDebugKotlin` và `:app:assembleAppDebug` pass.
6. Smoke test Nox có bằng chứng cho happy path, loading, empty và error path.
7. Logcat không có crash/ANR/Koin error và không lộ secret.
8. Tài liệu người dùng/cấu hình được cập nhật cho tính năng có thao tác thủ công.

## Lệnh gate nền

```powershell
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain
```

Nếu task thay đổi manifest, resource, Room schema, native library hoặc packaging, phải chạy thêm assemble và test tương ứng được ghi trong từng phase.
