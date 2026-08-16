# Phase 01 - Nhan dien, thuong hieu va cai song song

Trang thai: `DONE` (2026-07-29)

## Muc tieu phase

Tao DrDucBook co application sandbox rieng, cai song song voi app cu, trong khi van giu API/schema Legado/VBook o lop compatibility.

## Pham vi file chinh

- `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, local/CI Supabase build config
- `app/src/main/java/io/legado/app/**` `[PRESERVE/SELECTIVE FACADE]`, `app/src/main/java/com/drducbook/app/**` `[NEW]`
- `app/src/main/res/layout/**`, `xml/**`, `values/**`, `mipmap*/**`, `drawable/**`
- `modules/rhino/**`, `app/proguard-rules.pro`, baseline profiles
- `app/src/test/**`, `app/src/androidTest/**`, Supabase/CI configs

## Task chi tiet

### P01.T01 - Doi namespace/application ID

**Muc tieu:** Dat namespace/application ID chinh la `com.drducbook.app` ma khong pha compatibility packages.

**Pham vi file:** `app/build.gradle.kts`, generated R/BuildConfig imports, manifest, XML custom views, baseline profile va reflection/serialization strings.

**Thuc hien:** Doi application ID/Gradle namespace va generated-class references; tao package `com.drducbook.app` cho implementation moi; giu `io.legado.app` theo allow-list compatibility thay vi bulk-relocate; khong doi module namespace `me.ag2s`/`com.script` neu khong can.

**Dieu kien thong qua:** Allow-list `io.legado.app` co owner/ly do va corpus coverage; implementation moi khong tao dependency nguoc vao product package; compile Kotlin pass; khong ClassNotFound/Room/Koin error luc start.

**Log:** Ghi allow-list `io.legado.app` con lai va ly do tung nhom.

### P01.T02 - Tach Android authorities va deep links

**Muc tieu:** Loai moi install conflict voi app cu.

**Pham vi:** Manifest, ReaderProvider, FileProvider, custom permissions, OAuth callback, app links va tests.

**Thuc hien:** Dung authority/permission `com.drducbook.app.*`; them `drducbook://`; giu `legado://`/`yuedu://` nhu alias chooser; khong claim authority cu.

**Dieu kien thong qua:** APK manifest khong chua provider authority cu; Provider route/payload test pass; ca hai app cai cung luc.

**Log:** Dinh kem merged manifest path va ket qua install side-by-side.

### P01.T03 - Legacy compatibility island va R8

**Muc tieu:** Bao toan contract JS/reflection ma khong giu toan bo implementation o package cu.

**Pham vi:** module/source set `legacy-compat` `[NEW]`, `RhinoClassShutter`, compatibility adapters, R8 keep rules va ABI tests.

**Thuc hien:** Giu facade cho JS helpers/entities/API cong khai can thiet; map sang implementation moi; cam facade truy cap Supabase/session/secret; tao release keep rules.

**Dieu kien thong qua:** Fixture Legado/VBook pass tren debug va minified release; ABI allow-list khong bi R8 xoa; khong duplicate Android component.

**Log:** Lien ket danh sach facade/keep rule va APK class verification.

### P01.T04 - Supabase va build identities rieng

**Muc tieu:** DrDucBook co Supabase project/client config va OAuth callback rieng, khong anh huong app cu.

**Pham vi:** Gradle dependencies/config, Supabase project URL/publishable key injection, OAuth callback, Google OAuth client cho Auth va Drive authorization tach biet, build types, CI vars; Firebase plugins/dependencies/config hien co.

**Thuc hien:** Them Supabase Kotlin BOM va Auth/Postgrest/Storage/Functions modules; inject project URL + publishable key theo environment; cau hinh `drducbook://auth/callback`; khai bao Google OAuth identity/Drive consent config theo package/signing fingerprint; go Firebase plugins/Analytics/Performance va `google-services.json` khoi build DrDucBook.

**Dieu kien thong qua:** Supabase client init debug/release pass bang publishable key; OAuth PKCE/deep-link callback dung package; Drive scope chua duoc xin khi chi login; APK khong con Firebase SDK/provider/config; khong duplicate component.

**Log:** Ghi project/client logical ID va SHA fingerprint, khong ghi private key.

### P01.T05 - Rebrand va bo icon DrDucBook

**Muc tieu:** Khong con thuong hieu/icon Legado tren launcher, splash, notification, web va about.

**Pham vi:** strings, mipmap/drawable, adaptive icon XML, launcher aliases, splash, favicon/web assets.

**Thuc hien:** Edit anh nguon bang cong cu image editing; xoa marker Gemini va C2PA; tao master sạch va cac density/monochrome/notification variant.

**Dieu kien thong qua:** Metadata scan sach; icon ro o 48-512 px, mask tron/squircle khong cat chu; tat ca alias dung DrDucBook; visual QA co anh.

**Log:** Ghi source asset, output paths, metadata command va screenshot evidence.

### P01.T06 - Kiem thu coexistence va isolation

**Muc tieu:** Chung minh app moi va app cu cung ton tai an toan.

**Pham vi:** instrumentation/manual scripts, manifest assertions, device/Nox evidence.

**Thuc hien:** Cai app cu truoc, cai DrDucBook; mo hai app; test DB/preferences/WebView/cookie/provider/notification/tile/backup va uninstall tung app.

**Dieu kien thong qua:** Khong INSTALL_FAILED_CONFLICTING_PROVIDER; du lieu/cookie khong ro cheo; uninstall app nay khong lam hong app kia.

**Log:** Ghi package/version/signature, thiet bi va checklist ket qua.

## Gate dong phase

- Debug/noR8/release assemble pass.
- Compatibility corpus pass tren release minified.
- Co bang chung cai song song va icon/manifest QA.

## Ket qua thuc thi

- P01.T01-P01.T06 deu `DONE`; report nam trong `reports/P01-T01..T06`.
- Debug/noR8/release, 663 unit tests va web build PASS.
- Release R8 giu 17/17 legacy ABI classes.
- Legado cu va DrDucBook cai/mo song song voi package, provider, UID va data dir rieng.
- Icon DrDucBook alpha trong suot, metadata sach va da visual QA tren device.
