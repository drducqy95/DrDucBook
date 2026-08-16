# P06.T03 - Web background storage va UI

## Muc tieu

Cho WebService co background rieng, doc lap voi Android appearance. Android app van chi quan ly master WebService; web UI quan ly pairing, Export, Dich tu dong va background.

## Thay doi chinh

- Mo rong `WebServicePolicy` voi:
  - `backgroundAssetId`
  - `backgroundFit`: `cover` hoac `contain`
  - `backgroundPosition`: 9 vi tri an toan
  - `backgroundDim`: `0..0.75`
  - `backgroundBlur`: `0..24`
- Them `WebServiceBackgroundPolicy` de normalize asset id/style va chan path traversal.
- Them private asset store `WebServiceBackgroundStore`:
  - Luu anh trong `filesDir/web_service_background`.
  - Asset id la SHA-256 cua anh da sanitize, dang `{64 hex}.png`.
  - Upload toi da 5 MB; file sanitize luu toi da 12 MB.
  - Kich thuoc anh toi da 4096 x 4096.
  - Chi chap nhan bitmap PNG/JPEG/WebP, decode va encode lai thanh PNG de strip metadata va chan SVG/external payload.
- Them API v2:
  - `POST /api/v2/background`
  - `GET /api/v2/background/{assetId}`
  - `DELETE /api/v2/background`
  - `POST /api/v2/policy/reset` xoa asset nen cu neu reset policy.
- Upload/delete background dung bearer session, same-origin va `If-Match` cua policy ETag.
- Background response co `ETag` rieng va `Cache-Control: private, max-age=86400`.
- Web UI:
  - Them route `#/webService`.
  - Them man `WebServiceSettings.vue` co pairing code, Export toggle, Dich tu dong toggle, background picker/delete/reset, fit/position/dim/blur.
  - Them nut WebService tu Bookshelf va Source editor toolbar.
  - `App.vue` tai background bang bearer session thanh object URL va ap nen qua shell web.
  - Them CSS layer `web-service.css` de background ap dung Bookshelf/Reader/Source editor ma van giu contrast bang dim/blur.
- Web bundle moi duoc build va sync vao `app/src/main/assets/web/vue`.

## Pham vi file tac dong

- `app/src/main/java/io/legado/app/domain/webservice/WebServiceModels.kt`
- `app/src/main/java/io/legado/app/web/WebServicePolicyStore.kt`
- `app/src/main/java/io/legado/app/web/WebServiceBackgroundStore.kt`
- `app/src/main/java/io/legado/app/web/KtorServer.kt`
- `app/src/test/java/io/legado/app/domain/webservice/WebServiceModelsTest.kt`
- `modules/web/src/api/webService.ts`
- `modules/web/src/store/webServiceStore.ts`
- `modules/web/src/App.vue`
- `modules/web/src/assets/web-service.css`
- `modules/web/src/views/WebServiceSettings.vue`
- `modules/web/src/router/index.ts`
- `modules/web/src/views/BookShelf.vue`
- `modules/web/src/components/ToolBar.vue`
- `modules/web/dist/**`
- `app/src/main/assets/web/vue/**`

## API v2 background

| Method | Path | Auth | Dieu kien | Ket qua |
|---|---|---|---|---|
| POST | `/api/v2/background` | Bearer session | Same-origin + policy `If-Match` | Luu asset PNG da sanitize, gan vao policy, tra asset + policy moi |
| GET | `/api/v2/background/{assetId}` | Bearer session | Same-origin + asset id hop le | Tra PNG private, ho tro `ETag`/`If-None-Match` |
| DELETE | `/api/v2/background` | Bearer session | Same-origin + policy `If-Match` | Xoa asset hien tai va clear `backgroundAssetId` |

## Kiem thu

Da chay:

```text
pnpm type-check
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.webservice.WebServiceModelsTest" --no-daemon --console=plain
pnpm build
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
```

Ket qua:

- `modules/web` type-check: PASS.
- `:app:compileAppDebugKotlin`: BUILD SUCCESSFUL.
- `WebServiceModelsTest`: 7 tests PASS.
- `pnpm build`: PASS, tao `WebServiceSettings-*.js/css` va sync vao Android assets.
- Chay thu `:app:assembleAppDebug` nhung vuot 15 phut; da dung cac tien trinh Java con lai va chay lai Kotlin compile PASS. Khong tinh assemble la evidence hoan tat cua task nay.

Bang chung:

- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.webservice.WebServiceModelsTest.xml`
- `modules/web/dist/assets/WebServiceSettings-DZCEFlse.js`
- `modules/web/dist/assets/WebServiceSettings-CIgzK2sR.css`
- `app/src/main/assets/web/vue/assets/WebServiceSettings-DZCEFlse.js`
- `app/src/main/assets/web/vue/assets/WebServiceSettings-CIgzK2sR.css`

## Rui ro/cong viec con lai

- Chua co Ktor integration test multipart/streaming vi project chua co ktor test host dependency.
- Chua co Playwright screenshot/contrast evidence; P06.T07 se dong QA desktop/mobile, reload/offline va 409 scenarios.
- P06.T04/P06.T05 se noi `exportEnabled` va `autoTranslationEnabled` vao pipeline thuc thi.
