# P10.T06 - Google Drive appDataFolder backup transport checkpoint

## Muc tieu

Giu backup/sync Google Drive theo least privilege, tach biet voi Supabase/Google sign-in identity. Drive chi duoc xin `drive.appdata` khi user bat Drive target; token/email khong di vao log, Room plaintext hoac snapshot.

## Pham vi da thuc hien

- Them domain contract:
  - `GoogleDriveBackupModels.kt`
  - `GoogleDriveBackupGateway.kt`
  - `GoogleDriveBackupUseCase.kt`
- Them app-side Drive appDataFolder transport contract:
  - `GoogleDriveAppDataContract.kt`
  - `GoogleDriveAppDataBackupRepository.kt`
- Wire Koin:
  - `GoogleDriveBackupGateway`
  - `GoogleDriveBackupUseCase`
- Them tests:
  - `GoogleDriveAppDataContractTest.kt`
  - Cap nhat evidence qua `CloudConsentScopesTest`.

## Invariants da khoa

- Google sign-in scopes van chi gom `openid`, `email`, `profile`.
- Google Drive backup scopes bat buoc chi la `https://www.googleapis.com/auth/drive.appdata`.
- Drive upload metadata bat buoc co parent `appDataFolder`.
- Drive object namespace la `drducbook`, appData path la `snapshots/{revision}/{snapshot_id}.drducsnapshot`.
- Drive metadata dung `supabaseUserHash`, khong dung email thuc.
- Account mismatch duoc model hoa bang hash va yeu cau xac nhan ro khi Supabase account hash khac Drive account hash.
- `GoogleDriveCredentialSnapshot.toString()` redact access/refresh token.
- Drive request `toString()` redact Authorization header.

## Lenh kiem tra

```powershell
.\gradlew.bat :app:testAppDebugUnitTest --tests "com.drducbook.app.cloud.GoogleDriveAppDataContractTest" --tests "com.drducbook.app.cloud.CloudConsentScopesTest" --no-daemon --console=plain
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
rg -n "hf_[A-Za-z0-9]{20,}|supabase.*service.*key|service_role.*eyJ|ASSET_TICKET_SECRET\s*=|HF_READ_TOKEN\s*=|SUPABASE_SERVICE_ROLE_KEY\s*=|refresh-token-secret|access-token-secret|ya29\.access-token-secret" app/src/main/java app/src/main/res app/build.gradle.kts gradle/libs.versions.toml supabase/functions supabase/migrations scripts -g "*.kt" -g "*.xml" -g "*.kts" -g "*.toml" -g "*.ts" -g "*.mjs" -g "*.sql" -g "*.ps1"
```

## Ket qua

- Focused Drive consent/contract tests: BUILD SUCCESSFUL in 1m10s.
- `:app:compileAppDebugKotlin`: BUILD SUCCESSFUL in 38s.
- Secret scan hep: khong co match.
- XML evidence:
  - `app/build/test-results/testAppDebugUnitTest/TEST-com.drducbook.app.cloud.GoogleDriveAppDataContractTest.xml`
  - `app/build/test-results/testAppDebugUnitTest/TEST-com.drducbook.app.cloud.CloudConsentScopesTest.xml`

## Rui ro/cong viec con lai

- Chua them Google AuthorizationClient runtime, revoke/re-consent UI va encrypted Drive token store.
- Chua upload/download/resumable Drive API runtime do chua co OAuth client/tai khoan test trong moi truong nay.
- P10.T07 se dung contract nay de them snapshot head/conflict/restore; P10.T08 se dong gate Drive scope/revoke/account mismatch/resumable upload bang runtime.
