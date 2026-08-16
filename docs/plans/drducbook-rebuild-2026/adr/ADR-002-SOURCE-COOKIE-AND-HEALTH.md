# ADR-002 - Source, cookie va health

- Status: Accepted
- Date: 2026-07-29
- Owners: P04, P05

## Context

Browser, BookSource, RssSource va VBook hien co nhieu cach nhan dien source va dong bo cookie. Health check bi trung lap; mot flow dang sua group/comment cua source. Yeu cau moi can dau trang theo source, cookie lien thong va tinh trang source tin cay.

## Decision

1. Dinh danh chung la `SourceKey(kind, identity)`:
   - Book: `book:<bookSourceUrl>`.
   - RSS: `rss:<sourceUrl>`.
   - VBook: `vbook:<stablePluginId>`.
   URL identity chi canonicalize scheme/host casing va default port; khong bo query/path/trailing slash neu co the doi nghia source.
2. `SourceDomainIndex` map exact host va parent-domain candidates sang SourceKey. Browser tab giu `activeSourceKey`; user co the chon lai khi nhieu source cung domain. Khong suy source chi tu display name.
3. Dau trang co hai lop: user bookmark editable va source bookmark generated. Source bookmark duoc upsert tu home/login/explore URLs, gan SourceKey, khong xoa user bookmark va khong dong bo cookie.
4. `CookieVault` la owner duy nhat cua cookie source. Key logic gom SourceKey/domain/path/name; record co value encrypted, hostOnly, secure, httpOnly, sameSite, expiresAt, updatedAt. Encryption key nam trong Android Keystore; cookie value khong log, Agent, WebService, analytics hay backup.
5. Bridge WebView/OkHttp/Cronet/Rhino/VBook dung cung policy: import cookie truoc request/navigation, export Set-Cookie sau response/page, loc domain/path/secure/expiry. Logout theo source chi xoa SourceKey va domain lien quan; khong goi global session clear.
6. `SourceCheckEngine` la engine duy nhat. Mode `QUICK`, `STANDARD`, `FULL` tao immutable result `HEALTHY`, `AUTH_REQUIRED`, `RATE_LIMITED`, `RULE_ERROR`, `NETWORK_ERROR`, `TLS_ERROR`, `CONTENT_EMPTY`, `UNSUPPORTED` cung latency/stage/timestamp.
7. Probe adapter rieng cho Book, RSS va VBook. Login xong chi enqueue targeted probe cua SourceKey; worker toan bo source khong duoc kich hoat tu cookie event.
8. Health history nam bang rieng, retention mac dinh 30 ngay/100 result moi source. Engine khong sua source group/comment/enabled; UI chi doc summary va user tu quyet dinh.

## Public contract

- SourceKey schema version `1` va prefix `book`, `rss`, `vbook`.
- CookieVault khong nam trong backup/sync.
- Health result khong ghi response body, cookie, header secret hay full exception chain.
- Existing source JSON fields `enabledCookieJar`, `loginUrl`, `loginUi`, `loginCheckJs` van duoc ton trong.

## Alternatives

- Key bang domain: loai bo vi nhieu source co the cung host.
- Dung WebView CookieManager lam source of truth: loai bo vi khong bao phu Cronet/Rhino/VBook va kho xoa targeted.
- Giu hai health workers: loai bo vi classification/side effect mau thuan.

## Consequences

- Can migration cookie co kiem soat; cookie plaintext cu chi duoc import mot lan roi xoa.
- WebView co the khong expose day du `httpOnly`/SameSite; bridge phai bao toan khi co metadata va fail closed khi khong chac.
- Source check ton tai doc lap voi source config, lam schema tang nhung tranh pha JSON compatibility.

## Rollback

Co feature flag de dung bridge/engine moi va quay lai request adapter cu, nhung khong export plaintext CookieVault. Database migration khong ha version; rollback binary chi duoc dung tren snapshot test hoac release co forward-compatible columns.
