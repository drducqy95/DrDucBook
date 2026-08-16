# P10.T03 - App asset delivery/downloader

## Muc tieu

Chuyen app package/model catalog khoi Google Drive sang URI noi bo va downloader trong app, su dung Supabase `asset-ticket`/`asset-download` de proxy HuggingFace ma khong can dua HF token/server secret vao client.

## Trang thai

DONE cho phan app-side. Runtime gate voi Supabase Edge Function/HuggingFace live, replay/expired ticket va upload/license/source evidence van duoc theo doi o P10.T08/P11.

## Pham vi file da tac dong

- `app/src/main/java/com/drducbook/app/cloud/AssetDeliveryClientContract.kt`
- `app/src/main/java/io/legado/app/domain/model/ExternalAssetCatalog.kt`
- `app/src/main/java/io/legado/app/domain/model/LocalAiModelCatalog.kt`
- `app/src/main/java/io/legado/app/domain/model/AssetDeliveryModels.kt`
- `app/src/main/java/io/legado/app/domain/gateway/AssetDeliveryGateway.kt`
- `app/src/main/java/io/legado/app/domain/usecase/AssetDeliveryUseCase.kt`
- `app/src/main/java/io/legado/app/data/repository/AssetDeliveryRepository.kt`
- `app/src/main/java/io/legado/app/ui/assetdelivery/AssetDeliveryContract.kt`
- `app/src/main/java/io/legado/app/ui/assetdelivery/AssetDeliveryViewModel.kt`
- `app/src/main/java/io/legado/app/ui/assetdelivery/AssetDeliveryScreen.kt`
- `app/src/main/java/io/legado/app/ui/main/MainActivity.kt`
- `app/src/main/java/io/legado/app/ui/main/MainIntent.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavKey.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavigator.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavGraph.kt`
- `app/src/main/java/io/legado/app/utils/ContextExtensions.kt`
- `app/src/main/java/io/legado/app/di/appModule.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-vi/strings.xml`
- `app/src/test/java/com/drducbook/app/cloud/AssetDeliveryClientContractTest.kt`
- `app/src/test/java/io/legado/app/domain/model/HfArtifactManifestTest.kt`
- `app/src/test/java/io/legado/app/domain/model/AssetDeliveryCatalogResolverTest.kt`

## Noi dung da hoan thanh

- Catalog Android da dung `drducbook-asset://download/{artifactId}` va `drducbook-asset://catalog/{catalogId}` thay cho URL Drive.
- `Context.openUrl(...)` nhan dien URI asset va mo route downloader noi bo; URL thuong van giu hanh vi mo trinh duyet.
- Them resolver gom:
  - Translation packages.
  - Valtec/Piper TTS voice packages.
  - Hy-MT2 GGUF local AI catalog.
- Them gateway/usecase/repository downloader:
  - Lay Supabase access token tu `AccountAuthUseCase`.
  - Goi `asset-ticket` bang Supabase JWT.
  - Goi `asset-download` bang opaque ticket rieng, khong gui JWT/HF token vao download endpoint.
  - Ghi file vao thu muc rieng cua app bang file tam.
  - Kiem tra size va SHA-256 truoc khi thay file dich.
  - File da tai hop le duoc reuse sau khi checksum pass.
- Them Compose/MVI route `AssetDeliveryRouteScreen`:
  - Hien catalog va cho chon package.
  - Hien trang thai chua cau hinh, can dang nhap, dang tai, da xac thuc, loi.
  - Co hanh dong tai, huy, tai lai, mo file da xac thuc, mo trang Account.
  - Khi mo Account tu downloader, back ve lai downloader de tiep tuc.
- Cap nhat text UI khong con noi "Drive" cho cac goi package/model.

## Kiem tra da chay

- `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`
  - Ket qua: BUILD SUCCESSFUL.
- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.AssetDeliveryCatalogResolverTest" --no-daemon --console=plain`
  - Ket qua: BUILD SUCCESSFUL; 4 tests PASS.
- `.\gradlew.bat :app:testAppDebugUnitTest --tests "com.drducbook.app.cloud.AssetDeliveryClientContractTest" --tests "io.legado.app.domain.model.HfArtifactManifestTest" --tests "io.legado.app.domain.model.AssetDeliveryCatalogResolverTest" --no-daemon --console=plain`
  - Ket qua: BUILD SUCCESSFUL; 11 focused tests PASS.
- Quet `drive.google.com|hf_...` trong app/test/supabase/scripts:
  - Khong thay HF token.
  - Chi con 2 match `drive.google.com` trong test assertion kiem "khong co Drive URL".

## Dieu kien thong qua

- Internal asset URI parse va resolve duoc artifact/catalog dung.
- App khong mo URI asset bang trinh duyet ngoai nua.
- Downloader khong chua/log HF token, Supabase secret hoac ticket trong UI.
- File chi duoc expose sau khi size va SHA-256 khop catalog.
- Kotlin compile va focused tests PASS.

## Rui ro/cong viec chuyen tiep

- Can runtime Supabase/HF live de test expired/replay ticket, HTTP Range, offline/corrupt/resume va artifact lon.
- Can secret HF moi dua vao backend, khong dua vao app/client.
- Can P11 device smoke cho tai that tu mot account Supabase da dang nhap.
