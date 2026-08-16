# P06.T04 - Export feature gate va pipeline

## Muc tieu

Bat/tat cac lenh export qua WebService bang policy `exportEnabled`, uu tien API v2 co ghep noi trinh duyet, same-origin va bearer session. Cac endpoint legacy doc/sua du lieu van duoc giu nguyen de khong pha tuong thich Legado/VBook.

## Thay doi chinh

- Them request contract export trong `WebServiceModels.kt`:
  - `WebServiceExportSourcesRequest`
  - `WebServiceExportBookshelfRequest`
  - `WebServiceExportChapterRequest`
  - `WebServiceExportBookTextRequest`
- Them `WebServiceExportRequests` de normalize source type, key va chapter index.
- Them `WebServiceExportController` lam lop dieu phoi export v2:
  - Export BookSource/RssSource JSON tu DB hoac tu `payloadJson` hop le.
  - Export bookshelf JSON theo danh sach bookUrl hoac toan bo ke sach.
  - Export mot chuong TXT bang `BookController.getBookContent`.
  - Export sach TXT theo toan bo chuong hoac danh sach chapter index.
- Them API v2 trong `KtorServer`:
  - `POST /api/v2/export/sources`
  - `POST /api/v2/export/bookshelf`
  - `POST /api/v2/export/chapter`
  - `POST /api/v2/export/book-txt`
- Moi API export v2 deu yeu cau:
  - Same-origin hop le.
  - Bearer session tu pairing WebService.
  - Policy `exportEnabled = true`.
- Khi `exportEnabled = false`, backend tra `403` voi `WebServiceErrorResponse("FEATURE_DISABLED")`.
- Body rong duoc xem la request mac dinh; body JSON hong tra `400 EXPORT_REQUEST_INVALID` de tranh vo tinh export toan bo.
- Response file dat `Content-Disposition`, MIME va stream bytes qua Ktor.
- Web frontend:
  - Them client download v2 cho source, bookshelf, chapter va book TXT.
  - Nut export source/RSS bi disable neu policy export tat.
  - Source export dung API v2 thay vi tao file truc tiep trong browser.
  - Sua type handler trong `WebServiceSettings.vue` de web build pass.
- Web bundle moi duoc build va sync vao `app/src/main/assets/web/vue`.

## Pham vi file tac dong

- `app/src/main/java/io/legado/app/domain/webservice/WebServiceModels.kt`
- `app/src/main/java/io/legado/app/web/WebServiceExportController.kt`
- `app/src/main/java/io/legado/app/web/KtorServer.kt`
- `app/src/test/java/io/legado/app/domain/webservice/WebServiceModelsTest.kt`
- `modules/web/src/api/webService.ts`
- `modules/web/src/components/SourceList.vue`
- `modules/web/src/views/WebServiceSettings.vue`
- `modules/web/dist/**`
- `app/src/main/assets/web/vue/**`
- `docs/plans/drducbook-rebuild-2026/TASK-MATRIX.md`
- `docs/plans/drducbook-rebuild-2026/PLAN-LOG.md`

## API export v2

| Method | Path | Auth | Gate | Ket qua |
|---|---|---|---|---|
| POST | `/api/v2/export/sources` | Bearer session | Same-origin + `exportEnabled` | Tai file JSON BookSource/RssSource |
| POST | `/api/v2/export/bookshelf` | Bearer session | Same-origin + `exportEnabled` | Tai file JSON bookshelf |
| POST | `/api/v2/export/chapter` | Bearer session | Same-origin + `exportEnabled` | Tai file TXT mot chuong |
| POST | `/api/v2/export/book-txt` | Bearer session | Same-origin + `exportEnabled` | Tai file TXT theo sach/chon chuong |

## Kiem thu

Da chay:

```text
pnpm type-check
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.webservice.WebServiceModelsTest" --no-daemon --console=plain
pnpm build
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.webservice.WebServiceModelsTest" --no-daemon --console=plain
```

Ket qua:

- `modules/web` type-check: PASS.
- `modules/web` build: PASS va sync vao Android assets.
- `:app:compileAppDebugKotlin`: BUILD SUCCESSFUL.
- `WebServiceModelsTest`: 8 tests PASS.

Bang chung:

- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.webservice.WebServiceModelsTest.xml`
- `modules/web/dist/assets/WebServiceSettings-DBG7dyOM.js`
- `modules/web/dist/assets/WebServiceSettings-BgqYJaY3.css`
- `modules/web/dist/assets/BookShelf-BNXyy5Dc.js`
- `app/src/main/assets/web/vue/assets/WebServiceSettings-DBG7dyOM.js`
- `app/src/main/assets/web/vue/assets/WebServiceSettings-BgqYJaY3.css`
- `app/src/main/assets/web/vue/assets/BookShelf-BNXyy5Dc.js`

## Rui ro/cong viec con lai

- Chua co Ktor integration test cho auth/same-origin/403 vi project chua co test host Ktor cho server nay; hien da co compile, model tests va web build.
- EPUB/PDF/HTML/CBZ export native chua noi vao API v2 trong task nay. `ExportBookService` phu thuoc Android document tree URI va foreground service, nen can job/temp-file broker rieng o task sau de khong pha luong export hien co.
- Export sach TXT hien load noi dung chuong lan luot trong request; sach rat lon can pipeline stream/progress/cancel o task sau.
