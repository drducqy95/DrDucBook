# ADR-003 - Appearance va visual assets

- Status: Accepted
- Date: 2026-07-29
- Owners: P01, P03, P06

## Context

Nguoi dung muon doi avatar app, tuy chinh icon ben trong app, hinh nen va giao dien WebService. Android khong cho mot app thay launcher bitmap tuy y ma khong co activity alias/prebuilt resource.

## Decision

1. Icon nguon moi duoc xu ly xoa marker Gemini, cat/can bang va tao adaptive foreground/background, monochrome va density assets. File nguon da lam sach duoc luu cung provenance; khong giu ban co marker trong resource ship.
2. Launcher icon cho phep chon mot tap alias da bundle. Imported bitmap khong tro thanh launcher icon; no chi dung cho `IconSlot` trong app de tranh han che launcher/cache/security cua Android.
3. `AppearanceProfile` version `1` gom theme mode/engine, seed/palette, typography preset, density, corner scale, module background refs, icon-slot refs, contrast va reduce-motion. Profile luu bang repository rieng; Compose chi nhan immutable state.
4. `IconSlot` co stable ID cho navigation/action/source category/Agent tool. Custom image duoc copy vao app-private storage, decode gioi han kich thuoc, strip metadata, tao thumbnail va fallback ve `AppIcons` khi file hong/mat.
5. Background co scope `APP`, `READER`, `WORKSPACE`, `BROWSER`, `WEB_SERVICE`; moi scope luu asset ref, crop mode, overlay opacity va blur setting. Khong dung background lam giam contrast duoi accessibility gate.
6. `.drductheme` chi chua versioned manifest va optional sanitized image assets; khong chua cookie/session/source credential. Import vao staging, verify size/hash/schema roi commit atomic.
7. WebService co `WebAppearanceProfile` rieng va chi cho thay background. Android UI khong dieu khien web background; no chi co master toggle bat/tat dich vu. Export va Dich tu dong duoc bat/tat trong web settings theo ADR-005.
8. App theme va WebService theme khong tu dong dong bo. Backup/sync co the chua profile va sanitized assets, nhung khong chua temp/cache/original EXIF.

## Public contract

- `AppearanceProfile.schemaVersion = 1`.
- Custom launcher bitmap: unsupported; bundled aliases only.
- Icon/background import max dimensions va bytes duoc central config, test decode-bomb va corrupt file.
- App default van hoat dong khi moi custom asset bi xoa.

## Alternatives

- Dynamic shortcut icon thay launcher: loai bo vi khong thay app launcher identity.
- Mot theme profile chung cho Android va web: loai bo theo yeu cau tach giao dien.
- Luu URI ben ngoai lau dai: loai bo vi permission co the het han; asset phai copy private.

## Consequences

- User co tu do cao ben trong app nhung launcher icon tuy chon co gioi han ro rang.
- Backup co them asset manifest/dedupe va quota.
- Hai theme engine Material/Miuix can cung resolve AppearanceProfile va visual test.

## Rollback

Reset profile ve default va vo hieu hoa custom asset resolver; khong xoa asset ngay de user co the phuc hoi. Launcher alias rollback bat alias mac dinh truoc khi tat alias dang active.
