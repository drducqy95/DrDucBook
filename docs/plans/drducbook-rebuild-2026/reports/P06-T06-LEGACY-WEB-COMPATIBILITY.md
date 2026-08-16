# P06.T06 - Legacy HTTP/WebSocket compatibility

## Muc tieu

Giu cac route HTTP/WebSocket legacy cua Legado/VBook on dinh khi DrDucBook bo sung API v2 co pairing, policy, export va translation jobs.

## Thay doi chinh

- Them `WebServiceLegacyContract` trong domain WebService:
  - 14 route POST legacy.
  - 12 route GET legacy.
  - 3 route WebSocket legacy.
  - Shape payload `ReturnData`: `isSuccess`, `errorMsg`, `data`.
- Chuyen `KtorServer` sang dung constant tu `WebServiceLegacyContract` cho cac route legacy thay vi string literal roi rac.
- Giu nguyen controller va payload cu:
  - BookSource/RssSource save/delete/get/list.
  - Bookshelf, chapter list, refresh TOC, chapter content.
  - Cover/image proxy.
  - Web read config.
  - Replace rules.
  - Local book upload.
  - WebSocket debug/search.
- Xac nhan API v2 nam duoi `/api/v2/...`, khong trung path voi route legacy.
- Xac nhan frontend web hien co van suy ra WebSocket bang HTTP port + 1, phu hop cap cong DrDucBook `1124/1125`.

## Route matrix

### POST legacy

| Path | Chuc nang |
|---|---|
| `/saveBookSource` | Luu BookSource |
| `/saveBookSources` | Luu nhieu BookSource |
| `/deleteBookSources` | Xoa BookSource |
| `/saveBook` | Luu sach |
| `/deleteBook` | Xoa sach |
| `/saveBookProgress` | Luu tien do doc |
| `/addLocalBook` | Upload/import sach local |
| `/saveReadConfig` | Luu cau hinh doc web |
| `/saveRssSource` | Luu RSS source |
| `/saveRssSources` | Luu nhieu RSS source |
| `/deleteRssSources` | Xoa RSS source |
| `/saveReplaceRule` | Luu replace rule |
| `/deleteReplaceRule` | Xoa replace rule |
| `/testReplaceRule` | Test replace rule |

### GET legacy

| Path | Chuc nang |
|---|---|
| `/getBookSource` | Lay mot BookSource |
| `/getBookSources` | Lay danh sach BookSource |
| `/getBookshelf` | Lay ke sach |
| `/getChapterList` | Lay muc luc |
| `/refreshToc` | Lam moi muc luc |
| `/getBookContent` | Lay noi dung chuong |
| `/cover` | Lay cover |
| `/image` | Lay/proxy anh trong chuong |
| `/getReadConfig` | Lay cau hinh doc web |
| `/getRssSource` | Lay mot RSS source |
| `/getRssSources` | Lay danh sach RSS source |
| `/getReplaceRules` | Lay replace rules |

### WebSocket legacy

| Path | Chuc nang |
|---|---|
| `/bookSourceDebug` | Debug BookSource |
| `/rssSourceDebug` | Debug RSS source |
| `/searchBook` | Tim sach |

## Kiem thu

Da chay:

```text
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.webservice.WebServiceModelsTest" --no-daemon --console=plain
```

Ket qua:

- `:app:compileAppDebugKotlin`: BUILD SUCCESSFUL.
- `WebServiceModelsTest`: 10 tests PASS.

Bang chung:

- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.webservice.WebServiceModelsTest.xml`

## Rui ro/cong viec con lai

- Chua co Ktor test host de goi route thuc te va assert `ReturnData` response tren HTTP server.
- Chua co WebSocket integration test end-to-end cho debug/search; task nay khoa path contract va compile server.
- QA trinh duyet desktop/mobile, reload/offline va screenshot nam trong P06.T07.
