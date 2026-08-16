# P06.T01 - WebService identity, cong va pairing

## Muc tieu

Cho DrDucBook WebService co identity rieng, default port rieng de cai song song voi ban Legado cu, va API v2 co pairing/session rieng trong khi route legacy van giu nguyen.

## Thay doi chinh

- Them `WebServicePorts`:
  - HTTP mac dinh moi: `1124`.
  - WebSocket mac dinh moi: `1125`.
  - Ghi ro port legacy: HTTP `1122`, WebSocket `1123`.
  - Validate port trong khoang `1024..65534`.
  - Suggest cap port HTTP/WS tiep theo neu port hien tai bi chiem.
- Them `WebServiceInstanceResponse` va route public `GET /api/v2/instance`.
- Them stable instance id luu trong preference `webServiceInstanceId`.
- Them `WebServicePairingBroker`/`WebServicePairingCenter`:
  - App tao ma ghep noi 6 so.
  - Ma het han sau 5 phut.
  - Web doi ma mot lan qua `POST /api/v2/session`.
  - Session bearer het han sau 12 gio.
  - `GET /api/v2/session` kiem tra session.
  - `POST /api/v2/session/revoke` thu hoi session.
  - `toString()` cua challenge/session che ma va token.
- `KtorServer` nhan `httpPort/wsPort` va cai route v2 truoc route legacy.
- CORS them header `Authorization` de client v2 gui bearer session.
- `WebService`:
  - Dung default `1124/1125`.
  - Tu chon cap port trong neu port uu tien dang bi chiem.
  - Revoke session/challenge khi service dung.
- UI `My` WebService block:
  - Van chi hien master toggle, address, copy, open browser.
  - Them nut `Pair browser` va `Copy code` khi co ma.
  - Khong them cau hinh Export/Dich tu dong/Background o Android; cac tuy chon do thuoc web UI trong P06.T02-P06.T03.

## Pham vi file tac dong

- `app/src/main/java/io/legado/app/domain/webservice/WebServiceModels.kt`
- `app/src/main/java/io/legado/app/web/WebServiceIdentityStore.kt`
- `app/src/main/java/io/legado/app/web/KtorServer.kt`
- `app/src/main/java/io/legado/app/service/WebService.kt`
- `app/src/main/java/io/legado/app/constant/PreferKey.kt`
- `app/src/main/java/io/legado/app/ui/config/otherConfig/OtherConfig.kt`
- `app/src/main/java/io/legado/app/ui/main/my/MyViewModel.kt`
- `app/src/main/java/io/legado/app/ui/main/my/MyScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-vi/strings.xml`
- `app/src/test/java/io/legado/app/domain/webservice/WebServiceModelsTest.kt`

## API v2 moi

| Method | Path | Auth | Ket qua |
|---|---|---|---|
| GET | `/api/v2/instance` | Public | Ten app, package, version, instance id, HTTP/WS port, TTL pairing/session |
| POST | `/api/v2/session` | Pairing code | Doi `code` hoac `pairingCode` lay bearer session |
| GET | `/api/v2/session` | Bearer session | Xac nhan session con han |
| POST | `/api/v2/session/revoke` | Bearer session | Thu hoi session |

Route legacy nhu `/getBookshelf`, `/getBookSources`, `/bookSourceDebug`, `/rssSourceDebug`, `/searchBook` khong doi trong task nay.

## Kiem thu

Da chay:

```text
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.webservice.WebServiceModelsTest" --no-daemon --console=plain
```

Ket qua:

- `:app:compileAppDebugKotlin`: BUILD SUCCESSFUL.
- `WebServiceModelsTest`: 3 tests PASS.

Bang chung:

- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.webservice.WebServiceModelsTest.xml`

## Ghi chu

- Da thu chay mot lenh gom compile va test trong cung cau lenh; lenh vuot thoi gian cho 3 phut. Sau do da tach rieng compile va unit test, ca hai deu PASS.
- Warning trong `KtorServer.kt` ve `setLenient()` va `PartData.dispose` la warning API hien co quanh Ktor/Gson/upload legacy; task nay khong doi contract legacy do.
- Route v2 pairing da san sang cho P06.T02 bao ve policy/config API bang bearer session.
