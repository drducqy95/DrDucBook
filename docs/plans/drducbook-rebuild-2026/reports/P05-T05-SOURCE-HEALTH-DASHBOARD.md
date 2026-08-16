# P05.T05 - Dashboard va tich hop Browser/Source list

## Muc tieu

Nang `SourceHealth` tu manh danh sach co ban thanh dashboard co tong quan, search,
filter, lich su check va sheet chi tiet cho tung nguon, dong thoi noi truc tiep
voi Browser va man sua nguon cu cua Legado/VBook.

## Ket qua trien khai

- `BookSourceHealthRepository` lay du lieu tu `SourceDomainIndexGateway.index` de
  gom Book, RSS va VBook trong cung mot bang theo `sourceUrl`.
- `SourceHealthContract` va `SourceHealthViewModel`
  - them summary, query, filter, recent runs, selected source/run/stages
  - giu luong check ngay va check theo source rieng
  - map `selectedSource` doc lap voi filter/search de sheet khong mat context
- `SourceHealthScreen`
  - summary metrics
  - search bar
  - filter chips
  - recent runs strip
  - source list card
  - sheet chi tiet co `Check source`, `Open in browser`, `Edit source`
- `MainRouteSourceHealth`
  - chuyen sang `data class` co `sourceUrl: String? = null`
  - Browser mo dashboard voi source dang xem
  - route deep-link/intent co the mang `EXTRA_SOURCE_URL`
- `MainNavGraph` va `MainNavigator`
  - Browser open dashboard voi source focus
  - dashboard mo lai Browser voi `sourceUrl` + `initialUrl`
  - edit source giu compatibility voi `BookSourceEditActivity` va `RssSourceEditActivity`
- Strings moi cho dashboard duoc them vao `values` va `values-vi`.

## Kiem tra

- `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`
  - PASS.
- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.ui.main.MainNavigatorTest" --tests "io.legado.app.ui.main.MainIntentTest" --tests "io.legado.app.ui.book.source.health.SourceHealthStateTest" --tests "io.legado.app.data.repository.BookSourceHealthRepositoryTest" --no-daemon --console=plain`
  - PASS.

## Bang chung

- `app/src/main/java/io/legado/app/ui/book/source/health/SourceHealthContract.kt`
- `app/src/main/java/io/legado/app/ui/book/source/health/SourceHealthViewModel.kt`
- `app/src/main/java/io/legado/app/ui/book/source/health/SourceHealthScreen.kt`
- `app/src/main/java/io/legado/app/data/repository/BookSourceHealthRepository.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavKey.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavGraph.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavigator.kt`
- `app/src/main/java/io/legado/app/ui/main/MainIntent.kt`
- `app/src/test/java/io/legado/app/ui/book/source/health/SourceHealthStateTest.kt`
- `app/src/test/java/io/legado/app/data/repository/BookSourceHealthRepositoryTest.kt`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.main.MainNavigatorTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.main.MainIntentTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.book.source.health.SourceHealthStateTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.BookSourceHealthRepositoryTest.xml`

## Rui ro va viec tiep theo

- Dashboard da co sheet action va history; P05.T06 se tap trung retention/cleanup.
- Browser/Edit compatibility voi Legado/VBook da giu nguyen, khong co auto-disable.
