# P11.T01 - Full build/test matrix

## Muc tieu

Chay ma tran build/test lap lai duoc cho Android, Web va backend/local cloud gates sau cac thay doi Phase 04-10 va hotfix Khám phá.

## Trang thai

IN_PROGRESS.

## Pham vi gate

- Android compile/unit/lint/debug/noR8/release.
- Android artifact checksum neu assemble pass.
- Web `modules/web` type-check/build.
- Backend/local cloud Node gates.
- External runtime gates khong co trong workspace: Supabase CLI/Deno/OAuth/Google Drive/HF live.

## Lenh kiem tra

```powershell
node --test scripts/test-asset-ticket.mjs scripts/test-cloud-sync-migration.mjs scripts/test-cloud-security-gates.mjs
.\gradlew.bat :app:testAppDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:lint :app:assembleAppDebug :app:assembleAppNoR8 :app:assembleAppRelease --no-daemon --console=plain
pnpm type-check
pnpm build
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain
.\gradlew.bat :app:assembleAppNoR8 --no-daemon --console=plain
.\gradlew.bat :app:packageAppRelease --no-daemon --console=plain --stacktrace
.\gradlew.bat :app:assembleAppRelease --no-daemon --console=plain
.\gradlew.bat :app:lintAppDebug --no-daemon --console=plain
.\gradlew.bat :app:lintAnalyzeAppDebug --rerun-tasks --no-daemon --console=plain
.\gradlew.bat --stop
```

## Ket qua

- Node cloud/local backend gate: 15 tests PASS latest after cloud security/sync refresh.
- Android full app debug unit: BUILD SUCCESSFUL latest in 4m03s after local AI prompt/export path hotfix.
- Android debug unit XML aggregate latest: 222 XML files; 944 tests; 0 failures; 0 errors; 1 skipped.
- Regression fixed before latest full unit pass:
  - `LocalAiTranslationPrompt` keeps user custom style suffix when the configured prompt starts with the current `TranslationConstants.DEFAULT_PROMPT`.
  - `FileDoc` now builds local files from filesystem paths/`file://` Uris safely on Windows/JVM tests, preventing export EPUB local image packaging from failing before the output file is created.
- Web `pnpm type-check`: PASS.
- Web `pnpm build`: PASS, Vite build 25.41s; sync thanh cong vao `app/src/main/assets/web/vue`; `modules/web/dist` co 17 files, 767,350 bytes.
- Android `:app:compileAppDebugKotlin`: BUILD SUCCESSFUL in 1m54s sau khi sync web assets; sau lint fix `BgEffectBackground` tiep tuc BUILD SUCCESSFUL.
- Android `:app:assembleAppDebug`: BUILD SUCCESSFUL in 1m49s; sau lint fix BUILD SUCCESSFUL in 4m09s.
- Android `:app:assembleAppNoR8`: BUILD SUCCESSFUL in 1m36s; sau lint fix BUILD SUCCESSFUL in 11m49s, co cac warning startup profile cu nhung khong fail.
- Android `:app:assembleAppRelease`: lan dau fail tai `:app:packageAppRelease` sau 25m57s, nhung task rieng `:app:packageAppRelease --stacktrace` PASS in 6m40s; sau lint fix `:app:assembleAppRelease` PASS in 16m03s.
- Android artifact checksum da ghi lai cho debug/noR8/release APKs.
- Android `:app:assembleAppDebug` latest after regression recovery: BUILD SUCCESSFUL in 2m03s; x86_64 APK installed to `emulator-5554` PASS; launch smoke process alive and no matched crash in latest logcat sample.
- Android lint/debug/noR8/release combined gate truoc do: TIMED OUT after 904s, khong tinh pass/fail; Gradle daemon da duoc stop.
- Android `:app:lint` rieng bi ngat giua chung theo interruption; khong co `lint-results*` report moi nen lint van chua tinh pass/fail.
- Android `:app:lintAppDebug` truoc fix: FAILED sau 19m16s voi 1 error `NewApi` tai `BgEffectBackground.kt:47`.
- Lint fix: Tach `BgEffectBackgroundApi33` voi `@RequiresApi(TIRAMISU)` va fallback Android < 33 truoc khi goi `BgEffectPainter`.
- Android `:app:lintAppDebug` final sau media/reader lint cleanup: BUILD SUCCESSFUL in 39m10s. Report moi `app/build/reports/lint-results-appDebug.xml` co 0 fatal/error, 2998 warnings va 26 hints.
- P11.T01 van IN_PROGRESS.

