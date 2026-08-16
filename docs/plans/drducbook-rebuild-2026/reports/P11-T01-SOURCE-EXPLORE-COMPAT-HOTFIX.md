# P11.T01 - Source explore compatibility hotfix

## Muc tieu

Sua hoi quy Khám phá sau nang cap: nguon/import plugin hien thi duoc danh muc nhung danh sach sach bi trang.

## Thay doi

- `ExploreBooksUseCase` khong con fallback vao nguyen chuoi `exploreUrl` nhieu dong/JSON/JS khi caller khong truyen `moduleUrl`.
- `ExploreBooksGateway` bo sung ham lay `ExploreKind` da parse de chon URL danh muc dau tien.
- `ExploreShowViewModel` doi voi route khong co URL cu the se nap danh muc truoc, chon URL dau tien, roi moi load sach.
- `VbookPluginAdapter` chap nhan `vbook://home` bang cach resolve sang danh muc VBook dau tien co URL that.
- Preview Khám phá hien thong bao loi/rong va nut thu lai thay vi im lang trang.

## Bang chung

- Unit test moi: `ExploreBooksUseCaseTest`
  - `missing module url uses first parsed explore kind`
  - `blank module url falls back to first parsed explore kind`
- `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`: PASS.
- `.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain`: PASS.
- LDPlayer `emulator-5554`, `com.drducbook.app.debug`, ABI `x86_64`: cai de APK debug thanh cong.
- Smoke sau cai de: man Khám phá hien danh sach sach/bia cho source `Legado`, khong con trang trang.

## Luu y du lieu nguon

DB debug hien co mot so source rule/JS da bi dich vao noi dung ky thuat, vi du identifier JS co tieng Viet va dau cach. Cac source bi hong du lieu nhu vay van can import lai tu goi nguon sach, khong the sua an toan bang parser runtime.

## Correction 2026-07-31 09:05

- Bo sung fix runtime DI: `appModule` bind `AndroidCookieVaultCodec` theo interface `CookieVaultCodec`, de `CookieVaultRepository`/`SourceCookieGateway` duoc Koin tao dung khi Khám phá tải sách.
- Smoke evidence sau correction: cài lại `app-app-x86_64-debug.apk` tren LDPlayer `emulator-5554`; nguồn `Thân Sĩ Truyện Tranh (WNACG)` hiển thị danh sách sách và ảnh bìa; logcat không còn `InstanceCreationException`, `NoBeanDefFoundException` hoặc lỗi `SourceCookieGateway`.
