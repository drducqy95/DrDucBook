# Phase 04 - Browser, nguon, dau trang va cookie

## Muc tieu phase

Bien Browser thanh cong cu lam viec cung he thong nguon: nhan dien nguon theo tab, mo hanh dong nguon, sinh shortcut tu nguon hien co va dong bo cookie hai chieu voi moi network runtime trong DrDucBook.

## Pham vi file chinh

- `ui/browser/BrowserContract.kt`, `BrowserViewModel.kt`, `BrowserScreen.kt`, `BrowserRouteScreen.kt`, `BrowserTabStore.kt`
- `domain/model/SourceKey.kt`, `BrowserSourceContext.kt`, `BrowserBookmark.kt` `[NEW]`
- `data/entities/BrowserBookmarkEntity.kt`, `SourceBookmarkPreferenceEntity.kt`, DAO/repository `[NEW]`
- `help/http/CookieManager.kt`, `CookieStore.kt`, `data/entities/Cookie.kt`, `CookieDao.kt`
- `data/cookie/**`, `domain/gateway/SourceCookieGateway.kt` `[NEW]`
- source/RSS/VBook repositories, SourceLogin, Rhino/Cronet/OkHttp adapters, Room schema/tests

## Task chi tiet

### P04.T01 - SourceKey, source context va domain index

**Muc tieu:** Gan mot tab Browser voi dung source mot cach on dinh.

**Pham vi file:** SourceKey/context/domain-index models, source/RSS/VBook repositories, Browser tab persistence va matching tests.

**Thuc hien:** Tao `SourceKey(type,id/url)`, `BrowserSourceContext`, domain index tu base/login/search/explore/RSS/VBook metadata va observed redirect; cap nhat index theo flow import/edit/enable/delete.

**Dieu kien thong qua:** Match exact host/subdomain, redirect va nhieu source cung domain dung; VBook URL khong HTTP khong tao match gia; index rebuild idempotent.

**Log:** Ghi rule uu tien match, fixture domains va test report.

### P04.T02 - Browser Home va hanh dong theo nguon

**Muc tieu:** Thay Google home bang man hinh cong viec voi nguon.

**Pham vi:** Browser contract/screen/viewmodel, source icon/health adapters, nav callbacks.

**Thuc hien:** Home gom address/search, recent, manual bookmarks, source shortcuts va health summary; indicator source tren address bar; menu mo/tim/dang nhap/check/edit/history/clear-cookie/open-app.

**Dieu kien thong qua:** Source context theo tung tab va restore duoc; redirect ngoai domain khong tiep tuc ap header nguon; menu an action khong hop le.

**Log:** Screenshot Home/source indicator va test tab/context.

### P04.T03 - Dau trang ca nhan va dau trang nguon tu dong

**Muc tieu:** Co bookmark web rieng va shortcut dong theo danh sach nguon.

**Pham vi file:** Browser bookmark entities/DAO/repository/UI, source shortcut resolver, Room schema va backup adapters.

**Thuc hien:** Tao Room entity/DAO cho manual bookmark va pin/hide preference; source shortcut derive tu enabled/disabled sources; URL uu tien login/home/base/first endpoint; them folder/sort/search.

**Dieu kien thong qua:** Import/edit/disable/delete source phan anh ngay; source non-HTTP khong tao shortcut; manual bookmark CRUD/restore pass; khong dung entity bookmark sach.

**Log:** Ghi schema, URL priority, lifecycle tests va backup policy.

### P04.T04 - CookieVault schema va encryption

**Muc tieu:** Luu cookie co scope/expiry thay cho chuoi `name=value` theo subdomain.

**Pham vi:** Cookie entity/DAO migration, encrypted codec/Keystore, gateway/interface va compatibility adapter.

**Thuc hien:** Model name/value/domain/path/expires/secure/httpOnly/sameSite/hostOnly/origin; AES-GCM key Android Keystore; atomic merge/delete/expiry cleanup; adapter giu JS `cookie.get/set/remove`.

**Dieu kien thong qua:** RFC matching tests pass; plaintext cookie khong xuat hien trong DB dump/log/backup; key loss co loi/fallback xoa an toan, khong crash loop.

**Log:** Ghi schema/migration, encryption test va redaction scan, khong ghi cookie value.

### P04.T05 - Dong bo WebView va runtime nguon

**Muc tieu:** OkHttp, Cronet, Rhino, VBook, SourceLogin va Browser cung mot cookie gateway.

**Pham vi file:** HTTP/Cronet/Rhino/VBook cookie adapters, BrowserRouteScreen WebView bridge, SourceLogin va integration tests.

**Thuc hien:** Adapter load/save response; inject cookie theo URL truoc WebView load; import sau start/finish/redirect/download/background/dispose; khong `removeSessionCookies` toan cuc; flush va expiry cleanup.

**Dieu kien thong qua:** Cookie tu source vao Browser va nguoc lai; path/secure/expiry/session dung; tab/domain khac khong bi xoa; app cu khong nhan cookie.

**Log:** Ghi adapter coverage, network fixture results va security scan.

### P04.T06 - Dang nhap nguon va targeted probe

**Muc tieu:** Dong dang nhap Browser phai cap nhat dung source va check dung source.

**Pham vi file:** Browser effects/source actions, SourceLogin flow, targeted check use case/worker API va auth-state tests.

**Thuc hien:** Mo Browser bang `SourceKey`; inject source cookies/header/UA; auto sync moi page finish; nut `Dong bo va kiem tra`; goi targeted Standard check thay vi worker all-source.

**Dieu kien thong qua:** Auth/captcha state chi doi sau probe thanh cong; probe source khac khong chay; logout xoa vault va WebView cookie dung domain.

**Log:** Ghi source fixture, check run ID va before/after state, khong ghi credential.

### P04.T07 - Regression tests Browser/source/cookie

**Muc tieu:** Dong gate phase bang unit/integration/device tests.

**Pham vi:** domain index, bookmark DAO, CookieVault, WebView instrumentation va Browser UI tests.

**Dieu kien thong qua:** Cover process death, multiple tabs, multiple sources same host, expiry, redirects, logout, downloads, SSL block, malicious URL va side-by-side isolation.

**Log:** Bang testcase/pass-fail, report paths va device evidence.

## Gate dong phase

- P04.T01-P04.T07 `DONE` voi log rieng.
- Cookie khong lo plaintext va khong vao backup.
- Browser source context/bookmark/cookie pass test debug va release.
