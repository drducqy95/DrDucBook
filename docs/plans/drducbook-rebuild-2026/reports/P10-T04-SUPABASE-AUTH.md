# P10.T04 - Supabase Auth email/Google checkpoint

## Muc tieu

Them nen tang dang ky/dang nhap/logout/link/re-auth bang Supabase Auth, gom email/password, Google ID token qua Android Credential Manager, deep link callback `drducbook://auth/callback`, UI tai khoan Compose/MVI va DI/navigation trong app.

## Pham vi da thuc hien

- Them domain contract:
  - `AccountAuthModels.kt`
  - `AccountAuthGateway.kt`
  - `AccountAuthUseCase.kt`
- Them repository Supabase:
  - `SupabaseAccountAuthRepository.kt`
  - Ho tro email sign up/sign in, reset password, reauth, refresh session, local/global sign out va Google ID token sign in/link.
  - UI khong nhan access token; `currentAccessToken()` chi nam o use case/gateway cho cac adapter cloud can JWT sau nay.
- Them Android Google Credential bridge:
  - `GoogleCredentialBridge.kt`
  - Nonce URL-safe, token/nonce duoc redact trong `toString()`.
- Them Account Compose/MVI:
  - `AccountContract.kt`
  - `AccountViewModel.kt`
  - `AccountScreen.kt`
  - Route nam trong Settings, khong them top-level shortcut.
- Wire he thong:
  - Dependencies Credential Manager + Google ID.
  - Koin: `AccountAuthGateway`, `AccountAuthUseCase`, `AccountViewModel`.
  - Main navigation: `MainRouteSettingsAccount`, `ROUTE_SETTINGS_ACCOUNT`, Settings entry.
  - Config tag: `ConfigTag.ACCOUNT_CONFIG`.
- Sua loi test route: dung `MainIntent.createIntent(context, configTag)`.

## Dieu kien da kiem tra

- Compile app debug Kotlin: PASS.
- Account model/contract tests: PASS.
- Navigation/settings route tests: PASS.
- Auth callback/deep-link/config/consent tests: PASS.
- Secret scan hep: khong tim thay HF token thuc, Supabase service key literal, ticket secret literal hoac env assignment hardcode trong app/source/function scripts.

## Lenh kiem tra

```powershell
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain --stacktrace
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.AccountAuthModelsTest" --tests "io.legado.app.ui.account.AccountContractTest" --tests "io.legado.app.ui.main.MainNavigatorTest" --tests "io.legado.app.ui.main.MainIntentTest" --tests "com.drducbook.app.auth.DrDucBookDeepLinksTest" --tests "com.drducbook.app.ManifestIdentityTest" --tests "com.drducbook.app.cloud.SupabaseClientProviderTest" --tests "com.drducbook.app.cloud.CloudConsentScopesTest" --no-daemon --console=plain
rg -n "hf_[A-Za-z0-9]{20,}|supabase.*service.*key|service_role.*eyJ|ASSET_TICKET_SECRET\s*=|HF_READ_TOKEN\s*=|SUPABASE_SERVICE_ROLE_KEY\s*=" app/src/main/java app/src/main/res app/build.gradle.kts gradle/libs.versions.toml supabase/functions supabase/migrations scripts -g "*.kt" -g "*.xml" -g "*.kts" -g "*.toml" -g "*.ts" -g "*.mjs" -g "*.sql" -g "*.ps1"
```

## Ket qua

- `:app:compileAppDebugKotlin`: BUILD SUCCESSFUL in 8m 47s.
- Focused Auth/navigation/callback/config suite: BUILD SUCCESSFUL in 1m.
- XML evidence:
  - `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.model.AccountAuthModelsTest.xml`
  - `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.account.AccountContractTest.xml`
  - `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.main.MainIntentTest.xml`
  - `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.main.MainNavigatorTest.xml`
  - `app/build/test-results/testAppDebugUnitTest/TEST-com.drducbook.app.auth.DrDucBookDeepLinksTest.xml`
  - `app/build/test-results/testAppDebugUnitTest/TEST-com.drducbook.app.ManifestIdentityTest.xml`
  - `app/build/test-results/testAppDebugUnitTest/TEST-com.drducbook.app.cloud.SupabaseClientProviderTest.xml`
  - `app/build/test-results/testAppDebugUnitTest/TEST-com.drducbook.app.cloud.CloudConsentScopesTest.xml`

## Rui ro/cong viec con lai

- Chua co Supabase local stack/CLI va Google OAuth client thuc trong moi truong nay, nen chua the dong gate happy/error/cancel/offline/process restart runtime.
- Can test thuc te email verification/reset, Google account collision/link policy, session revoke va process restart tren device khi co redirect config.
- P10.T05 se dung Auth gateway/JWT nay de xay Postgres/RLS/Storage sync foundation.
