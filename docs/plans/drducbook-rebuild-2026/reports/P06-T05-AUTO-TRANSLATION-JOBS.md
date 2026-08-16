# P06.T05 - Auto-translation jobs

## Muc tieu

Cho web reader tao tac vu dich chuong thong qua WebService v2, su dung cau hinh dich hien co cua app va khong dua API key/provider secret ra trinh duyet. Cong tac `autoTranslationEnabled` la gate bat buoc cho viec tao job moi.

## Thay doi chinh

- Them contract WebService:
  - `WebServiceTranslationJobRequest`
  - `WebServiceTranslationJobResponse`
  - `WebServiceTranslationJobListResponse`
  - `WebServiceTranslationJobs`
- Them `WebServiceTranslationJobController`:
  - Tim sach/chuong trong DB.
  - Tao job runtime bang `TranslationManager.startTranslation`.
  - Dung provider va target language mac dinh tu `TranslationConfig` neu web khong chi dinh.
  - Dedupe job dang `idle`, `translating` hoac `translated`.
  - Cho phep tao lai job moi neu job cu `failed` hoac `cancelled`.
  - Tra tien do chunk, preview khi dang dich, content khi da dich, va error user-facing khi loi.
  - Ho tro huy tung job va huy tat ca job dang chay.
- Them API v2:
  - `POST /api/v2/translation/jobs`
  - `GET /api/v2/translation/jobs`
  - `GET /api/v2/translation/jobs/{jobId}`
  - `DELETE /api/v2/translation/jobs/{jobId}`
- `POST /api/v2/translation/jobs` yeu cau same-origin, bearer session va `autoTranslationEnabled = true`.
- Khi `autoTranslationEnabled = false`, tao job moi tra `403 FEATURE_DISABLED`.
- Khi policy patch/reset tat `autoTranslationEnabled`, backend goi `WebServiceTranslationJobController.cancelAll()`.
- Web reader:
  - Them nut `Dich/Huy`.
  - Tao job dich chuong hien tai bang API v2.
  - Poll trang thai job moi giay.
  - Hien trang thai tien do nho tren reader.
  - Khi job `translated`, thay noi dung chuong hien tai bang ban dich.
  - Khi roi reader, dung polling trong trinh duyet.
- WebService settings cap nhat mo ta tinh nang dich web thanh chuc nang dang hoat dong.
- Web bundle moi duoc build va sync vao `app/src/main/assets/web/vue`.

## Pham vi file tac dong

- `app/src/main/java/io/legado/app/domain/webservice/WebServiceModels.kt`
- `app/src/main/java/io/legado/app/web/WebServiceTranslationJobController.kt`
- `app/src/main/java/io/legado/app/web/KtorServer.kt`
- `app/src/test/java/io/legado/app/domain/webservice/WebServiceModelsTest.kt`
- `modules/web/src/api/webService.ts`
- `modules/web/src/views/BookChapter.vue`
- `modules/web/src/views/WebServiceSettings.vue`
- `modules/web/dist/**`
- `app/src/main/assets/web/vue/**`
- `docs/plans/drducbook-rebuild-2026/TASK-MATRIX.md`
- `docs/plans/drducbook-rebuild-2026/PLAN-LOG.md`

## API v2 translation jobs

| Method | Path | Auth | Gate | Ket qua |
|---|---|---|---|---|
| POST | `/api/v2/translation/jobs` | Bearer session | Same-origin + `autoTranslationEnabled` | Tao/tai su dung job dich chuong |
| GET | `/api/v2/translation/jobs` | Bearer session | Same-origin | Liet ke job runtime hien co |
| GET | `/api/v2/translation/jobs/{jobId}` | Bearer session | Same-origin | Lay trang thai job |
| DELETE | `/api/v2/translation/jobs/{jobId}` | Bearer session | Same-origin | Huy job |

## Kiem thu

Da chay:

```text
pnpm type-check
pnpm build
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.webservice.WebServiceModelsTest" --no-daemon --console=plain
```

Ket qua:

- `modules/web` type-check: PASS.
- `modules/web` build: PASS va sync vao Android assets.
- `:app:compileAppDebugKotlin`: BUILD SUCCESSFUL.
- `WebServiceModelsTest`: 9 tests PASS.

Bang chung:

- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.webservice.WebServiceModelsTest.xml`
- `modules/web/dist/assets/BookChapter-BgnZwOwn.js`
- `modules/web/dist/assets/BookChapter-CgHBplDE.css`
- `modules/web/dist/assets/WebServiceSettings-BdqNnGAr.js`
- `app/src/main/assets/web/vue/assets/BookChapter-BgnZwOwn.js`
- `app/src/main/assets/web/vue/assets/BookChapter-CgHBplDE.css`
- `app/src/main/assets/web/vue/assets/WebServiceSettings-BdqNnGAr.js`

## Rui ro/cong viec con lai

- Job hien la runtime-only; app restart se mat danh sach job dang hien thi, nhung cache dich cua `TranslationManager` van theo he thong hien co.
- Chua co Ktor integration test cho auth/403/cancel route vi server nay chua co test host rieng.
- Chua co realtime WebSocket/SSE; web dang poll 1 giay/lan.
- Hien moi noi dich tung chuong trong web reader. Dich hang loat/tu dong preload nhieu chuong nen lam sau khi co queue/progress/cancel day du hon.
