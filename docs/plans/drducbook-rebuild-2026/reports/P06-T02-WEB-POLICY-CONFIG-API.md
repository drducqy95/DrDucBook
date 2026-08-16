# P06.T02 - Web policy/config API

## Muc tieu

Them lop policy/config rieng cho DrDucBook WebService v2 de web UI co the bat/tat Export va Dich tu dong ma khong thay doi cac route legacy cua Legado/VBook.

## Thay doi chinh

- Them `WebServicePolicy` gom:
  - `exportEnabled`
  - `autoTranslationEnabled`
  - `updatedAt`
  - `revision`
- Them ETag dang `web-policy-{revision}` de client cap nhat policy an toan.
- Them `WebServicePolicyPatchRequest` va ket qua patch:
  - `Success`: cap nhat policy va tang revision.
  - `Conflict`: tra policy hien tai khi client gui ETag cu.
  - `PreconditionRequired`: bat buoc `If-Match` cho `PATCH`.
- Them `WebServicePolicyStore` luu policy vao preference `webServicePolicy`.
- Them same-origin guard cho API v2 policy de tranh lenh tu origin la.
- Mo CORS cho `Authorization`, `If-Match`, va method `PATCH`.
- Them cac route v2:
  - `GET /api/v2/policy`
  - `PATCH /api/v2/policy`
  - `POST /api/v2/policy/reset`
- API policy dung bearer session tu P06.T01; route legacy va WebSocket legacy khong bi doi contract.
- Them client web rieng cho API v2 trong `modules/web`:
  - Goi instance/session/policy bang axios instance rieng.
  - Luu bearer session vao localStorage key `drducbook.webService.session`.
  - Pinia store quan ly `instance`, `session`, `policy`, `etag`, `loading`, `error`.

## Pham vi file tac dong

- `app/src/main/java/io/legado/app/domain/webservice/WebServiceModels.kt`
- `app/src/main/java/io/legado/app/web/WebServicePolicyStore.kt`
- `app/src/main/java/io/legado/app/web/KtorServer.kt`
- `app/src/main/java/io/legado/app/constant/PreferKey.kt`
- `app/src/test/java/io/legado/app/domain/webservice/WebServiceModelsTest.kt`
- `modules/web/src/api/webService.ts`
- `modules/web/src/store/webServiceStore.ts`
- `modules/web/src/api/index.ts`
- `modules/web/src/store/index.ts`

## API v2 policy

| Method | Path | Auth | Dieu kien | Ket qua |
|---|---|---|---|---|
| GET | `/api/v2/policy` | Bearer session | Same-origin | Policy hien tai + header `ETag` |
| PATCH | `/api/v2/policy` | Bearer session | Same-origin + `If-Match` | Policy moi + header `ETag`, hoac `409`/`428` |
| POST | `/api/v2/policy/reset` | Bearer session | Same-origin | Reset policy mac dinh + header `ETag` |

## Kiem thu

Da chay:

```text
.\gradlew.bat :app:clean --no-daemon --console=plain
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.webservice.WebServiceModelsTest" --no-daemon --console=plain
pnpm type-check
```

Ket qua:

- `:app:clean`: BUILD SUCCESSFUL.
- `:app:compileAppDebugKotlin`: BUILD SUCCESSFUL.
- `WebServiceModelsTest`: 5 tests PASS.
- `modules/web` type-check: PASS.

Bang chung:

- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.webservice.WebServiceModelsTest.xml`
- `modules/web` `vue-tsc --build --force` PASS.

## Ghi chu

- Da gap loi cache Kotlin sau mot lan build timeout truoc do; da dung `:app:clean` va compile lai thanh cong.
- Task nay chi tao policy API va client store. UI chinh sua background cua WebService nam trong P06.T03; gate Export va Auto Translation duoc noi vao pipeline o P06.T04-P06.T05.
