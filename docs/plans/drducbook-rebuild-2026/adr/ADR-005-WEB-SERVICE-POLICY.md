# ADR-005 - WebService policy

- Status: Accepted
- Date: 2026-07-29
- Owners: P06

## Context

Embedded Ktor/Vue service hien co 27 HTTP routes, 3 WebSocket routes, `anyHost` CORS va hai cong 1122/1123. Yeu cau moi giu giao dien web doc lap: app chi bat/tat WebService; trong web, user chi duoc bat/tat Export, Dich tu dong va thay background.

## Decision

1. Giu HTTP port `1122` va WebSocket port `1123` de bao toan client compatibility. Port duoc owner boi mot WebService instance; start/stop idempotent va release port hoan toan.
2. Android UI chi co master toggle WebService. Dia chi va pairing code hien trong notification/service status khi dang chay; khong dua Export, Dich tu dong hay web background vao Android appearance screen.
3. `WebServicePolicy` version `1` gom `enabled`, `exportEnabled`, `autoTranslationEnabled`, `backgroundAssetRef`, pairing/session metadata. Web settings chi expose hai feature toggles va background picker/reset; khong expose app theme/icon/navigation controls.
4. First connection pair bang short-lived one-time code; thanh cong cap random 128-bit session token voi idle/absolute expiry. Token chi trong app-private storage va browser secure storage, khong la Supabase/Drive token.
5. Bind LAN chi khi master toggle bat. Mutating HTTP routes va moi WebSocket can paired session; static pairing shell co the anonymous. CORS dung explicit local origins/session checks, khong `anyHost`.
6. 27 HTTP va 3 WebSocket route shapes trong corpus duoc giu. Compatibility adapter map v1 payload sang use cases moi; authorization/feature policy boc ngoai route, khong thay response envelope am tham.
7. Khi `exportEnabled=false`, export endpoint tra structured `403 FEATURE_DISABLED`. Khi `autoTranslationEnabled=false`, khong tao job moi; job dang chay duoc cancel an toan va partial output khong commit.
8. Background upload gioi han type/size/dimensions, strip metadata, copy vao private storage va dung contrast overlay. No khong dong bo voi Android background.
9. WebService khong expose CookieVault, Supabase session, Google Drive token, HF signed URL/ticket hay Agent secret. Export file chi duoc tao qua broker va het han.

## Public contract

- Ports: HTTP 1122, WebSocket 1123.
- Existing route/path/payload baseline: `compat/contracts/web-service.json`.
- Feature state response co machine codes `FEATURE_DISABLED`, `PAIRING_REQUIRED`, `SESSION_EXPIRED`.
- Stop service dong sockets/jobs va revoke web sessions.

## Alternatives

- Dung Supabase Auth truc tiep cho LAN service: loai bo vi lo cloud session va lam offline LAN phu thuoc cloud.
- Cho Android va web chung appearance profile: loai bo theo yeu cau.
- Doi cong/version URL ngay: loai bo vi pha existing clients.

## Consequences

- Legacy web client can pairing bootstrap mot lan.
- Feature gate nam server-side, khong chi an nut Vue.
- Background asset them quota/cleanup nho trong backup policy.

## Rollback

Co the rollback Vue build doc lap trong khi giu Ktor contract. Kill switch tat Export/Dich tu dong; master stop luon kha dung. Khong rollback ve unauthenticated `anyHost` trong release production.
