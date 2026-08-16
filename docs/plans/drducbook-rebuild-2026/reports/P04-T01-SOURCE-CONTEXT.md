# P04.T01 - SourceKey, source context va domain index

## Ket qua

Trang thai: `DONE`

## Trien khai

- Tao `SourceKey`, `BrowserSourceContext`, `SourceDomainEntry` va `SourceDomainIndex` de browser nhan dien nguon on dinh theo URL.
- Gom index tu book source, RSS source va VBook metadata qua `SourceDomainIndexRepository`.
- Luu va khoi phuc `sourceKey` trong browser tab session de restore sau process death.
- BrowserViewModel reconcile source context khi initialize, navigate, switch tab, close tab va page finished.
- Dang ky repository va use case trong Koin de Browser co the resolve source index tu flow chung.

## Verification

- `:app:compileAppDebugKotlin`: PASS.
- `:app:testAppDebugUnitTest --tests "io.legado.app.domain.model.SourceDomainIndexTest" --tests "io.legado.app.ui.browser.BrowserTabStoreTest"`: PASS.
- `SourceDomainIndexTest`: 6 tests, 0 failure.
- `BrowserTabStoreTest`: 4 tests, 0 failure.

## Bang chung

- `app/src/main/java/io/legado/app/domain/model/SourceKey.kt`
- `app/src/main/java/io/legado/app/domain/gateway/SourceDomainIndexGateway.kt`
- `app/src/main/java/io/legado/app/domain/usecase/ResolveBrowserSourceContextUseCase.kt`
- `app/src/main/java/io/legado/app/data/repository/SourceDomainIndexRepository.kt`
- `app/src/main/java/io/legado/app/ui/browser/BrowserContract.kt`
- `app/src/main/java/io/legado/app/ui/browser/BrowserViewModel.kt`
- `app/src/main/java/io/legado/app/ui/browser/BrowserTabStore.kt`
- `app/src/main/java/io/legado/app/data/dao/BookSourceDao.kt`
- `app/src/main/java/io/legado/app/di/appModule.kt`
- `app/src/test/java/io/legado/app/domain/model/SourceDomainIndexTest.kt`
- `app/src/test/java/io/legado/app/ui/browser/BrowserTabStoreTest.kt`

## Rui ro con lai

- P04.T02 se dung context nay de thay Browser Home va action surface.