## Android artifact checksum

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `app/build/outputs/apk/app/debug/app-app-arm64-v8a-debug.apk` | 227,218,290 | `a08264974043aa63651d400a05bf5207c1715911731a21a79baf1164eb877a65` |
| `app/build/outputs/apk/app/debug/app-app-armeabi-v7a-debug.apk` | 188,550,103 | `0f250328ba90864d8dec19305cbfc8a6fe286faef785ebd422fb70b263dcfda7` |
| `app/build/outputs/apk/app/debug/app-app-universal-debug.apk` | 342,313,195 | `a45e63380e69f3b010afe75880f08370475316dc3d9c526a9134766c95d39f06` |
| `app/build/outputs/apk/app/debug/app-app-x86_64-debug.apk` | 249,364,662 | `566e5f51adc95afd3e18d85282611cd9f15378922f4eb778288038d602dfbd90` |
| `app/build/outputs/apk/app/noR8/app-app-arm64-v8a-noR8-unsigned.apk` | 158,670,515 | `60b1e77fe7b88b7895b447755110f63bb5609b1b0758a6d5b7d509892c2e7a57` |
| `app/build/outputs/apk/app/noR8/app-app-armeabi-v7a-noR8-unsigned.apk` | 120,002,328 | `8396518ca44077938b224481b6e7bd94fb03afe800eb2a5ad4e6ca36801dadf3` |
| `app/build/outputs/apk/app/noR8/app-app-universal-noR8-unsigned.apk` | 273,765,420 | `484965b1f0a9140d106f16a8cddb4ada77e80b3a41a671f5dbaa61818f1b235f` |
| `app/build/outputs/apk/app/noR8/app-app-x86_64-noR8-unsigned.apk` | 180,816,887 | `c5addf6674b43b0df84e51c50a6cf0ad9da8b6b999c24c8ab9879e30d9f53f53` |
| `app/build/outputs/apk/app/release/app-app-arm64-v8a-release-unsigned.apk` | 127,214,793 | `1a5de118c30c57b5a7376291251a3f0d18d38066b56f432e2adfd5c43141658b` |
| `app/build/outputs/apk/app/release/app-app-armeabi-v7a-release-unsigned.apk` | 88,546,606 | `af82485c7564bef8535b1cc24080ada1c1bbe15b45dddbb945a539280d9e64f2` |
| `app/build/outputs/apk/app/release/app-app-universal-release-unsigned.apk` | 242,309,698 | `28e390ac1f24787e04298146b618573669d43376953704b15c3eddf2641b8c35` |
| `app/build/outputs/apk/app/release/app-app-x86_64-release-unsigned.apk` | 149,361,165 | `e271900a5bef3bd37036db47bc59cbc77ea76a1fa8ed88e9560e61b1f22aa844` |

## Gate chua the dong trong moi truong hien tai

- Supabase local stack/Edge Function runtime do `supabase` CLI va `deno` chua co trong PATH.
- Google Drive OAuth/appDataFolder runtime can OAuth client/account thuc.
- HF private dataset runtime can secret moi da rotate va Edge Function deploy; HF public upload da verify o P10.T01.
- Android lint blocking gate da PASS; warning backlog van duoc theo doi trong P11.T04.
