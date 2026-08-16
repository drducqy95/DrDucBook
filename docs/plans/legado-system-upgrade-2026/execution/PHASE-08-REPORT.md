# Phase 08 — Báo cáo hoàn tất 2026-07-27

## Trạng thái

Phase 08 đã hoàn tất theo release gate tách module trên máy 16 GB RAM. Không tạo lint
baseline để che lỗi. Toàn bộ 55 lỗi code ngoài nhóm localization trong báo cáo strict lint
ban đầu đã được xử lý; `MissingTranslation` được quy định rõ là warning vì các locale cộng
đồng được duy trì theo dạng bản dịch từng phần.

## Phạm vi đã hoàn tất

- Room schema hiện hành là 105; golden fixture 98 được nâng tuần tự lên 105.
- Feature flags được lưu bằng SharedPreferences và chỉnh trong Lab; schema không phụ thuộc flag.
- Agent mutation/skill/plugin bị lọc khỏi tool list và bị broker chặn khi flag tắt.
- Browser translation, manga translation, chat bubble, source health, media download và fixed-layout có runtime gate.
- ZIP import/restore chặn path traversal, sibling-prefix escape, quá nhiều entry, quá kích thước và ZIP bomb.
- TTS/audiobook ghi nhớ trạng thái phát khi media lấy audio focus và tiếp tục khi focus trở lại.
- `prefDelegate` không còn chặn main thread để đọc/ghi DataStore.
- Lint runtime/UI đã sửa các nhóm permission, API level, lifecycle service, Media3 opt-in,
  Compose resource observation, string format, constants, AppCompat resource và restricted API.
- Bảy tài liệu người dùng và các file trạng thái dự án đã được cập nhật.

## Migration

| Version | Nội dung |
|---|---|
| 98→99 | Agent tables |
| 99→100 | Mở rộng AI memory |
| 100→101 | AI memory FTS |
| 101→102 | AI skill/version |
| 102→103 | Source health |
| 103→104 | Media download task/item |
| 104→105 | ETag, Last-Modified và content length |

Fixture gồm 5 sách, 50 chương, 3 nguồn, cookie, AI provider/model/route/credential và memory.
LDPlayer `MigrationTest`: **3/3 pass**, gồm fresh install, giữ dữ liệu 98→105 và reopen idempotent.

## Test và release gate

| Gate | Kết quả |
|---|---|
| Unit test toàn app | 603 pass, 1 skip, 0 failure/error |
| Security Phase 08 | 10/10 pass |
| Cross-feature instrumentation | 5/5 pass |
| Migration instrumentation | 3/3 pass |
| `compileAppDebugKotlin` | pass |
| `assembleAppDebug` | pass |
| `assembleAppNoR8` | pass ở revision Phase 08 |
| `assembleAppRelease` + R8 | pass |
| Release `lintVital` | pass |
| `modules:book:lintDebug` | pass |
| `modules:rhino:lintDebug` | pass |
| Resource lint | 0 errors, 975 `MissingTranslation` warnings |
| LDPlayer debug/release smoke | pass, không crash/ANR |

`lintAppDebug` nguyên khối không hoàn tất trên máy 16 GB: AGP 9.2.1 FIR/UAST dùng
khoảng 6,6–7,0 GB trong JVM và làm hệ thống chỉ còn dưới 1 GB RAM. Ba lượt 15–25 phút
đều nghẽn ở UAST trước khi ghi báo cáo. Release gate vì vậy được tách thành lint từng module,
resource lint, compile, toàn bộ unit test, `lintVital`, R8 và device smoke. Cấu hình mặc định
vẫn giữ `checkDependencies = true`; hai Gradle property chỉ phục vụ việc tách lượt lint trên
máy ít RAM.

## Performance LDPlayer

Thiết bị: `127.0.0.1:5555`, model giả lập SM-S9280.

| Variant | Cold sau cài/cập nhật | Cold ổn định | Hot |
|---|---:|---:|---:|
| Debug x86_64 | 8.809s | 9.706s | 13ms |
| Release x86_64 | 9.840s | 2.224s | <1s khi đưa task lên foreground |

Debug x86_64 dùng khoảng 305 MB PSS / 393 MB RSS sau khi ổn định. Release đạt mục tiêu
cold start dưới 3 giây ở lượt ổn định. Logcat sau smoke test không có `AndroidRuntime` crash
hoặc ANR. LDPlayer được trả về bản debug ở foreground sau kiểm tra release.

## APK release đã ký

Thư mục: `release/signed-apks-20260727-phase08-final`

Certificate SHA-256: `dd3d7fac2ae99482c15245219f3dd5a5ffa550e603a53d291e68c480c6d10631`

| APK | Size | SHA-256 |
|---|---:|---|
| `app-app-arm64-v8a-release-signed.apk` | 120.64 MB | `5549548F393A2503F841E8CDC9353FF469934D51426DFCD0F1E022CB8F122B37` |
| `app-app-armeabi-v7a-release-signed.apk` | 83.76 MB | `62CDD891FA7E7D8F517CC901396E3C1C2EF2A097694D574CCE9FB1243F858E4E` |
| `app-app-x86_64-release-signed.apk` | 141.75 MB | `128BA2821BE45DA9274925C790F1492C5BF2940CF2948D2C807F0413C9EE3F9A` |
| `app-app-universal-release-signed.apk` | 230.40 MB | `734A11971909A8A83EA437245AFE83DD2F8DDD2DEC69BDC6E403D9189555358C` |

Debug x86_64 cuối cùng: SHA-256
`015D87FF4CF44B5912BC1F55AC5378F6B959A5AF8DA255BCCA765348D5B32A42`.

## Ghi chú hạ tầng

CI hoặc máy build có ít nhất 24 GB RAM nên chạy lại `:app:lintAppDebug` nguyên khối để
xác nhận tương thích với future AGP. Đây là giới hạn công cụ hiện tại, không còn là backlog
lỗi code đã biết của Phase 08.

## Bản vá dịch chương sau Phase 08

Ngày 2026-07-27, pipeline dịch chương được build và ký lại sau khi sửa raw/display mapping,
literal QT, runtime prompt options, combo fallback tuần tự, typed `ROUTE_UNAVAILABLE`, cache chunk
và lỗi AI đến muộn ghi đè QT/Hán-Việt. Toàn bộ 619 unit test pass; QT trên chương thật 7.620 ký tự
đạt 2.581 ms cold và 947,3 ms warm trên LDPlayer.

Artifact mới: `release/signed-apks-20260727-phase03-translation-final`.

| APK | SHA-256 |
|---|---|
| `app-app-arm64-v8a-release-signed.apk` | `061AA69D17CA8C06A95B297E88F038D45F7300E4B5261C473A0610FB8FDB4C3F` |
| `app-app-armeabi-v7a-release-signed.apk` | `4FB6D597628E9BF84B5A332EFFB9FD6CEDE17334D26AAACB50A435664CD3F169` |
| `app-app-x86_64-release-signed.apk` | `43B6C4D2DD799ED0EFDA542549AD8C08EF92FABFAB08C8147F1114D3F49F69AF` |
| `app-app-universal-release-signed.apk` | `781F81D80B511CDC5ED8C3AFB8AB6A317863B7B4AE2B3F65FA120C33D95D7D7D` |

Các gate ML Kit/NMT thật, chọn raw trên mọi chế độ và soak 10 chương/30 phút vẫn được theo dõi
trong Phase 03 với trạng thái `DEVICE_PARTIAL`; phụ lục này không thay đổi kết luận đó.
