# P04.T03 - Dau trang ca nhan va dau trang nguon tu dong

## Ket qua

Trang thai: `DONE`

## Trien khai

- Tao bookmark web rieng voi `BrowserBookmark`, `browser_bookmarks`, DAO va repository rieng; khong dung entity bookmark sach.
- Tao `source_bookmark_preferences` de luu pin/an shortcut nguon theo `SourceKey`.
- Tang Room schema tu 105 len 106, them migration 105 -> 106 va export schema `106.json`.
- Browser Home co tim kiem bookmark/shortcut, danh sach bookmark thu cong, shortcut nguon tu dong va nut pin/an theo nguon.
- Shortcut nguon chi sinh tu source HTTP dang enable; source non-HTTP/VBook plugin khong tao shortcut gia.
- URL shortcut uu tien `homeUrl`/URL source hop le da duoc normalise tu domain index hien co.
- Backup/restore them `browserBookmarks.json` va `sourceBookmarkPreferences.json` de giu cau hinh trinh duyet khi sao luu.
- Them chuoi EN/VI cho tim kiem, them/sua/xoa bookmark, pin va an shortcut nguon.

## Verification

- `:app:compileAppDebugKotlin`: PASS.
- `:app:testAppDebugUnitTest --tests "io.legado.app.data.repository.BrowserBookmarkRepositoryTest" --tests "io.legado.app.ui.browser.BrowserHomeDataTest" --tests "io.legado.app.ui.browser.BrowserTabStoreTest" --tests "io.legado.app.domain.model.SourceDomainIndexTest"`: PASS.
- `BrowserBookmarkRepositoryTest`: 2 tests, 0 failure.
- `BrowserHomeDataTest`: 2 tests, 0 failure.
- `BrowserTabStoreTest`: 5 tests, 0 failure.
- `SourceDomainIndexTest`: 6 tests, 0 failure.

## Bang chung

- `app/src/main/java/io/legado/app/domain/model/BrowserBookmark.kt`
- `app/src/main/java/io/legado/app/data/entities/BrowserBookmarkEntity.kt`
- `app/src/main/java/io/legado/app/data/entities/SourceBookmarkPreferenceEntity.kt`
- `app/src/main/java/io/legado/app/data/dao/BrowserBookmarkDao.kt`
- `app/src/main/java/io/legado/app/data/repository/BrowserBookmarkRepository.kt`
- `app/src/main/java/io/legado/app/data/AppDatabase.kt`
- `app/src/main/java/io/legado/app/data/DatabaseMigrations.kt`
- `app/schemas/io.legado.app.data.AppDatabase/106.json`
- `app/src/main/java/io/legado/app/help/storage/Backup.kt`
- `app/src/main/java/io/legado/app/help/storage/Restore.kt`
- `app/src/main/java/io/legado/app/ui/browser/BrowserContract.kt`
- `app/src/main/java/io/legado/app/ui/browser/BrowserHomeData.kt`
- `app/src/main/java/io/legado/app/ui/browser/BrowserViewModel.kt`
- `app/src/main/java/io/legado/app/ui/browser/BrowserScreen.kt`
- `app/src/test/java/io/legado/app/data/repository/BrowserBookmarkRepositoryTest.kt`
- `app/src/test/java/io/legado/app/ui/browser/BrowserHomeDataTest.kt`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.BrowserBookmarkRepositoryTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.browser.BrowserHomeDataTest.xml`

## Rui ro con lai

- Cookie hien tai van con luu theo bang legacy `cookies`; CookieVault encryption va adapter compatibility thuoc P04.T04-P04.T05.
- P04.T06 se gan dang nhap/probe muc tieu voi source context da co.
