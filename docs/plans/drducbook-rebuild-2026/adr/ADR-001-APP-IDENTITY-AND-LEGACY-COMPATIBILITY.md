# ADR-001 - App identity va legacy compatibility

- Status: Accepted
- Date: 2026-07-29
- Owners: P01, P11

## Context

DrDucBook phai cai song song voi app cu, dung package moi `com.drducbook.app`, nhung van doc/chay duoc source, extension va plugin Legado/VBook. Repo hien co application ID `io.legato.kazusa`, namespace/source packages `io.legado.app`, dynamic ReaderProvider authority va legacy `legado`/`yuedu` deep links.

## Decision

1. Android `applicationId` va Gradle `namespace` dich la `com.drducbook.app` cho moi variant DrDucBook.
2. Khong bulk-relocate business classes chi de doi ten. Package `io.legado.app` duoc giu nhu compatibility island cho entity JSON, Rhino native wrappers, JS helper names va public adapters ma corpus tham chieu. Code moi thuoc `com.drducbook.app` hoac domain package hien co theo ownership; dependency chi di tu implementation moi sang facade qua interface, khong nguoc lai.
3. ReaderProvider authority la `com.drducbook.app.readerProvider`; FileProvider, Firebase-removal leftovers, startup/profile/provider authorities deu dung `${applicationId}` va phai unique. App moi khong claim authority cua app cu.
4. App moi dang ky `drducbook` la scheme rieng va van chap nhan `legado`/`yuedu` import schemes. Khi hai app cung cai, Android chooser la hanh vi mong doi; khong dat exclusive/default bang thu thuat.
5. OAuth callback rieng la `drducbook://auth/callback`; callback khong route vao import activity.
6. Data, cache, notification channels, WorkManager, shortcut IDs, tile/service state, WebService pairing, Supabase session va Google Drive authorization deu namespaced theo app moi. Khong `sharedUserId`, khong dung chung database/files/provider voi app cu.
7. Legacy JSON chua unknown fields phai duoc import theo tolerant-reader policy; export giu field cong khai can cho Legado/VBook. Service plugin TTS/translator khong bi ep thanh BookSource.
8. R8 keep rules chi bao ve facade/public JS surface duoc inventory va test; khong keep toan bo app.

## Public contract

- Install identity: `com.drducbook.app`.
- Provider: `content://com.drducbook.app.readerProvider/...` voi operation paths trong corpus.
- Import schemes: `drducbook`, `legado`, `yuedu`.
- Auth scheme/host: `drducbook://auth/callback`.
- Compatibility gate: `CompatibilityCorpusTest` tren debug va minified release.

## Alternatives

- Dung lai application ID cu: loai bo vi khong cai song song duoc.
- Rename moi package Kotlin trong mot lan: loai bo vi blast radius/R8/reflection cao va khong tao gia tri tuong thich.
- Claim provider authority cu bang alias: loai bo vi xung dot install voi app cu.

## Consequences

- Generated `R`/`BuildConfig`, manifest references va test imports can cap nhat khi doi namespace.
- Mot phan package ten Legado van ton tai co chu dich; day khong phai rebrand thieu.
- Client hard-code authority app cu phai chon app cu hoac cap nhat authority; JSON/JS plugin compatibility van duoc giu.

## Rollback

Truoc release dau, co the revert Gradle/manifest identity va generated references. Sau khi phat hanh, `applicationId` la bat bien; rollback bang release sua loi cung `com.drducbook.app`, khong doi package va khong dung data cua app cu.
