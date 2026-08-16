# P01.T02 - Authorities va deep links

## Ket qua

Trang thai: `DONE`

## Contract

- Release ReaderProvider: `com.drducbook.app.readerProvider`.
- Debug ReaderProvider: `com.drducbook.app.debug.readerProvider`.
- FileProvider/Startup Provider cung namespaced bang `${applicationId}`.
- Supabase callback: `drducbook://auth/callback` -> `AuthCallbackActivity`.
- DrDucBook import: `drducbook://import` -> `OnLineImportActivity`.
- Legacy import aliases: `legado://`, `yuedu://` duoc giu de Android chooser xu ly khi hai app cung cai.

`AuthCallbackActivity` nam trong product package, goi `supabase.handleDeeplinks(intent)` va quay ve compatibility MainActivity. Import va auth khac host/path, khong activity nao bat nham callback cua activity kia.

## Verification

- `ManifestIdentityTest`: Provider authority va hai intent resolution PASS.
- `DrDucBookDeepLinksTest`: callback allow/reject matrix PASS.
- Merged/packaged manifest co package `com.drducbook.app.debug`, Application moi, authority moi va khong co old provider authority.
- Focused command: BUILD SUCCESSFUL trong 1 phut 9 giay, 2 tests PASS.

Merged manifest evidence nam trong `app/build/intermediates/merged_manifests/appDebug/**/AndroidManifest.xml` va packaged manifest variant outputs.

## Rui ro con lai

- Install chooser va provider conflict duoc test thuc tren emulator tai P01.T06.
- OAuth callback can duoc dang ky tren Supabase dashboard khi project URL/client ID duoc cap; client-side contract da khoa.
