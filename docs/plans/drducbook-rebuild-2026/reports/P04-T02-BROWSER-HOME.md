# P04.T02 - Browser Home va hanh dong theo nguon

## Ket qua

Trang thai: `DONE`

## Trien khai

- Thay trang home mac dinh cua Browser bang Browser Home noi bo, khong load WebView khi tab dang o Home.
- Them `BrowserHomeUiState`, source shortcut UI va health summary vao Browser contract.
- Home hien shortcut book/RSS source HTTP, tab gan day va tom tat tinh trang nguon tu health repository.
- Them action theo nguon trong menu Browser: mo Source Health, dang nhap nguon, xac nhan dang nhap va probe lai, sua nguon, xoa cookie nguon, quay ve app.
- Giu lien thong source context cho tab/browser qua `SourceKey`, `BrowserSourceContext.iconPath` va RSS icon.
- Cap nhat route Browser de mo dung man hinh dang nhap/sua source, xoa cookie bang `CookieStore`, dong bo cookie WebView ve CookieStore va chay health worker khi login hoan tat.
- Cap nhat tab store de tab moi/session rong la Home noi bo (`url = ""`, `isHome = true`) va van loai bo URL khong an toan.
- Bo sung chuoi tieng Anh/tieng Viet cho Browser Home va action cookie.

## Verification

- `:app:compileAppDebugKotlin`: PASS.
- `:app:testAppDebugUnitTest --tests "io.legado.app.ui.browser.BrowserTabStoreTest" --tests "io.legado.app.domain.model.SourceDomainIndexTest"`: PASS.
- `BrowserTabStoreTest`: 5 tests, 0 failure.
- `SourceDomainIndexTest`: 6 tests, 0 failure.

## Bang chung

- `app/src/main/java/io/legado/app/domain/model/SourceKey.kt`
- `app/src/main/java/io/legado/app/data/repository/SourceDomainIndexRepository.kt`
- `app/src/main/java/io/legado/app/ui/browser/BrowserContract.kt`
- `app/src/main/java/io/legado/app/ui/browser/BrowserViewModel.kt`
- `app/src/main/java/io/legado/app/ui/browser/BrowserTabStore.kt`
- `app/src/main/java/io/legado/app/ui/browser/BrowserRouteScreen.kt`
- `app/src/main/java/io/legado/app/ui/browser/BrowserScreen.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavGraph.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-vi/strings.xml`
- `app/src/test/java/io/legado/app/ui/browser/BrowserTabStoreTest.kt`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.browser.BrowserTabStoreTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.model.SourceDomainIndexTest.xml`

## Rui ro con lai

- Dau trang ca nhan/tu dong theo nguon thuoc P04.T03.
- CookieVault encryption va dong bo cookie day du giua WebView/OkHttp/Cronet/Rhino/VBook thuoc P04.T04-P04.T05.
- UI health chi mo source health hien co; targeted probe sau dang nhap se tiep tuc trong P04.T06.
